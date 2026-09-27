package egovframework.backoffice.mvp.next;

import egovframework.backoffice.mvp.classification.ClassificationService;
import egovframework.backoffice.mvp.classification.ClassificationModels.Catalog;
import egovframework.backoffice.mvp.cms.CmsAccess;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Registered catalog only. Creation of types or vocabulary is not an operator feature in 2A. */
@RestController
@RequestMapping("/api/admin/next/classifications")
public class NextClassificationApi {
    private final CmsAccess access;
    private final ClassificationService classifications;
    public NextClassificationApi(CmsAccess access,ClassificationService classifications){this.access=access;this.classifications=classifications;}
    @GetMapping
    public Catalog catalog(@AuthenticationPrincipal AccountPrincipal actor,HttpServletResponse response){
        access.actor(actor);response.setHeader("Cache-Control","no-store");return classifications.catalog();
    }
}
