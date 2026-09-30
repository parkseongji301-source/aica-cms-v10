package egovframework.backoffice.mvp.next;

import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 구성 게시: HTTP adapter only; SiteStructureService keeps validation, authorization and writes. */
@RestController
@RequestMapping("/api/admin/next/site-structure")
public class NextStructureApi {
    private final SiteStructureService structure;
    private final PageService pages;
    public NextStructureApi(SiteStructureService structure,PageService pages){this.structure=structure;this.pages=pages;}
    public record PublishInput(String fingerprint, Long expectedLatestId) {}
    public record RepublishInput(Long expectedLatestId) {}
    public record ImportInput(String fingerprint) {}

    @ModelAttribute public void noStore(HttpServletResponse response) { response.setHeader("Cache-Control","no-store"); }

    @GetMapping
    public SiteStructureService.Status status(@AuthenticationPrincipal AccountPrincipal actor) {return structure.status(actor);}
    @PostMapping("/publications")
    public SiteStructureService.Status publish(@AuthenticationPrincipal AccountPrincipal actor,@RequestBody PublishInput input) {
        return structure.publish(actor,input.fingerprint(),input.expectedLatestId());
    }
    @GetMapping("/publications")
    public List<SiteStructureService.Published> publications(@AuthenticationPrincipal AccountPrincipal actor) {return structure.publications(actor);}
    @PostMapping("/publications/{id}/republish")
    public SiteStructureService.Republished republish(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody RepublishInput input) {
        return structure.republish(actor,id,input.expectedLatestId());
    }
    @GetMapping("/menu-import")
    public SiteStructureService.ImportPlan importPlan(@AuthenticationPrincipal AccountPrincipal actor) {return structure.importPlan(actor);}
    @PostMapping("/menu-import")
    public Map<String,Object> importMenus(@AuthenticationPrincipal AccountPrincipal actor,@RequestBody ImportInput input) {
        var plan=structure.importMenus(actor,input.fingerprint());
        return Map.of("plan",plan,"pages",NextWorkspaceApi.rows(pages.list(actor)));
    }
}
