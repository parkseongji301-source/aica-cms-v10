package egovframework.backoffice.mvp.next;

import egovframework.backoffice.mvp.cms.WritingTemplateService;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/next/writing-templates")
public class NextWritingTemplateApi {
 private final WritingTemplateService templates;
 public NextWritingTemplateApi(WritingTemplateService templates){this.templates=templates;}
 public record Input(Long revision,String name,String description,String richContent){}
 public record Version(Long revision,boolean confirmed){}
 @ModelAttribute public void noStore(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
 @GetMapping public List<WritingTemplateService.Summary> list(@AuthenticationPrincipal AccountPrincipal actor){return templates.list(actor);}
 @GetMapping("/{id}") public WritingTemplateService.Document get(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id){return templates.get(actor,id);}
 @PostMapping(value="/{id}/prepare",consumes="application/json") public WritingTemplateService.Document prepare(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody Version input){return templates.prepare(actor,id,input.revision());}
 @PostMapping(value="/manage",consumes="application/json") public WritingTemplateService.Document create(@AuthenticationPrincipal AccountPrincipal actor,@RequestBody Input input){return templates.save(actor,null,null,input.name(),input.description(),input.richContent());}
 @PutMapping(value="/manage/{id}",consumes="application/json") public WritingTemplateService.Document update(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody Input input){return templates.save(actor,id,input.revision(),input.name(),input.description(),input.richContent());}
 @DeleteMapping(value="/manage/{id}",consumes="application/json") public Map<String,Boolean> delete(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody Version input){templates.remove(actor,id,input.revision(),input.confirmed());return Map.of("deleted",true);}
}
