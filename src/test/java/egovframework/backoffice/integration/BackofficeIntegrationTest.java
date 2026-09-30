package egovframework.backoffice.integration;

import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.post.*;
import egovframework.backoffice.mvp.security.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static egovframework.backoffice.integration.HttpBrowser.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BackofficeIntegrationTest {
    // Test fixtures only; not deployed/bootstrap credentials.
    private static final String ROOT = "root@example.test";
    private static final String INITIAL = "Test-Initial-Root42";
    private static final String PASSWORD = "Test-Changed-Root42";
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountService accounts;
    @Autowired AccountMapper accountMapper;
    @Autowired PostService posts;
    @Autowired PasswordEncoder encoder;

    @BeforeEach void reset() {
        for (String table : List.of("post_publication_media","post_media","post_publications","page_media","page_publications","site_pages","posts","media","users","site_menus","categories","site_links","activity_log")) jdbc.update("DELETE FROM " + table);
        jdbc.update("UPDATE site_settings SET setting_value='' WHERE setting_key IN ('logoId','homePageId','contactEmail')");
        jdbc.update("UPDATE site_settings SET setting_value='인공지능 사관학교' WHERE setting_key='siteName'");
        jdbc.update("UPDATE site_settings SET setting_value='12' WHERE setting_key IN ('radius','postsPerPage')");
        accounts.bootstrap(ROOT, "최상위 테스트", INITIAL);
    }

    private HttpBrowser root() throws Exception {
        var root = new HttpBrowser(port);
        root.login(ROOT, INITIAL, "/account/password");
        root.password(INITIAL, PASSWORD);
        root.login(ROOT, PASSWORD, "/admin");
        return root;
    }
    private AccountPrincipal principal(String email) { return new AccountPrincipal(accountMapper.findByEmail(email)); }
    private HttpBrowser member(HttpBrowser root, String email, Role role) throws Exception {
        redirect(root.post("/admin/accounts", Map.of("email", email, "displayName", email, "role", role.name())),
                "/admin/legacy/accounts/issued");
        String temporary = root.issued();
        redirect(root.get("/admin/legacy/accounts/issued"), "/admin/legacy/accounts");
        var browser = new HttpBrowser(port);
        browser.login(email, temporary, "/account/password");
        redirect(browser.get("/admin/legacy/posts"), "/account/password");
        browser.password(temporary, PASSWORD);
        browser.login(email, PASSWORD, "/admin");
        return browser;
    }
    private long createPost(HttpBrowser browser, String title) throws Exception {
        var result = browser.post("/admin/posts", Map.of("title", title, "content", "테스트 본문", "authorId", "999999"));
        assertThat(result.statusCode()).isEqualTo(302);
        return Long.parseLong(location(result).substring(location(result).lastIndexOf('/') + 1));
    }

    @Test void completeFlowConnectsLoginAccountsRbacPostsPasswordAndLogout() throws Exception {
        var anonymous = new HttpBrowser(port);
        redirect(anonymous.get("/admin/legacy/posts"), "/login");
        anonymous.login(ROOT, "wrong-password", "/login?error");
        var root = root();
        var admin = member(root, "admin@example.test", Role.ADMIN);
        var supporter = member(root, "support@example.test", Role.SUPPORTER);
        assertThat(root.get("/admin/legacy/accounts").body()).contains("admin@example.test", "support@example.test");
        for (var denied : List.of(admin, supporter)) {
            assertThat(denied.get("/admin/legacy/accounts").statusCode()).isEqualTo(403);
            assertThat(denied.post("/admin/accounts", Map.of("email", "forged@example.test",
                    "displayName", "위조", "role", "ADMIN")).statusCode()).isEqualTo(403);
        }
        long adminPost = createPost(admin, "관리자 게시물");
        long supportPost = createPost(supporter, "서포터 게시물");
        assertThat(jdbc.queryForObject("SELECT author_id FROM posts WHERE id = ?", Long.class, supportPost))
                .isEqualTo(accountMapper.findByEmail("support@example.test").id());
        assertThat(admin.get("/admin/legacy/posts").body()).contains("관리자 게시물", "서포터 게시물");
        assertThat(supporter.get("/admin/legacy/posts").body()).contains("서포터 게시물").doesNotContain("관리자 게시물");
        assertThat(supporter.get("/admin/legacy/posts/" + adminPost).statusCode()).isEqualTo(403);
        assertThat(supporter.get("/admin/legacy/posts/" + adminPost + "/edit").statusCode()).isEqualTo(403);
        assertThat(supporter.post("/admin/posts/" + adminPost + "/edit",
                Map.of("title", "침범", "content", "침범")).statusCode()).isEqualTo(403);
        assertThat(supporter.post("/admin/posts/" + adminPost + "/delete", Map.of()).statusCode()).isEqualTo(403);
        redirect(supporter.post("/admin/posts/" + supportPost + "/edit",
                Map.of("revision","0","title", "본인 수정", "content", "수정 본문")), "/admin/legacy/posts/" + supportPost);
        // ADMIN's access to another author's post is the current proposal.
        redirect(admin.post("/admin/posts/" + supportPost + "/edit",
                Map.of("revision","1","title", "관리자 전체 수정", "content", "관리자가 수정")), "/admin/legacy/posts/" + supportPost);
        assertThat(supporter.get("/admin/legacy/posts/" + supportPost).body()).contains("관리자 전체 수정");
        assertThat(supporter.post("/admin/posts/" + supportPost + "/delete", Map.of()).statusCode()).isEqualTo(403);
        redirect(root.post("/admin/posts/" + supportPost + "/delete", Map.of("revision","2","confirmed","true")), "/admin/legacy/posts");
        assertThat(admin.get("/admin/legacy/posts/" + supportPost).statusCode()).isEqualTo(404);
        redirect(admin.post("/admin/posts/" + adminPost + "/edit",
                Map.of("revision","0","title", "관리자 수정", "content", "수정")), "/admin/legacy/posts/" + adminPost);
        assertThat(admin.post("/admin/posts/" + adminPost + "/delete", Map.of()).statusCode()).isEqualTo(403);
        redirect(root.post("/admin/posts/" + adminPost + "/delete", Map.of("revision","1","confirmed","true")), "/admin/legacy/posts");
        long extra = createPost(supporter, "전체 삭제 검증");
        assertThat(admin.post("/admin/posts/" + extra + "/delete", Map.of()).statusCode()).isEqualTo(403);
        redirect(root.post("/admin/posts/" + extra + "/delete", Map.of("revision","0","confirmed","true")), "/admin/legacy/posts");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE deleted_at IS NOT NULL", Integer.class)).isEqualTo(3);
        String next = "Test-Next-Password42";
        admin.password(PASSWORD, next);
        redirect(admin.get("/admin/legacy/posts"), "/login");
        admin.login("admin@example.test", PASSWORD, "/login?error");
        admin.login("admin@example.test", next, "/admin");
        redirect(admin.post("/logout", Map.of()), "/login?logout");
        redirect(admin.get("/admin/legacy/posts"), "/login");
        var stored = accountMapper.findByEmail("admin@example.test");
        assertThat(stored.passwordHash()).startsWith("$2");
        assertThat(encoder.matches(next, stored.passwordHash())).isTrue();
    }

    @Test void creationAndTemporaryPasswordCannotBypassRestrictions() throws Exception {
        var root = root();
        assertThat(root.get("/admin/legacy/accounts/new").body()).contains("value=\"ADMIN\"", "value=\"SUPPORTER\"")
                .doesNotContain("value=\"SUPER_ADMIN\"");
        assertThat(root.post("/admin/accounts", Map.of("email", "illegal@example.test",
                "displayName", "불가", "role", "SUPER_ADMIN")).statusCode()).isEqualTo(400);
        assertThat(accountMapper.findByEmail("illegal@example.test")).isNull();
        root.post("/admin/accounts", Map.of("email", "temporary@example.test", "displayName", "임시", "role", "ADMIN"));
        String temporary = root.issued();
        assertThat(accountMapper.findByEmail("temporary@example.test").passwordHash()).isNotEqualTo(temporary);
        var member = new HttpBrowser(port);
        member.login("temporary@example.test", temporary, "/account/password");
        assertThat(member.post("/admin/posts", Map.of("title", "변경 전", "content", "불가")).statusCode()).isEqualTo(403);
        assertThat(member.post("/account/password", Map.of("currentPassword", "wrong", "newPassword", PASSWORD,
                "confirmPassword", PASSWORD)).statusCode()).isEqualTo(400);
        assertThat(member.post("/account/password", Map.of("currentPassword", temporary, "newPassword", "한".repeat(30),
                "confirmPassword", "한".repeat(30))).statusCode()).isEqualTo(400);
        assertThat(root.post("/admin/accounts", Map.of("email", "TEMPORARY@example.test",
                "displayName", "중복", "role", "SUPPORTER")).statusCode()).isEqualTo(400);
        accounts.bootstrap("another@example.test", "중복 초기화", "Test-Other-Password42");
        assertThat(accountMapper.count()).isEqualTo(2);
    }

    @Test void accountChangesInvalidateExistingSessionsAndUseSeparatePromotion() throws Exception {
        var root = root();
        var member = member(root, "change@example.test", Role.ADMIN);
        long id = accountMapper.findByEmail("change@example.test").id();
        assertThat(root.get("/admin/legacy/accounts/" + id + "/role").body()).contains("SUPER_ADMIN");
        redirect(root.post("/admin/accounts/" + id + "/role", Map.of("role", "SUPPORTER")), "/admin/legacy/accounts");
        redirect(member.get("/admin/legacy/posts"), "/login?expired");
        member.login("change@example.test", PASSWORD, "/admin");
        assertThat(member.get("/admin/legacy/posts").body()).contains("본인이 작성한");
        redirect(root.post("/admin/accounts/" + id + "/role", Map.of("role", "SUPER_ADMIN")), "/admin/legacy/accounts");
        redirect(member.get("/admin/legacy/accounts"), "/login?expired");
        member.login("change@example.test", PASSWORD, "/admin");
        assertThat(member.get("/admin/legacy/accounts").statusCode()).isEqualTo(200);
        assertThat(member.post("/admin/accounts/" + id + "/role", Map.of("role", "ADMIN")).statusCode()).isEqualTo(400);
        assertThat(member.post("/admin/accounts/" + id + "/deactivate", Map.of()).statusCode()).isEqualTo(400);
        redirect(root.post("/admin/accounts/" + id + "/reset-password", Map.of()), "/admin/legacy/accounts/issued");
        String reset = root.issued();
        redirect(member.get("/admin/legacy/posts"), "/login?expired");
        member.login("change@example.test", PASSWORD, "/login?error");
        member.login("change@example.test", reset, "/account/password");
        member.password(reset, "Test-Reset-Password42");
        member.login("change@example.test", "Test-Reset-Password42", "/admin");
        redirect(root.post("/admin/accounts/" + id + "/deactivate", Map.of()), "/admin/legacy/accounts");
        redirect(member.get("/admin/legacy/posts"), "/login?expired");
        member.login("change@example.test", "Test-Reset-Password42", "/login?error");
    }

    @Test void csrfEscapingValidationAndDeletedRowsAreProtected() throws Exception {
        var root = root();
        assertThat(root.rawPost("/admin/posts", Map.of("title", "CSRF", "content", "blocked")).statusCode()).isEqualTo(403);
        assertThat(root.rawPost("/logout", Map.of()).statusCode()).isEqualTo(403);
        assertThat(root.get("/admin/legacy/posts").statusCode()).isEqualTo(200);
        assertThat(root.post("/admin/posts", Map.of("title", "x".repeat(201), "content", "body")).statusCode()).isEqualTo(400);
        assertThat(root.get("/admin/legacy/posts?page=-1").statusCode()).isEqualTo(400);
        var created = root.post("/admin/posts", Map.of("title", "<script>title</script>", "content", "<script>alert(1)</script>"));
        // Saving returns to the legacy screen; the form actions keep their original /admin/posts paths.
        String path = java.net.URI.create(location(created)).getRawPath();
        String action = path.replaceFirst("^/admin/legacy/", "/admin/");
        assertThat(path).startsWith("/admin/legacy/posts/");
        var html = root.get(path);
        assertThat(html.statusCode()).isEqualTo(200);
        assertThat(html.body()).contains("&lt;script&gt;").doesNotContain("<script>");
        assertThat(root.get(action + "/delete").statusCode()).isEqualTo(405);
        assertThat(root.get(path).statusCode()).isEqualTo(200);
        redirect(root.post(action + "/delete", Map.of("revision","0","confirmed","true")), "/admin/legacy/posts");
        assertThat(root.get(path).statusCode()).isEqualTo(404);
        assertThat(root.post(action + "/edit", Map.of("title", "복구 시도", "content", "불가")).statusCode()).isEqualTo(404);
        assertThat(root.get("/__stage0/status").statusCode()).isEqualTo(403);
    }

    @Test void supporterPaginationAndServiceAuthorizationUseTheSameScope() throws Exception {
        var root = root();
        var supporter = member(root, "pages@example.test", Role.SUPPORTER);
        var supportPrincipal = principal("pages@example.test");
        for (int i = 0; i < 12; i++) posts.create(supportPrincipal, "own-" + i, "본인 글");
        for (int i = 0; i < 3; i++) posts.create(principal(ROOT), "other-" + i, "다른 글");
        var page0 = posts.list(supportPrincipal, 0);
        var page1 = posts.list(supportPrincipal, 1);
        assertThat(page0.total()).isEqualTo(12);
        assertThat(page0.items()).hasSize(10);
        assertThat(page1.items()).hasSize(2);
        assertThat(supporter.get("/admin/legacy/posts?page=1").body()).contains("총 12건").doesNotContain("other-");
        assertThatThrownBy(() -> accounts.list(supportPrincipal))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> accounts.create(supportPrincipal, "escape@example.test", "불가", Role.ADMIN))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(accountMapper.findByEmail("escape@example.test")).isNull();
    }

    @Test void concurrentSuperAdminDemotionsCannotRemoveTheLastActiveSuper() throws Exception {
        root();
        var first = principal(ROOT);
        var issued = accounts.create(first, "second@example.test", "두 번째 관리자", Role.ADMIN);
        accounts.changeOwnPassword(principal(issued.email()), issued.temporaryPassword(), PASSWORD, PASSWORD);
        accounts.changeRole(first, issued.accountId(), Role.SUPER_ADMIN);
        var second = principal(issued.email());
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var tasks = List.of(
                pool.submit(() -> attemptDemotion(start, first, second.getId())),
                pool.submit(() -> attemptDemotion(start, second, first.getId())));
            start.countDown();
            int successes = 0;
            for (var task : tasks) if (task.get(15, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
            assertThat(accountMapper.activeSuperCount()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void dashboardSearchAndSampleStatisticsRespectAccessScope() throws Exception {
        var root = root();
        var supporter = member(root, "design-support@example.test", Role.SUPPORTER);
        var supportPrincipal = principal("design-support@example.test");
        posts.create(supportPrincipal, "own-100%_literal", "본문 검색 대상");
        posts.create(principal(ROOT), "hidden-admin-title", "다른 작성자");
        long deleted = posts.create(supportPrincipal, "deleted-title", "삭제 대상");
        posts.delete(principal(ROOT),deleted,posts.get(principal(ROOT),deleted).revision());
        var overview = posts.dashboard(supportPrincipal);
        assertThat(overview.total()).isEqualTo(1);
        assertThat(overview.weekCount()).isEqualTo(1);
        assertThat(overview.recentPosts()).hasSize(1);
        var dashboard = supporter.get("/admin/legacy");
        assertThat(dashboard.statusCode()).isEqualTo(200);
        assertThat(dashboard.body()).contains("own-100%_literal").doesNotContain("hidden-admin-title", "deleted-title", "오늘 방문자");
        var rootDashboard = root.get("/admin/legacy");
        assertThat(rootDashboard.statusCode()).isEqualTo(200);
        assertThat(rootDashboard.body()).contains("방문 통계", "예시 데이터", "방문자 추이", "hidden-admin-title")
                .doesNotContain("/admin/analytics");
        assertThat(root.get("/admin/analytics").statusCode()).isEqualTo(403);
        assertThat(supporter.get("/admin/analytics").statusCode()).isEqualTo(403);
        assertThat(supporter.get("/admin/legacy/roles").statusCode()).isEqualTo(403);
        assertThat(root.get("/admin/legacy/roles").body()).contains("역할 / 권한 관리", "최상위 관리자");
        assertThat(posts.list(supportPrincipal, 0, "%_").total()).isEqualTo(1);
        assertThat(posts.list(supportPrincipal, 0, "hidden-admin-title").total()).isZero();
        assertThat(posts.list(supportPrincipal, 0, "본문 검색").total()).isEqualTo(1);
        var search = supporter.get("/admin/legacy/posts?q=100%25_");
        assertThat(search.statusCode()).isEqualTo(200);
        assertThat(search.body()).contains("총 1건", "own-100%_literal").doesNotContain("hidden-admin-title");
        assertThat(root.get("/admin/legacy/posts?q=" + "x".repeat(101)).statusCode()).isEqualTo(400);
        assertThat(new HttpBrowser(port).get("/js/app.js").statusCode()).isEqualTo(200);
        redirect(new HttpBrowser(port).get("/admin/legacy"), "/login");
    }

    private boolean attemptDemotion(CountDownLatch start, AccountPrincipal actor, long target) throws InterruptedException {
        start.await();
        try { accounts.changeRole(actor, target, Role.ADMIN); return true; }
        catch (org.springframework.security.access.AccessDeniedException | egovframework.backoffice.mvp.common.BusinessException expected) { return false; }
    }

    @Autowired egovframework.backoffice.mvp.cms.MediaService media;
    @Autowired egovframework.backoffice.mvp.cms.PageService pages;
    @Autowired egovframework.backoffice.mvp.cms.SiteService site;
    @Autowired egovframework.backoffice.mvp.cms.CmsStore cms;

    private long image(AccountPrincipal actor, String filename) throws Exception {
        var image = new java.awt.image.BufferedImage(3, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", bytes);
        return media.upload(actor, new org.springframework.mock.web.MockMultipartFile("file", filename, "image/png", bytes.toByteArray()), "테스트 대체 텍스트");
    }
    @Test void publicationKeepsDraftPrivateAndSnapshotStableUntilRepublished() throws Exception {
        var browser=root();var actor=principal(ROOT);var anonymous=new HttpBrowser(port);
        long imageId=image(actor,"첫 이미지.png");
        long id=posts.save(actor,null,null,"첫 발행 제목","공개 본문 <script>alert(1)</script>",null,List.of(imageId),"save");
        assertThat(browser.get("/admin/legacy/posts/"+id+"/publication").statusCode()).isEqualTo(404);
        assertThat(anonymous.get("/admin/media/"+imageId+"/file").statusCode()).isEqualTo(302);
        assertThat(browser.post("/admin/posts/preview",Map.of("id",String.valueOf(id),"title","미리보기 제목","content","저장 안 한 내용","mediaIds",String.valueOf(imageId))).body()).contains("저장 안 한 내용","백오피스 내부 확인");
        assertThat(posts.get(actor,id).title()).isEqualTo("첫 발행 제목");
        posts.save(actor,id,0L,"첫 발행 제목","공개 본문 <script>alert(1)</script>",null,List.of(imageId),"publish");
        var view=browser.get("/admin/legacy/posts/"+id+"/publication");
        assertThat(view.statusCode()).isEqualTo(200);
        assertThat(view.body()).contains("첫 발행 제목","&lt;script&gt;").doesNotContain("<script>alert");
        long secondImage=image(actor,"새 초안 전용.png");
        posts.save(actor,id,1L,"아직 미발행 제목","수정 초안",null,List.of(secondImage),"save");
        assertThat(browser.get("/admin/legacy/posts/"+id+"/publication").body()).contains("첫 발행 제목").doesNotContain("아직 미발행 제목");
        assertThat(posts.pending(actor,id)).isTrue();
        assertThatThrownBy(()->posts.save(actor,id,1L,"오래된 수정","충돌",null,List.of(),"publish")).isInstanceOf(egovframework.backoffice.mvp.common.BusinessException.class);
        posts.save(actor,id,2L,"새 발행 제목","변경된 공개 본문",null,List.of(secondImage),"publish");
        assertThat(browser.get("/admin/legacy/posts/"+id+"/publication").body()).contains("새 발행 제목").doesNotContain("첫 발행 제목");
        posts.unpublish(actor,id,3L);
        assertThat(browser.get("/admin/legacy/posts/"+id+"/publication").statusCode()).isEqualTo(404);
        for(String path:List.of("/site","/site/posts/"+id,"/site/media/"+imageId))assertThat(anonymous.get(path).statusCode()).isNotEqualTo(200);
        assertThat(site.activities(actor,0,"").stream().map(egovframework.backoffice.mvp.cms.CmsModels.Activity::action)).contains("콘텐츠 발행","콘텐츠 비공개");
    }
    @Test void pagesMenusAndSettingsPersistWithoutServingAPublicWebsite() throws Exception {
        var browser=root();var actor=principal(ROOT);
        site.category(actor,null,"공지");long categoryId=site.categories().get(0).id();
        String sections="[{\"type\":\"TEXT\",\"heading\":\"사관학교 소개\",\"body\":\"첫 발행 소개\",\"visible\":true}]";
        long page=pages.save(actor,null,null,"소개","about",sections,"save");
        sections=pages.get(actor,page).sectionsJson();
        site.menu(actor,null,"사관학교 소개","PAGE",page,"",true);
        assertThat(browser.get("/admin/legacy/pages/"+page+"/preview").body()).contains("첫 발행 소개","홈페이지는 아직 연결되지");
        pages.save(actor,page,0L,"소개","about",sections,"publish");
        site.saveSettings(actor,"basic",Map.of("siteName","테스트 사관학교","description","소개문","contactEmail","contact@example.test","homePageId",String.valueOf(page)));
        site.saveSettings(actor,"style",Map.of("primaryColor","#123456","headerColor","#102030","radius","20"));
        site.link(actor,null,"교육 안내","https://example.test/education");
        assertThat(site.settings()).containsEntry("primaryColor","#123456").containsEntry("siteName","테스트 사관학교");
        String draft=sections.replace("첫 발행 소개","미발행 소개");
        pages.save(actor,page,1L,"소개 수정","about-new",draft,"save");
        var snapshot=cms.<egovframework.backoffice.mvp.cms.CmsModels.PublishedPage>one("publicPageById",page);
        assertThat(snapshot.sectionsJson()).contains("첫 발행 소개").doesNotContain("미발행 소개");
        assertThat(snapshot.slug()).isEqualTo("about");
        assertThat(site.menus(actor).get(0).targetId()).isEqualTo(page);
        assertThatThrownBy(()->pages.delete(actor,page,2L)).isInstanceOf(egovframework.backoffice.mvp.common.BusinessException.class);
        pages.save(actor,page,2L,"소개 수정","about-new",draft,"publish");
        assertThat(cms.<egovframework.backoffice.mvp.cms.CmsModels.PublishedPage>one("publicPageById",page).sectionsJson()).contains("미발행 소개");
        pages.unpublish(actor,page,3L);
        assertThat(cms.<Object>one("publicPageById",page)).isNull();
        assertThat(new HttpBrowser(port).get("/site/pages/about-new").statusCode()).isNotEqualTo(200);
    }
    @Test void newAdminScreensRenderAndSupporterCannotChangeSharedSite() throws Exception {
        var root=root();var actor=principal(ROOT);
        for(String path:List.of("/admin/legacy/pages","/admin/legacy/pages/new","/admin/legacy/media","/admin/legacy/categories","/admin/legacy/menus","/admin/legacy/design/style","/admin/legacy/design/components","/admin/legacy/settings/basic","/admin/legacy/settings/links","/admin/legacy/settings/system","/admin/legacy/activity","/admin/legacy/posts/new")) {
            var response=root.get(path);
            assertThat(response.statusCode()).as(path).isEqualTo(200);
            assertThat(response.body()).doesNotContain("nav-pending");
        }
        var supporter=member(root,"cms-supporter@example.test",Role.SUPPORTER);
        var supporterActor=principal("cms-supporter@example.test");
        long adminImage=image(actor,"관리자 이미지.png");
        assertThat(supporter.get("/admin/media/"+adminImage+"/file").statusCode()).isEqualTo(403);
        assertThatThrownBy(()->posts.save(supporterActor,null,null,"침범","본문",null,List.of(adminImage),"publish")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        for(String path:List.of("/admin/legacy/pages","/admin/legacy/categories","/admin/legacy/menus","/admin/legacy/design/style","/admin/legacy/settings/basic","/admin/legacy/activity"))
            assertThat(supporter.get(path).statusCode()).as(path).isEqualTo(403);
        assertThat(supporter.post("/admin/settings/save/basic",Map.of("siteName","위조")).statusCode()).isEqualTo(403);
        assertThat(root.rawPost("/admin/categories",Map.of("name","CSRF 없음")).statusCode()).isEqualTo(403);
        assertThat(new HttpBrowser(port).get("/site").statusCode()).isNotEqualTo(200);
    }
    @Test void mediaAndLinksValidateInputAndOrderingRejectsStaleLists() throws Exception {
        root();var actor=principal(ROOT);
        var fake=new org.springframework.mock.web.MockMultipartFile("file","fake.png","image/png","<script>evil</script>".getBytes());
        assertThatThrownBy(()->media.upload(actor,fake,"")).isInstanceOf(egovframework.backoffice.mvp.common.BusinessException.class);
        long imageId=image(actor,"실제 이미지.png");
        long id=posts.save(actor,null,null,"첨부 글","본문",null,List.of(imageId),"save");
        assertThatThrownBy(()->media.delete(actor,imageId)).isInstanceOf(egovframework.backoffice.mvp.common.BusinessException.class);
        posts.delete(actor,id,posts.get(actor,id).revision());
        assertThatThrownBy(()->media.delete(actor,imageId)).isInstanceOf(egovframework.backoffice.mvp.common.BusinessException.class);
        posts.purgeTrash(actor,id,posts.trashed(actor,id).revision());media.delete(actor,imageId);
        assertThatThrownBy(()->media.required(imageId)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->site.link(actor,null,"위험 링크","javascript:alert(1)")).isInstanceOf(egovframework.backoffice.mvp.common.BusinessException.class);
        site.category(actor,null,"첫 항목");site.category(actor,null,"둘째 항목");
        var ids=site.categories().stream().map(egovframework.backoffice.mvp.cms.CmsModels.Category::id).toList();
        site.reorder(actor,"categories",List.of(ids.get(1),ids.get(0)));
        assertThat(site.categories().get(0).name()).isEqualTo("둘째 항목");
        assertThatThrownBy(()->site.reorder(actor,"categories",List.of(ids.get(0)))).isInstanceOf(egovframework.backoffice.mvp.common.BusinessException.class);
        assertThatThrownBy(()->site.saveSettings(actor,"style",Map.of("primaryColor","red;}body{","headerColor","#ffffff","radius","12"))).isInstanceOf(egovframework.backoffice.mvp.common.BusinessException.class);
    }

    @Test void multipartUploadCanBeAttachedAndPublishedThroughHttpForms() throws Exception {
        var browser=root();var actor=principal(ROOT);
        var bytes=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4,3,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        var upload=browser.upload("/admin/media/upload",bytes.toByteArray());
        assertThat(upload.statusCode()).isEqualTo(200);
        var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(upload.body());
        long imageId=json.get("id").asLong();
        assertThat(json.get("width").asInt()).isEqualTo(4);
        assertThat(browser.upload("/admin/media/upload","invalid image".getBytes()).statusCode()).isEqualTo(400);
        var created=browser.post("/admin/posts",Map.of("title","HTTP 첨부 발행","content","본문","categoryId","","mediaIds",String.valueOf(imageId),"action","publish"));
        assertThat(created.statusCode()).isEqualTo(302);
        long postId=Long.parseLong(location(created).substring(location(created).lastIndexOf('/')+1));
        assertThat(browser.get("/admin/legacy/posts/"+postId+"/publication").body()).contains("HTTP 첨부 발행","/admin/media/"+imageId+"/file");
        assertThat(browser.get("/admin/legacy/media").body()).contains("upload.png");
        redirect(browser.post("/admin/media/"+imageId+"/edit",Map.of("name","수정한 이미지.png","alt","실제 설명")),"/admin/legacy/media");
        assertThat(browser.get("/admin/legacy/posts/"+postId+"/publication").body()).contains("실제 설명");
    }
    @Autowired egovframework.backoffice.mvp.cms.RichTextService rich;
    @Test void richEditorAutosaveRetainsInlineOrderFormattingAndIndependentPublication() throws Exception {
        var browser=root();var actor=principal(ROOT);var json=new com.fasterxml.jackson.databind.ObjectMapper();
        long img=image(actor,"본문 사진.png");
        long pdf=media.upload(actor,new org.springframework.mock.web.MockMultipartFile("file","수업 자료.pdf","application/pdf","%PDF-1.4\n자료".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"");
        String document=json.writeValueAsString(Map.of("ops",List.of(
            Map.of("insert","앞 문단 <script>\n","attributes",Map.of("bold",true,"color","blue")),
            Map.of("insert",Map.of("aicaImage",Map.of("id",img,"width","50","align","center","alt","사진 대체 문구","caption","사진 설명"))),
            Map.of("insert","중간 문단\n","attributes",Map.of("font","serif","size","large")),
            Map.of("insert",Map.of("aicaFile",Map.of("id",pdf,"label","수업 자료 다운로드"))),
            Map.of("insert",Map.of("aicaTable",Map.of("rows",List.of(List.of("과정","일시"),List.of("AI <img onerror=x>","내일"))))),
            Map.of("insert","마지막 문단\n"))));
        var created=browser.post("/admin/posts/save-json",Map.of("title","서식 있는 글","richContent",document,"action","publish"));
        assertThat(created.statusCode()).isEqualTo(200);long id=json.readTree(created.body()).get("id").asLong();
        assertThat(posts.get(actor,id).richContent()).isEqualTo(document);
        var view=browser.get("/admin/legacy/posts/"+id+"/publication").body();
        assertThat(view).contains("rt-color-blue","<strong>앞 문단 &lt;script&gt;</strong>","rt-width-50","rt-font-serif","rt-size-large","수업 자료 다운로드","<table>","AI &lt;img onerror=x&gt;");
        assertThat(view.indexOf("앞 문단")).isLessThan(view.indexOf("rt-width-50"));
        assertThat(view.indexOf("rt-width-50")).isLessThan(view.indexOf("중간 문단"));
        assertThat(view).doesNotContain("<img onerror=x>","/site/");
        assertThat(posts.attachments(actor,id)).hasSize(2);
        assertThatThrownBy(()->media.delete(actor,pdf)).isInstanceOf(egovframework.backoffice.mvp.common.BusinessException.class);
        var downloaded=browser.get("/admin/media/"+pdf+"/file");
        assertThat(downloaded.headers().firstValue("Content-Disposition").orElse("")).contains("attachment");
        assertThat(downloaded.headers().firstValue("X-Content-Type-Options").orElse("")).isEqualTo("nosniff");
        var changed=browser.post("/admin/posts/save-json",Map.of("id",String.valueOf(id),"revision","0","title","자동 저장된 수정","richContent",document.replace("마지막 문단","수정 문단")));
        assertThat(changed.statusCode()).isEqualTo(200);assertThat(json.readTree(changed.body()).get("revision").asLong()).isEqualTo(1);
        assertThat(browser.get("/admin/legacy/posts/"+id+"/publication").body()).contains("마지막 문단").doesNotContain("수정 문단");
        var stale=browser.post("/admin/posts/save-json",Map.of("id",String.valueOf(id),"revision","0","title","오래된 창","richContent",document));
        assertThat(stale.statusCode()).isEqualTo(400);assertThat(stale.body()).contains("다른 작업");
        assertThat(posts.get(actor,id).title()).isEqualTo("자동 저장된 수정");
        assertThat(browser.rawPost("/admin/posts/save-json",Map.of("title","CSRF 생략","richContent",document)).statusCode()).isEqualTo(403);
        assertThat(browser.get("/admin/legacy/posts/"+id+"/edit").body()).contains("실시간 미리보기","글자 크기","명조","aicaImage","수정 문단");
    }
    @Test void richDocumentsRejectUnsafeAttributesAndForeignMediaAndWorkInPageSections() throws Exception {
        var browser=root();var actor=principal(ROOT);var json=new com.fasterxml.jackson.databind.ObjectMapper();
        long img=image(actor,"관리자 사진.png");
        var supporter=member(browser,"rich-supporter@example.test",Role.SUPPORTER);
        var supporterActor=principal("rich-supporter@example.test");
        String document=json.writeValueAsString(Map.of("ops",List.of(Map.of("insert","서식 본문\n","attributes",Map.of("bold",true)),Map.of("insert",Map.of("aicaImage",Map.of("id",img))))));
        assertThatThrownBy(()->rich.validate(supporterActor,document)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(supporter.post("/admin/posts/save-json",Map.of("title","권한 없는 이미지","richContent",document)).statusCode()).isEqualTo(403);
        for(var attrs:List.of(Map.of("link","javascript:alert(1)"),Map.of("color","red;position:fixed"),Map.of("onclick","alert(1)"))) {
            String bad=json.writeValueAsString(Map.of("ops",List.of(Map.of("insert","위험","attributes",attrs))));
            assertThat(browser.post("/admin/posts/save-json",Map.of("title","잘못된 서식","richContent",bad)).statusCode()).isEqualTo(400);
        }
        String sections=json.writeValueAsString(List.of(Map.of("type","TEXT","heading","페이지 본문","bodyDoc",document,"visible",true)));
        var created=browser.post("/admin/pages/save-json",Map.of("title","서식 페이지","slug","rich-page","sectionsJson",sections,"action","publish"));
        assertThat(created.statusCode()).isEqualTo(200);long page=json.readTree(created.body()).get("id").asLong();
        assertThat(pages.get(actor,page).sectionsJson()).contains("bodyDoc","aicaImage");
        assertThat(browser.get("/admin/legacy/pages/"+page+"/preview").body()).contains("<strong>서식 본문</strong>","/admin/media/"+img+"/file");
        assertThatThrownBy(()->media.delete(actor,img)).isInstanceOf(egovframework.backoffice.mvp.common.BusinessException.class);
        assertThat(browser.post("/admin/pages/save-json",Map.of("id",String.valueOf(page),"revision","99","title","충돌","slug","rich-page","sectionsJson",sections)).statusCode()).isEqualTo(400);
        var invalidPdf=new org.springframework.mock.web.MockMultipartFile("file","fake.pdf","application/pdf","<script>bad</script>".getBytes());
        assertThatThrownBy(()->media.upload(actor,invalidPdf,"")).isInstanceOf(egovframework.backoffice.mvp.common.BusinessException.class);
        assertThat(new HttpBrowser(port).get("/admin/legacy/pages/"+page+"/preview").statusCode()).isEqualTo(302);
    }

    @Test void linkedMenuNamesFollowRenamesWithoutChangingDestinations() throws Exception {
        var browser=root();var actor=principal(ROOT);
        site.category(actor,null,"공지사항");long category=site.categories().get(0).id();
        // New category menus are no longer created; an existing legacy row is kept as-is until migration.
        browser.post("/admin/menus",Map.of("destination","CATEGORY:"+category,"visible","true"));
        assertThat(site.menus(actor)).isEmpty();
        jdbc.update("INSERT INTO site_menus(label,kind,target_id,url,visible,sort_order) VALUES('공지사항','CATEGORY',?,'',TRUE,1)",category);
        long menu=site.menus(actor).get(0).id();
        jdbc.update("UPDATE site_menus SET label='legacy alias' WHERE id=?",menu);
        site.category(actor,category,"학교 소식");
        assertThat(site.menus(actor).get(0).label()).isEqualTo("학교 소식");
        assertThat(site.menus(actor).get(0).targetId()).isEqualTo(category);
        String sections="[{\"type\":\"TEXT\",\"body\":\"소개\",\"visible\":true}]";
        long page=pages.save(actor,null,null,"소개",null,sections,"save");
        site.menu(actor,null,"ignored","PAGE",page,"",true);
        String title="긴 페이지 이름".repeat(20);
        pages.save(actor,page,0L,title,"",pages.get(actor,page).sectionsJson(),"save");
        assertThat(site.menus(actor).get(1).label()).isEqualTo(title);
        site.menu(actor,null,"교육 신청","LINK",null,"https://example.test/apply",true);
        assertThat(site.menus(actor).get(2).label()).isEqualTo("교육 신청");
        assertThat(browser.get("/admin/legacy/menus?edit="+menu).body()).contains("학교 소식","CATEGORY:"+category);
    }
    @Test void structureErrorsKeepInputsAndShowActualReferences() throws Exception {
        var browser=root();var actor=principal(ROOT);
        site.category(actor,null,"이미 있는 분류");long category=site.categories().get(0).id();
        var duplicate=browser.post("/admin/categories",Map.of("name","이미 있는 분류"));
        assertThat(duplicate.statusCode()).isEqualTo(400);
        assertThat(duplicate.body()).contains("이미 사용 중인 이름입니다.","value=\"이미 있는 분류\"");
        assertThat(site.categories()).hasSize(1);
        var invalid=browser.post("/admin/menus",Map.of("destination","LINK","label","입력 유지","url","javascript:alert(1)","visible","true"));
        assertThat(invalid.statusCode()).isEqualTo(400);assertThat(invalid.body()).contains("value=\"입력 유지\"");assertThat(site.menus(actor)).isEmpty();
        long post=posts.save(actor,null,null,"연결된 글","본문",category,List.of(),"publish");
        redirect(browser.post("/admin/categories/"+category+"/delete",Map.of()),"/admin/legacy/categories");
        assertThat(browser.get("/admin/legacy/categories").body()).contains("사용 중인 곳","연결된 글","/admin/posts/"+post+"/edit");
        assertThat(site.categories()).hasSize(1);
        long imageId=image(actor,"사용 중 이미지.png");
        posts.save(actor,post,0L,"연결된 글","본문",category,List.of(imageId),"save");
        redirect(browser.post("/admin/media/"+imageId+"/delete",Map.of("confirmed","true")),"/admin/legacy/media");
        assertThat(browser.get("/admin/legacy/media").body()).contains("사용 중인 곳","연결된 글");
    }
    @Test void pageAddressesAreGeneratedOnceAndSavingReturnsPublicationState() throws Exception {
        var browser=root();var actor=principal(ROOT);var json=new com.fasterxml.jackson.databind.ObjectMapper();
        String sections="[{\"type\":\"TEXT\",\"body\":\"작성 내용\",\"visible\":true}]";
        var created=browser.post("/admin/pages/save-json",Map.of("title","처음 작성","sectionsJson",sections,"action","publish"));
        assertThat(created.statusCode()).isEqualTo(200);var data=json.readTree(created.body());long id=data.get("id").asLong();String slug=data.get("slug").asText();
        assertThat(slug).startsWith("page-");assertThat(data.get("pending").asBoolean()).isFalse();
        var saved=browser.post("/admin/pages/save-json",Map.of("id",String.valueOf(id),"revision","0","title","수정 제목","sectionsJson",pages.get(actor,id).sectionsJson()));
        assertThat(saved.statusCode()).isEqualTo(200);data=json.readTree(saved.body());assertThat(data.get("pending").asBoolean()).isTrue();assertThat(data.get("slug").asText()).isEqualTo(slug);
        assertThat(pages.get(actor,id).slug()).isEqualTo(slug);
        var post=browser.post("/admin/posts/save-json",Map.of("title","발행 글","content","본문","action","publish"));
        long postId=json.readTree(post.body()).get("id").asLong();
        var changed=browser.post("/admin/posts/save-json",Map.of("id",String.valueOf(postId),"revision","0","title","수정 글","content","본문"));
        assertThat(json.readTree(changed.body()).get("pending").asBoolean()).isTrue();
        assertThat(posts.get(actor,postId).pending()).isTrue();
    }
    @Test void listContextAndSelectedCategorySurviveEditingWithoutExternalRedirects() throws Exception {
        var browser=root();var actor=principal(ROOT);site.category(actor,null,"선택 분류");long category=site.categories().get(0).id();
        String from="/admin/legacy/posts?categoryId="+category+"&status=DRAFT&q=keyword&page=2";
        String encoded=java.net.URLEncoder.encode(from,java.nio.charset.StandardCharsets.UTF_8);
        var page=browser.get("/admin/legacy/posts/new?categoryId="+category+"&from="+encoded);
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("value=\""+category+"\" selected=\"selected\"", "name=\"from\"", "keyword");
        var empty=browser.get("/admin/legacy/posts?categoryId="+category);
        assertThat(empty.body()).contains("조건에 맞는 글이 없습니다","필터 해제");
        long id=posts.save(actor,null,null,"keyword","본문",category,List.of(),"save");
        assertThat(browser.get("/admin/legacy/posts?q=keyword").body()).contains("/admin/legacy/posts/"+id+"/edit?from=");
        redirect(browser.post("/admin/posts/"+id+"/delete",Map.of("from","https://example.test/evil","revision",""+posts.get(actor,id).revision(),"confirmed","true")),"/admin/legacy/posts");
        assertThat(egovframework.backoffice.mvp.common.ListLocation.posts(from)).isEqualTo(from);
        assertThat(egovframework.backoffice.mvp.common.ListLocation.posts("//example.test")).isEqualTo("/admin/legacy/posts");
    }
    @Test void combinedSettingsValidateBeforeWritingAndActivityFiltersOnlyItsView() throws Exception {
        var browser=root();var actor=principal(ROOT);long logo=image(actor,"로고.png");
        var design=new HashMap<>(Map.of("primaryColor","#123abc","headerColor","#102030","radius","8","logoId",String.valueOf(logo),"headerNote","상단","footerText","하단"));
        redirect(browser.post("/admin/settings/save/design",design),"/admin/legacy/design/style");
        assertThat(site.settings()).containsEntry("logoId",String.valueOf(logo)).containsEntry("primaryColor","#123abc");
        design.put("primaryColor","#abcdef");design.put("logoId","invalid");
        var rejected=browser.post("/admin/settings/save/design",design);
        assertThat(rejected.statusCode()).isEqualTo(400);assertThat(rejected.body()).contains("#abcdef","상단");
        assertThat(site.settings()).containsEntry("primaryColor","#123abc");
        posts.save(actor,null,null,"기록 필터 확인","본문",null,List.of(),"save");
        assertThat(site.activities(actor,0,"기록 필터 확인",true)).hasSize(1);
        assertThat(site.activities(actor,0,"기록 필터 확인",false)).isEmpty();
        assertThat(site.activityCount(actor,"기록 필터 확인",true)).isEqualTo(1);
        assertThat(browser.get("/admin/legacy/activity").body()).contains("강조색","상단 문구").doesNotContain("primaryColor, headerColor");
    }

    @Test void incompletePageSectionsRemainSaveableWhilePublicationChecksRequirements() throws Exception {
        var browser=root();var json=new com.fasterxml.jackson.databind.ObjectMapper();
        String sections="[{\"type\":\"IMAGE\",\"visible\":true},{\"type\":\"CTA\",\"label\":\"지원 안내\",\"visible\":true}]";
        var saved=browser.post("/admin/pages/save-json",Map.of("title","작성 중인 페이지","sectionsJson",sections));
        assertThat(saved.statusCode()).isEqualTo(200);long id=json.readTree(saved.body()).get("id").asLong();
        var rejected=browser.post("/admin/pages/save-json",Map.of("id",String.valueOf(id),"revision","0","title","작성 중인 페이지","sectionsJson",pages.get(principal(ROOT),id).sectionsJson(),"action","publish"));
        assertThat(rejected.statusCode()).isEqualTo(400);assertThat(rejected.body()).contains("이미지");
        assertThat(pages.get(principal(ROOT),id).revision()).isZero();
        assertThat(pages.get(principal(ROOT),id).sectionsJson()).contains("지원 안내");
    }
}
