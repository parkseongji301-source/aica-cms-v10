package egovframework.backoffice.mvp.operations;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static egovframework.backoffice.mvp.operations.FileDatabaseSafety.*;

/** One-shot V10 -> V11 promotion. No web startup, baseline capture, seed or repair. */
public final class V11PromotionTool {
 public static final String KIND="V10_TO_V11";
 public static List<Map<String,Object>> manifest(String url,String user,String password){
  var rows=new ArrayList<Map<String,Object>>();
  for(var m:flyway(url,user,password,"11").info().all()){
   require(m.getVersion()!=null,"unversioned migration is forbidden");
   var row=new LinkedHashMap<String,Object>();row.put("version",m.getVersion().getVersion());row.put("script",m.getScript());row.put("checksum",m.getChecksum());rows.add(row);
  }
  require(rows.size()==11,"exactly V1..V11 required");
  for(int i=0;i<11;i++)require(rows.get(i).get("version").equals(""+(i+1)),"migration sequence mismatch");
  require(rows.get(10).get("script").equals("V11__post_trash.sql"),"unexpected V11 migration");return rows;
 }
 public static JsonNode plan(Path file,String jarHash,String user,String password)throws Exception {
  file=file.toRealPath();requireWriter(writerUrl(file));requireV10(file,user,password);
  var before=inspect(file,user,password);var migrations=manifest(readUrl(file),user,password);
  require(JSON.valueToTree(before.get("history")).equals(JSON.valueToTree(migrations.subList(0,10))),"approved V1..V10 checksums must match database");
  var p=JSON.createObjectNode();p.put("format",1).put("kind",KIND).put("fromVersion","10").put("toVersion","11")
   .put("databasePath",file.toString()).put("jdbcUrl",writerUrl(file)).put("jarSha256",jarHash)
   .put("migrationLocation",LOCATION).put("expiresAt",Instant.now().plusSeconds(86400).toString());
  p.set("before",JSON.valueToTree(before));p.set("migrations",JSON.valueToTree(migrations));return p;
 }
 public static Path validatePlan(JsonNode p,String jarHash,String user,String password)throws Exception {
  require(p.path("format").asInt()==1&&KIND.equals(p.path("kind").asText()),"unsupported promotion plan");
  require("10".equals(p.path("fromVersion").asText())&&"11".equals(p.path("toVersion").asText()),"only V10 -> V11 is supported");
  require(LOCATION.equals(p.path("migrationLocation").asText()),"migration location mismatch");
  require(Instant.parse(p.path("expiresAt").asText()).isAfter(Instant.now()),"promotion plan expired");
  require(jarHash.equals(p.path("jarSha256").asText()),"RC checksum mismatch");
  Path file=Path.of(p.path("databasePath").asText());require(file.isAbsolute(),"absolute target required");file=file.toRealPath();
  require(file.toString().equals(p.path("databasePath").asText())&&writerUrl(file).equals(p.path("jdbcUrl").asText()),"exact database path/URL mismatch");
  require(hash(file).equals(p.path("before").path("sha256").asText()),"target changed since approval");
  requireV10(file,user,password);var current=JSON.valueToTree(inspect(file,user,password));
  for(String field:List.of("path","sha256","columns","history","fingerprints"))require(current.path(field).equals(p.path("before").path(field)),"database baseline mismatch: "+field);
  var resolved=JSON.valueToTree(manifest(readUrl(file),user,password));require(resolved.equals(p.path("migrations")),"migration manifest mismatch");
  require(current.path("history").equals(JSON.valueToTree(manifest(readUrl(file),user,password).subList(0,10))),"legacy checksum mismatch");return file;
 }
 public static JsonNode migrate(JsonNode p,String jarHash,String planHash,String user,String password)throws Exception {
  Path file=validatePlan(p,jarHash,user,password);
  // Keep a connection across migration to compare every old column before the final close.
  try(var c=DriverManager.getConnection(writerUrl(file),user,password)){
   var oldColumns=columns(c);var before=fingerprint(c,oldColumns);
   require(JSON.valueToTree(before).equals(p.path("before").path("fingerprints")),"database changed at writer open");
   var f=flyway(writerUrl(file),user,password,"11");var pending=f.info().pending();
   require(pending.length==1&&"11".equals(pending[0].getVersion().getVersion()),"V11 must be the only pending migration");
   require(f.migrate().migrationsExecuted==1,"exactly one migration required");f.validate();
   require(before.equals(fingerprint(c,oldColumns)),"legacy data changed during V11 migration");
   var afterColumns=columns(c);require(afterColumns.size()==oldColumns.size()+1&&afterColumns.containsKey("POST_TRASH"),"only POST_TRASH may be added");
   for(var entry:oldColumns.entrySet())require(entry.getValue().equals(afterColumns.get(entry.getKey())),"legacy columns changed");
   try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM post_trash")){r.next();require(r.getLong(1)==0,"trash must start empty; no legacy backfill");}
  }
  requireCurrentSchema(file,user,password);var after=JSON.valueToTree(inspect(file,user,password));
  require(after.path("history").equals(p.path("migrations")),"cold migration history mismatch");
  var beforeFields=p.path("before").path("fingerprints").fields();while(beforeFields.hasNext()){var e=beforeFields.next();require(e.getValue().equals(after.path("fingerprints").path(e.getKey())),"cold legacy data mismatch: "+e.getKey());}
  var receipt=JSON.createObjectNode();receipt.put("status","MIGRATED_V11").put("approvalKind",KIND).put("databasePath",file.toString())
   .put("jarSha256",jarHash).put("approvalSha256",planHash).put("workaround","AUTO_COMPACT_FILL_RATE=0")
   .put("migrationsExecuted",1).put("legacyDataPreserved",true).put("baselineCreated",0).put("businessWrites",0).put("completedAt",Instant.now().toString());
  receipt.set("migrations",p.path("migrations"));receipt.set("after",after);return receipt;
 }
 public static void main(String[] args)throws Exception {
  require(args.length==3,"usage: plan|migrate|inspect source output");
  Path output=Path.of(args[2]).toAbsolutePath();require(!Files.exists(output),"evidence already exists");
  String jarHash=hash(runtimeJar()),user=System.getenv().getOrDefault("AICA_DB_USER","sa"),password=System.getenv().getOrDefault("AICA_DB_PASSWORD","");
  JsonNode value;
  switch(args[0]){
   case "plan" -> value=plan(Path.of(args[1]),jarHash,user,password);
   case "inspect" -> {Path file=Path.of(args[1]).toRealPath();requireCurrentSchema(file,user,password);value=JSON.valueToTree(inspect(file,user,password));}
   case "migrate" -> {
    require("true".equals(System.getenv("AICA_V11_PROMOTION_ENABLED")),"explicit V11 promotion flag required");
    Path planFile=Path.of(args[1]).toRealPath();String planHash=hash(planFile);
    require(planHash.equals(System.getenv("AICA_V11_APPROVAL_SHA256")),"approved promotion plan checksum mismatch");
    var p=JSON.readTree(planFile.toFile());Path file=validatePlan(p,jarHash,user,password);
    if(file.toString().replace('\\','/').toLowerCase(Locale.ROOT).contains("/.local-data/"))require("true".equals(System.getenv("AICA_V11_AUTHORIZE_ORIGINAL")),"original promotion requires separate authorization");
    Path spent=Path.of(planFile+".spent");Files.writeString(spent,"started="+Instant.now()+"\n",StandardOpenOption.CREATE_NEW);
    value=migrate(p,jarHash,planHash,user,password);
    Files.writeString(spent,"completed="+Instant.now()+"\n",StandardOpenOption.APPEND);
   }
   default -> throw new IllegalArgumentException("Unknown promotion operation");
  }
  Files.writeString(output,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value),StandardOpenOption.CREATE_NEW);
 }
}
