package egovframework.backoffice.mvp.cms;

import egovframework.backoffice.mvp.classification.ClassificationService;
import egovframework.backoffice.mvp.common.BusinessException;
import static egovframework.backoffice.mvp.cms.CmsModels.*;
import static egovframework.backoffice.mvp.cms.CmsStore.values;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Shared publication query for page rendering and editor previews. Never reads draft assignments. */
@Service
public class PublishedPostQueryService {
    public static final int DEFAULT_LIMIT=6,MAX_LIMIT=20,MAX_MANUAL_ITEMS=20;
    private final CmsStore store;
    private final ClassificationService classifications;
    public PublishedPostQueryService(CmsStore store,ClassificationService classifications){this.store=store;this.classifications=classifications;}
    public record Result(List<PublicPost> items,long total) {}
    /** Deletion impact: all matching publications, independent of the display limit. */
    public boolean matches(Section block,long postId) {
        if(!"POSTS".equals(block.type()))return false;
        if("manual".equals(block.sourceMode()))return block.manual().postIds().contains(postId);
        var q="query".equals(block.sourceMode())?block.query():null;
        return store.<Long>one("publicPostCount",values("postIds",List.of(postId),
            "categoryId",q==null?block.categoryId():null,"typeCode",q==null?null:q.typeCode(),
            "cohortIds",q==null?List.of():q.cohortIds(),"topicIds",q==null?List.of():q.topicIds()))>0;
    }

    public PostsQuery validate(PostsQuery query) {
        if(query==null||query.typeCode()==null||query.typeCode().isBlank()||query.cohortIds()==null||query.topicIds()==null)
            throw new BusinessException("콘텐츠 유형과 기수·주제 조건을 확인하세요.");
        if(!"LATEST".equals(query.sort())||query.limit()==null||query.limit()<1||query.limit()>MAX_LIMIT)
            throw new BusinessException("정렬은 최신순, 표시 개수는 1~20개로 선택하세요.");
        var filter=classifications.filter(List.of(query.typeCode()),query.cohortIds(),query.topicIds());
        var allowed=classifications.catalog().allowedTopics().stream().filter(t->t.typeCode().equals(query.typeCode())).map(t->t.topicId()).toList();
        if(!allowed.containsAll(filter.topicIds()))throw new BusinessException("유형에 맞지 않는 주제가 남아 있습니다. 직접 해제하거나 유형을 되돌려 주세요.");
        return new PostsQuery(query.typeCode(),filter.cohortIds(),filter.topicIds(),query.sort(),query.limit());
    }
    public PostsQuery validateBlock(Section block) {
        if(!"POSTS".equals(block.type())) {
            if(block.sourceMode()!=null||block.query()!=null||block.manual()!=null)throw new BusinessException("콘텐츠 연결 조건은 POSTS 블록에서만 사용할 수 있습니다.");
            return null;
        }
        if(block.sourceMode()!=null&&!Set.of("category","query","manual").contains(block.sourceMode()))throw new BusinessException("지원하지 않는 콘텐츠 소스입니다.");
        if("query".equals(block.sourceMode())&&block.query()==null)throw new BusinessException("콘텐츠 조건을 선택하세요.");
        if("manual".equals(block.sourceMode())&&block.manual()==null)throw new BusinessException("직접 선택 목록을 확인하세요.");
        if(block.sourceMode()==null&&(block.query()!=null||block.manual()!=null))throw new BusinessException("콘텐츠 소스를 확인하세요.");
        return block.query()==null?null:validate(block.query());
    }
    public PostsManual validateManual(PostsManual manual) {
        if(manual==null)return null;
        var ids=manual.postIds();
        if(ids==null||ids.size()>MAX_MANUAL_ITEMS||ids.stream().anyMatch(id->id==null||id<=0)||new HashSet<>(ids).size()!=ids.size())
            throw new BusinessException("직접 선택은 중복 없이 최대 20개까지 가능합니다. 콘텐츠 ID를 확인하세요.");
        // Missing/deleted IDs are intentionally retained; availability is not a mutation of configuration.
        return new PostsManual(ids);
    }
    public List<SelectedPost> selectedPosts(List<Long> ids) {
        var checked=validateManual(new PostsManual(ids)).postIds();
        if(checked.isEmpty())return List.of();
        var found=new HashMap<Long,SelectedPost>();
        for(var post:store.<SelectedPost>all("selectedPostStates",values("postIds",checked)))found.put(post.id(),post);
        return checked.stream().map(id->found.getOrDefault(id,new SelectedPost(id,"콘텐츠 #"+id,"UNAVAILABLE",null))).toList();
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Result find(Section block) {
        if(!"POSTS".equals(block.type()))return new Result(List.of(),0);
        if("manual".equals(block.sourceMode())) {
            var ids=block.manual().postIds();
            if(ids.isEmpty())return new Result(List.of(),0);
            var parameters=values("categoryId",null,"typeCode",null,"cohortIds",List.of(),"topicIds",List.of(),
                "postIds",ids,"limit",MAX_MANUAL_ITEMS,"offset",0);
            var found=new HashMap<Long,PublicPost>();
            for(var post:store.<PublicPost>all("publicPosts",parameters))found.put(post.id(),post);
            var ordered=ids.stream().map(found::get).filter(Objects::nonNull).toList();
            return new Result(ordered,store.<Long>one("publicPostCount",parameters));
        }
        // Saved publication settings are used as-is: dictionary edits must not rewrite old snapshots.
        var q="query".equals(block.sourceMode())?block.query():null;
        var parameters=values("categoryId",q==null?block.categoryId():null,"typeCode",q==null?null:q.typeCode(),
            "cohortIds",q==null?List.of():q.cohortIds(),"topicIds",q==null?List.of():q.topicIds(),
            "limit",q==null?DEFAULT_LIMIT:q.limit(),"offset",0);
        return new Result(store.all("publicPosts",parameters),store.<Long>one("publicPostCount",parameters));
    }
}
