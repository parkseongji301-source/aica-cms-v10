package egovframework.backoffice.mvp.cms;
import egovframework.backoffice.mvp.version.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.common.*;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import static egovframework.backoffice.mvp.cms.CmsModels.*;
import static egovframework.backoffice.mvp.cms.CmsStore.values;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PageTemplateService {
 public record Stored(long id,String name,String description,boolean active,String blocksJson,long revision,long createdBy,long updatedBy,LocalDateTime createdAt,LocalDateTime updatedAt,String creatorName,String updaterName) {}
 public record Summary(long id,String name,String description,boolean active,int blockCount,long revision,long createdBy,long updatedBy,LocalDateTime createdAt,LocalDateTime updatedAt,String creatorName,String updaterName) {}
 public record Document(Summary info,JsonNode blocks,List<SelectedPost> references) {}
 public record Prepared(long templateId,long revision,List<Section> sections,List<SelectedPost> references) {}
 private final PageTemplateStore templates;private final CmsStore guard;private final CmsAccess access;private final PageService pages;private final ObjectMapper json;private final ActivityService audit;
 private final VersionHistoryService history;
 public PageTemplateService(PageTemplateStore templates,CmsStore guard,CmsAccess access,PageService pages,ObjectMapper json,ActivityService audit,VersionHistoryService history){this.templates=templates;this.guard=guard;this.access=access;this.pages=pages;this.json=json;this.audit=audit;this.history=history;}
 public static boolean canManage(Role role){return role==Role.SUPER_ADMIN;}
 public static boolean canUse(Role role){return role==Role.SUPER_ADMIN;}
 private Account requireManage(AccountPrincipal actor){var user=access.actor(actor);if(!canManage(user.role()))throw new AccessDeniedException("공용 템플릿 관리 권한이 없습니다.");return user;}
 private void requireUse(AccountPrincipal actor){if(!canUse(access.actor(actor).role()))throw new AccessDeniedException("공용 템플릿 사용 권한이 없습니다.");}
 private Stored required(long id){var found=templates.get(id);if(found==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"공용 템플릿을 찾을 수 없습니다.");return found;}
 private void revision(Stored t,Long revision){if(revision==null)throw new BusinessException("템플릿 버전을 확인하세요.");if(t.revision()!=revision)throw new ResponseStatusException(HttpStatus.CONFLICT,"템플릿이 변경되었습니다. 다시 조회한 뒤 적용하세요.");}
 private String source(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new BusinessException("템플릿 구성을 읽을 수 없습니다.");}}
 private JsonNode tree(String source){try{return json.readTree(source);}catch(Exception e){throw new BusinessException("템플릿 구성을 읽을 수 없습니다.");}}
 private Summary summary(Stored t){return new Summary(t.id(),t.name(),t.description(),t.active(),pages.sections(t.blocksJson()).size(),t.revision(),t.createdBy(),t.updatedBy(),t.createdAt(),t.updatedAt(),t.creatorName(),t.updaterName());}
 private List<Section> fresh(List<Section> blocks){
  if(blocks==null||blocks.size()>30||blocks.stream().anyMatch(Objects::isNull))throw new BusinessException("템플릿 블록은 최대 30개입니다.");
  return blocks.stream().map(PageBlockService::copyWithNewId).toList();
 }
 private String withoutPageIds(List<Section> blocks){var nodes=json.valueToTree(blocks);nodes.forEach(n->((ObjectNode)n).remove("id"));return source(nodes);}
 private List<SelectedPost> references(AccountPrincipal actor,List<Section> blocks){
  var result=new LinkedHashMap<Long,SelectedPost>();
  for(var block:blocks)if(block.manual()!=null)for(var post:pages.selectedPosts(actor,block.manual().postIds()))result.putIfAbsent(post.id(),post);
  return List.copyOf(result.values());
 }
 @Transactional(readOnly=true) public List<Summary> list(AccountPrincipal actor){requireUse(actor);return templates.list().stream().map(this::summary).toList();}
 @Transactional(readOnly=true) public Document get(AccountPrincipal actor,long id){requireUse(actor);var t=required(id);return new Document(summary(t),tree(t.blocksJson()),references(actor,pages.sections(t.blocksJson())));}
 @Transactional public Document save(AccountPrincipal actor,Long id,Long revision,String name,String description,boolean active,List<Section> blocks){
  return saveInternal(actor,id,revision,name,description,active,blocks,true);
 }
 @Transactional public Document save(AccountPrincipal actor,Long id,Long revision,String name,String description,boolean active,List<Section> blocks,SaveIntent intent){
  return saveInternal(actor,id,revision,name,description,active,blocks,intent==SaveIntent.MANUAL_DRAFT);
 }
 @Transactional public Document restoreDraft(AccountPrincipal actor,long id,long revision,String name,String description,boolean active,List<Section> blocks){
  return saveInternal(actor,id,revision,name,description,active,blocks,false);
 }
 private Document saveInternal(AccountPrincipal actor,Long id,Long revision,String name,String description,boolean active,List<Section> blocks,boolean recordVersion){
  var user=requireManage(actor);guard.lock();var old=id==null?null:required(id);if(old!=null)revision(old,revision);
  if(old==null&&blocks==null)throw new BusinessException("저장할 블록 구성이 필요합니다.");
  if(blocks!=null&&blocks.stream().anyMatch(b->b!=null&&b.categoryId()!=null))throw new BusinessException("레거시 카테고리는 새로 지정할 수 없습니다. 콘텐츠 유형·주제 조건을 사용하세요.");
  String stored=blocks==null?old.blocksJson():withoutPageIds(pages.previewSections(actor,source(fresh(blocks))));
  var values=values("id",id,"name",InputRules.text(name,150,"템플릿 이름"),"description",CmsRules.optional(description,1000,"설명"),"active",active,"blocksJson",stored,"actorId",user.id());
  if(id==null)id=templates.insert(values);else templates.edit(values);
  audit.record(user,old==null?"공용 템플릿 생성":"공용 템플릿 수정","템플릿 #"+id,name);
  if(recordVersion)history.capture(user,VersionKind.TEMPLATE,id,"MANUAL_DRAFT",null,null,false);
  return get(actor,id);
 }
 /** Read only: the caller inserts these copies into its editor, then uses the normal page draft save. */
 @Transactional(readOnly=true) public Prepared prepare(AccountPrincipal actor,long id,Long revision){
  requireUse(actor);var t=required(id);revision(t,revision);if(!t.active())throw new BusinessException("비활성 템플릿은 불러올 수 없습니다.");
  var blocks=pages.previewSections(actor,source(fresh(pages.sections(t.blocksJson()))));
  return new Prepared(id,t.revision(),blocks,references(actor,blocks));
 }
}
