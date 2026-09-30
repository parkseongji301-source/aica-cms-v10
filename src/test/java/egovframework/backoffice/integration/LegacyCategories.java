package egovframework.backoffice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Legacy categories are retired: nothing can newly take one. Tests that exercise existing legacy data create
 * the post or page normally and then give it the category it had before the retirement, as stored data.
 */
final class LegacyCategories {
    private LegacyCategories() {}
    private static final ObjectMapper JSON=new ObjectMapper();
    static long assign(JdbcTemplate jdbc,Long category,long postId) {
        jdbc.update("UPDATE posts SET category_id=? WHERE id=?",category,postId);
        jdbc.update("UPDATE post_publications SET category_id=? WHERE post_id=?",category,postId);
        return postId;
    }
    /** Saves a new page without its blocks' categories, then stores them on the draft (and publication) at the same positions. */
    static long page(JdbcTemplate jdbc,String sections,Function<String,Long> save) {
        try {
            var blocks=(ArrayNode)JSON.readTree(sections);var categories=new LinkedHashMap<Integer,Long>();
            for(int i=0;i<blocks.size();i++){var b=blocks.get(i);if(b.hasNonNull("categoryId")){categories.put(i,b.get("categoryId").asLong());((ObjectNode)b).putNull("categoryId");}}
            long id=save.apply(JSON.writeValueAsString(blocks));
            if(!categories.isEmpty()){
                jdbc.update("UPDATE site_pages SET sections_json=? WHERE id=?",restore(jdbc.queryForObject("SELECT sections_json FROM site_pages WHERE id=?",String.class,id),categories),id);
                for(String published:jdbc.queryForList("SELECT sections_json FROM page_publications WHERE page_id=?",String.class,id))
                    jdbc.update("UPDATE page_publications SET sections_json=? WHERE page_id=?",restore(published,categories),id);
            }
            return id;
        } catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
    }
    /** Blocks as a template or copy would take them: without legacy categories. */
    static java.util.List<egovframework.backoffice.mvp.cms.CmsModels.Section> without(java.util.List<egovframework.backoffice.mvp.cms.CmsModels.Section> blocks) {
        return blocks.stream().map(s->new egovframework.backoffice.mvp.cms.CmsModels.Section(s.type(),s.heading(),s.body(),s.imageId(),null,s.link(),s.label(),s.visible(),s.bodyDoc(),s.id(),s.schemaVersion(),s.variation(),s.sourceMode(),s.query(),s.manual())).toList();
    }
    /** Removes the legacy categories stored on a page's draft and publication. */
    static void clearPage(JdbcTemplate jdbc,long pageId) {
        try {
            for(String table:java.util.List.of("site_pages","page_publications")) {
                String key=table.equals("site_pages")?"id":"page_id";
                for(String stored:jdbc.queryForList("SELECT sections_json FROM "+table+" WHERE "+key+"=?",String.class,pageId)) {
                    var blocks=(ArrayNode)JSON.readTree(stored);blocks.forEach(b->((ObjectNode)b).putNull("categoryId"));
                    jdbc.update("UPDATE "+table+" SET sections_json=? WHERE "+key+"=?",JSON.writeValueAsString(blocks),pageId);
                }
            }
        } catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
    }
    private static String restore(String stored,Map<Integer,Long> categories)throws com.fasterxml.jackson.core.JsonProcessingException {
        var blocks=(ArrayNode)JSON.readTree(stored);
        categories.forEach((i,c)->{if(i<blocks.size())((ObjectNode)blocks.get(i)).put("categoryId",c);});
        return JSON.writeValueAsString(blocks);
    }
}
