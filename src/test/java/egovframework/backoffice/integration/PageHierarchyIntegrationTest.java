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

/** V13 page hierarchy: placement, order, creation, deletion and home rules through the real HTTP stack. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:page-hierarchy-integration;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class PageHierarchyIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountMapper accounts;
    @Autowired PageService pages;
    @Autowired SiteService site;
    private static final String API="/api/admin/next";
    private static final String PASSWORD="Test-Hierarchy-Password42";
    private static final String SECTIONS="[{\"type\":\"TEXT\",\"heading\":\"소개\",\"body\":\"본문\",\"visible\":true}]";

    @BeforeEach void fixture() {
        for(String table:List.of("post_publication_media","post_media","post_publications","page_media","page_publications","page_block_identities","page_version_media","page_versions","site_pages","posts","media","site_menus","categories","site_links","activity_log","users"))jdbc.update("DELETE FROM "+table);
        jdbc.update("UPDATE site_settings SET setting_value='' WHERE setting_key IN ('logoId','homePageId')");
        String hash=encoder.encode(PASSWORD);
        for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,?,FALSE)",email(role),role,hash,role);
        long root=accounts.findByEmail(email("SUPER_ADMIN")).id();
        Object[][] fixture={{1L,"홈","home"},{65L,"인사교 소개","about"},{70L,"후기","reviews"},{71L,"FAQ","faq"},{72L,"오시는 길","location"}};
        for(Object[] p:fixture){
            jdbc.update("INSERT INTO site_pages(id,title,slug,sections_json,status,revision,author_id) VALUES(?,?,?,?,'PUBLISHED',2,?)",p[0],p[1],p[2],SECTIONS,root);
            jdbc.update("INSERT INTO page_publications(page_id,title,slug,sections_json,revision) SELECT id,title,slug,sections_json,revision FROM site_pages WHERE id=?",p[0]);
        }
        jdbc.update("UPDATE site_settings SET setting_value='1' WHERE setting_key='homePageId'");
        jdbc.execute("ALTER TABLE site_pages ALTER COLUMN id RESTART WITH 1000");
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) c->{try{db.migration.h2.V8__page_block_identity.upgrade(c);}catch(Exception e){throw new IllegalStateException(e);}return null;});
    }
    private String sections(long id){return jdbc.queryForObject("SELECT sections_json FROM site_pages WHERE id=?",String.class,id);}
    private static String email(String role){return role.toLowerCase(Locale.ROOT)+"@hierarchy.test";}
    private AccountPrincipal principal(String role){return new AccountPrincipal(accounts.findByEmail(email(role)));}
    private HttpBrowser login(String role)throws Exception{var b=new HttpBrowser(port);b.login(email(role),PASSWORD,"/admin");return b;}
    private String csrf(HttpBrowser b)throws Exception{return json.readTree(b.get(API+"/bootstrap").body()).path("csrf").path("token").asText();}
    private JsonNode body(HttpResponse<String> r)throws Exception{return json.readTree(r.body());}
    private HttpResponse<String> place(HttpBrowser b,String csrf,long id,Long parent,Long expected)throws Exception{
        var input=new LinkedHashMap<String,Object>();input.put("parentId",parent);input.put("expectedParentId",expected);
        return b.json("PUT",API+"/pages/"+id+"/placement",json.writeValueAsString(input),csrf);
    }
    private HttpResponse<String> order(HttpBrowser b,String csrf,Long parent,List<Long> ids)throws Exception{
        var input=new LinkedHashMap<String,Object>();input.put("parentId",parent);input.put("pageIds",ids);
        return b.json("PUT",API+"/page-order",json.writeValueAsString(input),csrf);
    }
    /** "id:parent:order" in the order the API returns rows. */
    private List<String> layout(JsonNode rows){
        var out=new ArrayList<String>();
        for(var r:rows)out.add(r.path("id").asLong()+":"+(r.path("parentId").isNull()?"-":r.path("parentId").asText())+":"+r.path("sortOrder").asInt());
        return out;
    }
    private String failure(HttpResponse<String> r,int status)throws Exception{assertThat(r.statusCode()).as(r.body()).isEqualTo(status);return body(r).path("message").asText();}

    @Test void movingAPageRenumbersBothSiblingGroupsWithoutTouchingTheDocument()throws Exception {
        var root=login("SUPER_ADMIN");String csrf=csrf(root);
        jdbc.update("UPDATE site_pages SET sort_order=5 WHERE id=72");
        // Ties on sort_order fall back to id; the list comes in sibling order.
        assertThat(layout(body(root.get(API+"/pages")))).containsExactly("1:-:0","65:-:0","70:-:0","71:-:0","72:-:5");
        // Rows stay flat in (sort_order, id) order; the client groups children under their parent.
        var moved=place(root,csrf,70,65L,null);assertThat(moved.statusCode()).as(moved.body()).isEqualTo(200);
        assertThat(layout(body(moved))).containsExactly("1:-:0","70:65:0","65:-:1","71:-:2","72:-:3");
        assertThat(layout(body(place(root,csrf,71,65L,null)))).containsExactly("1:-:0","70:65:0","65:-:1","71:65:1","72:-:2");
        assertThat(jdbc.queryForList("SELECT revision FROM site_pages WHERE id IN (70,71) ORDER BY id",Long.class)).containsExactly(2L,2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log WHERE action='페이지 위치 변경' AND detail LIKE '후기: 최상위 → 인사교 소개'",Integer.class)).isEqualTo(1);
        // Back to the top level: the page goes to the end and the old siblings close the gap.
        assertThat(layout(body(place(root,csrf,70,null,65L)))).containsExactly("1:-:0","71:65:0","65:-:1","72:-:2","70:-:3");
        // The same parent is a no-op.
        assertThat(place(root,csrf,71,65L,65L).statusCode()).isEqualTo(200);
        // ADMIN reads the hierarchy.
        var admin=login("ADMIN");
        assertThat(layout(body(admin.get(API+"/pages")))).contains("71:65:0");
        var boot=json.readTree(admin.get(API+"/bootstrap").body());
        assertThat(layout(boot.path("pages"))).contains("71:65:0");assertThat(boot.path("homePageId").asLong()).isEqualTo(1);
    }

    @Test void placementEnforcesDepthCyclesHomeAndStaleViews()throws Exception {
        var root=login("SUPER_ADMIN");String csrf=csrf(root);
        assertThat(place(root,csrf,70,65L,null).statusCode()).isEqualTo(200);
        // Current operating limit (V14): three levels; a fourth is refused.
        assertThat(place(root,csrf,72,70L,null).statusCode()).isEqualTo(200);
        assertThat(failure(place(root,csrf,71,72L,null),400)).contains("최대 3단계","그 페이지 아래에는 더 둘 수 없습니다");
        assertThat(failure(place(root,csrf,65,71L,null),400)).contains("최대 3단계","이 페이지와 그 하위 페이지");
        assertThat(failure(place(root,csrf,65,70L,null),400)).contains("자기 하위 페이지");
        assertThat(failure(place(root,csrf,72,72L,70L),400)).contains("자기 자신");
        assertThat(failure(place(root,csrf,1,65L,null),400)).contains("홈(첫 화면) 페이지는 최상위");
        assertThat(failure(place(root,csrf,72,1L,70L),400)).contains("홈(첫 화면) 페이지 아래");
        assertThat(failure(place(root,csrf,72,9999L,70L),400)).contains("상위 페이지를 찾을 수 없습니다");
        // The operator saw 70 at the top level, but it is already under 65.
        var stale=place(root,csrf,70,71L,null);assertThat(stale.statusCode()).isEqualTo(409);assertThat(body(stale).path("code").asText()).isEqualTo("REVISION_CONFLICT");
        assertThat(failure(place(root,csrf,9999,null,null),404)).isNotBlank();
        assertThat(jdbc.queryForList("SELECT id FROM site_pages WHERE parent_id IS NOT NULL ORDER BY id",Long.class)).containsExactly(70L,72L);
    }

    @Test void siblingOrderIsSavedContiguouslyOnlyForTheExactSiblingSet()throws Exception {
        var root=login("SUPER_ADMIN");String csrf=csrf(root);
        place(root,csrf,70,65L,null);place(root,csrf,71,65L,null);
        assertThat(layout(body(order(root,csrf,65L,List.of(71L,70L))))).containsSubsequence("71:65:0","70:65:1");
        assertThat(layout(body(order(root,csrf,null,List.of(72L,65L,1L))))).containsSubsequence("72:-:0","65:-:1","1:-:2");
        assertThat(failure(order(root,csrf,65L,List.of(71L)),400)).contains("목록이 바뀌었습니다");
        assertThat(failure(order(root,csrf,65L,List.of(71L,72L)),400)).contains("목록이 바뀌었습니다");
        assertThat(failure(order(root,csrf,65L,List.of(71L,71L)),400)).contains("목록이 바뀌었습니다");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log WHERE action='페이지 순서 변경'",Integer.class)).isEqualTo(2);
    }

    @Test void onlySiteManagersChangeTheHierarchy()throws Exception {
        for(String role:List.of("ADMIN","SUPPORTER")){
            var b=login(role);String csrf=csrf(b);
            for(var r:List.of(place(b,csrf,70,65L,null),order(b,csrf,null,List.of(1L,65L,70L,71L,72L)))){
                assertThat(r.statusCode()).as(role+" "+r.body()).isEqualTo(403);assertThat(body(r).path("code").asText()).isEqualTo("FORBIDDEN_OR_CSRF");
            }
        }
        var root=login("SUPER_ADMIN");
        assertThat(place(root,null,70,65L,null).statusCode()).isEqualTo(403);
        // Without a session there is no CSRF token either: the write is refused before authentication, reads get 401.
        var anonymous=new HttpBrowser(port);
        assertThat(place(anonymous,null,70,65L,null).statusCode()).isEqualTo(403);
        assertThat(anonymous.get(API+"/pages").statusCode()).isEqualTo(401);
        assertThatThrownBy(()->pages.place(principal("ADMIN"),70,65L,null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_pages WHERE parent_id IS NOT NULL",Integer.class)).isZero();
    }

    @Test void newPagesChooseAParentAndGoToTheEndOfTheirSiblings()throws Exception {
        var root=login("SUPER_ADMIN");String csrf=csrf(root);place(root,csrf,70,65L,null);
        var created=root.post("/admin/pages/save-json",Map.of("title","새 하위","sectionsJson","[]","parentId","65"));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(200);long id=body(created).path("id").asLong();
        assertThat(jdbc.queryForMap("SELECT parent_id,sort_order FROM site_pages WHERE id=?",id)).containsEntry("PARENT_ID",65L).containsEntry("SORT_ORDER",1);
        var top=root.post("/admin/pages/save-json",Map.of("title","새 최상위","sectionsJson","[]"));
        assertThat(jdbc.queryForMap("SELECT parent_id,sort_order FROM site_pages WHERE id=?",body(top).path("id").asLong())).containsEntry("PARENT_ID",null).containsEntry("SORT_ORDER",4);
        assertThat(body(root.post("/admin/pages/save-json",Map.of("title","홈 아래","sectionsJson","[]","parentId","1"))).path("error").asText()).contains("홈(첫 화면) 페이지 아래");
        var third=root.post("/admin/pages/save-json",Map.of("title","셋째 단계","sectionsJson","[]","parentId","70"));
        assertThat(third.statusCode()).as(third.body()).isEqualTo(200);
        assertThat(body(root.post("/admin/pages/save-json",Map.of("title","넷째 단계","sectionsJson","[]","parentId",body(third).path("id").asText()))).path("error").asText()).contains("최대 3단계");
        var revision=jdbc.queryForObject("SELECT revision FROM site_pages WHERE id=72",Long.class);
        assertThat(body(root.post("/admin/pages/save-json",Map.of("id","72","revision",""+revision,"title","오시는 길","sectionsJson",sections(72),"parentId","65"))).path("error").asText()).contains("사이트 구조 화면에서 옮깁니다");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_pages WHERE title IN ('홈 아래','넷째 단계')",Integer.class)).isZero();
    }

    @Test void aParentWithChildPagesCannotBeDeletedButSelectedChildrenCanGoFirst()throws Exception {
        var root=login("SUPER_ADMIN");String csrf=csrf(root);place(root,csrf,70,65L,null);place(root,csrf,71,65L,null);
        var impact=body(root.get(API+"/pages/65/delete-impact"));
        assertThat(impact.path("uses").toString()).contains("하위 페이지 · 후기","하위 페이지 · FAQ");
        var refused=root.json("DELETE",API+"/pages/65",json.writeValueAsString(Map.of("revision",2,"confirmed",true)),csrf);
        assertThat(failure(refused,400)).contains("하위 페이지 2개");
        for(long child:List.of(70L,71L))assertThat(root.json("DELETE",API+"/pages/"+child,json.writeValueAsString(Map.of("revision",2,"confirmed",true)),csrf).statusCode()).isEqualTo(200);
        assertThat(root.json("DELETE",API+"/pages/65",json.writeValueAsString(Map.of("revision",2,"confirmed",true)),csrf).statusCode()).isEqualTo(200);
    }

    @Test void theHomeSettingOnlyAcceptsATopLevelPageWithoutChildren()throws Exception {
        var root=login("SUPER_ADMIN");String csrf=csrf(root);place(root,csrf,70,65L,null);
        var actor=principal("SUPER_ADMIN");
        for(String candidate:List.of("65","70"))
            assertThatThrownBy(()->site.saveSettings(actor,"basic",Map.of("siteName","사이트","description","","contactEmail","","homePageId",candidate))).isInstanceOf(BusinessException.class).hasMessageContaining("최상위이면서 하위 페이지가 없는");
        site.saveSettings(actor,"basic",Map.of("siteName","사이트","description","","contactEmail","","homePageId","72"));
        assertThat(failure(place(root,csrf,72,65L,null),400)).contains("홈(첫 화면) 페이지는 최상위");
        assertThat(place(root,csrf,1,65L,null).statusCode()).isEqualTo(200);
    }

    @Test void documentSavesRestoresAndPublicationLeaveTheHierarchyAlone()throws Exception {
        var root=login("SUPER_ADMIN");String csrf=csrf(root);place(root,csrf,70,65L,null);
        var actor=principal("ADMIN");
        pages.save(actor,70L,2L,"후기 수정","reviews",sections(70),"publish");
        pages.restoreDraft(actor,70L,3L,"후기 복구",sections(70));
        assertThat(jdbc.queryForMap("SELECT parent_id,sort_order,title FROM site_pages WHERE id=70")).containsEntry("PARENT_ID",65L).containsEntry("SORT_ORDER",0).containsEntry("TITLE","후기 복구");
        // A private parent does not withdraw its published child; the public API has no hierarchy yet.
        pages.unpublish(actor,65L,2L);
        assertThat(jdbc.queryForObject("SELECT status FROM site_pages WHERE id=70",String.class)).isEqualTo("PUBLISHED");
        var publicPage=new HttpBrowser(port).get("/api/public/v1/pages/by-slug/reviews");
        assertThat(publicPage.statusCode()).isEqualTo(200);assertThat(publicPage.body()).doesNotContain("parentId","sortOrder");
        assertThat(new HttpBrowser(port).get("/api/public/v1/pages/by-slug/about").statusCode()).isEqualTo(404);
    }
}
