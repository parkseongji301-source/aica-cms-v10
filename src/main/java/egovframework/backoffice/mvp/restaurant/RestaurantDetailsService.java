package egovframework.backoffice.mvp.restaurant;

import com.fasterxml.jackson.databind.JsonNode;
import egovframework.backoffice.mvp.cms.CmsRules;
import egovframework.backoffice.mvp.common.BusinessException;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Invoked inside the existing authorized PostService transaction; no independent write endpoint. */
@Service
public class RestaurantDetailsService {
    public record Details(String address) {}
    private final RestaurantMapper mapper;
    public RestaurantDetailsService(RestaurantMapper mapper){this.mapper=mapper;}
    public Details parse(JsonNode value) {
        if(value==null)return null; // Old clients omit the field: preserve it.
        if(!value.isObject()||value.size()!=1||!value.has("address")||!value.get("address").isTextual())
            throw new BusinessException("맛집 주소는 address 문자열로 입력하세요. 비우려면 빈 문자열을 사용하세요.");
        return new Details(CmsRules.optional(value.get("address").asText(),500,"주소"));
    }
    public Details resolve(Long id,String type,Details input) {
        Details stored=id==null?null:mapper.draft(id);
        if("RESTAURANT".equals(type))return input!=null?new Details(CmsRules.optional(input.address(),500,"주소")):stored!=null?stored:new Details("");
        if(input!=null&&!CmsRules.optional(input.address(),500,"주소").isEmpty())
            throw new BusinessException("맛집 유형에서만 주소를 저장할 수 있습니다. 주소를 비우거나 유형을 되돌려 주세요.");
        if(input==null&&stored!=null&&!stored.address().isEmpty())
            throw new BusinessException("기존 맛집 주소가 남아 있습니다. 유형을 변경하려면 주소를 명시적으로 비워 주세요.");
        return null;
    }
    public Details draft(long id,String type){if(!"RESTAURANT".equals(type))return null;var value=mapper.draft(id);return value==null?new Details(""):value;}
    public Details published(long id,String type){if(!"RESTAURANT".equals(type))return null;var value=mapper.published(id);return value==null?new Details(""):value;}
    public void save(long id,Details value){if(value==null)clear(id);else mapper.change("save",Map.of("postId",id,"address",value.address()));}
    public void publish(long id){mapper.change("publish",id);}
    public void clear(long id){mapper.change("clear",id);}
}
