package egovframework.backoffice.integration;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static egovframework.backoffice.integration.ClassificationMigrationTest.*;
import static egovframework.backoffice.mvp.operations.FileDatabaseSafety.*;
import static org.assertj.core.api.Assertions.*;

class PostTrashMigrationTest {
    @TempDir Path directory;
    @Test void v11PreservesEveryV10ColumnAndDoesNotRestoreLegacyDeletedRows()throws Exception {
        Path base=directory.resolve("trash-copy");String url=url(base);flyway(url,"10").migrate();
        Map<String,List<String>> columns;Map<String,String> before;
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()){
            s.executeUpdate("INSERT INTO users(id,email,display_name,password_hash,role) VALUES(1,'migration@trash.test','기존 관리자','unchanged','SUPER_ADMIN')");
            s.executeUpdate("INSERT INTO posts(id,title,content,author_id,status,revision) VALUES(1,'살아 있는 글','본문',1,'DRAFT',7),(2,'과거 영구삭제','본문',1,'DRAFT',3)");
            s.executeUpdate("UPDATE posts SET deleted_at=CURRENT_TIMESTAMP WHERE id=2");
            columns=ClassificationMigrationTest.columns(c);before=fingerprints(c,columns);
        }
        Path file=Path.of(base+".mv.db").toRealPath();String hash=hash(file);
        requireV10(file,"sa","");assertThatThrownBy(()->requireCurrentSchema(file,"sa","")).isInstanceOf(Exception.class);assertThat(hash(file)).isEqualTo(hash);
        var migration=flyway(url,null);assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);migration.validate();
        assertThat(migration.migrate().migrationsExecuted).isZero();requireCurrentSchema(file,"sa","");
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()){
            assertThat(fingerprints(c,columns)).isEqualTo(before);
            try(var r=s.executeQuery("SELECT COUNT(*) FROM post_trash")){assertThat(r.next()).isTrue();assertThat(r.getLong(1)).isZero();}
            s.executeUpdate("INSERT INTO post_trash(post_id) VALUES(2)");s.executeUpdate("DELETE FROM posts WHERE id=2");
            try(var r=s.executeQuery("SELECT COUNT(*) FROM post_trash")){assertThat(r.next()).isTrue();assertThat(r.getLong(1)).isZero();}
        }
    }
    @Test void newRuntimeRejectsAnOldReceiptEvenIfJarAndPathMatch()throws Exception {
        Path base=directory.resolve("receipt-copy");flyway(url(base),null).migrate();Path file=Path.of(base+".mv.db").toRealPath(),receipt=directory.resolve("receipt.json");
        JSON.writeValue(receipt.toFile(),Map.of("status","MIGRATED_V10","databasePath",file.toString(),"jarSha256","same","workaround","AUTO_COMPACT_FILL_RATE=0"));
        assertThatThrownBy(()->requireReceipt(file,receipt,"same",CURRENT_VERSION)).hasMessageContaining("V11");
    }
}
