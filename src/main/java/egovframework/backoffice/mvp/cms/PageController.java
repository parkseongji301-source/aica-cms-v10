package egovframework.backoffice.mvp.cms;
import egovframework.backoffice.mvp.common.BusinessException;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller
// GET screens moved to /admin/legacy/pages (React owns /admin/pages); POST paths are unchanged.
public class PageController {
 private final UsageService usages;private final PageService pages;private final SiteService site;private final MediaService media;
 public PageController(PageService pages,SiteService site,MediaService media,UsageService usages) {this.usages=usages;this.pages=pages;this.site=site;this.media=media;}
 @GetMapping("/admin/legacy/pages")
 public String list(@AuthenticationPrincipal AccountPrincipal actor,Model model) {model.addAttribute("pages",pages.list(actor));return "cms/pages";}
 @GetMapping("/admin/legacy/pages/new")
 public String create(@AuthenticationPrincipal AccountPrincipal actor,Model model) {
  fields(actor,model,null,null,"","","[{\"type\":\"TEXT\",\"heading\":\"\",\"body\":\"\",\"visible\":true}]");return "cms/page-form";
 }
 @GetMapping("/admin/legacy/pages/{id}/edit")
 public String edit(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,Model model) {
  var p=pages.get(actor,id);fields(actor,model,id,p.revision(),p.title(),p.slug(),p.sectionsJson());
  model.addAttribute("pageStatus",p.status());model.addAttribute("pending",p.pending());return "cms/page-form";
 }
 @PostMapping("/admin/pages/save")
 public String save(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(required=false) Long id,@RequestParam(required=false) Long revision,
  @RequestParam String title,@RequestParam(defaultValue="") String slug,@RequestParam String sectionsJson,@RequestParam(defaultValue="save") String action,@RequestParam(required=false) String saveIntent,
  Model model,HttpServletResponse response,RedirectAttributes redirect) {
  try {
   long saved=pages.save(actor,id,revision,title,slug,sectionsJson,action,egovframework.backoffice.mvp.version.SaveIntent.request(saveIntent));
   redirect.addFlashAttribute("notice",action.equals("publish")?"페이지 발행본을 저장했습니다. 외부 홈페이지는 아직 연결되지 않았습니다.":"임시저장했습니다. 기존 발행본은 유지됩니다.");
   return "redirect:/admin/legacy/pages/"+saved+"/edit";
  } catch(BusinessException error) {
   response.setStatus(400);model.addAttribute("formError",error.getMessage());
   fields(actor,model,id,revision,title,slug,sectionsJson);return "cms/page-form";
  }
 }
 private void fields(AccountPrincipal actor,Model model,Long id,Long revision,String title,String slug,String sectionsJson) {
  model.addAttribute("pageId",id);model.addAttribute("revision",revision);model.addAttribute("title",title);
  model.addAttribute("slug",slug);model.addAttribute("sectionsJson",sectionsJson);
  if(!model.containsAttribute("pageStatus")) {var saved=id==null?null:pages.get(actor,id);model.addAttribute("pageStatus",saved==null?"DRAFT":saved.status());model.addAttribute("pending",saved!=null && saved.pending());}
  model.addAttribute("mediaItems",media.list(actor,""));model.addAttribute("categories",site.categories());
 }
 @GetMapping("/admin/legacy/pages/{id}/preview")
 public String preview(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,Model model) {
  var page=pages.get(actor,id);
  model.addAttribute("pageTitle",page.title());model.addAttribute("sections",pages.views(page.sectionsJson()));return "cms/preview";
 }
 @PostMapping("/admin/pages/save-json")
 @ResponseBody
 public org.springframework.http.ResponseEntity<?> saveJson(@AuthenticationPrincipal AccountPrincipal actor,
  @RequestParam(required=false) Long id,@RequestParam(required=false) Long revision,@RequestParam String title,
  @RequestParam(defaultValue="") String slug,@RequestParam String sectionsJson,@RequestParam(defaultValue="save") String action,@RequestParam(required=false) String saveIntent) {
  try {
   if(id!=null && revision==null) throw new BusinessException("저장 버전을 확인할 수 없습니다. 다시 열어 주세요.");
   var page=pages.saveDocument(actor,id,revision,title,slug,sectionsJson,action,egovframework.backoffice.mvp.version.SaveIntent.request(saveIntent));long saved=page.id();
   return org.springframework.http.ResponseEntity.ok(java.util.Map.of("id",saved,"revision",page.revision(),"status",page.status(),"pending",page.pending(),"slug",page.slug()));
  } catch(BusinessException error) {return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of("error",error.getMessage()));}
 }
 @PostMapping("/admin/pages/{id}/unpublish")
 public String unpublish(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestParam(required=false) Long revision,RedirectAttributes redirect) {
  try {pages.unpublish(actor,id,revision);redirect.addFlashAttribute("notice","비공개로 전환했습니다.");}catch(BusinessException error){redirect.addFlashAttribute("formError",error.getMessage());}return "redirect:/admin/legacy/pages/"+id+"/edit";
 }
 @PostMapping("/admin/pages/{id}/delete")
 public String delete(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestParam(required=false) Long revision,@RequestParam(defaultValue="false") boolean confirmed,RedirectAttributes redirect) {
  if(!confirmed)throw new BusinessException("영구 삭제 영향을 확인하고 확인란을 선택하세요.");
  try {pages.delete(actor,id,revision);redirect.addFlashAttribute("notice","페이지를 삭제했습니다.");return "redirect:/admin/legacy/pages";}
  catch(BusinessException error){redirect.addFlashAttribute("formError",error.getMessage());redirect.addFlashAttribute("usages",usages.find(actor,"pages",id));return "redirect:/admin/legacy/pages/"+id+"/edit";}
 }
}

