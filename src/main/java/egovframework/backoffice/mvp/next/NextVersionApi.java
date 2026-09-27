package egovframework.backoffice.mvp.next;
import egovframework.backoffice.mvp.version.*;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import egovframework.backoffice.mvp.common.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/next")
public class NextVersionApi {
 private final VersionHistoryService history;private final VersionRestoreService restore;private final VersionStore versions;private final VersionSnapshots snapshots;
 private final CmsStore cms;private final RichTextService rich;
 public NextVersionApi(VersionHistoryService history,VersionRestoreService restore,VersionStore versions,VersionSnapshots snapshots,CmsStore cms,RichTextService rich){
  this.history=history;this.restore=restore;this.versions=versions;this.snapshots=snapshots;this.cms=cms;this.rich=rich;
 }
 public record RestoreInput(Long expectedRevision,String operationId,boolean confirmed){}
 public record Confirm(boolean confirmed){}
 public record Preview(String currentHtml,String versionHtml,String warning){}
 @ModelAttribute public void noStore(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
 @GetMapping("/{kind:posts|pages|page-templates}/{id}/versions")
 public VersionHistoryService.Listing list(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable String kind,@PathVariable long id,@RequestParam(defaultValue="0") int page){return history.list(actor,VersionKind.route(kind),id,page);}
 @GetMapping("/{kind:posts|pages|page-templates}/{id}/versions/{version}")
 public VersionHistoryService.Detail detail(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable String kind,@PathVariable long id,@PathVariable long version){return history.detail(actor,VersionKind.route(kind),id,version);}
 @PostMapping("/{kind:posts|pages|page-templates}/{id}/versions/{version}/restore")
 public VersionRestoreService.Result restore(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable String kind,@PathVariable long id,@PathVariable long version,@RequestBody RestoreInput input){
  if(!input.confirmed())throw new BusinessException("복구 내용을 확인하세요.");
  return restore.restore(actor,VersionKind.route(kind),id,version,input.expectedRevision(),input.operationId());
 }
 @PostMapping("/page-templates/{id}/versions/{version}/prepare-restore")
 public VersionHistoryService.Detail prepare(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@PathVariable long version){
  history.authorize(actor,VersionKind.TEMPLATE,id,true);return history.detail(actor,VersionKind.TEMPLATE,id,version);
 }
 @PostMapping("/version-baseline")
 public Map<String,Integer> baseline(@AuthenticationPrincipal AccountPrincipal actor,@RequestBody Confirm input){
  if(!input.confirmed())throw new BusinessException("버전 기능 도입 기준점 생성을 확인하세요.");return history.baseline(actor);
 }
 @GetMapping("/{kind:posts|pages|page-templates}/{id}/versions/{version}/preview")
 public Preview preview(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable String kind,@PathVariable long id,@PathVariable long version){
  var k=VersionKind.route(kind);var d=history.detail(actor,k,id,version);
  if(k!=VersionKind.POST)return new Preview("","","페이지·템플릿은 블록 구성 비교를 사용합니다. 과거 홈페이지 전체를 재현하지 않습니다.");
  var old=snapshots.read(d.snapshot(),VersionSnapshots.PostSnapshot.class);var current=snapshots.read(d.current(),VersionSnapshots.PostSnapshot.class);
  try{return new Preview(rich.html(current.richContent(),current.content()),rich.html(old.richContent(),old.content(),media->"/api/admin/next/"+kind+"/"+id+"/versions/"+version+"/media/"+media),"");}
  catch(BusinessException e){return new Preview("","",e.getMessage()+" 본문 텍스트와 설정으로 확인하세요.");}
 }
 @GetMapping("/{kind:posts|pages|page-templates}/{id}/versions/{version}/media/{mediaId}")
 public ResponseEntity<byte[]> file(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable String kind,@PathVariable long id,@PathVariable long version,@PathVariable long mediaId){
  var k=VersionKind.route(kind);history.authorize(actor,k,id,false);history.required(k,id,version);
  if(!versions.references(k,id,version,mediaId))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
  var file=cms.<CmsModels.MediaFile>one("mediaFile",mediaId);var meta=cms.<CmsModels.Media>one("media",mediaId);
  if(file==null||meta==null)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
  var response=ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType(file.mime()));
  if(!file.mime().startsWith("image/"))response.header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(meta.name(),java.nio.charset.StandardCharsets.UTF_8).build().toString());
  return response.body(file.data());
 }
}

