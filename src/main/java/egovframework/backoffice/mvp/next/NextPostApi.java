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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Shared post drafts; all validation and writes remain in PostService. Publication uses the existing UI. */
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

    @ModelAttribute public void noStore(HttpServletResponse response) { response.setHeader("Cache-Control","no-store"); }

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

    private PostDocument document(AccountPrincipal actor,Post p) {
        var attachments=posts.attachments(actor,p.id());
        return new PostDocument(p.id(),p.title(),p.content(),p.richContent(),p.categoryId(),p.revision(),
            p.status(),p.publishedRevision(),p.pending(),p.authorId(),p.authorName(),p.createdAt(),p.updatedAt(),
            attachments.stream().map(Media::id).toList(),attachments,posts.classification(actor,p.id()),posts.restaurant(actor,p.id()));
    }
}
