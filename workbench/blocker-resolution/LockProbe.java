import java.nio.file.*;
import java.sql.*;
import java.util.*;
public class LockProbe extends BareProbe {
 public static void main(String[] a)throws Exception {
  Path p=file(a[0]);url=connection(p);try(var c=DriverManager.getConnection(url,"sa","")){throw new IllegalStateException("Unexpected concurrent file access");}
  catch(SQLException e){require(e.getErrorCode()==90020,"Unexpected SQL error "+e.getErrorCode());json.writeValue(Path.of(a[1]).toFile(),Map.of("pid",ProcessHandle.current().pid(),"jdbcUrl",url,"sqlErrorCode",e.getErrorCode(),"blocked",true));}
 }
}
