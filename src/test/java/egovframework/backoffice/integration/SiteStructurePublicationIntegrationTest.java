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

/** V14 step 2: 구성 게시, the public structure, derived menus with the site_menus fallback, import and republish. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:site-structure-publication;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class SiteStructurePublicationIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountMapper accounts;
    @Autowired SiteService site;
    private static final String API="/api/admin/next";
    private static final String PASSWORD="Test-Structure-Password42";
    private static final String SECTIONS="[{\"type\":\"TEXT\",\"heading\":\"소개\",\"body\":\"본문\",\"visible\":true}]";
    private long root,category,link;

    @BeforeEach void fixture() {
        for(String table:List.of("site_structure_publication_pages","site_structure_publications","post_publication_media","post_media","post_publications","page_media","page_publications","page_block_identities","page_version_media","page_versions","site_pages","posts","media","site_menus","categories","site_links","activity_log","users"))jdbc.update("DELETE FROM "+table);
        jdbc.update("UPDATE site_settings SET setting_value='' WHERE setting_key IN ('logoId','homePageId')");
        String hash=encoder.encode(PASSWORD);
        for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,?,FALSE)",email(role),role,hash,role);
        root=accounts.findByEmail(email("SUPER_ADMIN")).id();
        Object[][] fixture={{1L,"홈","home"},{65L,"소개","about"},{70L,"후기","reviews"},{72L,"오시는 길","location"}};
        for(Object[] p:fixture){
            jdbc.update("INSERT INTO site_pages(id,title,slug,sections_json,status,revision,author_id,sort_order) VALUES(?,?,?,?,'PUBLISHED',2,?,?)",p[0],p[1],p[2],SECTIONS,root,((Long)p[0]).intValue());
            jdbc.update("INSERT INTO page_publications(page_id,title,slug,sections_json,revision) SELECT id,title,slug,sections_json,revision FROM site_pages WHERE id=?",p[0]);
        }
        jdbc.update("UPDATE site_settings SET setting_value='1' WHERE setting_key='homePageId'");
        jdbc.execute("ALTER TABLE site_pages ALTER COLUMN id RESTART WITH 1000");
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) c->{try{db.migration.h2.V8__page_block_identity.upgrade(c);}catch(Exception e){throw new IllegalStateException(e);}return null;});
        jdbc.update("INSERT INTO categories(name,sort_order) VALUES('공지',0)");
        category=jdbc.queryForObject("SELECT id FROM categories",Long.class);
        jdbc.update("INSERT INTO site_menus(label,kind,target_id,url,visible,sort_order) VALUES('소개','PAGE',65,'',TRUE,0)");
        jdbc.update("INSERT INTO site_menus(label,kind,target_id,url,visible,sort_order) VALUES('공지','CATEGORY',?,'',TRUE,1)",category);
        jdbc.update("INSERT INTO site_menus(label,kind,target_id,url,visible,sort_order) VALUES('블로그','LINK',NULL,'https://blog.example.com',TRUE,2)");
        link=jdbc.queryForObject("SELECT id FROM site_menus WHERE kind='LINK'",Long.class);
    }
    private static String email(String role){return role.toLowerCase(Locale.ROOT)+"@structure.test";}
    private AccountPrincipal principal(String role){return new AccountPrincipal(accounts.findByEmail(email(role)));}
    private HttpBrowser login(String role)throws Exception{var b=new HttpBrowser(port);b.login(email(role),PASSWORD,"/admin");return b;}
    private String csrf(HttpBrowser b)throws Exception{return json.readTree(b.get(API+"/bootstrap").body()).path("csrf").path("token").asText();}
    private JsonNode body(HttpResponse<String> r)throws Exception{assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
    private String failure(HttpResponse<String> r,int status)throws Exception{assertThat(r.statusCode()).as(r.body()).isEqualTo(status);return json.readTree(r.body()).path("message").asText();}
    private HttpResponse<String> send(HttpBrowser b,String method,String path,Object value,String csrf)throws Exception{return b.json(method,API+path,json.writeValueAsString(value),csrf);}
    private Map<String,Object> composition(String type,boolean visible,String label,String name){var m=new LinkedHashMap<String,Object>();m.put("contentTypeCode",type);m.put("menuVisible",visible);m.put("menuLabel",label);m.put("name",name);return m;}
    private Map<String,Object> publish(JsonNode status){var m=new HashMap<String,Object>();m.put("fingerprint",status.path("draftFingerprint").asText());m.put("expectedLatestId",latestId(status));return m;}
    private static Long latestId(JsonNode status){return status.path("latest").isNull()?null:status.path("latest").path("id").asLong();}
    private JsonNode status(HttpBrowser b)throws Exception{return body(b.get(API+"/site-structure"));}
    private JsonNode publicJson(String path)throws Exception{var r=new HttpBrowser(port).get("/api/public/v1"+path);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
    private static List<String> menuLine(JsonNode menus){var out=new ArrayList<String>();for(var m:menus)out.add(m.path("key").asText()+"<"+m.path("parentKey").asText("")+">"+m.path("kind").asText()+":"+m.path("label").asText()+":"+m.path("slug").asText(""));return out;}
    private long group(HttpBrowser b,String csrf,String name,Long parent)throws Exception{
        var m=new HashMap<String,Object>();m.put("name",name);m.put("parentId",parent);
        for(var r:body(send(b,"POST","/page-groups",m,csrf)))if(name.equals(r.path("title").asText()))return r.path("id").asLong();throw new AssertionError(name);
    }

    @Test void theManagedMenusStayPublicUntilTheFirstStructurePublication()throws Exception {
        var admin=login("SUPER_ADMIN");String csrf=csrf(admin);
        String before=new HttpBrowser(port).get("/api/public/v1/menus").body();
        assertThat(menuLine(json.readTree(before))).containsExactly("menu:"+jdbc.queryForObject("SELECT id FROM site_menus WHERE kind='PAGE'",Long.class)+"<>PAGE:소개:about",
            "menu:"+jdbc.queryForObject("SELECT id FROM site_menus WHERE kind='CATEGORY'",Long.class)+"<>CATEGORY:공지:","menu:"+link+"<>LINK:블로그:");
        var structure=publicJson("/structure");
        assertThat(structure.path("publishedAt").isNull()).isTrue();assertThat(structure.path("items")).isEmpty();
        // Composition changes alone never touch the public menus.
        send(admin,"PUT","/pages/72/composition",composition(null,true,null,null),csrf);
        assertThat(new HttpBrowser(port).get("/api/public/v1/menus").body()).isEqualTo(before);
        var status=status(admin);
        assertThat(status.path("latest").isNull()).isTrue();assertThat(status.path("changed").asBoolean()).isTrue();
        assertThat(menuLine(status.path("managedMenusLeaving"))).extracting(s->s.replaceAll("^menu:\\d+","")).containsExactly("<>PAGE:소개:about","<>CATEGORY:공지:");
        assertThat(status.path("warnings").toString()).contains("2개(소개, 공지)","현재 메뉴에서 가져오기","카테고리 이관");
        // Import preview, then apply: menu pages become menu-visible areas; category and link rows stay.
        var plan=body(admin.get(API+"/site-structure/menu-import"));
        assertThat(plan.path("changes").toString()).contains("소개","메뉴 노출");
        assertThat(plan.path("notes").toString()).contains("카테고리 메뉴 '공지'","외부 링크 '블로그'");
        assertThat(failure(send(admin,"POST","/site-structure/menu-import",Map.of("fingerprint","stale"),csrf),409)).contains("다른 작업에서");
        var applied=body(send(admin,"POST","/site-structure/menu-import",Map.of("fingerprint",plan.path("draftFingerprint").asText()),csrf));
        for(var r:applied.path("pages"))if(r.path("id").asLong()==65)assertThat(r.path("menuVisible").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_menus",Integer.class)).isEqualTo(3);
        assertThat(new HttpBrowser(port).get("/api/public/v1/menus").body()).isEqualTo(before);
        assertThat(failure(send(admin,"POST","/site-structure/menu-import",Map.of("fingerprint",applied.path("plan").path("draftFingerprint").asText()),csrf),400)).contains("가져올 변경이 없습니다");
        assertThat(menuLine(status(admin).path("managedMenusLeaving"))).hasSize(1).allMatch(s->s.contains("CATEGORY"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log WHERE action='현재 메뉴에서 가져오기'",Integer.class)).isEqualTo(1);
    }

    @Test void publishingDerivesMenusAndStructureAndResolvesPagesAtReadTime()throws Exception {
        var admin=login("SUPER_ADMIN");String csrf=csrf(admin);
        long g=group(admin,csrf,"묶음A",null);
        send(admin,"PUT","/pages/70/placement",Map.of("parentId",g),csrf);
        send(admin,"PUT","/pages/"+g+"/composition",composition(null,true,null,"묶음A"),csrf);
        send(admin,"PUT","/pages/70/composition",composition("REVIEW",true,"후기 모음",null),csrf);
        send(admin,"PUT","/pages/65/composition",composition(null,true,null,null),csrf);
        var status=status(admin);
        assertThat(status.path("errors")).isEmpty();
        var stale=publish(status);stale.put("fingerprint","0".repeat(64));
        assertThat(failure(send(admin,"POST","/site-structure/publications",stale,csrf),409)).contains("다른 작업에서");
        var wrongLatest=publish(status);wrongLatest.put("expectedLatestId",5);
        assertThat(failure(send(admin,"POST","/site-structure/publications",wrongLatest,csrf),409)).contains("다른 작업에서");
        var published=body(send(admin,"POST","/site-structure/publications",publish(status),csrf));
        assertThat(published.path("changed").asBoolean()).isFalse();assertThat(published.path("latest").path("reason").asText()).isEqualTo("PUBLISH");
        assertThat(failure(send(admin,"POST","/site-structure/publications",publish(published),csrf),400)).contains("달라진 점이 없습니다");
        assertThat(menuLine(publicJson("/menus"))).containsExactly("area:65<>PAGE:소개:about","area:"+g+"<>GROUP:묶음A:","area:70<area:"+g+">PAGE:후기 모음:reviews","menu:"+link+"<>LINK:블로그:");
        var menus=publicJson("/menus");
        assertThat(menus.get(0).path("apiHref").asText()).isEqualTo("/api/public/v1/pages/65");
        assertThat(menus.get(1).path("pageId").isNull()).isTrue();assertThat(menus.get(1).path("apiHref").isNull()).isTrue();
        // The structure keeps pages hidden from the menu; they stay reachable by URL.
        var structure=publicJson("/structure");
        assertThat(structure.path("publishedAt").isNull()).isFalse();
        assertThat(structure.path("items").findValuesAsText("label")).containsExactly("홈","소개","오시는 길","묶음A","후기 모음");
        assertThat(structure.path("items").get(3).path("children").get(0).path("contentTypeCode").asText()).isEqualTo("REVIEW");
        assertThat(structure.toString()).doesNotContain("sectionsJson","status");
        assertThat(new HttpBrowser(port).get("/api/public/v1/pages/by-slug/location").statusCode()).isEqualTo(200);
        // A new page publication (new slug and title) shows without republishing the structure.
        jdbc.update("UPDATE page_publications SET slug='about-us',title='소개 새 제목' WHERE page_id=65");
        assertThat(menuLine(publicJson("/menus")).get(0)).isEqualTo("area:65<>PAGE:소개 새 제목:about-us");
        // Withdrawing the only page in a group removes both from the menu at once.
        assertThat(send(admin,"POST","/pages/70/unpublish",Map.of("revision",2),csrf).statusCode()).isEqualTo(200);
        assertThat(menuLine(publicJson("/menus"))).containsExactly("area:65<>PAGE:소개 새 제목:about-us","menu:"+link+"<>LINK:블로그:");
        // Draft changes wait for the next publication.
        send(admin,"PUT","/pages/65/composition",composition(null,false,null,null),csrf);
        var changed=status(admin);
        assertThat(changed.path("changed").asBoolean()).isTrue();assertThat(changed.path("changes").toString()).contains("메뉴 숨김");
        assertThat(publicJson("/menus")).hasSize(2);
        // After the first publication only external links are edited in the menu screen.
        var actor=principal("SUPER_ADMIN");
        assertThatThrownBy(()->site.menu(actor,null,"소개","PAGE",65L,"",true)).isInstanceOf(BusinessException.class).hasMessageContaining("외부 링크만");
        long categoryMenu=jdbc.queryForObject("SELECT id FROM site_menus WHERE kind='CATEGORY'",Long.class);
        assertThatThrownBy(()->site.menu(actor,categoryMenu,"공지","CATEGORY",category,"",true)).isInstanceOf(BusinessException.class).hasMessageContaining("외부 링크만");
        assertThatThrownBy(()->site.menu(actor,categoryMenu,"공지","LINK",null,"https://x.example.com",true)).isInstanceOf(BusinessException.class).hasMessageContaining("외부 링크만");
        var menuInput=new HashMap<String,Object>();menuInput.put("label","새 링크");menuInput.put("kind","LINK");menuInput.put("url","https://new.example.com");menuInput.put("visible",true);
        assertThat(send(admin,"POST","/menus",menuInput,csrf).statusCode()).isEqualTo(200);
        assertThat(send(admin,"PUT","/menus/"+link,Map.of("label","블로그2","kind","LINK","url","https://blog.example.com","visible",true),csrf).statusCode()).isEqualTo(200);
        assertThat(menuLine(publicJson("/menus"))).containsExactly("area:65<>PAGE:소개 새 제목:about-us","menu:"+link+"<>LINK:블로그2:",
            "menu:"+jdbc.queryForObject("SELECT id FROM site_menus WHERE label='새 링크'",Long.class)+"<>LINK:새 링크:");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log WHERE action='사이트 구성 게시'",Integer.class)).isEqualTo(1);
    }

    private HttpResponse<String> membership(HttpBrowser b,long id,boolean in,String csrf)throws Exception{return send(b,"PUT","/pages/"+id+"/structure-membership",Map.of("inStructure",in),csrf);}
    private static Map<String,Object> top(Long expected){var m=new HashMap<String,Object>();m.put("parentId",null);m.put("expectedParentId",expected);return m;}

    @Test void everyAreaOfTheLatestPublicationIsProtectedUntilRemovedFromTheStructureAndRepublished()throws Exception {
        var admin=login("SUPER_ADMIN");String csrf=csrf(admin);
        jdbc.update("DELETE FROM site_menus WHERE kind<>'LINK'");
        long g=group(admin,csrf,"묶음B",null);
        send(admin,"PUT","/pages/70/placement",Map.of("parentId",g),csrf);
        for(long id:List.of(g,70L))send(admin,"PUT","/pages/"+id+"/composition",composition(null,true,null,id==g?"묶음B":null),csrf);
        var first=body(send(admin,"POST","/site-structure/publications",publish(status(admin)),csrf));
        long firstId=latestId(first);
        // 72 is hidden from the menu but still in the published structure: hidden is not removed.
        assertThat(publicJson("/structure").findValuesAsText("id")).contains("72");
        assertThat(failure(send(admin,"DELETE","/pages/72",Map.of("revision",2,"confirmed",true),csrf),400)).contains("게시된 사이트 구성","구조에서 뺀");
        assertThat(body(admin.get(API+"/pages/72/delete-impact")).path("uses").toString()).contains("게시된 사이트 구성");
        // Removal rules: no children left in the structure, no content-work link.
        assertThat(failure(membership(admin,g,false,csrf),400)).contains("하위 영역이 1개");
        send(admin,"PUT","/pages/70/composition",composition("REVIEW",true,null,null),csrf);
        assertThat(failure(membership(admin,70,false,csrf),400)).contains("콘텐츠 작업에 연결");
        send(admin,"PUT","/pages/70/composition",composition(null,true,null,null),csrf);
        body(send(admin,"PUT","/pages/70/placement",top(g),csrf));
        var rows=body(membership(admin,72,false,csrf));
        for(var r:rows)if(r.path("id").asLong()==72)assertThat(r.path("inStructure").asBoolean()).isFalse();
        body(membership(admin,g,false,csrf));
        // Nothing goes under a removed area, and a removed area takes no content-work link.
        var under=new HashMap<String,Object>();under.put("parentId",g);under.put("expectedParentId",null);
        assertThat(failure(send(admin,"PUT","/pages/70/placement",under,csrf),400)).contains("구성에서 제거된 영역 아래");
        var child=new HashMap<String,Object>();child.put("name","하위");child.put("parentId",g);
        assertThat(failure(send(admin,"POST","/page-groups",child,csrf),400)).contains("구성에서 제거된 영역 아래");
        assertThat(failure(send(admin,"PUT","/pages/72/composition",composition("FAQ",false,null,null),csrf),400)).contains("구성에서 제거된 영역에는");
        // A child comes back only under a parent that is in the structure.
        body(membership(admin,72,true,csrf));body(send(admin,"PUT","/pages/65/placement",Map.of("parentId",72),csrf));
        body(membership(admin,65,false,csrf));body(membership(admin,72,false,csrf));
        assertThat(failure(membership(admin,65,true,csrf),400)).contains("상위 영역이 구성에서 제거");
        body(send(admin,"PUT","/pages/65/placement",top(72L),csrf));body(membership(admin,65,true,csrf));
        // Removal is a draft change: the public structure keeps 72 until the next publication; the page is kept.
        assertThat(publicJson("/structure").findValuesAsText("id")).contains("72",""+g);
        assertThat(failure(send(admin,"DELETE","/pages/72",Map.of("revision",2,"confirmed",true),csrf),400)).contains("게시된 사이트 구성");
        assertThat(status(admin).path("changes").toString()).contains("구성에서 제거");
        var second=body(send(admin,"POST","/site-structure/publications",publish(status(admin)),csrf));
        assertThat(publicJson("/structure").findValuesAsText("id")).doesNotContain("72",""+g);
        assertThat(new HttpBrowser(port).get("/api/public/v1/pages/by-slug/location").statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_structure_publication_pages WHERE publication_id=?",Integer.class,latestId(second))).isEqualTo(3);
        assertThat(send(admin,"DELETE","/pages/72",Map.of("revision",2,"confirmed",true),csrf).statusCode()).isEqualTo(200);
        assertThat(send(admin,"DELETE","/pages/"+g,Map.of("revision",0,"confirmed",true),csrf).statusCode()).isEqualTo(200);
        String draft=status(admin).path("draftFingerprint").asText();
        // Publishing the first structure again leaves out what no longer exists and keeps the draft as it is.
        assertThat(failure(send(admin,"POST","/site-structure/publications/"+firstId+"/republish",Map.of("expectedLatestId",firstId),csrf),409)).contains("다른 작업에서");
        var again=body(send(admin,"POST","/site-structure/publications/"+firstId+"/republish",Map.of("expectedLatestId",latestId(second)),csrf));
        assertThat(again.path("removed").toString()).contains("삭제된 페이지 #72","묶음B");
        assertThat(again.path("status").path("latest").path("reason").asText()).isEqualTo("REPUBLISH");
        assertThat(again.path("status").path("latest").path("sourcePublicationId").asLong()).isEqualTo(firstId);
        assertThat(again.path("status").path("draftFingerprint").asText()).isEqualTo(draft);
        assertThat(menuLine(publicJson("/menus"))).containsExactly("area:70<>PAGE:후기:reviews","menu:"+link+"<>LINK:블로그:");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log WHERE detail LIKE '%구성에서 제거'",Integer.class)).isGreaterThanOrEqualTo(3);
        var history=body(admin.get(API+"/site-structure/publications"));
        assertThat(history).hasSize(3);assertThat(history.get(0).path("reason").asText()).isEqualTo("REPUBLISH");
        assertThat(failure(send(admin,"POST","/site-structure/publications/"+latestId(again.path("status"))+"/republish",Map.of("expectedLatestId",latestId(again.path("status"))),csrf),400)).contains("이미 현재");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log WHERE action='사이트 구성 다시 게시'",Integer.class)).isEqualTo(1);
    }

    @Test void thePublicStructureAndMenusNeverShowAnAreaThatNoLongerExists()throws Exception {
        var admin=login("SUPER_ADMIN");String csrf=csrf(admin);
        jdbc.update("DELETE FROM site_menus WHERE kind<>'LINK'");
        long g=group(admin,csrf,"묶음C",null);
        send(admin,"PUT","/pages/70/placement",Map.of("parentId",g),csrf);
        for(long id:List.of(g,70L))send(admin,"PUT","/pages/"+id+"/composition",composition(null,true,null,id==g?"묶음C":null),csrf);
        body(send(admin,"POST","/site-structure/publications",publish(status(admin)),csrf));
        assertThat(menuLine(publicJson("/menus"))).containsExactly("area:"+g+"<>GROUP:묶음C:","area:70<area:"+g+">PAGE:후기:reviews","menu:"+link+"<>LINK:블로그:");
        // The service refuses this deletion; data changed outside it must still never surface as a dangling area.
        jdbc.update("UPDATE site_pages SET parent_id=NULL WHERE id=70");jdbc.update("DELETE FROM site_pages WHERE id=?",g);
        assertThat(menuLine(publicJson("/menus"))).containsExactly("area:70<>PAGE:후기:reviews","menu:"+link+"<>LINK:블로그:");
        var structure=publicJson("/structure");
        assertThat(structure.findValuesAsText("id")).doesNotContain(""+g);
        assertThat(structure.findValuesAsText("title")).doesNotContain("","묶음C");
        assertThat(structure.path("items").findValuesAsText("id")).contains("70");
    }

    @Test void blockingProblemsStopThePublication()throws Exception {
        var admin=login("SUPER_ADMIN");String csrf=csrf(admin);
        jdbc.update("UPDATE site_pages SET content_type_code='REVIEW',menu_visible=TRUE WHERE id IN (65,70)");
        var status=status(admin);
        assertThat(status.path("errors").toString()).contains("같은 콘텐츠 유형");
        assertThat(failure(send(admin,"POST","/site-structure/publications",publish(status),csrf),400)).contains("같은 콘텐츠 유형");
        jdbc.update("UPDATE site_pages SET content_type_code=NULL WHERE id=70");
        jdbc.update("UPDATE content_types SET active=FALSE WHERE code='REVIEW'");
        assertThat(failure(send(admin,"POST","/site-structure/publications",publish(status(admin)),csrf),400)).contains("사용하지 않는 콘텐츠 유형");
        jdbc.update("UPDATE content_types SET active=TRUE WHERE code='REVIEW'");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_structure_publications",Integer.class)).isZero();
    }

    @Test void onlySiteManagersUseTheStructurePublication()throws Exception {
        for(String role:List.of("ADMIN","SUPPORTER")){
            var b=login(role);String csrf=csrf(b);
            assertThat(b.get(API+"/site-structure").statusCode()).as(role).isEqualTo(403);
            assertThat(b.get(API+"/site-structure/publications").statusCode()).as(role).isEqualTo(403);
            assertThat(b.get(API+"/site-structure/menu-import").statusCode()).as(role).isEqualTo(403);
            assertThat(send(b,"POST","/site-structure/publications",Map.of("fingerprint","x"),csrf).statusCode()).as(role).isEqualTo(403);
            assertThat(send(b,"POST","/site-structure/menu-import",Map.of("fingerprint","x"),csrf).statusCode()).as(role).isEqualTo(403);
        }
        var anonymous=new HttpBrowser(port);
        assertThat(anonymous.json("POST","/api/public/v1/structure","{}",null).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_structure_publications",Integer.class)).isZero();
    }
}
