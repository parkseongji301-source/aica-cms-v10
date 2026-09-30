package egovframework.backoffice.integration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
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

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.datasource.url=jdbc:h2:mem:page-block-workflow;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class PageBlockWorkflowIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountMapper accounts;
    @Autowired PageService pages;
    static final String API="/api/admin/next/pages/",PASSWORD="Block-Test-Password42";
    long page;
    @BeforeEach void fixture() {
        for(String table:List.of("page_block_identities","page_media","page_publications","site_pages","site_menus","activity_log","users"))jdbc.update("DELETE FROM "+table);
        String hash=encoder.encode(PASSWORD);
        for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,?,FALSE)",role.toLowerCase(Locale.ROOT)+"@block.test",role,hash,role);
        page=pages.save(root(),null,null,"블록 검증","blocks","[{\"type\":\"TEXT\",\"heading\":\"A\",\"body\":\"내용 A\",\"visible\":true},{\"type\":\"TEXT\",\"heading\":\"B\",\"body\":\"내용 B\",\"visible\":true},{\"type\":\"TEXT\",\"heading\":\"C\",\"body\":\"내용 C\",\"visible\":true}]","publish");
    }
    AccountPrincipal root(){return new AccountPrincipal(accounts.findByEmail("super_admin@block.test"));}
 AccountPrincipal actor(){return new AccountPrincipal(accounts.findByEmail("admin@block.test"));}
    HttpBrowser login(String role)throws Exception {var b=new HttpBrowser(port);b.login(role+"@block.test",PASSWORD,"/admin");return b;}
    JsonNode get(HttpBrowser b,String path)throws Exception {var r=b.get(path);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
    String token(HttpBrowser b)throws Exception{return get(b,"/api/admin/next/bootstrap").path("csrf").path("token").asText();}
    ObjectNode input(JsonNode page){return json.createObjectNode().put("title",page.path("title").asText()).put("revision",page.path("revision").asLong()).set("sections",page.path("sections").deepCopy());}
    JsonNode save(HttpBrowser b,ObjectNode input)throws Exception {var r=b.json("PUT",API+page,input.toString(),token(b));assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
    java.net.http.HttpResponse<String> legacy(HttpBrowser b,JsonNode p,String action)throws Exception {return b.post("/admin/pages/save-json",Map.of("id",String.valueOf(page),"title",p.path("title").asText(),"revision",p.path("revision").asText(),"sectionsJson",p.path("sections").toString(),"action",action));}
    List<String> ids(JsonNode blocks){var ids=new ArrayList<String>();blocks.forEach(b->ids.add(b.path("id").asText()));return ids;}

    @Test void reorderAndEditKeepIdentityAndPublicationUntilLegacyRepublication()throws Exception {
        var b=login("admin");var original=get(b,API+page);var published=get(b,API+page+"/publication");assertThat(original.path("sections")).isEqualTo(published.path("sections"));
        var input=input(original);var old=(ArrayNode)input.path("sections");var reorder=json.createArrayNode().add(old.get(0)).add(old.get(2)).add(old.get(1));((ObjectNode)reorder.get(0)).put("body","수정된 A");((ObjectNode)reorder.get(1)).put("visible",false);input.set("sections",reorder);
        var saved=save(b,input);assertThat(ids(saved.path("sections"))).containsExactly(ids(original.path("sections")).get(0),ids(original.path("sections")).get(2),ids(original.path("sections")).get(1));assertThat(get(b,API+page)).isEqualTo(saved);assertThat(get(b,API+page+"/publication")).isEqualTo(published);
        var preview=get(b,API+page+"/preview");assertThat(ids(preview.path("sections"))).containsExactly(ids(original.path("sections")).get(0),ids(original.path("sections")).get(1));
        assertThat(b.get("/admin/legacy/pages/"+page+"/edit").body()).contains(ids(original.path("sections")).get(0));assertThat(legacy(b,saved,"save").statusCode()).isEqualTo(200);
        saved=get(b,API+page);assertThat(saved.path("sections")).isEqualTo(reorder);assertThat(get(b,API+page+"/publication")).isEqualTo(published);
        assertThat(legacy(b,saved,"publish").statusCode()).isEqualTo(200);assertThat(get(b,API+page+"/publication").path("sections")).isEqualTo(reorder);
        for(String view:List.of("manage","structure")){assertThat(b.get("/admin/pages/"+page+"/edit?view="+view).statusCode()).isEqualTo(200);assertThat(get(b,API+page+"?view="+view).path("sections")).isEqualTo(reorder);}
    }
    @Test void newAndServerDuplicateBlocksReceiveUniqueIdentitiesWithoutCopyingTheOriginalId()throws Exception {
        var b=login("admin");var original=get(b,API+page);var block=pages.sections(pages.get(actor(),page).sectionsJson()).get(0);var copy=PageBlockService.duplicate(block);
        assertThat(copy.id()).isNotEqualTo(block.id());assertThat(copy.heading()).isEqualTo(block.heading());assertThat(copy.body()).isEqualTo(block.body());
        var input=input(original);((ArrayNode)input.path("sections")).add(json.valueToTree(copy));var saved=save(b,input);assertThat(ids(saved.path("sections"))).doesNotHaveDuplicates().contains(copy.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM page_block_identities WHERE page_id=? AND retired=FALSE",Integer.class,page)).isEqualTo(4);
        assertThat(ids(get(b,API+page+"/publication").path("sections"))).doesNotContain(copy.id());
    }
    @Test void removedAndForeignIdsCannotBeReusedEvenAfterPageDeletion()throws Exception {
        var b=login("admin");var original=get(b,API+page);String removed=original.path("sections").get(2).path("id").asText();var input=input(original);((ArrayNode)input.path("sections")).remove(2);var saved=save(b,input);
        assertThat(jdbc.queryForObject("SELECT retired FROM page_block_identities WHERE block_id=?",Boolean.class,removed)).isTrue();assertThat(ids(get(b,API+page+"/publication").path("sections"))).contains(removed);
        input=input(saved);((ArrayNode)input.path("sections")).add(original.path("sections").get(2));assertThat(b.json("PUT",API+page,input.toString(),token(b)).statusCode()).isEqualTo(400);assertThat(get(b,API+page)).isEqualTo(saved);
        String other=pages.get(actor(),pages.save(root(),null,null,"다른 페이지","other","[{\"type\":\"TEXT\",\"visible\":true}]","save")).sectionsJson();var foreign=(ObjectNode)json.readTree(other).get(0);
        input=input(saved);((ArrayNode)input.path("sections")).add(foreign);assertThat(b.json("PUT",API+page,input.toString(),token(b)).statusCode()).isEqualTo(400);
        pages.delete(root(),page,saved.path("revision").asLong());assertThat(jdbc.queryForObject("SELECT page_id FROM page_block_identities WHERE block_id=?",Long.class,removed)).isNull();
        assertThatThrownBy(()->pages.save(root(),null,null,"재사용","reuse",original.path("sections").toString(),"save")).isInstanceOf(egovframework.backoffice.mvp.common.BusinessException.class);
    }
    @Test void unsupportedMetadataDuplicateIdsAndIdlessOldFormsCannotSilentlyRewriteIdentity()throws Exception {
        var b=login("admin");var original=get(b,API+page);
        for(String key:List.of("missingId","duplicate","schemaVersion","variation","unknown")) {
            var input=input(original);var blocks=(ArrayNode)input.path("sections");var first=(ObjectNode)blocks.get(0);
            switch(key){case "missingId"->first.remove("id");case "duplicate"->blocks.add(first.deepCopy());case "schemaVersion"->first.put("schemaVersion",999);case "variation"->first.put("variation","unregistered");case "unknown"->first.put("customCode","x");}
            assertThat(b.json("PUT",API+page,input.toString(),token(b)).statusCode()).as(key).isEqualTo(400);assertThat(get(b,API+page)).isEqualTo(original);
        }
        var old=input(original);for(var block:old.path("sections"))((ObjectNode)block).remove(List.of("id","schemaVersion","variation"));assertThat(legacy(b,old,"save").statusCode()).isEqualTo(400);assertThat(get(b,API+page)).isEqualTo(original);
    }
    @Test void revisionAndPermissionCsrfChecksApplyToBlockChangesAndPublicationReads()throws Exception {
        var b=login("admin");var original=get(b,API+page);var input=input(original);((ObjectNode)input.path("sections").get(0)).put("heading","최신");
        assertThat(b.json("PUT",API+page,input.toString(),null).statusCode()).isEqualTo(403);var saved=save(b,input);assertThat(b.json("PUT",API+page,input(original).toString(),token(b)).statusCode()).isEqualTo(409);
        var supporter=login("supporter");assertThat(supporter.get(API+page).statusCode()).isEqualTo(403);assertThat(supporter.get(API+page+"/publication").statusCode()).isEqualTo(403);assertThat(supporter.json("PUT",API+page,input(saved).toString(),token(supporter)).statusCode()).isEqualTo(403);assertThat(get(b,API+page)).isEqualTo(saved);
    }
    @Test void failedPublicationRollsBackBlockRegistrationDraftAndPublicationTogether()throws Exception {
        var b=login("admin");var original=get(b,API+page);var published=get(b,API+page+"/publication");var input=input(original).put("title","FAIL");String id=PageBlockService.newId();var copy=(ObjectNode)original.path("sections").get(0).deepCopy();copy.put("id",id);((ArrayNode)input.path("sections")).add(copy);
        jdbc.execute("ALTER TABLE page_publications ADD CONSTRAINT test_fail_block_publication CHECK(title<>'FAIL')");
        try {assertThat(legacy(b,input,"publish").statusCode()).isEqualTo(500);assertThat(get(b,API+page)).isEqualTo(original);assertThat(get(b,API+page+"/publication")).isEqualTo(published);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM page_block_identities WHERE block_id=?",Integer.class,id)).isZero();}
        finally {jdbc.execute("ALTER TABLE page_publications DROP CONSTRAINT test_fail_block_publication");}
    }

    @Test void registryExposesOnlyImplementedTypesAndVariationsWithManagerAuthorization()throws Exception {
        var b=login("admin");var catalog=get(b,"/api/admin/next/page-components");
        assertThat(catalog).hasSize(5);
        assertThat(catalog.get(0).path("type").asText()).isEqualTo("HERO");
        assertThat(catalog.get(0).path("variations").toString()).contains("default","centered");
        assertThat(catalog.get(0).path("fields").toString()).contains("heading","body","link").doesNotContain("imageId");
        for(var definition:catalog) {
            assertThat(definition.path("schemaVersion").asInt()).isEqualTo(2);
            assertThat(definition.path("defaultVariation").asText()).isEqualTo("default");
            assertThat(definition.path("defaults").path("visible").asBoolean()).isTrue();
            if(!definition.path("type").asText().equals("HERO"))assertThat(definition.path("variations")).hasSize(1);
        }
        assertThat(login("supporter").get("/api/admin/next/page-components").statusCode()).isEqualTo(403);
        assertThat(b.get("/css/page-blocks.css").body()).contains(".type-hero.variation-centered","text-align:center");
    }

    @Test void centeredVariationAndDuplicateDeletionRemainDraftUntilLegacyPublication()throws Exception {
        var b=login("admin");var initial=get(b,API+page);var originalPublication=get(b,API+page+"/publication");
        var input=input(initial);var blocks=(ArrayNode)input.path("sections");var hero=(ObjectNode)blocks.get(0);
        hero.put("type","HERO").put("variation","centered").put("visible",false).put("label","안내").put("link","/about");
        var firstSaved=save(b,input);var source=pages.sections(pages.get(actor(),page).sectionsJson()).get(0);var copy=PageBlockService.duplicate(source);
        assertThat(copy.variation()).isEqualTo("centered");assertThat(copy.visible()).isFalse();assertThat(copy.link()).isEqualTo("/about");
        input=input(firstSaved);blocks=(ArrayNode)input.path("sections");var cloned=(ObjectNode)json.valueToTree(copy);cloned.put("heading","독립 복제본").put("visible",true);
        var removed=blocks.get(1).deepCopy();blocks.remove(1);blocks.add(cloned);var swapped=json.createArrayNode().add(cloned).add(blocks.get(0)).add(blocks.get(1));input.set("sections",swapped);
        var saved=save(b,input);assertThat(saved.path("sections").get(1)).isEqualTo(firstSaved.path("sections").get(0));
        assertThat(ids(saved.path("sections"))).containsExactly(copy.id(),source.id(),initial.path("sections").get(2).path("id").asText());
        assertThat(get(b,API+page)).isEqualTo(saved);assertThat(get(b,API+page+"/publication")).isEqualTo(originalPublication);
        var preview=get(b,API+page+"/preview");assertThat(preview.path("sections")).hasSize(2);assertThat(preview.path("sections").get(0).path("variation").asText()).isEqualTo("centered");
        assertThat(b.get("/admin/legacy/pages/"+page+"/preview").body()).contains("type-hero variation-centered",copy.id());
        assertThat(legacy(b,saved,"save").statusCode()).isEqualTo(200);saved=get(b,API+page);assertThat(saved.path("sections")).isEqualTo(swapped);assertThat(get(b,API+page+"/publication")).isEqualTo(originalPublication);
        assertThat(legacy(b,saved,"publish").statusCode()).isEqualTo(200);saved=get(b,API+page);assertThat(get(b,API+page+"/publication").path("sections")).isEqualTo(swapped);
        input=input(saved);((ArrayNode)input.path("sections")).add(removed);assertThat(b.json("PUT",API+page,input.toString(),token(b)).statusCode()).isEqualTo(400);assertThat(get(b,API+page)).isEqualTo(saved);
    }

    @Test void crossTypeAndUnknownVariationsAreRejectedWithoutChangingEitherSnapshot()throws Exception {
        var b=login("admin");var initial=get(b,API+page);var publication=get(b,API+page+"/publication");
        for(String type:List.of("TEXT","IMAGE","POSTS","CTA")) {
            var input=input(initial);((ObjectNode)input.path("sections").get(0)).put("type",type).put("variation","centered");
            assertThat(b.json("PUT",API+page,input.toString(),token(b)).statusCode()).isEqualTo(400);
            assertThat(b.json("POST",API+page+"/preview",input.toString(),token(b)).statusCode()).isEqualTo(400);
            assertThat(get(b,API+page)).isEqualTo(initial);assertThat(get(b,API+page+"/publication")).isEqualTo(publication);
        }
        var input=input(initial);((ObjectNode)input.path("sections").get(0)).put("type","HERO").put("variation","arbitrary-css");
        assertThat(legacy(b,input,"publish").statusCode()).isEqualTo(400);assertThat(get(b,API+page)).isEqualTo(initial);
    }
}
