package egovframework.backoffice.integration;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class PageTemplateMigrationTest {
 @TempDir Path directory;
 static void verify(String url)throws Exception {
  Map<String,List<String>> columns;Map<String,String> before;
  try(var c=DriverManager.getConnection(url,"sa","")){
   columns=ClassificationMigrationTest.columns(c);before=ClassificationMigrationTest.fingerprints(c,columns);
   var migration=ClassificationMigrationTest.flyway(url,"9");assertThat(migration.info().current().getVersion().getVersion()).isEqualTo("8");
   assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);assertThat(migration.migrate().migrationsExecuted).isZero();
   assertThat(ClassificationMigrationTest.fingerprints(c,columns)).isEqualTo(before);
   try(var s=c.createStatement();var rows=s.executeQuery("SELECT COUNT(*) FROM page_templates")){rows.next();assertThat(rows.getLong(1)).isZero();}
  }
  try(var c=DriverManager.getConnection(url,"sa","")){assertThat(ClassificationMigrationTest.fingerprints(c,columns)).isEqualTo(before);}
 }
 @Test void v9IsAdditiveAndRerunnableWithEveryOldColumnPreserved()throws Exception {
  String url=ClassificationMigrationTest.url(directory.resolve("templates"));ClassificationMigrationTest.flyway(url,"8").migrate();verify(url);
 }
 @Test @EnabledIfSystemProperty(named="aica.templateValidationCopy",matches=".+")
 void latestActualV8CopyPreservesEveryExistingTable()throws Exception {
  Path file=Path.of(System.getProperty("aica.templateValidationCopy")).toRealPath();assertThat(file.startsWith(Path.of(".cache/react-phase4d-data").toRealPath())).isTrue();assertThat(file.toString()).endsWith(".mv.db");
  verify(ClassificationMigrationTest.url(Path.of(file.toString().substring(0,file.toString().length()-6))));
 }
}
