import java.nio.file.*;
import java.util.*;
import egovframework.backoffice.mvp.operations.FileDatabaseSafety;

/** Only prepares the opt-in migration-test fixtures. Not a cutover entry point. */
public final class PrepareCopies {
 public static void main(String[] args)throws Exception {
  Path root=Path.of("").toRealPath(),source=Path.of(args[0]).toRealPath();
  if(!source.startsWith(root.resolve(".cache/phase5c1c"))||!source.getFileName().toString().equals("v3-backup.mv.db"))throw new IllegalStateException("5C-1C backup only");
  var targets=new LinkedHashMap<String,String>();
  targets.put(".cache/react-phase3b2a-data/aica-phase3c3-5c1c-v6.mv.db","6");
  targets.put(".cache/react-phase4a-data/aica-phase4a-5c1c-v7.mv.db","7");
  targets.put(".cache/react-phase4d-data/aica-phase4d-5c1c-v8.mv.db","8");
  targets.put(".cache/phase5b2b2-data/aica-5c1c-v9.mv.db","9");
  targets.put(".cache/phase5b2b2-data/aica-5c1c-v3.mv.db","3");
  for(var entry:targets.entrySet()) {
   Path dest=root.resolve(entry.getKey());Files.copy(source,dest);
   if(!entry.getValue().equals("3"))FileDatabaseSafety.flyway(FileDatabaseSafety.writerUrl(dest),"sa","",entry.getValue()).migrate();
   System.out.println("Prepared V"+entry.getValue()+" "+dest);
  }
 }
}
