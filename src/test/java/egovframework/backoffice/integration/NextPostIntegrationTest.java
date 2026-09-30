package egovframework.backoffice.integration;

import com.fasterxml.jackson.databind.*;
import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.post.PostService;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import java.net.http.HttpResponse;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:next-post-integration;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class NextPostIntegrationTest {
    private static final String API="/api/admin/next/posts";
    private static final String PASSWORD="Post-Editor-Test-Password42";
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountMapper accounts;
    @Autowired PostService posts;
    @Autowired MediaService media;
    long publishedId,ownId,privateId,imageId,fileId;

    @BeforeEach void fixture() throws Exception {
        for(String table:List.of("post_publication_media","post_media","post_publications","page_media","page_publications","site_pages","posts","media","site_menus","categories","site_links","activity_log","users"))jdbc.update("DELETE FROM "+table);
        jdbc.update("UPDATE site_settings SET setting_value='' WHERE setting_key IN ('logoId','homePageId')");
        String hash=encoder.encode(PASSWORD);
        for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,?,FALSE)",email(role),role,hash,role);
        jdbc.update("INSERT INTO categories(id,name,sort_order) VALUES(11,'공지사항',0),(12,'교육 소식',1)");
        var actor=principal("ADMIN");
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        imageId=media.upload(actor,new MockMultipartFile("file","existing.png","image/png",bytes.toByteArray()),"기존 이미지");
        fileId=media.upload(actor,new MockMultipartFile("file","existing.txt","text/plain","원본 첨부".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"");
        publishedId=posts.save(actor,null,null,"기존 발행 콘텐츠","기존 일반 본문",11L,List.of(imageId,fileId),"publish",null);
        ownId=posts.save(principal("SUPPORTER"),null,null,"본인 초안","초안 본문",12L,List.of(),"save",null);
        privateId=posts.save(actor,null,null,"비공개 콘텐츠","비공개 본문",null,List.of(),"publish",null);
        posts.unpublish(actor,privateId,posts.get(actor,privateId).revision());
        jdbc.update("INSERT INTO site_menus(label,kind,target_id) VALUES('교육 소식','CATEGORY',12)");
    }
    private String email(String role){return role.toLowerCase(Locale.ROOT)+"@post.test";}
    private AccountPrincipal principal(String role){return new AccountPrincipal(accounts.findByEmail(email(role)));}
    private HttpBrowser login(String role)throws Exception {var b=new HttpBrowser(port);b.login(email(role),PASSWORD,"/admin");return b;}
    private JsonNode ok(HttpResponse<String> response)throws Exception {assertThat(response.statusCode()).as(response.body()).isEqualTo(200);return json.readTree(response.body());}
    private JsonNode read(HttpBrowser b,long id)throws Exception{return ok(b.get(API+"/"+id));}
    private String token(HttpBrowser b)throws Exception{return ok(b.get("/api/admin/next/bootstrap")).path("csrf").path("token").asText();}
    private Map<String,Object> input(JsonNode post) {
        var result=new LinkedHashMap<String,Object>();
        for(String key:List.of("revision","title","content","richContent","categoryId","mediaIds"))result.put(key,json.convertValue(post.path(key),Object.class));
        return result;
    }
    private HttpResponse<String> put(HttpBrowser b,long id,Map<String,Object> body,String csrf)throws Exception{return b.json("PUT",API+"/"+id,json.writeValueAsString(body),csrf);}
    private HttpResponse<String> publish(HttpBrowser b,long id,Map<String,Object> body,String csrf)throws Exception{return b.json("POST",API+"/"+id+"/publish",json.writeValueAsString(body),csrf);}
    private void error(HttpResponse<String> response,int status,String code)throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("content-type").orElse("")).contains("application/json");
        assertThat(json.readTree(response.body()).path("code").asText()).isEqualTo(code);
    }
    private String richDocument()throws Exception {
        return json.writeValueAsString(Map.of("ops",List.of(
            Map.of("insert","수정된 서식 본문","attributes",Map.of("bold",true,"font","serif","color","blue")),
            Map.of("insert","\n","attributes",Map.of("header",2)),
            Map.of("insert",Map.of("aicaImage",Map.of("id",imageId,"width","50","align","right","alt","대체 문구","caption","사진 설명"))),
            Map.of("insert","\n"),Map.of("insert",Map.of("aicaFile",Map.of("id",fileId,"label","안내 첨부"))),
            Map.of("insert","\n"),Map.of("insert",Map.of("aicaTable",Map.of("rows",List.of(List.of("과목","시간"),List.of("AI","3"))))),
            Map.of("insert",Map.of("divider",true)),Map.of("insert","\n"))));
    }

    @Test void existingIdsAndLegacyAttachmentsAreReadWithoutWritingOrCreating()throws Exception {
        var b=login("ADMIN");long count=jdbc.queryForObject("SELECT COUNT(*) FROM posts",Long.class);
        long activities=jdbc.queryForObject("SELECT COUNT(*) FROM activity_log",Long.class);
        var post=read(b,publishedId);
        assertThat(post.path("id").asLong()).isEqualTo(publishedId);assertThat(post.path("richContent").isNull()).isTrue();
        assertThat(post.path("mediaIds").toString()).isEqualTo("["+imageId+","+fileId+"]");
        assertThat(post.path("attachments").get(0).path("alt").asText()).isEqualTo("기존 이미지");
        for(String mode:List.of("manage","structure")) {
            var shell=b.get("/admin/posts/"+publishedId+"/edit?view="+mode);
            assertThat(shell.statusCode()).isEqualTo(200);assertThat(shell.body()).contains("/next-app/assets/");
            assertThat(read(b,publishedId)).isEqualTo(post);
        }
        assertThat(b.get("/admin/legacy/posts/"+publishedId+"/edit").body()).contains("기존 발행 콘텐츠","existing.txt");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts",Long.class)).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log",Long.class)).isEqualTo(activities);
    }

    @Test void legacyDraftSavePreservesAttachmentsAndOriginalPublication()throws Exception {
        var b=login("ADMIN");var before=read(b,publishedId);var fields=input(before);
        fields.put("title","React 초안 제목");fields.put("content","React 일반 본문");fields.put("categoryId",12);
        // Extra client fields cannot publish or redirect a save to another target/author.
        fields.put("action","publish");fields.put("id",ownId);fields.put("authorId",principal("SUPPORTER").getId());
        var after=ok(put(b,publishedId,fields,token(b)));
        assertThat(after.path("id").asLong()).isEqualTo(publishedId);assertThat(after.path("pending").asBoolean()).isTrue();
        assertThat(after.path("status").asText()).isEqualTo("PUBLISHED");assertThat(after.path("richContent").isNull()).isTrue();
        assertThat(after.path("revision").asLong()).isEqualTo(before.path("revision").asLong()+1);
        assertThat(after.path("publishedRevision")).isEqualTo(before.path("publishedRevision"));
        assertThat(after.path("authorId")).isEqualTo(before.path("authorId"));assertThat(after.path("mediaIds")).isEqualTo(before.path("mediaIds"));
        assertThat(read(b,publishedId)).isEqualTo(after);
        assertThat(b.get("/admin/legacy/posts/"+publishedId+"/edit").body()).contains("React 초안 제목","React 일반 본문");
        assertThat(b.get("/admin/legacy/posts/"+publishedId+"/publication").body()).contains("기존 발행 콘텐츠","기존 일반 본문").doesNotContain("React 초안 제목");
        assertThat(jdbc.queryForObject("SELECT category_id FROM post_publications WHERE post_id=?",Long.class,publishedId)).isEqualTo(11L);
        assertThat(read(b,ownId).path("title").asText()).isEqualTo("본인 초안");
    }

    @Test void publicationViewShowsOnlyTheCurrentPublicCopyWithTheSameAccessAsTheLegacyPage()throws Exception {
        var b=login("ADMIN");var fields=input(read(b,publishedId));
        fields.put("title","React 초안 제목");fields.put("content","React 일반 본문");ok(put(b,publishedId,fields,token(b)));
        int publications=jdbc.queryForObject("SELECT COUNT(*) FROM post_publications",Integer.class);
        var view=ok(b.get(API+"/"+publishedId+"/publication/view"));
        assertThat(view.path("title").asText()).isEqualTo("기존 발행 콘텐츠");
        assertThat(view.path("bodyHtml").asText()).contains("기존 일반 본문").doesNotContain("React");
        assertThat(view.path("attachments").size()).isEqualTo(2);
        assertThat(b.get("/admin/legacy/posts/"+publishedId+"/publication").body()).contains("기존 발행 콘텐츠","기존 일반 본문").doesNotContain("React 초안 제목");
        error(b.get(API+"/"+ownId+"/publication/view"),404,"NOT_FOUND");
        error(b.get(API+"/"+privateId+"/publication/view"),404,"NOT_FOUND");
        error(login("SUPPORTER").get(API+"/"+publishedId+"/publication/view"),403,"FORBIDDEN");
        error(new HttpBrowser(port).get(API+"/"+publishedId+"/publication/view"),401,"AUTH_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_publications",Integer.class)).isEqualTo(publications);
        assertThat(read(b,publishedId).path("title").asText()).isEqualTo("React 초안 제목");
    }
    @Test void reactUnpublishWithdrawsOnlyThePublicCopyWithPublishPermission()throws Exception {
        var admin=login("ADMIN");String csrf=token(admin);var before=read(admin,publishedId);long revision=before.path("revision").asLong();
        var supporter=login("SUPPORTER");
        error(supporter.json("POST",API+"/"+publishedId+"/unpublish",json.writeValueAsString(Map.of("revision",revision)),token(supporter)),403,"FORBIDDEN_OR_CSRF");
        error(admin.json("POST",API+"/"+publishedId+"/unpublish",json.writeValueAsString(Map.of("revision",revision)),null),403,"FORBIDDEN_OR_CSRF");
        error(admin.json("POST",API+"/"+publishedId+"/unpublish",json.writeValueAsString(Map.of("revision",revision-1)),csrf),409,"REVISION_CONFLICT");
        error(admin.json("POST",API+"/"+publishedId+"/unpublish","{}",csrf),400,"VALIDATION_ERROR");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_publications WHERE post_id=?",Integer.class,publishedId)).isEqualTo(1);
        var after=ok(admin.json("POST",API+"/"+publishedId+"/unpublish",json.writeValueAsString(Map.of("revision",revision)),csrf));
        assertThat(after.path("status").asText()).isEqualTo("PRIVATE");
        assertThat(after.path("title")).isEqualTo(before.path("title"));assertThat(after.path("content")).isEqualTo(before.path("content"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_publications WHERE post_id=?",Integer.class,publishedId)).isEqualTo(1);
        error(admin.get(API+"/"+publishedId+"/publication/view"),404,"NOT_FOUND");
        assertThat(admin.get("/admin/legacy/posts/"+publishedId+"/publication").statusCode()).isEqualTo(404);
    }
    @Test void richDraftKeepsImagesFilesFormattingAndExistingPublicationMedia()throws Exception {
        var b=login("ADMIN");var fields=input(read(b,publishedId));fields.put("richContent",richDocument());fields.put("mediaIds",List.of());
        var after=ok(put(b,publishedId,fields,token(b)));
        assertThat(json.readTree(after.path("richContent").asText())).isEqualTo(json.readTree(richDocument()));
        assertThat(after.path("content").asText()).contains("수정된 서식 본문","사진 설명","안내 첨부","AI");
        assertThat(after.path("mediaIds").toString()).isEqualTo("["+imageId+","+fileId+"]");
        var preview=ok(b.get(API+"/"+publishedId+"/preview"));
        assertThat(preview.path("bodyHtml").asText()).contains("<strong>","rt-font-serif","rt-width-50","rt-align-right","안내 첨부","<table>","<hr>");
        assertThat(b.get("/admin/legacy/posts/"+publishedId+"/edit").body()).contains("aicaImage","aicaFile","aicaTable");
        fields=input(after);fields.put("richContent","{\"ops\":[{\"insert\":\"첨부 없는 초안\\n\"}]}");
        var detached=ok(put(b,publishedId,fields,token(b)));assertThat(detached.path("mediaIds").size()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_publication_media WHERE post_id=?",Integer.class,publishedId)).isEqualTo(2);
        assertThat(b.get("/admin/legacy/posts/"+publishedId+"/publication").body()).contains("existing.txt","/admin/media/"+imageId+"/file");
    }

    @Test void previewSharesLegacyRenderingAndNeverWritesDraftOrPublication()throws Exception {
        var b=login("ADMIN");var before=read(b,publishedId);String doc=richDocument();
        var body=Map.of("title","저장 전 미리보기","content","","richContent",doc);
        var preview=ok(b.json("POST",API+"/"+publishedId+"/preview",json.writeValueAsString(body),token(b)));
        var legacy=b.post("/admin/posts/preview",Map.of("id",String.valueOf(publishedId),"title","저장 전 미리보기","content","","richContent",doc));
        assertThat(legacy.statusCode()).isEqualTo(200);assertThat(legacy.body()).contains(preview.path("bodyHtml").asText());
        assertThat(read(b,publishedId)).isEqualTo(before);
        var plain=ok(b.json("POST",API+"/"+publishedId+"/preview",json.writeValueAsString(Map.of("title","plain","content","<script>alert(1)</script>","mediaIds",List.of(imageId,fileId))),token(b)));
        assertThat(plain.path("bodyHtml").asText()).contains("&lt;script&gt;").doesNotContain("<script>");assertThat(plain.path("attachments").size()).isEqualTo(2);
    }

    @Test void missingVersionStaleRevisionAndValidationCannotOverwriteContent()throws Exception {
        var b=login("ADMIN");String csrf=token(b);var before=read(b,publishedId);var fields=input(before);
        fields.remove("revision");error(put(b,publishedId,fields,csrf),400,"VALIDATION_ERROR");
        fields=input(before);fields.put("categoryId",999999);error(put(b,publishedId,fields,csrf),400,"VALIDATION_ERROR");
        fields=input(before);fields.put("title","");error(put(b,publishedId,fields,csrf),400,"VALIDATION_ERROR");
        fields=input(before);fields.put("richContent","{\"ops\":[{\"insert\":\"bad\",\"attributes\":{\"link\":\"javascript:alert(1)\"}}]}");error(put(b,publishedId,fields,csrf),400,"VALIDATION_ERROR");
        assertThat(read(b,publishedId)).isEqualTo(before);
        fields=input(before);fields.put("title","먼저 저장한 수정");var saved=ok(put(b,publishedId,fields,csrf));
        fields.put("title","오래된 수정");error(put(b,publishedId,fields,csrf),409,"REVISION_CONFLICT");assertThat(read(b,publishedId)).isEqualTo(saved);
        error(put(b,999999,input(before),csrf),404,"NOT_FOUND");error(b.get(API+"/999999"),404,"NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts",Integer.class)).isEqualTo(3);
    }

    @Test void ownerPermissionsCsrfAndSessionInvalidationApplyToBothPaths()throws Exception {
        var admin=login("ADMIN");var supporter=login("SUPPORTER");var own=read(supporter,ownId);String csrf=token(supporter);
        for(String mode:List.of("manage","structure")) {
            assertThat(supporter.get("/admin/posts/"+ownId+"/edit?view="+mode).statusCode()).isEqualTo(200);
            error(supporter.get(API+"/"+publishedId+"?view="+mode),403,"FORBIDDEN");
        }
        var fields=input(own);fields.put("title","본인 React 초안");ok(put(supporter,ownId,fields,csrf));
        error(put(supporter,publishedId,input(read(admin,publishedId)),csrf),403,"FORBIDDEN");
        error(supporter.get(API+"/"+publishedId+"/preview"),403,"FORBIDDEN");
        fields=input(read(supporter,ownId));fields.put("mediaIds",List.of(imageId));error(put(supporter,ownId,fields,csrf),403,"FORBIDDEN");
        error(supporter.json("POST",API+"/"+ownId+"/preview",json.writeValueAsString(Map.of("title","mine","content","","mediaIds",List.of(fileId))),csrf),403,"FORBIDDEN");
        error(put(admin,publishedId,input(read(admin,publishedId)),null),403,"FORBIDDEN_OR_CSRF");
        error(new HttpBrowser(port).get(API+"/"+ownId),401,"AUTH_REQUIRED");
        jdbc.update("UPDATE users SET auth_version=auth_version+1 WHERE role='SUPPORTER'");error(supporter.get(API+"/"+ownId),401,"SESSION_EXPIRED");
    }

    @Test void newDraftCanBePublishedFromSubmittedEditorContentsWithoutAnotherSave()throws Exception {
        var b=login("ADMIN");String csrf=token(b);
        var created=b.json("POST",API,json.writeValueAsString(Map.of("title","새 글","content","","mediaIds",List.of(),"saveIntent","AUTOSAVE")),csrf);
        assertThat(created.statusCode()).isEqualTo(201);
        var draft=json.readTree(created.body());long id=draft.path("id").asLong();
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
        var fields=input(draft);fields.put("title","게시할 식당");fields.put("content","방금 입력한 소개");
        fields.put("classification",Map.of("typeCode","RESTAURANT","cohortIds",List.of(),"topicIds",List.of()));
        fields.put("restaurant",Map.of("address","광주광역시 테스트로 123"));
        fields.put("categoryId",12);fields.put("mediaIds",List.of(imageId,fileId));
        var result=ok(publish(b,id,fields,csrf));
        assertThat(result.path("id").asLong()).isEqualTo(id);
        assertThat(result.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(result.path("revision").asLong()).isEqualTo(draft.path("revision").asLong()+1);
        assertThat(result.path("publishedRevision")).isEqualTo(result.path("revision"));
        assertThat(result.path("pending").asBoolean()).isFalse();
        var publication=ok(b.get(API+"/"+id+"/publication"));
        assertThat(publication.path("post").path("title").asText()).isEqualTo("게시할 식당");
        assertThat(publication.path("post").path("content").asText()).isEqualTo("방금 입력한 소개");
        assertThat(publication.path("classification").path("typeCode").asText()).isEqualTo("RESTAURANT");
        assertThat(publication.path("restaurant").path("address").asText()).isEqualTo("광주광역시 테스트로 123");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_publication_media WHERE post_id=?",Integer.class,id)).isEqualTo(2);
        assertThat(b.get("/api/public/v1/posts/"+id).body()).contains("게시할 식당","방금 입력한 소개","광주광역시 테스트로 123");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_versions WHERE post_id=? AND reason='PUBLISH'",Integer.class,id)).isEqualTo(1);
    }

    @Test void autosavePreservesPublicationUntilExplicitRepublishAndPrivatePostCanPublish()throws Exception {
        var b=login("SUPER_ADMIN");String csrf=token(b);var before=read(b,publishedId);
        var fields=input(before);fields.put("title","수정 중 초안");fields.put("richContent",richDocument());fields.put("saveIntent","AUTOSAVE");
        var draft=ok(put(b,publishedId,fields,csrf));
        assertThat(draft.path("pending").asBoolean()).isTrue();
        assertThat(b.get("/api/public/v1/posts/"+publishedId).body()).contains("기존 발행 콘텐츠").doesNotContain("수정 중 초안");
        fields=input(draft);fields.put("title","최종 게시 제목");
        // A forged author or ID cannot change the publication target or ownership.
        fields.put("id",ownId);fields.put("authorId",principal("SUPER_ADMIN").getId());
        var published=ok(publish(b,publishedId,fields,csrf));
        assertThat(published.path("pending").asBoolean()).isFalse();
        assertThat(published.path("authorId")).isEqualTo(before.path("authorId"));
        assertThat(json.readTree(published.path("richContent").asText())).isEqualTo(json.readTree(richDocument()));
        assertThat(b.get("/admin/legacy/posts/"+publishedId+"/publication").body()).contains("최종 게시 제목","<strong>","<table>","안내 첨부");
        assertThat(read(b,ownId).path("title").asText()).isEqualTo("본인 초안");
        var privatePost=ok(publish(b,privateId,input(read(b,privateId)),csrf));
        assertThat(privatePost.path("status").asText()).isEqualTo("PUBLISHED");
    }

    @Test void rejectedPublicationDoesNotChangeDraftPublicationOrHistory()throws Exception {
        var b=login("ADMIN");String csrf=token(b);var before=read(b,publishedId);
        var publication=ok(b.get(API+"/"+publishedId+"/publication"));
        var history=ok(b.get(API+"/"+publishedId+"/versions"));
        var fields=input(before);fields.remove("revision");error(publish(b,publishedId,fields,csrf),400,"VALIDATION_ERROR");
        fields=input(before);fields.put("title","");error(publish(b,publishedId,fields,csrf),400,"VALIDATION_ERROR");
        fields=input(before);fields.put("content","");fields.put("mediaIds",List.of());error(publish(b,publishedId,fields,csrf),400,"VALIDATION_ERROR");
        fields=input(before);fields.put("categoryId",999999);error(publish(b,publishedId,fields,csrf),400,"VALIDATION_ERROR");
        fields=input(before);fields.put("classification",Map.of("typeCode","UNKNOWN","cohortIds",List.of(),"topicIds",List.of()));error(publish(b,publishedId,fields,csrf),400,"VALIDATION_ERROR");
        error(publish(b,999999,input(before),csrf),404,"NOT_FOUND");
        assertThat(read(b,publishedId)).isEqualTo(before);
        assertThat(ok(b.get(API+"/"+publishedId+"/publication"))).isEqualTo(publication);
        assertThat(ok(b.get(API+"/"+publishedId+"/versions"))).isEqualTo(history);
        var saved=ok(put(b,publishedId,input(before),csrf));
        error(publish(b,publishedId,input(before),csrf),409,"REVISION_CONFLICT");
        assertThat(read(b,publishedId)).isEqualTo(saved);
        assertThat(ok(b.get(API+"/"+publishedId+"/publication"))).isEqualTo(publication);
    }

    @Test void publicationEnforcesRoleCsrfAndCurrentSession()throws Exception {
        var admin=login("ADMIN");var supporter=login("SUPPORTER");var own=read(supporter,ownId);
        error(publish(supporter,ownId,input(own),token(supporter)),403,"FORBIDDEN_OR_CSRF");
        error(publish(supporter,publishedId,input(read(admin,publishedId)),token(supporter)),403,"FORBIDDEN_OR_CSRF");
        error(publish(admin,ownId,input(own),null),403,"FORBIDDEN_OR_CSRF");
        var anonymous=new HttpBrowser(port);
        String anonymousCsrf=HttpBrowser.extract(anonymous.get("/login").body(),"name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
        error(publish(anonymous,ownId,input(own),anonymousCsrf),401,"AUTH_REQUIRED");
        String csrf=token(admin);jdbc.update("UPDATE users SET auth_version=auth_version+1 WHERE role='ADMIN'");
        error(publish(admin,ownId,input(own),csrf),401,"SESSION_EXPIRED");
        assertThat(read(supporter,ownId)).isEqualTo(own);
    }

    @Test void racingAutosaveAndPublicationCannotSilentlyOverwriteEachOther()throws Exception {
        var b=login("ADMIN");String csrf=token(b);var before=read(b,publishedId);
        var draft=input(before);draft.put("title","동시 초안");draft.put("saveIntent","AUTOSAVE");
        var publication=input(before);publication.put("title","동시 게시");
        var gate=new java.util.concurrent.CountDownLatch(1);
        var saveFuture=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{gate.await();return put(b,publishedId,draft,csrf);}catch(Exception e){throw new RuntimeException(e);}});
        var publishFuture=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{gate.await();return publish(b,publishedId,publication,csrf);}catch(Exception e){throw new RuntimeException(e);}});
        gate.countDown();
        var saveResult=saveFuture.get(30,java.util.concurrent.TimeUnit.SECONDS);var publishResult=publishFuture.get(30,java.util.concurrent.TimeUnit.SECONDS);
        assertThat(List.of(saveResult.statusCode(),publishResult.statusCode())).containsExactlyInAnyOrder(200,409);
        var after=read(b,publishedId);var publicPost=ok(b.get(API+"/"+publishedId+"/publication")).path("post");
        assertThat(after.path("revision").asLong()).isEqualTo(before.path("revision").asLong()+1);
        assertThat(after.path("title").asText()).isEqualTo(publishResult.statusCode()==200?"동시 게시":"동시 초안");
        assertThat(publicPost.path("title").asText()).isEqualTo(publishResult.statusCode()==200?"동시 게시":"기존 발행 콘텐츠");
    }

    @Test void draftAndPrivateStatusStayUnchangedWithAdditiveClassificationSchema()throws Exception {
        var b=login("ADMIN");String csrf=token(b);
        for(long id:List.of(ownId,privateId)) {
            var before=read(b,id);var fields=input(before);fields.put("title","수정 "+id);fields.put("categoryId",null);
            var saved=ok(put(b,id,fields,csrf));assertThat(saved.path("status")).isEqualTo(before.path("status"));
            assertThat(saved.path("categoryId").isNull()).isTrue();assertThat(saved.path("publishedRevision")).isEqualTo(before.path("publishedRevision"));
        }
        assertThat(jdbc.queryForList("SELECT \"version\" FROM \"flyway_schema_history\" WHERE \"success\"=TRUE AND \"version\" IS NOT NULL",String.class)).containsExactly("1","2","3","4","5","6","7","8","9","10","11","12","13");
        assertThat(b.get("/admin/legacy/posts/new").statusCode()).isEqualTo(200);
    }
}
