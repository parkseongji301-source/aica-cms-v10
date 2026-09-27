import com.sun.tools.attach.VirtualMachine;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

/** Read-only observer, loaded only into the explicitly launched disposable-copy JVM. */
public class RuntimeObserver {
 static Object call(Object o,String method)throws Exception{return o.getClass().getMethod(method).invoke(o);}
 static List<Object> rows(Connection c,String sql)throws Exception {
  var a=new ArrayList<Object>();try(var s=c.createStatement();var r=s.executeQuery(sql)){while(r.next()){var v=new LinkedHashMap<String,Object>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)v.put(r.getMetaData().getColumnLabel(i),r.getString(i));a.add(v);}}return a;
 }
 static String channelHash(FileChannel ch)throws Exception {
  var md=MessageDigest.getInstance("SHA-256");var b=ByteBuffer.allocate(65536);long p=0,size=ch.size();while(p<size){b.clear();b.limit((int)Math.min(b.capacity(),size-p));int n=ch.read(b,p);if(n<=0)throw new IllegalStateException("Unstable file length");md.update(b.array(),0,n);p+=n;}return HexFormat.of().formatHex(md.digest());
 }
 public static void agentmain(String arg,Instrumentation ins)throws Exception {
  // Arguments: output path | exact allowlisted physical file | complete effective JDBC URL.
  var args=arg.split("\\|",3);Path file=Path.of(args[1]).toRealPath();
  if(file.toString().replace('\\','/').contains("/.local-data/")||!file.getFileName().toString().startsWith("server-"))throw new SecurityException("Diagnostic copies only");
  Class<?> driverClass=null;for(var t:ins.getAllLoadedClasses())if(t.getName().equals("org.h2.Driver")){driverClass=t;break;}
  if(driverClass==null)throw new IllegalStateException("No H2 driver loaded");
  var loader=driverClass.getClassLoader();var mapper=Class.forName("com.fasterxml.jackson.databind.ObjectMapper",true,loader).getConstructor().newInstance();
  var driver=(Driver)driverClass.getConstructor().newInstance();var properties=new Properties();properties.setProperty("user","sa");properties.setProperty("password","");
  var out=new LinkedHashMap<String,Object>();out.put("pid",ProcessHandle.current().pid());out.put("at",java.time.Instant.now().toString());out.put("effectiveJdbcUrl",args[2]);out.put("physicalPath",file.toString());out.put("profile","dev");out.put("observer","SELECT only, same JVM H2 Engine; no second file-mode process");
  try(var c=driver.connect(args[2],properties)) {
   out.put("metadataUrl",c.getMetaData().getURL());out.put("databasePath",rows(c,"SELECT DATABASE_PATH() AS path"));out.put("history",rows(c,"SELECT * FROM \"flyway_schema_history\" ORDER BY \"installed_rank\""));
   for(var pair:Map.of("page1","SELECT id,title,revision,sections_json FROM site_pages WHERE id=1","page65","SELECT id,title,revision,sections_json FROM site_pages WHERE id=65","post33","SELECT id,title,revision,category_id,content FROM posts WHERE id=33").entrySet())out.put(pair.getKey(),rows(c,pair.getValue()));
   Object db=call(call(c,"getSession"),"getDatabase"),mv=call(call(db,"getStore"),"getMvStore"),fs=call(mv,"getFileStore");
   var field=fs.getClass().getDeclaredField("fileChannel");field.setAccessible(true);var ch=(FileChannel)field.get(fs);
   String h1=channelHash(ch),h2=channelHash(ch);out.put("liveSha256",h2);out.put("twoReadHashesEqual",h1.equals(h2));out.put("size",ch.size());out.put("modified",Files.getLastModifiedTime(file).toString());out.put("hashNote","Live observation through existing locked H2 channel; authoritative cold hash recorded after shutdown");
  }
  mapper.getClass().getMethod("writeValue",java.io.File.class,Object.class).invoke(mapper,Path.of(args[0]).toFile(),out);
 }
 public static void main(String[] args)throws Exception {var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(args[1],args[2]);}finally{vm.detach();}}
}
