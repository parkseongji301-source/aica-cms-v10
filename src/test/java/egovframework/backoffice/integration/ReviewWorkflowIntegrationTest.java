package egovframework.backoffice.integration;

import com.fasterxml.jackson.databind.*;
import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.post.PostService;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:review-workflow;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ReviewWorkflowIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountMapper accounts;
    @Autowired PostService posts;
    @Autowired PageService pages;
    @Autowired SiteService site;
    static final String API="/api/admin/next/posts";
    static final String PASSWORD="Review-Workflow-Test42";
    long c6,c7,life,classes,project,legacy,pageId;

    @BeforeEach void fixture()throws Exception {
        for(String table:List.of("post_publication_media","post_media","post_publications","page_media","page_publications","site_pages","posts","media","site_menus","categories","activity_log","users","content_type_topics","cohorts","topics"))jdbc.update("DELETE FROM "+table);
        String hash=encoder.encode(PASSWORD);
        for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,?,FALSE)",role.toLowerCase(Locale.ROOT)+"@review.test",role,hash,role);
        jdbc.update("INSERT INTO categories(id,name) VALUES(11,'기존 분류')");
        legacy=LegacyCategories.assign(jdbc,11L,posts.save(actor("ADMIN"),null,null,"기존 일반 콘텐츠","기존 본문",null,List.of(),"save",null));
        pageId=LegacyCategories.page(jdbc,"[{\"type\":\"POSTS\",\"categoryId\":11,\"visible\":true}]",s->pages.save(actor("SUPER_ADMIN"),null,null,"기존 페이지","review-test",s,"publish"));
        jdbc.update("INSERT INTO site_menus(label,kind,target_id,url,visible,sort_order) SELECT name,'CATEGORY',id,'',TRUE,1 FROM categories WHERE id=11");
        seed();c6=id("cohorts","COHORT_06");c7=id("cohorts","COHORT_07");
        life=id("topics","REVIEW_LIFE");classes=id("topics","REVIEW_CLASS");project=id("topics","REVIEW_PROJECT");
    }
    void seed()throws Exception {try(var c=jdbc.getDataSource().getConnection()){ScriptUtils.executeSqlScript(c,new org.springframework.core.io.support.EncodedResource(new FileSystemResource(Path.of("workbench/review-candidate/vocabulary.sql")),java.nio.charset.StandardCharsets.UTF_8));}}
    long id(String table,String code){return jdbc.queryForObject("SELECT id FROM "+table+" WHERE code=?",Long.class,code);}
    AccountPrincipal actor(String role){return new AccountPrincipal(accounts.findByEmail(role.toLowerCase(Locale.ROOT)+"@review.test"));}
    HttpBrowser login(String role)throws Exception {var b=new HttpBrowser(port);b.login(role.toLowerCase(Locale.ROOT)+"@review.test",PASSWORD,"/admin");return b;}
    JsonNode get(HttpBrowser b,String path)throws Exception {var response=b.get(path);assertThat(response.statusCode()).as(response.body()).isEqualTo(200);return json.readTree(response.body());}
    String csrf(HttpBrowser b)throws Exception{return get(b,"/api/admin/next/bootstrap").path("csrf").path("token").asText();}
    Map<String,Object> selection(List<Long> cohorts,List<Long> topics){return Map.of("typeCode","REVIEW","cohortIds",cohorts,"topicIds",topics);}
    Map<String,Object> body(String title,List<Long> cohorts,List<Long> topics){return new LinkedHashMap<>(Map.of("title",title,"content","후기 본문","categoryId",11,"mediaIds",List.of(),"classification",selection(cohorts,topics)));}
    JsonNode create(HttpBrowser b,String title,List<Long> cohorts,List<Long> topics)throws Exception {
        var value=body(title,cohorts,topics);value.put("categoryId",null);
        var response=b.json("POST",API,json.writeValueAsString(value),csrf(b));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        long id=LegacyCategories.assign(jdbc,11L,json.readTree(response.body()).path("id").asLong());return get(b,API+"/"+id);
    }
    JsonNode update(HttpBrowser b,JsonNode post,List<Long> cohorts,List<Long> topics)throws Exception {
        var value=body(post.path("title").asText(),cohorts,topics);value.put("revision",post.path("revision").asLong());
        var response=b.json("PUT",API+"/"+post.path("id").asLong(),json.writeValueAsString(value),csrf(b));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);return json.readTree(response.body());
    }
    void publish(HttpBrowser b,JsonNode p)throws Exception {
        HttpBrowser.redirect(b.post("/admin/posts/"+p.path("id").asLong()+"/edit",Map.of("revision",p.path("revision").asText(),"title",p.path("title").asText(),"content",p.path("content").asText(),"categoryId","11","action","publish")),"/admin/legacy/posts/"+p.path("id").asLong());
    }

    @Test void candidateDictionaryIsSeparateIdempotentAndDoesNotCreateIaOrContent()throws Exception {
        var before=jdbc.queryForList("SELECT * FROM topics ORDER BY id");seed();
        assertThat(jdbc.queryForList("SELECT * FROM topics ORDER BY id")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cohorts",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT code FROM topics ORDER BY sort_order",String.class)).containsExactly("REVIEW_LIFE","REVIEW_CLASS","REVIEW_PROJECT");
        assertThat(jdbc.queryForList("SELECT type_code FROM content_type_topics",String.class)).containsOnly("REVIEW").hasSize(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_pages",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_menus",Integer.class)).isEqualTo(1);
        assertThat(posts.classification(actor("ADMIN"),legacy).typeCode()).isEqualTo("GENERAL");
    }
    @Test void fourReviewListsCountSingleAndMultipleAssignmentsWithoutDuplicateRows()throws Exception {
        var b=login("ADMIN");
        var a=create(b,"7기 생활 후기",List.of(c7),List.of(life));
        var comparison=create(b,"6기 7기 수업 비교",List.of(c6,c7),List.of(classes));
        var combined=create(b,"여러 주제 후기",List.of(c6),List.of(life,classes,project));
        create(b,"기수 주제 미선택 후기",List.of(),List.of());
        for(var pair:List.of(Map.entry("",4),Map.entry("&topicIds="+life,2),Map.entry("&topicIds="+classes,2),Map.entry("&topicIds="+project,1),Map.entry("&cohortIds="+c6+","+c7+"&topicIds="+life+","+classes,3))) {
            var result=get(b,API+"?typeCodes=REVIEW"+pair.getKey());
            assertThat(result.path("total").asInt()).isEqualTo(pair.getValue());
            assertThat(result.path("items").size()).isEqualTo(pair.getValue());
            var ids=new ArrayList<Long>();result.path("items").forEach(p->ids.add(p.path("id").asLong()));
            assertThat(ids).doesNotHaveDuplicates();
        }
        long contentId=comparison.path("id").asLong();
        for(String mode:List.of("manage","structure")) {
            assertThat(b.get("/admin/posts/"+contentId+"/edit?view="+mode+"&reviewSection=class").statusCode()).isEqualTo(200);
            assertThat(get(b,API+"/"+contentId+"?view="+mode)).isEqualTo(comparison);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts",Integer.class)).isEqualTo(5);
        assertThat(a.path("status").asText()).isEqualTo("DRAFT");
        assertThat(combined.path("classification").path("topicIds").size()).isEqualTo(3);
    }
    @Test void reviewDraftAndLegacyPublicationKeepCategoryAndIndependentClassification()throws Exception {
        var b=login("ADMIN");var post=create(b,"분류 변경 후기",List.of(c7),List.of(life));long id=post.path("id").asLong();
        publish(b,post);post=get(b,API+"/"+id);
        var draft=update(b,post,List.of(c6,c7),List.of(project));
        assertThat(get(b,API+"/"+id+"/publication").path("classification").path("topicIds").toString()).isEqualTo("["+life+"]");
        // Old Thymeleaf JSON form omits classification; preserve it on save and publication.
        var legacyBody=Map.of("id",String.valueOf(id),"title",draft.path("title").asText(),"content","후기 본문","categoryId","11","revision",draft.path("revision").asText(),"action","save");
        var oldSave=b.post("/admin/posts/save-json",legacyBody);
        assertThat(oldSave.statusCode()).as(oldSave.body()).isEqualTo(200);
        var saved=get(b,API+"/"+id);assertThat(saved.path("classification")).isEqualTo(draft.path("classification"));
        publish(b,saved);var published=get(b,API+"/"+id+"/publication");
        assertThat(published.path("classification").path("topicIds").toString()).isEqualTo("["+project+"]");
        assertThat(published.path("classification").path("cohortIds").size()).isEqualTo(2);
        var stale=body("충돌",List.of(),List.of());stale.put("revision",post.path("revision").asLong());
        assertThat(b.json("PUT",API+"/"+id,json.writeValueAsString(stale),csrf(b)).statusCode()).isEqualTo(409);
        assertThat(get(b,API+"/"+id).path("categoryId").asLong()).isEqualTo(11);
        assertThat(jdbc.queryForObject("SELECT sections_json FROM site_pages WHERE id=?",String.class,pageId)).contains("\"categoryId\":11");
        assertThat(jdbc.queryForObject("SELECT target_id FROM site_menus",Long.class)).isEqualTo(11);
    }
    @Test void draftCreationEnforcesAuthenticationCsrfValidationAndOwnership()throws Exception {
        var anonymous=new HttpBrowser(port);var b=login("SUPPORTER");String value=json.writeValueAsString(body("새 후기",List.of(),List.of()));
        assertThat(anonymous.json("POST",API,value,null).statusCode()).isIn(401,403);
        assertThat(b.json("POST",API,value,null).statusCode()).isEqualTo(403);
        for(var invalid:List.of(body("",List.of(),List.of()),body("중복",List.of(c6,c6),List.of()),body("없는 주제",List.of(),List.of(99999L))))
            assertThat(b.json("POST",API,json.writeValueAsString(invalid),csrf(b)).statusCode()).isEqualTo(400);
        var wrongType=body("허용 안 됨",List.of(),List.of());wrongType.put("classification",Map.of("typeCode","FAQ","cohortIds",List.of(),"topicIds",List.of(life)));
        assertThat(b.json("POST",API,json.writeValueAsString(wrongType),csrf(b)).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts",Integer.class)).isEqualTo(1);
        var post=create(b,"내 후기",List.of(),List.of());
        assertThat(post.path("authorId").asLong()).isEqualTo(actor("SUPPORTER").getId());
        assertThat(post.path("status").asText()).isEqualTo("DRAFT");
        assertThat(b.get(API+"/"+legacy).statusCode()).isEqualTo(403);
        assertThat(get(b,API+"?typeCodes=REVIEW").path("total").asInt()).isEqualTo(1);
    }
}
