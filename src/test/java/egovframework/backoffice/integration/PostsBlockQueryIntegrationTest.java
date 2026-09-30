package egovframework.backoffice.integration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.classification.ClassificationModels.Selection;
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

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.datasource.url=jdbc:h2:mem:posts-block-query;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class PostsBlockQueryIntegrationTest {
 @LocalServerPort int port;
 @Autowired JdbcTemplate jdbc;
 @Autowired ObjectMapper json;
 @Autowired PasswordEncoder encoder;
 @Autowired AccountMapper accounts;
 @Autowired PageService pages;
 @Autowired PostService posts;
 static final String API="/api/admin/next/pages/",PASSWORD="Query-Test-Password42";
 long page,life,both,project,faq,restaurant;
 @BeforeEach void fixture() {
  for(String table:List.of("post_publication_media","post_media","post_publications","page_block_identities","page_media","page_publications","site_menus","site_pages","posts","media","categories","activity_log","users","content_type_topics","cohorts","topics"))jdbc.update("DELETE FROM "+table);
  String hash=encoder.encode(PASSWORD);
  for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,?,FALSE)",role.toLowerCase(Locale.ROOT)+"@query.test",role,hash,role);
  jdbc.update("INSERT INTO categories(id,name) VALUES(11,'기존 분류')");
  jdbc.update("INSERT INTO cohorts(id,code,name) VALUES(101,'COHORT_06','6기'),(102,'COHORT_07','7기')");
  jdbc.update("INSERT INTO topics(id,code,name) VALUES(201,'REVIEW_LIFE','생활'),(202,'REVIEW_PROJECT','프로젝트'),(203,'REVIEW_CLASS','수업'),(204,'FAQ_LIFE','생활')");
  jdbc.update("INSERT INTO content_type_topics VALUES('REVIEW',201),('REVIEW',202),('REVIEW',203),('FAQ',204)");
  life=create("생활 후기","REVIEW",List.of(102L),List.of(201L),"publish");
  both=create("두 기수 두 주제","REVIEW",List.of(101L,102L),List.of(201L,202L),"publish");
  project=create("6기 프로젝트","REVIEW",List.of(101L),List.of(202L),"publish");
  faq=create("생활 질문","FAQ",List.of(),List.of(204L),"publish");
  restaurant=create("맛집","RESTAURANT",List.of(),List.of(),"publish");
  create("미발행 후기","REVIEW",List.of(102L),List.of(201L),"save");
  jdbc.update("UPDATE post_publications SET published_at=TIMESTAMP '2026-01-01 10:00:00'");
  page=pages.save(root(),null,null,"조건 연결","query-test","[{\"type\":\"POSTS\",\"heading\":\"기존 목록\",\"categoryId\":11,\"visible\":true}]","publish");
 }
 AccountPrincipal root(){return new AccountPrincipal(accounts.findByEmail("super_admin@query.test"));}
 AccountPrincipal actor(){return new AccountPrincipal(accounts.findByEmail("admin@query.test"));}
 long create(String title,String type,List<Long> cohorts,List<Long> topics,String action){return posts.save(actor(),null,null,title,"발행 본문",11L,List.of(),action,null,new Selection(type,cohorts,topics));}
 HttpBrowser login(String role)throws Exception{var b=new HttpBrowser(port);b.login(role+"@query.test",PASSWORD,"/admin");return b;}
 JsonNode ok(java.net.http.HttpResponse<String> r)throws Exception{assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
 JsonNode get(HttpBrowser b,String path)throws Exception{return ok(b.get(path));}
 String csrf(HttpBrowser b)throws Exception{return get(b,"/api/admin/next/bootstrap").path("csrf").path("token").asText();}
 ObjectNode input(HttpBrowser b)throws Exception{return (ObjectNode)get(b,API+page).deepCopy();}
 ObjectNode configure(ObjectNode document,String type,List<Long> cohorts,List<Long> topics,int limit){
  var block=(ObjectNode)document.path("sections").get(0);block.put("sourceMode","query");block.set("query",json.valueToTree(Map.of("typeCode",type,"cohortIds",cohorts,"topicIds",topics,"sort","LATEST","limit",limit)));return document;
 }
 JsonNode preview(HttpBrowser b,ObjectNode document)throws Exception{return ok(b.json("POST",API+page+"/preview",document.toString(),csrf(b))).path("sections").get(0);}
 List<Long> ids(JsonNode section){var ids=new ArrayList<Long>();section.path("posts").forEach(p->ids.add(p.path("id").asLong()));assertThat(ids).doesNotHaveDuplicates();return ids;}
 JsonNode save(HttpBrowser b,ObjectNode document)throws Exception{return ok(b.json("PUT",API+page,document.toString(),csrf(b)));}
 JsonNode legacy(HttpBrowser b,JsonNode document,String action)throws Exception{return ok(b.post("/admin/pages/save-json",Map.of("id",String.valueOf(page),"revision",document.path("revision").asText(),"title",document.path("title").asText(),"sectionsJson",document.path("sections").toString(),"action",action)));}
 void updatePost(long id,Selection selection,String action){var p=posts.get(actor(),id);posts.save(actor(),id,p.revision(),p.title(),p.content(),p.categoryId(),List.of(),action,p.richContent(),selection);}

 @Test void legacyAndPublicationFiltersUseOrWithinAndAcrossDimensionsWithoutDuplicates()throws Exception {
  var b=login("admin");var doc=input(b);var legacy=preview(b,doc);assertThat(ids(legacy)).containsExactly(restaurant,faq,project,both,life);assertThat(legacy.path("total").asLong()).isEqualTo(5);
  var all=preview(b,configure(doc,"REVIEW",List.of(),List.of(),20));assertThat(ids(all)).containsExactly(project,both,life);assertThat(all.path("total").asLong()).isEqualTo(3);
  var one=preview(b,configure(doc,"REVIEW",List.of(102L),List.of(201L,202L),20));assertThat(ids(one)).containsExactly(both,life);assertThat(one.path("total").asLong()).isEqualTo(2);
  var many=preview(b,configure(doc,"REVIEW",List.of(101L,102L),List.of(201L,202L),20));assertThat(ids(many)).containsExactly(project,both,life);assertThat(many.path("total").asLong()).isEqualTo(3);
  assertThat(ids(preview(b,configure(doc,"REVIEW",List.of(101L),List.of(201L),20)))).containsExactly(both);
  assertThat(ids(preview(b,configure(doc,"FAQ",List.of(),List.of(204L),20)))).containsExactly(faq);
  assertThat(ids(preview(b,configure(doc,"RESTAURANT",List.of(),List.of(),20)))).containsExactly(restaurant);
  var empty=preview(b,configure(doc,"REVIEW",List.of(),List.of(203L),20));assertThat(empty.path("total").asLong()).isZero();assertThat(ids(empty)).isEmpty();
  var limited=preview(b,configure(doc,"REVIEW",List.of(),List.of(),1));assertThat(ids(limited)).containsExactly(project);assertThat(limited.path("total").asLong()).isEqualTo(3);
  jdbc.update("UPDATE post_publications SET published_at=TIMESTAMP '2026-02-01 10:00:00' WHERE post_id=?",life);
  assertThat(ids(preview(b,configure(doc,"REVIEW",List.of(),List.of(),1)))).containsExactly(life);
 }
 @Test void contentAndPagePublicationBoundariesStayIndependentThroughLegacySaveAndPublish()throws Exception {
  var b=login("admin");var initial=configure(input(b),"REVIEW",List.of(102L),List.of(201L),6);var saved=save(b,initial);legacy(b,saved,"publish");
  var published=get(b,API+page+"/publication");var before=get(b,API+page+"/publication/preview").path("sections").get(0);assertThat(ids(before)).contains(life,both);
  updatePost(life,new Selection("REVIEW",List.of(102L),List.of(202L)),"save");
  assertThat(ids(get(b,API+page+"/publication/preview").path("sections").get(0))).containsExactlyElementsOf(ids(before));
  var draft=configure(input(b),"REVIEW",List.of(102L),List.of(202L),3);saved=save(b,draft);assertThat(get(b,API+page).path("sections")).isEqualTo(saved.path("sections"));assertThat(get(b,API+page+"/publication")).isEqualTo(published);
  assertThat(ids(get(b,API+page+"/preview").path("sections").get(0))).containsExactly(both);
  updatePost(life,null,"publish"); // Older Thymeleaf request omits classification; snapshot still preserves it.
  assertThat(ids(get(b,API+page+"/publication/preview").path("sections").get(0))).containsExactly(both);
  assertThat(ids(get(b,API+page+"/preview").path("sections").get(0))).contains(life,both);
  legacy(b,saved,"save");saved=get(b,API+page);assertThat(saved.path("sections")).isEqualTo(json.readTree(draft.path("sections").toString()));assertThat(get(b,API+page+"/publication")).isEqualTo(published);
  legacy(b,saved,"publish");assertThat(get(b,API+page+"/publication").path("sections")).isEqualTo(json.readTree(draft.path("sections").toString()));assertThat(ids(get(b,API+page+"/publication/preview").path("sections").get(0))).contains(life,both);
  assertThat(b.get("/admin/legacy/pages/"+page+"/preview").body()).contains("발행 콘텐츠","생활 후기");
 }
 @Test void queryFollowsBlockIdentityAcrossIndependentDuplicationAndReordering()throws Exception {
  var b=login("admin");var saved=save(b,configure(input(b),"REVIEW",List.of(101L,102L),List.of(201L,202L),6));var source=pages.sections(pages.get(actor(),page).sectionsJson()).get(0);var clone=PageBlockService.duplicate(source);assertThat(clone.id()).isNotEqualTo(source.id());assertThat(clone.query()).isEqualTo(source.query());
  var document=(ObjectNode)saved.deepCopy();var copy=(ObjectNode)json.valueToTree(clone);((ObjectNode)copy.path("query")).put("limit",2);document.set("sections",json.createArrayNode().add(copy).add(saved.path("sections").get(0)));var moved=save(b,document);
  assertThat(moved.path("sections").get(0).path("id").asText()).isEqualTo(clone.id());assertThat(moved.path("sections").get(1)).isEqualTo(saved.path("sections").get(0));
  assertThat(get(b,API+page).path("sections")).isEqualTo(moved.path("sections"));
  for(String view:List.of("manage","structure"))assertThat(get(b,API+page+"?view="+view).path("sections")).isEqualTo(moved.path("sections"));
 }
 @Test void invalidAndStaleClientsCannotEraseQueriesOrOverwriteConcurrentEdits()throws Exception {
  var b=login("admin");var saved=save(b,configure(input(b),"REVIEW",List.of(),List.of(201L),6));var publication=get(b,API+page+"/publication");
  for(String bad:List.of("topic","duplicate","limit0","limit21","sort","unknown","missing","type")){
   var d=(ObjectNode)saved.deepCopy();var block=(ObjectNode)d.path("sections").get(0);var q=(ObjectNode)block.path("query");
   switch(bad){case "topic"->q.set("topicIds",json.valueToTree(List.of(204)));case "duplicate"->q.set("cohortIds",json.valueToTree(List.of(101,101)));case "limit0"->q.put("limit",0);case "limit21"->q.put("limit",21);case "sort"->q.put("sort","random()");case "unknown"->q.put("sql","SELECT 1");case "missing"->block.remove(List.of("sourceMode","query"));case "type"->block.put("type","TEXT");}
   assertThat(b.json("PUT",API+page,d.toString(),csrf(b)).statusCode()).as(bad).isEqualTo(400);assertThat(get(b,API+page)).isEqualTo(saved);
  }
  var stale=(ObjectNode)saved.deepCopy();stale.put("revision",saved.path("revision").asLong()-1);assertThat(b.json("PUT",API+page,stale.toString(),csrf(b)).statusCode()).isEqualTo(409);
  assertThat(get(b,API+page+"/publication")).isEqualTo(publication);
  var category=(ObjectNode)saved.deepCopy();((ObjectNode)category.path("sections").get(0)).put("sourceMode","category");var categorySaved=save(b,category);assertThat(categorySaved.path("sections").get(0).path("query")).isEqualTo(saved.path("sections").get(0).path("query"));assertThat(ids(get(b,API+page+"/preview").path("sections").get(0))).hasSize(5);
 }
 @Test void queryPreviewRequiresExistingPermissionsAndCsrfAndExcludesPrivateDeletedPosts()throws Exception {
  var admin=login("admin");var d=configure(input(admin),"REVIEW",List.of(),List.of(),6);
  assertThat(admin.json("POST",API+page+"/preview",d.toString(),null).statusCode()).isEqualTo(403);
  var supporter=login("supporter");assertThat(supporter.get(API+page+"/publication/preview").statusCode()).isEqualTo(403);assertThat(supporter.json("POST",API+page+"/preview",d.toString(),csrf(supporter)).statusCode()).isEqualTo(403);
  jdbc.update("UPDATE posts SET status='PRIVATE' WHERE id=?",life);jdbc.update("UPDATE posts SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",both);
  var result=preview(admin,d);assertThat(ids(result)).containsExactly(project);assertThat(result.path("total").asLong()).isEqualTo(1);
 }
}

