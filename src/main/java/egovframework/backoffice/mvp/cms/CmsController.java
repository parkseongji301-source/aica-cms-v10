package egovframework.backoffice.mvp.cms;
import egovframework.backoffice.mvp.common.BusinessException;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import static egovframework.backoffice.mvp.cms.CmsModels.*;
import java.util.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CmsController {
 private final CmsStore store;private final CmsAccess access;private final SiteService site;private final PageService pages;private final MediaService media;private final UsageService usages;
 public CmsController(CmsStore store,CmsAccess access,SiteService site,PageService pages,MediaService media,UsageService usages) {
  this.store=store;this.access=access;this.site=site;this.pages=pages;this.media=media;this.usages=usages;
 }
 @GetMapping("/admin/media")
 public String media(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(defaultValue="") String q,Model model) {
  model.addAttribute("mediaItems",media.list(actor,q));model.addAttribute("query",q);return "cms/media";
 }
 @GetMapping("/admin/media/{id}/file") @ResponseBody
 public ResponseEntity<byte[]> file(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id) {
  var file=media.file(actor,id);var response=ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(file.mime()));
  if(!file.mime().startsWith("image/")) response.header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(media.required(id).name(),java.nio.charset.StandardCharsets.UTF_8).build().toString());
  return response.body(file.data());
 }
 @PostMapping("/admin/media")
 public String upload(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam MultipartFile file,@RequestParam(defaultValue="") String alt,RedirectAttributes redirect) {
  try {media.upload(actor,file,alt);redirect.addFlashAttribute("notice","파일을 업로드했습니다.");}
  catch(BusinessException error) {redirect.addFlashAttribute("formError",error.getMessage());}
  return "redirect:/admin/media";
 }
 @PostMapping("/admin/media/upload") @ResponseBody
 public ResponseEntity<?> quickUpload(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam MultipartFile file,@RequestParam(defaultValue="false") boolean imageOnly) {
  try {if(imageOnly && !Objects.toString(file.getOriginalFilename(),"").toLowerCase(Locale.ROOT).matches(".*[.](jpg|jpeg|png)"))throw new BusinessException("JPG 또는 PNG 이미지를 선택하세요.");return ResponseEntity.ok(media.required(media.upload(actor,file,"")));}
  catch(BusinessException error){return ResponseEntity.badRequest().body(Map.of("error",error.getMessage()));}
 }
 @PostMapping("/admin/media/{id}/edit")
 public String editMedia(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestParam String name,@RequestParam(defaultValue="") String alt,Model model,HttpServletResponse response,RedirectAttributes redirect) {
  try {media.edit(actor,id,name,alt);redirect.addFlashAttribute("notice","파일 정보를 저장했습니다.");return "redirect:/admin/media";}
  catch(BusinessException error){failure(model,response,error);model.addAttribute("failedMediaId",id);model.addAttribute("failedName",name);model.addAttribute("failedAlt",alt);return media(actor,"",model);}
 }
 @PostMapping("/admin/media/{id}/delete")
 public String deleteMedia(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestParam(defaultValue="false") boolean confirmed,RedirectAttributes redirect) {
  if(!confirmed)throw new BusinessException("영구 삭제 영향을 확인하세요.");
  try {media.delete(actor,id);redirect.addFlashAttribute("notice","파일을 삭제했습니다.");}
  catch(BusinessException error){redirect.addFlashAttribute("formError",error.getMessage());redirect.addFlashAttribute("usages",usages.find(actor,"media",id));}
  return "redirect:/admin/media";
 }
 private String structure(AccountPrincipal actor,String type,Long edit,Map<String,String> input,Model model) {
  access.manager(actor);var fields=new HashMap<String,String>();fields.put("visible","true");fields.put("destination","");
  String base=type.equals("links")?"/admin/settings/links":"/admin/"+type;
  if(type.equals("categories")) {
   model.addAttribute("items",site.categories());Category item=edit==null?null:store.one("category",edit);
   if(item!=null)fields.put("name",item.name());
  }else if(type.equals("menus")) {
   model.addAttribute("items",site.menus(actor));Menu item=edit==null?null:store.one("menu",edit);
   if(item!=null){fields.put("label",item.label());fields.put("url",item.url());fields.put("visible",String.valueOf(item.visible()));fields.put("destination",item.kind().equals("LINK")?"LINK":item.kind()+":"+item.targetId());}
  }else {
   model.addAttribute("items",site.links());Link item=edit==null?null:store.one("link",edit);
   if(item!=null){fields.put("label",item.label());fields.put("url",item.url());}
  }
  if(input!=null){fields.putAll(input);fields.put("visible",input.getOrDefault("visible","false"));}
  model.addAttribute("editId",edit);model.addAttribute("formValues",fields);model.addAttribute("structure",type);model.addAttribute("base",base);
  model.addAttribute("pages",pages.list(actor));model.addAttribute("categories",site.categories());return "cms/structure";
 }
 @GetMapping("/admin/categories")
 public String categories(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(required=false) Long edit,Model model) {return structure(actor,"categories",edit,null,model);}
 @GetMapping("/admin/menus")
 public String menus(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(required=false) Long edit,Model model) {return structure(actor,"menus",edit,null,model);}
 @GetMapping("/admin/settings/links")
 public String links(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(required=false) Long edit,Model model) {return structure(actor,"links",edit,null,model);}
 @PostMapping("/admin/categories")
 public String category(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(required=false) Long id,@RequestParam Map<String,String> fields,Model model,HttpServletResponse response,RedirectAttributes redirect) {
  try{site.category(actor,id,fields.get("name"));redirect.addFlashAttribute("notice","저장했습니다.");return "redirect:/admin/categories";}
  catch(BusinessException|DuplicateKeyException error){failure(model,response,error);return structure(actor,"categories",id,fields,model);}
 }
 @PostMapping("/admin/menus")
 public String menu(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(required=false) Long id,@RequestParam Map<String,String> fields,Model model,HttpServletResponse response,RedirectAttributes redirect) {
  try {
   String destination=fields.get("destination"),kind;Long target;
   if(destination!=null){String[] parts=destination.split(":",-1);kind=parts[0];target=parts.length==2?positiveId(parts[1]):null;}
   else {kind=fields.getOrDefault("kind","");target=positiveId(fields.get(kind.equals("PAGE")?"pageId":"categoryId"));}
   site.menu(actor,id,fields.get("label"),kind,target,fields.get("url"),"true".equals(fields.get("visible")));
   redirect.addFlashAttribute("notice","저장했습니다.");return "redirect:/admin/menus";
  }catch(BusinessException error){failure(model,response,error);return structure(actor,"menus",id,fields,model);}
 }
 @PostMapping("/admin/settings/links")
 public String link(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(required=false) Long id,@RequestParam Map<String,String> fields,Model model,HttpServletResponse response,RedirectAttributes redirect) {
  try{site.link(actor,id,fields.get("label"),fields.get("url"));redirect.addFlashAttribute("notice","저장했습니다.");return "redirect:/admin/settings/links";}
  catch(BusinessException error){failure(model,response,error);return structure(actor,"links",id,fields,model);}
 }
 @PostMapping("/admin/categories/{id}/delete")
 public String deleteCategory(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,RedirectAttributes redirect) {
  try{site.deleteCategory(actor,id);redirect.addFlashAttribute("notice","삭제했습니다.");}
  catch(BusinessException error){redirect.addFlashAttribute("formError",error.getMessage());redirect.addFlashAttribute("usages",usages.find(actor,"categories",id));}
  return "redirect:/admin/categories";
 }
 @PostMapping("/admin/menus/{id}/delete")
 public String deleteMenu(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestParam(required=false) Long parentId,RedirectAttributes redirect) {
  try{site.deleteItem(actor,"menus",id);redirect.addFlashAttribute("notice","삭제했습니다.");}catch(BusinessException error){redirect.addFlashAttribute("formError",error.getMessage());}
  return "redirect:/admin/menus"+(parentId==null?"":"?parent="+parentId);
 }
 @PostMapping("/admin/settings/links/{id}/delete")
 public String deleteLink(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,RedirectAttributes redirect) {site.deleteItem(actor,"links",id);redirect.addFlashAttribute("notice","삭제했습니다.");return "redirect:/admin/settings/links";}
 @PostMapping("/admin/structure/{type}/order")
 public String reorder(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable String type,@RequestParam(required=false) List<Long> ids,RedirectAttributes redirect) {
  if(!Set.of("categories","menus","links").contains(type))throw new BusinessException("목록을 확인하세요.");
  try{site.reorder(actor,type,ids);redirect.addFlashAttribute("notice","순서를 저장했습니다.");}catch(BusinessException error){redirect.addFlashAttribute("formError",error.getMessage());}
  return "redirect:"+(type.equals("links")?"/admin/settings/links":"/admin/"+type);
 }
 @GetMapping({"/admin/settings/basic","/admin/settings/system","/admin/design/style","/admin/design/components"})
 public String settings(@AuthenticationPrincipal AccountPrincipal actor,jakarta.servlet.http.HttpServletRequest request,Model model) {return settingsForm(actor,request.getServletPath().contains("/design/")?"design":"basic",null,model);}
 private String settingsForm(AccountPrincipal actor,String group,Map<String,String> input,Model model) {
  access.manager(actor);var values=new LinkedHashMap<>(site.settings());if(input!=null)values.putAll(input);
  model.addAttribute("group",group);model.addAttribute("settings",values);model.addAttribute("pages",pages.list(actor));model.addAttribute("mediaItems",media.list(actor,""));return "cms/settings";
 }
 @PostMapping("/admin/settings/save/{group}")
 public String settings(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable String group,@RequestParam Map<String,String> fields,Model model,HttpServletResponse response,RedirectAttributes redirect) {
  String display=Set.of("design","style","components").contains(group)?"design":"basic";
  try{site.saveSettings(actor,group,fields);redirect.addFlashAttribute("notice","저장했습니다.");return "redirect:"+(display.equals("design")?"/admin/design/style":"/admin/settings/basic");}
  catch(BusinessException error){failure(model,response,error);return settingsForm(actor,display,fields,model);}
 }
 @GetMapping("/admin/activity")
 public String activity(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="false") boolean drafts,Model model) {
  model.addAttribute("activities",site.activities(actor,page,q,drafts));model.addAttribute("total",site.activityCount(actor,q,drafts));model.addAttribute("drafts",drafts);
  model.addAttribute("page",page);model.addAttribute("query",q);return "cms/activity";
 }
 private Long positiveId(String value) {
  if(value==null || value.isBlank())return null;
  try{long id=Long.parseLong(value);if(id>0)return id;}catch(NumberFormatException ignored){}
  throw new BusinessException("연결할 항목을 선택하세요.");
 }
 private void failure(Model model,HttpServletResponse response,RuntimeException error) {
  response.setStatus(400);model.addAttribute("formError",error instanceof DuplicateKeyException?"이미 사용 중인 이름입니다.":error.getMessage());
 }
}
