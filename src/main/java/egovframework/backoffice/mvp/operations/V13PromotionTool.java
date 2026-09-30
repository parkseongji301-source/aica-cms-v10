package egovframework.backoffice.mvp.operations;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static egovframework.backoffice.mvp.operations.FileDatabaseSafety.*;

/**
 * V12 -> V13 (page hierarchy) on a byte-identical cold copy only. The only allowed change is two new
 * SITE_PAGES columns (PARENT_ID all NULL, SORT_ORDER all 0); every existing table, column and row must survive.
 */
public final class V13PromotionTool {
 static final List<String> ADDED=List.of("PARENT_ID","SORT_ORDER");
 public static JsonNode plan(Path file,Path original,String jarHash,String user,String password)throws Exception {
  file=file.toRealPath();original=original.toRealPath();
  require(!Files.isSameFile(file,original),"migration requires a separate copy");
  require(hash(file).equals(hash(original)),"copy differs from the cold original");
  requireWriter(writerUrl(file));requireSchema(file,user,password,"12");
  var before=inspect(file,user,password);var migrations=manifest(file,user,password);
  var previous=migrations.deepCopy();previous.remove(12);
  require(JSON.valueToTree(before.get("history")).equals(previous),"V1..V12 checksums mismatch");
  var p=JSON.createObjectNode();p.put("kind","V12_TO_V13_COPY").put("databasePath",file.toString()).put("originalPath",original.toString())
   .put("jarSha256",jarHash).put("expiresAt",Instant.now().plusSeconds(86400).toString());
  p.set("before",JSON.valueToTree(before));p.set("migrations",migrations);return p;
 }
 private static com.fasterxml.jackson.databind.node.ArrayNode manifest(Path file,String user,String password)throws Exception {
  var rows=JSON.createArrayNode();
  for(var m:flyway(readUrl(file),user,password,"13").info().all()){
   require(m.getVersion()!=null,"versioned migrations required");
   if(m.getVersion().compareTo(org.flywaydb.core.api.MigrationVersion.fromVersion("13"))>0)continue;
   rows.addObject().put("version",m.getVersion().getVersion()).put("script",m.getScript()).put("checksum",m.getChecksum());
  }
  require(rows.size()==13,"exactly V1..V13 required");
  for(int i=0;i<13;i++)require(rows.get(i).path("version").asText().equals(""+(i+1)),"migration sequence mismatch");
  require(rows.get(12).path("script").asText().equals("V13__page_hierarchy.sql"),"unexpected V13 migration");return rows;
 }
 public static Path validatePlan(JsonNode p,String jarHash,String user,String password)throws Exception {
  require("V12_TO_V13_COPY".equals(p.path("kind").asText()),"invalid migration plan");
  require(jarHash.equals(p.path("jarSha256").asText()),"runtime checksum mismatch");
  require(Instant.parse(p.path("expiresAt").asText()).isAfter(Instant.now()),"plan expired");
  Path file=Path.of(p.path("databasePath").asText()).toRealPath(),original=Path.of(p.path("originalPath").asText()).toRealPath();
  require(file.toString().equals(p.path("databasePath").asText())&&!Files.isSameFile(file,original),"separate exact copy required");
  require(hash(file).equals(p.path("before").path("sha256").asText()),"copy changed since planning");
  require(hash(original).equals(hash(file)),"cold original changed since copy");
  requireSchema(file,user,password,"12");
  var current=JSON.valueToTree(inspect(file,user,password));
  for(String field:List.of("path","sha256","columns","history","fingerprints"))require(current.path(field).equals(p.path("before").path(field)),"baseline mismatch: "+field);
  require(manifest(file,user,password).equals(p.path("migrations")),"manifest mismatch");return file;
 }
 public static JsonNode migrate(JsonNode p,String jarHash,String planHash,String user,String password)throws Exception {
  Path file=validatePlan(p,jarHash,user,password);
  Map<String,List<String>> old;
  try(var c=DriverManager.getConnection(writerUrl(file),user,password)){
   old=columns(c);var before=fingerprint(c,old);require(JSON.valueToTree(before).equals(p.path("before").path("fingerprints")),"changed at writer open");
   var f=flyway(writerUrl(file),user,password,"13");var pending=f.info().pending();
   require(pending.length==1&&"13".equals(pending[0].getVersion().getVersion()),"only V13 may run");
   require(f.migrate().migrationsExecuted==1,"one migration required");f.validate();
   require(before.equals(fingerprint(c,old)),"existing data changed");
   var after=columns(c);require(after.keySet().equals(old.keySet()),"no table may be added or removed");
   for(var e:old.entrySet()){
    var expected=new ArrayList<>(e.getValue());if(e.getKey().equals("SITE_PAGES"))expected.addAll(ADDED);
    require(expected.equals(after.get(e.getKey())),"only SITE_PAGES.PARENT_ID and SORT_ORDER may be added");
   }
   try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM site_pages WHERE parent_id IS NOT NULL OR sort_order<>0")){r.next();require(r.getInt(1)==0,"existing pages must stay top-level with sort order 0");}
  }
  requireSchema(file,user,password,"13");var after=JSON.valueToTree(inspect(file,user,password));
  require(after.path("history").equals(p.path("migrations")),"cold migration history mismatch");
  try(var c=DriverManager.getConnection(readUrl(file),user,password)){
   require(JSON.valueToTree(fingerprint(c,old)).equals(p.path("before").path("fingerprints")),"cold data mismatch");
  }
  require(hash(Path.of(p.path("originalPath").asText())).equals(p.path("before").path("sha256").asText()),"original was changed");
  var receipt=JSON.createObjectNode();receipt.put("status","MIGRATED_V13").put("databasePath",file.toString()).put("jarSha256",jarHash)
   .put("planSha256",planHash).put("workaround","AUTO_COMPACT_FILL_RATE=0").put("legacyDataPreserved",true).put("existingPagesTopLevel",true).put("completedAt",Instant.now().toString());
  receipt.set("after",after);return receipt;
 }
 public static void main(String[] args)throws Exception {
  require(args.length>=3,"usage: plan copy output original | migrate plan output");
  Path output=Path.of(args[2]).toAbsolutePath();require(!Files.exists(output),"evidence already exists");
  String jarHash=hash(runtimeJar()),user=System.getenv().getOrDefault("AICA_DB_USER","sa"),password=System.getenv().getOrDefault("AICA_DB_PASSWORD","");
  JsonNode result;
  switch(args[0]){
   case "plan" -> {require(args.length==4,"cold original required");result=plan(Path.of(args[1]),Path.of(args[3]),jarHash,user,password);}
   case "migrate" -> {
    require("true".equals(System.getenv("AICA_V13_PROMOTION_ENABLED")),"explicit migration flag required");
    Path source=Path.of(args[1]).toRealPath();String digest=hash(source);require(digest.equals(System.getenv("AICA_V13_PLAN_SHA256")),"plan checksum mismatch");
    var p=JSON.readTree(source.toFile());validatePlan(p,jarHash,user,password);
    Files.writeString(Path.of(source+".spent"),Instant.now().toString(),StandardOpenOption.CREATE_NEW);result=migrate(p,jarHash,digest,user,password);
   }
   default -> throw new IllegalArgumentException("Unknown operation; cold inspection of the current schema is V12PromotionTool inspect");
  }
  Files.writeString(output,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result),StandardOpenOption.CREATE_NEW);
 }
}
