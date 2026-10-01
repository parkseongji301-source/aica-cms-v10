package egovframework.backoffice.integration;

import java.util.List;
import java.util.stream.IntStream;
import static egovframework.backoffice.mvp.operations.FileDatabaseSafety.CURRENT_VERSION;

/** Flyway versions a fresh database has applied: 1 up to the current schema, so a new migration only bumps the constant. */
final class SchemaVersions {
    private SchemaVersions() {}
    static List<String> applied() { return IntStream.rangeClosed(1,Integer.parseInt(CURRENT_VERSION)).mapToObj(String::valueOf).toList(); }
}
