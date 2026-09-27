import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** No Flyway object, no Spring, no Hikari: test H2 commit/close/reopen only. */
public class JdbcOnlyProbe extends BareProbe {
 public static void main(String[] a)throws Exception {
  Path p=file(a[0]);url=connection(p)+System.getProperty("probe.options","");var result=new ArrayList<Object>();
  for(int i=0;i<6;i++)try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
   var row=new LinkedHashMap<String,Object>();row.put("round",i);row.put("pid",ProcessHandle.current().pid());row.put("url",url);row.put("databasePath",query(c,"SELECT DATABASE_PATH() AS path"));row.put("before",query(c,"SELECT revision FROM site_pages WHERE id=65"));
   s.executeUpdate("UPDATE site_pages SET revision=revision+1 WHERE id=65");row.put("after",query(c,"SELECT revision FROM site_pages WHERE id=65"));result.add(row);
  }
  json.writerWithDefaultPrettyPrinter().writeValue(Path.of(a[1]).toFile(),result);
 }
}
