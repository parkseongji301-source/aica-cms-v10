package egovframework.backoffice.integration;

import com.fasterxml.jackson.databind.*;
import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.post.*;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import egovframework.backoffice.mvp.classification.ClassificationModels.Selection;
import egovframework.backoffice.mvp.restaurant.RestaurantDetailsService.Details;
import egovframework.backoffice.mvp.version.SaveIntent;
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
    properties="spring.datasource.url=jdbc:h2:mem:post-trash-integration;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class PostTrashIntegrationTest {
    static final String API="/api/admin/next/posts",PASSWORD="Trash-Test-Password42";
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired AccountMapper accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired PostService posts;
    @Autowired MediaService media;
    @Autowired UsageService usages;
    @Autowired SiteService site;
    long id,file,oldDeleted;
    @BeforeEach void fixture()throws Exception {
        for(String table:List.of("site_pages","posts","media","site_menus","categories","activity_log","users"))jdbc.update("DELETE FROM "+table);
        jdbc.update("UPDATE site_settings SET setting_value='' WHERE setting_key IN ('logoId','homePageId')");
        String hash=encoder.encode(PASSWORD);
        for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,?,FALSE)",email(role),role,hash,role);
        jdbc.update("INSERT INTO categories(id,name) VALUES(11,'식당')");
        file=media.upload(actor("ADMIN"),new MockMultipartFile("file","menu.txt","text/plain","메뉴".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"");
        id=LegacyCategories.assign(jdbc,11L,posts.save(actor("ADMIN"),null,null,"공개된 식당","공개 본문",null,List.of(file),"publish",null,new Selection("RESTAURANT",List.of(),List.of()),new Details("광주 테스트로 1"),SaveIntent.MANUAL_DRAFT));
        String rich=json.writeValueAsString(Map.of("ops",List.of(Map.of("insert","보관할 본문","attributes",Map.of("bold",true)),Map.of("insert","\n"),Map.of("insert",Map.of("aicaFile",Map.of("id",file,"label","메뉴"))),Map.of("insert","\n"))));
        posts.save(actor("ADMIN"),id,liveRevision(),"수정 중인 식당 100%_","",11L,List.of(file),"save",rich,null,new Details("광주 테스트로 2"),SaveIntent.MANUAL_DRAFT);
        oldDeleted=posts.create(actor("ADMIN"),"이전에 영구삭제한 글","복원하면 안 되는 기록");
        jdbc.update("UPDATE posts SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",oldDeleted);
    }
    String email(String role){return role.toLowerCase(Locale.ROOT)+"@trash.test";}
    AccountPrincipal actor(String role){return new AccountPrincipal(accounts.findByEmail(email(role)));}
    HttpBrowser login(String role)throws Exception{var b=new HttpBrowser(port);b.login(email(role),PASSWORD,"/admin");return b;}
    JsonNode ok(HttpResponse<String> r)throws Exception{assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
    String csrf(HttpBrowser b)throws Exception{return ok(b.get("/api/admin/next/bootstrap")).path("csrf").path("token").asText();}
    long liveRevision(){return posts.get(actor("SUPER_ADMIN"),id).revision();}
    long trashRevision(){return posts.trashed(actor("SUPER_ADMIN"),id).revision();}
    long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE post_id=?",Long.class,id);}
    HttpResponse<String> change(HttpBrowser b,String method,long target,String action,Long revision,boolean confirmed,String token)throws Exception {
        var fields=new LinkedHashMap<String,Object>();fields.put("revision",revision);fields.put("confirmed",confirmed);
        return b.json(method,API+"/"+target+"/"+action,json.writeValueAsString(fields),token);
    }
    void error(HttpResponse<String> r,int status,String code)throws Exception {
        assertThat(r.statusCode()).as(r.body()).isEqualTo(status);assertThat(json.readTree(r.body()).path("code").asText()).isEqualTo(code);
    }

    @Test void trashHidesPublicAndEditableCopiesWhileProtectingAllRecoveryData()throws Exception {
        var b=login("SUPER_ADMIN");String token=csrf(b);var before=posts.get(actor("SUPER_ADMIN"),id);long history=count("post_versions");
        assertThat(new HttpBrowser(port).get("/api/public/v1/media/"+file+"/file").statusCode()).isEqualTo(200);
        ok(change(b,"POST",id,"trash",before.revision(),true,token));
        var list=ok(b.get(API+"/trash"));assertThat(list.path("total").asLong()).isEqualTo(1);
        assertThat(list.path("items").get(0).path("id").asLong()).isEqualTo(id);
        assertThat(list.path("items").get(0).path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(list.path("items").get(0).path("deletedAt").asText()).isNotBlank();
        for(String suffix:List.of("","/publication","/preview"))error(b.get(API+"/"+id+suffix),404,"NOT_FOUND");
        assertThat(b.get(API+"/"+id+"/versions").statusCode()).isEqualTo(404);
        assertThat(posts.list(actor("SUPER_ADMIN"),0).items()).noneMatch(p->p.id()==id);
        assertThat(b.get("/api/public/v1/posts/"+id).statusCode()).isEqualTo(404);
        assertThat(b.get("/api/public/v1/media/"+file+"/file").statusCode()).isEqualTo(404);
        var saved=posts.trashed(actor("SUPER_ADMIN"),id);
        assertThat(saved.content()).isEqualTo(before.content());assertThat(saved.richContent()).isEqualTo(before.richContent());
        assertThat(saved.revision()).isEqualTo(before.revision()+1);assertThat(count("post_versions")).isEqualTo(history);
        for(String table:List.of("post_media","post_publications","post_publication_media","post_restaurant_details","post_publication_restaurant_details"))assertThat(count(table)).as(table).isEqualTo(1);
        assertThatThrownBy(()->media.delete(actor("SUPER_ADMIN"),file)).hasMessageContaining("사용 중");
        assertThatThrownBy(()->site.deleteCategory(actor("SUPER_ADMIN"),11)).isInstanceOf(RuntimeException.class);
        assertThat(usages.find(actor("SUPER_ADMIN"),"media",file)).anyMatch(u->u.label().contains("휴지통")&&u.label().contains("과거 버전"));
        assertThat(usages.find(actor("ADMIN"),"media",file)).noneMatch(u->"/admin/trash".equals(u.href()));
    }

    @Test void restorationKeepsLatestDraftAndHistoryAndRequiresExplicitRepublication()throws Exception {
        var b=login("SUPER_ADMIN");String token=csrf(b);var before=ok(b.get(API+"/"+id));long history=count("post_versions");
        ok(change(b,"POST",id,"trash",liveRevision(),true,token));long revision=trashRevision();
        var restored=ok(change(b,"POST",id,"restore",revision,true,token));
        for(String field:List.of("id","title","content","richContent","categoryId","mediaIds","classification","restaurant","authorId","createdAt"))assertThat(restored.path(field)).as(field).isEqualTo(before.path(field));
        assertThat(restored.path("status").asText()).isEqualTo("DRAFT");assertThat(restored.path("publishedRevision").isNull()).isTrue();
        assertThat(restored.path("pending").asBoolean()).isFalse();assertThat(restored.path("revision").asLong()).isEqualTo(revision+1);
        assertThat(count("post_versions")).isEqualTo(history);assertThat(count("post_trash")).isZero();assertThat(count("post_publications")).isZero();
        assertThat(b.get("/api/public/v1/posts/"+id).statusCode()).isEqualTo(404);
        assertThat(ok(b.get(API+"/trash")).path("total").asLong()).isZero();
        var published=ok(b.json("POST",API+"/"+id+"/publish",restored.toString(),token));
        assertThat(published.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(b.get("/api/public/v1/posts/"+id).body()).contains("보관할 본문","광주 테스트로 2");
    }

    @Test void purgeRequiresTrashAndRemovesOwnedDataButKeepsMediaAndManualReferences()throws Exception {
        var b=login("SUPER_ADMIN");String token=csrf(b);
        String references="[{\"type\":\"POSTS\",\"sourceMode\":\"manual\",\"manual\":{\"postIds\":["+id+"]},\"visible\":true}]";
        jdbc.update("INSERT INTO site_pages(title,slug,sections_json,author_id) VALUES('참조 페이지','trash-reference',?,?)",references,actor("SUPER_ADMIN").getId());
        error(change(b,"DELETE",id,"trash",liveRevision(),true,token),404,"NOT_FOUND");
        ok(change(b,"POST",id,"trash",liveRevision(),true,token));
        ok(change(b,"DELETE",id,"trash",trashRevision(),true,token));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE id=?",Long.class,id)).isZero();
        for(String table:List.of("post_trash","post_versions","post_media","post_publications","post_publication_media","post_cohorts","post_topics","post_restaurant_details","post_publication_restaurant_details"))assertThat(count(table)).as(table).isZero();
        assertThat(media.required(file)).isNotNull();assertThat(usages.find(actor("SUPER_ADMIN"),"media",file)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT sections_json FROM site_pages WHERE slug='trash-reference'",String.class)).isEqualTo(references);
        error(change(b,"POST",id,"restore",0L,true,token),404,"NOT_FOUND");
        media.delete(actor("SUPER_ADMIN"),file);
    }

    @Test void legacyDeletionCannotBeRecoveredAndLegacyFormNowMovesToTrash()throws Exception {
        var b=login("SUPER_ADMIN");String token=csrf(b);
        assertThat(ok(b.get(API+"/trash")).path("total").asLong()).isZero();
        for(String method:List.of("POST","DELETE"))error(change(b,method,oldDeleted,method.equals("POST")?"restore":"trash",0L,true,token),404,"NOT_FOUND");
        assertThat(b.get("/admin/legacy/posts/"+id+"/delete-confirm").body()).contains("휴지통 이동 확인","개 버전 이력을 유지합니다.").doesNotContain("함께 삭제할 버전 이력");
        long history=count("post_versions");var moved=b.post("/admin/posts/"+id+"/delete",Map.of("revision",String.valueOf(liveRevision()),"confirmed","true"));
        assertThat(moved.statusCode()).isEqualTo(302);assertThat(count("post_trash")).isEqualTo(1);assertThat(count("post_versions")).isEqualTo(history);
    }

    @Test void rolesSessionCsrfConfirmationAndRevisionAreEnforcedWithoutSideEffects()throws Exception {
        var root=login("SUPER_ADMIN");String token=csrf(root);long revision=liveRevision();
        for(String role:List.of("ADMIN","SUPPORTER")){
            var b=login(role);String other=csrf(b);error(b.get(API+"/trash"),403,"FORBIDDEN_OR_CSRF");assertThat(b.get("/admin/trash").statusCode()).isEqualTo(200);
            for(String action:List.of("trash","restore"))error(change(b,"POST",id,action,revision,true,other),403,"FORBIDDEN_OR_CSRF");
            error(change(b,"DELETE",id,"trash",revision,true,other),403,"FORBIDDEN_OR_CSRF");
        }
        error(change(root,"POST",id,"trash",revision,true,null),403,"FORBIDDEN_OR_CSRF");
        error(change(root,"POST",id,"trash",null,true,token),400,"VALIDATION_ERROR");
        error(change(root,"POST",id,"trash",revision,false,token),400,"VALIDATION_ERROR");
        error(change(root,"POST",id,"trash",revision-1,true,token),409,"REVISION_CONFLICT");
        assertThat(liveRevision()).isEqualTo(revision);assertThat(count("post_trash")).isZero();
        ok(change(root,"POST",id,"trash",revision,true,token));
        for(String method:List.of("POST","DELETE")){
            String action=method.equals("POST")?"restore":"trash";
            error(change(root,method,id,action,trashRevision(),false,token),400,"VALIDATION_ERROR");
            error(change(root,method,id,action,null,true,token),400,"VALIDATION_ERROR");
            error(change(root,method,id,action,revision,true,token),409,"REVISION_CONFLICT");
        }
        assertThat(count("post_trash")).isEqualTo(1);
        jdbc.update("UPDATE users SET auth_version=auth_version+1 WHERE role='SUPER_ADMIN'");
        error(change(root,"POST",id,"restore",trashRevision(),true,token),401,"SESSION_EXPIRED");
        error(new HttpBrowser(port).get(API+"/trash"),401,"AUTH_REQUIRED");
    }

    @Test void searchEscapesWildcardsAndPaginationExcludesLiveAndOldDeletedPosts()throws Exception {
        var b=login("SUPER_ADMIN");var root=actor("SUPER_ADMIN");posts.delete(root,id,liveRevision());
        for(int n=0;n<11;n++){long extra=posts.create(root,"별도 보관 "+n,"본문");posts.delete(root,extra,0L);}
        assertThat(ok(b.get(API+"/trash")).path("items").size()).isEqualTo(10);
        var second=ok(b.get(API+"/trash?page=1"));assertThat(second.path("items").size()).isEqualTo(2);assertThat(second.path("total").asLong()).isEqualTo(12);
        assertThat(ok(b.get(API+"/trash?q=100%25_")).path("total").asLong()).isEqualTo(1);
        error(b.get(API+"/trash?page=-1"),400,"VALIDATION_ERROR");error(b.get(API+"/trash?q="+"a".repeat(101)),400,"VALIDATION_ERROR");
    }

    @Test void concurrentRestorationAndPurgeHaveOnlyOneWinner()throws Exception {
        var b=login("SUPER_ADMIN");String token=csrf(b);posts.delete(actor("SUPER_ADMIN"),id,liveRevision());long revision=trashRevision();
        var gate=new java.util.concurrent.CountDownLatch(1);
        var restore=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{gate.await();return change(b,"POST",id,"restore",revision,true,token);}catch(Exception e){throw new RuntimeException(e);}});
        var purge=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{gate.await();return change(b,"DELETE",id,"trash",revision,true,token);}catch(Exception e){throw new RuntimeException(e);}});
        gate.countDown();var r=restore.get(30,java.util.concurrent.TimeUnit.SECONDS);var p=purge.get(30,java.util.concurrent.TimeUnit.SECONDS);
        assertThat(List.of(r.statusCode(),p.statusCode())).containsExactlyInAnyOrder(200,404);assertThat(count("post_trash")).isZero();
        if(r.statusCode()==200)assertThat(posts.get(actor("SUPER_ADMIN"),id).status()).isEqualTo("DRAFT");
        else assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE id=?",Long.class,id)).isZero();
    }
}
