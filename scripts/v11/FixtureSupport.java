import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.security.MessageDigest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static egovframework.backoffice.mvp.operations.FileDatabaseSafety.*;

/** Isolated rehearsal helper. Never included in the release JAR. */
public class FixtureSupport {
 public static void main(String[] args)throws Exception {
  Path root=Path.of(args[1]).toRealPath(),db=Path.of(args[2]).toRealPath();
  require(root.toString().contains("rehearsal")&&db.startsWith(root)&&!db.toString().replace('\\','/').contains("/.local-data/"),"isolated rehearsal copy required");
  if(args[0].equals("account")){
   try(var c=DriverManager.getConnection(writerUrl(db),"sa","");var s=c.prepareStatement("INSERT INTO users(email,display_name,password_hash,role,password_change_required) VALUES(?,?,?,'SUPER_ADMIN',FALSE)")){
    s.setString(1,"v11-rehearsal@example.test");s.setString(2,"V11 리허설 전용");s.setString(3,new BCryptPasswordEncoder().encode("V11-Rehearsal-Only-2026!"));s.executeUpdate();
   }
  }else if(args[0].equals("rows")){
   Map<String,Map<String,Integer>> out=new TreeMap<>();
   try(var c=DriverManager.getConnection(readUrl(db),"sa","")){
    for(var table:columns(c).keySet()){
     Map<String,Integer> hashes=new TreeMap<>();
     try(var s=c.createStatement();var r=s.executeQuery("SELECT * FROM \""+table+"\"")){
      while(r.next()){
       var h=MessageDigest.getInstance("SHA-256");
       for(int n=1;n<=r.getMetaData().getColumnCount();n++){
        boolean binary=Set.of(Types.BLOB,Types.BINARY,Types.VARBINARY,Types.LONGVARBINARY).contains(r.getMetaData().getColumnType(n));
        String value=binary?null:r.getString(n);
        byte[] b=binary?r.getBytes(n):(value==null?null:value.getBytes(java.nio.charset.StandardCharsets.UTF_8));h.update((byte)(b==null?0:1));
        if(b!=null){h.update(java.nio.ByteBuffer.allocate(4).putInt(b.length).array());h.update(b);}
       }
       hashes.merge(HexFormat.of().formatHex(h.digest()),1,Integer::sum);
      }
     }out.put(table,hashes);
    }
   }
   Files.writeString(Path.of(args[3]),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(out),StandardOpenOption.CREATE_NEW);
  }else throw new IllegalArgumentException("unknown helper operation");
 }
}
