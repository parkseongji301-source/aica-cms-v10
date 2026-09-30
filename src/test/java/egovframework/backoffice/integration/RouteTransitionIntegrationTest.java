package egovframework.backoffice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpResponse;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static egovframework.backoffice.integration.HttpBrowser.location;
import static egovframework.backoffice.integration.HttpBrowser.redirect;
import static org.assertj.core.api.Assertions.*;

/**
 * Step 5 of the React transition: React owns /admin, legacy Thymeleaf screens live under /admin/legacy,
 * and old addresses redirect. The data APIs keep exactly the authorization they had before the move.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:route-transition-integration;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class RouteTransitionIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    private static final String PASSWORD="Test-Route-Password42";
    private static final List<String> ROLES=List.of("SUPER_ADMIN","ADMIN","SUPPORTER");
    private static final List<String> REACT_ROUTES=List.of("/admin","/admin/","/admin/dashboard","/admin/trash","/admin/posts","/admin/posts/101/edit",
        "/admin/media","/admin/pages","/admin/pages/65/edit","/admin/menus","/admin/design/style","/admin/design/components","/admin/design/templates",
        "/admin/design/writing-templates","/admin/accounts","/admin/roles","/admin/activity","/admin/settings/basic","/admin/settings/links","/admin/settings/system");

    @BeforeEach void fixture() {
        for(String table:List.of("post_publication_media","post_media","post_publications","page_media","page_publications","site_pages","posts","media","site_menus","categories","site_links","activity_log","users"))jdbc.update("DELETE FROM "+table);
        String hash=encoder.encode(PASSWORD);
        for(String role:ROLES)jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,?,FALSE)",email(role),"표시 "+role,hash,role);
        jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES('change@routes.test','변경 필요',?,'ADMIN',TRUE)",hash);
        long root=jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email("SUPER_ADMIN"));
        jdbc.update("INSERT INTO site_pages(id,title,slug,sections_json,status,revision,author_id) VALUES(65,'인사교 소개','about','[]','DRAFT',0,?)",root);
        jdbc.update("INSERT INTO posts(id,title,content,author_id,status) VALUES(101,'경로 확인 글','본문',?,'DRAFT')",root);
    }
    private static String email(String role){return role.toLowerCase(Locale.ROOT)+"@routes.test";}
    private HttpBrowser login(String role)throws Exception{var b=new HttpBrowser(port);b.login(email(role),PASSWORD,"/admin");return b;}
    private static int status(HttpResponse<String> r){return r.statusCode();}

    @Test void everySignedInRoleReceivesTheSameDataFreeReactShellAtEveryAdminRoute()throws Exception {
        Map<String,String> shells=new LinkedHashMap<>();
        for(String role:ROLES) {
            var b=login(role);
            for(String route:REACT_ROUTES) {
                var r=b.get(route);
                assertThat(status(r)).as(role+" "+route).isEqualTo(200);
                assertThat(r.body()).as(route).contains("id=\"root\"","/next-app/assets/");
                // The shell is static: no account, role, CSRF token or permission-specific content is embedded.
                assertThat(r.body()).doesNotContain(email(role),"표시 "+role,role,"_csrf","csrf-token");
                shells.merge(route,r.body(),(a,c)->{assertThat(c).as("shell differs by role at "+route).isEqualTo(a);return a;});
            }
        }
        var anonymous=new HttpBrowser(port);
        for(String route:REACT_ROUTES)redirect(anonymous.get(route),"/login");
        var change=new HttpBrowser(port);change.login("change@routes.test",PASSWORD,"/account/password");
        redirect(change.get("/admin/posts"),"/account/password");
    }

    @Test void reactPreviewAddressesRedirectTemporarilyToTheSameScreenAndQuery()throws Exception {
        var b=login("ADMIN");
        Map<String,String> cases=new LinkedHashMap<>();
        cases.put("/admin-next","/admin");
        cases.put("/admin-next/","/admin");
        cases.put("/admin-next?view=manage","/admin?view=manage");
        cases.put("/admin-next/posts/101/edit?view=structure&from=%2Fposts","/admin/posts/101/edit?view=structure&from=%2Fposts");
        cases.put("/admin-next/pages/65/edit?view=manage&block=b-1","/admin/pages/65/edit?view=manage&block=b-1");
        cases.put("/admin-next/design/templates?template=3&history=4","/admin/design/templates?template=3&history=4");
        for(var c:cases.entrySet()) {
            var r=b.get(c.getKey());
            assertThat(status(r)).as(c.getKey()).isEqualTo(302);
            assertThat(location(r)).as(c.getKey()).endsWith(c.getValue()).doesNotContain("admin-next");
        }
        redirect(new HttpBrowser(port).get("/admin-next/posts"),"/login");
    }

    @Test void oldLegacyOnlyAddressesOpenTheMatchingReactScreen()throws Exception {
        var b=login("SUPER_ADMIN");
        Map<String,String> cases=new LinkedHashMap<>();
        cases.put("/admin/posts/new","/admin/posts");
        cases.put("/admin/posts/101","/admin/posts/101/edit");
        cases.put("/admin/posts/101/publication","/admin/posts/101/edit");
        cases.put("/admin/pages/new","/admin/pages");
        cases.put("/admin/pages/65/preview","/admin/pages/65/edit");
        cases.put("/admin/accounts/new","/admin/accounts");
        cases.put("/admin/accounts/issued","/admin/accounts");
        cases.put("/admin/accounts/7/role","/admin/accounts");
        cases.put("/admin/categories?edit=3","/admin/legacy/categories?edit=3");
        for(var c:cases.entrySet())redirect(b.get(c.getKey()),c.getValue());
        // A redirect grants nothing: the legacy category screen still requires MANAGE_SITE.
        var admin=login("ADMIN");redirect(admin.get("/admin/categories"),"/admin/legacy/categories");
        assertThat(status(admin.get("/admin/legacy/categories"))).isEqualTo(403);
        for(String path:List.of("/admin/analytics","/admin/legacy/unknown","/admin/posts/101/delete-confirm"))assertThat(status(b.get(path))).as(path).isIn(403,404);
        // Stored HTML embeds /admin/media/{id}/file, so file delivery keeps its address and sign-in rule.
        assertThat(status(b.get("/admin/media/999999/file"))).isEqualTo(404);
        redirect(new HttpBrowser(port).get("/admin/media/999999/file"),"/login");
    }

    @Test void legacyScreensKeepTheirAuthoritiesAndOnlyLinkToLegacyScreens()throws Exception {
        Map<String,List<String>> allowed=new LinkedHashMap<>();
        allowed.put("/admin/legacy",ROLES);
        allowed.put("/admin/legacy/posts",ROLES);
        allowed.put("/admin/legacy/posts/new",ROLES);
        allowed.put("/admin/legacy/posts/101/edit",List.of("SUPER_ADMIN","ADMIN"));
        allowed.put("/admin/legacy/media",ROLES);
        allowed.put("/admin/legacy/pages",List.of("SUPER_ADMIN","ADMIN"));
        allowed.put("/admin/legacy/pages/65/edit",List.of("SUPER_ADMIN","ADMIN"));
        allowed.put("/admin/legacy/pages/new",List.of("SUPER_ADMIN"));
        allowed.put("/admin/legacy/posts/101/delete-confirm",List.of("SUPER_ADMIN"));
        for(String path:List.of("/admin/legacy/categories","/admin/legacy/menus","/admin/legacy/design/style","/admin/legacy/design/components","/admin/legacy/settings/basic","/admin/legacy/settings/links","/admin/legacy/settings/system"))allowed.put(path,List.of("SUPER_ADMIN"));
        for(String path:List.of("/admin/legacy/accounts","/admin/legacy/accounts/new","/admin/legacy/roles","/admin/legacy/activity"))allowed.put(path,List.of("SUPER_ADMIN"));
        for(String role:ROLES) {
            var b=login(role);
            for(var e:allowed.entrySet()) {
                var r=b.get(e.getKey());
                assertThat(status(r)).as(role+" "+e.getKey()).isEqualTo(e.getValue().contains(role)?200:403);
                if(status(r)!=200)continue;
                assertThat(r.body()).as(e.getKey()).contains("기존 화면(비교·복구용)","href=\"/admin/legacy/posts\"").doesNotContain("href=\"/admin/posts\"","admin-next");
            }
        }
        redirect(new HttpBrowser(port).get("/admin/legacy/posts"),"/login");
        // The password page is reached from React, so its header returns to React screens and shows no legacy banner.
        var password=login("SUPER_ADMIN").get("/account/password").body();
        assertThat(password).contains("href=\"/admin/posts\"").doesNotContain("기존 화면(비교·복구용)","/admin/legacy");
    }

    @Test void formPostsKeepTheirPathsCsrfAndAuthorities()throws Exception {
        var supporter=login("SUPPORTER");
        assertThat(status(supporter.post("/admin/settings/save/basic",Map.of("siteName","위조")))).isEqualTo(403);
        assertThat(status(supporter.post("/admin/menus",Map.of("label","위조","kind","PAGE","targetId","65")))).isEqualTo(403);
        assertThat(status(supporter.post("/admin/pages/65/delete",Map.of("confirmed","true")))).isEqualTo(403);
        var root=login("SUPER_ADMIN");
        assertThat(status(root.rawPost("/admin/posts",Map.of("title","CSRF 없음","content","본문")))).isEqualTo(403);
        var saved=root.post("/admin/posts",Map.of("title","기존 폼 저장","content","본문","action","save"));
        assertThat(status(saved)).isEqualTo(302);assertThat(location(saved)).matches(".*/admin/legacy/posts/\\d+$");
    }

    /** Pins the API permission matrix: step 5 moves screens only, so every API answers as before. */
    @Test void dataApisKeepTheirAuthorizationForEveryRole()throws Exception {
        String api="/api/admin/next";
        Map<String,List<String>> allowed=new LinkedHashMap<>();
        for(String path:List.of("/bootstrap","/dashboard","/posts","/media","/classifications","/writing-templates"))allowed.put(path,ROLES);
        for(String path:List.of("/pages","/page-components","/page-structure"))allowed.put(path,List.of("SUPER_ADMIN","ADMIN"));
        for(String path:List.of("/menus","/links","/categories","/settings/basic","/accounts","/roles","/activity","/posts/trash","/page-templates"))allowed.put(path,List.of("SUPER_ADMIN"));
        for(String role:ROLES) {
            var b=login(role);
            for(var e:allowed.entrySet()) {
                var r=b.get(api+e.getKey());
                assertThat(status(r)).as(role+" "+e.getKey()+" "+r.body()).isEqualTo(e.getValue().contains(role)?200:403);
                assertThat(r.headers().firstValue("content-type").orElse("")).as(e.getKey()).contains("application/json");
                if(status(r)==403)assertThat(json.readTree(r.body()).path("code").asText()).isEqualTo("FORBIDDEN_OR_CSRF");
            }
        }
        var anonymous=new HttpBrowser(port);
        for(String path:allowed.keySet()) {
            var r=anonymous.get(api+path);
            assertThat(status(r)).as(path).isEqualTo(401);
            assertThat(json.readTree(r.body()).path("code").asText()).isEqualTo("AUTH_REQUIRED");
        }
    }
}
