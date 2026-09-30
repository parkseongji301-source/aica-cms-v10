package egovframework.backoffice.mvp.next;

import egovframework.backoffice.mvp.classification.ClassificationService;
import egovframework.backoffice.mvp.classification.VocabularyService;
import egovframework.backoffice.mvp.classification.ClassificationModels.Catalog;
import egovframework.backoffice.mvp.cms.CmsAccess;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Registered catalog. Types are fixed; topics can only be added (operating data, MANAGE_SITE), never edited here. */
@RestController
@RequestMapping("/api/admin/next/classifications")
public class NextClassificationApi {
    private final CmsAccess access;
    private final ClassificationService classifications;
    private final VocabularyService vocabulary;
    public NextClassificationApi(CmsAccess access,ClassificationService classifications,VocabularyService vocabulary){this.access=access;this.classifications=classifications;this.vocabulary=vocabulary;}
    public record TopicInput(String typeCode,String code,String name,String description) {}
    @PostMapping("/topics")
    public Catalog addTopic(@AuthenticationPrincipal AccountPrincipal actor,@RequestBody TopicInput input,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");return vocabulary.addTopic(actor,input.typeCode(),input.code(),input.name(),input.description());
    }
    @GetMapping
    public Catalog catalog(@AuthenticationPrincipal AccountPrincipal actor,HttpServletResponse response){
        access.actor(actor);response.setHeader("Cache-Control","no-store");return classifications.catalog();
    }
}
