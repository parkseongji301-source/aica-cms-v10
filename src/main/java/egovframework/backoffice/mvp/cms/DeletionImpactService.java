package egovframework.backoffice.mvp.cms;

import egovframework.backoffice.mvp.security.AccountPrincipal;
import egovframework.backoffice.mvp.post.PostService;
import egovframework.backoffice.mvp.common.BusinessException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static egovframework.backoffice.mvp.cms.CmsModels.*;

@Service
public class DeletionImpactService {
 public record Impact(String kind,long id,String title,long revision,List<UsageService.Usage> uses,String consequence,egovframework.backoffice.mvp.version.VersionMediaReferences.Impact history) {}
 private final egovframework.backoffice.mvp.version.VersionMediaReferences versionMedia;
 private final CmsAccess access;private final CmsStore store;private final PageService pages;private final PostService posts;
 private final PageTemplateStore templates;private final PublishedPostQueryService queries;private final UsageService usages;
 public DeletionImpactService(CmsAccess access,CmsStore store,PageService pages,PostService posts,PageTemplateStore templates,PublishedPostQueryService queries,UsageService usages,egovframework.backoffice.mvp.version.VersionMediaReferences versionMedia){
  this.versionMedia=versionMedia;
  this.access=access;this.store=store;this.pages=pages;this.posts=posts;this.templates=templates;this.queries=queries;this.usages=usages;
 }
 @Transactional(readOnly=true)
 public Impact get(AccountPrincipal actor,String kind,long id) {
  access.permanentDelete(actor);
  if(kind.equals("media")) {CmsModels.Media m=store.one("media",id);if(m==null)throw new BusinessException("파일을 찾을 수 없습니다.");return new Impact(kind,id,m.name(),0,usages.find(actor,kind,id),"파일 원본과 메타데이터를 영구 삭제합니다. 사용 중인 파일은 삭제할 수 없습니다. 복구 기능은 없습니다.",new egovframework.backoffice.mvp.version.VersionMediaReferences.Impact(0,List.of()));}
  if(kind.equals("pages")) {var p=pages.get(actor,id);return new Impact(kind,id,p.title(),p.revision(),usages.find(actor,kind,id),
    "페이지 초안과 발행본을 영구 삭제합니다. 메뉴·첫 화면 연결이 있으면 삭제할 수 없습니다. 본문·버튼에 직접 입력한 URL과 외부 링크는 별도 확인이 필요합니다. 복구 기능은 없습니다.",versionMedia.impact(egovframework.backoffice.mvp.version.VersionKind.PAGE,id));}
  if(!kind.equals("posts"))throw new BusinessException("삭제 대상을 확인하세요.");
  var p=posts.get(actor,id);var found=new ArrayList<UsageService.Usage>();
  for(Page page:pages.list(actor)) {
   collect(found,pages.sections(page.sectionsJson()),id,"페이지 초안 · "+page.title(),"/admin/pages/"+page.id()+"/edit");
   PublishedPage pub=store.one("anyPagePublication",page.id());
   if(pub!=null)collect(found,pages.sections(pub.sectionsJson()),id,"페이지 발행본 · "+pub.title(),"/admin/pages/"+page.id()+"/edit");
  }
  for(var t:templates.list())collect(found,pages.sections(t.blocksJson()),id,"템플릿 · "+t.name()+(t.active()?" · 활성":" · 비활성"),"/admin/design/templates");
  return new Impact(kind,id,p.title(),p.revision(),List.copyOf(found),
    "콘텐츠를 휴지통으로 이동하고 공개 목록에서 제외합니다. 본문·분류·첨부·맛집 상세·버전 이력을 보관하며, 휴지통에서 임시보관으로 복원할 수 있습니다. 직접 선택 ID는 남아 사용 불가로 표시됩니다. 영구삭제는 휴지통에서 별도로 실행합니다.",versionMedia.impact(egovframework.backoffice.mvp.version.VersionKind.POST,id));
 }
 private void collect(List<UsageService.Usage> result,List<Section> blocks,long id,String label,String href){
  for(var b:blocks)if("POSTS".equals(b.type())){
   boolean manual=b.manual()!=null&&b.manual().postIds().contains(id);
   if(manual||queries.matches(b,id))result.add(new UsageService.Usage(label+" / "+Objects.toString(b.heading(),b.type())+" ["+Objects.toString(b.id(),"템플릿 블록")+"]"+(manual?" · 직접 선택 참조":" · 현재 발행 조건 일치"),href));
  }
 }
}
