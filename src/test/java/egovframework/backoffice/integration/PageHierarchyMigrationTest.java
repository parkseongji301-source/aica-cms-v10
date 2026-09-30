package egovframework.backoffice.integration;
import egovframework.backoffice.mvp.operations.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static egovframework.backoffice.mvp.operations.FileDatabaseSafety.*;
import static org.assertj.core.api.Assertions.*;

/** V12 -> V13 runs only on a cold copy, adds two SITE_PAGES columns and keeps every existing row. */
class PageHierarchyMigrationTest {
 @TempDir Path dir;
 private Path v12(String name,boolean data)throws Exception {
  Path base=dir.resolve(name);ClassificationMigrationTest.flyway(ClassificationMigrationTest.url(base),"12").migrate();
  Path file=Path.of(base+".mv.db").toRealPath();
  if(data)try(var c=DriverManager.getConnection(writerUrl(file),"sa","");var s=c.createStatement()){
   s.executeUpdate("INSERT INTO users(id,email,display_name,password_hash,role) VALUES(7,'root@hierarchy.test','관리자','x','SUPER_ADMIN')");
   s.executeUpdate("INSERT INTO site_pages(id,title,slug,sections_json,status,revision,author_id) VALUES(1,'홈','home','[]','PUBLISHED',3,7),(65,'인사교 소개','about','[]','PUBLISHED',5,7),(97,'초안','draft','[]','DRAFT',0,7)");
   s.executeUpdate("INSERT INTO site_menus(label,kind,target_id,sort_order) VALUES('소개','PAGE',65,0)");
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

 @Test void coldCopyGainsOnlyParentAndOrderColumnsAndKeepsEveryRow()throws Exception {
  Path original=v12("original",true),copy=dir.resolve("copy.mv.db");Files.copy(original,copy);String originalHash=hash(original);
  var pagesBefore=rows(original,"SELECT id,title,slug,status,revision,author_id FROM site_pages ORDER BY id");
  assertThatThrownBy(()->V13PromotionTool.plan(original,original,"jar","sa","")).hasMessageContaining("separate copy");
  var plan=V13PromotionTool.plan(copy,original,"jar","sa","");
  assertThat(plan.path("migrations").size()).isEqualTo(13);assertThat(plan.path("migrations").get(12).path("script").asText()).isEqualTo("V13__page_hierarchy.sql");
  assertThatThrownBy(()->V13PromotionTool.validatePlan(plan,"wrong","sa","")).hasMessageContaining("checksum");

  var receipt=V13PromotionTool.migrate(plan,"jar","plan","sa","");
  assertThat(receipt.path("status").asText()).isEqualTo("MIGRATED_V13");assertThat(receipt.path("after").path("history").size()).isEqualTo(13);
  assertThat(hash(original)).isEqualTo(originalHash);
  requireCurrentSchema(copy,"sa","");
  assertThat(rows(copy,"SELECT id,title,slug,status,revision,author_id FROM site_pages ORDER BY id")).isEqualTo(pagesBefore);
  assertThat(rows(copy,"SELECT id,parent_id,sort_order FROM site_pages ORDER BY id")).containsExactly("1|null|0","65|null|0","97|null|0");
  assertThat(rows(copy,"SELECT label,kind,target_id FROM site_menus")).containsExactly("소개|PAGE|65");
  try(var c=DriverManager.getConnection(readUrl(copy),"sa","")){
   var columns=columns(c);assertThat(columns.get("SITE_PAGES")).endsWith("PARENT_ID","SORT_ORDER");
  }
  // General integrity is in the DB: a page cannot be its own parent and a parent with children cannot be deleted.
  try(var c=DriverManager.getConnection(writerUrl(copy),"sa","");var s=c.createStatement()){
   assertThatThrownBy(()->s.executeUpdate("UPDATE site_pages SET parent_id=id WHERE id=97")).isInstanceOf(SQLException.class);
   s.executeUpdate("UPDATE site_pages SET parent_id=65 WHERE id=97");
   assertThatThrownBy(()->s.executeUpdate("DELETE FROM site_pages WHERE id=65")).isInstanceOf(SQLException.class);
   // The two-level limit is deliberately not a DB rule.
   s.executeUpdate("INSERT INTO site_pages(id,title,slug,sections_json,author_id,parent_id) VALUES(98,'셋째 단계','third','[]',7,97)");
  }
  // A V12 runtime cannot open the V13 copy, and a V12 receipt is not accepted by the V13 runtime.
  assertThatThrownBy(()->requireSchema(copy,"sa","","12")).isInstanceOf(IllegalStateException.class);
  Path v12Receipt=dir.resolve("v12-receipt.json");
  JSON.writeValue(v12Receipt.toFile(),Map.of("status","MIGRATED_V12","databasePath",copy.toRealPath().toString(),"jarSha256","same","workaround","AUTO_COMPACT_FILL_RATE=0"));
  assertThatThrownBy(()->requireReceipt(copy,v12Receipt,"same",CURRENT_VERSION)).hasMessageContaining("V13");
 }

 @Test void planRefusesAChangedCopyAndANonV12Database()throws Exception {
  Path original=v12("changed",true),copy=dir.resolve("changed-copy.mv.db");Files.copy(original,copy);
  var plan=V13PromotionTool.plan(copy,original,"jar","sa","");
  try(var c=DriverManager.getConnection(writerUrl(copy),"sa","");var s=c.createStatement()){s.executeUpdate("UPDATE site_pages SET title='바뀜' WHERE id=97");}
  assertThatThrownBy(()->V13PromotionTool.validatePlan(plan,"jar","sa","")).hasMessageContaining("changed since planning");

  Path base=dir.resolve("v11");ClassificationMigrationTest.flyway(ClassificationMigrationTest.url(base),"11").migrate();
  Path v11=Path.of(base+".mv.db").toRealPath(),v11Copy=dir.resolve("v11-copy.mv.db");Files.copy(v11,v11Copy);
  // Flyway validation refuses a V11 database before any plan exists (V12 not applied).
  assertThatThrownBy(()->V13PromotionTool.plan(v11Copy,v11,"jar","sa","")).hasMessageContaining("not applied to database: 12");
 }
}
