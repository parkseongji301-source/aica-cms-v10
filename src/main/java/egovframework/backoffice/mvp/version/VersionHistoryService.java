package egovframework.backoffice.mvp.version;
import com.fasterxml.jackson.databind.JsonNode;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.post.*;
import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.security.*;
import egovframework.backoffice.mvp.common.BusinessException;
import java.util.*;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;
import static egovframework.backoffice.mvp.cms.CmsStore.values;

@Service
public class VersionHistoryService {
 public record Summary(long id,String kind,long targetId,String reason,long sourceRevision,Long sourceVersionId,long createdBy,String creatorName,LocalDateTime createdAt,boolean baseline){}
 public record Listing(List<Summary> items,long total,int page,boolean canRestore){}
 public record Detail(Summary version,JsonNode snapshot,JsonNode current,long currentRevision,boolean canRestore){}
 private final VersionStore versions;private final VersionSnapshots snapshots;private final CmsStore cms;private final CmsAccess access;
 private final PostMapper posts;private final PageTemplateStore templates;private final AccessPolicy policy;private final ActivityService audit;
 private final int postKeep,pageKeep,templateKeep;
 public VersionHistoryService(VersionStore versions,VersionSnapshots snapshots,CmsStore cms,CmsAccess access,PostMapper posts,PageTemplateStore templates,AccessPolicy policy,ActivityService audit,
  @Value("${backoffice.versions.post-draft-limit:20}") int postKeep,@Value("${backoffice.versions.page-draft-limit:20}") int pageKeep,@Value("${backoffice.versions.template-limit:20}") int templateKeep){
  this.versions=versions;this.snapshots=snapshots;this.cms=cms;this.access=access;this.posts=posts;this.templates=templates;this.policy=policy;this.audit=audit;
  if(postKeep<2||pageKeep<2||templateKeep<2)throw new IllegalArgumentException("Version retention must keep at least the restore pair");
  this.postKeep=postKeep;this.pageKeep=pageKeep;this.templateKeep=templateKeep;
 }
 public Account authorize(AccountPrincipal p,VersionKind k,long id,boolean restore){
  var actor=access.actor(p);
  if(k==VersionKind.POST){var post=posts.find(id);if(post==null)throw missing();policy.requirePostAccess(actor,post.authorId());if(restore&&!policy.canManageAllPosts(actor.role()))throw new AccessDeniedException("이전 버전 복구는 관리자만 가능합니다.");}
  else if(k==VersionKind.PAGE){access.manager(p);if(cms.one("page",id)==null)throw missing();}
  else{access.operator(p);if(templates.get(id)==null)throw missing();}
  return actor;
 }
 public boolean canRestore(Account a,VersionKind k){return k==VersionKind.TEMPLATE?a.role()==Role.SUPER_ADMIN:policy.canManageAllPosts(a.role());}
 private Summary summary(VersionKind k,VersionStore.Entry e){return new Summary(e.id(),k.route,e.targetId(),e.reason(),e.sourceRevision(),e.sourceVersionId(),e.createdBy(),e.creatorName(),e.createdAt(),e.reason().startsWith("BASELINE_"));}
 @Transactional(readOnly=true) public Listing list(AccountPrincipal p,VersionKind k,long id,int page){
  var a=authorize(p,k,id,false);if(page<0||page>100000)throw new BusinessException("이력 페이지를 확인하세요.");
  return new Listing(versions.list(k,id,page*20).stream().map(e->summary(k,e)).toList(),versions.count(k,id),page,canRestore(a,k));
 }
 public VersionStore.Entry required(VersionKind k,long id,long version){var e=versions.get(k,id,version);if(e==null)throw missing();return e;}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Detail detail(AccountPrincipal p,VersionKind k,long id,long version){
  var a=authorize(p,k,id,false);var e=required(k,id,version);
  return new Detail(summary(k,e),snapshots.decode(e),snapshots.capture(k,id,false),snapshots.revision(k,id,false),canRestore(a,k));
 }
 public static String operation(String value){
  if(value==null||value.isBlank())return null;
  try{return UUID.fromString(value).toString();}catch(Exception e){throw new BusinessException("작업 식별자를 확인하세요.");}
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public long capture(Account actor,VersionKind k,long id,String reason,Long source,String operation,boolean published){
  operation=operation(operation);var prior=versions.operation(k,id,operation,reason);if(prior!=null)return prior.id();
  JsonNode snapshot=snapshots.capture(k,id,published);long revision=snapshots.revision(k,id,published);
  long activity=audit.record(actor,"버전 기록",k.route+" #"+id,reason+" · revision "+revision+(source==null?"":" · source version #"+source));
  long version=versions.insert(k,id,values("snapshot",snapshot.toString(),"reason",reason,"revision",revision,"source",source,"operation",operation,"actorId",actor.id(),"actorName",actor.displayName(),"activity",activity));
  for(long media:snapshots.media(k,snapshot))versions.media(k,version,media);
  prune(k,id);return version;
 }
 @Transactional(propagation=Propagation.MANDATORY) public void prune(VersionKind k,long id){versions.prune(k,id,k==VersionKind.POST?postKeep:k==VersionKind.PAGE?pageKeep:templateKeep);}
 @Transactional(propagation=Propagation.MANDATORY) public void purge(VersionKind k,long id){versions.purge(k,id);}
 @Transactional public Map<String,Integer> baseline(AccountPrincipal principal){
  var a=access.operator(principal);cms.lock();var result=new LinkedHashMap<String,Integer>();
  if(versions.baselineComplete())return Map.of("posts",0,"pages",0,"page-templates",0);
  for(var kind:VersionKind.values()){
   List<Long> ids=cms.all(kind==VersionKind.POST?"versionPostIds":kind==VersionKind.PAGE?"versionPageIds":"versionTemplateIds",null);int added=0;
   for(long id:ids){
    String reason=kind==VersionKind.TEMPLATE?"BASELINE_TEMPLATE":"BASELINE_DRAFT";
    if(!versions.hasReason(kind,id,reason)){capture(a,kind,id,reason,null,null,false);added++;}
    boolean publication=kind==VersionKind.POST?cms.one("versionPostPublication",id)!=null:kind==VersionKind.PAGE&&cms.one("anyPagePublication",id)!=null;
    if(publication&&!versions.hasReason(kind,id,"BASELINE_PUBLISHED")){capture(a,kind,id,"BASELINE_PUBLISHED",null,null,true);added++;}
   }
   result.put(kind.route,added);
  }
  versions.markBaseline(a.id());return result;
 }
 private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"대상 또는 버전을 찾을 수 없습니다.");}
}

