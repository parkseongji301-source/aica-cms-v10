package egovframework.backoffice.integration;

import com.fasterxml.jackson.databind.*;
import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.classification.ClassificationService;
import egovframework.backoffice.mvp.classification.ClassificationModels.Selection;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static egovframework.backoffice.mvp.cms.CmsStore.values;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:classification-integration;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ClassificationIntegrationTest {
    static final String API="/api/admin/next/posts/";
    static final String PASSWORD="Classification-Test-Password42";
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountMapper accounts;
    @Autowired PostService posts;
    @Autowired PageService pages;
    @Autowired SiteService site;
    @Autowired CmsStore cms;
    @Autowired ClassificationService classifications;
    long id,ownId,pageId;

    @BeforeEach void fixture() {
        for(String table:List.of("post_publication_media","post_media","post_publications","page_media","page_publications","site_pages","posts","media","site_menus","categories","activity_log","users","content_type_topics","cohorts","topics"))jdbc.update("DELETE FROM "+table);
        jdbc.update("UPDATE content_types SET active=TRUE");
        jdbc.update("UPDATE content_types SET name='후기' WHERE code='REVIEW'");
        String hash=encoder.encode(PASSWORD);
        for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,?,FALSE)",email(role),role,hash,role);
        jdbc.update("INSERT INTO categories(id,name) VALUES(11,'공지사항'),(12,'기존 생활')");
        // Verification vocabulary only: migration does not infer or register IA terms.
        jdbc.update("INSERT INTO cohorts(id,code,name) VALUES(101,'C6','6기'),(102,'C7','7기')");
        jdbc.update("INSERT INTO topics(id,code,name) VALUES(201,'REVIEW_LIFE','생활'),(202,'REVIEW_PROJECT','프로젝트'),(203,'SHARED_CLASS','수업'),(204,'FAQ_LIFE','생활')");
        jdbc.update("INSERT INTO content_type_topics VALUES('REVIEW',201),('REVIEW',202),('REVIEW',203),('FAQ',203),('FAQ',204)");
        id=posts.save(actor("ADMIN"),null,null,"기존 발행 글","기존 본문",11L,List.of(),"publish",null,
            new Selection("REVIEW",List.of(102L),List.of(201L)));
        ownId=posts.save(actor("SUPPORTER"),null,null,"서포터 글","본문",12L,List.of(),"save",null);
        pageId=pages.save(actor("SUPER_ADMIN"),null,null,"기존 분류 페이지","existing-category",
            "[{\"type\":\"POSTS\",\"heading\":\"분류 글\",\"categoryId\":11,\"visible\":true}]","publish");
        jdbc.update("INSERT INTO site_menus(label,kind,target_id,url,visible,sort_order) SELECT name,'CATEGORY',id,'',TRUE,1 FROM categories WHERE id=11");
    }
    String email(String role){return role.toLowerCase(Locale.ROOT)+"@classification.test";}
    AccountPrincipal actor(String role){return new AccountPrincipal(accounts.findByEmail(email(role)));}
    HttpBrowser login(String role)throws Exception {var b=new HttpBrowser(port);b.login(email(role),PASSWORD,"/admin");return b;}
    JsonNode ok(HttpResponse<String> response)throws Exception {assertThat(response.statusCode()).as(response.body()).isEqualTo(200);return json.readTree(response.body());}
    JsonNode read(HttpBrowser b)throws Exception{return ok(b.get(API+id));}
    JsonNode publication(HttpBrowser b)throws Exception{return ok(b.get(API+id+"/publication"));}
    String token(HttpBrowser b)throws Exception{return ok(b.get("/api/admin/next/bootstrap")).path("csrf").path("token").asText();}
    Map<String,Object> fields(JsonNode post){
        var result=new LinkedHashMap<String,Object>();
        for(String key:List.of("revision","title","content","richContent","categoryId","mediaIds"))result.put(key,json.convertValue(post.get(key),Object.class));
        return result;
    }
    Map<String,Object> selection(String type,List<Long> cohorts,List<Long> topics){return Map.of("typeCode",type,"cohortIds",cohorts,"topicIds",topics);}
    HttpResponse<String> put(HttpBrowser b,Map<String,Object> body)throws Exception{return b.json("PUT",API+id,json.writeValueAsString(body),token(b));}
    JsonNode save(HttpBrowser b,String type,List<Long> cohorts,List<Long> topics)throws Exception {
        var body=fields(read(b));body.put("classification",selection(type,cohorts,topics));return ok(put(b,body));
    }
    void error(HttpResponse<String> response,int status,String code)throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(json.readTree(response.body()).path("code").asText()).isEqualTo(code);
    }
    void publishLegacy(HttpBrowser b)throws Exception {
        var p=read(b);
        HttpBrowser.redirect(b.post("/admin/posts/"+id+"/edit",Map.of("revision",p.path("revision").asText(),
            "title",p.path("title").asText(),"content",p.path("content").asText(),"categoryId",p.path("categoryId").asText(),"action","publish")),"/admin/posts/"+id);
    }
    List<CmsModels.PublicPost> published(List<Long> cohorts,List<Long> topics){
        return cms.all("publicPosts",values("categoryId",11L,"typeCode","REVIEW","cohortIds",cohorts,"topicIds",topics,"limit",20,"offset",0));
    }

    @Test void existingCategoryMenusPageBlocksAndBothEditorRoutesKeepOriginalIds()throws Exception {
        var b=login("ADMIN");var before=read(b);
        for(String mode:List.of("manage","structure")) {
            assertThat(b.get("/admin-next/posts/"+id+"/edit?view="+mode).statusCode()).isEqualTo(200);
            assertThat(ok(b.get(API+id+"?view="+mode))).isEqualTo(before);
        }
        save(b,"FAQ",List.of(),List.of(204L));
        assertThat(read(b).path("categoryId").asLong()).isEqualTo(11);
        assertThat(jdbc.queryForObject("SELECT category_id FROM post_publications WHERE post_id=?",Long.class,id)).isEqualTo(11);
        assertThat(login("SUPER_ADMIN").get("/admin/menus").body()).contains("공지사항");
        assertThat(login("SUPER_ADMIN").get("/admin/categories").body()).contains("공지사항","기존 생활");
        assertThat(b.get("/admin/pages/"+pageId+"/edit").body()).contains("기존 분류 페이지");
        assertThat(jdbc.queryForObject("SELECT sections_json FROM site_pages WHERE id=?",String.class,pageId)).contains("\"categoryId\":11");
        assertThat(jdbc.queryForObject("SELECT target_id FROM site_menus WHERE kind='CATEGORY'",Long.class)).isEqualTo(11);
        assertThat(b.get("/admin/posts?categoryId=11").body()).contains("기존 발행 글");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts",Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_pages",Long.class)).isEqualTo(1);
        assertThat(published(List.of(),List.of(201L))).extracting(CmsModels.PublicPost::id).containsExactly(id);
    }

    @Test void multipleAssignmentsRoundTripAndNamesCanCollideWithoutSharingIds()throws Exception {
        var b=login("ADMIN");var before=read(b);
        var saved=save(b,"REVIEW",List.of(102L,101L),List.of(203L,201L));
        assertThat(saved.path("classification").path("cohortIds").toString()).isEqualTo("[101,102]");
        assertThat(saved.path("classification").path("topicIds").toString()).isEqualTo("[201,203]");
        assertThat(saved.path("revision").asLong()).isEqualTo(before.path("revision").asLong()+1);
        assertThat(read(b)).isEqualTo(saved);
        var catalog=ok(b.get("/api/admin/next/classifications"));
        assertThat(catalog.path("types").size()).isEqualTo(5);
        assertThat(catalog.path("topics").findValuesAsText("name")).containsExactly("생활","프로젝트","수업","생활");
        assertThat(classifications.draft(ownId).typeCode()).isEqualTo("GENERAL");
        assertThat(classifications.draft(ownId).topicIds()).isEmpty();
        var cleared=save(b,"GENERAL",List.of(),List.of());
        assertThat(cleared.path("classification").path("topicIds").size()).isZero();
        assertThat(cleared.path("classification").path("cohortIds").size()).isZero();
    }

    @Test void duplicateAndInvalidAssignmentsRejectWholeSave()throws Exception {
        var b=login("ADMIN");var before=read(b);
        for(Object invalid:Arrays.asList(null,Map.of("typeCode","REVIEW"),
                selection("REVIEW",List.of(101L,101L),List.of()),selection("REVIEW",List.of(),List.of(201L,201L)),
                selection("REVIEW",List.of(),List.of(204L)),selection("UNKNOWN",List.of(),List.of()),
                selection("REVIEW",List.of(999L),List.of()),selection("REVIEW",List.of(),List.of(999L)),
                selection("FAQ",List.of(),List.of(201L)),selection("REVIEW",List.of(-1L),List.of()))) {
            var body=fields(before);body.put("title","실패하면 저장되면 안 됨");body.put("classification",invalid);
            error(put(b,body),400,"VALIDATION_ERROR");assertThat(read(b)).isEqualTo(before);
        }
        assertThatThrownBy(()->jdbc.update("INSERT INTO post_topics(post_id,topic_id) VALUES(?,201)",id))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void draftClassificationCannotChangePublishedMembershipUntilRepublished()throws Exception {
        var b=login("ADMIN");var before=publication(b);
        save(b,"REVIEW",List.of(101L),List.of(202L));
        assertThat(publication(b)).isEqualTo(before);
        assertThat(published(List.of(102L),List.of(201L))).extracting(CmsModels.PublicPost::id).containsExactly(id);
        assertThat(published(List.of(),List.of(202L))).isEmpty();
        publishLegacy(b);
        assertThat(publication(b).path("classification").path("topicIds").toString()).isEqualTo("[202]");
        assertThat(publication(b).path("classification").path("cohortIds").toString()).isEqualTo("[101]");
        assertThat(published(List.of(),List.of(201L))).isEmpty();
        assertThat(published(List.of(101L),List.of(202L))).extracting(CmsModels.PublicPost::id).containsExactly(id);
        assertThat(read(b).path("pending").asBoolean()).isFalse();
    }

    @Test void legacyHtmlJsonAndPhase3ARequestsPreserveNewClassification()throws Exception {
        var b=login("ADMIN");save(b,"REVIEW",List.of(101L,102L),List.of(201L,202L));
        assertThat(b.get("/admin/posts/"+id+"/edit").body()).contains("현재 초안 분류","6기","7기","생활","프로젝트","/admin-next/posts/"+id+"/edit")
            .doesNotContain("name=\"typeCode\"","name=\"cohortIds\"","name=\"topicIds\"");
        var taxonomy=read(b).path("classification");
        var body=fields(read(b));body.put("title","구 React 저장");ok(put(b,body));
        assertThat(read(b).path("classification")).isEqualTo(taxonomy);
        var p=read(b);
        HttpBrowser.redirect(b.post("/admin/posts/"+id+"/edit",Map.of("revision",p.path("revision").asText(),"title","구 폼 저장","content","본문","categoryId","11","action","save")),"/admin/posts/"+id);
        assertThat(read(b).path("classification")).isEqualTo(taxonomy);
        p=read(b);
        ok(b.post("/admin/posts/save-json",Map.of("id",String.valueOf(id),"revision",p.path("revision").asText(),"title","구 자동저장","content","본문","categoryId","11","action","save")));
        assertThat(read(b).path("classification")).isEqualTo(taxonomy);
        publishLegacy(b);assertThat(publication(b).path("classification")).isEqualTo(taxonomy);
        p=read(b);
        ok(b.post("/admin/posts/save-json",Map.of("id",String.valueOf(id),"revision",p.path("revision").asText(),"title","구 JSON 발행","content","본문","categoryId","11","action","publish")));
        assertThat(publication(b).path("classification")).isEqualTo(taxonomy);
    }

    @Test void staleClassificationCannotOverwriteDraftOrPublication()throws Exception {
        var b=login("ADMIN");var old=fields(read(b));var published=publication(b);
        assertThatThrownBy(()->posts.save(actor("ADMIN"),id,null,"버전 없는 저장","본문",11L,List.of(),"save",null,new Selection("FAQ",List.of(),List.of(204L))))
            .hasMessageContaining("저장 버전이 필요");
        var saved=save(b,"REVIEW",List.of(101L),List.of(202L));
        old.put("classification",selection("FAQ",List.of(),List.of(204L)));
        error(put(b,old),409,"REVISION_CONFLICT");
        assertThat(read(b)).isEqualTo(saved);assertThat(publication(b)).isEqualTo(published);
        assertThatThrownBy(()->posts.save(actor("ADMIN"),id,((Number)old.get("revision")).longValue(),"오래된 발행","본문",11L,List.of(),"publish",null,new Selection("FAQ",List.of(),List.of(204L))))
            .hasMessageContaining("다른 작업에서 변경");
        assertThat(read(b)).isEqualTo(saved);assertThat(publication(b)).isEqualTo(published);
    }

    @Test void databaseFailureRollsBackDraftSnapshotAssignmentsAndRevisionTogether()throws Exception {
        var b=login("ADMIN");var before=read(b);var publication=publication(b);
        long activities=jdbc.queryForObject("SELECT COUNT(*) FROM activity_log",Long.class);
        jdbc.execute("ALTER TABLE post_publication_topics ADD CONSTRAINT test_reject_project CHECK(topic_id<>202)");
        try {
            assertThatThrownBy(()->posts.save(actor("ADMIN"),id,before.path("revision").asLong(),"롤백","수정 본문",12L,List.of(),"publish",null,new Selection("REVIEW",List.of(101L),List.of(202L))))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(read(b)).isEqualTo(before);assertThat(publication(b)).isEqualTo(publication);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log",Long.class)).isEqualTo(activities);
        } finally {jdbc.execute("ALTER TABLE post_publication_topics DROP CONSTRAINT test_reject_project");}
    }

    @Test void previewValidatesUnsavedClassificationWithoutWriting()throws Exception {
        var b=login("ADMIN");var before=read(b);var published=publication(b);var body=fields(before);
        body.put("classification",selection("REVIEW",List.of(101L),List.of(202L)));
        var preview=ok(b.json("POST",API+id+"/preview",json.writeValueAsString(body),token(b)));
        assertThat(preview.path("classification").path("topicIds").toString()).isEqualTo("[202]");
        assertThat(ok(b.get(API+id+"/preview")).path("classification")).isEqualTo(before.path("classification"));
        body.put("classification",selection("FAQ",List.of(),List.of(201L)));
        error(b.json("POST",API+id+"/preview",json.writeValueAsString(body),token(b)),400,"VALIDATION_ERROR");
        assertThat(read(b)).isEqualTo(before);assertThat(publication(b)).isEqualTo(published);
    }

    @Test void publicationNamesAreFrozenAndArchivedTermsKeepPublishedMembership()throws Exception {
        var b=login("ADMIN");jdbc.update("UPDATE topics SET name='생활 새 이름',active=FALSE WHERE id=201");
        jdbc.update("UPDATE cohorts SET name='7기 새 이름',active=FALSE WHERE id=102");
        jdbc.update("UPDATE content_types SET name='후기 새 이름',active=FALSE WHERE code='REVIEW'");
        assertThat(publication(b).path("classification").path("typeName").asText()).isEqualTo("후기");
        assertThat(publication(b).path("classification").path("topics").get(0).path("name").asText()).isEqualTo("생활");
        assertThat(publication(b).path("classification").path("cohorts").get(0).path("name").asText()).isEqualTo("7기");
        assertThat(read(b).path("classification").path("topics").get(0).path("name").asText()).isEqualTo("생활 새 이름");
        save(b,"REVIEW",List.of(102L),List.of(201L)); // Retain archived selections, not newly assigned.
        assertThat(published(List.of(102L),List.of(201L))).hasSize(1);
        save(b,"GENERAL",List.of(),List.of());
        var body=fields(read(b));body.put("classification",selection("REVIEW",List.of(102L),List.of(201L)));
        error(put(b,body),400,"VALIDATION_ERROR");
    }

    @Test void publishedFiltersUseOrWithinAxisAndAndAcrossAxesWithoutDuplicates()throws Exception {
        var b=login("ADMIN");save(b,"REVIEW",List.of(101L,102L),List.of(201L,203L));publishLegacy(b);
        assertThat(published(List.of(101L,102L),List.of(201L,203L))).extracting(CmsModels.PublicPost::id).containsExactly(id);
        assertThat(published(List.of(999L),List.of(201L))).isEmpty();
        Long count=cms.one("publicPostCount",values("categoryId",11L,"typeCode","REVIEW","cohortIds",List.of(101L,102L),"topicIds",List.of(201L,203L)));
        assertThat(count).isEqualTo(1L);
        List<CmsModels.PublicPost> legacy=cms.all("publicPosts",values("categoryId",11L,"limit",20,"offset",0));
        assertThat(legacy).hasSize(1);
    }

    @Test void authenticationOwnershipAndCsrfApplyToClassificationAndSnapshotRoutes()throws Exception {
        var admin=login("ADMIN");var supporter=login("SUPPORTER");
        error(new HttpBrowser(port).get("/api/admin/next/classifications"),401,"AUTH_REQUIRED");
        ok(supporter.get("/api/admin/next/classifications"));
        error(supporter.get(API+id+"/publication"),403,"FORBIDDEN");
        var body=fields(read(admin));body.put("classification",selection("FAQ",List.of(),List.of(204L)));
        error(supporter.json("PUT",API+id,json.writeValueAsString(body),token(supporter)),403,"FORBIDDEN");
        error(admin.json("PUT",API+id,json.writeValueAsString(body),null),403,"FORBIDDEN_OR_CSRF");
        var own=ok(supporter.get(API+ownId));var ownFields=fields(own);ownFields.put("classification",selection("FAQ",List.of(101L),List.of(204L)));
        ok(supporter.json("PUT",API+ownId,json.writeValueAsString(ownFields),token(supporter)));
        // 5B-2A: SUPPORTER retains draft ownership; ADMIN publishes it.
        var p=posts.get(actor("SUPPORTER"),ownId);
        assertThatThrownBy(()->posts.save(actor("SUPPORTER"),ownId,p.revision(),p.title(),p.content(),p.categoryId(),List.of(),"publish",p.richContent())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        posts.save(actor("ADMIN"),ownId,p.revision(),p.title(),p.content(),p.categoryId(),List.of(),"publish",p.richContent());
        assertThat(ok(supporter.get(API+ownId+"/publication")).path("classification").path("typeCode").asText()).isEqualTo("FAQ");
        assertThat(supporter.json("POST","/api/admin/next/classifications","{}",token(supporter)).statusCode()).isEqualTo(405);
    }

    @Test void unpublishAndDeleteCannotLeaveVisibleClassificationOrOrphanLinks()throws Exception {
        var b=login("ADMIN");posts.unpublish(actor("ADMIN"),id,posts.get(actor("ADMIN"),id).revision());
        assertThat(published(List.of(),List.of())).isEmpty();error(b.get(API+id+"/publication"),404,"NOT_FOUND");
        posts.delete(actor("SUPER_ADMIN"),id,posts.get(actor("SUPER_ADMIN"),id).revision());
        assertThat(published(List.of(),List.of())).isEmpty();
        posts.purgeTrash(actor("SUPER_ADMIN"),id,posts.trashed(actor("SUPER_ADMIN"),id).revision());
        for(String table:List.of("post_cohorts","post_topics","post_publication_cohorts","post_publication_topics"))
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE post_id=?",Long.class,id)).isZero();
    }

    List<Long> resultIds(JsonNode result){return java.util.stream.StreamSupport.stream(result.path("items").spliterator(),false).map(p->p.path("id").asLong()).toList();}

    @Test void adminFiltersApplyOrWithinAxesAndAndAcrossAxesWithoutDuplicateRowsOrCount()throws Exception {
        var b=login("ADMIN");save(b,"REVIEW",List.of(101L,102L),List.of(201L,203L));
        long faq=posts.save(actor("ADMIN"),null,null,"FAQ 생활","본문",11L,List.of(),"save",null,new Selection("FAQ",List.of(101L),List.of(204L)));
        String base="/api/admin/next/posts?";
        var union=ok(b.get(base+"typeCodes=REVIEW,FAQ&cohortIds=101,102&topicIds=201,203,204"));
        assertThat(union.path("total").asLong()).isEqualTo(2);assertThat(resultIds(union)).containsExactly(faq,id);
        var intersect=ok(b.get(base+"typeCodes=REVIEW,FAQ&cohortIds=102&topicIds=204"));
        assertThat(intersect.path("total").asLong()).isZero();assertThat(resultIds(intersect)).isEmpty();
        var review=ok(b.get(base+"typeCodes=REVIEW&cohortIds=101&cohortIds=102&topicIds=201&topicIds=203&categoryId=11"));
        assertThat(review.path("total").asLong()).isEqualTo(1);assertThat(resultIds(review)).containsExactly(id);
        assertThat(review.path("items").get(0).path("classification").path("topicIds").toString()).isEqualTo("[201,203]");
        assertThat(resultIds(ok(b.get(base+"topicIds=204")))).containsExactly(faq);
        assertThat(resultIds(ok(b.get(base+"topicIds=201")))).containsExactly(id);
        assertThat(ok(b.get(base+"typeCodes=REVIEW&categoryId=12")).path("total").asLong()).isZero();
    }

    @Test void filteredPaginationCountMatchesDistinctResultsAndOwnership()throws Exception {
        var b=login("ADMIN");
        for(int n=0;n<12;n++)posts.save(actor("ADMIN"),null,null,"페이지 검사 "+n,"필터 본문",11L,List.of(),"save",null,new Selection("REVIEW",List.of(101L,102L),List.of(201L,203L)));
        String path="/api/admin/next/posts?typeCodes=REVIEW&cohortIds=101,102&topicIds=201,203&categoryId=11";
        var first=ok(b.get(path));var second=ok(b.get(path+"&page=1"));
        assertThat(first.path("total").asLong()).isEqualTo(13);assertThat(second.path("total")).isEqualTo(first.path("total"));
        assertThat(first.path("items").size()).isEqualTo(10);assertThat(second.path("items").size()).isEqualTo(3);
        var all=new ArrayList<>(resultIds(first));all.addAll(resultIds(second));assertThat(new HashSet<>(all)).hasSize(13);
        assertThat(ok(b.get(path+"&status=DRAFT")).path("total").asLong()).isEqualTo(12);
        assertThat(ok(b.get(path+"&q="+java.net.URLEncoder.encode("페이지 검사",java.nio.charset.StandardCharsets.UTF_8))).path("total").asLong()).isEqualTo(12);
        var supporter=login("SUPPORTER");assertThat(ok(supporter.get(path)).path("total").asLong()).isZero();
        var own=posts.get(actor("SUPPORTER"),ownId);
        posts.save(actor("SUPPORTER"),ownId,own.revision(),own.title(),own.content(),12L,List.of(),"save",null,new Selection("FAQ",List.of(101L),List.of(204L)));
        assertThat(resultIds(ok(supporter.get("/api/admin/next/posts?typeCodes=FAQ&cohortIds=101&topicIds=204")))).containsExactly(ownId);
        error(new HttpBrowser(port).get(path),401,"AUTH_REQUIRED");
    }

    @Test void listFiltersAndSummariesUseDraftEvenWhenStatusIsPublished()throws Exception {
        var b=login("ADMIN");var pub=publication(b);save(b,"FAQ",List.of(101L),List.of(204L));
        var result=ok(b.get("/api/admin/next/posts?status=PUBLISHED&typeCodes=FAQ&topicIds=204"));
        assertThat(resultIds(result)).containsExactly(id);
        assertThat(result.path("items").get(0).path("classification").path("typeCode").asText()).isEqualTo("FAQ");
        assertThat(ok(b.get("/api/admin/next/posts?status=PUBLISHED&typeCodes=REVIEW&topicIds=201")).path("total").asLong()).isZero();
        assertThat(publication(b)).isEqualTo(pub);assertThat(published(List.of(),List.of(201L))).hasSize(1);
        publishLegacy(b);assertThat(publication(b).path("classification").path("topicIds").toString()).isEqualTo("[204]");
    }

    @Test void malformedAndUnknownFiltersAreRejectedWithoutChangingData()throws Exception {
        var b=login("ADMIN");var before=read(b);
        for(String query:List.of("typeCodes=UNKNOWN","topicIds=9999","cohortIds=-1","topicIds=201,201"))error(b.get("/api/admin/next/posts?"+query),400,"VALIDATION_ERROR");
        error(b.get("/api/admin/next/posts?cohortIds=text"),400,"INVALID_PARAMETER");
        assertThat(read(b)).isEqualTo(before);
    }
}
