import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.flywaydb.core.Flyway;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Rehearsal tooling only. Uses classes and migrations extracted from the frozen RC JAR. */
public class BareProbe extends LifetimeProbe {
 static Path file(String value)throws Exception {
  Path p=Path.of(value).toRealPath();var reg=json.readTree(Path.of(System.getProperty("probe.registry")).toFile());boolean allowed=false;
  for(var v:reg.path("allowedFiles"))if(p.equals(Path.of(v.asText()).toRealPath()))allowed=true;
  require(allowed&&!p.toString().replace('\\','/').contains("/.local-data/"),"Only whitelisted copies");return p;
 }
 static String connection(Path file){return "jdbc:h2:file:"+file.toString().substring(0,file.toString().length()-6).replace('\\','/')+";IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE";}
 static Map<String,JsonNode> documents(Connection c)throws Exception {
  var out=new TreeMap<String,JsonNode>();
  for(String t:List.of("site_pages","page_publications"))for(var r:query(c,"SELECT "+(t.equals("site_pages")?"id":"page_id")+" AS id,revision,sections_json FROM "+t+" ORDER BY 1"))out.put(t+":"+r.get("ID"),json.valueToTree(r));
  return out;
 }
 static JsonNode blocks(JsonNode doc)throws Exception{return json.readTree(doc.path("SECTIONS_JSON").asText());}
 static Set<String> ids(JsonNode blocks){var ids=new HashSet<String>();blocks.forEach(b->ids.add(b.path("id").asText()));return ids;}
 static void verifyBlocks(Map<String,JsonNode> before,Map<String,JsonNode> after,Connection c)throws Exception {
  require(before.keySet().equals(after.keySet()),"Page/publication IDs changed");
  for(String key:before.keySet()){
   var original=blocks(before.get(key));var migrated=blocks(after.get(key));var stripped=migrated.deepCopy();
   require(ids(migrated).size()==migrated.size(),"Duplicate IDs");
   for(var b:migrated){require(b.path("id").asText().matches("block_[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"),"Invalid ID");require(b.path("schemaVersion").asInt()==2&&b.path("variation").asText().equals("default"),"Invalid metadata");}
   stripped.forEach(b->((ObjectNode)b).remove(List.of("id","schemaVersion","variation")));require(stripped.equals(original),"Block content changed: "+key);
  }
  for(String key:before.keySet())if(key.startsWith("site_pages:")){
   String id=key.split(":")[1],published="page_publications:"+id;
   var draftIds=ids(blocks(after.get(key)));
   Set<String> publishedIds=before.containsKey(published)?ids(blocks(after.get(published))):Set.of();
   boolean same=before.containsKey(published)&&before.get(key).path("REVISION").equals(before.get(published).path("REVISION"))&&blocks(before.get(key)).equals(blocks(before.get(published)));
   require(same?draftIds.equals(publishedIds):Collections.disjoint(draftIds,publishedIds),"Invalid draft/publication correspondence: "+id);
   for(var identity:query(c,"SELECT block_id,retired FROM page_block_identities WHERE page_id="+Long.parseLong(id))){
    boolean retired=Boolean.parseBoolean((String)identity.get("RETIRED"));require(retired==!draftIds.contains(identity.get("BLOCK_ID")),"Retired ledger mismatch");
   }
  }
 }
 static Map<String,Object> snapshot(Connection c)throws Exception {
  var columns=columns(c);var result=new LinkedHashMap<String,Object>();result.put("columns",columns);result.put("tables",fingerprint(c,columns));result.put("history",history(c));result.put("pageDocuments",documents(c));
  result.put("postIds",query(c,"SELECT id,category_id,status,revision,author_id FROM posts ORDER BY id"));
  result.put("menus",query(c,"SELECT * FROM site_menus ORDER BY id"));
  result.put("accounts",query(c,"SELECT id,email,role,active,password_change_required FROM users ORDER BY id"));
  if(columns.containsKey("PAGE_BLOCK_IDENTITIES"))result.put("blockIdentities",query(c,"SELECT * FROM page_block_identities ORDER BY block_id"));
  if(columns.containsKey("POST_VERSIONS"))for(String table:List.of("post_versions","page_versions","page_template_versions"))result.put(table,query(c,"SELECT id,"+(table.equals("post_versions")?"post_id":table.equals("page_versions")?"page_id":"template_id")+",reason,source_revision,source_version_id FROM "+table+" ORDER BY id"));
  return result;
 }
 public static void main(String[] args)throws Exception {
  String mode=args[0];boolean durable=mode.endsWith("-durable");if(durable)mode=mode.substring(0,mode.length()-8);Path dbFile=file(args[1]),out=Path.of(args[2]);url=connection(dbFile)+System.getProperty("probe.options", "");LifetimeProbe.dbFile=dbFile;LifetimeProbe.out=Path.of(out+".events.json");
  if(mode.equals("snapshot")){try(var c=DriverManager.getConnection(url+";ACCESS_MODE_DATA=r","sa","")){json.writerWithDefaultPrettyPrinter().writeValue(out.toFile(),snapshot(c));}System.out.println("Snapshot recorded");return;}
  if(mode.equals("lock-probe")){try(var c=DriverManager.getConnection(url,"sa","")){throw new IllegalStateException("Unexpected second-process access");}catch(SQLException e){require(e.getErrorCode()==90020,"Unexpected SQL error: "+e);Files.writeString(out,"Second JVM rejected with H2 90020 (file locked)\n");}return;}
  if(mode.equals("different-revision-fixture")){try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()){require("3".equals(flyway("3").info().current().getVersion().getVersion()),"V3 fixture only");s.executeUpdate("UPDATE site_pages SET revision=revision+1 WHERE id=65");if(durable)s.execute("SHUTDOWN");}mode="migrate";}
  if(mode.equals("migrate")){
   var report=new LinkedHashMap<String,Object>();var steps=new ArrayList<Object>();
   report.put("runtime",Map.of("pid",ProcessHandle.current().pid(),"profile","standalone JDBC (no Spring)","jdbcUrl",url,"physicalFile",dbFile.toString(),"defaultCharset",java.nio.charset.Charset.defaultCharset().name()));
   try(var c=DriverManager.getConnection(url,"sa","")){
    require("3".equals(flyway("3").info().current().getVersion().getVersion()),"V3 is required");
    var columns=columns(c);var before=fingerprint(c,columns);var docs=documents(c);var oldHistory=history(c);report.put("before",snapshot(c));
    var projected=new TreeMap<String,List<String>>();columns.forEach((k,v)->projected.put(k,new ArrayList<>(v)));projected.get("SITE_PAGES").remove("SECTIONS_JSON");projected.get("PAGE_PUBLICATIONS").remove("SECTIONS_JSON");var projectedBefore=fingerprint(c,projected);
    for(int n=4;n<=10;n++){
     var f=flyway(""+n);require(f.migrate().migrationsExecuted==1,"Expected one migration to "+n);f.validate();require(f.migrate().migrationsExecuted==0,"Repeat not zero");
     require(projectedBefore.equals(fingerprint(c,projected)),"Legacy columns changed at V"+n);
     if(n<8)require(before.equals(fingerprint(c,columns)),"Legacy JSON changed before V8");else verifyBlocks(docs,documents(c),c);
     require(history(c).subList(0,oldHistory.size()).equals(oldHistory),"Existing Flyway rows changed");
     if(n==8){var once=fingerprint(c,columns(c));db.migration.h2.V8__page_block_identity.upgrade(c);require(once.equals(fingerprint(c,columns(c))),"Repeated V8 conversion changed IDs/data");}
     steps.add(Map.of("target",n,"applied",1,"repeated",0,"validated",true,"legacyFieldsPreserved",true,"history",history(c)));report.put("steps",steps);json.writerWithDefaultPrettyPrinter().writeValue(out.toFile(),report);
    }
    require(count(c,"SELECT COUNT(*) FROM topics")==0&&count(c,"SELECT COUNT(*) FROM cohorts")==0,"Dictionary seeded in migration");
    require(count(c,"SELECT COUNT(*) FROM post_versions")==0&&count(c,"SELECT COUNT(*) FROM page_versions")==0&&count(c,"SELECT COUNT(*) FROM version_baseline_runs")==0,"Baseline seeded in migration");
    require(count(c,"SELECT COUNT(*) FROM posts WHERE content_type_code<>'GENERAL'")==0,"Incorrect type backfill");report.put("after",snapshot(c));report.put("explicitShutdown",durable);if(durable)try(var s=c.createStatement()){s.execute("SHUTDOWN");}
   }
   try(var c=DriverManager.getConnection(url,"sa","")){flyway(null).validate();require(flyway(null).migrate().migrationsExecuted==0,"Reopen not zero");report.put("afterReconnect",snapshot(c));}
   report.put("reconnectStable",json.valueToTree(report.get("after")).equals(json.valueToTree(report.get("afterReconnect"))));require(Boolean.TRUE.equals(report.get("reconnectStable")),"Reconnect changed data");json.writerWithDefaultPrettyPrinter().writeValue(out.toFile(),report);System.out.println("PASS V3 -> V4 -> V5 -> V6 -> V7 -> V8 -> V9 -> V10, each validate/repeat0, legacy values preserved");return;
  }
  if(mode.equals("lifetime")){
   var result=new ArrayList<Object>();
   // Intentionally no anchor connection: reproduce the historical short-lived JDBC/Flyway path.
   var f=flyway("6");require("3".equals(f.info().current().getVersion().getVersion()),"V3 only");int first=f.migrate().migrationsExecuted;require(first==3,"Initial V3->V6 expected");
   for(int i=0;i<12;i++){int repeated=f.migrate().migrationsExecuted;String current=f.info().current().getVersion().getVersion();result.add(Map.of("round",i,"repeated",repeated,"version",current));require(repeated==0&&current.equals("6"),"Connection-lifetime anomaly reproduced");}
   try(var c=DriverManager.getConnection(url,"sa","")){reportLifetime(c,result,out);}return;
  }
  throw new IllegalArgumentException("Unknown mode");
 }
 static void reportLifetime(Connection c,List<Object> rounds,Path out)throws Exception{json.writerWithDefaultPrettyPrinter().writeValue(out.toFile(),Map.of("unanchoredRounds",rounds,"snapshot",snapshot(c)));System.out.println("PASS 12 unanchored reopen/repeat rounds; historical anomaly not reproduced in this run");}
}
