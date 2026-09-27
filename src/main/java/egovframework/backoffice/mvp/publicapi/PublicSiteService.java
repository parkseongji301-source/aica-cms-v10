package egovframework.backoffice.mvp.publicapi;

import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.classification.ClassificationService;
import egovframework.backoffice.mvp.restaurant.RestaurantDetailsService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static egovframework.backoffice.mvp.publicapi.PublicDocuments.*;
import static egovframework.backoffice.mvp.cms.CmsStore.values;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class PublicSiteService {
    public static final String BASE="/api/public/v1";
    private final CmsStore store;
    private final PageService pages;
    private final PublishedPostQueryService queries;
    private final ClassificationService classifications;
    private final RestaurantDetailsService restaurants;
    private final RichTextService rich;
    public PublicSiteService(CmsStore store,PageService pages,PublishedPostQueryService queries,
                             ClassificationService classifications,RestaurantDetailsService restaurants,RichTextService rich) {
        this.store=store;this.pages=pages;this.queries=queries;this.classifications=classifications;this.restaurants=restaurants;this.rich=rich;
    }
    public List<Menu> menus(){return store.all("publicMenus",null);}
    public Page page(long id){return document(requiredPage(id));}
    public Page page(String slug) {
        if(slug==null||slug.length()>100||!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))throw missing();
        CmsModels.PublishedPage p=store.one("publicPage",slug);
        if(p==null)throw missing();return document(p);
    }
    private CmsModels.PublishedPage requiredPage(long id) {
        CmsModels.PublishedPage p=store.one("publicPageById",id);if(p==null)throw missing();return p;
    }
    private Page document(CmsModels.PublishedPage p) {
        var posts=new HashMap<Long,Post>();
        var blocks=pages.sections(p.sectionsJson()).stream().filter(CmsModels.Section::visible).map(s->{
            PageComponentRegistry.validate(s.type(),s.schemaVersion(),s.variation());
            var result="POSTS".equals(s.type())?result(queries.find(s),posts):null;
            var data=new Data(s.heading(),s.body(),rich.publicHtml(s.bodyDoc(),s.body()),
                s.imageId()==null?null:media(s.imageId()),s.link(),s.label(),
                result==null?null:Objects.requireNonNullElse(s.sourceMode(),"category"),result);
            return new Block(s.id(),s.schemaVersion(),s.type(),s.variation(),true,data);
        }).toList();
        return new Page(1,p.pageId(),p.title(),p.slug(),p.publishedAt(),blocks);
    }
    public Posts blockPosts(long pageId,String blockId) {
        var p=requiredPage(pageId);
        var block=pages.sections(p.sectionsJson()).stream()
            .filter(s->s.visible()&&"POSTS".equals(s.type())&&Objects.equals(s.id(),blockId)).findFirst().orElseThrow(PublicSiteService::missing);
        return result(queries.find(block),new HashMap<>());
    }
    /** Category menus need a paginated list; uses the same publication SQL as POSTS. */
    public Posts categoryPosts(Long categoryId,int page,int limit) {
        if(page<1||page>10000||limit<1||limit>PublishedPostQueryService.MAX_LIMIT||categoryId!=null&&categoryId<=0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if(categoryId!=null&&store.one("category",categoryId)==null)throw missing();
        var params=values("categoryId",categoryId,"limit",limit,"offset",(page-1)*limit);
        return result(new PublishedPostQueryService.Result(store.all("publicPosts",params),store.one("publicPostCount",params)),new HashMap<>());
    }
    private Posts result(PublishedPostQueryService.Result source,Map<Long,Post> cache) {
        return new Posts(source.items().stream().map(p->cache.computeIfAbsent(p.id(),id->document(p))).toList(),source.total());
    }
    public Post post(long id) {
        CmsModels.PublicPost p=store.one("publicPost",id);if(p==null)throw missing();return document(p);
    }
    private Post document(CmsModels.PublicPost p) {
        var c=classifications.published(p.id());
        var classification=new Classification(c.typeCode(),c.typeName(),
            c.cohorts().stream().map(t->new Term(t.id(),t.code(),t.name())).toList(),
            c.topics().stream().map(t->new Term(t.id(),t.code(),t.name())).toList());
        var restaurant=restaurants.published(p.id(),c.typeCode());
        var media=store.<CmsModels.Media>all("publishedPostMedia",p.id()).stream().map(this::media).toList();
        return new Post(p.id(),p.title(),p.content(),rich.publicHtml(p.richContent(),p.content()),p.categoryId(),classification,
            restaurant==null?null:new Restaurant(restaurant.address()),media,p.publishedAt());
    }
    private Media media(long id) {
        CmsModels.Media m=store.one("publicMediaMetadata",id);return m==null?null:media(m);
    }
    private Media media(CmsModels.Media m) {return new Media(m.id(),filename(m.name()),m.alt(),m.mime(),m.width(),m.height(),m.byteSize(),BASE+"/media/"+m.id()+"/file");}
    public record Download(Media metadata,byte[] bytes) {}
    public Download download(long id) {
        var metadata=media(id);if(metadata==null)throw missing();
        CmsModels.MediaFile file=store.one("mediaFile",id);if(file==null)throw missing();
        return new Download(metadata,file.data());
    }
    private static String filename(String value) {
        String name=value.replace('\\','/');return name.substring(name.lastIndexOf('/')+1);
    }
    private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND);}
}
