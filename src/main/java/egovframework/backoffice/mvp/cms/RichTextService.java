package egovframework.backoffice.mvp.cms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import egovframework.backoffice.mvp.common.BusinessException;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/** Stores an allowlisted Delta document, never caller-supplied HTML. */
@Service("richText")
public class RichTextService {
    private final ObjectMapper json;
    private final MediaService media;
    private static final Set<String> COLORS = Set.of("navy","blue","teal","red","purple","gray","yellow","white");
    public RichTextService(ObjectMapper json, MediaService media) { this.json=json; this.media=media; }
    public record Document(String json, String text, List<Long> mediaIds) {}

    public Document validate(AccountPrincipal actor, String source) {
        if(source==null || source.isBlank()) return new Document(null,"",List.of());
        JsonNode root=parse(source);
        var ids=new LinkedHashSet<Long>(); var plain=new StringBuilder();
        for(JsonNode op:root.get("ops")) {
            keys(op,Set.of("insert","attributes"));
            JsonNode value=op.get("insert");
            if(value==null) fail();
            if(value.isTextual()) plain.append(value.asText());
            else {
                if(!value.isObject() || value.size()!=1) fail();
                String kind=value.fieldNames().next(); JsonNode v=value.get(kind);
                switch(kind) {
                    case "aicaImage" -> {
                        keys(v,Set.of("id","width","align","alt","caption"));
                        long id=id(v); ids.add(id);
                        if(!media.required(id).mime().startsWith("image/")) throw new BusinessException("사진 위치에는 이미지 파일을 선택하세요.");
                        choice(v,"width",Set.of("25","50","75","100")); choice(v,"align",Set.of("left","center","right"));
                        text(v,"alt",300); text(v,"caption",300); plain.append(v.path("caption").asText()).append('\n');
                    }
                    case "aicaFile" -> { keys(v,Set.of("id","label")); ids.add(id(v)); text(v,"label",200); plain.append(v.path("label").asText()).append('\n'); }
                    case "aicaTable" -> {
                        keys(v,Set.of("rows")); var rows=v.path("rows");
                        if(!rows.isArray() || rows.isEmpty() || rows.size()>20) fail();
                        int columns=rows.get(0).size(); if(columns<1 || columns>8) fail();
                        for(var row:rows) { if(!row.isArray() || row.size()!=columns) fail(); for(var cell:row) { if(!cell.isTextual() || cell.asText().length()>500) fail(); plain.append(cell.asText()).append(' '); } plain.append('\n'); }
                    }
                    case "divider" -> { if(!v.isBoolean() || !v.asBoolean()) fail(); }
                    default -> fail();
                }
            }
            var attrs=op.get("attributes");
            if(attrs!=null) {
                keys(attrs,Set.of("bold","italic","underline","strike","header","font","size","color","background","align","list","indent","blockquote","code-block","link"));
                var it=attrs.fields();
                while(it.hasNext()) {
                    var entry=it.next(); String k=entry.getKey(); var v=entry.getValue();
                    switch(k) {
                        case "bold","italic","underline","strike","blockquote" -> { if(!v.isBoolean()) fail(); }
                        case "header" -> { if(!v.isIntegralNumber() || v.asInt()<1 || v.asInt()>3) fail(); }
                        case "indent" -> { if(!v.isIntegralNumber() || v.asInt()<1 || v.asInt()>4) fail(); }
                        case "font" -> choice(attrs,k,Set.of("sans","serif","mono","nanumgothic","nanummyeongjo","gowundodum","gowunbatang","ibmplexsanskr","nanumpenscript","jua","dohyeon"));
                        case "size" -> choice(attrs,k,Set.of("small","large","huge"));
                        case "color","background" -> choice(attrs,k,COLORS);
                        case "align" -> choice(attrs,k,Set.of("left","center","right","justify"));
                        case "list" -> choice(attrs,k,Set.of("ordered","bullet"));
                        case "code-block" -> { if(!(v.isBoolean() && v.asBoolean()) && !v.asText().equals("plain")) fail(); }
                        case "link" -> { if(!v.isTextual() || v.asText().length()>1000 || !safeLink(v.asText())) throw new BusinessException("링크는 올바른 http 또는 https 주소를 입력하세요."); }
                        default -> fail();
                    }
                }
            }
        }
        if(plain.length()>20000) throw new BusinessException("본문은 20,000자 이하로 입력하세요.");
        media.validate(actor,new ArrayList<>(ids));
        return new Document(root.toString(),plain.toString().stripTrailing(),List.copyOf(ids));
    }
    public List<Long> mediaIds(String source) {
        if(source==null || source.isBlank()) return List.of();
        var ids=new LinkedHashSet<Long>();
        for(var op:parse(source).get("ops")) {
            var insert=op.path("insert");
            for(String key:List.of("aicaImage","aicaFile")) if(insert.has(key)) ids.add(insert.get(key).path("id").asLong());
        }
        return List.copyOf(ids);
    }
    private JsonNode parse(String source) {
        if(source.length()>250000) throw new BusinessException("본문 구성이 너무 큽니다.");
        try {
            JsonNode node=json.readTree(source); keys(node,Set.of("ops"));
            if(!node.path("ops").isArray() || node.path("ops").size()>5000) fail();
            return node;
        } catch(BusinessException error) { throw error; }
          catch(Exception error) { throw new BusinessException("본문 형식을 읽을 수 없습니다."); }
    }
    private static long id(JsonNode v) { if(!v.path("id").isIntegralNumber() || !v.path("id").canConvertToLong() || v.path("id").asLong()<1) fail(); return v.path("id").asLong(); }
    private static void keys(JsonNode n,Set<String> allowed) { if(n==null || !n.isObject()) fail(); n.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))fail();}); }
    private static void choice(JsonNode n,String k,Set<String> choices) { if(n.has(k) && !choices.contains(n.get(k).asText())) fail(); }
    private static void text(JsonNode n,String k,int max) { if(n.has(k) && (!n.get(k).isTextual() || n.get(k).asText().length()>max)) fail(); }
    private static void fail() { throw new BusinessException("지원하지 않는 본문 서식입니다. 내용을 확인해 주세요."); }
    private static boolean safeLink(String value) {
        try { var uri=java.net.URI.create(value); return Set.of("http","https").contains(uri.getScheme()) && uri.getHost()!=null && uri.getUserInfo()==null; } catch(Exception ignored) { return false; }
    }
    private static String e(String value) { return HtmlUtils.htmlEscape(value); }

    /** Output is assembled only from escaped text and fixed tags/classes. */
    public String html(String source,String fallback) {
        return html(source,fallback,id->"/admin/media/"+id+"/file");
    }
    public String publicHtml(String source,String fallback) {
        return html(source,fallback,id->"/api/public/v1/media/"+id+"/file");
    }
    public String html(String source,String fallback,java.util.function.LongFunction<String> mediaUrl) {
        if(source==null || source.isBlank()) return "<p>"+e(Objects.toString(fallback,"")).replace("\n","<br>")+"</p>";
        var out=new StringBuilder(); var line=new StringBuilder();
        String activeList=""; int listItem=0;
        for(var op:parse(source).get("ops")) {
            var value=op.get("insert"); var a=op.path("attributes");
            if(value.isTextual()) {
                String[] parts=value.asText().split("\n",-1);
                for(int i=0;i<parts.length;i++) {
                    line.append(inline(parts[i],a));
                    if(i<parts.length-1) {
                        String list=a.path("list").asText("")+":"+a.path("indent").asInt();
                        listItem=list.equals(activeList)?listItem+1:1; activeList=list;
                        String rendered=block(line.toString(),a);
                        if(a.path("list").asText().equals("ordered"))rendered=rendered.replace("<ol class=", "<ol start=\""+listItem+"\" class=");
                        out.append(rendered);line.setLength(0);
                    }
                }
            } else {
                activeList="";listItem=0;
                if(!line.isEmpty()) {out.append(block(line.toString(),json.createObjectNode()));line.setLength(0);}
                if(value.has("aicaImage")) {
                    var v=value.get("aicaImage");
                    out.append("<figure class=\"rt-image rt-width-").append(e(v.path("width").asText("100"))).append(" rt-align-").append(e(v.path("align").asText("center"))).append("\"><img src=\"").append(e(mediaUrl.apply(v.path("id").asLong()))).append("\" alt=\"").append(e(v.path("alt").asText())).append("\"><figcaption>").append(e(v.path("caption").asText())).append("</figcaption></figure>");
                } else if(value.has("aicaFile")) {
                    var v=value.get("aicaFile");out.append("<p class=\"rt-file\"><a download href=\"").append(e(mediaUrl.apply(v.path("id").asLong()))).append("\">").append(e(v.path("label").asText("첨부 파일"))).append(" ↓</a></p>");
                } else if(value.has("aicaTable")) {
                    out.append("<div class=\"rt-table\"><table><tbody>");
                    for(var row:value.path("aicaTable").path("rows")) {out.append("<tr>");for(var cell:row)out.append("<td>").append(e(cell.asText()).replace("\n","<br>")).append("</td>");out.append("</tr>");}out.append("</tbody></table></div>");
                } else if(value.has("divider")) out.append("<hr>");
            }
        }
        if(!line.isEmpty())out.append(block(line.toString(),json.createObjectNode()));
        return out.toString();
    }
    private String inline(String text,JsonNode a) {
        String s=e(text); if(s.isEmpty())return "";
        for(String key:List.of("bold","italic","underline","strike"))if(a.path(key).asBoolean()) {
            String tag=Map.of("bold","strong","italic","em","underline","u","strike","s").get(key);s="<"+tag+">"+s+"</"+tag+">";
        }
        var classes=new StringBuilder();
        for(String k:List.of("font","size","color","background"))if(a.has(k))classes.append("rt-").append(k).append('-').append(e(a.get(k).asText())).append(' ');
        if(!classes.isEmpty())s="<span class=\""+classes+"\">"+s+"</span>";
        if(a.has("link") && safeLink(a.get("link").asText()))s="<a href=\""+e(a.get("link").asText())+"\" target=\"_blank\" rel=\"noopener noreferrer\">"+s+"</a>";
        return s;
    }
    private String block(String line,JsonNode a) {
        String tag=a.has("header")?"h"+Math.max(1,Math.min(3,a.path("header").asInt())):a.path("blockquote").asBoolean()?"blockquote":a.has("code-block")?"pre":"p";
        String classes="rt-align-"+e(a.path("align").asText("left"))+" rt-indent-"+Math.max(0,Math.min(4,a.path("indent").asInt()));
        if(a.has("list"))return "<"+(a.path("list").asText().equals("ordered")?"ol":"ul")+" class=\""+classes+"\"><li>"+(line.isEmpty()?"<br>":line)+"</li></"+(a.path("list").asText().equals("ordered")?"ol":"ul")+">";
        return "<"+tag+" class=\""+classes+"\">"+(line.isEmpty()?"<br>":line)+"</"+tag+">";
    }
}
