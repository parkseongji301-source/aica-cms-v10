package egovframework.backoffice.integration;

import com.fasterxml.jackson.databind.*;
import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.security.*;
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

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:next-workspace-integration;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class NextWorkspaceIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountMapper accountMapper;
    @Autowired SiteService site;
    @Autowired PageService pages;
    private static final String API="/api/admin/next";
    private static final String PASSWORD="Test-Workspace-Password42";
    private static final String SECTIONS="[{\"type\":\"TEXT\",\"heading\":\"소개\",\"body\":\"기존 본문\",\"visible\":true}]";

    @BeforeEach void fixture() {
        for(String table:List.of("post_publication_media","post_media","post_publications","page_media","page_publications","site_pages","posts","media","site_menus","categories","site_links","activity_log","users"))jdbc.update("DELETE FROM "+table);
        jdbc.update("UPDATE site_settings SET setting_value='' WHERE setting_key IN ('logoId','homePageId')");
        String hash=encoder.encode(PASSWORD);
        for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,?,FALSE)",email(role),role,hash,role);
        long root=id("SUPER_ADMIN");
        for(long page:List.of(1L,65L)) {
            jdbc.update("INSERT INTO site_pages(id,title,slug,sections_json,status,revision,author_id) VALUES(?,?,?,?, 'PUBLISHED',4,?)",page,page==1?"홈":"인사교 소개",page==1?"home":"about",SECTIONS,root);
            jdbc.update("INSERT INTO page_publications(page_id,title,slug,sections_json,revision) SELECT id,title,slug,sections_json,revision FROM site_pages WHERE id=?",page);
        }
        jdbc.update("INSERT INTO categories(id,name,sort_order) VALUES(11,'공지사항',0),(12,'교육 소식',1)");
        jdbc.update("INSERT INTO site_menus(label,kind,target_id,sort_order) VALUES('홈','PAGE',1,0),('소개','PAGE',65,1),('공지','CATEGORY',11,2)");
        jdbc.update("INSERT INTO posts(id,title,content,author_id,status,category_id) VALUES(101,'공지 원본','본문',?,'PUBLISHED',11),(102,'본인 초안','내 본문',?,'DRAFT',12)",root,id("SUPPORTER"));
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) c->{try{db.migration.h2.V8__page_block_identity.upgrade(c);}catch(Exception e){throw new IllegalStateException(e);}return null;});
    }
    private String email(String role){return role.toLowerCase(Locale.ROOT)+"@workspace.test";}
    private long id(String role){return accountMapper.findByEmail(email(role)).id();}
    private AccountPrincipal principal(String role){return new AccountPrincipal(accountMapper.findByEmail(email(role)));}
    private HttpBrowser login(String role)throws Exception {var b=new HttpBrowser(port);b.login(email(role),PASSWORD,"/admin");return b;}
    private JsonNode ok(HttpResponse<String> r)throws Exception {assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
    private void denied(HttpResponse<String> r,int status)throws Exception {assertThat(r.statusCode()).as(r.body()).isEqualTo(status);assertThat(r.headers().firstValue("content-type").orElse("")).contains("application/json");assertThat(json.readTree(r.body()).has("code")).isTrue();}
    private String token(HttpBrowser b)throws Exception{return ok(b.get(API+"/bootstrap")).path("csrf").path("token").asText();}
    private JsonNode write(HttpBrowser b,String method,String path,Object body,String csrf)throws Exception{return ok(b.json(method,API+path,json.writeValueAsString(body),csrf));}

    @Test void existingCategoriesStayReadableButAreNoLongerManaged()throws Exception {
        var admin=login("ADMIN");String adminCsrf=token(admin);
        denied(admin.get(API+"/categories"),403);
        denied(admin.json("POST",API+"/categories",json.writeValueAsString(Map.of("name","관리자 시도")),adminCsrf),403);
        var supporter=login("SUPPORTER");denied(supporter.json("POST",API+"/categories",json.writeValueAsString(Map.of("name","지원자 시도")),token(supporter)),403);
        var root=login("SUPER_ADMIN");String csrf=token(root);
        denied(root.json("POST",API+"/categories",json.writeValueAsString(Map.of("name","CSRF 없음")),null),403);
        denied(root.json("POST",API+"/categories",json.writeValueAsString(Map.of("name"," ")),csrf),400);
        assertThat(site.categories()).hasSize(2);
        // Legacy categories are retired: existing rows and their usage stay readable, but nothing is added, renamed, reordered or deleted.
        for(var attempt:List.of(root.json("POST",API+"/categories",json.writeValueAsString(Map.of("name","행사 안내")),csrf),
                root.json("PUT",API+"/categories/12",json.writeValueAsString(Map.of("name","행사 소식")),csrf),
                root.json("PUT",API+"/categories/order",json.writeValueAsString(Map.of("ids",List.of(12L,11L))),csrf),
                root.json("DELETE",API+"/categories/12","{}",csrf)))
            assertThat(json.readTree(attempt.body()).path("message").asText()).as(attempt.body()).contains("레거시 카테고리");
        assertThat(ok(root.get(API+"/categories")).size()).isEqualTo(2);
        assertThat(ok(root.get(API+"/categories/11/usage")).size()).isPositive();
        assertThat(site.categories()).extracting(c->c.name()).containsExactly("공지사항","교육 소식");
        assertThat(jdbc.queryForObject("SELECT category_id FROM posts WHERE id=101",Long.class)).isEqualTo(11L);
    }
    @Test void accountsAreIssuedChangedAndResetThroughAccountServiceWithOneTimePasswords()throws Exception {
        var admin=login("ADMIN");String adminCsrf=token(admin);
        denied(admin.json("POST",API+"/accounts",json.writeValueAsString(Map.of("email","x@workspace.test","displayName","x","role","SUPPORTER")),adminCsrf),403);
        denied(admin.json("POST",API+"/accounts/"+id("SUPPORTER")+"/reset-password","{}",adminCsrf),403);
        var supporter=login("SUPPORTER");
        var root=login("SUPER_ADMIN");String csrf=token(root);
        assertThat(ok(root.get(API+"/accounts/creatable-roles")).findValuesAsText("code")).containsExactly("ADMIN","SUPPORTER");
        denied(root.json("POST",API+"/accounts",json.writeValueAsString(Map.of("email","root2@workspace.test","displayName","두번째","role","SUPER_ADMIN")),csrf),400);
        denied(root.json("POST",API+"/accounts",json.writeValueAsString(Map.of("email","not-an-email","displayName","잘못","role","ADMIN")),csrf),400);
        denied(root.json("POST",API+"/accounts",json.writeValueAsString(Map.of("email","new@workspace.test","displayName","신규","role","ADMIN")),null),403);
        var created=root.json("POST",API+"/accounts",json.writeValueAsString(Map.of("email","new@workspace.test","displayName","신규","role","ADMIN")),csrf);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        assertThat(created.headers().firstValue("cache-control").orElse("")).contains("no-store");
        var issued=json.readTree(created.body());String temporary=issued.path("temporaryPassword").asText();
        assertThat(temporary).isNotBlank();assertThat(issued.path("email").asText()).isEqualTo("new@workspace.test");
        denied(root.json("POST",API+"/accounts",json.writeValueAsString(Map.of("email","new@workspace.test","displayName","중복","role","ADMIN")),csrf),400);
        var list=root.get(API+"/accounts");assertThat(list.body()).doesNotContain(temporary).doesNotContain("password_hash").doesNotContain("passwordHash");
        var fresh=new HttpBrowser(port);fresh.login("new@workspace.test",temporary,"/account/password");
        denied(fresh.get(API+"/bootstrap"),403);
        long supporterId=id("SUPPORTER");
        denied(root.json("PUT",API+"/accounts/"+id("SUPER_ADMIN")+"/role",json.writeValueAsString(Map.of("role","ADMIN")),csrf),400);
        denied(root.json("PUT",API+"/accounts/"+supporterId+"/role",json.writeValueAsString(Map.of("role","OWNER")),csrf),400);
        var changed=write(root,"PUT","/accounts/"+supporterId+"/role",Map.of("role","ADMIN"),csrf);
        assertThat(changed.findValuesAsText("role")).contains("ADMIN");
        denied(supporter.get(API+"/bootstrap"),401);
        var reset=json.readTree(root.json("POST",API+"/accounts/"+id("ADMIN")+"/reset-password","{}",csrf).body());
        assertThat(reset.path("temporaryPassword").asText()).isNotBlank().isNotEqualTo(temporary);
        denied(admin.get(API+"/bootstrap"),401);
        new HttpBrowser(port).login(email("ADMIN"),reset.path("temporaryPassword").asText(),"/account/password");
        var deactivated=write(root,"POST","/accounts/"+supporterId+"/deactivate",Map.of(),csrf);
        assertThat(jdbc.queryForObject("SELECT active FROM users WHERE id=?",Boolean.class,supporterId)).isFalse();
        assertThat(deactivated).isNotEmpty();
        denied(root.json("POST",API+"/accounts/"+supporterId+"/reset-password","{}",csrf),400);
        denied(root.json("POST",API+"/accounts/"+id("SUPER_ADMIN")+"/deactivate","{}",csrf),400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log WHERE action IN ('계정 발급','계정 역할 변경','비밀번호 초기화','계정 비활성화')",Integer.class)).isEqualTo(4);
    }
    @Test void everyImplementedMenuLoadsAndBootstrapReflectsExistingRowsWithoutWrites()throws Exception {
        var root=login("SUPER_ADMIN");var boot=ok(root.get(API+"/bootstrap"));
        assertThat(boot.path("pages").size()).isEqualTo(2);assertThat(boot.path("menus").size()).isEqualTo(3);
        for(String path:List.of("dashboard","posts","media","pages","menus","design/style","design/components","accounts","roles","activity","settings/basic","settings/links","settings/system","pages/1/edit","pages/65/edit")) {
            var response=root.get("/admin/"+path);assertThat(response.statusCode()).as(path).isEqualTo(200);assertThat(response.body()).contains("id=\"root\"","/next-app/assets/");
        }
        for(String path:List.of("dashboard","posts","media","pages","menus","links","accounts","roles","activity","settings/basic","settings/style","settings/components","settings/system"))ok(root.get(API+"/"+path));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_pages",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log",Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT \"version\" FROM \"flyway_schema_history\" WHERE \"success\"=TRUE AND \"version\" IS NOT NULL",String.class)).containsExactly("1","2","3","4","5","6","7","8","9","10","11","12","13","14","15");
        jdbc.update("DELETE FROM site_menus");jdbc.update("DELETE FROM page_publications");jdbc.update("DELETE FROM site_pages");
        assertThat(ok(root.get(API+"/bootstrap")).path("pages").size()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_pages",Integer.class)).isZero();
    }

    @Test void allExistingPagesUseTheSameEditorServiceAndNeverAlterPublishedCopies()throws Exception {
        var admin=login("ADMIN");String csrf=token(admin);
        for(long id:List.of(1L,65L)) {
            var draft=ok(admin.get(API+"/pages/"+id));
            var saved=write(admin,"PUT","/pages/"+id,Map.of("revision",draft.path("revision").asLong(),"title","공통 편집 "+id,"sections",draft.path("sections")),csrf);
            assertThat(saved.path("id").asLong()).isEqualTo(id);
            assertThat(saved.path("revision").asLong()).isEqualTo(5);
            assertThat(pages.get(principal("ADMIN"),id).title()).isEqualTo("공통 편집 "+id);
            assertThat(admin.get("/admin/legacy/pages/"+id+"/edit").body()).contains("공통 편집 "+id);
            assertThat(ok(admin.get(API+"/pages/"+id+"/preview")).path("title").asText()).isEqualTo("공통 편집 "+id);
            assertThat(jdbc.queryForObject("SELECT revision FROM page_publications WHERE page_id=?",Long.class,id)).isEqualTo(4);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_pages",Integer.class)).isEqualTo(2);
        assertThat(ok(admin.get(API+"/bootstrap")).path("menus").toString()).contains("공통 편집 1","공통 편집 65");
    }

    @Test void contentCategoryStatusSearchAndOwnershipUseTheExistingPostSource()throws Exception {
        var admin=login("ADMIN");var supporter=login("SUPPORTER");
        var all=ok(admin.get(API+"/posts"));assertThat(all.path("total").asLong()).isEqualTo(2);
        var category=ok(admin.get(API+"/posts?categoryId=11&status=PUBLISHED"));assertThat(category.path("total").asLong()).isEqualTo(1);
        assertThat(category.path("items").get(0).path("id").asLong()).isEqualTo(101);
        var own=ok(supporter.get(API+"/posts"));assertThat(own.path("total").asLong()).isEqualTo(1);assertThat(own.path("items").get(0).path("id").asLong()).isEqualTo(102);
        assertThat(ok(supporter.get(API+"/posts?categoryId=11")).path("total").asLong()).isZero();
        assertThat(ok(admin.get(API+"/posts?q=missing")).path("total").asLong()).isZero();
        assertThat(admin.get("/admin/legacy/posts/101/edit").body()).contains("공지 원본");
        var dash=ok(supporter.get(API+"/dashboard"));assertThat(dash.path("total").asLong()).isEqualTo(1);assertThat(dash.path("trafficStatus").asText()).isEqualTo("NOT_CONNECTED");
    }

    @Test void menusAndStructureShareConnectionsVisibilityOrderAndLabels()throws Exception {
        var admin=login("SUPER_ADMIN");String csrf=token(admin);var before=ok(admin.get(API+"/menus"));
        long menuId=before.get(0).path("id").asLong();
        long categoryMenu=jdbc.queryForObject("SELECT id FROM site_menus WHERE kind='CATEGORY'",Long.class);
        // New category links are rejected; the existing category menu keeps its target and can still be edited or moved to a page.
        denied(admin.json("POST",API+"/menus",json.writeValueAsString(Map.of("kind","CATEGORY","targetId",12,"visible",true)),csrf),400);
        denied(admin.json("PUT",API+"/menus/"+menuId,json.writeValueAsString(Map.of("kind","CATEGORY","targetId",12,"visible",false)),csrf),400);
        denied(admin.json("PUT",API+"/menus/"+categoryMenu,json.writeValueAsString(Map.of("kind","CATEGORY","targetId",12,"visible",true)),csrf),400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_menus WHERE kind='CATEGORY'",Integer.class)).isEqualTo(1);
        write(admin,"PUT","/menus/"+categoryMenu,Map.of("kind","CATEGORY","targetId",11,"visible",false),csrf);
        assertThat(jdbc.queryForObject("SELECT target_id FROM site_menus WHERE id=?",Long.class,categoryMenu)).isEqualTo(11L);
        write(admin,"PUT","/menus/"+menuId,Map.of("kind","PAGE","targetId",65,"visible",false),csrf);
        var boot=ok(admin.get(API+"/bootstrap"));assertThat(boot.path("menus").get(0).path("label").asText()).isEqualTo("인사교 소개");assertThat(boot.path("menus").get(0).path("visible").asBoolean()).isFalse();
        List<Long> ids=new ArrayList<>();for(var menu:boot.path("menus"))ids.add(menu.path("id").asLong());Collections.reverse(ids);
        var sorted=write(admin,"PUT","/menus/order",Map.of("ids",ids),csrf);assertThat(sorted.get(0).path("id").asLong()).isEqualTo(ids.get(0));
        assertThat(ok(admin.get(API+"/bootstrap")).path("menus")).isEqualTo(sorted);
        denied(admin.json("PUT",API+"/menus/order","{\"ids\":[999]}",csrf),400);
        int original=sorted.size();var created=write(admin,"POST","/menus",Map.of("kind","PAGE","targetId",65,"visible",true),csrf);assertThat(created.size()).isEqualTo(original+1);
        long added=created.get(created.size()-1).path("id").asLong();write(admin,"DELETE","/menus/"+added,Map.of(),csrf);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_pages",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts",Integer.class)).isEqualTo(2);
    }

    @Test void settingsAndLinksUseExistingStoreWithGroupBoundariesAndValidation()throws Exception {
        var admin=login("SUPER_ADMIN");String csrf=token(admin);var before=site.settings();
        write(admin,"PUT","/settings/style",Map.of("primaryColor","#123456","headerColor","#234567","radius","8"),csrf);
        assertThat(site.settings().get("primaryColor")).isEqualTo("#123456");assertThat(site.settings().get("headerNote")).isEqualTo(before.get("headerNote"));
        write(admin,"PUT","/settings/components",Map.of("logoId","","headerNote","공통 상단","footerText","공통 하단"),csrf);
        assertThat(site.settings().get("primaryColor")).isEqualTo("#123456");
        write(admin,"PUT","/settings/system",Map.of("postsPerPage","24"),csrf);
        write(admin,"PUT","/settings/basic",Map.of("siteName","관리 검증","description","소개","contactEmail","","homePageId","1"),csrf);
        assertThat(site.settings().get("postsPerPage")).isEqualTo("24");
        assertThat(admin.get("/admin/legacy/settings/basic").body()).contains("관리 검증");
        denied(admin.json("PUT",API+"/settings/system","{\"postsPerPage\":\"999\"}",csrf),400);
        assertThat(site.settings().get("postsPerPage")).isEqualTo("24");
        var links=write(admin,"POST","/links",Map.of("label","AICA","url","https://example.com/aica"),csrf);long link=links.get(0).path("id").asLong();
        write(admin,"PUT","/links/"+link,Map.of("label","SNS","url","https://example.com/social"),csrf);
        assertThat(site.links().get(0).label()).isEqualTo("SNS");
        denied(admin.json("POST",API+"/links","{\"label\":\"bad\",\"url\":\"javascript:alert(1)\"}",csrf),400);
        write(admin,"DELETE","/links/"+link,Map.of(),csrf);assertThat(site.links()).isEmpty();
        assertThat(site.activities(principal("SUPER_ADMIN"),0,"",true)).isNotEmpty();
    }

    @Test void mediaUploadMetadataUsageProtectionAndOwnershipReuseExistingService()throws Exception {
        var admin=login("SUPER_ADMIN");var supporter=login("SUPPORTER");String csrf=token(admin),supportToken=token(supporter);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        long media=ok(admin.upload(API+"/media",bytes.toByteArray())).path("id").asLong();
        write(admin,"PUT","/media/"+media,Map.of("name","공통 이미지.png","alt","설명"),csrf);
        assertThat(admin.get("/admin/legacy/media").body()).contains("공통 이미지.png");
        assertThat(ok(supporter.get(API+"/media")).size()).isZero();
        denied(supporter.json("PUT",API+"/media/"+media,"{\"name\":\"침범\",\"alt\":\"\"}",supportToken),403);
        denied(supporter.json("DELETE",API+"/media/"+media,"{}",supportToken),403);
        jdbc.update("INSERT INTO page_media(page_id,media_id,published) VALUES(65,?,FALSE)",media);
        denied(admin.json("DELETE",API+"/media/"+media,"{}",csrf),400);
        assertThat(ok(admin.get(API+"/media/"+media+"/usage")).size()).isPositive();
        jdbc.update("DELETE FROM page_media WHERE media_id=?",media);
        write(admin,"DELETE","/media/"+media,Map.of(),csrf);assertThat(ok(admin.get(API+"/media")).size()).isZero();
    }

    @Test void permissionsAndCsrfApplyToEveryNewAreaAndAccountResponsesExcludeSecrets()throws Exception {
        var anonymous=new HttpBrowser(port);denied(anonymous.get(API+"/dashboard"),401);
        var root=login("SUPER_ADMIN");var admin=login("ADMIN");var supporter=login("SUPPORTER");String csrf=token(supporter);
        assertThat(ok(supporter.get(API+"/bootstrap")).path("pages").size()).isZero();
        assertThat(supporter.get("/admin/posts").statusCode()).isEqualTo(200);
        for(String path:List.of("pages","menus","links","settings/basic","settings/style","settings/components","settings/system","accounts","roles","activity"))denied(supporter.get(API+"/"+path),403);
        for(String path:List.of("menus","links","settings/basic","settings/style","settings/components","settings/system"))denied(supporter.json("PUT",API+"/"+path,"{}",csrf),403);
        for(String path:List.of("accounts","roles","activity")){denied(admin.get(API+"/"+path),403);assertThat(admin.get("/admin/"+path).statusCode()).isEqualTo(200);}
        denied(admin.json("PUT",API+"/settings/system","{\"postsPerPage\":\"6\"}",null),403);
        var response=root.get(API+"/accounts");assertThat(response.body()).doesNotContain("passwordHash","authVersion","$2a$","$2b$");assertThat(ok(response).size()).isEqualTo(3);
        assertThat(ok(root.get(API+"/roles")).size()).isEqualTo(3);
        jdbc.update("UPDATE users SET auth_version=auth_version+1 WHERE role='ADMIN'");denied(admin.get(API+"/menus"),401);
    }
}
