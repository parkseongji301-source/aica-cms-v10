package egovframework.backoffice.integration;
import egovframework.backoffice.mvp.operations.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static egovframework.backoffice.mvp.operations.FileDatabaseSafety.*;
import static org.assertj.core.api.Assertions.*;

/** V14 -> V15 runs only on a cold copy: one new SITE_PAGES column, TRUE for every page, no row changed. */
class StructureMembershipMigrationTest {
 @TempDir Path dir;
 private Path v14(String name)throws Exception {
  Path base=dir.resolve(name);ClassificationMigrationTest.flyway(ClassificationMigrationTest.url(base),"14").migrate();
  Path file=Path.of(base+".mv.db").toRealPath();
  try(var c=DriverManager.getConnection(writerUrl(file),"sa","");var s=c.createStatement()){
   s.executeUpdate("INSERT INTO users(id,email,display_name,password_hash,role) VALUES(7,'root@membership.test','관리자','x','SUPER_ADMIN')");
   s.executeUpdate("INSERT INTO site_pages(id,title,slug,sections_json,status,revision,author_id,menu_visible) VALUES(1,'홈','home','[]','PUBLISHED',3,7,TRUE),(65,'소개','about','[]','PUBLISHED',5,7,FALSE)");
   s.executeUpdate("INSERT INTO site_pages(id,title,slug,sections_json,status,revision,author_id,area_kind) VALUES(80,'묶음','group-x','[]','DRAFT',0,7,'GROUP')");
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

 @Test void coldCopyGainsOnlyTheMembershipColumnWithEveryPageInTheStructure()throws Exception {
  Path original=v14("original"),copy=dir.resolve("copy.mv.db");Files.copy(original,copy);String originalHash=hash(original);
  String pages="SELECT id,title,slug,status,revision,area_kind,content_type_code,menu_visible,menu_label,parent_id,sort_order FROM site_pages ORDER BY id";
  var before=rows(original,pages);var publications=rows(original,"SELECT id,CAST(snapshot_json AS VARCHAR),fingerprint,reason FROM site_structure_publications");
  assertThatThrownBy(()->V15PromotionTool.plan(original,original,"jar","sa","")).hasMessageContaining("separate copy");
  var plan=V15PromotionTool.plan(copy,original,"jar","sa","");
  assertThat(plan.path("migrations").size()).isEqualTo(15);assertThat(plan.path("migrations").get(14).path("script").asText()).isEqualTo("V15__structure_membership.sql");
  assertThatThrownBy(()->V15PromotionTool.validatePlan(plan,"wrong","sa","")).hasMessageContaining("checksum");

  var receipt=V15PromotionTool.migrate(plan,"jar","plan","sa","");
  assertThat(receipt.path("status").asText()).isEqualTo("MIGRATED_V15");assertThat(receipt.path("after").path("history").size()).isEqualTo(15);
  assertThat(hash(original)).isEqualTo(originalHash);
  // V15 is the version this tool produces; the current runtime version (V16) is verified by its own tool.
  requireSchema(copy,"sa","","15");
  assertThat(rows(copy,pages)).isEqualTo(before);
  assertThat(rows(copy,"SELECT id,in_structure FROM site_pages ORDER BY id")).containsExactly("1|true","65|true","80|true");
  assertThat(rows(copy,"SELECT id,CAST(snapshot_json AS VARCHAR),fingerprint,reason FROM site_structure_publications")).isEqualTo(publications);
  assertThat(rows(copy,"SELECT publication_id,page_id FROM site_structure_publication_pages")).containsExactly("1|1");
  // Removal rules are service rules: the DB accepts any combination of membership and menu visibility.
  try(var c=DriverManager.getConnection(writerUrl(copy),"sa","");var s=c.createStatement()){
   assertThat(s.executeUpdate("UPDATE site_pages SET in_structure=FALSE WHERE id=1")).isEqualTo(1);
   assertThatThrownBy(()->s.executeUpdate("UPDATE site_pages SET in_structure=NULL WHERE id=65")).isInstanceOf(SQLException.class);
  }
  assertThatThrownBy(()->requireSchema(copy,"sa","","14")).isInstanceOf(IllegalStateException.class);
  Path v14Receipt=dir.resolve("v14-receipt.json");
  JSON.writeValue(v14Receipt.toFile(),Map.of("status","MIGRATED_V14","databasePath",copy.toRealPath().toString(),"jarSha256","same","workaround","AUTO_COMPACT_FILL_RATE=0"));
  assertThatThrownBy(()->requireReceipt(copy,v14Receipt,"same",CURRENT_VERSION)).hasMessageContaining("V"+CURRENT_VERSION);
 }

 @Test void planRefusesAChangedCopyAndANonV14Database()throws Exception {
  Path original=v14("changed"),copy=dir.resolve("changed-copy.mv.db");Files.copy(original,copy);
  var plan=V15PromotionTool.plan(copy,original,"jar","sa","");
  try(var c=DriverManager.getConnection(writerUrl(copy),"sa","");var s=c.createStatement()){s.executeUpdate("UPDATE site_pages SET title='바뀜' WHERE id=65");}
  assertThatThrownBy(()->V15PromotionTool.validatePlan(plan,"jar","sa","")).hasMessageContaining("changed since planning");
  Path base=dir.resolve("v13");ClassificationMigrationTest.flyway(ClassificationMigrationTest.url(base),"13").migrate();
  Path v13=Path.of(base+".mv.db").toRealPath(),v13Copy=dir.resolve("v13-copy.mv.db");Files.copy(v13,v13Copy);
  assertThatThrownBy(()->V15PromotionTool.plan(v13Copy,v13,"jar","sa","")).hasMessageContaining("not applied to database: 14");
 }
}
