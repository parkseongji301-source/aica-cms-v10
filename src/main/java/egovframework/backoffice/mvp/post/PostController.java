package egovframework.backoffice.mvp.post;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.common.*;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import java.time.LocalDateTime;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller
// GET screens moved to /admin/legacy/posts (React owns /admin/posts); POST paths are unchanged.
public class PostController {
 private final PostService posts;private final SiteService site;private final MediaService media;private final RichTextService rich;private final CmsStore store;
 public PostController(PostService posts,SiteService site,MediaService media,RichTextService rich,CmsStore store) {this.posts=posts;this.site=site;this.media=media;this.rich=rich;this.store=store;}
 @ModelAttribute
 public void location(@RequestParam(required=false) String from,Model model) {model.addAttribute("returnTo",ListLocation.posts(from));}
 @GetMapping("/admin/legacy/posts")
 public String list(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(defaultValue="0") int page,
   @RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String status,@RequestParam(required=false) Long categoryId,Model model) {
  model.addAttribute("pageData",posts.list(actor,page,q,status,categoryId));model.addAttribute("query",q.strip());
  model.addAttribute("listUrl",org.springframework.web.util.UriComponentsBuilder.fromPath("/admin/legacy/posts").queryParam("q",q).queryParam("status",status).queryParam("page",page).queryParamIfPresent("categoryId",Optional.ofNullable(categoryId)).build().encode().toUriString());
  model.addAttribute("filtered",!q.isBlank() || !status.isBlank() || categoryId!=null);model.addAttribute("status",status);model.addAttribute("categoryId",categoryId);model.addAttribute("categories",site.categories());return "posts/list";
 }
 @GetMapping("/admin/legacy/posts/{id}")
 public String detail(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,Model model) {
  model.addAttribute("post",posts.get(actor,id));model.addAttribute("images",posts.attachments(actor,id));
  model.addAttribute("pending",posts.pending(actor,id));return "posts/detail";
 }
 @GetMapping("/admin/legacy/posts/new")
 public String createForm(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(required=false) Long categoryId,Model model) {
  fields(actor,model,null,null,"","",categoryId,List.of());return "posts/form";
 }
 @GetMapping("/admin/legacy/posts/{id}/edit")
 public String editForm(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,Model model) {
  var p=posts.get(actor,id);
  fields(actor,model,id,p.revision(),p.title(),p.content(),p.categoryId(),posts.attachments(actor,id).stream().map(CmsModels.Media::id).toList());
  model.addAttribute("richContent",p.richContent());model.addAttribute("postStatus",p.status());model.addAttribute("pending",p.pending());return "posts/form";
 }
 @PostMapping("/admin/posts")
 public String create(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam String title,@RequestParam String content,
  @RequestParam(required=false) Long categoryId,@RequestParam(required=false) List<Long> mediaIds,
  @RequestParam(defaultValue="save") String action,@RequestParam(required=false) String richContent,@RequestParam(required=false) String saveIntent,Model model,HttpServletResponse response,RedirectAttributes redirect) {
  return save(actor,null,null,title,content,categoryId,mediaIds,action,richContent,saveIntent,model,response,redirect);
 }
 @PostMapping("/admin/posts/{id}/edit")
 public String edit(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestParam(required=false) Long revision,
  @RequestParam String title,@RequestParam String content,@RequestParam(required=false) Long categoryId,
  @RequestParam(required=false) List<Long> mediaIds,@RequestParam(defaultValue="save") String action,@RequestParam(required=false) String richContent,@RequestParam(required=false) String saveIntent,Model model,HttpServletResponse response,RedirectAttributes redirect) {
  return save(actor,id,revision,title,content,categoryId,mediaIds,action,richContent,saveIntent,model,response,redirect);
 }
 private String save(AccountPrincipal actor,Long id,Long revision,String title,String content,Long categoryId,List<Long> mediaIds,
  String action,String richContent,@RequestParam(required=false) String saveIntent,Model model,HttpServletResponse response,RedirectAttributes redirect) {
  try {
   long saved=posts.save(actor,id,revision,title,content,categoryId,mediaIds,action,richContent,null,null,egovframework.backoffice.mvp.version.SaveIntent.request(saveIntent));
   redirect.addFlashAttribute("notice",action.equals("publish")?"발행본을 저장했습니다. 외부 홈페이지는 아직 연결되지 않았습니다.":"임시저장했습니다. 기존 발행본은 유지됩니다.");
   return "redirect:/admin/legacy/posts/"+saved;
  } catch(BusinessException error) {
   response.setStatus(400);model.addAttribute("formError",error.getMessage());model.addAttribute("richContent",richContent);
   fields(actor,model,id,revision,title,content,categoryId,mediaIds==null?List.of():mediaIds);return "posts/form";
  }
 }
 private void fields(AccountPrincipal actor,Model model,Long id,Long revision,String title,String content,Long categoryId,List<Long> mediaIds) {
  model.addAttribute("editing",id!=null);model.addAttribute("postId",id);model.addAttribute("revision",revision);
  model.addAttribute("title",title);model.addAttribute("content",content);model.addAttribute("categoryId",categoryId);
  model.addAttribute("classification",id==null?null:posts.classification(actor,id));
  model.addAttribute("restaurant",id==null?null:posts.restaurant(actor,id));
  if(!model.containsAttribute("postStatus")) {var saved=id==null?null:posts.get(actor,id);model.addAttribute("postStatus",saved==null?"DRAFT":saved.status());model.addAttribute("pending",saved!=null && saved.pending());}
  model.addAttribute("selectedMedia",mediaIds);model.addAttribute("categories",site.categories());model.addAttribute("mediaItems",media.list(actor,""));
 }
 @PostMapping("/admin/posts/save-json")
 @ResponseBody
 public org.springframework.http.ResponseEntity<?> saveJson(@AuthenticationPrincipal AccountPrincipal actor,
  @RequestParam(required=false) Long id,@RequestParam(required=false) Long revision,@RequestParam String title,
  @RequestParam(defaultValue="") String content,@RequestParam(required=false) String richContent,
  @RequestParam(required=false) Long categoryId,@RequestParam(defaultValue="save") String action,@RequestParam(required=false) String saveIntent,@RequestParam(required=false) List<Long> mediaIds) {
  try {
   if(id!=null && revision==null) throw new BusinessException("저장 버전을 확인할 수 없습니다. 다시 열어 주세요.");
   var p=posts.saveDocument(actor,id,revision,title,content,categoryId,mediaIds==null&&id!=null&&richContent==null?posts.attachments(actor,id).stream().map(CmsModels.Media::id).toList():mediaIds,action,richContent,null,null,egovframework.backoffice.mvp.version.SaveIntent.request(saveIntent));
   long saved=p.id();
   return org.springframework.http.ResponseEntity.ok(Map.of("id",saved,"revision",p.revision(),"status",p.status(),"pending",p.pending()));
  } catch(BusinessException error) {return org.springframework.http.ResponseEntity.badRequest().body(Map.of("error",error.getMessage()));}
 }
 @GetMapping("/admin/legacy/posts/{id}/publication")
 public String publication(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,Model model) {
  posts.get(actor,id); CmsModels.PublicPost post=store.one("publicPost",id);
  if(post==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
  model.addAttribute("pageTitle",post.title());model.addAttribute("bodyHtml",rich.html(post.richContent(),post.content()));
  model.addAttribute("restaurant",posts.publication(actor,id).restaurant());
  model.addAttribute("images",post.richContent()==null?store.all("publishedPostMedia",id):List.of());return "cms/preview";
 }
 @PostMapping("/admin/posts/preview")
 public String preview(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(required=false) Long id,
  @RequestParam String title,@RequestParam String content,@RequestParam(required=false) String richContent,
  @RequestParam(required=false) Long categoryId,@RequestParam(required=false) List<Long> mediaIds,Model model) {
  var preview=posts.preview(actor,id,title,content,richContent,mediaIds);
  model.addAttribute("pageTitle",preview.title());model.addAttribute("bodyHtml",preview.bodyHtml());
  model.addAttribute("images",preview.attachments());model.addAttribute("restaurant",preview.restaurant());return "cms/preview";
 }
 @PostMapping("/admin/posts/{id}/unpublish")
 public String unpublish(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestParam(required=false) Long revision,@RequestParam(required=false) String from,RedirectAttributes redirect) {
  try {posts.unpublish(actor,id,revision);redirect.addFlashAttribute("notice","비공개로 전환했습니다.");}catch(BusinessException error){redirect.addFlashAttribute("formError",error.getMessage());}
  return "redirect:/admin/legacy/posts/"+id+"/edit?from="+java.net.URLEncoder.encode(ListLocation.posts(from),java.nio.charset.StandardCharsets.UTF_8);
 }
 @PostMapping("/admin/posts/{id}/delete")
 public String delete(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestParam(required=false) Long revision,@RequestParam(defaultValue="false") boolean confirmed,@RequestParam(required=false) String from,RedirectAttributes redirect) {
  if(!confirmed)throw new BusinessException("휴지통 이동 영향을 확인하고 확인란을 선택하세요.");
  posts.delete(actor,id,revision);redirect.addFlashAttribute("notice","콘텐츠를 휴지통으로 이동했습니다. 휴지통에서 임시보관으로 복원할 수 있습니다.");return "redirect:"+ListLocation.posts(from);
 }
}
