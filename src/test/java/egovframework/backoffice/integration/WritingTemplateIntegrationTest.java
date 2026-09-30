package egovframework.backoffice.integration;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.datasource.url=jdbc:h2:mem:writing-templates;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class WritingTemplateIntegrationTest {
 @LocalServerPort int port;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired PasswordEncoder encoder;
 static final String API="/api/admin/next/writing-templates",PASSWORD="Writing-Test42";
 HttpBrowser browser;String csrf;
 @BeforeEach void setup()throws Exception {
  jdbc.update("DELETE FROM writing_templates");jdbc.update("DELETE FROM activity_log");jdbc.update("DELETE FROM users");
  for(String role:List.of("SUPER_ADMIN","ADMIN","SUPPORTER"))jdbc.update("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,?,FALSE)",role.toLowerCase(Locale.ROOT)+"@writing.test",role,encoder.encode(PASSWORD),role);
  browser=login("super_admin");csrf=token(browser);
 }
 HttpBrowser login(String role)throws Exception{var b=new HttpBrowser(port);b.login(role+"@writing.test",PASSWORD,"/admin");return b;}
 String token(HttpBrowser b)throws Exception{return ok(b.get("/api/admin/next/bootstrap")).path("csrf").path("token").asText();}
 JsonNode ok(java.net.http.HttpResponse<String> r)throws Exception{assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return json.readTree(r.body());}
 ObjectNode input(String text){var ops=json.createArrayNode();ops.addObject().put("insert",text+"\n");return json.createObjectNode().put("name","프로젝트 후기").put("description","설명").put("richContent",json.createObjectNode().set("ops",ops).toString());}
 @Test void createReadUpdateStaleDeleteAndPrepareOnlyCopyBody()throws Exception {
  var d=ok(browser.json("POST",API+"/manage",input("경험").toString(),csrf));long id=d.path("info").path("id").asLong();
  assertThat(ok(browser.get(API+"/"+id))).isEqualTo(d);
  var before=jdbc.queryForList("SELECT * FROM posts");
  assertThat(ok(browser.json("POST",API+"/"+id+"/prepare","{\"revision\":0}",csrf))).isEqualTo(d);assertThat(jdbc.queryForList("SELECT * FROM posts")).isEqualTo(before);
  var changed=ok(browser.json("PUT",API+"/manage/"+id,input("새 경험").put("revision",0).toString(),csrf));assertThat(changed.path("info").path("revision").asInt()).isEqualTo(1);
  assertThat(browser.json("PUT",API+"/manage/"+id,input("stale").put("revision",0).toString(),csrf).statusCode()).isEqualTo(409);
  assertThat(browser.json("POST",API+"/"+id+"/prepare","{\"revision\":0}",csrf).statusCode()).isEqualTo(409);
  assertThat(browser.json("DELETE",API+"/manage/"+id,"{\"revision\":0,\"confirmed\":true}",csrf).statusCode()).isEqualTo(409);
  assertThat(browser.json("DELETE",API+"/manage/"+id,"{\"revision\":1}",csrf).statusCode()).isEqualTo(400);
  ok(browser.json("DELETE",API+"/manage/"+id,"{\"revision\":1,\"confirmed\":true}",csrf));assertThat(browser.get(API+"/"+id).statusCode()).isEqualTo(404);
 }
 @Test void writersCanUseButOnlyStructureAdministratorCanManageAndCsrfIsRequired()throws Exception {
  var d=ok(browser.json("POST",API+"/manage",input("후기 본문").toString(),csrf));long id=d.path("info").path("id").asLong();
  for(String role:List.of("admin","supporter")){var b=login(role);var t=token(b);ok(b.get(API));ok(b.get(API+"/"+id));ok(b.json("POST",API+"/"+id+"/prepare","{\"revision\":0}",t));
   assertThat(b.get("/admin/design/writing-templates").statusCode()).isEqualTo(200);
   assertThat(b.json("POST",API+"/manage",input("권한 없음").toString(),t).statusCode()).isEqualTo(403);
   assertThat(b.json("PUT",API+"/manage/"+id,input("권한 없음").put("revision",0).toString(),t).statusCode()).isEqualTo(403);
   assertThat(b.json("DELETE",API+"/manage/"+id,"{\"revision\":0,\"confirmed\":true}",t).statusCode()).isEqualTo(403);
  }
  assertThat(browser.json("POST",API+"/manage",input("csrf").toString(),null).statusCode()).isEqualTo(403);
  assertThat(browser.json("POST",API+"/"+id+"/prepare","{\"revision\":0}",null).statusCode()).isEqualTo(403);
 }
 @Test void invalidEmptyAndMediaDocumentsAreRejectedAndHtmlEscaped()throws Exception {
  for(String body:List.of("", "not json", "{\"ops\":[{\"insert\":\"\\n\"}]}","{\"ops\":[{\"insert\":{\"aicaImage\":{\"id\":1}}}]}"))assertThat(browser.json("POST",API+"/manage",input("x").put("richContent",body).toString(),csrf).statusCode()).isEqualTo(400);
  var d=ok(browser.json("POST",API+"/manage",input("<script>alert(1)</script>").toString(),csrf));assertThat(d.path("bodyHtml").asText()).contains("&lt;script&gt;").doesNotContain("<script>");
 }
}
