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

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.datasource.url=jdbc:h2:mem:posts-block-manual;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class PostsBlockManualIntegrationTest {
 @LocalServerPort int port;
 @Autowired JdbcTemplate jdbc;
 @Autowired ObjectMapper json;
 @Autowired PasswordEncoder encoder;
 @Autowired AccountMapper accounts;
 @Autowired PageService pages;
 @Autowired PostService posts;
 static final String API="/api/admin/next/pages/",PASSWORD="Query-Test-Password42";
 long page,life,both,project,faq,restaurant,draft;
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
  draft=create("미발행 후기","REVIEW",List.of(102L),List.of(201L),"save");
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

 ObjectNode manual(ObjectNode document,Long... ids){var block=(ObjectNode)document.path("sections").get(0);block.put("sourceMode","manual");block.set("manual",json.valueToTree(Map.of("postIds",List.of(ids))));return document;}
 JsonNode states(HttpBrowser b,Long... ids)throws Exception{return get(b,API+"selected-posts?ids="+Arrays.stream(ids).map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));}
 List<Long> selected(JsonNode document){var values=new ArrayList<Long>();document.path("sections").get(0).path("manual").path("postIds").forEach(id->values.add(id.asLong()));return values;}

 @Test void manualRoundTripsMixedTypesInChosenOrderAndSkipsOnlyUnpublished()throws Exception {
  var b=login("admin");var published=get(b,API+page+"/publication");var input=manual(configure(input(b),"REVIEW",List.of(101L),List.of(202L),1),faq,draft,life,restaurant);
  var saved=save(b,input);assertThat(selected(saved)).containsExactly(faq,draft,life,restaurant);assertThat(get(b,API+page)).isEqualTo(saved);
  var result=preview(b,(ObjectNode)saved);assertThat(ids(result)).containsExactly(faq,life,restaurant);assertThat(result.path("total").asLong()).isEqualTo(3);
  assertThat(states(b,faq,draft,life,restaurant).findValuesAsText("status")).containsExactly("PUBLISHED","UNPUBLISHED","PUBLISHED","PUBLISHED");
  assertThat(get(b,API+page+"/publication")).isEqualTo(published);
  for(String mode:List.of("manage","structure"))assertThat(get(b,API+page+"?view="+mode)).isEqualTo(saved);
  var changed=save(b,manual((ObjectNode)saved.deepCopy(),restaurant,faq,draft,life));assertThat(selected(changed)).containsExactly(restaurant,faq,draft,life);assertThat(ids(preview(b,(ObjectNode)changed))).containsExactly(restaurant,faq,life);
 }
 @Test void deletedPrivateMissingAndNoSnapshotReferencesStayConfiguredAndDoNotFailTheOthers()throws Exception {
  var b=login("admin");var saved=save(b,manual(input(b),restaurant,life,faq,draft,999999L));legacy(b,saved,"publish");var before=get(b,API+page+"/publication");
  posts.delete(root(),life,posts.get(actor(),life).revision());posts.unpublish(actor(),faq,posts.get(actor(),faq).revision());
  assertThat(states(b,restaurant,life,faq,draft,999999L).findValuesAsText("status")).containsExactly("PUBLISHED","DELETED","PRIVATE","UNPUBLISHED","UNAVAILABLE");
  assertThat(ids(get(b,API+page+"/publication/preview").path("sections").get(0))).containsExactly(restaurant);
  var document=input(b);var resaved=save(b,document);assertThat(selected(resaved)).containsExactly(restaurant,life,faq,draft,999999L);assertThat(get(b,API+page+"/publication")).isEqualTo(before);
  legacy(b,resaved,"publish");assertThat(selected(get(b,API+page+"/publication"))).containsExactly(restaurant,life,faq,draft,999999L);
  updatePost(draft,null,"publish");assertThat(ids(get(b,API+page+"/publication/preview").path("sections").get(0))).containsExactly(restaurant,draft);
  jdbc.update("DELETE FROM post_publications WHERE post_id=?",restaurant);assertThat(states(b,restaurant).get(0).path("status").asText()).isEqualTo("UNPUBLISHED");assertThat(ids(get(b,API+page+"/publication/preview").path("sections").get(0))).containsExactly(draft);
 }
 @Test void contentDraftTitleAndBodyAreHiddenUntilRepublishingWithoutReorderingTheBlock()throws Exception {
  var b=login("admin");var saved=save(b,manual(input(b),life,restaurant));legacy(b,saved,"publish");var p=posts.get(actor(),life);
  posts.save(actor(),life,p.revision(),"새 초안 제목","새 초안 본문",p.categoryId(),List.of(),"save",null);
  var preview=get(b,API+page+"/publication/preview").path("sections").get(0);assertThat(ids(preview)).containsExactly(life,restaurant);assertThat(preview.path("posts").get(0).path("title").asText()).isEqualTo("생활 후기");assertThat(preview.path("posts").get(0).path("content").asText()).isEqualTo("발행 본문");
  var status=states(b,life).get(0);assertThat(status.path("title").asText()).isEqualTo("새 초안 제목");assertThat(status.path("publicationTitle").asText()).isEqualTo("생활 후기");
  updatePost(life,null,"publish");preview=get(b,API+page+"/publication/preview").path("sections").get(0);assertThat(ids(preview)).containsExactly(life,restaurant);assertThat(preview.path("posts").get(0).path("title").asText()).isEqualTo("새 초안 제목");assertThat(preview.path("posts").get(0).path("content").asText()).isEqualTo("새 초안 본문");
 }
 @Test void pageDraftOrderingAndDeselectionWaitForLegacyPublicationAndKeepAllSources()throws Exception {
  var b=login("admin");var saved=save(b,manual(configure(input(b),"REVIEW",List.of(),List.of(201L),6),life,restaurant));legacy(b,saved,"publish");var pub=get(b,API+page+"/publication");
  saved=save(b,manual(input(b),restaurant,faq,life));legacy(b,saved,"save");assertThat(get(b,API+page+"/publication")).isEqualTo(pub);assertThat(ids(get(b,API+page+"/publication/preview").path("sections").get(0))).containsExactly(life,restaurant);
  legacy(b,get(b,API+page),"publish");assertThat(selected(get(b,API+page+"/publication"))).containsExactly(restaurant,faq,life);assertThat(ids(get(b,API+page+"/publication/preview").path("sections").get(0))).containsExactly(restaurant,faq,life);
  var original=input(b).path("sections").get(0).deepCopy();
  for(String mode:List.of("category","query","manual")){
   var document=input(b);((ObjectNode)document.path("sections").get(0)).put("sourceMode",mode);var round=save(b,document);
   for(String field:List.of("query","manual","categoryId","id"))assertThat(round.path("sections").get(0).path(field)).isEqualTo(original.path(field));
   var result=preview(b,(ObjectNode)round);if(mode.equals("category"))assertThat(ids(result)).hasSize(5);if(mode.equals("query"))assertThat(ids(result)).containsExactly(both,life);if(mode.equals("manual"))assertThat(ids(result)).containsExactly(restaurant,faq,life);
  }
  saved=save(b,manual(input(b)));assertThat(selected(saved)).isEmpty();assertThat(ids(preview(b,(ObjectNode)saved))).isEmpty();assertThat(selected(get(b,API+page+"/publication"))).containsExactly(restaurant,faq,life);
 }
 @Test void cloneGetsNewIdentityAndIndependentManualSelection()throws Exception {
  var b=login("admin");var saved=save(b,manual(input(b),restaurant,life,draft));var source=pages.sections(pages.get(actor(),page).sectionsJson()).get(0);var clone=PageBlockService.duplicate(source);assertThat(clone.id()).isNotEqualTo(source.id());assertThat(clone.manual()).isEqualTo(source.manual());
  var copied=(ObjectNode)json.valueToTree(clone);copied.set("manual",json.valueToTree(Map.of("postIds",List.of(life,restaurant))));var document=(ObjectNode)saved.deepCopy();document.set("sections",json.createArrayNode().add(copied).add(saved.path("sections").get(0)));var result=save(b,document);
  assertThat(result.path("sections").get(1)).isEqualTo(saved.path("sections").get(0));assertThat(result.path("sections").get(0).path("id").asText()).isEqualTo(clone.id());assertThat(ids(preview(b,(ObjectNode)result))).containsExactly(life,restaurant);
 }
 @Test void duplicateInvalidOversizedAndOmittedManualSettingsAreRejectedWithoutMutation()throws Exception {
  var b=login("admin");var saved=save(b,manual(input(b),life,restaurant));
  for(String failure:List.of("duplicate","zero","negative","tooMany","missing","nonPosts","unknown")){
   var d=(ObjectNode)saved.deepCopy();var block=(ObjectNode)d.path("sections").get(0);
   switch(failure){case "duplicate"->manual(d,life,life);case "zero"->manual(d,0L);case "negative"->manual(d,-1L);case "tooMany"->manual(d,java.util.stream.LongStream.rangeClosed(1,21).boxed().toArray(Long[]::new));case "missing"->{block.put("sourceMode","category");block.remove("manual");}case "nonPosts"->block.put("type","TEXT");case "unknown"->((ObjectNode)block.path("manual")).put("title","Do not copy content");}
   assertThat(b.json("PUT",API+page,d.toString(),csrf(b)).statusCode()).as(failure).isEqualTo(400);assertThat(get(b,API+page)).isEqualTo(saved);
  }
  var old=(ObjectNode)saved.deepCopy();old.put("revision",saved.path("revision").asLong()-1);assertThat(b.json("PUT",API+page,old.toString(),csrf(b)).statusCode()).isEqualTo(409);
  assertThat(b.get(API+"selected-posts?ids="+life+","+life).statusCode()).isEqualTo(400);
 }
 @Test void pickerReusesExistingSearchFiltersAndManagerPermissionProtectsSelectedStates()throws Exception {
  var b=login("admin");var result=get(b,"/api/admin/next/posts?q="+java.net.URLEncoder.encode("생활",java.nio.charset.StandardCharsets.UTF_8)+"&typeCodes=REVIEW&cohortIds=101,102&topicIds=201,202");assertThat(result.path("total").asLong()).isEqualTo(1);assertThat(result.path("items").get(0).path("id").asLong()).isEqualTo(life);
  var supporter=login("supporter");assertThat(supporter.get(API+"selected-posts?ids="+life).statusCode()).isEqualTo(403);
  var d=manual(input(b),life);assertThat(b.json("PUT",API+page,d.toString(),null).statusCode()).isEqualTo(403);
 }
}

