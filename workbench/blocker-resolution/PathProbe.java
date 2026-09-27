import java.nio.file.*;
import java.sql.*;
import java.util.*;
public class PathProbe extends BareProbe {
 public static void main(String[] a)throws Exception {
  Path expected=file(a[0]);String relative=Path.of("").toAbsolutePath().relativize(expected).toString().replace('\\','/');
  var results=new ArrayList<Object>();
  for(String base:List.of(expected.toString().substring(0,expected.toString().length()-6).replace('\\','/'),"./"+relative.substring(0,relative.length()-6),expected.toString().replace('\\','/'))) {
   String jdbc="jdbc:h2:file:"+base+";IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE;ACCESS_MODE_DATA=r";var r=new LinkedHashMap<String,Object>();r.put("jdbc",jdbc);r.put("workingDirectory",Path.of("").toAbsolutePath().toString());
   try(var c=DriverManager.getConnection(jdbc,"sa","")){r.put("path",query(c,"SELECT DATABASE_PATH() AS path"));r.put("history",history(c));}catch(SQLException e){r.put("sqlErrorCode",e.getErrorCode());r.put("state",e.getSQLState());}
   results.add(r);
  }
  json.writerWithDefaultPrettyPrinter().writeValue(Path.of(a[1]).toFile(),results);
 }
}
