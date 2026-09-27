package egovframework.backoffice.mvp.publicapi;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import static egovframework.backoffice.mvp.publicapi.PublicDocuments.*;

@RestController
@RequestMapping(PublicSiteService.BASE)
public class PublicSiteApi {
    private final PublicSiteService site;
    public PublicSiteApi(PublicSiteService site){this.site=site;}
    @GetMapping("/menus") public List<Menu> menus(){return site.menus();}
    @GetMapping("/pages/{id}") public Page page(@PathVariable long id){return site.page(id);}
    @GetMapping("/pages/by-slug/{slug}") public Page slug(@PathVariable String slug){return site.page(slug);}
    @GetMapping("/pages/{id}/blocks/{blockId}/posts")
    public Posts block(@PathVariable long id,@PathVariable String blockId){return site.blockPosts(id,blockId);}
    @GetMapping("/posts/{id}") public Post post(@PathVariable long id){return site.post(id);}
    @GetMapping("/posts") public Posts posts(@RequestParam(required=false) Long categoryId,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="6") int limit){return site.categoryPosts(categoryId,page,limit);}
    @GetMapping("/media/{id}/file") public ResponseEntity<byte[]> file(@PathVariable long id) {
        var file=site.download(id);var m=file.metadata();
        var disposition=SetHolder.IMAGES.contains(m.mime())?ContentDisposition.inline():ContentDisposition.attachment();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(m.mime()))
            .header(HttpHeaders.CONTENT_DISPOSITION,disposition.filename(m.name(),StandardCharsets.UTF_8).build().toString())
            .contentLength(file.bytes().length).body(file.bytes());
    }
    private static final class SetHolder {static final java.util.Set<String> IMAGES=java.util.Set.of("image/png","image/jpeg");}
}
