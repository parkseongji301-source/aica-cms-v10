import java.nio.file.*;
import java.nio.file.attribute.*;
import java.sql.*;
import java.util.*;
import java.time.*;
import org.flywaydb.core.Flyway;

/** Diagnostic only, whitelisted disposable copies. No application/migration changes. */
public class LifetimeProbe extends CutoverAudit {
 static Path dbFile,out;static List<Object> events=new ArrayList<>();
 static Map<String,Object> physical()throws Exception{
  var a=Files.readAttributes(dbFile,BasicFileAttributes.class);var m=new LinkedHashMap<String,Object>();
  m.put("requested",dbFile.toString());m.put("realPath",dbFile.toRealPath().toString());m.put("fileKey",Objects.toString(a.fileKey()));m.put("size",a.size());m.put("modified",a.lastModifiedTime().toString());try{m.put("sha256",hash(Files.readAllBytes(dbFile)));}catch(java.io.IOException e){m.put("sha256","LOCKED_WHILE_OPEN");}var p=new ProcessBuilder("fsutil","file","queryfileid",dbFile.toString()).redirectErrorStream(true).start();m.put("windowsFileId",new String(p.getInputStream().readAllBytes(),java.nio.charset.Charset.defaultCharset()).trim());p.waitFor();return m;
 }
 static void event(String phase,Connection c)throws Exception{
  var e=new LinkedHashMap<String,Object>();e.put("phase",phase);e.put("at",Instant.now().toString());e.put("pid",ProcessHandle.current().pid());e.put("profile","standalone JDBC (no Spring)");e.put("jdbcUrl",url);e.put("file",physical());
  if(c!=null){e.put("metadataUrl",c.getMetaData().getURL());e.put("databasePath",query(c,"SELECT DATABASE_PATH() AS path"));e.put("autoCommit",c.getAutoCommit());e.put("readOnly",c.isReadOnly());e.put("history",history(c));e.put("representatives",query(c,"SELECT 'page' AS kind,id,revision,title FROM site_pages WHERE id IN(1,65) UNION ALL SELECT 'post',id,revision,title FROM posts WHERE id=33 ORDER BY 1,2"));e.put("fingerprints",fingerprint(c,Map.of("SITE_PAGES",List.of("ID","TITLE","SLUG","SECTIONS_JSON","REVISION"),"POSTS",List.of("ID","TITLE","CONTENT","CATEGORY_ID","REVISION"))));e.put("settings",query(c,"SELECT * FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME IN ('DB_CLOSE_ON_EXIT','DB_CLOSE_DELAY','AUTO_SERVER','AUTO_RECONNECT','MODE','WRITE_DELAY','FILE_LOCK','DATABASE_TO_UPPER','DATABASE_TO_LOWER','info.FILE_WRITE','info.FILE_WRITE_BYTES','info.FILE_SIZE','info.FILE_READ_ONLY','info.UPDATE_FAILURE_PERCENT') ORDER BY SETTING_NAME"));}
  events.add(e);json.writerWithDefaultPrettyPrinter().writeValue(out.toFile(),events);System.out.println(phase+" physical="+e.get("file")+(c==null?"":" history="+query(c,"SELECT MAX(CAST(\"version\" AS INT)) AS version FROM \"flyway_schema_history\"")));
 }
 static Connection open()throws Exception{return DriverManager.getConnection(url,"sa","");}
 static void edit(Connection c)throws Exception{try(var s=c.createStatement()){require(s.executeUpdate("UPDATE site_pages SET revision=revision+1 WHERE id=65")==1,"No target");}}
 static void migrate()throws Exception{try(var c=open()){event("migration-open",c);for(int n=4;n<=10;n++){var f=flyway(""+n);var r=f.migrate();require(r.migrationsExecuted==1,"Expected migration "+n);f.validate();require(f.migrate().migrationsExecuted==0,"repeat");}event("migration-v10",c);}event("migration-closed",null);}
 public static void main(String[] args)throws Exception{
  dbFile=Path.of(args[1]).toRealPath();out=Path.of(args[2]);var registry=json.readTree(Path.of(args[3]).toFile());boolean allowed=false;
  for(var p:registry.path("allowedFiles"))if(dbFile.equals(Path.of(p.asText()).toRealPath()))allowed=true;
  require(allowed&&!dbFile.toString().replace('\\','/').contains("/.local-data/"),"Unapproved diagnostic file");
  url=connection(dbFile)+(args.length>4?args[4]:"");event("before",null);
  switch(args[0]){
   case "read":{url+=";ACCESS_MODE_DATA=r";try(var c=open()){event("readonly",c);}event("readonly-closed",null);break;}
   case "plain":{for(int n=0;n<3;n++){try(var c=open()){event("open-"+n,c);edit(c);event("edited-"+n,c);}event("closed-"+n,null);}break;}
   case "repro":{try(var c=open()){event("prepare-open",c);require("3".equals(flyway("3").info().current().getVersion().getVersion()),"V3 expected");edit(c);event("prepare-edited",c);}event("prepare-closed",null);migrate();try(var c=open()){event("reconnected",c);}event("finished",null);break;}
   case "migrate":{migrate();try(var c=open()){event("reconnected",c);}event("finished",null);break;}
   default:throw new IllegalArgumentException(args[0]);
  }
 }
}
