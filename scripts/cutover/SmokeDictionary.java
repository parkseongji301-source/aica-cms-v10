import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;

/** Fixture writer only; extracts the unmodified H2 library from the pinned RC. */
public final class SmokeDictionary {
    public static void main(String[] args) throws Exception {
        Path file=Path.of(args[0]).toRealPath();
        if(!"true".equals(System.getenv("AICA_ISOLATED_SMOKE")) || !file.getFileName().toString().equals("fixture.mv.db")
           || !file.getParent().getFileName().toString().equals("smoke") || file.toString().replace('\\','/').contains("/.local-data/"))
            throw new IllegalStateException("Isolated smoke fork only");
        String base=file.toString().replace('\\','/');base=base.substring(0,base.length()-6);
        try(var c=DriverManager.getConnection("jdbc:h2:file:"+base+";IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE;AUTO_COMPACT_FILL_RATE=0",
                System.getenv().getOrDefault("AICA_DB_USER","sa"),System.getenv().getOrDefault("AICA_DB_PASSWORD",""))) {
            try(var s=c.createStatement();var r=s.executeQuery("SELECT MAX(CAST(\"version\" AS INT)) FROM \"flyway_schema_history\"")) {
                r.next();if(r.getInt(1)!=10)throw new IllegalStateException("V10 required");
            }
            try(var reader=Files.newBufferedReader(Path.of(args[1]),StandardCharsets.UTF_8)){org.h2.tools.RunScript.execute(c,reader);}
        }
    }
}
