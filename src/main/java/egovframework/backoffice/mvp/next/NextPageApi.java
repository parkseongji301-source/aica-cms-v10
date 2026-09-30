package egovframework.backoffice.mvp.next;

import static egovframework.backoffice.mvp.cms.CmsModels.*;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.common.*;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/next")
public class NextPageApi {
    private final PageService pages; private final RichTextService rich; private final ObjectMapper json; private final DeletionImpactService impacts;
    public NextPageApi(PageService pages,RichTextService rich,ObjectMapper json,DeletionImpactService impacts) {
        this.pages=pages;this.rich=rich;this.json=json;this.impacts=impacts;
    }
    public record PageDocument(long id,String title,String slug,List<Section> sections,String status,
                               long revision,Long publishedRevision,boolean pending) {}
    public record SaveRequest(Long revision,String title,List<Section> sections,String saveIntent) {}
    public record PreviewRequest(String title,List<Section> sections) {}
    public record PreviewSection(String id,String type,String variation,String heading,String bodyHtml,Long imageId,String label,List<PublicPost> posts,long total) {}
    public record PreviewDocument(String title,List<PreviewSection> sections) {}
    @ModelAttribute public void noStore(HttpServletResponse response) {response.setHeader("Cache-Control","no-store");}

    @GetMapping("/pages/{id}")
    public PageDocument get(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id) {return document(target(actor,id));}

    @GetMapping("/page-components")
    public List<PageComponentRegistry.Definition> components(@AuthenticationPrincipal AccountPrincipal actor) {return pages.componentDefinitions(actor);}

    @GetMapping("/pages/selected-posts")
    public List<SelectedPost> selectedPosts(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(required=false) List<Long> ids) {
        return pages.selectedPosts(actor,ids==null?List.of():ids);
    }
    @GetMapping("/page-structure")
    public List<PageService.PageTarget> structure(@AuthenticationPrincipal AccountPrincipal actor) {return pages.structure(actor);}
    public record PublicationDocument(long id,String title,String slug,List<Section> sections,long revision) {}
    @GetMapping("/pages/{id}/publication")
    public PublicationDocument publication(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id) {
        var p=pages.publication(actor,id);return new PublicationDocument(p.pageId(),p.title(),p.slug(),pages.sections(p.sectionsJson()),p.revision());
    }

    @PutMapping(value="/pages/{id}",consumes="application/json")
    public PageDocument save(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody SaveRequest input) {
        Page existing=target(actor,id);
        if(input.revision()==null)throw new BusinessException("저장 버전이 필요합니다.");
        if(input.revision()!=existing.revision())throw new ResponseStatusException(HttpStatus.CONFLICT,"다른 창에서 변경되었습니다. 작성 내용을 보관한 뒤 다시 조회하세요.");
        // Neither creation, publishing, slug changes nor a caller-supplied target ID are accepted here.
        return document(pages.saveDocument(actor,id,input.revision(),input.title(),existing.slug(),source(input.sections()),"save",egovframework.backoffice.mvp.version.SaveIntent.request(input.saveIntent())));
    }
    public record PublishRequest(Long revision,String title,String slug,List<Section> sections) {}
    public record RevisionRequest(Long revision) {}
    /** Saves the submitted editor contents and publishes them in PageService's existing transaction. */
    @PostMapping(value="/pages/{id}/publish",consumes="application/json")
    public PageDocument publish(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody PublishRequest input) {
        Page existing=current(actor,id,input.revision());
        // Omitted slug keeps the address; PageService still requires structure permission for a different one.
        String slug=input.slug()==null?existing.slug():input.slug();
        return document(pages.saveDocument(actor,id,input.revision(),input.title(),slug,source(input.sections()),"publish",egovframework.backoffice.mvp.version.SaveIntent.MANUAL_DRAFT));
    }
    /** Same withdrawal as the legacy page screen: the draft and history stay. */
    @PostMapping(value="/pages/{id}/unpublish",consumes="application/json")
    public PageDocument unpublish(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody RevisionRequest input) {
        current(actor,id,input.revision());pages.unpublish(actor,id,input.revision());
        return document(target(actor,id));
    }
    public record AddressRequest(Long revision,String slug) {}
    /** Draft address change as in the legacy page form: the public address follows at the next publish. PageService requires structure permission. */
    @PutMapping(value="/pages/{id}/address",consumes="application/json")
    public PageDocument address(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody AddressRequest input) {
        Page existing=current(actor,id,input.revision());
        if(input.slug()==null||input.slug().isBlank())throw new BusinessException("페이지 주소를 입력하세요.");
        if(input.slug().trim().equalsIgnoreCase(existing.slug()))throw new BusinessException("현재 주소와 같습니다.");
        return document(pages.saveDocument(actor,id,input.revision(),existing.title(),input.slug().trim(),existing.sectionsJson(),"save",egovframework.backoffice.mvp.version.SaveIntent.MANUAL_DRAFT));
    }
    /** The same impact list as the legacy delete-confirm screen, read before a permanent delete. */
    @GetMapping("/pages/{id}/delete-impact")
    public DeletionImpactService.Impact deleteImpact(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id) {return impacts.get(actor,"pages",id);}
    public record DeleteRequest(Long revision,boolean confirmed) {}
    /** Permanent delete through PageService.delete; menu or home-page use still blocks it. */
    @DeleteMapping(value="/pages/{id}",consumes="application/json")
    public Map<String,Object> delete(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody DeleteRequest input) {
        if(!input.confirmed())throw new BusinessException("영구 삭제 영향을 확인하고 확인란을 선택하세요.");
        if(input.revision()==null)throw new BusinessException("저장 버전이 필요합니다.");
        pages.delete(actor,id,input.revision());
        return Map.of("id",id,"deleted",true);
    }
    private Page current(AccountPrincipal actor,long id,Long revision) {
        Page existing=target(actor,id);
        if(revision==null)throw new BusinessException("저장 버전이 필요합니다.");
        if(revision!=existing.revision())throw new ResponseStatusException(HttpStatus.CONFLICT,"다른 창에서 변경되었습니다. 작성 내용을 보관한 뒤 다시 조회하세요.");
        return existing;
    }
    @GetMapping("/pages/{id}/preview")
    public PreviewDocument savedPreview(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id) {
        Page page=target(actor,id);return preview(actor,page.title(),pages.sections(page.sectionsJson()));
    }
    @PostMapping(value="/pages/{id}/preview",consumes="application/json")
    public PreviewDocument draftPreview(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody PreviewRequest input) {
        target(actor,id);return preview(actor,input.title(),input.sections());
    }
    @GetMapping("/pages/{id}/publication/preview")
    public PreviewDocument publishedPreview(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id) {
        var p=pages.publication(actor,id);return rendered(p.title(),p.sectionsJson());
    }
    private PreviewDocument preview(AccountPrincipal actor,String title,List<Section> sections) {
        var checked=pages.previewSections(actor,source(sections));
        return rendered(CmsRules.optional(title,200,"페이지 제목"),source(checked));
    }
    private PreviewDocument rendered(String title,String document) {
        return new PreviewDocument(title,pages.views(document).stream().map(v->{var s=v.section();
            return new PreviewSection(s.id(),s.type(),s.variation(),s.heading(),rich.html(s.bodyDoc(),s.body()),s.imageId(),s.label(),v.posts(),v.total());}).toList());
    }
    private Page target(AccountPrincipal actor,long id) {
        return pages.get(actor,id);
    }
    private PageDocument document(Page p) {return new PageDocument(p.id(),p.title(),p.slug(),pages.sections(p.sectionsJson()),p.status(),p.revision(),p.publishedRevision(),p.pending());}
    private String source(List<Section> sections) {
        if(sections==null)throw new BusinessException("섹션 구성이 필요합니다.");
        try{return json.writeValueAsString(sections);}catch(JsonProcessingException e){throw new BusinessException("섹션 구성을 읽을 수 없습니다.");}
    }
}
