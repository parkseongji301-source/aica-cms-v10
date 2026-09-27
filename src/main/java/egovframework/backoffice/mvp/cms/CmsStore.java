package egovframework.backoffice.mvp.cms;
import java.util.*;
import org.egovframe.rte.psl.dataaccess.EgovAbstractMapper;
import org.springframework.stereotype.Repository;
/** Prepared MyBatis statements only. Names are supplied by application code. */
@Repository
public class CmsStore extends EgovAbstractMapper {
    public <T> T one(String statement, Object values) { return selectOne("Cms." + statement, values); }
    public <T> List<T> all(String statement, Object values) { return selectList("Cms." + statement, values); }
    public int change(String statement, Object values) { return update("Cms." + statement, values); }
    public long create(String statement, Map<String,Object> values) {
        insert("Cms." + statement, values);
        return ((Number)values.get("id")).longValue();
    }
    public void lock() { selectOne("Cms.lock", null); }
    public static Map<String,Object> values(Object... pairs) {
        var result = new HashMap<String,Object>();
        for (int i=0;i<pairs.length;i+=2) result.put((String)pairs[i], pairs[i+1]);
        return result;
    }
}

