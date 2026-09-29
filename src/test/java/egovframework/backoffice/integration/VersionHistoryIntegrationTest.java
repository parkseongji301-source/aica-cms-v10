package egovframework.backoffice.integration;
import com.fasterxml.jackson.databind.*;
import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.post.*;
import egovframework.backoffice.mvp.security.*;
import egovframework.backoffice.mvp.version.*;
import egovframework.backoffice.mvp.classification.ClassificationModels.Selection;
import egovframework.backoffice.mvp.restaurant.RestaurantDetailsService.Details;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;
import static egovframework.backoffice.mvp.version.VersionKind.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.datasource.url=jdbc:h2:mem:version-history;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class VersionHistoryIntegrationTest {
 @LocalServerPort int port;
 @Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired AccountMapper accounts;@Autowired PasswordEncoder encoder;
 @Autowired PostService posts;@Autowired PageService pages;@Autowired PageTemplateService templates;@Autowired MediaService media;@Autowired UsageService usages;@Autowired DeletionImpactService impacts;
 @Autowired VersionStore versions;@Autowired VersionHistoryService history;@Autowired VersionRestoreService restore;@Autowired VersionSnapshots snapshots;
 static final String API="/api/admin/next",PASSWORD="Version-Test42";
 long post,page,category,c6,c7,life,project;
 AccountPrincipal actor(String role){return new AccountPrincipal(accounts.findByEmail(role.toLowerCase(Locale.ROOT)+"@versions.test"));}
 HttpBrowser login(String role)throws Exception{var b=new HttpBrowser(port);b.login(role.toLowerCase(Locale.ROOT)+"@versions.test",PASSWORD,"/admin");return b;}
 JsonNode read(java.net.http.HttpResponse<String> r)throws Exception{assertThat(r.statusCode()).as(r.body()).isIn(200,201);return json.readTree(r.body());}
 String csrf(HttpBrowser b)throws Exception{return read(b.get(API+"/bootstrap")).path("csrf").path("token").asText();}
 @BeforeEach void setup(){
  for(String t:List.of("version_baseline_runs","page_templates","post_publication_media","post_media","post_publications","page_media","page_publications","page_block_identities","site_menus","site_pages","posts","media","categories","activity_log","users","content_type_topics","topics","cohorts"))jdbc.update("DELETE FROM "+t);
  jdbc.update("UPDATE site_settings SET setting_value='' WHERE setting_key IN ('logoId','homePageId')");
  String hash=encoder.encode(PASSWORD);
  for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,?,FALSE)",role.toLowerCase(Locale.ROOT)+"@versions.test",role,hash,role);
  jdbc.update("INSERT INTO categories(name) VALUES('기존 분류')");category=jdbc.queryForObject("SELECT id FROM categories",Long.class);
  jdbc.update("INSERT INTO cohorts(code,name) VALUES('COHORT_06','6기'),('COHORT_07','7기')");
  c6=jdbc.queryForObject("SELECT id FROM cohorts WHERE code='COHORT_06'",Long.class);c7=jdbc.queryForObject("SELECT id FROM cohorts WHERE code='COHORT_07'",Long.class);
  jdbc.update("INSERT INTO topics(code,name) VALUES('REVIEW_LIFE','생활'),('REVIEW_PROJECT','프로젝트')");
  life=jdbc.queryForObject("SELECT id FROM topics WHERE code='REVIEW_LIFE'",Long.class);project=jdbc.queryForObject("SELECT id FROM topics WHERE code='REVIEW_PROJECT'",Long.class);
  jdbc.update("INSERT INTO content_type_topics(type_code,topic_id) SELECT 'REVIEW',id FROM topics");
  post=posts.save(actor("SUPPORTER"),null,null,"원본","본문",category,List.of(),"save");
  page=pages.save(actor("SUPER_ADMIN"),null,null,"원래 페이지","version-page","[{\"type\":\"HERO\",\"heading\":\"A\",\"body\":\"내용 A\",\"visible\":true},{\"type\":\"TEXT\",\"heading\":\"B\",\"body\":\"내용 B\",\"visible\":false}]","publish");
 }
 long revision(){return posts.get(actor("ADMIN"),post).revision();}
 void savePost(String title,String action,SaveIntent intent){posts.save(actor("ADMIN"),post,revision(),title,"본문",category,List.of(),action,null,null,null,intent);}
 VersionStore.Entry latest(VersionKind k,long id){return versions.list(k,id,0).get(0);}
 long image()throws Exception{var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);return media.upload(actor("SUPER_ADMIN"),new org.springframework.mock.web.MockMultipartFile("file","version.png","image/png",out.toByteArray()),"");}
 @Test void explicitAndAutomaticAndLegacySavesHaveDifferentHistorySemantics()throws Exception{
  var b=login("SUPPORTER");String token=csrf(b);var input=(com.fasterxml.jackson.databind.node.ObjectNode)read(b.get(API+"/posts/"+post));
  input.remove("restaurant");input.put("title","자동 초안").put("saveIntent","AUTOSAVE");
  var saved=read(b.json("PUT",API+"/posts/"+post,input.toString(),token));assertThat(versions.count(POST,post)).isZero();
  input.put("revision",saved.path("revision").asLong()).put("saveIntent","MANUAL_DRAFT");
  var manual=read(b.json("PUT",API+"/posts/"+post,input.toString(),token));assertThat(manual.path("revision")).isEqualTo(saved.path("revision"));assertThat(versions.count(POST,post)).isEqualTo(1);
  read(b.json("PUT",API+"/posts/"+post,input.toString(),token));assertThat(versions.count(POST,post)).isEqualTo(2);
  input.remove("saveIntent");input.put("title","구형 호환 저장");read(b.json("PUT",API+"/posts/"+post,input.toString(),token));assertThat(versions.count(POST,post)).isEqualTo(2);
  input.put("revision",revision()).put("saveIntent","PUBLISH");assertThat(b.json("PUT",API+"/posts/"+post,input.toString(),token).statusCode()).isEqualTo(400);
 }
 @Test void pageManualCheckpointAfterAutosaveDoesNotIncrementRevision()throws Exception{
  var b=login("ADMIN");var doc=(com.fasterxml.jackson.databind.node.ObjectNode)read(b.get(API+"/pages/"+page));String token=csrf(b);
  doc.put("title","페이지 자동저장").put("saveIntent","AUTOSAVE");var saved=read(b.json("PUT",API+"/pages/"+page,doc.toString(),token));long before=versions.count(PAGE,page);
  doc.put("revision",saved.path("revision").asLong()).put("saveIntent","MANUAL_DRAFT");var manual=read(b.json("PUT",API+"/pages/"+page,doc.toString(),token));
  assertThat(manual.path("revision")).isEqualTo(saved.path("revision"));assertThat(versions.count(PAGE,page)).isEqualTo(before+1);
 }
 @Test void baselineIsSeparatePinnedAndOneTimeAndPublishingIsAnImmutableSnapshot()throws Exception{
  savePost("발행 원문","publish",SaveIntent.LEGACY);long published=latest(POST,post).id();
  String immutable=versions.get(POST,post,published).snapshotJson();
  templates.save(actor("SUPER_ADMIN"),null,null,"도입 템플릿","용도",true,pages.sections(pages.get(actor("ADMIN"),page).sectionsJson()));
  assertThat(history.baseline(actor("SUPER_ADMIN")).values()).containsExactly(2,2,1);
  for(int i=0;i<23;i++)savePost("초안 "+i,"save",SaveIntent.MANUAL_DRAFT);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_versions WHERE reason='MANUAL_DRAFT'",Long.class)).isEqualTo(20);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_versions WHERE reason LIKE 'BASELINE_%'",Long.class)).isEqualTo(2);
  assertThat(versions.get(POST,post,published).snapshotJson()).isEqualTo(immutable);
  assertThat(immutable).isEqualTo(snapshots.capture(POST,post,true).toString());
  posts.save(actor("SUPER_ADMIN"),null,null,"후속 생성","본문",null,List.of(),"save");
  assertThat(history.baseline(actor("SUPER_ADMIN")).values()).allMatch(n->n==0);
 }
 @Test void postRestoreRecoversClassificationAttachmentOrderAndAddressWithoutPublishing()throws Exception{
  long file=image();var owner=actor("ADMIN");var selection=new Selection("RESTAURANT",List.of(c6,c7),List.of());
  posts.save(owner,post,revision(),"식당","식당 소개",category,List.of(file),"publish",null,selection,new Details("광주 첫 주소"),SaveIntent.LEGACY);
  long source=latest(POST,post).id();String publication=snapshots.capture(POST,post,true).toString();long author=posts.get(owner,post).authorId();
  posts.save(owner,post,revision(),"후기 초안","후기 본문",null,List.of(),"save",null,new Selection("REVIEW",List.of(c7),List.of(life,project)),new Details(""),SaveIntent.MANUAL_DRAFT);
  long before=revision();var result=restore.restore(owner,POST,post,source,before,UUID.randomUUID().toString());
  assertThat(revision()).isEqualTo(before+1);assertThat(result.revision()).isEqualTo(before+1);
  assertThat(posts.get(owner,post).authorId()).isEqualTo(author);assertThat(posts.get(owner,post).status()).isEqualTo("PUBLISHED");
  assertThat(posts.classification(owner,post).cohortIds()).containsExactly(c6,c7);assertThat(posts.restaurant(owner,post).address()).isEqualTo("광주 첫 주소");
  assertThat(posts.attachments(owner,post)).extracting(CmsModels.Media::id).containsExactly(file);
  assertThat(posts.get(owner,post).categoryId()).isEqualTo(category);
  assertThat(snapshots.capture(POST,post,true).toString()).isEqualTo(publication);
  var restored=latest(POST,post);assertThat(restored.reason()).isEqualTo("RESTORE");assertThat(restored.sourceVersionId()).isEqualTo(source);
  var backup=versions.list(POST,post,0).get(1);assertThat(backup.reason()).isEqualTo("RESTORE_BACKUP");
  assertThat(snapshots.decode(backup).path("classification").path("topicIds")).hasSize(2);
  restore.restore(owner,POST,post,backup.id(),revision(),null);
  assertThat(posts.classification(owner,post).topicIds()).containsExactly(life,project);assertThat(posts.restaurant(owner,post)).isNull();
  var p=posts.get(owner,post);posts.save(owner,post,p.revision(),p.title(),p.content(),p.categoryId(),List.of(),"publish",p.richContent(),null,null,SaveIntent.LEGACY);
  assertThat(snapshots.capture(POST,post,true).path("classification").path("typeCode").asText()).isEqualTo("REVIEW");
 }
 @Test void richDocumentAndMediaAreRestoredWithoutReassigningAuthor()throws Exception{
  long file=image();String rich=json.writeValueAsString(Map.of("ops",List.of(Map.of("insert","사진 설명\n"),Map.of("insert",Map.of("aicaImage",Map.of("id",file))))));
  posts.save(actor("ADMIN"),post,revision(),"리치 후기","",category,List.of(),"save",rich,new Selection("REVIEW",List.of(c6,c7),List.of(life,project)),null,SaveIntent.MANUAL_DRAFT);
  long source=latest(POST,post).id();savePost("다른 본문","save",SaveIntent.AUTOSAVE);
  restore.restore(actor("ADMIN"),POST,post,source,revision(),null);
  assertThat(posts.get(actor("ADMIN"),post).richContent()).contains("aicaImage");
  assertThat(posts.classification(actor("ADMIN"),post).topicIds()).containsExactly(life,project);
 }
 @Test void pageRestoreMaintainsLivingIdsRemapsRetiredIdsAndKeepsSlugAndPublication()throws Exception{
  var a=actor("SUPER_ADMIN");var original=pages.get(a,page);var originalBlocks=pages.sections(original.sectionsJson());String living=originalBlocks.get(0).id(),deleted=originalBlocks.get(1).id();
  long source=latest(PAGE,page).id();String published=pages.publication(a,page).sectionsJson();
  pages.save(a,page,original.revision(),"수정 페이지","changed-slug",json.writeValueAsString(List.of(originalBlocks.get(0))),"save",SaveIntent.MANUAL_DRAFT);
  var current=pages.get(a,page);var restored=restore.restore(actor("ADMIN"),PAGE,page,source,current.revision(),null);
  var after=pages.get(a,page);var blocks=pages.sections(after.sectionsJson());
  assertThat(after.slug()).isEqualTo("changed-slug");assertThat(after.revision()).isEqualTo(current.revision()+1);
  assertThat(blocks.get(0).id()).isEqualTo(living);assertThat(blocks.get(1).id()).isNotEqualTo(deleted).isEqualTo(restored.blockIds().get(deleted));
  assertThat(blocks.get(1).visible()).isFalse();assertThat(pages.publication(a,page).sectionsJson()).isEqualTo(published);
  assertThat(jdbc.queryForObject("SELECT retired FROM page_block_identities WHERE block_id=?",Boolean.class,deleted)).isTrue();
  pages.save(a,page,after.revision(),after.title(),after.slug(),after.sectionsJson(),"publish");
  assertThat(pages.publication(a,page).sectionsJson()).isEqualTo(after.sectionsJson());
 }
 @Test void postsModesAndHiddenVariationsSurviveTemplateAndPageRestoration()throws Exception{
  var a=actor("SUPER_ADMIN");
  String blocks=json.writeValueAsString(List.of(
   Map.of("id",PageBlockService.newId(),"type","HERO","variation","centered","schemaVersion",2,"heading","공용 구성","visible",false),
   Map.of("type","POSTS","sourceMode","category","categoryId",category,"visible",true),
   Map.of("type","POSTS","sourceMode","query","visible",true,"query",Map.of("typeCode","REVIEW","cohortIds",List.of(c7),"topicIds",List.of(project),"sort","LATEST","limit",6)),
   Map.of("type","POSTS","sourceMode","manual","visible",true,"manual",Map.of("postIds",List.of(post)))));
  long target=pages.save(a,null,null,"세 가지 목록","three-modes",blocks,"save",SaveIntent.MANUAL_DRAFT);var saved=pages.get(a,target);long source=latest(PAGE,target).id();
  var template=templates.save(a,null,null,"원본 템플릿","용도",true,pages.sections(saved.sectionsJson()));long tid=template.info().id(),tv=latest(TEMPLATE,tid).id();
  String templateSnapshot=versions.get(TEMPLATE,tid,tv).snapshotJson();assertThat(templateSnapshot).doesNotContain("block_").contains("category","query","manual");
  pages.save(a,target,saved.revision(),"빈 구성",saved.slug(),"[]","save",SaveIntent.MANUAL_DRAFT);
  restore.restore(a,PAGE,target,source,pages.get(a,target).revision(),null);
  assertThat(pages.sections(pages.get(a,target).sectionsJson())).extracting(CmsModels.Section::type).containsExactly("HERO","POSTS","POSTS","POSTS");
  var beforePage=pages.get(a,target);
  templates.save(a,tid,template.info().revision(),"비활성","변경",false,List.of());
  restore.restore(a,TEMPLATE,tid,tv,templates.get(a,tid).info().revision(),null);
  assertThat(templates.get(a,tid).info().active()).isTrue();assertThat(templates.get(a,tid).blocks().size()).isEqualTo(4);
  assertThat(pages.get(a,target)).isEqualTo(beforePage);assertThat(versions.get(TEMPLATE,tid,tv).snapshotJson()).isEqualTo(templateSnapshot);
  assertThat(templates.get(a,tid).blocks().get(0).path("variation").asText()).isEqualTo("centered");
 }
 @Test void historyOnlyMediaIsProtectedThenReleasedByRetentionAndPermanentDeletion()throws Exception{
  long file=image();var a=actor("ADMIN");
  posts.save(a,post,revision(),"첨부","본문",category,List.of(file),"save",null,null,null,SaveIntent.MANUAL_DRAFT);
  savePost("첨부 제거","save",SaveIntent.AUTOSAVE);
  assertThatThrownBy(()->media.delete(actor("SUPER_ADMIN"),file)).hasMessageContaining("사용 중");
  assertThat(usages.find(actor("SUPER_ADMIN"),"media",file)).anyMatch(u->u.label().contains("과거 버전"));
  assertThat(impacts.get(actor("SUPER_ADMIN"),"posts",post).history().files()).extracting(VersionMediaReferences.FileImpact::id).contains(file);
  for(int i=0;i<20;i++)savePost("보관 "+i,"save",SaveIntent.MANUAL_DRAFT);
  assertThat(versions.mediaIds(POST,post)).isEmpty();assertThat(media.required(file)).isNotNull();media.delete(actor("SUPER_ADMIN"),file);
  long retained=versions.count(POST,post);posts.delete(actor("SUPER_ADMIN"),post,revision());assertThat(versions.count(POST,post)).isEqualTo(retained);
  posts.purgeTrash(actor("SUPER_ADMIN"),post,posts.trashed(actor("SUPER_ADMIN"),post).revision());assertThat(versions.count(POST,post)).isZero();
 }
 @Test void inactiveTemplateAndHiddenPageVersionsProtectFiles()throws Exception{
  long file=image();var a=actor("SUPER_ADMIN");
  var source=pages.sections("[{\"type\":\"IMAGE\",\"schemaVersion\":2,\"variation\":\"default\",\"imageId\":"+file+",\"visible\":false}]");
  var t=templates.save(a,null,null,"파일 보관","",false,source);long id=t.info().id();
  templates.save(a,id,t.info().revision(),"파일 해제","",false,List.of());
  assertThatThrownBy(()->media.delete(a,file)).hasMessageContaining("사용 중");
  assertThat(versions.mediaIds(TEMPLATE,id)).containsExactly(file);
  for(int i=0;i<20;i++){var current=templates.get(a,id);templates.save(a,id,current.info().revision(),"새 구성 "+i,"",false,List.of());}
  assertThat(versions.count(TEMPLATE,id)).isEqualTo(20);media.delete(a,file);
 }
 @Test void rolesAndThymeleafRequestsShareHistoryContracts()throws Exception{
  var root=login("SUPER_ADMIN");var supporter=login("SUPPORTER");var admin=login("ADMIN");
  var manual=Map.of("id",""+post,"revision",""+revision(),"title","수동 기록","content","본문","action","save","saveIntent","MANUAL_DRAFT");
  read(supporter.post("/admin/posts/save-json",manual));long version=latest(POST,post).id();
  assertThat(supporter.get(API+"/posts/"+post+"/versions").statusCode()).isEqualTo(200);
  assertThat(supporter.json("POST",API+"/posts/"+post+"/versions/"+version+"/restore","{\"expectedRevision\":"+revision()+",\"confirmed\":true}",csrf(supporter)).statusCode()).isEqualTo(403);
  assertThat(supporter.get(API+"/pages/"+page+"/versions").statusCode()).isEqualTo(403);
  assertThat(admin.get(API+"/pages/"+page+"/versions").statusCode()).isEqualTo(200);
  assertThat(admin.get(API+"/page-templates/1/versions").statusCode()).isEqualTo(403);
  long foreign=posts.save(actor("SUPER_ADMIN"),null,null,"타인","본문",null,List.of(),"save",null,null,null,SaveIntent.MANUAL_DRAFT);
  assertThat(supporter.get(API+"/posts/"+foreign+"/versions").statusCode()).isEqualTo(403);
  var publish=Map.of("id",""+post,"revision",""+revision(),"title","발행 기록","content","본문","action","publish","saveIntent","MANUAL_DRAFT");
  read(admin.post("/admin/posts/save-json",publish));assertThat(latest(POST,post).reason()).isEqualTo("PUBLISH");
  assertThat(root.get("/admin/posts/"+post+"/delete-confirm").body()).contains("개 버전 이력을 유지합니다.").doesNotContain("함께 삭제할 버전 이력");
  var before=revision();var res=read(admin.json("POST",API+"/posts/"+post+"/versions/"+version+"/restore","{\"expectedRevision\":"+before+",\"confirmed\":true}",csrf(admin)));
  assertThat(res.path("revision").asLong()).isEqualTo(before+1);
 }
 @Test void revisionConflictAndFailuresRollBackDraftPublicationBackupAndHistory(){
  savePost("원본 발행","publish",SaveIntent.LEGACY);long source=latest(POST,post).id();long rev=revision(),count=versions.count(POST,post);String pub=snapshots.capture(POST,post,true).toString();
  assertThatThrownBy(()->restore.restore(actor("ADMIN"),POST,post,source,rev-1,null)).isInstanceOf(RuntimeException.class);
  assertThat(versions.count(POST,post)).isEqualTo(count);
  for(String reason:List.of("MANUAL_DRAFT","PUBLISH","RESTORE")){
   jdbc.execute("ALTER TABLE post_versions ADD CONSTRAINT fail_version CHECK(id <= "+jdbc.queryForObject("SELECT MAX(id) FROM post_versions",Long.class)+" OR reason <> '"+reason+"')");
   try {
    if(reason.equals("RESTORE"))assertThatThrownBy(()->restore.restore(actor("ADMIN"),POST,post,source,rev,null)).isInstanceOf(RuntimeException.class);
    else assertThatThrownBy(()->savePost("실패해야 함",reason.equals("PUBLISH")?"publish":"save",SaveIntent.MANUAL_DRAFT)).isInstanceOf(RuntimeException.class);
    assertThat(revision()).isEqualTo(rev);assertThat(versions.count(POST,post)).isEqualTo(count);assertThat(snapshots.capture(POST,post,true).toString()).isEqualTo(pub);
   }finally{jdbc.execute("ALTER TABLE post_versions DROP CONSTRAINT fail_version");}
  }
 }
 @Test void templatePreparationDoesNotWriteAndRestorationRequiresConfirmation()throws Exception{
  var a=actor("SUPER_ADMIN");var t=templates.save(a,null,null,"복구할 템플릿","설명",true,pages.sections(pages.get(a,page).sectionsJson()));long id=t.info().id(),source=latest(TEMPLATE,id).id();
  templates.save(a,id,t.info().revision(),"다른 이름","",false,List.of());var before=templates.get(a,id);long count=versions.count(TEMPLATE,id);
  var b=login("SUPER_ADMIN");String token=csrf(b),url=API+"/page-templates/"+id+"/versions/"+source;
  read(b.json("POST",url+"/prepare-restore","{}",token));assertThat(templates.get(a,id)).isEqualTo(before);assertThat(versions.count(TEMPLATE,id)).isEqualTo(count);
  assertThat(b.json("POST",url+"/restore","{\"expectedRevision\":"+before.info().revision()+"}",token).statusCode()).isEqualTo(400);
  read(b.json("POST",url+"/restore","{\"expectedRevision\":"+before.info().revision()+",\"confirmed\":true}",token));
  assertThat(templates.get(a,id).info().name()).isEqualTo("복구할 템플릿");assertThat(versions.count(TEMPLATE,id)).isEqualTo(count+2);
 }
 @Test void pageHistoryProtectsHiddenMediaAndThymeleafCreatesVersions()throws Exception{
  long file=image();var a=actor("SUPER_ADMIN");
  long target=pages.save(a,null,null,"파일 페이지","history-file","[{\"type\":\"IMAGE\",\"imageId\":"+file+",\"visible\":false}]","save",SaveIntent.MANUAL_DRAFT);
  var old=pages.get(a,target);pages.save(a,target,old.revision(),old.title(),old.slug(),"[]","save",SaveIntent.AUTOSAVE);
  assertThat(versions.mediaIds(PAGE,target)).containsExactly(file);assertThatThrownBy(()->media.delete(a,file)).hasMessageContaining("사용 중");
  var b=login("ADMIN");var current=pages.get(a,target);
  read(b.post("/admin/pages/save-json",Map.of("id",""+target,"revision",""+current.revision(),"title",current.title(),"slug",current.slug(),"sectionsJson","[]","action","save","saveIntent","MANUAL_DRAFT")));
  assertThat(latest(PAGE,target).reason()).isEqualTo("MANUAL_DRAFT");
  current=pages.get(a,target);
  read(b.post("/admin/pages/save-json",Map.of("id",""+target,"revision",""+current.revision(),"title",current.title(),"slug",current.slug(),"sectionsJson",json.writeValueAsString(List.of(Map.of("id",PageBlockService.newId(),"schemaVersion",2,"type","HERO","variation","default","visible",true,"heading","발행용 소개"))),"action","publish","saveIntent","MANUAL_DRAFT")));
  assertThat(latest(PAGE,target).reason()).isEqualTo("PUBLISH");
  pages.delete(a,target,pages.get(a,target).revision());assertThat(versions.count(PAGE,target)).isZero();media.delete(a,file);
 }
 @Test void restoreRejectsForeignOrMissingBlockIdentityAndRetryDoesNotRestoreTwice(){
  var a=actor("SUPER_ADMIN");long source=latest(PAGE,page).id();var original=pages.get(a,page);String id=pages.sections(original.sectionsJson()).get(0).id();long count=versions.count(PAGE,page);
  long other=pages.save(a,null,null,"다른 페이지","other-history-page","[]","save");
  jdbc.update("UPDATE page_block_identities SET page_id=? WHERE block_id=?",other,id);
  assertThatThrownBy(()->restore.restore(a,PAGE,page,source,original.revision(),null)).hasMessageContaining("소속");assertThat(versions.count(PAGE,page)).isEqualTo(count);
  jdbc.update("UPDATE page_block_identities SET page_id=? WHERE block_id=?",page,id);
  String operation=UUID.randomUUID().toString();var result=restore.restore(a,PAGE,page,source,original.revision(),operation);
  var retry=restore.restore(a,PAGE,page,source,original.revision(),operation);assertThat(retry.alreadyApplied()).isTrue();assertThat(retry.revision()).isEqualTo(result.revision());assertThat(versions.count(PAGE,page)).isEqualTo(count+2);
  jdbc.update("DELETE FROM page_block_identities WHERE block_id=?",id);
  assertThatThrownBy(()->restore.restore(a,PAGE,page,source,result.revision(),null)).hasMessageContaining("등록 기록");assertThat(versions.count(PAGE,page)).isEqualTo(count+2);
 }
 @Test void templateRequestsWithoutIntentDoNotInventManualHistory()throws Exception{
  var b=login("SUPER_ADMIN");String token=csrf(b);
  var input=json.createObjectNode().put("name","구형 템플릿").put("description","").put("active",true);input.putArray("blocks");
  var doc=read(b.json("POST",API+"/page-templates",input.toString(),token));long id=doc.path("info").path("id").asLong();assertThat(versions.count(TEMPLATE,id)).isZero();
  input.put("revision",doc.path("info").path("revision").asLong()).put("saveIntent","MANUAL_DRAFT");
  read(b.json("PUT",API+"/page-templates/"+id,input.toString(),token));assertThat(versions.count(TEMPLATE,id)).isEqualTo(1);
 }

}

