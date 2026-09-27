package egovframework.backoffice.mvp.post;

import java.util.*;
import java.time.LocalDate;
import egovframework.backoffice.mvp.classification.ClassificationModels.Filter;
import org.egovframe.rte.psl.dataaccess.EgovAbstractMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PostMapper extends EgovAbstractMapper {
    public Post find(long id) { return selectOne("Post.find", id); }
    public List<Post> list(Long authorId, int limit, int offset) {
        return list(authorId, limit, offset, "");
    }
    public List<Post> list(Long authorId, int limit, int offset, String query) {
        return list(authorId, limit, offset, query, null, null);
    }
    public List<Post> list(Long authorId, int limit, int offset, String query, String status, Long categoryId) {
        return list(authorId,limit,offset,query,status,categoryId,null);
    }
    public List<Post> list(Long authorId,int limit,int offset,String query,String status,Long categoryId,Filter classification) {
        var values = searchValues(authorId, query);
        values.put("classification",classification);
        values.put("status", status); values.put("categoryId", categoryId);
        values.put("authorId", authorId); values.put("limit", limit); values.put("offset", offset);
        return selectList("Post.list", values);
    }
    public long count(Long authorId) {
        return count(authorId, "");
    }
    public long count(Long authorId, String query) {
        return count(authorId, query, null, null);
    }
    public long count(Long authorId, String query, String status, Long categoryId) {
        return count(authorId,query,status,categoryId,null);
    }
    public long count(Long authorId,String query,String status,Long categoryId,Filter classification) {
        var values = searchValues(authorId, query);
        values.put("classification",classification);
        values.put("status", status); values.put("categoryId", categoryId);
        return selectOne("Post.count", values);
    }
    public List<DailyPostCount> dailyCounts(Long authorId, LocalDate start, LocalDate end) {
        var values = new HashMap<String, Object>();
        values.put("authorId", authorId);
        values.put("start", start.atStartOfDay()); values.put("end", end.atStartOfDay());
        return selectList("Post.dailyCounts", values);
    }
    private Map<String, Object> searchValues(Long authorId, String query) {
        var values = new HashMap<String, Object>();
        values.put("authorId", authorId);
        values.put("search", query.isEmpty() ? null : "%" + query.toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        return values;
    }
    public long create(String title, String content, long authorId) {
        var values = new HashMap<String, Object>();
        values.put("title", title); values.put("content", content); values.put("authorId", authorId);
        insert("Post.create", values);
        return ((Number) values.get("id")).longValue();
    }
    public int edit(long id, String title, String content, Long authorId) {
        var values = new HashMap<String, Object>();
        values.put("id", id); values.put("title", title); values.put("content", content); values.put("authorId", authorId);
        return update("Post.edit", values);
    }
    public int softDelete(long id, Long authorId) {
        var values = new HashMap<String, Object>(); values.put("id", id); values.put("authorId", authorId);
        return update("Post.softDelete", values);
    }
}
