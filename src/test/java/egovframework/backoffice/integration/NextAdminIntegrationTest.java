package egovframework.backoffice.integration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import static egovframework.backoffice.integration.HttpBrowser.*;

/** Real HTTP sessions and an isolated in-memory DB; never writes to the local operator DB. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:next-pilot-integration;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class NextAdminIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    private static final String PASSWORD="Test-Pilot-Password42";
    private static final String API="/api/admin/next";
    private static final String SECTIONS="[{\"type\":\"TEXT\",\"heading\":\"원본 섹션\",\"body\":\"원본 본문\",\"visible\":true}]";

    @BeforeEach void fixture() {
        for(String table:List.of("page_media","page_publications","site_pages","site_menus","activity_log","users"))jdbc.update("DELETE FROM "+table);
        String hash=encoder.encode(PASSWORD);
        for(var role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,?,FALSE)",role.toLowerCase(Locale.ROOT)+"@test.local",role,hash,role);
        long actor=jdbc.queryForObject("SELECT id FROM users WHERE role='SUPER_ADMIN'",Long.class);
        jdbc.update("INSERT INTO site_pages(id,title,slug,sections_json,status,revision,author_id) VALUES(65,'인사교 소개','about',?,'PUBLISHED',4,?)",SECTIONS,actor);
        jdbc.update("INSERT INTO page_publications(page_id,title,slug,sections_json,revision) SELECT id,title,slug,sections_json,revision FROM site_pages WHERE id=65");
        jdbc.update("INSERT INTO site_menus(label,kind,target_id,visible) VALUES('인사교 소개','PAGE',65,TRUE)");
        upgradeFixture();
    }
    private void upgradeFixture() {jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) c->{try{db.migration.h2.V8__page_block_identity.upgrade(c);}catch(Exception e){throw new IllegalStateException(e);}return null;});}
    private HttpBrowser login(String role)throws Exception {var b=new HttpBrowser(port);b.login(role+"@test.local",PASSWORD,"/admin");return b;}
    private JsonNode boot(HttpBrowser b)throws Exception {var r=b.get(API+"/bootstrap");assertThat(r.statusCode()).isEqualTo(200);return json.readTree(r.body());}
    private String token(HttpBrowser b)throws Exception{return boot(b).path("csrf").path("token").asText();}
    private ObjectNode edit(String title,long revision)throws Exception {var value=json.createObjectNode();value.put("revision",revision);value.put("title",title);value.set("sections",json.readTree(jdbc.queryForObject("SELECT sections_json FROM site_pages WHERE id=65",String.class)));return value;}
    private JsonNode ok(HttpResponse<String> response)throws Exception {assertThat(response.statusCode()).as(response.body()).isEqualTo(200);return json.readTree(response.body());}
    private void error(HttpResponse<String> response,int status)throws Exception {assertThat(response.statusCode()).as(response.body()).isEqualTo(status);assertThat(response.headers().firstValue("content-type").orElse("")).contains("application/json");assertThat(json.readTree(response.body()).hasNonNull("code")).isTrue();}

    @Test void reactPublishWithdrawAndAddressChangeFollowTheLegacyPageRules()throws Exception {
        var admin=login("ADMIN");String csrf=token(admin);
        var draft=ok(admin.json("PUT",API+"/pages/65",edit("새 초안 제목",4).toString(),csrf));
        long revision=draft.path("revision").asLong();assertThat(draft.path("pending").asBoolean()).isTrue();
        assertThat(ok(admin.get(API+"/pages/65/publication")).path("title").asText()).isEqualTo("인사교 소개");
        var moved=edit("새 초안 제목",revision);moved.put("slug","new-about");
        error(admin.json("POST",API+"/pages/65/publish",moved.toString(),csrf),403);
        var supporter=login("SUPPORTER");String supporterCsrf=token(supporter);
        error(supporter.json("POST",API+"/pages/65/publish",edit("지원자",revision).toString(),supporterCsrf),403);
        error(admin.json("POST",API+"/pages/65/publish",edit("CSRF 없음",revision).toString(),null),403);
        error(admin.json("POST",API+"/pages/65/publish",edit("오래된 버전",revision-1).toString(),csrf),409);
        assertThat(jdbc.queryForObject("SELECT slug FROM site_pages WHERE id=65",String.class)).isEqualTo("about");
        assertThat(ok(admin.get(API+"/pages/65/publication")).path("title").asText()).isEqualTo("인사교 소개");
        var published=ok(admin.json("POST",API+"/pages/65/publish",edit("새 초안 제목",revision).toString(),csrf));
        assertThat(published.path("status").asText()).isEqualTo("PUBLISHED");assertThat(published.path("pending").asBoolean()).isFalse();
        var publication=ok(admin.get(API+"/pages/65/publication"));
        assertThat(publication.path("title").asText()).isEqualTo("새 초안 제목");assertThat(publication.path("slug").asText()).isEqualTo("about");
        var root=login("SUPER_ADMIN");String rootCsrf=token(root);
        var renamed=edit("새 초안 제목",published.path("revision").asLong());renamed.put("slug","new-about");
        var addressed=ok(root.json("POST",API+"/pages/65/publish",renamed.toString(),rootCsrf));
        assertThat(addressed.path("slug").asText()).isEqualTo("new-about");
        assertThat(ok(root.get(API+"/pages/65/publication")).path("slug").asText()).isEqualTo("new-about");
        long current=addressed.path("revision").asLong();
        error(admin.json("POST",API+"/pages/65/unpublish",json.writeValueAsString(Map.of("revision",current-1)),csrf),409);
        error(supporter.json("POST",API+"/pages/65/unpublish",json.writeValueAsString(Map.of("revision",current)),supporterCsrf),403);
        var withdrawn=ok(admin.json("POST",API+"/pages/65/unpublish",json.writeValueAsString(Map.of("revision",current)),csrf));
        assertThat(withdrawn.path("status").asText()).isNotEqualTo("PUBLISHED");assertThat(withdrawn.path("title").asText()).isEqualTo("새 초안 제목");
        assertThat(withdrawn.path("status").asText()).isEqualTo("PRIVATE");
        assertThat(new HttpBrowser(port).get("/api/public/v1/pages/65").statusCode()).isNotEqualTo(200);
    }
    @Test void contentCollectionPresetCreatesAnOrdinaryDraftPageWithAPostsQueryBlock()throws Exception {
        String preset="[{\"id\":\"block_"+java.util.UUID.randomUUID()+"\",\"schemaVersion\":2,\"type\":\"POSTS\",\"variation\":\"default\",\"heading\":\"후기 모음\",\"body\":\"\",\"bodyDoc\":null,\"imageId\":null,\"categoryId\":null,\"link\":\"\",\"label\":\"\",\"visible\":true,"
            +"\"sourceMode\":\"query\",\"query\":{\"typeCode\":\"%s\",\"cohortIds\":[],\"topicIds\":[],\"sort\":\"LATEST\",\"limit\":6},\"manual\":null}]";
        var root=login("SUPER_ADMIN");int pagesBefore=jdbc.queryForObject("SELECT COUNT(*) FROM site_pages",Integer.class);
        var rejected=root.post("/admin/pages/save-json",Map.of("title","잘못된 모음","slug","","sectionsJson",preset.formatted("UNKNOWN"),"action","save","saveIntent","MANUAL_DRAFT"));
        assertThat(rejected.statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_pages",Integer.class)).isEqualTo(pagesBefore);
        var response=root.post("/admin/pages/save-json",Map.of("title","후기 모음","slug","","sectionsJson",preset.formatted("REVIEW"),"action","save","saveIntent","MANUAL_DRAFT"));
        var created=ok(response);long id=created.path("id").asLong();
        assertThat(created.path("status").asText()).isEqualTo("DRAFT");
        var page=ok(root.get(API+"/pages/"+id));var block=page.path("sections").get(0);
        assertThat(page.path("sections").size()).isEqualTo(1);assertThat(block.path("type").asText()).isEqualTo("POSTS");
        assertThat(block.path("sourceMode").asText()).isEqualTo("query");assertThat(block.path("id").asText()).startsWith("block_");
        assertThat(block.path("query").path("typeCode").asText()).isEqualTo("REVIEW");assertThat(block.path("query").path("limit").asInt()).isEqualTo(6);
        assertThat(block.path("categoryId").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM page_publications WHERE page_id=?",Integer.class,id)).isZero();
        String csrf=token(root);var published=ok(root.json("POST",API+"/pages/"+id+"/publish",json.writeValueAsString(Map.of("revision",page.path("revision").asLong(),"title","후기 모음","sections",page.path("sections"))),csrf));
        assertThat(published.path("status").asText()).isEqualTo("PUBLISHED");
        var anonymous=new HttpBrowser(port);
        assertThat(anonymous.get("/api/public/v1/pages/"+id+"/blocks/"+block.path("id").asText()+"/posts").statusCode()).isEqualTo(200);
    }
    @Test void addressChangeIsADraftChangeForSuperAdminsAndGoesPublicAtTheNextPublish()throws Exception {
        var admin=login("ADMIN");String adminCsrf=token(admin);
        error(admin.json("PUT",API+"/pages/65/address",json.writeValueAsString(Map.of("revision",4,"slug","about-us")),adminCsrf),403);
        var root=login("SUPER_ADMIN");String csrf=token(root);
        error(root.json("PUT",API+"/pages/65/address",json.writeValueAsString(Map.of("revision",4,"slug","about")),csrf),400);
        error(root.json("PUT",API+"/pages/65/address",json.writeValueAsString(Map.of("revision",4,"slug","Bad Address")),csrf),400);
        error(root.json("PUT",API+"/pages/65/address",json.writeValueAsString(Map.of("revision",3,"slug","about-us")),csrf),409);
        error(root.json("PUT",API+"/pages/65/address",json.writeValueAsString(Map.of("revision",4,"slug","about-us")),null),403);
        var changed=ok(root.json("PUT",API+"/pages/65/address",json.writeValueAsString(Map.of("revision",4,"slug","about-us")),csrf));
        assertThat(changed.path("slug").asText()).isEqualTo("about-us");assertThat(changed.path("pending").asBoolean()).isTrue();
        var publishedBlock=ok(root.get(API+"/pages/65/publication")).path("sections").get(0);var draftBlock=changed.path("sections").get(0);
        assertThat(changed.path("sections").size()).isEqualTo(1);
        for(String field:List.of("id","type","heading","body","visible"))assertThat(draftBlock.path(field)).isEqualTo(publishedBlock.path(field));
        var anonymous=new HttpBrowser(port);
        assertThat(anonymous.get("/api/public/v1/pages/by-slug/about").statusCode()).isEqualTo(200);
        assertThat(anonymous.get("/api/public/v1/pages/by-slug/about-us").statusCode()).isEqualTo(404);
        ok(root.json("POST",API+"/pages/65/publish",json.writeValueAsString(Map.of("revision",changed.path("revision").asLong(),"title",changed.path("title").asText(),"sections",changed.path("sections"))),csrf));
        assertThat(anonymous.get("/api/public/v1/pages/by-slug/about-us").statusCode()).isEqualTo(200);
        assertThat(anonymous.get("/api/public/v1/pages/by-slug/about").statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT target_id FROM site_menus WHERE kind='PAGE'",Long.class)).isEqualTo(65L);
    }
    @Test void reactPermanentPageDeleteUsesTheLegacyImpactAndUsageRules()throws Exception {
        long author=jdbc.queryForObject("SELECT id FROM users WHERE role='SUPER_ADMIN'",Long.class);
        jdbc.update("INSERT INTO site_pages(id,title,slug,sections_json,status,revision,author_id) VALUES(66,'정리할 페이지','cleanup','[]','DRAFT',1,?)",author);
        var admin=login("ADMIN");String adminCsrf=token(admin);
        error(admin.get(API+"/pages/66/delete-impact"),403);
        error(admin.json("DELETE",API+"/pages/66",json.writeValueAsString(Map.of("revision",1,"confirmed",true)),adminCsrf),403);
        var root=login("SUPER_ADMIN");String csrf=token(root);
        var used=ok(root.get(API+"/pages/65/delete-impact"));
        assertThat(used.path("uses").size()).isPositive();assertThat(used.path("revision").asLong()).isEqualTo(4);
        error(root.json("DELETE",API+"/pages/65",json.writeValueAsString(Map.of("revision",4,"confirmed",true)),csrf),400);
        assertThat(ok(root.get(API+"/pages/66/delete-impact")).path("title").asText()).isEqualTo("정리할 페이지");
        error(root.json("DELETE",API+"/pages/66",json.writeValueAsString(Map.of("revision",1,"confirmed",false)),csrf),400);
        error(root.json("DELETE",API+"/pages/66",json.writeValueAsString(Map.of("revision",0,"confirmed",true)),csrf),409);
        error(root.json("DELETE",API+"/pages/66",json.writeValueAsString(Map.of("revision",1,"confirmed",true)),null),403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_pages WHERE id=66",Integer.class)).isEqualTo(1);
        assertThat(ok(root.json("DELETE",API+"/pages/66",json.writeValueAsString(Map.of("revision",1,"confirmed",true)),csrf)).path("deleted").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_pages WHERE id=66",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_pages WHERE id=65",Integer.class)).isEqualTo(1);
    }
    @Test void bothEntrancesShareThePageAndSavingRoundTripsThroughLegacyEditor()throws Exception {
        var admin=login("ADMIN");var boot=boot(admin);String csrf=boot.path("csrf").path("token").asText();
        assertThat(boot.path("pages").get(0).path("id").asLong()).isEqualTo(65);
        assertThat(boot.path("menus").get(0).path("targetId").asLong()).isEqualTo(65);
        for(String path:List.of("/admin-next?view=manage","/admin-next/pages/65/edit?view=structure")) {
            var response=admin.get(path);assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("id=\"root\"","/next-app/assets/");
        }
        var request=edit("React에서 수정",4);request.put("id",999);request.put("action","publish");request.put("slug","forged");
        var saved=ok(admin.json("PUT",API+"/pages/65",request.toString(),csrf));
        assertThat(saved.path("id").asLong()).isEqualTo(65);
        assertThat(saved.path("revision").asLong()).isEqualTo(5);
        assertThat(saved.path("slug").asText()).isEqualTo("about");
        assertThat(saved.path("pending").asBoolean()).isTrue();
        assertThat(ok(admin.get(API+"/pages/65"))).isEqualTo(saved);
        assertThat(admin.get("/admin/pages/65/edit").body()).contains("React에서 수정");
        redirect(admin.post("/admin/pages/save",Map.of("id","65","revision","5","title","기존 편집기 수정","slug","about","sectionsJson",saved.path("sections").toString(),"action","save")),"/admin/pages/65/edit");
        assertThat(ok(admin.get(API+"/pages/65")).path("title").asText()).isEqualTo("기존 편집기 수정");
        assertThat(jdbc.queryForObject("SELECT title FROM page_publications WHERE page_id=65",String.class)).isEqualTo("인사교 소개");
        assertThat(jdbc.queryForObject("SELECT revision FROM page_publications WHERE page_id=65",Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_pages",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log WHERE action='페이지 임시저장'",Integer.class)).isEqualTo(2);
    }

    @Test void unsavedPreviewUsesSafeRichRenderingAndDoesNotWrite()throws Exception {
        var admin=login("ADMIN");String csrf=token(admin);String before=admin.get(API+"/pages/65").body();
        var input=edit("아직 저장하지 않음",4);
        ((ObjectNode)input.path("sections").get(0)).put("bodyDoc","{\"ops\":[{\"insert\":\"<script>alert(1)</script> 굵은 내용\",\"attributes\":{\"bold\":true}},{\"insert\":\"\\n\"}]}");
        var preview=ok(admin.json("POST",API+"/pages/65/preview",input.toString(),csrf));
        assertThat(preview.path("title").asText()).isEqualTo("아직 저장하지 않음");
        assertThat(preview.path("sections").get(0).path("bodyHtml").asText()).contains("<strong>","&lt;script&gt;","굵은 내용").doesNotContain("<script>");
        assertThat(admin.get(API+"/pages/65").body()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log",Integer.class)).isZero();
        assertThat(ok(admin.get(API+"/pages/65/preview")).path("sections").get(0).path("bodyHtml").asText()).contains("원본 본문");
    }

    @Test void savedRichDocumentAndSavedPreviewAgree()throws Exception {
        var root=login("SUPER_ADMIN");String csrf=token(root);var input=edit("리치 본문 검증",4);
        String delta="{\"ops\":[{\"insert\":\"서식 본문\",\"attributes\":{\"bold\":true,\"color\":\"blue\",\"size\":\"large\",\"font\":\"serif\"}},{\"insert\":\"\\n\",\"attributes\":{\"align\":\"center\"}}]}";
        ((ObjectNode)input.path("sections").get(0)).put("bodyDoc",delta);
        var saved=ok(root.json("PUT",API+"/pages/65",input.toString(),csrf));
        var reread=ok(root.get(API+"/pages/65"));assertThat(reread).isEqualTo(saved);
        assertThat(json.readTree(reread.path("sections").get(0).path("bodyDoc").asText())).isEqualTo(json.readTree(delta));
        String html=ok(root.get(API+"/pages/65/preview")).path("sections").get(0).path("bodyHtml").asText();
        assertThat(html).contains("서식 본문","<strong>","rt-color-blue","rt-size-large","rt-font-serif","rt-align-center");
    }

    @Test void versionCsrfAndValidationFailuresNeverOverwriteThePage()throws Exception {
        var admin=login("ADMIN");String csrf=token(admin);var input=edit("첫 수정",4);
        error(admin.json("PUT",API+"/pages/65",input.toString(),null),403);
        ok(admin.json("PUT",API+"/pages/65",input.toString(),csrf));
        error(admin.json("PUT",API+"/pages/65",edit("덮어쓰기",4).toString(),csrf),409);
        var missing=edit("버전 없음",5);missing.remove("revision");error(admin.json("PUT",API+"/pages/65",missing.toString(),csrf),400);
        var bad=edit("잘못된 블록",5);((ObjectNode)bad.path("sections").get(0)).put("type","EXECUTE_CODE");error(admin.json("PUT",API+"/pages/65",bad.toString(),csrf),400);
        error(admin.json("PUT",API+"/pages/65","{",csrf),400);
        assertThat(ok(admin.get(API+"/pages/65")).path("title").asText()).isEqualTo("첫 수정");
        assertThat(jdbc.queryForObject("SELECT revision FROM site_pages WHERE id=65",Long.class)).isEqualTo(5);
    }

    @Test void unchangedRoleAndSessionRestrictionsApplyToBothModesAndAllApis()throws Exception {
        var anonymous=new HttpBrowser(port);error(anonymous.get(API+"/bootstrap"),401);
        var supporter=login("SUPPORTER");String csrf=extract(supporter.get("/login").body(),"name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
        for(String path:List.of("/admin-next/pages/65/edit?view=manage","/admin-next/pages/65/edit?view=structure","/admin/pages/65/edit"))assertThat(supporter.get(path).statusCode()).isEqualTo(403);
        assertThat(boot(supporter).path("permissions").path("site").asBoolean()).isFalse();
        for(String path:List.of("/pages/65","/pages/65/preview"))error(supporter.get(API+path),403);
        error(supporter.json("PUT",API+"/pages/65",edit("침범",4).toString(),csrf),403);
        error(supporter.json("POST",API+"/pages/65/preview",edit("침범",4).toString(),csrf),403);
        var admin=login("ADMIN");String token=token(admin);
        jdbc.update("UPDATE users SET auth_version=auth_version+1 WHERE role='ADMIN'");
        error(admin.json("PUT",API+"/pages/65",edit("만료된 세션",4).toString(),token),401);
        var fresh=login("ADMIN");jdbc.update("UPDATE users SET password_change_required=TRUE WHERE role='ADMIN'");
        error(fresh.get(API+"/bootstrap"),403);
        assertThat(jdbc.queryForObject("SELECT revision FROM site_pages WHERE id=65",Long.class)).isEqualTo(4);
    }

    @Test void navigationDoesNotRegisterIaApplyV4OrCreateMissingTargets()throws Exception {
        var admin=login("ADMIN");String csrf=token(admin);
        for(String target:List.of("1","999")) {
            error(admin.get(API+"/pages/"+target),404);
            error(admin.json("PUT",API+"/pages/"+target,edit("다른 대상",4).toString(),csrf),404);
            error(admin.json("POST",API+"/pages/"+target+"/preview",edit("다른 대상",4).toString(),csrf),404);
        }
        assertThat(jdbc.queryForList("SELECT \"version\" FROM \"flyway_schema_history\" WHERE \"success\"=TRUE AND \"version\" IS NOT NULL",String.class)).containsExactly("1","2","3","4","5","6","7","8","9","10","11","12");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_pages",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_menus",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories",Integer.class)).isZero();
    }

    @Test void structureUsesExistingMenuRelationshipIncludingVisibilityAndMissingLinks()throws Exception {
        var admin=login("ADMIN");
        jdbc.update("UPDATE site_menus SET visible=FALSE");
        assertThat(boot(admin).path("menus").get(0).path("visible").asBoolean()).isFalse();
        jdbc.update("UPDATE site_pages SET title='변경된 페이지 이름' WHERE id=65");
        assertThat(boot(admin).path("menus").get(0).path("label").asText()).isEqualTo("변경된 페이지 이름");
        jdbc.update("DELETE FROM site_menus");
        var data=boot(admin);
        assertThat(data.path("menus").size()).isZero();
        assertThat(data.path("pages").get(0).path("id").asLong()).isEqualTo(65);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_menus",Integer.class)).isZero();
    }
}
