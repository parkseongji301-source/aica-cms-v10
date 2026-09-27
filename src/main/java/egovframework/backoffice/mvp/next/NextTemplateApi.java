package egovframework.backoffice.mvp.next;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/next/page-templates")
public class NextTemplateApi {
 private final PageTemplateService templates;
 public NextTemplateApi(PageTemplateService templates){this.templates=templates;}
 public record Input(Long revision,String name,String description,boolean active,List<CmsModels.Section> blocks,String saveIntent) {}
 public record Prepare(Long revision) {}
 @ModelAttribute public void noStore(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
 @GetMapping public List<PageTemplateService.Summary> list(@AuthenticationPrincipal AccountPrincipal actor){return templates.list(actor);}
 @GetMapping("/{id}") public PageTemplateService.Document get(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id){return templates.get(actor,id);}
 @PostMapping(consumes="application/json") public PageTemplateService.Document create(@AuthenticationPrincipal AccountPrincipal actor,@RequestBody Input input){return templates.save(actor,null,null,input.name(),input.description(),input.active(),input.blocks(),egovframework.backoffice.mvp.version.SaveIntent.request(input.saveIntent()));}
 @PutMapping(value="/{id}",consumes="application/json") public PageTemplateService.Document update(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody Input input){return templates.save(actor,id,input.revision(),input.name(),input.description(),input.active(),input.blocks(),egovframework.backoffice.mvp.version.SaveIntent.request(input.saveIntent()));}
 @PostMapping(value="/{id}/prepare",consumes="application/json") public PageTemplateService.Prepared prepare(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody Prepare input){return templates.prepare(actor,id,input.revision());}
}
