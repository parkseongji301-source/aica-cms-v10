package egovframework.backoffice.mvp.classification;

import java.util.List;
public final class ClassificationModels {
    private ClassificationModels() {}
    public record Selection(String typeCode,List<Long> cohortIds,List<Long> topicIds) {}
    public record Filter(List<String> typeCodes,List<Long> cohortIds,List<Long> topicIds) {}
    public record Type(String code,String name,boolean active,int sortOrder) {}
    public record Term(long id,String code,String name,boolean active) {}
    public record AllowedTopic(String typeCode,long topicId) {}
    public record Catalog(List<Type> types,List<Term> cohorts,List<Term> topics,List<AllowedTopic> allowedTopics) {}
    public record Classification(String typeCode,String typeName,List<Long> cohortIds,List<Long> topicIds,
                                 List<Term> cohorts,List<Term> topics) {}
    public record TypeValue(String code,String name) {}
}
