package egovframework.backoffice.mvp.version;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.type.TypeReference;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.post.*;
import egovframework.backoffice.mvp.classification.*;
import egovframework.backoffice.mvp.restaurant.*;
import egovframework.backoffice.mvp.common.BusinessException;
import java.util.*;
import org.springframework.stereotype.Component;
import static egovframework.backoffice.mvp.cms.CmsModels.*;

/** Captures persisted canonical values, including fields omitted by legacy clients. No writer dependencies. */
@Component
public class VersionSnapshots {
 public record Attachment(long id,String name,String alt,String mime,long byteSize){}
 public record PostSnapshot(String title,String content,String richContent,Long categoryId,String categoryName,
  ClassificationModels.Classification classification,List<Long> mediaIds,List<Attachment> attachments,RestaurantDetailsService.Details restaurant){}
 public record PageSnapshot(String title,String slug,List<Section> sections){}
 public record TemplateSnapshot(String name,String description,boolean active,List<Section> blocks){}
 private final CmsStore cms;private final PostMapper posts;private final ClassificationService classifications;
 private final RestaurantDetailsService restaurants;private final PageTemplateStore templates;private final ObjectMapper json;
 public VersionSnapshots(CmsStore cms,PostMapper posts,ClassificationService classifications,RestaurantDetailsService restaurants,PageTemplateStore templates,ObjectMapper json){
  this.cms=cms;this.posts=posts;this.classifications=classifications;this.restaurants=restaurants;this.templates=templates;this.json=json;
 }
 public JsonNode capture(VersionKind k,long id,boolean published){
  Object value;
  if(k==VersionKind.POST){
   var p=posts.find(id);if(p==null)throw new BusinessException("콘텐츠를 찾을 수 없습니다.");
   PublicPost pub=published?cms.one("versionPostPublication",id):null;
   if(published&&pub==null)throw new BusinessException("발행본을 찾을 수 없습니다.");
   var c=published?classifications.published(id):classifications.draft(id);
   List<Media> media=cms.all(published?"publishedPostMedia":"postMedia",id);
   Long category=published?pub.categoryId():p.categoryId();Category cat=category==null?null:cms.one("category",category);
   value=new PostSnapshot(published?pub.title():p.title(),published?pub.content():p.content(),published?pub.richContent():p.richContent(),
    category,cat==null?null:cat.name(),c,media.stream().map(Media::id).toList(),media.stream().map(m->new Attachment(m.id(),m.name(),m.alt(),m.mime(),m.byteSize())).toList(),published?restaurants.published(id,c.typeCode()):restaurants.draft(id,c.typeCode()));
  }else if(k==VersionKind.PAGE){
   Page p=cms.one("page",id);PublishedPage pub=published?cms.one("anyPagePublication",id):null;
   if(p==null||published&&pub==null)throw new BusinessException("페이지를 찾을 수 없습니다.");
   value=new PageSnapshot(published?pub.title():p.title(),published?pub.slug():p.slug(),blocks(published?pub.sectionsJson():p.sectionsJson()));
  }else{
   var t=templates.get(id);if(t==null)throw new BusinessException("템플릿을 찾을 수 없습니다.");
   value=new TemplateSnapshot(t.name(),t.description(),t.active(),blocks(t.blocksJson()));
  }
  JsonNode node=json.valueToTree(value);
  if(k==VersionKind.TEMPLATE)node.path("blocks").forEach(b->((com.fasterxml.jackson.databind.node.ObjectNode)b).remove("id"));
  return node;
 }
 public long revision(VersionKind k,long id,boolean published){
  if(k==VersionKind.POST){if(published)return cms.<Long>one("publishedRevision",id);return posts.find(id).revision();}
  if(k==VersionKind.PAGE){if(published)return cms.<PublishedPage>one("anyPagePublication",id).revision();return cms.<Page>one("page",id).revision();}
  return templates.get(id).revision();
 }
 public List<Section> blocks(String source){try{return json.readValue(source,new TypeReference<List<Section>>(){});}catch(Exception e){throw new BusinessException("블록 snapshot을 읽을 수 없습니다.");}}
 public <T>T read(JsonNode node,Class<T> type){try{return json.treeToValue(node,type);}catch(Exception e){throw new BusinessException("지원하지 않는 snapshot 형식입니다.");}}
 public JsonNode decode(VersionStore.Entry e){if(e.snapshotSchemaVersion()!=1)throw new BusinessException("지원하지 않는 snapshot 버전입니다.");try{return json.readTree(e.snapshotJson());}catch(Exception x){throw new BusinessException("버전 snapshot을 읽을 수 없습니다.");}}
 public Set<Long> media(VersionKind kind,JsonNode snapshot){
  var ids=new LinkedHashSet<Long>();
  if(kind==VersionKind.POST){for(var id:snapshot.path("mediaIds"))add(ids,id);delta(ids,snapshot.path("richContent"));}
  else for(var b:snapshot.path(kind==VersionKind.PAGE?"sections":"blocks")){if(!b.path("imageId").isMissingNode()&&!b.path("imageId").isNull())add(ids,b.path("imageId"));delta(ids,b.path("bodyDoc"));}
  return ids;
 }
 private void delta(Set<Long> ids,JsonNode source){
  if(source.isMissingNode()||source.isNull()||source.asText().isBlank())return;
  try{var doc=json.readTree(source.asText());if(!doc.path("ops").isArray())throw new IllegalArgumentException();
   for(var op:doc.path("ops"))for(String key:List.of("aicaImage","aicaFile"))if(op.path("insert").has(key))add(ids,op.path("insert").path(key).path("id"));
  }catch(Exception e){throw new BusinessException("버전 미디어 참조를 읽을 수 없습니다. 저장하지 않았습니다.");}
 }
 private void add(Set<Long> ids,JsonNode id){if(!id.isIntegralNumber()||!id.canConvertToLong()||id.asLong()<=0)throw new BusinessException("버전 미디어 ID를 확인하세요.");ids.add(id.asLong());}
}

