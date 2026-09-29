import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;

/** Resolve real Flyway checksums without opening any file database or running migration. */
public final class MigrationManifest {
 public static void main(String[] args)throws Exception {
  var f=Flyway.configure().dataSource("jdbc:h2:mem:migration_manifest","sa","")
   .locations("classpath:db/migration/h2").cleanDisabled(true).load();
  var rows=new ArrayList<Map<String,Object>>();
  for(var m:f.info().all()){
   if(m.getVersion()==null)throw new IllegalStateException("Unexpected unversioned migration");
   var row=new LinkedHashMap<String,Object>();row.put("version",m.getVersion().getVersion());
   row.put("script",m.getScript());row.put("checksum",m.getChecksum());rows.add(row);
  }
  Files.writeString(Path.of(args[0]),new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(rows),StandardOpenOption.CREATE_NEW);
 }
}
