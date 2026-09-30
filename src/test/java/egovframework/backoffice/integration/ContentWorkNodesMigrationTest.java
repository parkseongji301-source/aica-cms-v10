package egovframework.backoffice.integration;
import egovframework.backoffice.mvp.operations.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static egovframework.backoffice.mvp.operations.FileDatabaseSafety.*;
import static org.assertj.core.api.Assertions.*;

/** V15 -> V16 runs only on a cold copy: one new empty CONTENT_WORK_NODES table, no existing table or row changed. */
class ContentWorkNodesMigrationTest {
 @TempDir Path dir;
 private Path v15(String name)throws Exception {
  Path base=dir.resolve(name);ClassificationMigrationTest.flyway(ClassificationMigrationTest.url(base),"15").migrate();
  Path file=Path.of(base+".mv.db").toRealPath();
  try(var c=DriverManager.getConnection(writerUrl(file),"sa","");var s=c.createStatement()){
   s.executeUpdate("INSERT INTO users(id,email,display_name,password_hash,role) VALUES(7,'root@nodes.test','관리자','x','SUPER_ADMIN')");
   s.executeUpdate("INSERT INTO site_pages(id,title,slug,sections_json,status,revision,author_id,menu_visible,content_type_code) VALUES(1,'홈','home','[]','PUBLISHED',3,7,TRUE,NULL),(70,'후기 전체','reviews','[]','DRAFT',1,7,FALSE,'REVIEW')");
   s.executeUpdate("INSERT INTO topics(id,code,name,description,active,sort_order) VALUES(95,'REVIEW_PROJECT','프로젝트','',TRUE,1)");
   s.executeUpdate("INSERT INTO content_type_topics(type_code,topic_id) VALUES('REVIEW',95)");
   s.executeUpdate("INSERT INTO site_structure_publications(id,snapshot_json,fingerprint,reason,published_by,publisher_name) VALUES(1,'{\"version\":1,\"areas\":[]}','f','PUBLISH',7,'관리자')");
   s.executeUpdate("INSERT INTO site_structure_publication_pages(publication_id,page_id) VALUES(1,1)");
  }
  return file;
 }
 private static List<String> rows(Path file,String sql)throws Exception {
  var out=new ArrayList<String>();
  try(var c=DriverManager.getConnection(readUrl(file),"sa","");var s=c.createStatement();var r=s.executeQuery(sql)){
   int n=r.getMetaData().getColumnCount();while(r.next()){var row=new StringJoiner("|");for(int i=1;i<=n;i++)row.add(String.valueOf(r.getObject(i)));out.add(row.toString());}
  }
  return out;
 }

 @Test void coldCopyGainsOnlyAnEmptyNodeTable()throws Exception {
  Path original=v15("original"),copy=dir.resolve("copy.mv.db");Files.copy(original,copy);String originalHash=hash(original);
  String pages="SELECT id,title,slug,status,revision,area_kind,content_type_code,menu_visible,menu_label,parent_id,sort_order,in_structure FROM site_pages ORDER BY id";
  var before=rows(original,pages);var topics=rows(original,"SELECT id,code,name,active FROM topics");var allowed=rows(original,"SELECT type_code,topic_id FROM content_type_topics");
  assertThatThrownBy(()->V16PromotionTool.plan(original,original,"jar","sa","")).hasMessageContaining("separate copy");
  var plan=V16PromotionTool.plan(copy,original,"jar","sa","");
  assertThat(plan.path("migrations").size()).isEqualTo(16);assertThat(plan.path("migrations").get(15).path("script").asText()).isEqualTo("V16__content_work_nodes.sql");
  assertThatThrownBy(()->V16PromotionTool.validatePlan(plan,"wrong","sa","")).hasMessageContaining("checksum");

  var receipt=V16PromotionTool.migrate(plan,"jar","plan","sa","");
  assertThat(receipt.path("status").asText()).isEqualTo("MIGRATED_V16");assertThat(receipt.path("after").path("history").size()).isEqualTo(16);
  assertThat(hash(original)).isEqualTo(originalHash);
  requireCurrentSchema(copy,"sa","");
  assertThat(rows(copy,pages)).isEqualTo(before);
  assertThat(rows(copy,"SELECT id,code,name,active FROM topics")).isEqualTo(topics);
  assertThat(rows(copy,"SELECT type_code,topic_id FROM content_type_topics")).isEqualTo(allowed);
  assertThat(rows(copy,"SELECT publication_id,page_id FROM site_structure_publication_pages")).containsExactly("1|1");
  assertThat(rows(copy,"SELECT COUNT(*) FROM content_work_nodes")).containsExactly("0");
  // The table is general: page and topic references, cascade on page delete, no uniqueness or type rule in the DB.
  try(var c=DriverManager.getConnection(writerUrl(copy),"sa","");var s=c.createStatement()){
   assertThat(s.executeUpdate("INSERT INTO content_work_nodes(page_id,name,topic_id,sort_order) VALUES(70,'프로젝트 후기',95,0),(70,'프로젝트 후기 2',95,1)")).isEqualTo(2);
   assertThatThrownBy(()->s.executeUpdate("INSERT INTO content_work_nodes(page_id,name,topic_id) VALUES(999,'없는 페이지',95)")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.executeUpdate("INSERT INTO content_work_nodes(page_id,name,topic_id) VALUES(70,'없는 주제',999)")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.executeUpdate("DELETE FROM topics WHERE id=95")).isInstanceOf(SQLException.class);
   assertThat(s.executeUpdate("DELETE FROM site_pages WHERE id=70")).isEqualTo(1);
  }
  assertThat(rows(copy,"SELECT COUNT(*) FROM content_work_nodes")).containsExactly("0");
  assertThat(rows(copy,"SELECT id FROM topics")).containsExactly("95");
  assertThatThrownBy(()->requireSchema(copy,"sa","","15")).isInstanceOf(IllegalStateException.class);
  Path v15Receipt=dir.resolve("v15-receipt.json");
  JSON.writeValue(v15Receipt.toFile(),Map.of("status","MIGRATED_V15","databasePath",copy.toRealPath().toString(),"jarSha256","same","workaround","AUTO_COMPACT_FILL_RATE=0"));
  assertThatThrownBy(()->requireReceipt(copy,v15Receipt,"same",CURRENT_VERSION)).hasMessageContaining("V16");
 }

 @Test void planRefusesAChangedCopyAndANonV15Database()throws Exception {
  Path original=v15("changed"),copy=dir.resolve("changed-copy.mv.db");Files.copy(original,copy);
  var plan=V16PromotionTool.plan(copy,original,"jar","sa","");
  try(var c=DriverManager.getConnection(writerUrl(copy),"sa","");var s=c.createStatement()){s.executeUpdate("UPDATE site_pages SET title='바뀜' WHERE id=70");}
  assertThatThrownBy(()->V16PromotionTool.validatePlan(plan,"jar","sa","")).hasMessageContaining("changed since planning");
  Path base=dir.resolve("v14");ClassificationMigrationTest.flyway(ClassificationMigrationTest.url(base),"14").migrate();
  Path v14=Path.of(base+".mv.db").toRealPath(),v14Copy=dir.resolve("v14-copy.mv.db");Files.copy(v14,v14Copy);
  assertThatThrownBy(()->V16PromotionTool.plan(v14Copy,v14,"jar","sa","")).hasMessageContaining("not applied to database: 15");
 }
}
