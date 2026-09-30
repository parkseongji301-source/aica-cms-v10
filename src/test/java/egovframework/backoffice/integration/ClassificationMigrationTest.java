package egovframework.backoffice.integration;

import egovframework.backoffice.mvp.config.ClassificationMigrationConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ClassificationMigrationTest {
    @TempDir Path directory;
    static Flyway flyway(String url,String target){
        var config=Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration/h2");
        if(target!=null)config.target(target);
        return config.load();
    }
    static String url(Path file){return "jdbc:h2:file:"+file.toAbsolutePath().toString().replace('\\','/')+";DB_CLOSE_ON_EXIT=FALSE;AUTO_COMPACT_FILL_RATE=0";}

    @Test void migrationPreservesEveryLegacyColumnAndDoesNotInferTerms()throws Exception {
        String url=url(directory.resolve("migration-copy"));flyway(url,"3").migrate();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO users(id,email,display_name,password_hash,role) VALUES(1,'existing@test','운영자','unchanged-hash','ADMIN')");
            s.executeUpdate("INSERT INTO categories(id,name) VALUES(11,'7기'),(12,'생활'),(13,'FAQ')");
            s.executeUpdate("INSERT INTO posts(id,title,content,author_id,category_id,status,revision) VALUES(33,'초안','초안 본문',1,12,'PUBLISHED',8),(34,'분류 없는 글','본문',1,NULL,'DRAFT',0)");
            s.executeUpdate("INSERT INTO post_publications(post_id,title,content,category_id,revision) VALUES(33,'이전 발행 제목','발행 본문',11,6)");
            s.executeUpdate("INSERT INTO site_pages(id,title,slug,sections_json,author_id) VALUES(65,'인사교 소개','intro','[{\"type\":\"POSTS\",\"categoryId\":11}]',1)");
            s.executeUpdate("INSERT INTO site_menus(label,kind,target_id) VALUES('7기','CATEGORY',11),('소개','PAGE',65)");
        }
        verifyMigration(url,null);
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            assertThat(s.executeQuery("SELECT category_id FROM posts WHERE id=33").next()).isTrue();
            // The old application publisher cannot silently omit the new published classification.
            assertThatThrownBy(()->s.executeUpdate("INSERT INTO post_publications(post_id,title,content,revision) VALUES(34,'old writer','body',0)"))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeUpdate("INSERT INTO site_menus(label,kind) VALUES('not allowed','GROUP')"))
                .isInstanceOf(SQLException.class);
        }
    }

    @Test void onlyReviewedMigrationsAreOnRuntimePath()throws Exception {
        String url="jdbc:h2:mem:resolved-migrations;DB_CLOSE_DELAY=-1";
        var scripts=Arrays.stream(flyway(url,null).info().all()).map(i->i.getScript()).toList();
        assertThat(scripts).containsExactly("V1__backoffice.sql","V2__cms.sql","V3__rich_editor.sql",
            "V4__content_classification_schema.sql","V5__content_classification_baseline.sql","V6__content_classification_constraints.sql","V7__restaurant_details.sql","db.migration.h2.V8__page_block_identity","V9__page_templates.sql","V10__document_versions.sql","V11__post_trash.sql","V12__writing_templates.sql","V13__page_hierarchy.sql","V14__site_composition.sql","V15__structure_membership.sql","V16__content_work_nodes.sql","V17__content_work_visibility.sql");
        assertThatThrownBy(()->Class.forName("db.migration.h2.V4__navigation")).isInstanceOf(ClassNotFoundException.class);
        var archive=Path.of("workbench/navigation-draft/src/main/java/db/migration/h2/V4__navigation.java.txt");
        assertThat(archive).exists();
        assertThat(Files.readString(archive)).contains("V4__navigation");
    }

    @Test void fileDatabasesRequireExplicitCopyValidationAndLocalProfileIsAlwaysBlocked() {
        assertThatThrownBy(()->ClassificationMigrationConfiguration.requireValidationDatabase("jdbc:h2:file:./.local-data/aica-local",true,true))
            .hasMessageContaining("original DB migration is disabled");
        assertThatThrownBy(()->ClassificationMigrationConfiguration.requireValidationDatabase("jdbc:h2:file:copy",false,false))
            .hasMessageContaining("copied DB");
        assertThatThrownBy(()->ClassificationMigrationConfiguration.requireValidationDatabase("jdbc:h2:file:./.local-data/aica-local",false,true))
            .hasMessageContaining("original DB migration is disabled");
        assertThatThrownBy(()->ClassificationMigrationConfiguration.requireValidationDatabase("jdbc:h2:file:.local-data/aica-local",false,true))
            .hasMessageContaining("original DB migration is disabled");
        ClassificationMigrationConfiguration.requireValidationDatabase("jdbc:h2:mem:test",false,false);
        ClassificationMigrationConfiguration.requireValidationDatabase(url(directory.resolve("copy")),false,true);
    }

    /** Hash all pre-existing columns (including blobs/clobs), retaining exactly their original row counts. */
    static Map<String,List<String>> columns(Connection c)throws Exception {
        var tables=new TreeMap<String,List<String>>();
        try(var r=c.getMetaData().getTables(null,"PUBLIC","%",new String[]{"TABLE"})) {
            while(r.next()) {
                String name=r.getString("TABLE_NAME");if(name.equals("flyway_schema_history"))continue;
                var fields=new ArrayList<String>();
                try(var cols=c.getMetaData().getColumns(null,"PUBLIC",name,"%")){while(cols.next())fields.add(cols.getString("COLUMN_NAME"));}
                tables.put(name,fields);
            }
        }
        return tables;
    }
    static Map<String,String> fingerprints(Connection c,Map<String,List<String>> tables)throws Exception {
        var result=new TreeMap<String,String>();
        for(var table:tables.entrySet()) {
            var rows=new ArrayList<String>();
            try(var s=c.createStatement();var r=s.executeQuery("SELECT "+String.join(",",table.getValue())+" FROM "+table.getKey())) {
                while(r.next()) {
                    var row=new StringBuilder();
                    for(int i=1;i<=table.getValue().size();i++) {
                        int type=r.getMetaData().getColumnType(i);
                        boolean binary=type==Types.BLOB||type==Types.BINARY||type==Types.VARBINARY||type==Types.LONGVARBINARY;
                        String text=binary?null:r.getString(i);
                        byte[] bytes=binary?r.getBytes(i):(text==null?null:text.getBytes(StandardCharsets.UTF_8));
                        row.append(bytes==null?"NULL":Base64.getEncoder().encodeToString(bytes)).append('|');
                    }
                    rows.add(row.toString());
                }
            }
            Collections.sort(rows);
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\n",rows).getBytes(StandardCharsets.UTF_8)));
            result.put(table.getKey(),rows.size()+":"+hash);
        }
        return result;
    }
    static void verifyMigration(String url,Path report)throws Exception {
        Map<String,List<String>> columns;Map<String,String> before;
        try(var c=DriverManager.getConnection(url,"sa","")){columns=columns(c);before=fingerprints(c,columns);}
        assertThat(columns.keySet()).contains("POSTS","POST_PUBLICATIONS","CATEGORIES","SITE_MENUS","SITE_PAGES","USERS","MEDIA");
        // Mirror the running app's datasource lifetime, then explicitly verify a fresh reopen below.
        // Keep the file DB open across repeated calls, then check durability after that connection closes.
        try(var lifetime=DriverManager.getConnection(url,"sa","")) {
            var migration=flyway(url,"6");assertThat(migration.info().current().getVersion().getVersion()).isEqualTo("3");
            assertThat(migration.migrate().migrationsExecuted).isEqualTo(3);
            assertThat(migration.migrate().migrationsExecuted).isZero();
        }
        var reopened=flyway(url,"6");
        assertThat(reopened.info().current().getVersion().getVersion()).isEqualTo("6");
        assertThat(reopened.migrate().migrationsExecuted).isZero();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            assertThat(fingerprints(c,columns)).isEqualTo(before);
            for(String table:List.of("posts","post_publications")) {
                try(var r=s.executeQuery("SELECT COUNT(*) FROM "+table+" WHERE content_type_code<>'GENERAL'")){r.next();assertThat(r.getLong(1)).isZero();}
            }
            for(String table:List.of("cohorts","topics","content_type_topics","post_cohorts","post_topics","post_publication_cohorts","post_publication_topics")) {
                try(var r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();assertThat(r.getLong(1)).isZero();}
            }
            try(var r=s.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='SITE_MENUS' AND COLUMN_NAME='PARENT_ID'")){r.next();assertThat(r.getLong(1)).isZero();}
            try(var r=s.executeQuery("SELECT COUNT(*) FROM content_types")){r.next();assertThat(r.getLong(1)).isEqualTo(5);}
        }
        if(report!=null)Files.writeString(report,"V3 -> V6; second migration: 0; all legacy column hashes unchanged\n"+before+"\nGENERAL for all drafts/publications; no cohorts/topics/IA seeded\n");
    }
}
