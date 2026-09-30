package egovframework.backoffice.mvp.cms;

import com.fasterxml.jackson.databind.ObjectMapper;
import egovframework.backoffice.mvp.common.*;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static egovframework.backoffice.mvp.cms.CmsStore.values;

@Service
public class WritingTemplateService {
 public record Stored(long id,String name,String description,String typeCode,String richContent,String content,long revision,LocalDateTime createdAt,LocalDateTime updatedAt,String updaterName) {}
 public record Summary(long id,String name,String description,String typeCode,long revision,LocalDateTime updatedAt,String updaterName) {}
 public record Document(Summary info,String richContent,String content,String bodyHtml) {}
 private final WritingTemplateStore templates;private final CmsStore guard;private final CmsAccess access;
 private final RichTextService rich;private final ObjectMapper json;private final ActivityService audit;
 public WritingTemplateService(WritingTemplateStore templates,CmsStore guard,CmsAccess access,RichTextService rich,ObjectMapper json,ActivityService audit){this.templates=templates;this.guard=guard;this.access=access;this.rich=rich;this.json=json;this.audit=audit;}
 private Stored required(long id){var value=templates.get(id);if(value==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"삭제되었거나 찾을 수 없는 글쓰기 템플릿입니다.");return value;}
 private void revision(Stored value,Long revision){if(revision==null)throw new BusinessException("템플릿 버전을 확인하세요.");if(value.revision()!=revision)throw new ResponseStatusException(HttpStatus.CONFLICT,"템플릿이 변경되었습니다. 다시 열어 확인해 주세요.");}
 private Summary summary(Stored t){return new Summary(t.id(),t.name(),t.description(),t.typeCode(),t.revision(),t.updatedAt(),t.updaterName());}
 private Document document(Stored t){return new Document(summary(t),t.richContent(),t.content(),rich.html(t.richContent(),t.content()));}
 @Transactional(readOnly=true) public List<Summary> list(AccountPrincipal actor){access.actor(actor);return templates.list().stream().map(this::summary).toList();}
 @Transactional(readOnly=true) public Document get(AccountPrincipal actor,long id){access.actor(actor);return document(required(id));}
 @Transactional(readOnly=true) public Document prepare(AccountPrincipal actor,long id,Long revision){access.actor(actor);var t=required(id);revision(t,revision);return document(t);}
 private RichTextService.Document validate(AccountPrincipal actor,String source){
  if(source==null||source.isBlank()||source.length()>250000)throw new BusinessException("템플릿 본문을 입력하세요. 최대 20,000자까지 저장할 수 있습니다.");
  try{var root=json.readTree(source);for(var op:root.path("ops")){var insert=op.path("insert");if(insert.has("aicaImage")||insert.has("aicaFile"))throw new BusinessException("템플릿에는 글과 서식을 저장하세요. 사진·첨부는 후기 작성 시 넣을 수 있습니다.");}}
  catch(BusinessException error){throw error;}catch(Exception error){throw new BusinessException("템플릿 본문 형식을 확인하세요.");}
  var checked=rich.validate(actor,source);
  if(checked.text().isBlank())throw new BusinessException("템플릿 본문을 입력하세요.");
  return checked;
 }
 @Transactional public Document save(AccountPrincipal principal,Long id,Long revision,String name,String description,String source){
  var actor=access.structure(principal);guard.lock();if(id!=null)revision(required(id),revision);
  var label=InputRules.text(name,150,"템플릿 이름");var note=CmsRules.optional(description,1000,"설명");var body=validate(principal,source);
  var fields=values("id",id,"name",label,"description",note,"richContent",body.json(),"content",body.text(),"actorId",actor.id());
  boolean created=id==null;if(created)id=templates.create(fields);else templates.edit(fields);
  audit.record(actor,created?"글쓰기 템플릿 추가":"글쓰기 템플릿 수정","글쓰기 템플릿 #"+id,label);
  return document(required(id));
 }
 @Transactional public void remove(AccountPrincipal principal,long id,Long revision,boolean confirmed){
  var actor=access.structure(principal);guard.lock();var t=required(id);revision(t,revision);
  if(!confirmed)throw new BusinessException("삭제할 템플릿을 확인해 주세요.");
  templates.remove(id);audit.record(actor,"글쓰기 템플릿 삭제","글쓰기 템플릿 #"+id,t.name());
 }
}
