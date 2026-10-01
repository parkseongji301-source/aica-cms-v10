package egovframework.backoffice.mvp.post;

import egovframework.backoffice.mvp.classification.ClassificationModels.AllowedTopic;
import egovframework.backoffice.mvp.classification.ClassificationService;
import egovframework.backoffice.mvp.cms.PageService;
import egovframework.backoffice.mvp.common.BusinessException;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import java.util.*;
import org.springframework.stereotype.Service;

/** Resolves saved work items to exact type/topic pairs. Old or hidden items never widen a search. */
@Service
public class WorkTopicFilter {
    private final PageService pages;
    private final ClassificationService classifications;
    public WorkTopicFilter(PageService pages, ClassificationService classifications) {
        this.pages=pages;this.classifications=classifications;
    }
    public List<AllowedTopic> resolve(AccountPrincipal principal,List<Long> ids) {
        if(ids==null||ids.isEmpty())return List.of();
        if(ids.size()>100||ids.stream().anyMatch(id->id==null||id<=0))
            throw new BusinessException("주제 필터의 작업 항목을 확인하세요.");
        var wanted=new LinkedHashSet<>(ids);
        var catalog=classifications.catalog();
        var available=new HashMap<Long,AllowedTopic>();
        for(var area:pages.contentAreas(principal)) {
            if(area.typeCode()==null||catalog.types().stream().noneMatch(t->t.code().equals(area.typeCode())&&t.active()))continue;
            for(var node:area.nodes()) {
                if(node.topicId()==null||catalog.topics().stream().noneMatch(t->t.id()==node.topicId()&&t.active()))continue;
                var pair=new AllowedTopic(area.typeCode(),node.topicId());
                if(catalog.allowedTopics().contains(pair))available.put(node.id(),pair);
            }
        }
        if(!available.keySet().containsAll(wanted))
            throw new BusinessException("선택한 작업 항목이 없어졌거나 사용할 수 없습니다. 주제 필터를 해제한 뒤 다시 선택하세요.");
        return wanted.stream().map(available::get).distinct().toList();
    }
}
