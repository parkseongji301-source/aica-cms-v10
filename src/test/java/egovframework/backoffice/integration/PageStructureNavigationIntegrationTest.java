package egovframework.backoffice.integration;

import com.fasterxml.jackson.databind.*;
import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.datasource.url=jdbc:h2:mem:block-navigation;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class PageStructureNavigationIntegrationTest {
 @LocalServerPort int port;
 @Autowired JdbcTemplate jdbc;
 @Autowired PageService pages;
 @Autowired AccountMapper accounts;
 @Autowired PasswordEncoder encoder;
 @Autowired ObjectMapper json;
 long page;
 static final String PASSWORD="Navigation-Test-Password42";
 @BeforeEach void fixture() {
  for(String t:List.of("page_media","site_menus","page_publications","page_block_identities","site_pages","activity_log","users"))jdbc.update("DELETE FROM "+t);
  for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,?,FALSE)",role.toLowerCase(Locale.ROOT)+"@navigation.test",role,encoder.encode(PASSWORD),role);
  page=pages.save(root(),null,null,"실제 페이지","navigation-test","[{\"type\":\"HERO\",\"heading\":\"소개\",\"body\":\"원문은 트리로 복사하지 않는다\",\"visible\":true},{\"type\":\"POSTS\",\"heading\":\"소식\",\"visible\":false}]","publish");
 }
 AccountPrincipal root(){return new AccountPrincipal(accounts.findByEmail("super_admin@navigation.test"));}
 AccountPrincipal actor(){return new AccountPrincipal(accounts.findByEmail("admin@navigation.test"));}
 HttpBrowser login(String role)throws Exception{var b=new HttpBrowser(port);b.login(role+"@navigation.test",PASSWORD,"/admin");return b;}
 JsonNode read(HttpBrowser browser,String path)throws Exception{var r=browser.get(path);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
 @Test void structureUsesCurrentPageAndBlockIdsIncludingHiddenBlocksWithoutWritingAnything()throws Exception {
  var b=login("admin");var before=pages.get(actor(),page);var publication=pages.publication(actor(),page);
  var tree=read(b,"/api/admin/next/page-structure").get(0);var editor=read(b,"/api/admin/next/pages/"+page);
  assertThat(tree.path("kind").asText()).isEqualTo("page");assertThat(tree.path("pageId").asLong()).isEqualTo(page);
  assertThat(tree.path("blocks").size()).isEqualTo(2);
  for(int i=0;i<2;i++){var block=tree.path("blocks").get(i);assertThat(block.path("blockId")).isEqualTo(editor.path("sections").get(i).path("id"));assertThat(block.path("pageId").asLong()).isEqualTo(page);assertThat(block.path("kind").asText()).isEqualTo("block");}
  assertThat(tree.path("blocks").get(1).path("visible").asBoolean()).isFalse();assertThat(tree.toString()).doesNotContain("원문은").doesNotContain("categoryId");
  assertThat(pages.get(actor(),page)).isEqualTo(before);assertThat(pages.publication(actor(),page)).isEqualTo(publication);
  for(String view:List.of("manage","structure"))assertThat(b.get("/admin-next/pages/"+page+"/edit?view="+view+"&block="+tree.path("blocks").get(1).path("blockId").asText()).statusCode()).isEqualTo(200);
 }
 @Test void navigationTracksDraftChangesAndNeverSubstitutesRetiredOrDuplicatedIds()throws Exception {
  var b=login("admin");var original=pages.get(actor(),page);var blocks=pages.sections(original.sectionsJson());var first=blocks.get(0);var hidden=blocks.get(1);var copy=PageBlockService.duplicate(first);
  pages.save(actor(),page,original.revision(),original.title(),original.slug(),json.writeValueAsString(List.of(hidden,copy)),"save");
  var tree=read(b,"/api/admin/next/page-structure").get(0).path("blocks");assertThat(tree.get(0).path("blockId").asText()).isEqualTo(hidden.id());assertThat(tree.get(1).path("blockId").asText()).isEqualTo(copy.id());assertThat(tree.toString()).doesNotContain(first.id());
  assertThat(pages.sections(pages.publication(actor(),page).sectionsJson())).isEqualTo(blocks);
  assertThat(jdbc.queryForObject("SELECT retired FROM page_block_identities WHERE block_id=?",Boolean.class,first.id())).isTrue();
 }
 @Test void legacyMissingDuplicateAndMalformedIdsAreReportedWithoutGuessingOrMigration()throws Exception {
  var b=login("admin");var original=pages.get(actor(),page);var blocks=pages.sections(original.sectionsJson());
  for(String source:List.of("[{\"type\":\"HERO\",\"heading\":\"소개\",\"visible\":true}]",json.writeValueAsString(List.of(blocks.get(0),blocks.get(0))))) {
   jdbc.update("UPDATE site_pages SET sections_json=? WHERE id=?",source,page);
   var tree=read(b,"/api/admin/next/page-structure").get(0);assertThat(tree.path("issue").isTextual()).isTrue();for(var block:tree.path("blocks"))assertThat(block.path("blockId").isNull()).isTrue();
   assertThat(jdbc.queryForObject("SELECT sections_json FROM site_pages WHERE id=?",String.class,page)).isEqualTo(source);
  }
  jdbc.update("UPDATE site_pages SET sections_json='invalid-json' WHERE id=?",page);
  var malformed=read(b,"/api/admin/next/page-structure").get(0);assertThat(malformed.path("blocks").isEmpty()).isTrue();assertThat(malformed.path("issue").isTextual()).isTrue();
 }
 @Test void outlineUsesExistingPageManagerPermissions()throws Exception {
  var supporter=login("supporter");assertThat(supporter.get("/api/admin/next/page-structure").statusCode()).isEqualTo(403);
  assertThat(supporter.get("/api/admin/next/pages/"+page).statusCode()).isEqualTo(403);
 }
}
