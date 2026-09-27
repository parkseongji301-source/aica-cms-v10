package egovframework.backoffice.integration;

import egovframework.backoffice.mvp.operations.*;
import egovframework.backoffice.mvp.config.FileRuntimeConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.mock.env.MockEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static egovframework.backoffice.mvp.operations.FileDatabaseSafety.*;
import static org.assertj.core.api.Assertions.*;

class CutoverSafetyTest {
    @TempDir Path dir;
    Path v3() throws Exception {
        var base=dir.resolve("source");ClassificationMigrationTest.flyway(ClassificationMigrationTest.url(base),"3").migrate();return Path.of(base+".mv.db").toRealPath();
    }
    com.fasterxml.jackson.databind.node.ObjectNode plan(Path db) throws Exception {
        var p=JSON.createObjectNode();p.put("format",1).put("databasePath",db.toString()).put("migrationLocation",LOCATION).put("jarSha256","approvedJar").put("jdbcUrl",writerUrl(db)).put("expiresAt",Instant.now().plusSeconds(3600).toString()).put("dictionaryMode","DEFER");
        p.set("database",JSON.valueToTree(inspect(db,"sa","")));p.set("migrations",JSON.valueToTree(migrations(flyway(readUrl(db),"sa","",null))));return p;
    }
    @Test void writerOptionsRejectMissingDuplicateAlternativeAndRelativePathsBeforeOpening() throws Exception {
        Path db=v3();String initial=hash(db),url=writerUrl(db);
        assertThat(requireWriter(url)).isEqualTo(db);
        for(String bad:List.of(url.replace(";AUTO_COMPACT_FILL_RATE=0",""),url.replace("FILL_RATE=0","FILL_RATE=90"),url+";AUTO_COMPACT_FILL_RATE=0",url+";INIT=DROP ALL OBJECTS",url.replace(";IFEXISTS=TRUE",""),"jdbc:h2:file:./relative;IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE;AUTO_COMPACT_FILL_RATE=0"))
            assertThatThrownBy(()->requireWriter(bad)).isInstanceOf(Exception.class);
        assertThat(hash(db)).isEqualTo(initial);
    }
    @Test void planBindsFileJarHistoryMigrationsHashAndExpiryWithoutWriting() throws Exception {
        Path db=v3();var p=plan(db);String initial=hash(db);CutoverTool.validatePlan(p,db,"approvedJar","sa","");
        for(String field:List.of("databasePath","jarSha256","migrationLocation","dictionaryMode","jdbcUrl","expiresAt")) {
            var bad=p.deepCopy();bad.put(field,field.equals("expiresAt")?"2000-01-01T00:00:00Z":"wrong");
            assertThatThrownBy(()->CutoverTool.validatePlan(bad,db,"approvedJar","sa","")).isInstanceOf(Exception.class);
        }
        for(String child:List.of("sha256","fingerprints","history")) {
            var bad=p.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)bad.get("database")).put(child,"wrong");
            assertThatThrownBy(()->CutoverTool.validatePlan(bad,db,"approvedJar","sa","")).isInstanceOf(Exception.class);
        }
        var bad=p.deepCopy();bad.putArray("migrations");assertThatThrownBy(()->CutoverTool.validatePlan(bad,db,"approvedJar","sa","")).hasMessageContaining("migration manifest");
        assertThat(hash(db)).isEqualTo(initial);
    }
    @Test void normalFileRuntimeRefusesV3WithoutChangingIt() throws Exception {
        Path db=v3();String before=hash(db);
        assertThatThrownBy(()->requireV10(db,"sa","")).isInstanceOf(Exception.class);
        assertThat(hash(db)).isEqualTo(before);
    }
    @Test void hikariOverrideAndCutoverFlagCannotStartPool() throws Exception {
        Path db=v3();String before=hash(db);var props=new DataSourceProperties();props.setUrl(writerUrl(db));props.setUsername("sa");props.setPassword("");props.setDriverClassName("org.h2.Driver");
        var env=new MockEnvironment().withProperty("spring.datasource.hikari.jdbc-url",writerUrl(db).replace("FILL_RATE=0","FILL_RATE=90"));
        assertThatThrownBy(()->new FileRuntimeConfiguration().dataSource(props,env)).hasMessageContaining("URL mismatch");
        assertThatThrownBy(()->new FileRuntimeConfiguration().dataSource(props,new MockEnvironment().withProperty("AICA_CUTOVER_ENABLED","true"))).hasMessageContaining("cutover mode");
        assertThatThrownBy(()->new FileRuntimeConfiguration().dataSource(props,new MockEnvironment().withProperty("spring.datasource.hikari.connection-init-sql","SET AUTO_COMPACT_FILL_RATE 90"))).hasMessageContaining("init SQL");
        assertThatThrownBy(()->new FileRuntimeConfiguration().dataSource(props,new MockEnvironment().withProperty("spring.flyway.url",writerUrl(db)))).hasMessageContaining("separate Flyway");
        assertThatThrownBy(()->new FileRuntimeConfiguration().dataSource(props,new MockEnvironment().withProperty("spring.sql.init.mode","always"))).hasMessageContaining("initialization");
        assertThat(hash(db)).isEqualTo(before);
    }
    @Test void receiptRejectsOtherPathAndOtherRuntime() throws Exception {
        Path db=v3(),receipt=dir.resolve("receipt.json");
        JSON.writeValue(receipt.toFile(),Map.of("status","MIGRATED_V10","databasePath",db.toString(),"jarSha256","correct","workaround","AUTO_COMPACT_FILL_RATE=0"));
        requireReceipt(db,receipt,"correct");assertThatThrownBy(()->requireReceipt(db,receipt,"other")).hasMessageContaining("checksum");
        Path other=dir.resolve("other.mv.db");Files.copy(db,other);assertThatThrownBy(()->requireReceipt(other,receipt,"correct")).hasMessageContaining("path");
    }
}
