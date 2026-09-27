package egovframework.backoffice.integration;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.post.PostService;
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

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.datasource.url=jdbc:h2:mem:page-templates;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class PageTemplateIntegrationTest {
 @LocalServerPort int port;
 @Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired PageService pages;@Autowired PostService posts;@Autowired PageTemplateService templates;@Autowired AccountMapper accounts;@Autowired PasswordEncoder encoder;
 static final String API="/api/admin/next/page-templates",PASSWORD="Template-Test42";
 long page,other,published,draft;HttpBrowser browser;String csrf;
 AccountPrincipal actor(){return new AccountPrincipal(accounts.findByEmail("super_admin@template.test"));}
 @BeforeEach void setup()throws Exception {
  for(String t:List.of("page_templates","post_publication_media","post_media","post_publications","page_media","page_publications","page_block_identities","site_menus","site_pages","posts","media","categories","activity_log","users"))jdbc.update("DELETE FROM "+t);
  String hash=encoder.encode(PASSWORD);for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,?,FALSE)",role.toLowerCase(Locale.ROOT)+"@template.test",role,hash,role);
  jdbc.update("INSERT INTO categories(id,name) VALUES(11,'기존 분류')");
  published=posts.save(actor(),null,null,"발행 원본","본문",11L,List.of(),"publish",null);draft=posts.save(actor(),null,null,"미발행 원본","본문",11L,List.of(),"save",null);
  page=pages.save(actor(),null,null,"원래 페이지","template-a","[{\"type\":\"HERO\",\"heading\":\"기존 내용\",\"visible\":true}]","publish");
  other=pages.save(actor(),null,null,"다른 페이지","template-b","[]","save");browser=login("super_admin");csrf=ok(browser.get("/api/admin/next/bootstrap")).path("csrf").path("token").asText();
 }
 HttpBrowser login(String role)throws Exception{var b=new HttpBrowser(port);b.login(role+"@template.test",PASSWORD,"/admin");return b;}
 JsonNode ok(java.net.http.HttpResponse<String> r)throws Exception{assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
 ObjectNode request()throws Exception {
  var source=pages.sections(pages.get(actor(),page).sectionsJson()).get(0);var blocks=json.createArrayNode();var hero=(ObjectNode)json.valueToTree(source);hero.put("variation","centered");hero.put("visible",false);blocks.add(hero);
  for(String mode:List.of("category","query","manual")){var b=hero.deepCopy();b.put("type","POSTS").put("id",PageBlockService.newId()).put("variation","default").put("visible",true).put("sourceMode",mode).put("categoryId",11);b.set("query",json.valueToTree(Map.of("typeCode","GENERAL","cohortIds",List.of(),"topicIds",List.of(),"sort","LATEST","limit",6)));b.set("manual",json.valueToTree(Map.of("postIds",List.of(published,draft,999999L))));blocks.add(b);}
  return json.createObjectNode().put("name","공용 구성").put("description","사용 용도").put("active",true).set("blocks",blocks);
 }
 JsonNode create()throws Exception{return ok(browser.json("POST",API,request().toString(),csrf));}
 JsonNode prepare(JsonNode d)throws Exception{return ok(browser.json("POST",API+"/"+d.path("info").path("id").asLong()+"/prepare","{\"revision\":"+d.path("info").path("revision").asLong()+"}",csrf));}
 List<CmsModels.Section> blocks(JsonNode node){return pages.sections(node.toString());}
 void savePage(long id,JsonNode sections,String action)throws Exception{var p=pages.get(actor(),id);pages.save(actor(),id,p.revision(),p.title(),p.slug(),sections.toString(),action);}
 @Test void savesOnlyBlockConfigurationAndRoundTripsAllSourcesWithoutPageIds()throws Exception {
  var input=request();var saved=ok(browser.json("POST",API,input.toString(),csrf));assertThat(ok(browser.get(API+"/"+saved.path("info").path("id").asLong()))).isEqualTo(saved);
  var original=(ArrayNode)input.path("blocks").deepCopy();original.forEach(n->((ObjectNode)n).remove("id"));assertThat(saved.path("blocks")).isEqualTo(json.readTree(original.toString()));
  assertThat(saved.path("blocks").findValues("id")).isEmpty();assertThat(saved.path("info").path("blockCount").asInt()).isEqualTo(4);
  assertThat(jdbc.queryForObject("SELECT blocks_json FROM page_templates",String.class)).doesNotContain("block_","pageId","authorId");
  assertThat(saved.path("references").findValuesAsText("status")).containsExactly("PUBLISHED","UNPUBLISHED","UNAVAILABLE");
 }
 @Test void eachPreparationCreatesFreshIdsAndDoesNotWritePagesOrRegisterIdentities()throws Exception {
  var d=create();var before=pages.get(actor(),page);var ledger=jdbc.queryForObject("SELECT COUNT(*) FROM page_block_identities",Long.class);var a=prepare(d);var b=prepare(d);
  var ids=a.path("sections").findValuesAsText("id");assertThat(ids).doesNotHaveDuplicates().doesNotContainAnyElementsOf(b.path("sections").findValuesAsText("id"));assertThat(ids).doesNotContain(blocks(json.valueToTree(pages.sections(before.sectionsJson()))).get(0).id());
  assertThat(pages.get(actor(),page)).isEqualTo(before);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM page_block_identities",Long.class)).isEqualTo(ledger);
 }
 @Test void appendAndReplaceAffectOnlyDraftUntilLegacyPublishAndPreserveEverySetting()throws Exception {
  var d=create();var publication=pages.publication(actor(),page);var existing=(ArrayNode)json.readTree(pages.get(actor(),page).sectionsJson());var copy=prepare(d).path("sections");copy.forEach(existing::add);savePage(page,existing,"save");assertThat(pages.sections(pages.get(actor(),page).sectionsJson())).hasSize(5);assertThat(pages.publication(actor(),page)).isEqualTo(publication);
  var replacement=prepare(d).path("sections");savePage(page,replacement,"save");assertThat(json.readTree(pages.get(actor(),page).sectionsJson())).isEqualTo(replacement);assertThat(pages.publication(actor(),page)).isEqualTo(publication);
  var current=pages.get(actor(),page);ok(browser.post("/admin/pages/save-json",Map.of("id",String.valueOf(page),"revision",String.valueOf(current.revision()),"title",current.title(),"sectionsJson",current.sectionsJson(),"action","publish")));
  assertThat(json.readTree(pages.publication(actor(),page).sectionsJson())).isEqualTo(replacement);assertThat(json.readTree(pages.get(actor(),page).sectionsJson())).isEqualTo(replacement);
  assertThat(jdbc.queryForObject("SELECT retired FROM page_block_identities WHERE block_id=?",Boolean.class,blocks(json.readTree(publication.sectionsJson())).get(0).id())).isTrue();
 }
 @Test void templateTwoPagesAndLaterEditsAreIndependentIncludingDeactivation()throws Exception {
  var d=create();var id=d.path("info").path("id").asLong();savePage(page,prepare(d).path("sections"),"save");savePage(other,prepare(d).path("sections"),"save");var a=pages.get(actor(),page);var b=pages.get(actor(),other);
  var input=request();input.put("revision",0);((ObjectNode)input.path("blocks").get(0)).put("heading","템플릿 수정");var changed=ok(browser.json("PUT",API+"/"+id,input.toString(),csrf));assertThat(pages.get(actor(),page)).isEqualTo(a);assertThat(pages.get(actor(),other)).isEqualTo(b);
  var edited=(ArrayNode)json.readTree(a.sectionsJson());((ObjectNode)edited.get(0)).put("heading","페이지 수정");savePage(page,edited,"save");assertThat(ok(browser.get(API+"/"+id))).isEqualTo(changed);assertThat(pages.get(actor(),other)).isEqualTo(b);
  var after=pages.get(actor(),page);var meta=json.createObjectNode().put("revision",1).put("name","비활성 구성").put("description","").put("active",false);ok(browser.json("PUT",API+"/"+id,meta.toString(),csrf));assertThat(pages.get(actor(),page)).isEqualTo(after);assertThat(pages.get(actor(),other)).isEqualTo(b);
  assertThat(browser.json("POST",API+"/"+id+"/prepare","{\"revision\":2}",csrf).statusCode()).isEqualTo(400);
 }
 @Test void staleRevisionBadVariationAndDuplicateManualIdsCannotOverwriteTemplate()throws Exception {
  var d=create();var id=d.path("info").path("id").asLong();var input=request().put("revision",-1);assertThat(browser.json("PUT",API+"/"+id,input.toString(),csrf).statusCode()).isEqualTo(409);
  assertThat(browser.json("POST",API+"/"+id+"/prepare","{\"revision\":-1}",csrf).statusCode()).isEqualTo(409);
  input.put("revision",0);((ObjectNode)input.path("blocks").get(0)).put("variation","unregistered");assertThat(browser.json("PUT",API+"/"+id,input.toString(),csrf).statusCode()).isEqualTo(400);
  input=request().put("revision",0);((ObjectNode)input.path("blocks").get(3)).set("manual",json.valueToTree(Map.of("postIds",List.of(published,published))));assertThat(browser.json("PUT",API+"/"+id,input.toString(),csrf).statusCode()).isEqualTo(400);assertThat(ok(browser.get(API+"/"+id))).isEqualTo(d);
 }
 @Test void onlySuperAdminCanReadPrepareOrManageAndCsrfIsRequired()throws Exception {
  var d=create();long id=d.path("info").path("id").asLong();for(String role:List.of("admin","supporter")){var b=login(role);var bootstrap=ok(b.get("/api/admin/next/bootstrap"));var token=bootstrap.path("csrf").path("token").asText();assertThat(bootstrap.path("permissions").path("templateUse").asBoolean()).isFalse();assertThat(bootstrap.path("permissions").path("templateManage").asBoolean()).isFalse();assertThat(b.get(API).statusCode()).isEqualTo(403);assertThat(b.get(API+"/"+id).statusCode()).isEqualTo(403);assertThat(b.json("POST",API,request().toString(),token).statusCode()).isEqualTo(403);assertThat(b.json("PUT",API+"/"+id,request().put("revision",0).toString(),token).statusCode()).isEqualTo(403);assertThat(b.json("POST",API+"/"+id+"/prepare","{\"revision\":0}",token).statusCode()).isEqualTo(403);var actor=new AccountPrincipal(accounts.findByEmail(role+"@template.test"));assertThatThrownBy(()->templates.list(actor)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);}
  assertThat(browser.json("POST",API,request().toString(),null).statusCode()).isEqualTo(403);assertThat(browser.json("POST",API+"/"+id+"/prepare","{\"revision\":0}",null).statusCode()).isEqualTo(403);
 }
 @Test void deletedAndUnpublishedManualReferencesSurviveAndAreCheckedAgainAtApply()throws Exception {
  var d=create();posts.delete(actor(),published,posts.get(actor(),published).revision());var prepared=prepare(d);assertThat(prepared.path("references").findValuesAsText("status")).containsExactly("DELETED","UNPUBLISHED","UNAVAILABLE");assertThat(prepared.path("sections").get(3).path("manual")).isEqualTo(d.path("blocks").get(3).path("manual"));savePage(page,prepared.path("sections"),"publish");assertThat(pages.views(pages.publication(actor(),page).sectionsJson()).get(2).posts()).isEmpty();
 }
}
