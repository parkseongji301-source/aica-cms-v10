package egovframework.backoffice.mvp.cms;
import egovframework.backoffice.mvp.common.*;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import static egovframework.backoffice.mvp.cms.CmsStore.values;
import static egovframework.backoffice.mvp.cms.CmsModels.*;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
@Service
public class MediaService {
 private final CmsStore store;
 private final CmsAccess access;
 private final ActivityService audit;
 private final TemplateReferences templates;
 private final egovframework.backoffice.mvp.version.VersionMediaReferences versionMedia;
 public MediaService(CmsStore store,CmsAccess access,ActivityService audit,TemplateReferences templates,egovframework.backoffice.mvp.version.VersionMediaReferences versionMedia) {this.versionMedia=versionMedia;this.store=store;this.access=access;this.audit=audit;this.templates=templates;}
 @Transactional(readOnly=true)
 public List<Media> list(AccountPrincipal actor,String query) {
  String q=CmsRules.optional(query,100,"검색어").toLowerCase(Locale.ROOT);
  return store.all("mediaList",values("ownerId",access.owner(actor),"search",q.isEmpty()?null:"%"+q.replace("!","!!").replace("%","!%").replace("_","!_")+"%"));
 }
 public Media required(long id) {
  Media media=store.one("media",id);
  if(media==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"미디어를 찾을 수 없습니다.");
  return media;
 }
 public void validate(AccountPrincipal actor,List<Long> ids) {
  for(long id:CmsRules.ids(ids,12)) access.media(actor,required(id).ownerId());
 }
 @Transactional
 public long upload(AccountPrincipal principal,MultipartFile file,String alt) {
  var actor=access.actor(principal);
  if(file==null || file.isEmpty() || file.getSize()>5*1024*1024)
   throw new BusinessException("5MB 이하의 이미지 또는 문서 파일을 선택하세요.");
  String name=InputRules.text(Objects.toString(file.getOriginalFilename(),"image"),200,"파일 이름");
  String description=CmsRules.optional(alt,300,"대체 텍스트");
  String extension=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
  if(Set.of("pdf","txt","docx","xlsx","pptx","hwp").contains(extension)) {
   try {
    byte[] data=file.getBytes();String mime=documentMime(extension,data);
    long id=store.create("createMedia",values("name",name,"alt",description,"mime",mime,"width",0,"height",0,"byteSize",data.length,"data",data,"ownerId",actor.id()));
    audit.record(actor,"미디어 업로드","미디어 #"+id,name);return id;
   } catch(IOException error) {throw new BusinessException("문서 파일을 읽을 수 없습니다.");}
  }
  try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
   var readers=ImageIO.getImageReaders(stream);
   if(!readers.hasNext()) throw new BusinessException("올바른 JPG 또는 PNG 이미지가 아닙니다.");
   var reader=readers.next();
   try {
    reader.setInput(stream,true,true);
    String format=reader.getFormatName().toLowerCase(Locale.ROOT);
    if(!Set.of("jpeg","jpg","png").contains(format)) throw new BusinessException("JPG와 PNG 형식만 지원합니다.");
    int w=reader.getWidth(0),h=reader.getHeight(0);
    if(w<=0 || h<=0 || (long)w*h>16000000L) throw new BusinessException("이미지는 1,600만 화소 이하로 올려 주세요.");
    var image=reader.read(0);
    var output=new ByteArrayOutputStream();
    String encoding=format.equals("png")?"png":"jpeg";
    if(!ImageIO.write(image,encoding,output)) throw new BusinessException("이미지를 처리할 수 없습니다.");
    byte[] data=output.toByteArray();
    if(data.length>10*1024*1024) throw new BusinessException("처리된 이미지가 너무 큽니다. 크기를 줄여 주세요.");
    long id=store.create("createMedia",values("name",name,"alt",description,"mime","image/"+encoding,"width",w,"height",h,"byteSize",data.length,"data",data,"ownerId",actor.id()));
    audit.record(actor,"미디어 업로드","미디어 #"+id,name);
    return id;
   } finally {reader.dispose();}
  } catch(IOException error) {throw new BusinessException("이미지를 읽을 수 없습니다. 다른 파일을 선택하세요.");}
 }
 private String documentMime(String extension,byte[] data) throws IOException {
  if(extension.equals("pdf") && data.length>=5 && new String(data,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))return "application/pdf";
  if(extension.equals("txt")) {
   try {java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(data));}
   catch(java.nio.charset.CharacterCodingException error) {throw new BusinessException("텍스트 파일은 UTF-8 형식으로 올려 주세요.");}
   for(byte b:data)if(b==0)throw new BusinessException("올바른 텍스트 파일이 아닙니다.");
   return "text/plain";
  }
  if(extension.equals("hwp") && data.length>=8 && java.util.HexFormat.of().formatHex(Arrays.copyOf(data,8)).equals("d0cf11e0a1b11ae1"))return "application/x-hwp";
  if(Set.of("docx","xlsx","pptx").contains(extension)) {
   String expected=Map.of("docx","word/document.xml","xlsx","xl/workbook.xml","pptx","ppt/presentation.xml").get(extension);
   boolean types=false,body=false;int count=0,total=0;
   try(var zip=new java.util.zip.ZipInputStream(new ByteArrayInputStream(data))) {
    java.util.zip.ZipEntry entry;byte[] buffer=new byte[8192];
    while((entry=zip.getNextEntry())!=null) {
     if(++count>2000)throw new BusinessException("문서 내부 항목이 너무 많습니다.");
     types|=entry.getName().equals("[Content_Types].xml");body|=entry.getName().equals(expected);
     int read;while((read=zip.read(buffer))!=-1) {total+=read;if(total>30*1024*1024)throw new BusinessException("압축 해제된 문서가 너무 큽니다.");}
    }
   }
   if(types && body)return "application/vnd.openxmlformats-officedocument."+Map.of("docx","wordprocessingml.document","xlsx","spreadsheetml.sheet","pptx","presentationml.presentation").get(extension);
  }
  throw new BusinessException("파일 내용과 확장자가 일치하는 PDF·TXT·DOCX·XLSX·PPTX·HWP 파일을 선택하세요.");
 }
 @Transactional
 public void edit(AccountPrincipal principal,long id,String name,String alt) {
  store.lock(); var actor=access.actor(principal); access.media(principal,required(id).ownerId());
  store.change("editMedia",values("id",id,"name",InputRules.text(name,200,"이름"),"alt",CmsRules.optional(alt,300,"대체 텍스트")));
  audit.record(actor,"미디어 정보 수정","미디어 #"+id,"이름·대체 텍스트 변경");
 }
 @Transactional
 public void delete(AccountPrincipal principal,long id) {
  access.permanentDelete(principal);
  store.lock(); var actor=access.actor(principal); access.media(principal,required(id).ownerId());
  if(store.<Long>one("mediaUsage",id)>0 || !templates.media(id).isEmpty() || versionMedia.used(id)) throw new BusinessException("사용 중인 파일입니다. 아래 사용처에서 연결을 해제한 후 삭제하세요.");
  store.change("deleteMedia",id); audit.record(actor,"미디어 삭제","미디어 #"+id,"");
 }
 @Transactional(readOnly=true)
 public MediaFile file(AccountPrincipal actor,long id) {
  access.media(actor,required(id).ownerId());
  MediaFile file=store.one("mediaFile",id);
  if(file==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  return file;
 }
}

