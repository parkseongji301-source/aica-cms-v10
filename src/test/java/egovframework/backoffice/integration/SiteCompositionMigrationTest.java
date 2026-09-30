package egovframework.backoffice.integration;
import egovframework.backoffice.mvp.operations.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static egovframework.backoffice.mvp.operations.FileDatabaseSafety.*;
import static org.assertj.core.api.Assertions.*;

/** V13 -> V14 runs only on a cold copy: four new SITE_PAGES columns at their defaults, two empty tables, no row changed. */
class SiteCompositionMigrationTest {
 @TempDir Path dir;
 private Path v13(String name)throws Exception {
  Path base=dir.resolve(name);ClassificationMigrationTest.flyway(ClassificationMigrationTest.url(base),"13").migrate();
  Path file=Path.of(base+".mv.db").toRealPath();
  try(var c=DriverManager.getConnection(writerUrl(file),"sa","");var s=c.createStatement()){
   s.executeUpdate("INSERT INTO users(id,email,display_name,password_hash,role) VALUES(7,'root@composition.test','관리자','x','SUPER_ADMIN')");
   s.executeUpdate("INSERT INTO site_pages(id,title,slug,sections_json,status,revision,author_id) VALUES(1,'홈','home','[]','PUBLISHED',3,7),(65,'인사교 소개','about','[]','PUBLISHED',5,7)");
   s.executeUpdate("INSERT INTO site_pages(id,title,slug,sections_json,status,revision,author_id,parent_id,sort_order) VALUES(70,'후기','reviews','[]','DRAFT',1,7,65,0)");
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

 @Test void coldCopyGainsOnlyCompositionColumnsAndEmptyStructureTables()throws Exception {
  Path original=v13("original"),copy=dir.resolve("copy.mv.db");Files.copy(original,copy);String originalHash=hash(original);
  var before=rows(original,"SELECT id,title,slug,status,revision,author_id,parent_id,sort_order FROM site_pages ORDER BY id");
  assertThatThrownBy(()->V14PromotionTool.plan(original,original,"jar","sa","")).hasMessageContaining("separate copy");
  var plan=V14PromotionTool.plan(copy,original,"jar","sa","");
  assertThat(plan.path("migrations").size()).isEqualTo(14);assertThat(plan.path("migrations").get(13).path("script").asText()).isEqualTo("V14__site_composition.sql");
  assertThatThrownBy(()->V14PromotionTool.validatePlan(plan,"wrong","sa","")).hasMessageContaining("checksum");

  var receipt=V14PromotionTool.migrate(plan,"jar","plan","sa","");
  assertThat(receipt.path("status").asText()).isEqualTo("MIGRATED_V14");assertThat(receipt.path("after").path("history").size()).isEqualTo(14);
  assertThat(hash(original)).isEqualTo(originalHash);
  requireSchema(copy,"sa","","14");
  assertThat(rows(copy,"SELECT id,title,slug,status,revision,author_id,parent_id,sort_order FROM site_pages ORDER BY id")).isEqualTo(before);
  assertThat(rows(copy,"SELECT id,area_kind,content_type_code,menu_visible,menu_label FROM site_pages ORDER BY id"))
   .containsExactly("1|PAGE|null|false|null","65|PAGE|null|false|null","70|PAGE|null|false|null");
  assertThat(rows(copy,"SELECT label,kind,target_id FROM site_menus")).containsExactly("소개|PAGE|65");
  assertThat(rows(copy,"SELECT COUNT(*) FROM site_structure_publications")).containsExactly("0");
  try(var c=DriverManager.getConnection(writerUrl(copy),"sa","");var s=c.createStatement()){
   // General integrity only: known area kinds and existing content types. One area per type is not a DB rule.
   assertThatThrownBy(()->s.executeUpdate("UPDATE site_pages SET area_kind='FOLDER' WHERE id=70")).isInstanceOf(SQLException.class);
   assertThatThrownBy(()->s.executeUpdate("UPDATE site_pages SET content_type_code='NOPE' WHERE id=70")).isInstanceOf(SQLException.class);
   s.executeUpdate("UPDATE site_pages SET content_type_code='REVIEW' WHERE id IN (65,70)");
   // Structure snapshots refer to page ids without a foreign key, so an old snapshot never blocks deleting a page.
   s.executeUpdate("INSERT INTO site_structure_publications(id,snapshot_json,fingerprint,reason,published_by,publisher_name) VALUES(1,'[]','x','PUBLISH',7,'관리자')");
   s.executeUpdate("INSERT INTO site_structure_publication_pages(publication_id,page_id) VALUES(1,70),(1,999)");
  }
  assertThatThrownBy(()->requireSchema(copy,"sa","","13")).isInstanceOf(IllegalStateException.class);
  Path v13Receipt=dir.resolve("v13-receipt.json");
  JSON.writeValue(v13Receipt.toFile(),Map.of("status","MIGRATED_V13","databasePath",copy.toRealPath().toString(),"jarSha256","same","workaround","AUTO_COMPACT_FILL_RATE=0"));
  assertThatThrownBy(()->requireReceipt(copy,v13Receipt,"same",CURRENT_VERSION)).hasMessageContaining("V"+CURRENT_VERSION);
 }

 @Test void planRefusesAChangedCopyAndANonV13Database()throws Exception {
  Path original=v13("changed"),copy=dir.resolve("changed-copy.mv.db");Files.copy(original,copy);
  var plan=V14PromotionTool.plan(copy,original,"jar","sa","");
  try(var c=DriverManager.getConnection(writerUrl(copy),"sa","");var s=c.createStatement()){s.executeUpdate("UPDATE site_pages SET title='바뀜' WHERE id=70");}
  assertThatThrownBy(()->V14PromotionTool.validatePlan(plan,"jar","sa","")).hasMessageContaining("changed since planning");
  Path base=dir.resolve("v12");ClassificationMigrationTest.flyway(ClassificationMigrationTest.url(base),"12").migrate();
  Path v12=Path.of(base+".mv.db").toRealPath(),v12Copy=dir.resolve("v12-copy.mv.db");Files.copy(v12,v12Copy);
  assertThatThrownBy(()->V14PromotionTool.plan(v12Copy,v12,"jar","sa","")).hasMessageContaining("not applied to database: 13");
 }
}
