package egovframework.backoffice.integration;

import com.fasterxml.jackson.databind.*;
import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.classification.ClassificationModels.Selection;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.common.BusinessException;
import egovframework.backoffice.mvp.post.PostService;
import egovframework.backoffice.mvp.security.*;
import egovframework.backoffice.mvp.version.*;
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

/**
 * Legacy category retirement, code preparation: no new category use, "all types" query mode with the meaning
 * of the old category mode without a category, read-only categories, restore compatibility and topic addition.
 * REVIEW and FAQ are content types here, never categories.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:legacy-category-retirement;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class LegacyCategoryRetirementIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountMapper accounts;
    @Autowired PageService pages;
    @Autowired PageTemplateService templates;
    @Autowired PostService posts;
    @Autowired VersionHistoryService history;
    @Autowired VersionRestoreService restores;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    private static final String API="/api/admin/next";
    private static final String PASSWORD="Test-Category-Password42";
    // The old POSTS block: no sourceMode and no category, i.e. every published post, newest first, 6 at a time.
    private static final String LEGACY_BLOCK="{\"type\":\"POSTS\",\"heading\":\"소식\",\"body\":\"\",\"imageId\":null,\"categoryId\":null,\"link\":\"\",\"label\":\"\",\"visible\":true,\"id\":\"block_11111111-1111-4111-8111-111111111111\",\"schemaVersion\":2,\"variation\":\"default\"}";
    private long root;

    @BeforeEach void fixture() {
        for(String table:List.of("site_structure_publication_pages","site_structure_publications","post_publication_topics","post_publication_cohorts","post_topics","post_cohorts","post_versions","post_publication_media","post_media","post_publications","post_trash","posts","page_media","page_publications","page_block_identities","page_version_media","page_versions","site_pages","page_template_versions","page_templates","media","site_menus","categories","site_links","activity_log","users"))jdbc.update("DELETE FROM "+table);
        jdbc.update("DELETE FROM content_type_topics WHERE topic_id IN (SELECT id FROM topics WHERE code LIKE 'GENERAL_%')");
        jdbc.update("DELETE FROM topics WHERE code LIKE 'GENERAL_%'");
        jdbc.update("UPDATE site_settings SET setting_value='' WHERE setting_key IN ('logoId','homePageId')");
        String hash=encoder.encode(PASSWORD);
        for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,?,FALSE)",email(role),role,hash,role);
        root=accounts.findByEmail(email("SUPER_ADMIN")).id();
        jdbc.update("INSERT INTO categories(id,name,sort_order) VALUES(1,'공지사항',0),(2,'교육 소식',1)");
        // Published GENERAL and REVIEW posts without a category, and one GENERAL draft in category 2.
        jdbc.update("INSERT INTO posts(id,title,content,author_id,status,revision,content_type_code) VALUES(201,'일반 글','본문',?,'PUBLISHED',1,'GENERAL'),(202,'후기 글','본문',?,'PUBLISHED',1,'REVIEW')",root,root);
        jdbc.update("INSERT INTO post_publications(post_id,title,content,revision,content_type_code,type_name_snapshot) VALUES(201,'일반 글','본문',1,'GENERAL','일반'),(202,'후기 글','본문',1,'REVIEW','후기')");
        jdbc.update("INSERT INTO posts(id,title,content,author_id,status,revision,content_type_code,category_id) VALUES(233,'아아','본문',?,'DRAFT',0,'GENERAL',2)",root);
        jdbc.update("INSERT INTO site_pages(id,title,slug,sections_json,status,revision,author_id) VALUES(1,'홈','home',?,'PUBLISHED',1,?)","["+LEGACY_BLOCK+"]",root);
        jdbc.update("INSERT INTO page_publications(page_id,title,slug,sections_json,revision) SELECT id,title,slug,sections_json,revision FROM site_pages WHERE id=1");
        jdbc.update("INSERT INTO site_menus(label,kind,target_id,url,visible,sort_order) VALUES('공지사항','CATEGORY',1,'',TRUE,0)");
        jdbc.update("INSERT INTO topics(code,name) VALUES('GENERAL_SAMPLE','예시')");
        jdbc.update("INSERT INTO content_type_topics(type_code,topic_id) SELECT 'GENERAL',id FROM topics WHERE code='GENERAL_SAMPLE'");
        jdbc.execute("ALTER TABLE site_pages ALTER COLUMN id RESTART WITH 1000");
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) c->{try{db.migration.h2.V8__page_block_identity.upgrade(c);}catch(Exception e){throw new IllegalStateException(e);}return null;});
    }
    private static String email(String role){return role.toLowerCase(Locale.ROOT)+"@category.test";}
    private AccountPrincipal principal(String role){return new AccountPrincipal(accounts.findByEmail(email(role)));}
    private HttpBrowser login(String role)throws Exception{var b=new HttpBrowser(port);b.login(email(role),PASSWORD,"/admin");return b;}
    private String csrf(HttpBrowser b)throws Exception{return json.readTree(b.get(API+"/bootstrap").body()).path("csrf").path("token").asText();}
    private HttpResponse<String> send(HttpBrowser b,String method,String path,Object value,String csrf)throws Exception{return b.json(method,API+path,json.writeValueAsString(value),csrf);}
    private String failure(HttpResponse<String> r,int status)throws Exception{assertThat(r.statusCode()).as(r.body()).isEqualTo(status);return json.readTree(r.body()).path("message").asText();}
    private List<Long> publicBlockPosts()throws Exception{
        var page=json.readTree(new HttpBrowser(port).get("/api/public/v1/pages/by-slug/home").body());
        var ids=new ArrayList<Long>();for(var item:page.path("blocks").get(0).path("data").path("posts").path("items"))ids.add(item.path("id").asLong());return ids;
    }
    private String sectionsWith(String block)throws Exception{return "["+block+"]";}
    private String allTypes(int limit){return LEGACY_BLOCK.replace("\"variation\":\"default\"}","\"variation\":\"default\",\"sourceMode\":\"query\",\"query\":{\"typeCode\":null,\"cohortIds\":[],\"topicIds\":[],\"sort\":\"LATEST\",\"limit\":"+limit+"},\"manual\":null}");}

    @Test void allTypesKeepsTheMeaningOfTheOldCategoryModeWithoutACategory()throws Exception {
        var actor=principal("SUPER_ADMIN");
        var before=publicBlockPosts();
        assertThat(before).containsExactlyInAnyOrder(201L,202L);
        var revision=jdbc.queryForObject("SELECT revision FROM site_pages WHERE id=1",Long.class);
        pages.save(actor,1L,revision,"홈","home",sectionsWith(allTypes(6)),"publish");
        assertThat(publicBlockPosts()).isEqualTo(before);
        var page=json.readTree(new HttpBrowser(port).get("/api/public/v1/pages/by-slug/home").body()).path("blocks").get(0).path("data");
        assertThat(page.path("sourceMode").asText()).isEqualTo("query");assertThat(page.path("posts").path("total").asLong()).isEqualTo(2);
        // Topics belong to a type, so "all types" takes none; cohorts stay possible.
        long sampleTopic=jdbc.queryForObject("SELECT id FROM topics WHERE code='GENERAL_SAMPLE'",Long.class);
        String withTopic=allTypes(6).replace("\"topicIds\":[]","\"topicIds\":["+sampleTopic+"]");
        long current=jdbc.queryForObject("SELECT revision FROM site_pages WHERE id=1",Long.class);
        assertThatThrownBy(()->pages.save(actor,1L,current,"홈","home",sectionsWith(withTopic),"save")).isInstanceOf(BusinessException.class).hasMessageContaining("전체 유형에서는 주제");
    }

    @Test void noCategoryIsNewlySetOnPostsBlocksOrTemplates()throws Exception {
        var actor=principal("SUPER_ADMIN");
        long revision=jdbc.queryForObject("SELECT revision FROM site_pages WHERE id=1",Long.class);
        // The unchanged old block (no category) still saves; giving it a category does not.
        pages.save(actor,1L,revision,"홈","home",sectionsWith(LEGACY_BLOCK),"save");
        long next=jdbc.queryForObject("SELECT revision FROM site_pages WHERE id=1",Long.class);
        assertThatThrownBy(()->pages.save(actor,1L,next,"홈","home",sectionsWith(LEGACY_BLOCK.replace("\"categoryId\":null","\"categoryId\":2")),"save"))
            .isInstanceOf(BusinessException.class).hasMessageContaining("레거시 카테고리는 새로 지정할 수 없습니다");
        // A block that already has a category keeps it while unchanged.
        jdbc.update("UPDATE site_pages SET sections_json=? WHERE id=1","["+LEGACY_BLOCK.replace("\"categoryId\":null","\"categoryId\":2")+"]");
        long kept=jdbc.queryForObject("SELECT revision FROM site_pages WHERE id=1",Long.class);
        pages.save(actor,1L,kept,"홈","home",sectionsWith(LEGACY_BLOCK.replace("\"categoryId\":null","\"categoryId\":2")),"save");
        var sections=pages.sections(jdbc.queryForObject("SELECT sections_json FROM site_pages WHERE id=1",String.class));
        assertThatThrownBy(()->templates.save(actor,null,null,"새 템플릿","",true,sections)).isInstanceOf(BusinessException.class).hasMessageContaining("레거시 카테고리");
        var clean=pages.sections(sectionsWith(allTypes(6)));
        assertThat(templates.save(actor,null,null,"새 템플릿","",true,clean)).isNotNull();
    }

    @Test void postsKeepOrClearACategoryButNeverTakeANewOne()throws Exception {
        var actor=principal("SUPER_ADMIN");
        var general=new Selection("GENERAL",List.of(),List.of());
        assertThatThrownBy(()->posts.save(actor,null,null,"새 글","본문",1L,List.of(),"save",null,general)).isInstanceOf(BusinessException.class).hasMessageContaining("레거시 카테고리");
        assertThatThrownBy(()->posts.save(actor,201L,1L,"일반 글","본문",2L,List.of(),"save",null,general)).isInstanceOf(BusinessException.class).hasMessageContaining("레거시 카테고리");
        // Editing a post that already has category 2 without touching it still works.
        posts.save(actor,233L,0L,"아아 수정","본문",2L,List.of(),"save",null,general);
        assertThat(jdbc.queryForObject("SELECT category_id FROM posts WHERE id=233",Long.class)).isEqualTo(2L);
        // A snapshot that still has category 2 is readable in history, but restoring it never brings the category back.
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(t->
            history.capture(accounts.findByEmail(email("SUPER_ADMIN")),VersionKind.POST,233L,"MANUAL_DRAFT",null,null,false));
        long version=jdbc.queryForObject("SELECT MAX(id) FROM post_versions WHERE post_id=233",Long.class);
        assertThat(jdbc.queryForObject("SELECT snapshot_json FROM post_versions WHERE id=?",String.class,version)).contains("\"categoryId\":2","교육 소식");
        long revision=jdbc.queryForObject("SELECT revision FROM posts WHERE id=233",Long.class);
        posts.save(actor,233L,revision,"아아 수정","본문",null,List.of(),"save",null,general);
        assertThat(jdbc.queryForObject("SELECT category_id FROM posts WHERE id=233",Long.class)).isNull();
        long cleared=jdbc.queryForObject("SELECT revision FROM posts WHERE id=233",Long.class);
        restores.restore(actor,VersionKind.POST,233L,version,cleared,UUID.randomUUID().toString());
        assertThat(jdbc.queryForObject("SELECT category_id FROM posts WHERE id=233",Long.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT title FROM posts WHERE id=233",String.class)).isEqualTo("아아 수정");
        // REVIEW posts are a content type, not a category: nothing about them changed.
        assertThat(jdbc.queryForObject("SELECT content_type_code FROM posts WHERE id=202",String.class)).isEqualTo("REVIEW");
    }

    @Test void categoriesAreReadOnlyWhileExistingReferencesKeepWorking()throws Exception {
        var admin=login("SUPER_ADMIN");String csrf=csrf(admin);
        assertThat(failure(send(admin,"POST","/categories",Map.of("name","새 분류"),csrf),400)).contains("레거시 카테고리");
        assertThat(failure(send(admin,"PUT","/categories/1",Map.of("name","바뀜"),csrf),400)).contains("레거시 카테고리");
        assertThat(failure(send(admin,"DELETE","/categories/1",Map.of(),csrf),400)).contains("레거시 카테고리");
        assertThat(failure(send(admin,"PUT","/categories/order",Map.of("ids",List.of(2,1)),csrf),400)).contains("레거시 카테고리");
        assertThat(admin.get(API+"/categories").statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories",Integer.class)).isEqualTo(2);
        // The CATEGORY menu and the category list API stay public until the structure takes over.
        var anonymous=new HttpBrowser(port);
        assertThat(anonymous.get("/api/public/v1/menus").body()).contains("\"kind\":\"CATEGORY\"","공지사항");
        assertThat(anonymous.get("/api/public/v1/posts?categoryId=1").statusCode()).isEqualTo(200);
    }

    @Test void siteManagersAddTopicsAsOperatingData()throws Exception {
        var admin=login("SUPER_ADMIN");String csrf=csrf(admin);
        Map<String,Object> notice=Map.of("typeCode","GENERAL","code","GENERAL_NOTICE","name","공지","description","");
        var catalog=json.readTree(send(admin,"POST","/classifications/topics",notice,csrf).body());
        long id=-1;for(var t:catalog.path("topics"))if("GENERAL_NOTICE".equals(t.path("code").asText()))id=t.path("id").asLong();
        assertThat(id).isPositive();
        long topic=id;
        assertThat(catalog.path("allowedTopics").toString()).contains("{\"typeCode\":\"GENERAL\",\"topicId\":"+topic+"}");
        assertThat(failure(send(admin,"POST","/classifications/topics",notice,csrf),400)).contains("이미 등록된 주제 코드");
        assertThat(failure(send(admin,"POST","/classifications/topics",Map.of("typeCode","NOTICE","code","GENERAL_X","name","x"),csrf),400)).contains("콘텐츠 유형");
        assertThat(failure(send(admin,"POST","/classifications/topics",Map.of("typeCode","GENERAL","code","general_x","name","x"),csrf),400)).contains("주제 코드");
        assertThat(failure(send(admin,"POST","/classifications/topics",Map.of("typeCode","GENERAL","code","GENERAL_Y","name","공지"),csrf),400)).contains("같은 이름");
        for(String role:List.of("ADMIN","SUPPORTER")){var b=login(role);
            assertThat(send(b,"POST","/classifications/topics",Map.of("typeCode","GENERAL","code","GENERAL_Z","name","z"),csrf(b)).statusCode()).as(role).isEqualTo(403);}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log WHERE action='주제 추가'",Integer.class)).isEqualTo(1);
        // The new topic works like any other: a GENERAL post can take it, and a query block can filter by it.
        var actor=principal("SUPER_ADMIN");
        posts.save(actor,201L,1L,"일반 글","본문",null,List.of(),"save",null,new Selection("GENERAL",List.of(),List.of(topic)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_topics WHERE post_id=201 AND topic_id=?",Integer.class,topic)).isEqualTo(1);
    }
}
