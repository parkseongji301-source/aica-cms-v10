import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Separate, idempotent candidate-data rehearsal; never infers categories or accepts fixture IDs. */
public class CandidateDictionary extends CutoverAudit {
 static final String[][] cohorts={{"COHORT_06","6기"},{"COHORT_07","7기"}};
 static final String[][] topics={{"REVIEW_LIFE","생활"},{"REVIEW_CLASS","수업"},{"REVIEW_PROJECT","프로젝트"},{"FAQ_PREPARATION","준비사항"},{"FAQ_APPLICATION","지원·선발"},{"FAQ_CLASS","수업"},{"FAQ_LIFE","생활"},{"FAQ_EMPLOYMENT","취업"},{"FAQ_ALLOWANCE","지원금"},{"FAQ_PROJECT","프로젝트"}};
 static long ensure(Connection c,String table,String code,String name,int order)throws Exception {
  try(var p=c.prepareStatement("SELECT id,name,active FROM "+table+" WHERE code=?")){p.setString(1,code);try(var r=p.executeQuery()){if(r.next()){require(r.getString(2).equals(name)&&r.getBoolean(3),"Conflicting dictionary value: "+code);return r.getLong(1);}}}
  try(var p=c.prepareStatement("INSERT INTO "+table+"(code,name,sort_order) VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS)){p.setString(1,code);p.setString(2,name);p.setInt(3,order);p.executeUpdate();try(var r=p.getGeneratedKeys()){require(r.next(),"Missing generated ID");return r.getLong(1);}}
 }
 public static void main(String[] args)throws Exception{
  var db=file(args[0]);var output=Path.of(args[1]);url=connection(db);
  var report=new LinkedHashMap<String,Object>();
  try(var c=DriverManager.getConnection(url,"sa","")){
   require("10".equals(flyway(null).info().current().getVersion().getVersion()),"V10 required");c.setAutoCommit(false);
   try{
    try(var lock=c.createStatement()){lock.executeQuery("SELECT id FROM cms_guard WHERE id=1 FOR UPDATE").close();}
    require(count(c,"SELECT COUNT(*) FROM content_types WHERE code IN ('GENERAL','REVIEW','RESTAURANT','INTERVIEW','FAQ') AND active=TRUE")==5,"Registered types missing/inactive");
    for(int i=0;i<cohorts.length;i++)ensure(c,"cohorts",cohorts[i][0],cohorts[i][1],i+6);
    for(int i=0;i<topics.length;i++){
     String code=topics[i][0],type=code.startsWith("REVIEW_")?"REVIEW":"FAQ";long id=ensure(c,"topics",code,topics[i][1],i);
     try(var p=c.prepareStatement("SELECT type_code FROM content_type_topics WHERE topic_id=?")){p.setLong(1,id);try(var r=p.executeQuery()){while(r.next())require(r.getString(1).equals(type),"Topic shared across different types: "+code);}}
     try(var p=c.prepareStatement("INSERT INTO content_type_topics(type_code,topic_id) SELECT ?,? WHERE NOT EXISTS(SELECT 1 FROM content_type_topics WHERE type_code=? AND topic_id=?)")){p.setString(1,type);p.setLong(2,id);p.setString(3,type);p.setLong(4,id);p.executeUpdate();}
    }
    c.commit();report.put("candidateOnly",true);report.put("types",query(c,"SELECT * FROM content_types ORDER BY sort_order"));report.put("cohorts",query(c,"SELECT * FROM cohorts ORDER BY code"));report.put("topics",query(c,"SELECT * FROM topics ORDER BY code"));report.put("allowances",query(c,"SELECT a.type_code,t.code FROM content_type_topics a JOIN topics t ON t.id=a.topic_id ORDER BY 1,2"));report.put("snapshot",snapshot(c));
   }catch(Exception e){c.rollback();throw e;}
   c.setAutoCommit(true);try(var s=c.createStatement()){s.execute("SHUTDOWN");}
  }
  try(var c=DriverManager.getConnection(url+";ACCESS_MODE_DATA=r","sa","")){require(json.valueToTree(report.get("snapshot")).equals(json.valueToTree(snapshot(c))),"Data changed after reconnect");}
  json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(),report);System.out.println("Candidate dictionary committed and reopened; IDs generated from codes");
 }
}
