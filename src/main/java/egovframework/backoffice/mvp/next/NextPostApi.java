package egovframework.backoffice.mvp.next;

import egovframework.backoffice.mvp.cms.CmsModels.Media;
import egovframework.backoffice.mvp.common.BusinessException;
import egovframework.backoffice.mvp.post.Post;
import egovframework.backoffice.mvp.restaurant.RestaurantDetailsService;
import egovframework.backoffice.mvp.restaurant.RestaurantDetailsService.Details;
import egovframework.backoffice.mvp.post.PostService;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import egovframework.backoffice.mvp.classification.ClassificationService;
import egovframework.backoffice.mvp.classification.ClassificationModels.Classification;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Shared post drafts and explicit publication; validation and writes remain in PostService. */
@RestController
@RequestMapping("/api/admin/next/posts")
public class NextPostApi {
    private final PostService posts;
    private final ClassificationService classifications;
    private final RestaurantDetailsService restaurants;
    public NextPostApi(PostService posts,ClassificationService classifications,RestaurantDetailsService restaurants) { this.posts=posts;this.classifications=classifications;this.restaurants=restaurants; }

    public record PostDocument(long id,String title,String content,String richContent,Long categoryId,
                               long revision,String status,Long publishedRevision,boolean pending,
                               long authorId,String authorName,LocalDateTime createdAt,LocalDateTime updatedAt,
                               List<Long> mediaIds,List<Media> attachments,Classification classification,Details restaurant) {}
    public record SaveRequest(Long revision,String title,String content,String richContent,Long categoryId,List<Long> mediaIds,JsonNode classification,JsonNode restaurant,String saveIntent) {}
    public record PreviewRequest(String title,String content,String richContent,List<Long> mediaIds,JsonNode classification,JsonNode restaurant) {}
    public record TrashRequest(Long revision,boolean confirmed) {}
    public record TrashRow(long id,String title,String authorName,Long categoryId,String status,long revision,LocalDateTime deletedAt) {}

    @ModelAttribute public void noStore(HttpServletResponse response) { response.setHeader("Cache-Control","no-store"); }

    @GetMapping("/trash")
    public Map<String,Object> trash(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="") String q) {
        var result=posts.trash(actor,page,q);
        return Map.of("items",result.items().stream().map(p->new TrashRow(p.id(),p.title(),p.authorName(),p.categoryId(),p.status(),p.revision(),p.deletedAt())).toList(),
            "page",result.page(),"pageSize",result.pageSize(),"total",result.total());
    }
    @PostMapping(value="/{id}/trash",consumes="application/json")
    public Map<String,Long> moveToTrash(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody TrashRequest input) {
        if(!input.confirmed())throw new BusinessException("휴지통 이동을 확인하세요.");
        posts.delete(actor,id,input.revision());return Map.of("id",id);
    }
    @PostMapping(value="/{id}/restore",consumes="application/json")
    public PostDocument restoreTrash(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody TrashRequest input) {
        if(!input.confirmed())throw new BusinessException("임시보관으로 복원할지 확인하세요.");
        return document(actor,posts.restoreTrash(actor,id,input.revision()));
    }
    @DeleteMapping(value="/{id}/trash",consumes="application/json")
    public Map<String,Long> purgeTrash(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody TrashRequest input) {
        if(!input.confirmed())throw new BusinessException("복구할 수 없는 영구 삭제를 확인하세요.");
        posts.purgeTrash(actor,id,input.revision());return Map.of("id",id);
    }

    @GetMapping("/{id}")
    public PostDocument get(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id) {
        return document(actor,posts.get(actor,id));
    }

    @PostMapping(consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public PostDocument create(@AuthenticationPrincipal AccountPrincipal actor,@RequestBody SaveRequest input) {
        if(input.revision()!=null)throw new BusinessException("새 콘텐츠에는 기존 저장 버전을 지정할 수 없습니다.");
        var saved=posts.saveDocument(actor,null,null,input.title(),input.content(),input.categoryId(),
            input.mediaIds(),"save",input.richContent(),classifications.parse(input.classification()),restaurants.parse(input.restaurant()),egovframework.backoffice.mvp.version.SaveIntent.request(input.saveIntent()));
        return document(actor,saved);
    }

    @PutMapping(value="/{id}",consumes="application/json")
    public PostDocument save(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody SaveRequest input) {
        // Require an existing target and a version. The caller cannot request create/publish or change the author.
        posts.get(actor,id);
        if(input.revision()==null) throw new BusinessException("저장 버전이 필요합니다. 내용을 다시 조회하세요.");
        var saved=posts.saveDocument(actor,id,input.revision(),input.title(),input.content(),
            input.categoryId(),input.mediaIds(),"save",input.richContent(),classifications.parse(input.classification()),restaurants.parse(input.restaurant()),egovframework.backoffice.mvp.version.SaveIntent.request(input.saveIntent()));
        return document(actor,saved);
    }

    @PostMapping(value="/{id}/publish",consumes="application/json")
    public PostDocument publish(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody SaveRequest input) {
        posts.get(actor,id);
        if(input.revision()==null) throw new BusinessException("저장 버전이 필요합니다. 내용을 다시 조회하세요.");
        // Save the submitted editor contents and publication in the same existing transaction.
        // Autosave stays on PUT; publication cannot be enabled through a draft request field.
        var published=posts.saveDocument(actor,id,input.revision(),input.title(),input.content(),
            input.categoryId(),input.mediaIds(),"publish",input.richContent(),classifications.parse(input.classification()),
            restaurants.parse(input.restaurant()),egovframework.backoffice.mvp.version.SaveIntent.MANUAL_DRAFT);
        return document(actor,published);
    }

    @GetMapping("/{id}/preview")
    public PostService.Preview savedPreview(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id) {
        var post=get(actor,id);
        return posts.preview(actor,id,post.title(),post.content(),post.richContent(),post.mediaIds());
    }

    @PostMapping(value="/{id}/preview",consumes="application/json")
    public PostService.Preview preview(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody PreviewRequest input) {
        return posts.preview(actor,id,input.title(),input.content(),input.richContent(),input.mediaIds(),classifications.parse(input.classification()),restaurants.parse(input.restaurant()));
    }

    @GetMapping("/{id}/publication")
    public PostService.Publication publication(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id) {
        return posts.publication(actor,id);
    }

    @GetMapping("/{id}/publication/view")
    public PostService.Preview publicationView(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id) {
        return posts.publicationView(actor,id);
    }

    private PostDocument document(AccountPrincipal actor,Post p) {
        var attachments=posts.attachments(actor,p.id());
        return new PostDocument(p.id(),p.title(),p.content(),p.richContent(),p.categoryId(),p.revision(),
            p.status(),p.publishedRevision(),p.pending(),p.authorId(),p.authorName(),p.createdAt(),p.updatedAt(),
            attachments.stream().map(Media::id).toList(),attachments,posts.classification(actor,p.id()),posts.restaurant(actor,p.id()));
    }
}
