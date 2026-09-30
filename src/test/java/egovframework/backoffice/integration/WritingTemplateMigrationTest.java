package egovframework.backoffice.integration;
import egovframework.backoffice.mvp.operations.*;
import java.nio.file.*;
import java.sql.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static egovframework.backoffice.mvp.operations.FileDatabaseSafety.*;
import static org.assertj.core.api.Assertions.*;
class WritingTemplateMigrationTest {
 @TempDir Path dir;
 @Test void coldCopyPreservesAllLegacyDataAndDeletedStarterStaysDeleted()throws Exception {
  Path base=dir.resolve("original");ClassificationMigrationTest.flyway(ClassificationMigrationTest.url(base),"11").migrate();
  Path original=Path.of(base+".mv.db").toRealPath(),copy=dir.resolve("copy.mv.db");
  try(var c=DriverManager.getConnection(writerUrl(original),"sa","");var s=c.createStatement()){s.executeUpdate("INSERT INTO categories(name) VALUES('기존 분류')");}
  Files.copy(original,copy);String originalHash=hash(original);
  assertThatThrownBy(()->V12PromotionTool.plan(original,original,"jar","sa","")).hasMessageContaining("separate copy");
  var plan=V12PromotionTool.plan(copy,original,"jar","sa","");
  assertThatThrownBy(()->V12PromotionTool.validatePlan(plan,"wrong","sa","")).hasMessageContaining("checksum");
  var receipt=V12PromotionTool.migrate(plan,"jar","plan","sa","");
  assertThat(receipt.path("status").asText()).isEqualTo("MIGRATED_V12");assertThat(hash(original)).isEqualTo(originalHash);
  requireSchema(copy,"sa","","12");
  try(var c=DriverManager.getConnection(writerUrl(copy),"sa","");var s=c.createStatement()){
   try(var r=s.executeQuery("SELECT content FROM writing_templates")){assertThat(r.next()).isTrue();assertThat(r.getString(1)).contains("해결 과정","다음 기수");assertThat(r.next()).isFalse();}
   s.executeUpdate("DELETE FROM writing_templates");
  }
  assertThat(flyway(writerUrl(copy),"sa","","12").migrate().migrationsExecuted).isZero();
  try(var c=DriverManager.getConnection(readUrl(copy),"sa","");var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM writing_templates")){r.next();assertThat(r.getInt(1)).isZero();}
 }
}
