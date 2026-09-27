package egovframework.backoffice.mvp.version;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
@Component
public class VersionMediaReferences {
 public record FileImpact(long id,String name){}
 public record Impact(long versionCount,List<FileImpact> files){}
 private final VersionStore versions;private final VersionHistoryService history;private final CmsStore cms;
 public VersionMediaReferences(VersionStore versions,VersionHistoryService history,CmsStore cms){this.versions=versions;this.history=history;this.cms=cms;}
 public boolean used(long media){return Arrays.stream(VersionKind.values()).anyMatch(k->!versions.uses(k,media).isEmpty());}
 public List<UsageService.Usage> uses(AccountPrincipal actor,long media){
  var result=new ArrayList<UsageService.Usage>();boolean restricted=false;
  for(var kind:VersionKind.values())for(var use:versions.uses(kind,media)){
   try{history.authorize(actor,kind,use.targetId(),false);}
   catch(AccessDeniedException e){restricted=true;continue;}
   String label=(kind==VersionKind.POST?"콘텐츠":kind==VersionKind.PAGE?"페이지":"공용 템플릿")+" #"+use.targetId()+" · 과거 버전 #"+use.versionId()+" · "+use.reason();
   String href=kind==VersionKind.TEMPLATE?"/admin-next/design/templates?template="+use.targetId()+"&history="+use.versionId():"/admin-next/"+kind.route+"/"+use.targetId()+"/edit?history="+use.versionId();
   result.add(new UsageService.Usage(label,href));
  }
  if(restricted)result.add(new UsageService.Usage("접근 권한이 없는 대상의 과거 버전에서 사용 중",null));
  return List.copyOf(result);
 }
 public Impact impact(VersionKind kind,long id){
  return new Impact(versions.count(kind,id),versions.mediaIds(kind,id).stream().map(media->{var m=cms.<CmsModels.Media>one("media",media);return new FileImpact(media,m==null?"사용 불가 파일":m.name());}).toList());
 }
}

