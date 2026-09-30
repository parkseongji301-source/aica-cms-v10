package egovframework.backoffice.integration;

import com.fasterxml.jackson.databind.*;
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
    properties="spring.datasource.url=jdbc:h2:mem:faq-workflow;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class FaqWorkflowIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    static final String API="/api/admin/next/posts",PASSWORD="Faq-Workflow-Test42";
    long preparation,life,project,reviewLife;
    @BeforeEach void fixture()throws Exception {
        for(String table:List.of("post_publication_media","post_media","post_publications","page_media","page_publications","site_pages","posts","media","site_menus","categories","activity_log","users","content_type_topics","cohorts","topics"))jdbc.update("DELETE FROM "+table);
        jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES('admin@faq.test','FAQ 운영자',?,'ADMIN',FALSE)",encoder.encode(PASSWORD));
        jdbc.update("INSERT INTO categories(id,name) VALUES(11,'기존 분류')");
        seed("review");seed("faq");
        preparation=id("FAQ_PREPARATION");life=id("FAQ_LIFE");project=id("FAQ_PROJECT");reviewLife=id("REVIEW_LIFE");
    }
    void seed(String kind)throws Exception {try(var c=jdbc.getDataSource().getConnection()){ScriptUtils.executeSqlScript(c,new org.springframework.core.io.support.EncodedResource(new FileSystemResource(Path.of("workbench/"+kind+"-candidate/vocabulary.sql")),java.nio.charset.StandardCharsets.UTF_8));}}
    long id(String code){return jdbc.queryForObject("SELECT id FROM topics WHERE code=?",Long.class,code);}
    HttpBrowser login()throws Exception {var b=new HttpBrowser(port);b.login("admin@faq.test",PASSWORD,"/admin");return b;}
    JsonNode get(HttpBrowser b,String path)throws Exception {var r=b.get(path);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
    String csrf(HttpBrowser b)throws Exception{return get(b,"/api/admin/next/bootstrap").path("csrf").path("token").asText();}
    Map<String,Object> body(String type,String question,String answer,List<Long> topics){return new LinkedHashMap<>(Map.of("title",question,"content",answer,"categoryId",11,"mediaIds",List.of(),"classification",Map.of("typeCode",type,"cohortIds",List.of(),"topicIds",topics)));}
    JsonNode create(HttpBrowser b,String question,List<Long> topics)throws Exception {
        var r=b.json("POST",API,json.writeValueAsString(body("FAQ",question,"검증용 답변입니다. 실제 지원 안내가 아닙니다.",topics)),csrf(b));
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);return json.readTree(r.body());
    }
    JsonNode save(HttpBrowser b,JsonNode old,String question,String answer,List<Long> topics)throws Exception {
        var value=body("FAQ",question,answer,topics);value.put("revision",old.path("revision").asLong());
        var r=b.json("PUT",API+"/"+old.path("id").asLong(),json.writeValueAsString(value),csrf(b));
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());
    }
    void legacy(HttpBrowser b,JsonNode p,String action)throws Exception {
        var r=b.post("/admin/posts/"+p.path("id").asLong()+"/edit",Map.of("revision",p.path("revision").asText(),"title",p.path("title").asText(),"content",p.path("content").asText(),"categoryId","11","action",action));
        HttpBrowser.redirect(r,"/admin/legacy/posts/"+p.path("id").asLong());
    }
    @Test void candidateDictionaryIsIdempotentAndKeepsSameNamedTopicsSeparate()throws Exception {
        var before=jdbc.queryForList("SELECT * FROM topics ORDER BY id");seed("faq");
        assertThat(jdbc.queryForList("SELECT * FROM topics ORDER BY id")).isEqualTo(before);
        assertThat(life).isNotEqualTo(reviewLife);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM topics WHERE name='생활'",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_type_topics WHERE type_code='FAQ'",Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_type_topics WHERE type_code='REVIEW'",Integer.class)).isEqualTo(3);
        for(String table:List.of("posts","site_pages","site_menus"))assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class)).isZero();
    }
    @Test void fourQuestionsAllSevenFiltersCountsAndReviewLifeAreIsolated()throws Exception {
        var b=login();create(b,"포트폴리오가 꼭 필요한가요?",List.of(preparation));create(b,"코딩테스트 많이 어렵나요?",List.of(preparation));
        var parking=create(b,"주차 공간이 있나요?",List.of(life));create(b,"팀 구성은 어떻게 하나요?",List.of(project));
        var review=b.json("POST",API,json.writeValueAsString(body("REVIEW","생활 후기","후기 본문",List.of(reviewLife))),csrf(b));assertThat(review.statusCode()).isEqualTo(201);
        assertThat(get(b,API+"?typeCodes=FAQ").path("total").asInt()).isEqualTo(4);
        for(String code:List.of("FAQ_PREPARATION","FAQ_APPLICATION","FAQ_CLASS","FAQ_LIFE","FAQ_EMPLOYMENT","FAQ_ALLOWANCE","FAQ_PROJECT")) {
            int count=code.equals("FAQ_PREPARATION")?2:List.of("FAQ_LIFE","FAQ_PROJECT").contains(code)?1:0;
            var list=get(b,API+"?typeCodes=FAQ&topicIds="+id(code));assertThat(list.path("total").asInt()).isEqualTo(count);assertThat(list.path("items").size()).isEqualTo(count);
        }
        assertThat(get(b,API+"?typeCodes=FAQ&topicIds="+reviewLife).path("total").asInt()).isZero();
        assertThat(get(b,API+"?typeCodes=REVIEW&topicIds="+life).path("total").asInt()).isZero();
        assertThat(get(b,API+"?typeCodes=REVIEW&topicIds="+reviewLife).path("total").asInt()).isEqualTo(1);
        for(String mode:List.of("manage","structure")) {
            assertThat(b.get("/admin/posts/"+parking.path("id").asLong()+"/edit?view="+mode+"&faqSection=life").statusCode()).isEqualTo(200);
            assertThat(get(b,API+"/"+parking.path("id").asLong()+"?view="+mode)).isEqualTo(parking);
        }
        var updated=save(b,parking,parking.path("title").asText(),"복수 주제 답변",List.of(life,project));
        var combined=get(b,API+"?typeCodes=FAQ&topicIds="+life+","+project);assertThat(combined.path("total").asInt()).isEqualTo(2);assertThat(combined.path("items").size()).isEqualTo(2);
        assertThat(updated.path("classification").path("cohortIds").isEmpty()).isTrue();
    }
    @Test void questionAnswerUseOriginalFieldsAndPublicationChangesOnlyAfterLegacyRepublish()throws Exception {
        var b=login();var post=create(b,"주차 공간이 있나요?",List.of(life));long id=post.path("id").asLong();
        legacy(b,post,"publish");post=get(b,API+"/"+id);var before=get(b,API+"/"+id+"/publication");
        var changed=save(b,post,"변경한 질문","변경한 답변",List.of(project));
        assertThat(jdbc.queryForMap("SELECT title,content FROM posts WHERE id=?",id)).containsEntry("TITLE","변경한 질문").containsEntry("CONTENT","변경한 답변");
        assertThat(get(b,API+"/"+id+"/publication")).isEqualTo(before);
        var preview=get(b,API+"/"+id+"/preview");assertThat(preview.path("title").asText()).isEqualTo("변경한 질문");assertThat(preview.path("bodyHtml").asText()).contains("변경한 답변");
        legacy(b,changed,"save");var saved=get(b,API+"/"+id);assertThat(saved.path("classification")).isEqualTo(changed.path("classification"));
        legacy(b,saved,"publish");var pub=get(b,API+"/"+id+"/publication");
        assertThat(pub.path("classification")).isEqualTo(changed.path("classification"));assertThat(pub.path("post").path("content").asText()).isEqualTo("변경한 답변");
        assertThat(get(b,API+"/"+id).path("categoryId").asLong()).isEqualTo(11);
        var stale=body("FAQ","덮어쓰기","실패해야 함",List.of());stale.put("revision",post.path("revision").asLong());
        assertThat(b.json("PUT",API+"/"+id,json.writeValueAsString(stale),csrf(b)).statusCode()).isEqualTo(409);
    }
    @Test void faqValidationDoesNotRequireCohortMediaOrDraftAnswerAndRejectsForeignTopic()throws Exception {
        var b=login();var emptyAnswer=body("FAQ","질문만 있는 초안","",List.of());
        var r=b.json("POST",API,json.writeValueAsString(emptyAnswer),csrf(b));assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        var p=json.readTree(r.body());assertThat(p.path("mediaIds").isEmpty()).isTrue();assertThat(p.path("classification").path("cohortIds").isEmpty()).isTrue();
        for(String title:List.of("","x".repeat(201))) {
            var invalid=b.json("POST",API,json.writeValueAsString(body("FAQ",title,"답변",List.of())),csrf(b));
            assertThat(invalid.statusCode()).isEqualTo(400);assertThat(invalid.body()).contains("질문");
        }
        var invalid=body("REVIEW",p.path("title").asText(),"본문",List.of(life));invalid.put("revision",p.path("revision").asLong());
        assertThat(b.json("PUT",API+"/"+p.path("id").asLong(),json.writeValueAsString(invalid),csrf(b)).statusCode()).isEqualTo(400);
        assertThat(get(b,API+"/"+p.path("id").asLong())).isEqualTo(p);
        assertThat(b.json("POST",API,json.writeValueAsString(body("FAQ","잘못된 주제","답변",List.of(reviewLife))),csrf(b)).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts",Integer.class)).isEqualTo(1);
    }
}
