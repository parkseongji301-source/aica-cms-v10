package egovframework.backoffice.integration;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.assertj.core.api.Assertions.*;
class VersionMigrationTest {
 @TempDir Path directory;
 static void verify(String url)throws Exception{
  Map<String,List<String>> columns;Map<String,String> before;
  try(var c=DriverManager.getConnection(url,"sa","")){
   columns=ClassificationMigrationTest.columns(c);before=ClassificationMigrationTest.fingerprints(c,columns);
   var flyway=ClassificationMigrationTest.flyway(url,"10");assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("9");
   assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);assertThat(flyway.migrate().migrationsExecuted).isZero();
   assertThat(ClassificationMigrationTest.fingerprints(c,columns)).isEqualTo(before);
   for(String table:List.of("post_versions","page_versions","page_template_versions","version_baseline_runs"))
    try(var s=c.createStatement();var rows=s.executeQuery("SELECT COUNT(*) FROM "+table)){rows.next();assertThat(rows.getLong(1)).isZero();}
  }
  try(var c=DriverManager.getConnection(url,"sa","")){assertThat(ClassificationMigrationTest.fingerprints(c,columns)).isEqualTo(before);assertThat(ClassificationMigrationTest.flyway(url,"10").migrate().migrationsExecuted).isZero();}
 }
 @Test void additiveSchemaPreservesV9AndCreatesNoBaseline()throws Exception{
  String url=ClassificationMigrationTest.url(directory.resolve("version-schema"));ClassificationMigrationTest.flyway(url,"9").migrate();verify(url);
 }
 @Test @EnabledIfSystemProperty(named="aica.versionValidationCopy",matches=".+")
 void actualLatestV9CopyPreservesEveryOldColumn()throws Exception{verify(copyUrl("aica.versionValidationCopy"));}
 @Test @EnabledIfSystemProperty(named="aica.versionV3Copy",matches=".+")
 void actualV3CopyMigratesThroughTheEntireReviewedChain()throws Exception{
  String url=copyUrl("aica.versionV3Copy");
  try(var c=DriverManager.getConnection(url,"sa","")){
   var beforeColumns=ClassificationMigrationTest.columns(c);var before=ClassificationMigrationTest.fingerprints(c,beforeColumns);
   var pageBefore=legacyPages(c,"site_pages");var publicationBefore=legacyPages(c,"page_publications");
   var migration=ClassificationMigrationTest.flyway(url,"10");assertThat(migration.info().current().getVersion().getVersion()).isEqualTo("3");
   assertThat(migration.migrate().migrationsExecuted).isEqualTo(7);assertThat(migration.migrate().migrationsExecuted).isZero();
   assertThat(legacyPages(c,"site_pages")).isEqualTo(pageBefore);assertThat(legacyPages(c,"page_publications")).isEqualTo(publicationBefore);
   // Compare original page fields and JSON after excluding only V8-added identity metadata.
   beforeColumns.get("SITE_PAGES").remove("SECTIONS_JSON");beforeColumns.get("PAGE_PUBLICATIONS").remove("SECTIONS_JSON");
   var projectedBefore=new LinkedHashMap<String,String>(before);
   // Page fields and stripped JSON are verified above; fingerprints verify all remaining original columns.
   projectedBefore.remove("SITE_PAGES");projectedBefore.remove("PAGE_PUBLICATIONS");
   var after=ClassificationMigrationTest.fingerprints(c,beforeColumns);after.remove("SITE_PAGES");after.remove("PAGE_PUBLICATIONS");assertThat(after).isEqualTo(projectedBefore);
   assertThat(Arrays.stream(migration.info().applied()).filter(i->i.getVersion()!=null).map(i->i.getVersion().getVersion()).toList()).containsExactly("1","2","3","4","5","6","7","8","9","10");
  }
  try(var c=DriverManager.getConnection(url,"sa","")){assertThat(ClassificationMigrationTest.flyway(url,"10").migrate().migrationsExecuted).isZero();}
 }
 static List<String> legacyPages(Connection c,String table)throws Exception{
  var result=new ArrayList<String>();var json=new com.fasterxml.jackson.databind.ObjectMapper();
  try(var q=c.createStatement();var r=q.executeQuery("SELECT * FROM "+table+" ORDER BY "+(table.equals("site_pages")?"id":"page_id"))){
   while(r.next()){var row=json.createObjectNode();for(int i=1;i<=r.getMetaData().getColumnCount();i++){
    String column=r.getMetaData().getColumnName(i);
    if(column.equalsIgnoreCase("sections_json")){var blocks=json.readTree(r.getString(i));blocks.forEach(b->{var object=(com.fasterxml.jackson.databind.node.ObjectNode)b;object.remove(List.of("id","schemaVersion","variation"));});row.set(column,blocks);}
    else row.put(column,r.getString(i));
   }result.add(row.toString());}
  }return result;
 }
 static String copyUrl(String property)throws Exception{
  Path file=Path.of(System.getProperty(property)).toRealPath();
  assertThat(file.startsWith(Path.of(".cache/phase5b2b2-data").toRealPath())).isTrue();assertThat(file.toString()).endsWith(".mv.db");
  return ClassificationMigrationTest.url(Path.of(file.toString().substring(0,file.toString().length()-6)));
 }
}

