package egovframework.backoffice.integration;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class RestaurantMigrationTest {
    @TempDir Path directory;
    static void verify(String url)throws Exception {
        Map<String,List<String>> columns;Map<String,String> before;List<String> history=new ArrayList<>();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            columns=ClassificationMigrationTest.columns(c);before=ClassificationMigrationTest.fingerprints(c,columns);
            try(var rs=s.executeQuery("SELECT \"version\" || ':' || \"checksum\" FROM \"flyway_schema_history\" ORDER BY \"installed_rank\"")){while(rs.next())history.add(rs.getString(1));}
            var f=ClassificationMigrationTest.flyway(url,"7");assertThat(f.info().current().getVersion().getVersion()).isEqualTo("6");
            assertThat(f.migrate().migrationsExecuted).isEqualTo(1);assertThat(f.migrate().migrationsExecuted).isZero();
        }
        var reopened=ClassificationMigrationTest.flyway(url,"7");assertThat(reopened.info().current().getVersion().getVersion()).isEqualTo("7");assertThat(reopened.migrate().migrationsExecuted).isZero();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            assertThat(ClassificationMigrationTest.fingerprints(c,columns)).isEqualTo(before);
            var after=new ArrayList<String>();try(var rs=s.executeQuery("SELECT \"version\" || ':' || \"checksum\" FROM \"flyway_schema_history\" ORDER BY \"installed_rank\"")){while(rs.next())after.add(rs.getString(1));}
            assertThat(after.subList(0,history.size())).isEqualTo(history);
            for(String table:List.of("post_restaurant_details","post_publication_restaurant_details")) {
                try(var rs=s.executeQuery("SELECT COUNT(*) FROM "+table)){rs.next();assertThat(rs.getInt(1)).isZero();}
                assertThatThrownBy(()->s.executeUpdate("INSERT INTO "+table+"(post_id,address) VALUES(999999,'orphan')")).isInstanceOf(SQLException.class);
            }
        }
    }
    @Test void additiveV7PreservesV6AndHasOneToOneCascadeKeys()throws Exception {
        String url=ClassificationMigrationTest.url(directory.resolve("restaurant-copy"));ClassificationMigrationTest.flyway(url,"6").migrate();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO users(id,email,display_name,password_hash,role) VALUES(1,'existing@test','기존 운영자','hash','ADMIN')");
            s.executeUpdate("INSERT INTO categories(id,name) VALUES(11,'기존 분류')");
            s.executeUpdate("INSERT INTO posts(id,title,content,author_id,category_id,content_type_code) VALUES(50,'기존 맛집','소개',1,11,'RESTAURANT')");
            s.executeUpdate("INSERT INTO post_publications(post_id,title,content,category_id,revision,content_type_code,type_name_snapshot) VALUES(50,'발행 맛집','소개',11,0,'RESTAURANT','맛집')");
        }
        verify(url);
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO post_restaurant_details VALUES(50,'초안 주소')");s.executeUpdate("INSERT INTO post_publication_restaurant_details VALUES(50,'발행 주소')");
            assertThatThrownBy(()->s.executeUpdate("INSERT INTO post_restaurant_details VALUES(50,'중복')")).isInstanceOf(SQLException.class);
            s.executeUpdate("DELETE FROM post_publications WHERE post_id=50");
            try(var rs=s.executeQuery("SELECT COUNT(*) FROM post_publication_restaurant_details")){rs.next();assertThat(rs.getInt(1)).isZero();}
            try(var rs=s.executeQuery("SELECT address FROM post_restaurant_details WHERE post_id=50")){rs.next();assertThat(rs.getString(1)).isEqualTo("초안 주소");}
            s.executeUpdate("DELETE FROM posts WHERE id=50");try(var rs=s.executeQuery("SELECT COUNT(*) FROM post_restaurant_details")){rs.next();assertThat(rs.getInt(1)).isZero();}
        }
    }
    @Test @EnabledIfSystemProperty(named="aica.restaurantValidationCopy",matches=".+")
    void migrateActualFaqCopyAndCompareEveryExistingColumn()throws Exception {
        Path file=Path.of(System.getProperty("aica.restaurantValidationCopy")).toRealPath();
        assertThat(file.startsWith(Path.of(".cache/react-phase3b2a-data").toRealPath())).isTrue();
        assertThat(file.getFileName().toString()).startsWith("aica-phase3c3").endsWith(".mv.db");
        verify(ClassificationMigrationTest.url(Path.of(file.toString().substring(0,file.toString().length()-6))));
    }
}
