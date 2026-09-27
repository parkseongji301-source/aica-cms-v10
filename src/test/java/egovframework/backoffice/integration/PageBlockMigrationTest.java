package egovframework.backoffice.integration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import db.migration.h2.V8__page_block_identity;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class PageBlockMigrationTest {
    @TempDir Path directory;
    static final ObjectMapper JSON=new ObjectMapper();
    static Map<String,JsonNode> documents(Connection c)throws Exception {
        var out=new TreeMap<String,JsonNode>();
        for(String table:List.of("site_pages","page_publications")) {
            String key=table.equals("site_pages")?"id":"page_id";
            try(var s=c.createStatement();var rs=s.executeQuery("SELECT "+key+",sections_json FROM "+table)){while(rs.next())out.put(table+":"+rs.getLong(1),JSON.readTree(rs.getString(2)));}
        }
        return out;
    }
    static void verify(String url)throws Exception {
        Map<String,List<String>> columns;Map<String,String> before;Map<String,JsonNode> documents,after;List<String> history=new ArrayList<>();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            columns=ClassificationMigrationTest.columns(c);columns.get("SITE_PAGES").remove("SECTIONS_JSON");columns.get("PAGE_PUBLICATIONS").remove("SECTIONS_JSON");before=ClassificationMigrationTest.fingerprints(c,columns);documents=documents(c);
            try(var rs=s.executeQuery("SELECT \"version\" || ':' || \"checksum\" FROM \"flyway_schema_history\" ORDER BY \"installed_rank\"")){while(rs.next())history.add(rs.getString(1));}
            var f=ClassificationMigrationTest.flyway(url,"8");assertThat(f.info().current().getVersion().getVersion()).isEqualTo("7");assertThat(f.migrate().migrationsExecuted).isEqualTo(1);assertThat(f.migrate().migrationsExecuted).isZero();after=documents(c);
            var all=ClassificationMigrationTest.columns(c);var once=ClassificationMigrationTest.fingerprints(c,all);V8__page_block_identity.upgrade(c);assertThat(ClassificationMigrationTest.fingerprints(c,all)).isEqualTo(once);
        }
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            assertThat(ClassificationMigrationTest.fingerprints(c,columns)).isEqualTo(before);assertThat(documents(c)).isEqualTo(after);
            for(var entry:after.entrySet()) {
                var stripped=(ArrayNode)entry.getValue().deepCopy();var ids=new HashSet<String>();
                for(var block:stripped) {assertThat(block.path("id").asText()).matches("block_[0-9a-f-]{36}");assertThat(ids.add(block.path("id").asText())).isTrue();assertThat(block.path("schemaVersion").asInt()).isEqualTo(2);assertThat(block.path("variation").asText()).isEqualTo("default");((ObjectNode)block).remove(List.of("id","schemaVersion","variation"));}
                assertThat(stripped).isEqualTo(documents.get(entry.getKey()));
            }
            var next=new ArrayList<String>();try(var rs=s.executeQuery("SELECT \"version\" || ':' || \"checksum\" FROM \"flyway_schema_history\" ORDER BY \"installed_rank\"")){while(rs.next())next.add(rs.getString(1));}assertThat(next.subList(0,history.size())).isEqualTo(history);
            var reopened=ClassificationMigrationTest.flyway(url,"8");assertThat(reopened.info().current().getVersion().getVersion()).isEqualTo("8");assertThat(reopened.migrate().migrationsExecuted).isZero();
        }
    }
    String fixture()throws Exception {
        String url=ClassificationMigrationTest.url(directory.resolve("copy"));ClassificationMigrationTest.flyway(url,"7").migrate();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {s.executeUpdate("INSERT INTO users(id,email,display_name,password_hash,role) VALUES(1,'test@test','운영자','hash','ADMIN')");}
        return url;
    }
    void page(Connection c,long id,long revision,long publishedRevision,String text)throws Exception {
        try(var s=c.prepareStatement("INSERT INTO site_pages(id,title,slug,sections_json,revision,author_id) VALUES(?,'기존 페이지',?,?,?,1)")){s.setLong(1,id);s.setString(2,"page-"+id);s.setString(3,text);s.setLong(4,revision);s.executeUpdate();}
        try(var s=c.prepareStatement("INSERT INTO page_publications(page_id,title,slug,sections_json,revision) SELECT id,title,slug,sections_json,? FROM site_pages WHERE id=?")){s.setLong(1,publishedRevision);s.setLong(2,id);s.executeUpdate();}
    }
    @Test void snapshotEvidenceSharesIdsButDivergedRevisionsNeverGuessEvenForIdenticalContent()throws Exception {
        String url=fixture(),text="[{\"type\":\"TEXT\",\"body\":\"같은 내용\",\"visible\":false},{\"type\":\"TEXT\",\"body\":\"같은 내용\",\"visible\":true}]";
        try(var c=DriverManager.getConnection(url,"sa","")){page(c,1,3,3,text);page(c,65,4,3,text);}
        verify(url);
        try(var c=DriverManager.getConnection(url,"sa","")) {
            var d=documents(c);assertThat(d.get("site_pages:1")).isEqualTo(d.get("page_publications:1"));
            var draft=d.get("site_pages:65");var published=d.get("page_publications:65");assertThat(draft.get(0).path("id")).isNotEqualTo(published.get(0).path("id"));
            try(var s=c.createStatement();var rs=s.executeQuery("SELECT COUNT(*) FROM page_block_identities WHERE retired=TRUE")){rs.next();assertThat(rs.getInt(1)).isEqualTo(2);}
        }
    }
    @Test @EnabledIfSystemProperty(named="aica.blockValidationCopy",matches=".+")
    void actualV7CopyPreservesEveryOldColumnAndBlockValue()throws Exception {
        Path file=Path.of(System.getProperty("aica.blockValidationCopy")).toRealPath();assertThat(file.startsWith(Path.of(".cache/react-phase4a-data").toRealPath())).isTrue();assertThat(file.getFileName().toString()).startsWith("aica-phase4a").endsWith(".mv.db");
        verify(ClassificationMigrationTest.url(Path.of(file.toString().substring(0,file.toString().length()-6))));
    }
    @Test void malformedLaterPageStopsBeforeAnyWritesOrIdentityTableCreation()throws Exception {
        String url=fixture();try(var c=DriverManager.getConnection(url,"sa","")) {
            page(c,1,0,0,"[{\"type\":\"TEXT\",\"body\":\"보존\"}]");page(c,65,0,0,"{bad");
            assertThatThrownBy(()->V8__page_block_identity.upgrade(c)).isInstanceOf(Exception.class);
            try(var s=c.createStatement();var r=s.executeQuery("SELECT sections_json FROM site_pages WHERE id=1")){r.next();assertThat(r.getString(1)).doesNotContain("schemaVersion","block_");}
            try(var r=c.getMetaData().getTables(null,"PUBLIC","PAGE_BLOCK_IDENTITIES",null)){assertThat(r.next()).isFalse();}
        }
    }
    @Test void duplicateIdsAcrossPagesFailBeforeConversionWrites()throws Exception {
        String url=fixture(),text="[{\"id\":\"block_00000000-0000-4000-8000-000000000001\",\"schemaVersion\":2,\"variation\":\"default\",\"type\":\"TEXT\"}]";
        try(var c=DriverManager.getConnection(url,"sa","")){page(c,1,0,0,text);page(c,65,0,0,text);assertThatThrownBy(()->V8__page_block_identity.upgrade(c)).isInstanceOf(IllegalStateException.class);try(var r=c.getMetaData().getTables(null,"PUBLIC","PAGE_BLOCK_IDENTITIES",null)){assertThat(r.next()).isFalse();}}
    }
}
