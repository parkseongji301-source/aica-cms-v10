package egovframework.backoffice.integration;

import com.fasterxml.jackson.databind.*;
import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.post.*;
import egovframework.backoffice.mvp.security.*;
import egovframework.backoffice.mvp.common.BusinessException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.datasource.url=jdbc:h2:mem:operating-policy;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class OperatingPolicyIntegrationTest {
 @LocalServerPort int port;
 @Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired AccountMapper accounts;@Autowired PasswordEncoder encoder;
 @Autowired PostService posts;@Autowired PageService pages;@Autowired MediaService media;@Autowired PageTemplateService templates;
 @Autowired UsageService usages;@Autowired DeletionImpactService impacts;@Autowired SiteService site;
 static final String API="/api/admin/next",PASSWORD="Policy-Test42";
 long post,page;
 AccountPrincipal actor(String role){return new AccountPrincipal(accounts.findByEmail(role.toLowerCase(Locale.ROOT)+"@policy.test"));}
 HttpBrowser login(String role)throws Exception{var b=new HttpBrowser(port);b.login(role.toLowerCase(Locale.ROOT)+"@policy.test",PASSWORD,"/admin");return b;}
 String csrf(HttpBrowser b)throws Exception{return read(b.get(API+"/bootstrap")).path("csrf").path("token").asText();}
 JsonNode read(java.net.http.HttpResponse<String> r)throws Exception{assertThat(r.statusCode()).as(r.body()).isIn(200,201);return json.readTree(r.body());}
 @BeforeEach void setup(){
  for(String t:List.of("page_templates","post_publication_media","post_media","post_publications","page_media","page_publications","page_block_identities","site_menus","site_pages","posts","media","categories","activity_log","users"))jdbc.update("DELETE FROM "+t);
  jdbc.update("UPDATE site_settings SET setting_value='' WHERE setting_key IN ('logoId','homePageId')");
  String hash=encoder.encode(PASSWORD);
  for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,?,FALSE)",role.toLowerCase(Locale.ROOT)+"@policy.test",role,hash,role);
  post=posts.save(actor("SUPPORTER"),null,null,"서포터 원본","본문",null,List.of(),"save");
  page=pages.save(actor("SUPER_ADMIN"),null,null,"기존 페이지","policy-page","[{\"type\":\"TEXT\",\"heading\":\"원본\",\"body\":\"본문\",\"visible\":true}]","publish");
 }
 Map<String,String> postFields(long id,String action){var p=posts.get(actor("SUPER_ADMIN"),id);return Map.of("id",""+id,"revision",""+p.revision(),"title",p.title(),"content",p.content(),"action",action);}
 Map<String,String> pageFields(String slug){var p=pages.get(actor("SUPER_ADMIN"),page);return Map.of("id",""+page,"revision",""+p.revision(),"title","수정 페이지","slug",slug,"sectionsJson",p.sectionsJson(),"action","publish");}
 @Test void supporterCanCreateEditDraftButCannotPublishWithdrawOrDelete()throws Exception{
  var b=login("SUPPORTER");String token=csrf(b);
  var created=read(b.json("POST",API+"/posts",json.writeValueAsString(Map.of("title","새 초안","content","본문","mediaIds",List.of(),"classification",Map.of("typeCode","GENERAL","cohortIds",List.of(),"topicIds",List.of()))),token));
  long id=created.path("id").asLong();
  var edit=(com.fasterxml.jackson.databind.node.ObjectNode)created;edit.put("title","수정 초안");edit.remove("classification");edit.remove("restaurant");
  read(b.json("PUT",API+"/posts/"+id,edit.toString(),token));
  assertThat(b.post("/admin/posts/save-json",postFields(id,"save")).statusCode()).isEqualTo(200);
  assertThat(b.post("/admin/posts/save-json",postFields(id,"publish")).statusCode()).isEqualTo(403);
  assertThat(b.post("/admin/posts/"+id+"/edit",postFields(id,"publish")).statusCode()).isEqualTo(403);
  assertThat(b.post("/admin/posts/"+id+"/unpublish",Map.of("revision",""+posts.get(actor("SUPPORTER"),id).revision())).statusCode()).isEqualTo(403);
  assertThat(b.post("/admin/posts/"+id+"/delete",Map.of("confirmed","true","revision","0")).statusCode()).isEqualTo(403);
  assertThat(b.get("/admin/legacy/posts/"+id+"/edit").body()).doesNotContain("value=\"publish\"","data-document-action=\"unpublish\"","영구 삭제 검토");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_publications WHERE post_id=?",Integer.class,id)).isZero();
 }
 @Test void adminCanOperateExistingDocumentsButCannotChangeStructure()throws Exception{
  var b=login("ADMIN");
  assertThat(b.post("/admin/posts/save-json",postFields(post,"publish")).statusCode()).isEqualTo(200);
  long revision=posts.get(actor("ADMIN"),post).revision();
  assertThat(b.post("/admin/posts/"+post+"/unpublish",Map.of("revision",""+revision)).statusCode()).isEqualTo(302);
  assertThat(posts.get(actor("ADMIN"),post).status()).isEqualTo("PRIVATE");
  assertThat(b.post("/admin/pages/save-json",pageFields("policy-page")).statusCode()).isEqualTo(200);
  assertThat(b.post("/admin/pages/save-json",pageFields("changed-url")).statusCode()).isEqualTo(403);
  assertThat(b.post("/admin/pages/save-json",Map.of("title","새 페이지","sectionsJson","[]")).statusCode()).isEqualTo(403);
  assertThat(b.get("/admin/legacy/pages/new").statusCode()).isEqualTo(403);
  assertThat(b.post("/admin/pages/"+page+"/delete",Map.of("confirmed","true","revision","0")).statusCode()).isEqualTo(403);
  assertThat(b.post("/admin/posts/"+post+"/delete",Map.of("confirmed","true","revision",""+posts.get(actor("ADMIN"),post).revision())).statusCode()).isEqualTo(403);
  var boot=read(b.get(API+"/bootstrap"));assertThat(boot.path("permissions").path("site").asBoolean()).isTrue();assertThat(boot.path("permissions").path("structure").asBoolean()).isFalse();
  for(String path:List.of("/menus","/links","/settings/basic","/settings/style","/settings/components","/settings/system","/page-templates")){
   assertThat(b.get(API+path).statusCode()).as(path).isEqualTo(403);
   assertThat(b.json("PUT",API+path,"{}",csrf(b)).statusCode()).as(path).isEqualTo(403);
  }
  read(b.get(API+"/page-structure"));read(b.get(API+"/page-components"));
  String html=b.get("/admin/legacy/pages/"+page+"/edit").body();assertThat(html).doesNotContain("페이지 주소 설정","영구 삭제 검토").contains("value=\"publish\"");
  assertThat(b.get("/admin/legacy/pages").body()).doesNotContain("href=\"/admin/legacy/pages/new\"");
  assertThatThrownBy(()->site.menu(actor("ADMIN"),null,"링크","LINK",null,"https://example.test",true)).isInstanceOf(AccessDeniedException.class);
 }
 @Test void missingAndStaleRevisionsNeverOverwriteAndWithdrawalAdvancesVersion()throws Exception{
  var root=actor("SUPER_ADMIN");long before=posts.get(root,post).revision();
  assertThatThrownBy(()->posts.save(root,post,null,"누락","본문",null,List.of(),"save")).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->posts.delete(root,post,null)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->posts.unpublish(root,post,null)).isInstanceOf(BusinessException.class);
  posts.save(root,post,before,"발행","본문",null,List.of(),"publish");
  long published=posts.get(root,post).revision();posts.unpublish(root,post,published);
  assertThat(posts.get(root,post).revision()).isEqualTo(published+1);
  assertThatThrownBy(()->posts.save(root,post,published,"오래된 발행","본문",null,List.of(),"publish")).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->posts.delete(root,post,published)).isInstanceOf(BusinessException.class);
  assertThat(posts.get(root,post).status()).isEqualTo("PRIVATE");
  var p=pages.get(root,page);assertThatThrownBy(()->pages.delete(root,page,null)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->pages.unpublish(root,page,null)).isInstanceOf(BusinessException.class);
  pages.unpublish(root,page,p.revision());assertThat(pages.get(root,page).revision()).isEqualTo(p.revision()+1);
  assertThatThrownBy(()->pages.save(root,page,p.revision(),"오래된 페이지",p.slug(),p.sectionsJson(),"publish")).isInstanceOf(BusinessException.class);
  var b=login("SUPER_ADMIN");
  assertThat(b.post("/admin/posts/"+post+"/edit",Map.of("title","누락 요청","content","본문")).statusCode()).isEqualTo(400);
  var document=(com.fasterxml.jackson.databind.node.ObjectNode)read(b.get(API+"/posts/"+post));document.put("revision",published);document.remove("classification");document.remove("restaurant");
  assertThat(b.json("PUT",API+"/posts/"+post,document.toString(),csrf(b)).statusCode()).isEqualTo(409);
 }
 @Test void deletionRequiresImpactAcknowledgementAndKeepsManualIds()throws Exception{
  var root=actor("SUPER_ADMIN");var b=login("SUPER_ADMIN");
  var p=pages.get(root,page);String blocks="[{\"type\":\"POSTS\",\"sourceMode\":\"manual\",\"manual\":{\"postIds\":["+post+"]},\"visible\":true}]";
  long linked=pages.save(root,null,null,"참조 페이지","linked-page",blocks,"publish");
  var template=templates.save(root,null,null,"참조 템플릿","",false,pages.sections(pages.get(root,linked).sectionsJson()));
  var impact=impacts.get(root,"posts",post);assertThat(impact.uses()).hasSize(3);
  String html=b.get("/admin/legacy/posts/"+post+"/delete-confirm").body();assertThat(html).contains("참조 페이지","참조 템플릿","name=\"confirmed\"","휴지통");
  assertThat(b.post("/admin/posts/"+post+"/delete",Map.of("revision",""+impact.revision())).statusCode()).isEqualTo(400);
  assertThat(b.post("/admin/posts/"+post+"/delete",Map.of("revision",""+impact.revision(),"confirmed","true")).statusCode()).isEqualTo(302);
  assertThat(templates.get(root,template.info().id()).references().get(0).status()).isEqualTo("DELETED");
  assertThat(pages.get(root,linked).sectionsJson()).contains("\"postIds\":["+post+"]");
  site.menu(root,null,"","PAGE",page,"",true);
  assertThatThrownBy(()->pages.delete(root,page,pages.get(root,page).revision())).isInstanceOf(BusinessException.class);
 }
 long image()throws Exception{var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);return media.upload(actor("SUPER_ADMIN"),new org.springframework.mock.web.MockMultipartFile("file","template.png","image/png",out.toByteArray()),"");}
 @Test void activeAndInactiveTemplateImageAndRichMediaAreProtected()throws Exception{
  var root=actor("SUPER_ADMIN");long image=image();var source=pages.sections("[{\"schemaVersion\":2,\"variation\":\"default\",\"type\":\"IMAGE\",\"imageId\":"+image+",\"visible\":false}]");
  var active=templates.save(root,null,null,"활성 파일 템플릿","",true,source);
  String body=json.writeValueAsString(Map.of("ops",List.of(Map.of("insert",Map.of("aicaImage",Map.of("id",image))))));
  var rich=pages.sections(json.writeValueAsString(List.of(Map.of("type","TEXT","bodyDoc",body,"visible",true,"schemaVersion",2,"variation","default"))));
  var inactive=templates.save(root,null,null,"비활성 본문 템플릿","",false,rich);
  assertThat(usages.find(root,"media",image)).extracting(UsageService.Usage::label).contains("공용 템플릿 · 활성 파일 템플릿 · 활성","공용 템플릿 · 비활성 본문 템플릿 · 비활성");
  assertThatThrownBy(()->media.delete(root,image)).isInstanceOf(BusinessException.class);
  templates.save(root,active.info().id(),active.info().revision(),"활성 파일 템플릿","",true,List.of());
  assertThatThrownBy(()->media.delete(root,image)).isInstanceOf(BusinessException.class);
  var admin=login("ADMIN");assertThat(admin.json("DELETE",API+"/media/"+image,"{}",csrf(admin)).statusCode()).isEqualTo(403);
  assertThat(read(admin.get(API+"/media/"+image+"/usage")).toString()).contains("비활성 본문 템플릿");
  templates.save(root,inactive.info().id(),inactive.info().revision(),"비활성 본문 템플릿","",false,List.of());assertThatThrownBy(()->media.delete(root,image)).isInstanceOf(BusinessException.class);
  assertThat(usages.find(root,"media",image)).anyMatch(u->u.label().contains("과거 버전"));
 }
 @Test void timestampsExposeOffsetWithoutRewritingStoredLocalTime()throws Exception{
  jdbc.update("UPDATE posts SET updated_at=TIMESTAMP '2026-09-27 17:46:00' WHERE id=?",post);
  var response=read(login("ADMIN").get(API+"/posts/"+post));
  assertThat(response.path("updatedAt").asText()).isEqualTo("2026-09-27T17:46:00+09:00");
  assertThat(jdbc.queryForObject("SELECT updated_at FROM posts WHERE id=?",java.time.LocalDateTime.class,post)).isEqualTo(java.time.LocalDateTime.of(2026,9,27,17,46));
  assertThat(read(login("SUPER_ADMIN").get(API+"/bootstrap")).path("timeZone").asText()).isEqualTo("Asia/Seoul");
 }
}

