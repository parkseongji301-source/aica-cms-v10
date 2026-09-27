package egovframework.backoffice.mvp.operations;

import java.nio.file.*;
import java.util.*;
import static egovframework.backoffice.mvp.operations.FileDatabaseSafety.*;

/** Read-only relocation inspection using the unchanged approved RC's safety/Flyway code. */
public final class RelocationInspect {
    public static void main(String[] args) throws Exception {
        require(args.length == 3, "usage: database output expected-rc-sha256");
        Path db = Path.of(args[0]);
        require(db.isAbsolute(), "absolute database path required");
        db = db.toRealPath();
        Path output = Path.of(args[1]);
        require(!Files.exists(output), "inspection output already exists");
        String jarHash = hash(runtimeJar());
        require(jarHash.equals(args[2]), "approved RC checksum mismatch");
        require(!"true".equalsIgnoreCase(System.getenv("AICA_CUTOVER_ENABLED")), "cutover mode is forbidden for relocation");
        String user = System.getenv().getOrDefault("AICA_DB_USER", "sa");
        String password = System.getenv().getOrDefault("AICA_DB_PASSWORD", "");
        String before = hash(db);
        try {
            requireWriter(writerUrl(db)); // Check the exact options normal serve will use; no writer is opened.
            requireV10(db, user, password); // Existing RC: read-only Flyway validate and pending=0.
            var flyway = flyway(readUrl(db), user, password, null);
            var manifest = migrations(flyway); // Exactly V1..V10, resolved from this RC.
            var data = inspect(db, user, password);
            require(JSON.valueToTree(manifest).equals(JSON.valueToTree(data.get("history"))), "V10 history/checksum differs from RC manifest");
            data.put("jarSha256", jarHash);
            data.put("migrations", manifest);
            data.put("validated", true);
            data.put("pending", flyway.info().pending().length);
            data.put("migrationsExecuted", 0);
            data.put("writerUrl", writerUrl(db));
            data.put("bytesUnchanged", before.equals(hash(db)));
            require(before.equals(hash(db)), "relocation inspection changed database bytes");
            Files.writeString(output, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(data), StandardOpenOption.CREATE_NEW);
        } finally {
            require(before.equals(hash(db)), "read-only relocation changed database bytes");
        }
    }
}
