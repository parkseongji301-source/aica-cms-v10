package egovframework.backoffice.integration;

import egovframework.backoffice.BackofficeApplication;
import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.post.*;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import static egovframework.backoffice.integration.HttpBrowser.*;
import static org.assertj.core.api.Assertions.assertThat;

class PersistenceRestartTest {
    @TempDir Path directory;
    @Test void fileDatabaseSurvivesCompleteServerShutdownAndRestart() throws Exception {
        String url = "jdbc:h2:file:" + directory.resolve("backoffice").toAbsolutePath().toString().replace('\\', '/')
                + ";DB_CLOSE_ON_EXIT=FALSE;AUTO_COMPACT_FILL_RATE=0";
        // Fixture creation is explicit; the normal file runtime is validate-only.
        ClassificationMigrationTest.flyway(url,null).migrate();
        url += ";IFEXISTS=TRUE";
        long postId;
        long imageId;
        long pageId;
        String hash;
        String email = "restart@example.test";
        String initial = "Test-Restart-Initial42";
        String changed = "Test-Restart-Changed42";
        try (var first = start(url, true, email, initial)) {
            var mapper = first.getBean(AccountMapper.class);
            var service = first.getBean(AccountService.class);
            var original = mapper.findByEmail(email);
            service.changeOwnPassword(new AccountPrincipal(original), initial, changed, changed);
            var actor = new AccountPrincipal(mapper.findByEmail(email));
            var jdbc = first.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
            jdbc.update("INSERT INTO cohorts(id,code,name) VALUES(901,'RESTART_COHORT','검증 기수')");
            jdbc.update("INSERT INTO topics(id,code,name) VALUES(902,'RESTART_TOPIC','검증 주제')");
            jdbc.update("INSERT INTO content_type_topics(type_code,topic_id) VALUES('REVIEW',902)");
            service.create(actor, "persist-admin@example.test", "지속 관리자", Role.ADMIN);
            service.create(actor, "persist-support@example.test", "지속 서포터", Role.SUPPORTER);
            postId = first.getBean(PostService.class).create(actor, "재시작 후 유지", "파일 DB 본문");
            var imageBytes = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB), "png", imageBytes);
            imageId = first.getBean(egovframework.backoffice.mvp.cms.MediaService.class).upload(actor,
                new org.springframework.mock.web.MockMultipartFile("file","영속 이미지.png","image/png",imageBytes.toByteArray()),"재시작 이미지");
            first.getBean(PostService.class).save(actor,postId,0L,"재시작 후 유지","",null,java.util.List.of(),"publish", "{\"ops\":[{\"insert\":\"파일 DB 본문\",\"attributes\":{\"bold\":true}},{\"insert\":\"\\n\"},{\"insert\":{\"aicaImage\":{\"id\":"+imageId+",\"width\":\"50\",\"align\":\"center\",\"caption\":\"재시작 이미지\"}}},{\"insert\":\"\\n\"}]}",
                new egovframework.backoffice.mvp.classification.ClassificationModels.Selection("REVIEW",java.util.List.of(901L),java.util.List.of(902L)));
            pageId = first.getBean(egovframework.backoffice.mvp.cms.PageService.class).save(actor,null,null,"영속 페이지","persistent",
                "[{\"type\":\"TEXT\",\"heading\":\"유지되는 섹션\",\"body\":\"내용\",\"visible\":true}]","publish");
            first.getBean(egovframework.backoffice.mvp.cms.SiteService.class).menu(actor,null,"영속 메뉴","PAGE",pageId,"",true);
            first.getBean(egovframework.backoffice.mvp.cms.SiteService.class).saveSettings(actor,"components",java.util.Map.of("headerNote","유지되는 헤더","footerText","유지되는 푸터","logoId",String.valueOf(imageId)));
            hash = mapper.findByEmail(email).passwordHash();
            var browser = new HttpBrowser(port(first));
            browser.login(email, changed, "/admin");
            assertThat(browser.get("/admin/posts/" + postId).body()).contains("파일 DB 본문");
        }
        // A new application context and a new embedded Tomcat process lifecycle, using the same DB file.
        try (var second = start(url, true, email, initial)) {
            var mapper = second.getBean(AccountMapper.class);
            assertThat(mapper.count()).isEqualTo(3);
            assertThat(mapper.findByEmail(email).passwordHash()).isEqualTo(hash);
            var classifications=second.getBean(egovframework.backoffice.mvp.classification.ClassificationService.class);
            assertThat(classifications.draft(postId).typeCode()).isEqualTo("REVIEW");
            assertThat(classifications.draft(postId).cohortIds()).containsExactly(901L);
            assertThat(classifications.draft(postId).topicIds()).containsExactly(902L);
            assertThat(classifications.published(postId)).isEqualTo(classifications.draft(postId));
            var browser = new HttpBrowser(port(second));
            redirect(browser.get("/admin/posts"), "/login");
            browser.login(email, initial, "/login?error");
            browser.login(email, changed, "/admin");
            assertThat(browser.get("/admin/posts/" + postId).body()).contains("재시작 후 유지", "파일 DB 본문");
            assertThat(browser.get("/admin/accounts").body()).contains("persist-admin@example.test", "persist-support@example.test");
            var anonymous = new HttpBrowser(port(second));
            assertThat(browser.get("/admin/posts/"+postId+"/publication").body()).contains("<strong>파일 DB 본문</strong>","rt-width-50","재시작 이미지");
            assertThat(browser.get("/admin/pages/"+pageId+"/preview").body()).contains("유지되는 섹션");
            assertThat(browser.get("/admin/menus").body()).contains("영속 페이지");
            assertThat(browser.get("/admin/design/components").body()).contains("유지되는 헤더","유지되는 푸터");
            assertThat(browser.get("/admin/media/"+imageId+"/file").statusCode()).isEqualTo(200);
            assertThat(anonymous.get("/site").statusCode()).isNotEqualTo(200);
            assertThat(browser.get("/admin/activity").body()).contains("글 발행","페이지 발행","사이트 설정 변경");
        }
    }
    private ConfigurableApplicationContext start(String url, boolean bootstrap, String email, String password) {
        return new SpringApplicationBuilder(BackofficeApplication.class).profiles("dev").run(
                "--server.port=0", "--spring.datasource.url=" + url,
                "--backoffice.classification-migration.copy-validation=true",
                "--backoffice.bootstrap.enabled=" + bootstrap,
                "--backoffice.bootstrap.email=" + email,
                "--backoffice.bootstrap.password=" + password);
    }
    private int port(ConfigurableApplicationContext context) {
        return ((ServletWebServerApplicationContext) context).getWebServer().getPort();
    }
}
