package egovframework.backoffice.integration;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.assertj.core.api.Assertions.*;

/** Run explicitly against the cold copy, never the original .local-data database. */
@EnabledIfSystemProperty(named="aica.validationCopy",matches=".+")
class LocalCopyClassificationMigrationTest {
    @Test void migrateActualLocalCopyAndCompareEveryExistingColumn()throws Exception {
        Path allowed=Path.of(".cache/react-phase3b2a-data").toRealPath();
        Path file=Path.of(System.getProperty("aica.validationCopy")).toRealPath();
        assertThat(file.startsWith(allowed)).isTrue();assertThat(file.getFileName().toString()).endsWith(".mv.db");
        String base=file.toString().substring(0,file.toString().length()-6);
        ClassificationMigrationTest.verifyMigration(ClassificationMigrationTest.url(Path.of(base)),allowed.resolve("migration-verification.txt"));
    }
}
