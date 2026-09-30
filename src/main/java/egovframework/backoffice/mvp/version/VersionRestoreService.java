package egovframework.backoffice.mvp.version;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.post.PostService;
import egovframework.backoffice.mvp.classification.ClassificationModels.Selection;
import egovframework.backoffice.mvp.restaurant.RestaurantDetailsService.Details;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import egovframework.backoffice.mvp.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** All three writes (backup, draft, restored snapshot) share the caller's transaction and CMS lock. */
@Service
public class VersionRestoreService {
 public record Result(long versionId,long revision,Map<String,String> blockIds,boolean alreadyApplied){}
 private final VersionHistoryService history;private final VersionStore versions;private final VersionSnapshots snapshots;
 private final CmsStore guard;private final PostService posts;private final PageService pages;private final PageTemplateService templates;private final PageBlockService blocks;private final ObjectMapper json;
 public VersionRestoreService(VersionHistoryService history,VersionStore versions,VersionSnapshots snapshots,CmsStore guard,PostService posts,PageService pages,PageTemplateService templates,PageBlockService blocks,ObjectMapper json){
  this.history=history;this.versions=versions;this.snapshots=snapshots;this.guard=guard;this.posts=posts;this.pages=pages;this.templates=templates;this.blocks=blocks;this.json=json;
 }
 @Transactional
 public Result restore(AccountPrincipal principal,VersionKind kind,long id,long version,Long expectedRevision,String operation){
  var actor=history.authorize(principal,kind,id,true);guard.lock();operation=VersionHistoryService.operation(operation);
  var repeated=versions.operation(kind,id,operation,"RESTORE");
  if(repeated!=null){
   if(!Objects.equals(repeated.sourceVersionId(),version))throw new BusinessException("다른 복구에 사용한 작업 식별자입니다.");
   return new Result(repeated.id(),snapshots.revision(kind,id,false),Map.of(),true);
  }
  CmsRules.revision(snapshots.revision(kind,id,false),expectedRevision);
  var source=history.required(kind,id,version);var payload=snapshots.decode(source);
  // Read the source first: retention during capture may remove a non-baseline source.
  history.capture(actor,kind,id,"RESTORE_BACKUP",version,operation,false);
  Map<String,String> changed=Map.of();
  if(kind==VersionKind.POST){
   var p=snapshots.read(payload,VersionSnapshots.PostSnapshot.class);var c=p.classification();
   var details=p.restaurant()==null?new Details(""):p.restaurant();
   // Legacy categories are retired: a snapshot's category is kept only if the post still has that same category.
   Long category=Objects.equals(p.categoryId(),posts.get(principal,id).categoryId())?p.categoryId():null;
   posts.save(principal,id,expectedRevision,p.title(),p.content(),category,p.mediaIds(),"save",p.richContent(),new Selection(c.typeCode(),c.cohortIds(),c.topicIds()),details,SaveIntent.LEGACY);
  }else if(kind==VersionKind.PAGE){
   var p=snapshots.read(payload,VersionSnapshots.PageSnapshot.class);var current=pages.get(principal,id);
   var copies=blocks.restore(id,pages.sections(current.sectionsJson()),p.sections());changed=copies.changedIds();
   try{pages.restoreDraft(principal,id,expectedRevision,p.title(),json.writeValueAsString(copies.sections()));}
   catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new BusinessException("블록을 복구할 수 없습니다.");}
  }else{
   var t=snapshots.read(payload,VersionSnapshots.TemplateSnapshot.class);
   templates.restoreDraft(principal,id,expectedRevision,t.name(),t.description(),t.active(),t.blocks());
  }
  long restored=history.capture(actor,kind,id,"RESTORE",version,operation,false);
  return new Result(restored,snapshots.revision(kind,id,false),changed,false);
 }
}

