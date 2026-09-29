package egovframework.backoffice.integration;

import com.fasterxml.jackson.databind.*;
import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.cms.MediaService;
import egovframework.backoffice.mvp.security.AccountPrincipal;
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

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.datasource.url=jdbc:h2:mem:restaurant-workflow;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class RestaurantWorkflowIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountMapper accounts;
    @Autowired MediaService media;
    static final String API="/api/admin/next/posts",PASSWORD="Restaurant-Test-Password42";
    @BeforeEach void fixture() {
        for(String table:List.of("post_publication_media","post_media","post_publications","page_media","page_publications","site_pages","posts","media","site_menus","categories","activity_log","users"))jdbc.update("DELETE FROM "+table);
        String hash=encoder.encode(PASSWORD);
        for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,?,FALSE)",role.toLowerCase(Locale.ROOT)+"@restaurant.test",role,hash,role);
        jdbc.update("INSERT INTO categories(id,name) VALUES(11,'기존 분류')");
        jdbc.update("INSERT INTO site_menus(label,kind,target_id) VALUES('기존 메뉴','CATEGORY',11)");
    }
    HttpBrowser login(String role)throws Exception {var b=new HttpBrowser(port);b.login(role.toLowerCase(Locale.ROOT)+"@restaurant.test",PASSWORD,"/admin");return b;}
    JsonNode get(HttpBrowser b,String path)throws Exception {var r=b.get(path);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
    String token(HttpBrowser b)throws Exception{return get(b,"/api/admin/next/bootstrap").path("csrf").path("token").asText();}
    Map<String,Object> body(String type,String address) {
        var body=new LinkedHashMap<String,Object>();body.put("title","검증 식당");body.put("content","식당 소개");body.put("categoryId",11);body.put("mediaIds",List.of());
        body.put("classification",Map.of("typeCode",type,"cohortIds",List.of(),"topicIds",List.of()));if(address!=null)body.put("restaurant",Map.of("address",address));return body;
    }
    Map<String,Object> input(JsonNode p) {var value=new LinkedHashMap<String,Object>();for(String key:List.of("revision","title","content","richContent","categoryId","mediaIds","classification"))value.put(key,json.convertValue(p.path(key),Object.class));return value;}
    JsonNode create(HttpBrowser b,String address)throws Exception {var r=b.json("POST",API,json.writeValueAsString(body("RESTAURANT",address)),token(b));assertThat(r.statusCode()).as(r.body()).isEqualTo(201);return json.readTree(r.body());}
    JsonNode save(HttpBrowser b,JsonNode post,Map<String,Object> changes)throws Exception {
        var value=input(post);value.putAll(changes);var r=b.json("PUT",API+"/"+post.path("id").asLong(),json.writeValueAsString(value),token(b));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());
    }
    java.net.http.HttpResponse<String> legacy(HttpBrowser b,JsonNode post,String action)throws Exception {
        var value=new LinkedHashMap<String,String>();for(String key:List.of("revision","title","content","categoryId"))value.put(key,post.path(key).asText());if(!post.path("richContent").isNull())value.put("richContent",post.path("richContent").asText());value.put("action",action);
        return b.post("/admin/posts/"+post.path("id").asLong()+"/edit",value);
    }
    void publish(HttpBrowser b,JsonNode post)throws Exception {HttpBrowser.redirect(legacy(b,post,"publish"),"/admin/posts/"+post.path("id").asLong());}
    @Test void addressOnlySaveRevisesCommonPostAndPreviewAndMediaShareTheSameId()throws Exception {
        var b=login("ADMIN");var p=create(b,"  검증 주소 A  ");long id=p.path("id").asLong();assertThat(p.path("restaurant").path("address").asText()).isEqualTo("검증 주소 A");
        var updated=save(b,p,Map.of("restaurant",Map.of("address","검증 주소 B")));
        assertThat(updated.path("revision").asLong()).isEqualTo(p.path("revision").asLong()+1);
        assertThat(updated.path("content")).isEqualTo(p.path("content"));assertThat(get(b,API+"/"+id)).isEqualTo(updated);
        var preview=input(updated);preview.put("restaurant",Map.of("address","미저장 주소"));
        var response=b.json("POST",API+"/"+id+"/preview",json.writeValueAsString(preview),token(b));assertThat(response.statusCode()).isEqualTo(200);assertThat(json.readTree(response.body()).path("restaurant").path("address").asText()).isEqualTo("미저장 주소");assertThat(get(b,API+"/"+id)).isEqualTo(updated);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        long image=media.upload(new AccountPrincipal(accounts.findByEmail("admin@restaurant.test")),new MockMultipartFile("file","restaurant.png","image/png",bytes.toByteArray()),"검증 이미지");
        String rich=json.writeValueAsString(Map.of("ops",List.of(Map.of("insert","식당 소개\n"),Map.of("insert",Map.of("aicaImage",Map.of("id",image,"width","100","align","center","alt","검증","caption",""))),Map.of("insert","\n"))));
        updated=save(b,updated,Map.of("richContent",rich));assertThat(updated.path("attachments").size()).isEqualTo(1);assertThat(updated.path("restaurant").path("address").asText()).isEqualTo("검증 주소 B");
        for(String view:List.of("manage","structure")){assertThat(b.get("/admin-next/posts/"+id+"/edit?view="+view+"&restaurantSection=all").statusCode()).isEqualTo(200);assertThat(get(b,API+"/"+id+"?view="+view)).isEqualTo(updated);}
        var list=get(b,API+"?typeCodes=RESTAURANT");assertThat(list.path("total").asInt()).isEqualTo(1);assertThat(list.path("items").size()).isEqualTo(1);
    }
    @Test void draftAddressAndLegacySavePublishPreserveIndependentSnapshot()throws Exception {
        var b=login("ADMIN");var p=create(b,"주소 A");long id=p.path("id").asLong();publish(b,p);p=get(b,API+"/"+id);var published=get(b,API+"/"+id+"/publication");
        p=save(b,p,Map.of("restaurant",Map.of("address","주소 B")));assertThat(get(b,API+"/"+id+"/publication")).isEqualTo(published);
        assertThat(b.get("/admin/posts/"+id+"/edit").body()).contains("주소 B","주소 편집");
        HttpBrowser.redirect(legacy(b,p,"save"),"/admin/posts/"+id);p=get(b,API+"/"+id);assertThat(p.path("restaurant").path("address").asText()).isEqualTo("주소 B");
        var form=new LinkedHashMap<String,String>();form.put("id",String.valueOf(id));form.put("revision",p.path("revision").asText());form.put("title",p.path("title").asText());form.put("content",p.path("content").asText());form.put("categoryId","11");
        assertThat(b.post("/admin/posts/save-json",form).statusCode()).isEqualTo(200);p=get(b,API+"/"+id);assertThat(p.path("restaurant").path("address").asText()).isEqualTo("주소 B");assertThat(get(b,API+"/"+id+"/publication")).isEqualTo(published);
        publish(b,p);assertThat(get(b,API+"/"+id+"/publication").path("restaurant").path("address").asText()).isEqualTo("주소 B");assertThat(b.get("/admin/posts/"+id+"/publication").body()).contains("주소 B");
        assertThat(get(b,API+"/"+id).path("categoryId").asLong()).isEqualTo(11);assertThat(jdbc.queryForObject("SELECT target_id FROM site_menus",Long.class)).isEqualTo(11);
    }
    @Test void leavingRestaurantRequiresExplicitAddressClearButOldPublicationKeepsAddress()throws Exception {
        var b=login("ADMIN");var p=create(b,"공개 주소");long id=p.path("id").asLong();publish(b,p);p=get(b,API+"/"+id);
        var value=input(p);value.put("classification",Map.of("typeCode","FAQ","cohortIds",List.of(),"topicIds",List.of()));
        assertThat(b.json("PUT",API+"/"+id,json.writeValueAsString(value),token(b)).statusCode()).isEqualTo(400);assertThat(get(b,API+"/"+id)).isEqualTo(p);
        value.put("restaurant",Map.of("address",""));var changed=save(b,p,value);assertThat(changed.path("restaurant").isNull()).isTrue();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_restaurant_details",Integer.class)).isZero();
        assertThat(get(b,API+"/"+id+"/publication").path("restaurant").path("address").asText()).isEqualTo("공개 주소");assertThat(get(b,API+"/"+id+"/preview").path("restaurant").isNull()).isTrue();
        publish(b,changed);assertThat(get(b,API+"/"+id+"/publication").path("restaurant").isNull()).isTrue();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_publication_restaurant_details",Integer.class)).isZero();
    }
    @Test void strictAddressContractAndTypeRestrictionDoNotWritePartialData()throws Exception {
        var b=login("ADMIN");var empty=create(b,null);assertThat(empty.path("restaurant").path("address").asText()).isEmpty();
        for(Object invalid:List.of(Map.of("address","x".repeat(501)),Map.of(),Map.of("address",12),Map.of("address","a","latitude",1),"address")){
            var value=body("RESTAURANT",null);value.put("restaurant",invalid);assertThat(b.json("POST",API,json.writeValueAsString(value),token(b)).statusCode()).isEqualTo(400);
        }
        var explicitNull=body("RESTAURANT",null);explicitNull.put("restaurant",null);assertThat(b.json("POST",API,json.writeValueAsString(explicitNull),token(b)).statusCode()).isEqualTo(400);
        for(String type:List.of("GENERAL","REVIEW","FAQ"))assertThat(b.json("POST",API,json.writeValueAsString(body(type,"주소")),token(b)).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts",Integer.class)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_restaurant_details",Integer.class)).isEqualTo(1);
    }
    @Test void existingSecurityConflictAndDeletionApplyToDetails()throws Exception {
        var admin=login("ADMIN");var p=create(admin,"보호 주소");long id=p.path("id").asLong();var supporter=login("SUPPORTER");
        assertThat(supporter.get(API+"/"+id).statusCode()).isEqualTo(403);var input=input(p);input.put("restaurant",Map.of("address","덮어쓰기"));
        assertThat(supporter.json("PUT",API+"/"+id,json.writeValueAsString(input),token(supporter)).statusCode()).isEqualTo(403);
        assertThat(admin.json("PUT",API+"/"+id,json.writeValueAsString(input),null).statusCode()).isEqualTo(403);
        var latest=save(admin,p,Map.of("restaurant",Map.of("address","최신 주소")));
        assertThat(admin.json("PUT",API+"/"+id,json.writeValueAsString(input),token(admin)).statusCode()).isEqualTo(409);assertThat(get(admin,API+"/"+id)).isEqualTo(latest);
        var own=create(supporter,"내 주소");assertThat(own.path("authorId").asLong()).isEqualTo(accounts.findByEmail("supporter@restaurant.test").id());
        publish(admin,latest);assertThat(admin.post("/admin/posts/"+id+"/delete",Map.of()).statusCode()).isEqualTo(403);
        var root=login("SUPER_ADMIN");
        assertThat(root.post("/admin/posts/"+id+"/delete",Map.of("revision",get(admin,API+"/"+id).path("revision").asText(),"confirmed","true")).statusCode()).isEqualTo(302);
        assertThat(jdbc.queryForObject("SELECT address FROM post_restaurant_details WHERE post_id=?",String.class,id)).isEqualTo("최신 주소");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_publication_restaurant_details WHERE post_id=?",Integer.class,id)).isEqualTo(1);
        long revision=jdbc.queryForObject("SELECT revision FROM posts WHERE id=?",Long.class,id);
        assertThat(root.json("DELETE",API+"/"+id+"/trash",json.writeValueAsString(Map.of("revision",revision,"confirmed",true)),token(root)).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_restaurant_details WHERE post_id=?",Integer.class,id)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_publication_restaurant_details WHERE post_id=?",Integer.class,id)).isZero();
    }
    @Test void failedSnapshotWriteRollsBackPublicationAndRevisionAsOneTransaction()throws Exception {
        var b=login("ADMIN");var p=create(b,"주소 A");long id=p.path("id").asLong();publish(b,p);var before=get(b,API+"/"+id+"/publication");p=save(b,get(b,API+"/"+id),Map.of("restaurant",Map.of("address","fail-test")));
        jdbc.execute("ALTER TABLE post_publication_restaurant_details ADD CONSTRAINT test_reject_address CHECK(address <> 'fail-test')");
        try{assertThat(legacy(b,p,"publish").statusCode()).isEqualTo(500);assertThat(get(b,API+"/"+id)).isEqualTo(p);assertThat(get(b,API+"/"+id+"/publication")).isEqualTo(before);}
        finally{jdbc.execute("ALTER TABLE post_publication_restaurant_details DROP CONSTRAINT test_reject_address");}
    }
}
