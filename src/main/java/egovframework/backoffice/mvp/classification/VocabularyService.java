package egovframework.backoffice.mvp.classification;

import egovframework.backoffice.mvp.cms.ActivityService;
import egovframework.backoffice.mvp.cms.CmsAccess;
import egovframework.backoffice.mvp.cms.CmsStore;
import egovframework.backoffice.mvp.common.BusinessException;
import egovframework.backoffice.mvp.common.InputRules;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import static egovframework.backoffice.mvp.classification.ClassificationModels.*;
import static egovframework.backoffice.mvp.cms.CmsStore.values;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds a topic to a registered content type as operating data. Add-only: existing topics are never renamed,
 * deactivated or removed here, so no published name snapshot or post assignment can change.
 */
@Service
public class VocabularyService {
    private final ClassificationMapper mapper;private final ClassificationService classifications;
    private final CmsStore store;private final CmsAccess access;private final ActivityService audit;
    public VocabularyService(ClassificationMapper mapper,ClassificationService classifications,CmsStore store,CmsAccess access,ActivityService audit){
        this.mapper=mapper;this.classifications=classifications;this.store=store;this.access=access;this.audit=audit;
    }
    @Transactional
    public Catalog addTopic(AccountPrincipal principal,String typeCode,String code,String name,String description){
        store.lock();var actor=access.structure(principal);
        var catalog=classifications.catalog();
        String type=Objects.toString(typeCode,"").trim();
        var registered=catalog.types().stream().filter(t->t.code().equals(type)).findFirst().orElse(null);
        if(registered==null||!ClassificationService.REGISTERED_TYPES.contains(type)||!registered.active())throw new BusinessException("사용 중인 콘텐츠 유형을 선택하세요.");
        String topicCode=Objects.toString(code,"").trim();
        if(!topicCode.matches("[A-Z][A-Z0-9_]{1,63}"))throw new BusinessException("주제 코드는 영문 대문자로 시작하는 대문자·숫자·밑줄 2~64자로 입력하세요.");
        if(catalog.topics().stream().anyMatch(t->t.code().equals(topicCode)))throw new BusinessException("이미 등록된 주제 코드입니다.");
        String label=InputRules.text(name,80,"주제 이름");
        var sameType=catalog.allowedTopics().stream().filter(a->a.typeCode().equals(type)).map(AllowedTopic::topicId).toList();
        if(catalog.topics().stream().anyMatch(t->sameType.contains(t.id())&&t.name().equals(label)))throw new BusinessException("이 유형에 같은 이름의 주제가 있습니다.");
        String about=Objects.toString(description,"").trim();
        if(about.length()>500)throw new BusinessException("주제 설명은 500자 이하로 입력하세요.");
        var params=values("code",topicCode,"name",label,"description",about);
        mapper.change("addTopic",params);
        long id=((Number)mapper.<Long>one("topicId",topicCode)).longValue();
        mapper.change("allowTopic",values("typeCode",type,"topicId",id));
        audit.record(actor,"주제 추가","주제 #"+id,registered.name()+" · "+label+" ("+topicCode+")");
        return classifications.catalog();
    }
}
