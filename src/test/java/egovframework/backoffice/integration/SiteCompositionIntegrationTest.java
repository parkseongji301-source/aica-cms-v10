package egovframework.backoffice.integration;

import com.fasterxml.jackson.databind.*;
import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.common.BusinessException;
import egovframework.backoffice.mvp.security.*;
import java.net.http.HttpResponse;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

/** V14 step 1: GROUP areas, content-work links, menu visibility/label and the published-structure delete guard. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:site-composition-integration;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class SiteCompositionIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountMapper accounts;
    @Autowired PageService pages;
    @Autowired SiteService site;
    @Autowired CmsStore store;
    private static final String API="/api/admin/next";
    private static final String PASSWORD="Test-Composition-Password42";
    private static final String SECTIONS="[{\"type\":\"TEXT\",\"heading\":\"소개\",\"body\":\"본문\",\"visible\":true}]";
    private long root;

    @BeforeEach void fixture() {
        for(String table:List.of("site_structure_publication_pages","site_structure_publications","post_publication_media","post_media","post_publications","page_media","page_publications","page_block_identities","page_version_media","page_versions","site_pages","posts","media","site_menus","categories","site_links","activity_log","users"))jdbc.update("DELETE FROM "+table);
        jdbc.update("UPDATE site_settings SET setting_value='' WHERE setting_key IN ('logoId','homePageId')");
        String hash=encoder.encode(PASSWORD);
        for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,?,FALSE)",email(role),role,hash,role);
        root=accounts.findByEmail(email("SUPER_ADMIN")).id();
        Object[][] fixture={{1L,"홈","home"},{65L,"인사교 소개","about"},{70L,"후기","reviews"},{72L,"오시는 길","location"}};
        for(Object[] p:fixture){
            jdbc.update("INSERT INTO site_pages(id,title,slug,sections_json,status,revision,author_id) VALUES(?,?,?,?,'PUBLISHED',2,?)",p[0],p[1],p[2],SECTIONS,root);
            jdbc.update("INSERT INTO page_publications(page_id,title,slug,sections_json,revision) SELECT id,title,slug,sections_json,revision FROM site_pages WHERE id=?",p[0]);
        }
        jdbc.update("UPDATE site_settings SET setting_value='1' WHERE setting_key='homePageId'");
        jdbc.execute("ALTER TABLE site_pages ALTER COLUMN id RESTART WITH 1000");
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) c->{try{db.migration.h2.V8__page_block_identity.upgrade(c);}catch(Exception e){throw new IllegalStateException(e);}return null;});
    }
    private static String email(String role){return role.toLowerCase(Locale.ROOT)+"@composition.test";}
    private AccountPrincipal principal(String role){return new AccountPrincipal(accounts.findByEmail(email(role)));}
    private HttpBrowser login(String role)throws Exception{var b=new HttpBrowser(port);b.login(email(role),PASSWORD,"/admin");return b;}
    private String csrf(HttpBrowser b)throws Exception{return json.readTree(b.get(API+"/bootstrap").body()).path("csrf").path("token").asText();}
    private JsonNode body(HttpResponse<String> r)throws Exception{return json.readTree(r.body());}
    private String failure(HttpResponse<String> r,int status)throws Exception{assertThat(r.statusCode()).as(r.body()).isEqualTo(status);return body(r).path("message").asText();}
    private HttpResponse<String> send(HttpBrowser b,String method,String path,Object value,String csrf)throws Exception{return b.json(method,API+path,json.writeValueAsString(value),csrf);}
    private Map<String,Object> composition(String type,boolean visible,String label,String name){var m=new LinkedHashMap<String,Object>();m.put("contentTypeCode",type);m.put("menuVisible",visible);m.put("menuLabel",label);m.put("name",name);return m;}
    private JsonNode row(JsonNode rows,long id){for(var r:rows)if(r.path("id").asLong()==id)return r;throw new AssertionError("row "+id);}
    private long groupId(JsonNode rows,String name){for(var r:rows)if("GROUP".equals(r.path("areaKind").asText())&&name.equals(r.path("title").asText()))return r.path("id").asLong();throw new AssertionError(name);}
    private Map<String,Object> group(String name,Long parent){var m=new LinkedHashMap<String,Object>();m.put("name",name);m.put("parentId",parent);return m;}

    @Test void groupsAreStructureNodesAndPagesCanNestThreeLevels()throws Exception {
        var admin=login("SUPER_ADMIN");String csrf=csrf(admin);
        var rows=body(send(admin,"POST","/page-groups",group("선배들의 SSUL",null),csrf));
        long ssul=groupId(rows,"선배들의 SSUL");
        assertThat(row(rows,ssul).path("status").asText()).isEqualTo("DRAFT");
        assertThat(row(rows,ssul).path("slug").asText()).startsWith("group-");
        assertThat(row(rows,1).path("areaKind").asText()).isEqualTo("PAGE");
        assertThat(row(rows,1).path("menuVisible").asBoolean()).isFalse();
        long tips=groupId(body(send(admin,"POST","/page-groups",group("인사교 꿀팁",65L),csrf)),"인사교 꿀팁");
        // PAGE under GROUP under PAGE: three levels, the current operating limit.
        assertThat(send(admin,"PUT","/pages/72/placement",Map.of("parentId",tips),csrf).statusCode()).isEqualTo(200);
        assertThat(send(admin,"PUT","/pages/70/placement",Map.of("parentId",ssul),csrf).statusCode()).isEqualTo(200);
        assertThat(failure(send(admin,"POST","/page-groups",group("넷째",72L),csrf),400)).contains("최대 3단계");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM page_versions WHERE page_id=?",Integer.class,ssul)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log WHERE action='묶음 추가'",Integer.class)).isEqualTo(2);
        for(String role:List.of("ADMIN","SUPPORTER")){var b=login(role);
            var r=send(b,"POST","/page-groups",group("권한 없음",null),csrf(b));assertThat(r.statusCode()).as(role).isEqualTo(403);assertThat(body(r).path("code").asText()).isEqualTo("FORBIDDEN_OR_CSRF");}
    }

    @Test void theContentTypeLinkMarksOneRepresentativeAreaWithoutTouchingBlocksOrRevision()throws Exception {
        var admin=login("SUPER_ADMIN");String csrf=csrf(admin);
        var rows=body(send(admin,"PUT","/pages/70/composition",composition("REVIEW",true,"후기 모음",null),csrf));
        assertThat(row(rows,70).path("contentTypeCode").asText()).isEqualTo("REVIEW");
        assertThat(row(rows,70).path("menuVisible").asBoolean()).isTrue();assertThat(row(rows,70).path("menuLabel").asText()).isEqualTo("후기 모음");
        assertThat(jdbc.queryForObject("SELECT revision FROM site_pages WHERE id=70",Long.class)).isEqualTo(2L);
        // One representative area per type is the operating rule; the message names the current area.
        assertThat(failure(send(admin,"PUT","/pages/72/composition",composition("REVIEW",false,null,null),csrf),400)).contains("'후기'","대표 작업 영역");
        assertThat(failure(send(admin,"PUT","/pages/72/composition",composition("NOPE",false,null,null),csrf),400)).contains("콘텐츠 유형");
        jdbc.update("UPDATE content_types SET active=FALSE WHERE code='INTERVIEW'");
        assertThat(failure(send(admin,"PUT","/pages/72/composition",composition("INTERVIEW",false,null,null),csrf),400)).contains("콘텐츠 유형");
        jdbc.update("UPDATE content_types SET active=TRUE WHERE code='INTERVIEW'");
        assertThat(failure(send(admin,"PUT","/pages/72/composition",composition(null,false,null,"다른 제목"),csrf),400)).contains("편집기");
        // A blank label means "use the title".
        assertThat(row(body(send(admin,"PUT","/pages/70/composition",composition("REVIEW",true,"  ",null),csrf)),70).path("menuLabel").isNull()).isTrue();
        // The link never limits where the type's posts appear: another page lists REVIEW posts freely.
        var doc=body(admin.get(API+"/pages/72"));
        var sections=(com.fasterxml.jackson.databind.node.ArrayNode)doc.path("sections");
        var posts=sections.addObject();posts.put("id","block_"+UUID.randomUUID());posts.put("schemaVersion",2);posts.put("type","POSTS");posts.put("variation","default");posts.put("heading","최근 후기");
        posts.put("body","");posts.putNull("bodyDoc");posts.putNull("imageId");posts.putNull("categoryId");posts.put("link","");posts.put("label","");posts.put("visible",true);posts.put("sourceMode","query");posts.putNull("manual");
        var query=posts.putObject("query");query.put("typeCode","REVIEW");query.putArray("cohortIds");query.putArray("topicIds");query.put("sort","LATEST");query.put("limit",6);
        var published=send(admin,"POST","/pages/72/publish",Map.of("title","오시는 길","revision",doc.path("revision").asLong(),"sections",sections),csrf);
        assertThat(published.statusCode()).as(published.body()).isEqualTo(200);
        // Unlinking leaves the page and its content alone.
        assertThat(row(body(send(admin,"PUT","/pages/70/composition",composition(null,false,null,null),csrf)),70).path("contentTypeCode").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log WHERE action='사이트 구성 변경'",Integer.class)).isEqualTo(3);
        for(String role:List.of("ADMIN","SUPPORTER")){var b=login(role);
            assertThat(send(b,"PUT","/pages/70/composition",composition("REVIEW",true,null,null),csrf(b)).statusCode()).as(role).isEqualTo(403);}
    }

    @Test void groupsAreExcludedFromEveryPagePath()throws Exception {
        var admin=login("SUPER_ADMIN");String csrf=csrf(admin);
        long group=groupId(body(send(admin,"POST","/page-groups",group("인사교 꿀팁",null),csrf)),"인사교 꿀팁");
        var actor=principal("SUPER_ADMIN");
        String slug=jdbc.queryForObject("SELECT slug FROM site_pages WHERE id=?",String.class,group);
        // Document save, publish, withdraw, address and preview over the React API.
        assertThat(failure(send(admin,"PUT","/pages/"+group,Map.of("revision",0,"title","x","sections",List.of()),csrf),400)).contains("묶음");
        assertThat(failure(send(admin,"POST","/pages/"+group+"/publish",Map.of("revision",0,"title","x","sections",List.of()),csrf),400)).contains("묶음");
        assertThat(failure(send(admin,"POST","/pages/"+group+"/unpublish",Map.of("revision",0),csrf),400)).contains("묶음");
        assertThat(failure(send(admin,"PUT","/pages/"+group+"/address",Map.of("revision",0,"slug","tips"),csrf),400)).contains("묶음");
        assertThat(failure(admin.get(API+"/pages/"+group+"/preview"),400)).contains("묶음");
        assertThat(failure(send(admin,"POST","/pages/"+group+"/preview",Map.of("title","x","sections",List.of()),csrf),400)).contains("묶음");
        // The legacy form endpoint and version restore go through the same service path.
        assertThat(body(admin.post("/admin/pages/save-json",Map.of("id",""+group,"revision","0","title","x","sectionsJson","[]"))).path("error").asText()).contains("묶음");
        assertThatThrownBy(()->pages.save(actor,group,0L,"x",slug,"[]","publish")).isInstanceOf(BusinessException.class).hasMessageContaining("묶음");
        assertThatThrownBy(()->pages.restoreDraft(actor,group,0L,"x","[]")).isInstanceOf(BusinessException.class).hasMessageContaining("묶음");
        // Home setting, legacy menus and content-work links refuse a GROUP.
        assertThatThrownBy(()->site.saveSettings(actor,"basic",Map.of("siteName","사이트","description","","contactEmail","","homePageId",""+group))).isInstanceOf(BusinessException.class).hasMessageContaining("묶음");
        assertThatThrownBy(()->site.menu(actor,null,"꿀팁","PAGE",group,"",true)).isInstanceOf(BusinessException.class).hasMessageContaining("묶음");
        assertThat(failure(send(admin,"PUT","/pages/"+group+"/composition",composition("RESTAURANT",true,null,"인사교 꿀팁"),csrf),400)).contains("묶음");
        // Versions, block navigation and the public page API never see a GROUP.
        assertThat(store.<Long>all("versionPageIds",null)).doesNotContain(group).contains(1L,65L,70L,72L);
        assertThat(admin.get(API+"/page-structure").body()).doesNotContain("\"pageId\":"+group+",");
        var anonymous=new HttpBrowser(port);
        assertThat(anonymous.get("/api/public/v1/pages/"+group).statusCode()).isEqualTo(404);
        assertThat(anonymous.get("/api/public/v1/pages/by-slug/"+slug).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM page_publications WHERE page_id=?",Integer.class,group)).isZero();
        // Renaming a GROUP is a composition change; it stays deletable like any page.
        assertThat(row(body(send(admin,"PUT","/pages/"+group+"/composition",composition(null,true,null,"생활 꿀팁"),csrf)),group).path("title").asText()).isEqualTo("생활 꿀팁");
        assertThat(send(admin,"DELETE","/pages/"+group,Map.of("revision",0,"confirmed",true),csrf).statusCode()).isEqualTo(200);
    }

    @Test void onlyTheLatestPublishedStructureProtectsItsPagesFromPermanentDeletion()throws Exception {
        var admin=login("SUPER_ADMIN");String csrf=csrf(admin);
        jdbc.update("INSERT INTO site_structure_publications(id,snapshot_json,fingerprint,reason,published_by,publisher_name) VALUES(1,'[]','a','PUBLISH',?,'관리자')",root);
        jdbc.update("INSERT INTO site_structure_publication_pages(publication_id,page_id) VALUES(1,72)");
        assertThat(body(admin.get(API+"/pages/72/delete-impact")).path("uses").toString()).contains("게시된 사이트 구성");
        assertThat(failure(send(admin,"DELETE","/pages/72",Map.of("revision",2,"confirmed",true),csrf),400)).contains("게시된 사이트 구성");
        // A newer structure without the page releases it; the older snapshot no longer protects anything.
        jdbc.update("INSERT INTO site_structure_publications(id,snapshot_json,fingerprint,reason,published_by,publisher_name) VALUES(2,'[]','b','PUBLISH',?,'관리자')",root);
        assertThat(body(admin.get(API+"/pages/72/delete-impact")).path("uses").toString()).doesNotContain("게시된 사이트 구성");
        assertThat(send(admin,"DELETE","/pages/72",Map.of("revision",2,"confirmed",true),csrf).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_structure_publication_pages WHERE page_id=72",Integer.class)).isEqualTo(1);
    }

    @Test void everyRoleGetsTheRepresentativeWorkAreasInStructureOrder()throws Exception {
        var admin=login("SUPER_ADMIN");String csrf=csrf(admin);
        long ssul=groupId(body(send(admin,"POST","/page-groups",group("선배들의 SSUL",null),csrf)),"선배들의 SSUL");
        send(admin,"PUT","/pages/70/placement",Map.of("parentId",ssul),csrf);
        send(admin,"PUT","/pages/70/composition",composition("REVIEW",false,null,null),csrf);
        send(admin,"PUT","/pages/72/composition",composition("FAQ",false,null,null),csrf);
        for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER")){
            var areas=json.readTree(login(role).get(API+"/bootstrap").body()).path("contentAreas");
            assertThat(areas.toString()).as(role).isEqualTo("[{\"pageId\":72,\"typeCode\":\"FAQ\",\"label\":\"오시는 길\",\"groups\":[]},{\"pageId\":70,\"typeCode\":\"REVIEW\",\"label\":\"후기\",\"groups\":[\"선배들의 SSUL\"]}]");
        }
    }

    @Test void stepOneLeavesThePublicMenusAndPagesUnchanged()throws Exception {
        jdbc.update("INSERT INTO site_menus(label,kind,target_id,sort_order) VALUES('소개','PAGE',65,0)");
        var anonymous=new HttpBrowser(port);
        String menus=anonymous.get("/api/public/v1/menus").body(),page=anonymous.get("/api/public/v1/pages/by-slug/about").body();
        var admin=login("SUPER_ADMIN");String csrf=csrf(admin);
        send(admin,"PUT","/pages/70/composition",composition("REVIEW",true,"후기 모음",null),csrf);
        send(admin,"PUT","/pages/65/composition",composition(null,true,"인사교 알아보기",null),csrf);
        send(admin,"POST","/page-groups",group("선배들의 SSUL",null),csrf);
        assertThat(anonymous.get("/api/public/v1/menus").body()).isEqualTo(menus);
        assertThat(anonymous.get("/api/public/v1/pages/by-slug/about").body()).isEqualTo(page).doesNotContain("menuLabel","areaKind","contentTypeCode");
    }
}
