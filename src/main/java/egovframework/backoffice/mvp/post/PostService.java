package egovframework.backoffice.mvp.post;

import egovframework.backoffice.mvp.common.*;
import egovframework.backoffice.mvp.security.*;
import java.time.LocalDate;
import java.util.*;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.version.*;
import egovframework.backoffice.mvp.classification.ClassificationService;
import egovframework.backoffice.mvp.restaurant.RestaurantDetailsService;
import egovframework.backoffice.mvp.restaurant.RestaurantDetailsService.Details;
import egovframework.backoffice.mvp.classification.ClassificationModels.*;
import static egovframework.backoffice.mvp.cms.CmsStore.values;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PostService extends EgovAbstractServiceImpl {
    private static final int PAGE_SIZE = 10;
    private final java.time.Clock clock;
    private final PostMapper posts;
    private final CurrentAccount current;
    private final AccessPolicy policy;
    private final CmsStore cms;
    private final MediaService media;
    private final ActivityService audit;
    private final RichTextService rich;
    private final ClassificationService classifications;
    private final RestaurantDetailsService restaurants;
    private final VersionHistoryService history;
    public PostService(PostMapper posts, CurrentAccount current, AccessPolicy policy, CmsStore cms, MediaService media, ActivityService audit, RichTextService rich, ClassificationService classifications, RestaurantDetailsService restaurants,java.time.Clock clock,VersionHistoryService history) {
        this.history=history;
        this.clock=clock;
        this.posts = posts; this.current = current; this.policy = policy;
        this.cms = cms; this.media = media; this.audit = audit; this.rich = rich;
        this.classifications = classifications;this.restaurants=restaurants;
    }
    @Transactional(readOnly = true)
    public PostPage list(AccountPrincipal principal, int page) {
        return list(principal, page, "");
    }
    @Transactional(readOnly = true)
    public PostPage list(AccountPrincipal principal, int page, String query) {
        return list(principal,page,query,"",null);
    }
    @Transactional(readOnly = true)
    public PostPage list(AccountPrincipal principal, int page, String query, String status, Long categoryId) {
        return list(principal,page,query,status,categoryId,null,null,null);
    }
    @Transactional(readOnly = true)
    public PostPage list(AccountPrincipal principal,int page,String query,String status,Long categoryId,
                         List<String> typeCodes,List<Long> cohortIds,List<Long> topicIds) {
        if (status == null || !Set.of("", "DRAFT", "PUBLISHED", "PRIVATE").contains(status)) throw new BusinessException("콘텐츠 상태를 확인하세요.");
        String filter = status.isEmpty() ? null : status;
        var actor = current.require(principal, false);
        policy.requirePostAccess(actor, actor.id());
        if (page < 0 || page > 100000) throw new BusinessException("페이지 번호가 올바르지 않습니다.");
        Long author = policy.canManageAllPosts(actor.role()) ? null : actor.id();
        String search = query == null ? "" : query.strip();
        if (search.length() > 100) throw new BusinessException("검색어는 100자 이하로 입력하세요.");
        var taxonomy=classifications.filter(typeCodes,cohortIds,topicIds);
        return new PostPage(posts.list(author, PAGE_SIZE, page * PAGE_SIZE, search, filter, categoryId,taxonomy), page, PAGE_SIZE, posts.count(author, search, filter, categoryId,taxonomy));
    }
    @Transactional(readOnly = true)
    public DashboardOverview dashboard(AccountPrincipal principal) {
        var actor = current.require(principal, false);
        policy.requirePostAccess(actor, actor.id());
        Long author = policy.canManageAllPosts(actor.role()) ? null : actor.id();
        LocalDate today = LocalDate.now(clock);
        var counts = posts.dailyCounts(author, today.minusDays(6), today.plusDays(1));
        var days = new ArrayList<DailyPostCount>();
        for (int offset = 6; offset >= 0; offset--) {
            LocalDate day = today.minusDays(offset);
            long count = counts.stream().filter(item -> item.day().equals(day)).mapToLong(DailyPostCount::count).sum();
            days.add(new DailyPostCount(day, count));
        }
        return new DashboardOverview(posts.count(author), days.get(6).count(),
                days.stream().mapToLong(DailyPostCount::count).sum(), today, days,
                posts.list(author, 5, 0));
    }
    @Transactional(readOnly = true)
    public Post get(AccountPrincipal principal, long id) {
        var actor = current.require(principal, false);
        var post = required(id);
        policy.requirePostAccess(actor, post.authorId());
        return post;
    }
    @Transactional
    public long create(AccountPrincipal principal, String title, String content) {
        var actor = current.require(principal, false);
        policy.requirePostAccess(actor, actor.id());
        cms.lock();
        long id = posts.create(InputRules.text(title, 200, "제목"), InputRules.text(content, 20000, "본문"), actor.id());
        audit.record(actor,"콘텐츠 등록","콘텐츠 #"+id,title);
        return id;
    }
    @Transactional
    public void edit(AccountPrincipal principal, long id, Long revision, String title, String content) {
        cms.lock();
        var actor = current.require(principal, false);
        var post = required(id);
        policy.requirePostAccess(actor, post.authorId());
        CmsRules.revision(post.revision(),revision);
        Long author = policy.canManageAllPosts(actor.role()) ? null : actor.id();
        if (posts.edit(id, InputRules.text(title, 200, "제목"), InputRules.text(content, 20000, "본문"), author) != 1)
            throw missing();
        audit.record(actor,"콘텐츠 수정","콘텐츠 #"+id,title);
    }
    @Transactional
    public void delete(AccountPrincipal principal, long id, Long revision) {
        cms.lock();
        var actor = current.require(principal, false);
        policy.requireDelete(actor);
        var post = required(id);
        policy.requirePostAccess(actor, post.authorId());
        CmsRules.revision(post.revision(),revision);
        Long author = policy.canManageAllPosts(actor.role()) ? null : actor.id();
        if (posts.softDelete(id, author) != 1) throw missing();
        history.purge(VersionKind.POST,id);
        cms.change("postMetadata",values("id",id,"categoryId",null));
        cms.change("clearPostMedia",id); cms.change("clearPostPublicationMedia",id); cms.change("clearPostPublication",id);
        classifications.clear(id);restaurants.clear(id);
        audit.record(actor,"콘텐츠 삭제","콘텐츠 #"+id,post.title());
    }
    @Transactional
    public long save(AccountPrincipal principal, Long id, Long revision, String title, String content,
                     Long categoryId, List<Long> mediaIds, String action) {
        return save(principal,id,revision,title,content,categoryId,mediaIds,action,null);
    }
    @Transactional
    public long save(AccountPrincipal principal, Long id, Long revision, String title, String content,
                     Long categoryId, List<Long> mediaIds, String action, String richContent) {
        return save(principal,id,revision,title,content,categoryId,mediaIds,action,richContent,null);
    }
    /** A missing selection preserves the assignments made through newer clients. */
    @Transactional
    public long save(AccountPrincipal principal, Long id, Long revision, String title, String content,
                     Long categoryId, List<Long> mediaIds, String action, String richContent, Selection selection) {
        return save(principal,id,revision,title,content,categoryId,mediaIds,action,richContent,selection,null);
    }
    @Transactional
    public long save(AccountPrincipal principal,Long id,Long revision,String title,String content,Long categoryId,List<Long> mediaIds,String action,String richContent,Selection selection,Details restaurant) {
        return save(principal,id,revision,title,content,categoryId,mediaIds,action,richContent,selection,restaurant,SaveIntent.LEGACY);
    }
    @Transactional
    public long save(AccountPrincipal principal,Long id,Long revision,String title,String content,Long categoryId,List<Long> mediaIds,String action,String richContent,Selection selection,Details restaurant,SaveIntent intent) {
        cms.lock(); var actor=current.require(principal,false);
        if (!Set.of("save","publish").contains(action)) throw new BusinessException("저장 방식을 확인하세요.");
        var existing=id==null?null:required(id);
        policy.requirePostAccess(actor,existing==null?actor.id():existing.authorId());
        if(action.equals("publish")) policy.requirePublish(actor);
        if(existing!=null && (selection!=null || restaurant!=null) && revision==null) throw new BusinessException("분류·주소를 수정하려면 저장 버전이 필요합니다.");
        if(existing!=null) CmsRules.revision(existing.revision(),revision);
        var classification=classifications.validate(selection,existing==null?null:classifications.draft(id));
        var details=restaurants.resolve(id,classification.typeCode(),restaurant);
        boolean faq="FAQ".equals(classification.typeCode()),isRestaurant="RESTAURANT".equals(classification.typeCode());
        String name=InputRules.text(title,200,faq?"질문":isRestaurant?"식당명":"제목");
        var doc=rich.validate(principal,richContent);
        String body=doc.json()==null?CmsRules.optional(content,20000,faq?"답변":isRestaurant?"소개":"본문"):doc.text();
        var images=doc.json()==null?CmsRules.ids(mediaIds,12):doc.mediaIds();media.validate(principal,images);
        if(action.equals("publish") && body.isEmpty() && images.isEmpty()) throw new BusinessException(faq?"발행하려면 답변이나 이미지를 추가하세요.":"발행하려면 본문이나 이미지를 추가하세요.");
        if(categoryId!=null && cms.one("category",categoryId)==null) throw new BusinessException("카테고리를 다시 선택하세요.");
        if(existing!=null && action.equals("save") && intent==SaveIntent.MANUAL_DRAFT) {
            var oldClassification=classifications.draft(id);
            var oldMedia=cms.<CmsModels.Media>all("postMedia",id).stream().map(CmsModels.Media::id).toList();
            boolean same=Objects.equals(existing.title(),name)&&Objects.equals(existing.content(),body)&&Objects.equals(existing.richContent(),doc.json())
                &&Objects.equals(existing.categoryId(),categoryId)&&oldMedia.equals(images)
                &&oldClassification.typeCode().equals(classification.typeCode())&&oldClassification.cohortIds().equals(classification.cohortIds())&&oldClassification.topicIds().equals(classification.topicIds())
                &&Objects.equals(restaurants.draft(id,oldClassification.typeCode()),details);
            if(same){history.capture(actor,VersionKind.POST,id,"MANUAL_DRAFT",null,null,false);return id;}
        }
        if(id==null) id=posts.create(name,body,actor.id());
        else if(posts.edit(id,name,body,policy.canManageAllPosts(actor.role())?null:actor.id())!=1) throw missing();
        cms.change("postRichContent",values("id",id,"richContent",doc.json()));
        cms.change("postMetadata",values("id",id,"categoryId",categoryId));
        if(selection!=null || existing==null) classifications.save(id,classification);
        restaurants.save(id,details);
        cms.change("clearPostMedia",id);
        for(int i=0;i<images.size();i++) cms.change("attachPostMedia",values("postId",id,"mediaId",images.get(i),"sortOrder",i));
        if(action.equals("publish")) {
            cms.change("clearPostPublicationMedia",id);cms.change("clearPostPublication",id);
            cms.change("publishPost",id);cms.change("publishPostMedia",id);
            classifications.publish(id);restaurants.publish(id);
            cms.change("postVisibility",values("id",id,"status","PUBLISHED"));
        }
        audit.record(actor,action.equals("publish")?"콘텐츠 발행":"콘텐츠 임시저장","콘텐츠 #"+id,name);
        if(action.equals("publish"))history.capture(actor,VersionKind.POST,id,"PUBLISH",null,null,true);
        else if(intent==SaveIntent.MANUAL_DRAFT)history.capture(actor,VersionKind.POST,id,"MANUAL_DRAFT",null,null,false);
        return id;
    }
    @Transactional
    public Post saveDocument(AccountPrincipal actor,Long id,Long revision,String title,String content,Long categoryId,String action,String richContent) {
        return required(save(actor,id,revision,title,content,categoryId,List.of(),action,richContent));
    }
    /** Preserve legacy separate attachments as well as the existing rich document format. */
    @Transactional
    public Post saveDocument(AccountPrincipal actor,Long id,Long revision,String title,String content,
                             Long categoryId,List<Long> mediaIds,String action,String richContent) {
        return required(save(actor,id,revision,title,content,categoryId,mediaIds,action,richContent));
    }
    @Transactional
    public Post saveDocument(AccountPrincipal actor,Long id,Long revision,String title,String content,
                             Long categoryId,List<Long> mediaIds,String action,String richContent,Selection selection) {
        return required(save(actor,id,revision,title,content,categoryId,mediaIds,action,richContent,selection));
    }
    @Transactional(readOnly=true)
    public Classification classification(AccountPrincipal actor,long id) {
        get(actor,id);return classifications.draft(id);
    }
    @Transactional
    public Post saveDocument(AccountPrincipal actor,Long id,Long revision,String title,String content,Long categoryId,List<Long> mediaIds,String action,String richContent,Selection selection,Details restaurant) {
        return required(save(actor,id,revision,title,content,categoryId,mediaIds,action,richContent,selection,restaurant));
    }
    @Transactional
    public Post saveDocument(AccountPrincipal actor,Long id,Long revision,String title,String content,Long categoryId,List<Long> mediaIds,String action,String richContent,Selection selection,Details restaurant,SaveIntent intent) {
        return required(save(actor,id,revision,title,content,categoryId,mediaIds,action,richContent,selection,restaurant,intent));
    }
    @Transactional(readOnly=true)
    public Details restaurant(AccountPrincipal actor,long id) {get(actor,id);return restaurants.draft(id,classifications.draft(id).typeCode());}
    public record Publication(CmsModels.PublicPost post,Classification classification,Details restaurant) {}
    @Transactional(readOnly=true)
    public Publication publication(AccountPrincipal actor,long id) {
        get(actor,id);
        CmsModels.PublicPost post=cms.one("publicPost",id);
        if(post==null)throw missing();
        var classification=classifications.published(id);
        return new Publication(post,classification,restaurants.published(id,classification.typeCode()));
    }
    public record Preview(String title,String bodyHtml,List<CmsModels.Media> attachments,Classification classification,Details restaurant) {}
    /** Both administrator UIs render the same validated document without writing a draft. */
    @Transactional(readOnly=true)
    public Preview preview(AccountPrincipal actor,Long id,String title,String content,String richContent,List<Long> mediaIds) {
        return preview(actor,id,title,content,richContent,mediaIds,null);
    }
    @Transactional(readOnly=true)
    public Preview preview(AccountPrincipal actor,Long id,String title,String content,String richContent,List<Long> mediaIds,Selection selection) {
        return preview(actor,id,title,content,richContent,mediaIds,selection,null);
    }
    @Transactional(readOnly=true)
    public Preview preview(AccountPrincipal actor,Long id,String title,String content,String richContent,List<Long> mediaIds,Selection selection,Details restaurant) {
        if(id!=null) get(actor,id);
        var classification=classifications.validate(selection,id==null?null:classifications.draft(id));
        var doc=rich.validate(actor,richContent);
        var ids=doc.json()==null?CmsRules.ids(mediaIds,12):doc.mediaIds();
        media.validate(actor,ids);
        boolean faq="FAQ".equals(classification.typeCode()),isRestaurant="RESTAURANT".equals(classification.typeCode());
        return new Preview(InputRules.text(title,200,faq?"질문":isRestaurant?"식당명":"제목"),
            rich.html(doc.json(),CmsRules.optional(content,20000,faq?"답변":isRestaurant?"소개":"본문")),
            doc.json()==null?ids.stream().map(media::required).toList():List.of(),classification,restaurants.resolve(id,classification.typeCode(),restaurant));
    }
    @Transactional
    public void unpublish(AccountPrincipal principal,long id,Long revision) {
        policy.requirePublish(current.require(principal,false));
        cms.lock();var post=get(principal,id);CmsRules.revision(post.revision(),revision);
        cms.change("withdrawPost",id);
        audit.record(current.require(principal,false),"콘텐츠 비공개","콘텐츠 #"+id,post.title());
    }
    @Transactional(readOnly=true)
    public List<CmsModels.Media> attachments(AccountPrincipal principal,long id) {
        get(principal,id);return cms.all("postMedia",id);
    }
    @Transactional(readOnly=true)
    public boolean pending(AccountPrincipal principal,long id) {
        var post=get(principal,id);Long published=cms.one("publishedRevision",id);
        return published!=null && published!=post.revision();
    }
    private Post required(long id) {
        var post = posts.find(id);
        if (post == null) throw missing();
        return post;
    }
    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, processException("post.missing").getMessage());
    }
}
