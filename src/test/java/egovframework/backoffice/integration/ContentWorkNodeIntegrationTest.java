package egovframework.backoffice.integration;

import com.fasterxml.jackson.databind.*;
import egovframework.backoffice.mvp.account.*;
import java.net.http.HttpResponse;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

/**
 * 콘텐츠 작업 하위 항목 (V16): the operator builds the sub-navigation by hand; nothing is derived from the topic
 * dictionary, and removing a node never touches posts or topics.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:content-work-node-integration;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ContentWorkNodeIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountMapper accounts;
    private static final String API="/api/admin/next";
    private static final String PASSWORD="Test-Nodes-Password42";
    private static final String SECTIONS="[{\"type\":\"TEXT\",\"heading\":\"소개\",\"body\":\"본문\",\"visible\":true}]";
    private long root;

    @BeforeEach void fixture() {
        for(String table:List.of("content_work_nodes","site_structure_publication_pages","site_structure_publications","post_publication_topics","post_publication_cohorts","post_topics","post_cohorts","post_publication_media","post_media","post_publications","page_media","page_publications","page_block_identities","page_version_media","page_versions","post_versions","site_pages","posts","media","site_menus","categories","site_links","content_type_topics","topics","activity_log","users"))jdbc.update("DELETE FROM "+table);
        jdbc.update("UPDATE site_settings SET setting_value='' WHERE setting_key IN ('logoId','homePageId')");
        String hash=encoder.encode(PASSWORD);
        for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,?,FALSE)",email(role),role,hash,role);
        root=accounts.findByEmail(email("SUPER_ADMIN")).id();
        Object[][] fixture={{1L,"홈","home"},{70L,"후기 전체","reviews"},{72L,"오시는 길","location"}};
        for(Object[] p:fixture){
            jdbc.update("INSERT INTO site_pages(id,title,slug,sections_json,status,revision,author_id) VALUES(?,?,?,?,'PUBLISHED',2,?)",p[0],p[1],p[2],SECTIONS,root);
            jdbc.update("INSERT INTO page_publications(page_id,title,slug,sections_json,revision) SELECT id,title,slug,sections_json,revision FROM site_pages WHERE id=?",p[0]);
        }
        jdbc.update("INSERT INTO site_pages(id,title,slug,sections_json,status,revision,author_id,area_kind) VALUES(80,'선배들의 SSUL','group-ssul','[]','DRAFT',0,?,'GROUP')",root);
        jdbc.update("UPDATE site_settings SET setting_value='1' WHERE setting_key='homePageId'");
        jdbc.execute("ALTER TABLE site_pages ALTER COLUMN id RESTART WITH 1000");
        // Dictionary: three REVIEW topics (one retired), one FAQ topic. The sidebar must never list these on its own.
        jdbc.update("INSERT INTO topics(id,code,name,active,sort_order) VALUES(71,'REVIEW_LIFE','생활',TRUE,1),(84,'REVIEW_CLASS','수업',TRUE,2),(95,'REVIEW_PROJECT','프로젝트',TRUE,3),(99,'REVIEW_OLD','폐지',FALSE,4),(301,'FAQ_PREPARATION','준비사항',TRUE,5)");
        jdbc.update("INSERT INTO content_type_topics(type_code,topic_id) VALUES('REVIEW',71),('REVIEW',84),('REVIEW',95),('REVIEW',99),('FAQ',301)");
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) c->{try{db.migration.h2.V8__page_block_identity.upgrade(c);}catch(Exception e){throw new IllegalStateException(e);}return null;});
    }
    private static String email(String role){return role.toLowerCase(Locale.ROOT)+"@nodes.test";}
    private HttpBrowser login(String role)throws Exception{var b=new HttpBrowser(port);b.login(email(role),PASSWORD,"/admin");return b;}
    private String csrf(HttpBrowser b)throws Exception{return json.readTree(b.get(API+"/bootstrap").body()).path("csrf").path("token").asText();}
    private JsonNode body(HttpResponse<String> r)throws Exception{return json.readTree(r.body());}
    private JsonNode ok(HttpResponse<String> r)throws Exception{assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return body(r);}
    private String failure(HttpResponse<String> r,int status)throws Exception{assertThat(r.statusCode()).as(r.body()).isEqualTo(status);return body(r).path("message").asText();}
    private HttpResponse<String> send(HttpBrowser b,String method,String path,Object value,String csrf)throws Exception{return b.json(method,API+path,json.writeValueAsString(value),csrf);}
    private Map<String,Object> composition(String type){var m=new LinkedHashMap<String,Object>();m.put("contentTypeCode",type);m.put("menuVisible",false);m.put("menuLabel",null);m.put("name",null);m.put("contentWorkVisible",type!=null);return m;}
    private Map<String,Object> node(String name,Long topicId){var m=new LinkedHashMap<String,Object>();m.put("name",name);m.put("topicId",topicId);return m;}
    private JsonNode areas(String role)throws Exception{return json.readTree(login(role).get(API+"/bootstrap").body()).path("contentAreas");}
    private static List<String> names(JsonNode nodes){var out=new ArrayList<String>();for(var n:nodes)out.add(n.path("name").asText()+":"+n.path("topicId").asLong());return out;}

    @Test void theOperatorBuildsTheSubNavigationAndTheDictionaryNeverDoes()throws Exception {
        var admin=login("SUPER_ADMIN");String csrf=csrf(admin);
        ok(send(admin,"PUT","/pages/70/composition",composition("REVIEW"),csrf));
        // Linking alone creates exactly one sidebar item: the area, with no nodes for any role.
        for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER")){var a=areas(role);assertThat(a.size()).as(role).isEqualTo(1);assertThat(a.get(0).path("nodes").size()).as(role).isZero();}
        var nodes=ok(send(admin,"POST","/pages/70/content-nodes",node("프로젝트 후기",95L),csrf));
        long project=nodes.get(0).path("id").asLong();
        nodes=ok(send(admin,"POST","/pages/70/content-nodes",node("생활 후기",71L),csrf));
        long life=nodes.get(1).path("id").asLong();
        assertThat(names(nodes)).containsExactly("프로젝트 후기:95","생활 후기:71");
        // Order, name and topic are the operator's; every role sees the same saved list.
        nodes=ok(send(admin,"PUT","/pages/70/content-node-order",Map.of("nodeIds",List.of(life,project)),csrf));
        assertThat(names(nodes)).containsExactly("생활 후기:71","프로젝트 후기:95");
        nodes=ok(send(admin,"PUT","/content-nodes/"+project,node("수업 후기",84L),csrf));
        assertThat(names(nodes)).containsExactly("생활 후기:71","수업 후기:84");
        for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))assertThat(names(areas(role).get(0).path("nodes"))).as(role).containsExactly("생활 후기:71","수업 후기:84");
        assertThat(names(ok(admin.get(API+"/pages/70/content-nodes")))).containsExactly("생활 후기:71","수업 후기:84");
        // Rules: a linked PAGE area only; an active topic allowed for the area's type; no duplicate name or topic.
        assertThat(failure(send(admin,"POST","/pages/72/content-nodes",node("준비",301L),csrf),400)).contains("먼저 글 종류");
        assertThat(failure(send(admin,"POST","/pages/80/content-nodes",node("묶음",95L),csrf),400)).contains("묶음");
        assertThat(failure(send(admin,"POST","/pages/70/content-nodes",node("준비사항",301L),csrf),400)).contains("허용되지");
        assertThat(failure(send(admin,"POST","/pages/70/content-nodes",node("폐지",99L),csrf),400)).contains("사용 중지");
        assertThat(failure(send(admin,"POST","/pages/70/content-nodes",node("없는 주제",12345L),csrf),400)).contains("주제를 다시");
        assertThat(failure(send(admin,"POST","/pages/70/content-nodes",node("생활 후기",95L),csrf),400)).contains("같은 이름");
        assertThat(failure(send(admin,"POST","/pages/70/content-nodes",node("또 생활",71L),csrf),400)).contains("이미 '생활 후기'");
        assertThat(failure(send(admin,"POST","/pages/70/content-nodes",node("  ",95L),csrf),400)).contains("하위 항목 이름");
        assertThat(failure(send(admin,"PUT","/pages/70/content-node-order",Map.of("nodeIds",List.of(life)),csrf),400)).contains("목록이 바뀌었습니다");
        assertThat(send(admin,"PUT","/content-nodes/999999",node("x",95L),csrf).statusCode()).isEqualTo(404);
        // Nodes are structure attributes: the page document and revision stay untouched.
        assertThat(jdbc.queryForObject("SELECT revision FROM site_pages WHERE id=70",Long.class)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM page_versions WHERE page_id=70",Integer.class)).isZero();
        // A post written under a node keeps its topic after the node is removed; the dictionary is untouched.
        var created=send(admin,"POST","/posts",Map.of("saveIntent","AUTOSAVE","title","생활 후기 글","content","","mediaIds",List.of(),"classification",Map.of("typeCode","REVIEW","cohortIds",List.of(),"topicIds",List.of(71))),csrf);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);long post=body(created).path("id").asLong();
        nodes=ok(send(admin,"DELETE","/content-nodes/"+life,null,csrf));
        assertThat(names(nodes)).containsExactly("수업 후기:84");
        assertThat(body(admin.get(API+"/posts/"+post)).path("classification").path("topicIds").toString()).isEqualTo("[71]");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM topics",Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_type_topics",Integer.class)).isEqualTo(5);
        var actions=jdbc.queryForList("SELECT action FROM activity_log WHERE action LIKE '콘텐츠 작업 하위 항목%' ORDER BY id",String.class);
        assertThat(actions).containsExactly("콘텐츠 작업 하위 항목 추가","콘텐츠 작업 하위 항목 추가","콘텐츠 작업 하위 항목 순서 변경","콘텐츠 작업 하위 항목 변경","콘텐츠 작업 하위 항목 제거");
        for(String role:List.of("ADMIN","SUPPORTER")){var b=login(role);String token=csrf(b);
            assertThat(send(b,"POST","/pages/70/content-nodes",node("권한 없음",95L),token).statusCode()).as(role).isEqualTo(403);
            assertThat(send(b,"PUT","/content-nodes/"+project,node("권한 없음",95L),token).statusCode()).as(role).isEqualTo(403);
            assertThat(send(b,"PUT","/pages/70/content-node-order",Map.of("nodeIds",List.of(project)),token).statusCode()).as(role).isEqualTo(403);
            assertThat(send(b,"DELETE","/content-nodes/"+project,null,token).statusCode()).as(role).isEqualTo(403);
            // The editor's read endpoint follows pages/** (ALL_POSTS); every role still gets the nodes through bootstrap.
            assertThat(b.get(API+"/pages/70/content-nodes").statusCode()).as(role).isEqualTo(role.equals("ADMIN")?200:403);}
    }

    @Test void workItemFiltersUseExactConnectionsAndKeepOtherCriteriaAndRoleScope()throws Exception {
        var admin=login("SUPER_ADMIN");String token=csrf(admin);
        ok(send(admin,"PUT","/pages/70/composition",composition("REVIEW"),token));
        ok(send(admin,"PUT","/pages/72/composition",composition("FAQ"),token));
        long review=ok(send(admin,"POST","/pages/70/content-nodes",node("생활 이야기",71L),token)).get(0).path("id").asLong();
        long faq=ok(send(admin,"POST","/pages/72/content-nodes",node("준비 이야기",301L),token)).get(0).path("id").asLong();
        // Both topics are valid in both types: a cross-product of selected type/topic ids would include decoys.
        jdbc.update("INSERT INTO content_type_topics(type_code,topic_id) VALUES('FAQ',71),('REVIEW',301)");
        for(int i=0;i<12;i++) {
            jdbc.update("INSERT INTO posts(id,title,content,author_id,status,content_type_code) VALUES(?,?,'필터 확인',?,'DRAFT','REVIEW')",8100+i,"생활 기록 "+i,root);
            jdbc.update("INSERT INTO post_topics(post_id,topic_id) VALUES(?,71)",8100+i);
        }
        jdbc.update("INSERT INTO posts(id,title,content,author_id,status,content_type_code) VALUES(8200,'FAQ 기록','본문',?,'PRIVATE','FAQ'),(8201,'제외할 FAQ','본문',?,'DRAFT','FAQ'),(8202,'제외할 후기','본문',?,'DRAFT','REVIEW'),(8203,'내 생활 기록','본문',?,'DRAFT','REVIEW')",root,root,root,accounts.findByEmail(email("SUPPORTER")).id());
        jdbc.update("INSERT INTO post_topics(post_id,topic_id) VALUES(8200,301),(8201,71),(8202,301),(8203,71),(8100,301)");
        String path=API+"/posts?workNodeIds="+review+","+faq;
        var first=ok(admin.get(path));var second=ok(admin.get(path+"&page=1"));
        assertThat(first.path("total").asInt()).isEqualTo(14);assertThat(first.path("items").size()).isEqualTo(10);assertThat(second.path("items").size()).isEqualTo(4);
        var ids=new HashSet<Long>();for(var response:List.of(first,second))for(var post:response.path("items"))ids.add(post.path("id").asLong());
        assertThat(ids).hasSize(14).doesNotContain(8201L,8202L);
        assertThat(ok(admin.get(path+"&typeCodes=FAQ")).path("total").asInt()).isEqualTo(1);
        assertThat(ok(admin.get(path+"&status=PRIVATE&q=FAQ")).path("items").get(0).path("id").asLong()).isEqualTo(8200);
        assertThat(ok(admin.get(path+"&status=DRAFT&q=FAQ")).path("total").asInt()).isZero();
        long cohort=701L;
        jdbc.update("INSERT INTO cohorts(id,code,name) VALUES(701,'WORK_FILTER_COHORT','확인용 기수')");
        jdbc.update("INSERT INTO post_cohorts(post_id,cohort_id) VALUES(8100,?)",cohort);
        assertThat(ok(admin.get(path+"&cohortIds="+cohort)).path("total").asInt()).isEqualTo(1);
        jdbc.update("DELETE FROM post_cohorts WHERE cohort_id=?",cohort);
        jdbc.update("DELETE FROM cohorts WHERE id=?",cohort);
        assertThat(ok(login("SUPPORTER").get(path)).path("items").get(0).path("id").asLong()).isEqualTo(8203);
        assertThat(ok(login("SUPPORTER").get(path)).path("total").asInt()).isEqualTo(1);
        ok(send(admin,"PUT","/content-nodes/"+review,Map.of("name","바뀐 항목 이름"),token));
        assertThat(ok(admin.get(path)).path("total").asInt()).isEqualTo(14);
        assertThat(ok(admin.get(API+"/posts?topicIds=71")).path("total").asInt()).isEqualTo(14); // old topic URLs retain their meaning
        var hidden=composition("FAQ");hidden.put("contentWorkVisible",false);
        ok(send(admin,"PUT","/pages/72/composition",hidden,token));
        assertThat(failure(admin.get(path),400)).contains("작업 항목");
        ok(send(admin,"PUT","/pages/72/composition",composition("FAQ"),token));
        jdbc.update("UPDATE topics SET active=FALSE WHERE id=71");
        assertThat(failure(admin.get(path),400)).contains("작업 항목");
        jdbc.update("UPDATE topics SET active=TRUE WHERE id=71");
        ok(send(admin,"DELETE","/content-nodes/"+review,null,token));
        assertThat(failure(admin.get(path),400)).contains("작업 항목");
        assertThat(ok(admin.get(API+"/posts")).path("total").asInt()).isEqualTo(16);
        assertThat(failure(admin.get(API+"/posts?workNodeIds=-1"),400)).contains("필터");
    }

    @Test void nameOnlyCreationReusesTopicsAndPreservesTheConnectionOnRenameAndRemoval()throws Exception {
        var admin=login("SUPER_ADMIN");String csrf=csrf(admin);
        ok(send(admin,"PUT","/pages/70/composition",composition("REVIEW"),csrf));
        String menus=new HttpBrowser(port).get("/api/public/v1/menus").body();
        String structure=new HttpBrowser(port).get("/api/public/v1/structure").body();
        String page=new HttpBrowser(port).get("/api/public/v1/pages/70").body();
        var nodes=ok(send(admin,"POST","/pages/70/content-nodes",Map.of("name","  생활  "),csrf));
        long life=nodes.get(0).path("id").asLong();
        assertThat(nodes.get(0).path("topicId").asLong()).isEqualTo(71L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM topics",Integer.class)).isEqualTo(5);
        // An omitted topic never infers a new connection from the new label (even an existing topic name).
        nodes=ok(send(admin,"PUT","/content-nodes/"+life,Map.of("name","수업"),csrf));
        assertThat(names(nodes)).containsExactly("수업:71");
        assertThat(jdbc.queryForObject("SELECT name FROM topics WHERE id=71",String.class)).isEqualTo("생활");

        nodes=ok(send(admin,"POST","/pages/70/content-nodes",Map.of("name","취업 준비"),csrf));
        long item=nodes.get(1).path("id").asLong(),topic=nodes.get(1).path("topicId").asLong();
        assertThat(jdbc.queryForObject("SELECT name FROM topics WHERE id=?",String.class,topic)).isEqualTo("취업 준비");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_type_topics WHERE type_code='REVIEW' AND topic_id=?",Integer.class,topic)).isEqualTo(1);
        assertThat(failure(send(admin,"POST","/pages/70/content-nodes",Map.of("name","취업 준비"),csrf),400)).contains("같은 이름");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM topics",Integer.class)).isEqualTo(6);
        for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))
            assertThat(areas(role).get(0).path("nodes").get(1).path("topicId").asLong()).as(role).isEqualTo(topic);
        assertThat(body(admin.get(API+"/classifications")).path("topics").findValuesAsText("name")).contains("취업 준비");
        var created=send(admin,"POST","/posts",Map.of("saveIntent","AUTOSAVE","title","취업 준비 기록","content","","mediaIds",List.of(),"classification",Map.of("typeCode","REVIEW","cohortIds",List.of(),"topicIds",List.of(topic))),csrf);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);long post=body(created).path("id").asLong();
        assertThat(body(admin.get(API+"/posts?typeCodes=REVIEW&topicIds="+topic)).path("items").get(0).path("id").asLong()).isEqualTo(post);
        assertThat(body(admin.get(API+"/posts?typeCodes=REVIEW&topicIds=71")).path("total").asLong()).isZero();
        ok(send(admin,"DELETE","/content-nodes/"+item,null,csrf));
        assertThat(jdbc.queryForObject("SELECT topic_id FROM post_topics WHERE post_id=?",Long.class,post)).isEqualTo(topic);
        nodes=ok(send(admin,"POST","/pages/70/content-nodes",Map.of("name","취업 준비"),csrf));
        assertThat(nodes.get(1).path("topicId").asLong()).isEqualTo(topic);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM topics",Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT revision FROM site_pages WHERE id=70",Long.class)).isEqualTo(2L);
        assertThat(new HttpBrowser(port).get("/api/public/v1/menus").body()).isEqualTo(menus);
        assertThat(new HttpBrowser(port).get("/api/public/v1/structure").body()).isEqualTo(structure);
        assertThat(new HttpBrowser(port).get("/api/public/v1/pages/70").body()).isEqualTo(page);
    }

    @Test void nameResolutionIsScopedAndDoesNotReviveOrGuessTopics()throws Exception {
        var admin=login("SUPER_ADMIN");String csrf=csrf(admin);
        ok(send(admin,"PUT","/pages/70/composition",composition("REVIEW"),csrf));
        // The FAQ topic with the same label must not be shared or reclassified automatically.
        var nodes=ok(send(admin,"POST","/pages/70/content-nodes",Map.of("name","준비사항"),csrf));
        assertThat(nodes.get(0).path("topicId").asLong()).isNotEqualTo(301L);
        assertThat(jdbc.queryForList("SELECT type_code FROM content_type_topics WHERE topic_id=301",String.class)).containsExactly("FAQ");
        // A linked content type with no topics can create its first item.
        ok(send(admin,"PUT","/pages/72/composition",composition("GENERAL"),csrf));
        ok(send(admin,"POST","/pages/72/content-nodes",Map.of("name","새 소식"),csrf));
        int count=jdbc.queryForObject("SELECT COUNT(*) FROM topics",Integer.class);
        assertThat(failure(send(admin,"POST","/pages/70/content-nodes",Map.of("name","폐지"),csrf),400)).contains("사용 중지");
        assertThat(failure(send(admin,"POST","/pages/80/content-nodes",Map.of("name","잘못된 위치"),csrf),400)).contains("묶음");
        assertThat(failure(send(admin,"POST","/pages/70/content-nodes",Map.of("name"," "),csrf),400)).contains("항목 이름");
        for(String role:List.of("ADMIN","SUPPORTER")){
            var user=login(role);
            assertThat(send(user,"POST","/pages/70/content-nodes",Map.of("name","권한 없는 생성"),csrf(user)).statusCode()).isEqualTo(403);
            assertThat(send(user,"PUT","/content-nodes/"+nodes.get(0).path("id").asLong(),Map.of("name","권한 없는 변경"),csrf(user)).statusCode()).isEqualTo(403);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM topics",Integer.class)).isEqualTo(count);
        jdbc.update("INSERT INTO topics(id,code,name) VALUES(601,'AMBIGUOUS_LIFE','생활')");
        jdbc.update("INSERT INTO content_type_topics(type_code,topic_id) VALUES('REVIEW',601)");
        assertThat(failure(send(admin,"POST","/pages/70/content-nodes",Map.of("name","생활"),csrf),400)).contains("여러 개");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_work_nodes WHERE page_id=70",Integer.class)).isEqualTo(1);
    }

    @Test void nodeFailureRollsBackTheNewTopicTypeLinkAndAudit()throws Exception {
        var admin=login("SUPER_ADMIN");String csrf=csrf(admin);
        ok(send(admin,"PUT","/pages/70/composition",composition("REVIEW"),csrf));
        long audit=jdbc.queryForObject("SELECT COUNT(*) FROM activity_log",Long.class);
        jdbc.execute("ALTER TABLE content_work_nodes ADD CONSTRAINT reject_test_node CHECK(name <> '저장 실패')");
        try{
            assertThat(send(admin,"POST","/pages/70/content-nodes",Map.of("name","저장 실패"),csrf).statusCode()).isEqualTo(500);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM topics",Integer.class)).isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_type_topics",Integer.class)).isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_work_nodes",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log",Long.class)).isEqualTo(audit);
        }finally{jdbc.execute("ALTER TABLE content_work_nodes DROP CONSTRAINT reject_test_node");}
    }

    @Test void changingTheLinkRemovesTheNodesAndDeletingThePageCascades()throws Exception {
        var admin=login("SUPER_ADMIN");String csrf=csrf(admin);
        var anonymous=new HttpBrowser(port);
        jdbc.update("INSERT INTO site_menus(label,kind,target_id,sort_order) VALUES('후기','PAGE',70,0)");
        String menus=anonymous.get("/api/public/v1/menus").body(),structure=anonymous.get("/api/public/v1/structure").body(),page=anonymous.get("/api/public/v1/pages/70").body();
        ok(send(admin,"PUT","/pages/70/composition",composition("REVIEW"),csrf));
        ok(send(admin,"POST","/pages/70/content-nodes",node("프로젝트 후기",95L),csrf));
        ok(send(admin,"POST","/pages/70/content-nodes",node("생활 후기",71L),csrf));
        // Unlinking removes the nodes with the link; the audit says so and posts/topics stay.
        ok(send(admin,"PUT","/pages/70/composition",composition(null),csrf));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_work_nodes",Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT detail FROM activity_log WHERE action='사이트 구성 변경' ORDER BY id DESC LIMIT 1",String.class).get(0)).contains("하위 항목 2개 제거");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM topics",Integer.class)).isEqualTo(5);
        // Relinking to another type also drops nodes of the old type; nodes of the new type are added afresh.
        ok(send(admin,"PUT","/pages/70/composition",composition("REVIEW"),csrf));
        ok(send(admin,"POST","/pages/70/content-nodes",node("프로젝트 후기",95L),csrf));
        ok(send(admin,"PUT","/pages/70/composition",composition("FAQ"),csrf));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_work_nodes",Integer.class)).isZero();
        assertThat(failure(send(admin,"POST","/pages/70/content-nodes",node("프로젝트",95L),csrf),400)).contains("허용되지");
        ok(send(admin,"POST","/pages/70/content-nodes",node("준비사항",301L),csrf));
        // Menu settings alone keep the nodes.
        var keep=new LinkedHashMap<String,Object>(composition("FAQ"));keep.put("menuVisible",true);keep.put("menuLabel","자주 묻는 질문");
        ok(send(admin,"PUT","/pages/70/composition",keep,csrf));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_work_nodes",Integer.class)).isEqualTo(1);
        // Nodes are admin navigation only: public menus, structure and the page are byte for byte unchanged.
        assertThat(anonymous.get("/api/public/v1/menus").body()).isEqualTo(menus);
        assertThat(anonymous.get("/api/public/v1/structure").body()).isEqualTo(structure);
        assertThat(anonymous.get("/api/public/v1/pages/70").body()).isEqualTo(page);
        // Deleting the page takes its nodes with it (DB cascade); the delete impact says so.
        jdbc.update("DELETE FROM site_menus");
        assertThat(body(admin.get(API+"/pages/70/delete-impact")).path("consequence").asText()).contains("하위 항목");
        ok(send(admin,"PUT","/pages/70/composition",composition(null),csrf));
        ok(send(admin,"PUT","/pages/72/composition",composition("FAQ"),csrf));
        ok(send(admin,"POST","/pages/72/content-nodes",node("준비사항",301L),csrf));
        assertThat(send(admin,"DELETE","/pages/72",Map.of("revision",2,"confirmed",true),csrf).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_work_nodes",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM topics WHERE id=301",Integer.class)).isEqualTo(1);
    }
}
