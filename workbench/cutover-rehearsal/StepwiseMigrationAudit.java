import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** One-off audit: accepts only existing disposable copies; never opens the source backup. */
public class StepwiseMigrationAudit {
  static String url;
  static final ObjectMapper json=new ObjectMapper();
  static Flyway flyway(String target) {
    var config=Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration/h2");
    if(target!=null) config.target(target);
    return config.load();
  }
  static void require(boolean condition,String reason) {if(!condition)throw new IllegalStateException(reason);}
  static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
  static Map<String,List<String>> columns(Connection c)throws Exception {
    var tables=new TreeMap<String,List<String>>();
    try(var r=c.getMetaData().getTables(null,"PUBLIC","%",new String[]{"TABLE"})) {
      while(r.next()) {
        String name=r.getString("TABLE_NAME");if(name.equals("flyway_schema_history"))continue;
        var fields=new ArrayList<String>();
        try(var cols=c.getMetaData().getColumns(null,"PUBLIC",name,"%")){while(cols.next())fields.add(cols.getString("COLUMN_NAME"));}
        tables.put(name,fields);
      }
    }
    return tables;
  }
  static Map<String,Object> fingerprint(Connection c,Map<String,List<String>> tables)throws Exception {
    var result=new TreeMap<String,Object>();
    for(var table:tables.entrySet()) {
      var rows=new ArrayList<String>();
      try(var s=c.createStatement();var r=s.executeQuery("SELECT "+String.join(",",table.getValue())+" FROM "+table.getKey())) {
        while(r.next()) {
          var row=new StringBuilder();
          for(int i=1;i<=table.getValue().size();i++) {
            int type=r.getMetaData().getColumnType(i);
            boolean binary=type==Types.BLOB||type==Types.BINARY||type==Types.VARBINARY||type==Types.LONGVARBINARY;
            String text=binary?null:r.getString(i);
            byte[] bytes=binary?r.getBytes(i):(text==null?null:text.getBytes(StandardCharsets.UTF_8));
            row.append(bytes==null?"NULL":Base64.getEncoder().encodeToString(bytes)).append('|');
          }
          rows.add(row.toString());
        }
      }
      Collections.sort(rows);
      result.put(table.getKey(),Map.of("rows",rows.size(),"sha256",hash(String.join("\n",rows).getBytes(StandardCharsets.UTF_8))));
    }
    return result;
  }
  static List<Map<String,Object>> query(Connection c,String sql)throws Exception {
    var rows=new ArrayList<Map<String,Object>>();
    try(var s=c.createStatement();var r=s.executeQuery(sql)) {
      while(r.next()) {
        var row=new LinkedHashMap<String,Object>();
        for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getString(i));
        rows.add(row);
      }
    }
    return rows;
  }
  static List<Map<String,Object>> history(Connection c)throws Exception {
    return query(c,"SELECT * FROM \"flyway_schema_history\" ORDER BY \"installed_rank\"");
  }
  static long count(Connection c,String sql)throws Exception {
    try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}
  }
  public static void main(String[] args)throws Exception {
    Path allowed=Path.of(".cache/react-phase3b2a-data").toRealPath(),file=Path.of(args[0]).toRealPath();
    require(file.startsWith(allowed)&&file.toString().endsWith(".mv.db"),"Only disposable verification copies allowed");
    String base=file.toString().substring(0,file.toString().length()-6).replace('\\','/');
    url="jdbc:h2:file:"+base+";IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE;AUTO_COMPACT_FILL_RATE=0";
    require("3".equals(flyway(null).info().current().getVersion().getVersion()),"Expected latest original V3 copy");
    var report=new LinkedHashMap<String,Object>();
    Map<String,List<String>> columns;
    Map<String,Object> before;
    List<Map<String,Object>> oldHistory;
    try(var c=DriverManager.getConnection(url,"sa","")) {
      columns=columns(c);before=fingerprint(c,columns);oldHistory=history(c);
      report.put("legacyColumns",columns);report.put("before",before);report.put("historyBefore",oldHistory);
      report.put("postIdsAndCategories",query(c,"SELECT id,category_id,status,revision FROM posts ORDER BY id"));
      report.put("publicationIdsAndCategories",query(c,"SELECT post_id,category_id,revision FROM post_publications ORDER BY post_id"));
      report.put("menus",query(c,"SELECT id,kind,target_id,visible,sort_order FROM site_menus ORDER BY id"));
      report.put("pageIds",query(c,"SELECT id,slug,status,revision FROM site_pages ORDER BY id"));
    }
    report.put("database",file.toString());report.put("location","classpath:db/migration/h2");
    report.put("resolvedScripts",Arrays.stream(flyway(null).info().all()).map(m->m.getScript()).toList());
    var steps=new ArrayList<Map<String,Object>>();
    for(String target:List.of("4","5","6")) {
      var migration=flyway(target);var result=migration.migrate();
      require(result.migrationsExecuted==1,"Expected exactly one migration to V"+target);
      require(target.equals(migration.info().current().getVersion().getVersion()),"Unexpected current version");
      var step=new LinkedHashMap<String,Object>();step.put("target",target);step.put("migrationsExecuted",result.migrationsExecuted);
      try(var c=DriverManager.getConnection(url,"sa","")) {
        var after=fingerprint(c,columns);require(before.equals(after),"Legacy values changed at V"+target);
        var nextHistory=history(c);require(nextHistory.subList(0,oldHistory.size()).equals(oldHistory),"Existing Flyway history changed");
        step.put("allLegacyColumnsUnchanged",true);step.put("tableFingerprints",after);step.put("history",nextHistory);
        var emptyCounts=new TreeMap<String,Long>();
        for(String table:List.of("cohorts","topics","content_type_topics","post_cohorts","post_topics","post_publication_cohorts","post_publication_topics")) {
          long n=count(c,"SELECT COUNT(*) FROM "+table);require(n==0,"Unexpected seed in "+table);emptyCounts.put(table,n);
        }
        step.put("emptyNewTaxonomyTables",emptyCounts);
        if(!target.equals("4")) {
          for(String table:List.of("posts","post_publications"))require(count(c,"SELECT COUNT(*) FROM "+table+" WHERE content_type_code IS NULL OR content_type_code<>'GENERAL'")==0,"Incorrect baseline type");
          require(count(c,"SELECT COUNT(*) FROM content_types")==5,"Expected only registered types");
          step.put("allExistingDraftsAndPublicationsGeneral",true);
        }
      }
      steps.add(step);report.put("steps",steps);
      json.writerWithDefaultPrettyPrinter().writeValue(Path.of(args[1]).toFile(),report);
    }
    var finalFlyway=flyway(null);finalFlyway.validate();
    int repeated=finalFlyway.migrate().migrationsExecuted;require(repeated==0,"Repeated migration should do nothing");
    report.put("repeatMigrationsExecuted",repeated);report.put("flywayValidatePassed",true);
    json.writerWithDefaultPrettyPrinter().writeValue(Path.of(args[1]).toFile(),report);
    System.out.println("PASS: V3 -> V4 -> V5 -> V6, all "+before.size()+" legacy tables unchanged after EACH step; repeat 0");
  }
}
