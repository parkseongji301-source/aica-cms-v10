package egovframework.backoffice.mvp.classification;

import com.fasterxml.jackson.databind.JsonNode;
import egovframework.backoffice.mvp.common.BusinessException;
import static egovframework.backoffice.mvp.classification.ClassificationModels.*;
import static egovframework.backoffice.mvp.cms.CmsStore.values;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Assignments are internal to PostService's authorized, revision-checked transaction. */
@Service
public class ClassificationService {
    public static final Set<String> REGISTERED_TYPES=Set.of("GENERAL","REVIEW","RESTAURANT","INTERVIEW","FAQ");
    private final ClassificationMapper mapper;
    public ClassificationService(ClassificationMapper mapper){this.mapper=mapper;}
    public Catalog catalog(){return new Catalog(mapper.all("types",null),mapper.all("cohorts",null),mapper.all("topics",null),mapper.all("allowedTopics",null));}
    public Classification draft(long id){return read(id,false);}
    public Classification published(long id){return read(id,true);}
    /** Admin list filters use draft assignments. Archived catalog values remain searchable. */
    public Filter filter(List<String> types,List<Long> cohorts,List<Long> topics){
        var codes=types==null?List.<String>of():types.stream().distinct().sorted().toList();
        if(codes.stream().anyMatch(code->!REGISTERED_TYPES.contains(code)))throw new BusinessException("콘텐츠 유형 필터를 확인하세요.");
        var cohortIds=cohorts==null?List.<Long>of():checkedIds(cohorts);
        var topicIds=topics==null?List.<Long>of():checkedIds(topics);
        var catalog=catalog();
        if(cohortIds.stream().anyMatch(id->catalog.cohorts().stream().noneMatch(t->t.id()==id)) ||
           topicIds.stream().anyMatch(id->catalog.topics().stream().noneMatch(t->t.id()==id)))throw new BusinessException("기수·주제 필터를 확인하세요.");
        return new Filter(codes,cohortIds,topicIds);
    }
    private Classification read(long id,boolean published){
        String prefix=published?"published":"draft";
        TypeValue type=mapper.one(prefix+"Type",id);
        if(type==null)throw new BusinessException("분류 원본을 찾을 수 없습니다.");
        return document(type.code(),type.name(),mapper.all(prefix+"Cohorts",id),mapper.all(prefix+"Topics",id));
    }
    private Classification document(String code,String name,List<Term> cohorts,List<Term> topics){
        return new Classification(code,name,cohorts.stream().map(Term::id).toList(),topics.stream().map(Term::id).toList(),cohorts,topics);
    }
    /** Missing object means preserve; explicit null/partial objects are errors, not a request to clear. */
    public Selection parse(JsonNode input){
        if(input==null)return null;
        if(!input.isObject()||!input.path("typeCode").isTextual())throw new BusinessException("분류 유형과 기수·주제를 함께 입력하세요.");
        return new Selection(input.get("typeCode").asText(),ids(input.get("cohortIds")),ids(input.get("topicIds")));
    }
    private List<Long> ids(JsonNode input){
        if(input==null||!input.isArray())throw new BusinessException("기수·주제는 배열로 입력하세요. 선택 해제는 빈 배열입니다.");
        var result=new ArrayList<Long>();
        for(var value:input){if(!value.isIntegralNumber()||!value.canConvertToLong())throw new BusinessException("분류 ID를 확인하세요.");result.add(value.longValue());}
        return checkedIds(result);
    }
    private List<Long> checkedIds(List<Long> input){
        if(input==null||input.stream().anyMatch(id->id==null||id<=0)||new HashSet<>(input).size()!=input.size())throw new BusinessException("기수·주제 ID는 중복 없이 선택하세요.");
        return input.stream().sorted().toList();
    }
    public Classification validate(Selection input,Classification existing){
        if(input==null) return existing==null?document("GENERAL","일반",List.of(),List.of()):existing;
        if(!REGISTERED_TYPES.contains(Objects.toString(input.typeCode(),"")))throw new BusinessException("등록된 콘텐츠 유형을 선택하세요.");
        var catalog=catalog();
        var type=catalog.types().stream().filter(t->t.code().equals(input.typeCode())).findFirst().orElseThrow(()->new BusinessException("유형을 확인하세요."));
        boolean same=existing!=null&&type.code().equals(existing.typeCode());
        if(!type.active()&&!same)throw new BusinessException("사용 중인 유형을 선택하세요.");
        var cohorts=terms(checkedIds(input.cohortIds()),catalog.cohorts(),existing==null?List.of():existing.cohortIds());
        var topics=terms(checkedIds(input.topicIds()),catalog.topics(),same?existing.topicIds():List.of());
        for(var topic:topics)if(catalog.allowedTopics().stream().noneMatch(a->a.typeCode().equals(type.code())&&a.topicId()==topic.id()))throw new BusinessException("이 콘텐츠 유형에 허용되지 않은 주제입니다.");
        return document(type.code(),type.name(),cohorts,topics);
    }
    private List<Term> terms(List<Long> ids,List<Term> available,List<Long> existing){
        return ids.stream().map(id->{var term=available.stream().filter(t->t.id()==id).findFirst().orElseThrow(()->new BusinessException("기수·주제를 다시 선택하세요."));
            if(!term.active()&&!existing.contains(id))throw new BusinessException("비활성 기수·주제는 새로 선택할 수 없습니다.");return term;}).toList();
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void save(long id,Classification data){
        mapper.change("type",values("postId",id,"typeCode",data.typeCode()));mapper.change("clearCohorts",id);mapper.change("clearTopics",id);
        for(long term:data.cohortIds())mapper.change("cohort",values("postId",id,"termId",term));
        for(long term:data.topicIds())mapper.change("topic",values("postId",id,"termId",term));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void publish(long id){mapper.change("publishCohorts",id);mapper.change("publishTopics",id);}
    @Transactional(propagation=Propagation.MANDATORY)
    public void clear(long id){mapper.change("clearCohorts",id);mapper.change("clearTopics",id);}
}
