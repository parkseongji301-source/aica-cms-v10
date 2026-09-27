package egovframework.backoffice.mvp.cms;

import egovframework.backoffice.mvp.common.BusinessException;
import java.util.*;

/** Developer-owned definitions shared by validation and the read-only editor catalog. */
public final class PageComponentRegistry {
    private PageComponentRegistry() {}
    public record Variation(String value,String label,String description) {}
    public record Defaults(String heading,String body,String bodyDoc,Long imageId,Long categoryId,String link,String label,boolean visible) {}
    public record Definition(String type,String label,String description,int schemaVersion,String defaultVariation,
                             List<String> fields,List<Variation> variations,Defaults defaults,PostsCapability postsQuery) {}
    public record PostsCapability(List<String> sourceModes,List<String> sorts,int minLimit,int maxLimit,int defaultLimit,boolean singleType,int maxManualItems) {}
    private static final Variation DEFAULT=new Variation("default","기본형","기존 블록의 기본 배치");
    private static final List<Definition> DEFINITIONS=List.of(
        definition("HERO","대표 문구","페이지의 첫인상과 주요 안내",List.of("heading","body","link","label"),
            List.of(DEFAULT,new Variation("centered","가운데 강조형","제목·본문·버튼을 가운데로 모아 강조합니다."))),
        definition("TEXT","본문","문구와 서식이 있는 설명",List.of("heading","body"),List.of(DEFAULT)),
        definition("IMAGE","이미지","등록된 이미지와 설명",List.of("heading","body","imageId"),List.of(DEFAULT)),
        definition("POSTS","글 목록","카테고리·분류 조건·직접 선택으로 연결하는 발행 콘텐츠",List.of("heading","categoryId","query","manual"),List.of(DEFAULT)),
        definition("CTA","안내 버튼","설명과 연결 버튼",List.of("heading","body","link","label"),List.of(DEFAULT))
    );
    private static Definition definition(String type,String label,String description,List<String> fields,List<Variation> variations) {
        return new Definition(type,label,description,2,"default",fields,variations,new Defaults("","",null,null,null,"","",true),"POSTS".equals(type)?new PostsCapability(List.of("category","query","manual"),List.of("LATEST"),1,PublishedPostQueryService.MAX_LIMIT,PublishedPostQueryService.DEFAULT_LIMIT,true,PublishedPostQueryService.MAX_MANUAL_ITEMS):null);
    }
    public static List<Definition> definitions(){return DEFINITIONS;}
    public static Definition required(String type) {
        return DEFINITIONS.stream().filter(d->d.type().equals(type)).findFirst()
            .orElseThrow(()->new BusinessException("지원하지 않는 블록 종류입니다."));
    }
    public static void validate(String type,Integer version,String variation) {
        var definition=required(type);
        if(!Objects.equals(definition.schemaVersion(),version)||definition.variations().stream().noneMatch(v->v.value().equals(variation)))
            throw new BusinessException("지원하지 않는 블록 버전 또는 Variation입니다. 저장하지 않았습니다.");
    }
}
