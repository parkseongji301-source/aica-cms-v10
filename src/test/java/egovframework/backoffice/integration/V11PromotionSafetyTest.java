package egovframework.backoffice.integration;
import egovframework.backoffice.mvp.operations.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static egovframework.backoffice.mvp.operations.FileDatabaseSafety.*;
import static org.assertj.core.api.Assertions.*;
class V11PromotionSafetyTest {
 @TempDir Path dir;
 Path v10()throws Exception {
  Path base=dir.resolve("copy");ClassificationMigrationTest.flyway(ClassificationMigrationTest.url(base),"10").migrate();
  return Path.of(base+".mv.db").toRealPath();
 }
 @Test void migrationReceiptAndColdReopenPreserveEveryLegacyTable()throws Exception {
  Path db=v10();var p=V11PromotionTool.plan(db,"rc","sa","");String before=hash(db);
  V11PromotionTool.validatePlan(p,"rc","sa","");assertThat(hash(db)).isEqualTo(before);
  var receipt=V11PromotionTool.migrate(p,"rc","plan","sa","");
  assertThat(receipt.path("status").asText()).isEqualTo("MIGRATED_V11");assertThat(receipt.path("migrationsExecuted").asInt()).isEqualTo(1);
  assertThat(receipt.path("after").path("history").size()).isEqualTo(11);requireCurrentSchema(db,"sa","");
  var fields=p.path("before").path("fingerprints").fields();while(fields.hasNext()){var e=fields.next();assertThat(receipt.path("after").path("fingerprints").path(e.getKey())).isEqualTo(e.getValue());}
  assertThatThrownBy(()->V11PromotionTool.validatePlan(p,"rc","sa","")).hasMessageContaining("target changed");
 }
 @Test void invalidApprovalStopsBeforeChangingDatabase()throws Exception {
  Path db=v10();var p=V11PromotionTool.plan(db,"rc","sa","");String before=hash(db);
  for(String field:List.of("kind","fromVersion","toVersion","databasePath","jdbcUrl","jarSha256","migrationLocation","expiresAt")){
   var bad=p.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)bad).put(field,field.equals("expiresAt")?"2000-01-01T00:00:00Z":"wrong");
   assertThatThrownBy(()->V11PromotionTool.validatePlan(bad,"rc","sa","")).isInstanceOf(Exception.class);
  }
  var bad=p.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)bad).putArray("migrations");
  assertThatThrownBy(()->V11PromotionTool.validatePlan(bad,"rc","sa","")).hasMessageContaining("manifest");assertThat(hash(db)).isEqualTo(before);
 }
 @Test void changedLegacyContentInvalidatesThePlan()throws Exception {
  Path db=v10();var p=V11PromotionTool.plan(db,"rc","sa","");
  try(var c=DriverManager.getConnection(writerUrl(db),"sa","");var s=c.createStatement()){s.executeUpdate("INSERT INTO categories(name) VALUES('after approval')");}
  assertThatThrownBy(()->V11PromotionTool.validatePlan(p,"rc","sa","")).hasMessageContaining("target changed");
  requireV10(db,"sa","");
 }
}
