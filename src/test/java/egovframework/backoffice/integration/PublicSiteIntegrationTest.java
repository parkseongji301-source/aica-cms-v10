package egovframework.backoffice.integration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.classification.ClassificationModels.Selection;
import egovframework.backoffice.mvp.restaurant.RestaurantDetailsService.Details;
import egovframework.backoffice.mvp.post.PostService;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import java.util.*;
import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.datasource.url=jdbc:h2:mem:public-site;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class PublicSiteIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired PageService pages;@Autowired PostService posts;
    @Autowired MediaService media;@Autowired SiteService site;@Autowired PageTemplateService templates;
    @Autowired AccountMapper accounts;@Autowired PasswordEncoder encoder;
    static final String API="/api/public/v1",PASSWORD="Public-Test42";
    HttpBrowser anonymous;long page,life,both,faq,restaurant,draft,image,hiddenImage,document;
    AccountPrincipal actor(){return new AccountPrincipal(accounts.findByEmail("public@integration.test"));}
    @BeforeEach void setup()throws Exception {
        for(String t:List.of("page_templates","post_publication_media","post_media","post_publications","page_media","page_publications","page_block_identities","site_menus","site_pages","posts","media","categories","activity_log","users","content_type_topics","cohorts","topics"))jdbc.update("DELETE FROM "+t);
        jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES('public@integration.test','Private operator',?,'SUPER_ADMIN',FALSE)",encoder.encode(PASSWORD));
        jdbc.update("INSERT INTO categories(id,name) VALUES(11,'기존 카테고리')");
        jdbc.update("INSERT INTO cohorts(id,code,name) VALUES(101,'COHORT_06','6기'),(102,'COHORT_07','7기')");
        jdbc.update("INSERT INTO topics(id,code,name) VALUES(201,'REVIEW_LIFE','생활'),(202,'REVIEW_PROJECT','프로젝트'),(204,'FAQ_LIFE','생활')");
        jdbc.update("INSERT INTO content_type_topics VALUES('REVIEW',201),('REVIEW',202),('FAQ',204)");
        life=create("공개 생활 후기","REVIEW",List.of(102L),List.of(201L),"publish");
        both=create("두 기수 후기","REVIEW",List.of(101L,102L),List.of(201L,202L),"publish");
        faq=create("주차 공간이 있나요?","FAQ",List.of(),List.of(204L),"publish");
        restaurant=create("공개 식당","RESTAURANT",List.of(),List.of(),"publish");
        draft=create("절대 노출할 수 없는 초안","REVIEW",List.of(),List.of(),"save");
        var r=posts.get(actor(),restaurant);posts.save(actor(),r.id(),r.revision(),r.title(),r.content(),11L,List.of(),"publish",null,null,new Details("공개 주소"));
        jdbc.update("UPDATE post_publications SET published_at=TIMESTAMP '2026-01-01 10:00:00'");
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        image=media.upload(actor(),new MockMultipartFile("file","public.png","image/png",bytes.toByteArray()),"공개 이미지");
        hiddenImage=media.upload(actor(),new MockMultipartFile("file","hidden.png","image/png",bytes.toByteArray()),"비공개 이미지");
        document=media.upload(actor(),new MockMultipartFile("file","C:\\private\\attached.txt","text/plain","attachment".getBytes()),"");
        var blocks=json.createArrayNode();blocks.add(block("HERO","default").put("heading","발행된 소개"));
        blocks.add(block("HERO","centered").put("heading","가운데 강조"));
        blocks.add(block("TEXT","default").put("heading","본문").put("bodyDoc",rich()));
        blocks.add(block("IMAGE","default").put("imageId",image));
        blocks.add(block("CTA","default").put("link","https://example.com/apply").put("label","지원"));
        blocks.add(block("POSTS","default").put("sourceMode","category").put("categoryId",11));
        var query=block("POSTS","default").put("sourceMode","query");query.set("query",query("REVIEW",List.of(101L,102L),List.of(201L,202L),20));blocks.add(query);
        var manual=block("POSTS","default").put("sourceMode","manual");manual.set("manual",json.valueToTree(Map.of("postIds",List.of(restaurant,draft,faq,999999L,life))));blocks.add(manual);
        blocks.add(block("IMAGE","default").put("visible",false).put("heading","숨겨진 비밀").put("imageId",hiddenImage));
        page=pages.save(actor(),null,null,"공개 페이지","public-test",blocks.toString(),"publish");
        site.menu(actor(),null,"","PAGE",page,"",true);site.menu(actor(),null,"","CATEGORY",11L,"",true);
        anonymous=new HttpBrowser(port);
    }
    long create(String title,String type,List<Long> cohorts,List<Long> topics,String action){return posts.save(actor(),null,null,title,"발행 본문",11L,List.of(),action,null,new Selection(type,cohorts,topics));}
    String rich(){return "{\"ops\":[{\"insert\":\"<script>bad</script>\\n\"},{\"insert\":{\"aicaImage\":{\"id\":"+image+",\"alt\":\"공개 이미지\"}}},{\"insert\":{\"aicaFile\":{\"id\":"+document+",\"label\":\"첨부\"}}}]}";}
    ObjectNode block(String type,String variation){return json.createObjectNode().put("id",PageBlockService.newId()).put("schemaVersion",2).put("type",type).put("variation",variation).put("visible",true);}
    JsonNode query(String type,List<Long> cohorts,List<Long> topics,int limit){return json.valueToTree(Map.of("typeCode",type,"cohortIds",cohorts,"topicIds",topics,"sort","LATEST","limit",limit));}
    JsonNode ok(java.net.http.HttpResponse<String> r)throws Exception{assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
    JsonNode get(String path)throws Exception{return ok(anonymous.get(API+path));}
    ArrayNode sections()throws Exception{return (ArrayNode)json.readTree(pages.get(actor(),page).sectionsJson());}
    void save(ArrayNode blocks,String title,String action){var p=pages.get(actor(),page);pages.save(actor(),page,p.revision(),title,p.slug(),blocks.toString(),action);}
    List<Long> ids(JsonNode result){var ids=new ArrayList<Long>();result.path("items").forEach(p->ids.add(p.path("id").asLong()));assertThat(ids).doesNotHaveDuplicates();return ids;}
    HttpBrowser login()throws Exception{var b=new HttpBrowser(port);b.login("public@integration.test",PASSWORD,"/admin");return b;}
    @Test void anonymousContractOnlyContainsVisiblePublishedFieldsAndSharedRenderingRules()throws Exception {
        var response=anonymous.get(API+"/pages/"+page);var p=ok(response);assertThat(p.path("apiVersion").asInt()).isEqualTo(1);
        assertThat(get("/pages/by-slug/public-test")).isEqualTo(p);assertThat(p.path("blocks")).hasSize(8);
        assertThat(p.path("blocks").findValuesAsText("type")).contains("HERO","TEXT","IMAGE","POSTS","CTA");
        assertThat(p.path("blocks").get(1).path("variation").asText()).isEqualTo("centered");
        assertThat(response.body()).doesNotContain("authorId","ownerId","ownerName","Private operator","sectionsJson","\"query\":","postIds","숨겨진 비밀","절대 노출할 수 없는 초안");
        String html=p.path("blocks").get(2).path("data").path("bodyHtml").asText();assertThat(html).contains("&lt;script&gt;","/api/public/v1/media/").doesNotContain("<script>","/admin/media/");
        var b=login();var preview=ok(b.get("/api/admin/next/pages/"+page+"/publication/preview"));
        for(int i=0;i<8;i++){assertThat(preview.path("sections").get(i).path("id")).isEqualTo(p.path("blocks").get(i).path("id"));assertThat(preview.path("sections").get(i).path("bodyHtml").asText().replace("/admin/media/",API+"/media/")).isEqualTo(p.path("blocks").get(i).path("data").path("bodyHtml").asText());}
        assertThat(response.headers().firstValue("cache-control").orElse("")).contains("no-store");assertThat(response.headers().allValues("set-cookie")).isEmpty();
    }
    @Test void categoryQueryManualKeepDistinctRulesCountsOrderAndPublicationFiltering()throws Exception {
        var blocks=get("/pages/"+page).path("blocks");
        assertThat(ids(blocks.get(5).path("data").path("posts"))).containsExactly(restaurant,faq,both,life);
        assertThat(ids(blocks.get(6).path("data").path("posts"))).containsExactly(both,life);
        assertThat(blocks.get(6).path("data").path("posts").path("total").asInt()).isEqualTo(2);
        assertThat(ids(blocks.get(7).path("data").path("posts"))).containsExactly(restaurant,faq,life);
        assertThat(get("/pages/"+page+"/blocks/"+blocks.get(7).path("id").asText()+"/posts")).isEqualTo(blocks.get(7).path("data").path("posts"));
        var changed=sections();((ObjectNode)changed.get(6)).set("query",query("FAQ",List.of(),List.of(204L),1));save(changed,"공개 페이지","publish");
        var faqList=get("/pages/"+page).path("blocks").get(6).path("data").path("posts");assertThat(ids(faqList)).containsExactly(faq);assertThat(faqList.path("total").asInt()).isEqualTo(1);
        ((ObjectNode)changed.get(6)).set("query",query("REVIEW",List.of(101L),List.of(201L),1));save(changed,"공개 페이지","publish");
        assertThat(ids(get("/pages/"+page).path("blocks").get(6).path("data").path("posts"))).containsExactly(both);
        assertThat(ids(get("/posts?categoryId=11&limit=2&page=2"))).containsExactly(both,life);
        assertThat(get("/posts?categoryId=11&limit=2").path("total").asInt()).isEqualTo(4);
        for(String url:List.of("/posts?limit=0","/posts?limit=21","/posts?page=-1","/posts?page=2147483647","/posts?categoryId=-1","/posts/not-id"))assertThat(anonymous.get(API+url).statusCode()).isEqualTo(400);
    }
    @Test void contentTitleClassificationAndRestaurantAddressStayPublishedUntilRepublish()throws Exception {
        var blocks=sections();((ObjectNode)blocks.get(6)).set("query",query("REVIEW",List.of(),List.of(201L),20));save(blocks,"공개 페이지","publish");
        var old=get("/posts/"+life);var oldRestaurant=get("/posts/"+restaurant);var oldPage=get("/pages/"+page);
        var p=posts.get(actor(),life);posts.save(actor(),life,p.revision(),"초안 제목","초안 본문",null,List.of(),"save",null,new Selection("REVIEW",List.of(101L),List.of(202L)));
        var r=posts.get(actor(),restaurant);posts.save(actor(),restaurant,r.revision(),"초안 식당",r.content(),11L,List.of(),"save",null,null,new Details("새 주소"));
        assertThat(get("/posts/"+life)).isEqualTo(old);assertThat(get("/posts/"+restaurant)).isEqualTo(oldRestaurant);assertThat(get("/pages/"+page)).isEqualTo(oldPage);
        p=posts.get(actor(),life);posts.save(actor(),life,p.revision(),p.title(),p.content(),p.categoryId(),List.of(),"publish",null); // Legacy omitted classification.
        assertThat(ids(get("/pages/"+page).path("blocks").get(6).path("data").path("posts"))).containsExactly(both);
        assertThat(get("/posts/"+life).path("classification").path("topics").get(0).path("code").asText()).isEqualTo("REVIEW_PROJECT");
        r=posts.get(actor(),restaurant);posts.save(actor(),restaurant,r.revision(),r.title(),r.content(),r.categoryId(),List.of(),"publish",null);
        assertThat(get("/posts/"+restaurant).path("restaurant").path("address").asText()).isEqualTo("새 주소");assertThat(get("/posts/"+faq).path("restaurant").isNull()).isTrue();
        jdbc.update("UPDATE topics SET name='사전의 새 이름' WHERE id=204");assertThat(get("/posts/"+faq).path("classification").path("topics").get(0).path("name").asText()).isEqualTo("생활");
    }
    @Test void pageDraftAndMenusDoNotLeakUntilLegacyPublishAndHiddenBlockCannotBeFetched()throws Exception {
        var before=get("/pages/"+page);var menu=get("/menus");var blocks=sections();String hidden=blocks.get(8).path("id").asText();
        ((ObjectNode)blocks.get(0)).put("heading","초안 편집");((ObjectNode)blocks.get(7)).set("manual",json.valueToTree(Map.of("postIds",List.of(life,restaurant))));
        ((ObjectNode)blocks.get(6)).set("query",query("FAQ",List.of(),List.of(204L),3));save(blocks,"초안 페이지 이름","save");
        assertThat(get("/pages/"+page)).isEqualTo(before);assertThat(get("/menus")).isEqualTo(menu);
        var b=login();assertThat(ok(b.get("/api/admin/next/pages/"+page+"/preview")).path("sections").get(0).path("heading").asText()).isEqualTo("초안 편집");
        var p=pages.get(actor(),page);ok(b.post("/admin/pages/save-json",Map.of("id",String.valueOf(page),"revision",String.valueOf(p.revision()),"title",p.title(),"sectionsJson",p.sectionsJson(),"action","publish")));
        assertThat(get("/pages/"+page).path("title").asText()).isEqualTo("초안 페이지 이름");assertThat(get("/menus").get(0).path("label").asText()).isEqualTo("초안 페이지 이름");
        assertThat(ids(get("/pages/"+page).path("blocks").get(7).path("data").path("posts"))).containsExactly(life,restaurant);
        assertThat(anonymous.get(API+"/pages/"+page+"/blocks/"+hidden+"/posts").statusCode()).isEqualTo(404);
        assertThat(anonymous.get(API+"/pages/"+page+"/blocks/block_missing/posts").statusCode()).isEqualTo(404);
    }
    @Test void unpublishedPrivateDeletedTargetsNeverBecomeVisibleEvenWhenManualRetainsIds()throws Exception {
        assertThat(anonymous.get(API+"/posts/"+draft).statusCode()).isEqualTo(404);
        posts.unpublish(actor(),faq,posts.get(actor(),faq).revision());posts.delete(actor(),restaurant,posts.get(actor(),restaurant).revision());
        assertThat(anonymous.get(API+"/posts/"+faq).statusCode()).isEqualTo(404);assertThat(anonymous.get(API+"/posts/"+restaurant).statusCode()).isEqualTo(404);
        assertThat(ids(get("/pages/"+page).path("blocks").get(7).path("data").path("posts"))).containsExactly(life);
        assertThat(sections().get(7).path("manual").path("postIds")).hasSize(5);
        pages.unpublish(actor(),page,pages.get(actor(),page).revision());assertThat(anonymous.get(API+"/pages/"+page).statusCode()).isEqualTo(404);assertThat(anonymous.get(API+"/pages/by-slug/public-test").statusCode()).isEqualTo(404);assertThat(get("/menus").findValuesAsText("kind")).doesNotContain("PAGE");
    }
    @Test void mediaRequiresCurrentPublicationNotDraftTemplateLogoOrHiddenBlockAndRechecksWithdrawal()throws Exception {
        assertThat(anonymous.get(API+"/media/"+image+"/file").statusCode()).isEqualTo(200);
        var attachment=anonymous.get(API+"/media/"+document+"/file");assertThat(attachment.statusCode()).isEqualTo(200);assertThat(attachment.headers().firstValue("content-disposition").orElse("")).contains("attachment","attached.txt").doesNotContain("private");
        assertThat(attachment.headers().firstValue("x-content-type-options").orElse("")).isEqualTo("nosniff");
        templates.save(actor(),null,null,"미발행 이미지 템플릿","",true,pages.sections(sections().toString()));
        jdbc.update("UPDATE site_settings SET setting_value=? WHERE setting_key='logoId'",String.valueOf(hiddenImage));
        assertThat(anonymous.get(API+"/media/"+hiddenImage+"/file").statusCode()).isEqualTo(404);
        var blocks=sections();((ObjectNode)blocks.get(3)).put("imageId",hiddenImage);save(blocks,"공개 페이지","save");assertThat(anonymous.get(API+"/media/"+hiddenImage+"/file").statusCode()).isEqualTo(404);
        var p=posts.get(actor(),life);posts.save(actor(),life,p.revision(),p.title(),p.content(),11L,List.of(document),"publish",null);
        pages.unpublish(actor(),page,pages.get(actor(),page).revision());assertThat(anonymous.get(API+"/media/"+image+"/file").statusCode()).isEqualTo(404);assertThat(anonymous.get(API+"/media/"+document+"/file").statusCode()).isEqualTo(200);
        posts.unpublish(actor(),life,posts.get(actor(),life).revision());assertThat(anonymous.get(API+"/media/"+document+"/file").statusCode()).isEqualTo(404);
        for(String url:List.of("/media/..%2F..%2Fapplication.yml/file","/media/%2e%2e/file","/media/999999/file"))assertThat(anonymous.get(API+url).statusCode()).isIn(400,404);
    }
    @Test void securitySeparatesAnonymousReadOnlyApiFromSessionsCsrfAndPrivateEndpoints()throws Exception {
        for(String path:List.of("/api/admin/next/pages/"+page,"/api/admin/next/posts/"+life,"/api/admin/next/accounts","/api/admin/next/page-templates"))assertThat(anonymous.get(path).statusCode()).isEqualTo(401);
        for(String method:List.of("POST","PUT","PATCH","DELETE"))assertThat(anonymous.json(method,API+"/pages/"+page,"{}",null).statusCode()).isEqualTo(403);
        var b=login();assertThat(ok(b.get(API+"/pages/"+page))).isEqualTo(get("/pages/"+page));assertThat(b.json("PUT","/api/admin/next/pages/"+page,"{}",null).statusCode()).isEqualTo(403);
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+API+"/menus")).header("Origin","https://unconfigured.example").GET().build(),HttpResponse.BodyHandlers.ofString());assertThat(response.headers().firstValue("access-control-allow-origin")).isEmpty();
        assertThat(get("/posts/"+life).toString()).doesNotContain("revision","author","owner","password","active","categoryName");
    }
    @Test void templateCopiesPublishIndependentIdsWhilePreservingThreePostsModesAndVariations()throws Exception {
        var original=pages.sections(sections().toString());var t=templates.save(actor(),null,null,"공용 페이지 구성","",true,original);var prepared=templates.prepare(actor(),t.info().id(),t.info().revision());
        var before=get("/pages/"+page);save((ArrayNode)json.valueToTree(prepared.sections()),"공개 페이지","save");assertThat(get("/pages/"+page)).isEqualTo(before);
        save(sections(),"공개 페이지","publish");var after=get("/pages/"+page);
        var oldIds=new HashSet<String>();before.path("blocks").forEach(b->oldIds.add(b.path("id").asText()));after.path("blocks").forEach(b->assertThat(oldIds).doesNotContain(b.path("id").asText()));
        for(int i=0;i<8;i++){assertThat(after.path("blocks").get(i).path("data")).isEqualTo(before.path("blocks").get(i).path("data"));assertThat(after.path("blocks").get(i).path("variation")).isEqualTo(before.path("blocks").get(i).path("variation"));}
        templates.save(actor(),t.info().id(),t.info().revision(),"비활성","",false,null);assertThat(get("/pages/"+page)).isEqualTo(after);
    }
}
