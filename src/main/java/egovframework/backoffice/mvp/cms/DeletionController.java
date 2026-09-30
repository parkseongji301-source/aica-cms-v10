package egovframework.backoffice.mvp.cms;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller
public class DeletionController {
 private final DeletionImpactService impacts;
 public DeletionController(DeletionImpactService impacts){this.impacts=impacts;}
 @GetMapping("/admin/legacy/{kind:posts|pages|media}/{id}/delete-confirm")
 public String confirm(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable String kind,@PathVariable long id,Model model){
  model.addAttribute("impact",impacts.get(actor,kind,id));return "cms/delete-confirm";
 }
}
