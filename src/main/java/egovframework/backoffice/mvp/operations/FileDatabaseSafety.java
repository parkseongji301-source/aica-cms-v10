package egovframework.backoffice.mvp.operations;

import com.fasterxml.jackson.databind.*;
import org.flywaydb.core.Flyway;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Shared pre-connection checks for the packaged V10 runtime and one-shot cutover tool. */
public final class FileDatabaseSafety {
    public static final ObjectMapper JSON = new ObjectMapper();
    public static final String LOCATION = "classpath:db/migration/h2";
    private FileDatabaseSafety() {}
    public static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException("DB safety STOP: " + reason);
    }
    public static Path file(String url) throws Exception {
        require(url != null && url.startsWith("jdbc:h2:file:"), "explicit H2 file URL required");
        String base = url.substring("jdbc:h2:file:".length()).split(";", -1)[0];
        require(!base.endsWith(".mv.db"), "JDBC base must omit .mv.db suffix");
        Path path = Path.of(base + ".mv.db");
        require(path.isAbsolute(), "absolute database path required");
        return path.toRealPath();
    }
    public static Map<String,String> options(String url) {
        var out = new TreeMap<String,String>();
        String[] parts = url.split(";", -1);
        for (int n=1;n<parts.length;n++) {
            String[] option=parts[n].split("=",2);
            require(option.length==2 && !out.containsKey(option[0].toUpperCase(Locale.ROOT)), "invalid/duplicate JDBC option");
            out.put(option[0].toUpperCase(Locale.ROOT),option[1].toUpperCase(Locale.ROOT));
        }
        return out;
    }
    public static Path requireWriter(String url) throws Exception {
        var opts=options(url);
        require("0".equals(opts.get("AUTO_COMPACT_FILL_RATE")), "AUTO_COMPACT_FILL_RATE=0 is required before a file write connection");
        require("TRUE".equals(opts.get("IFEXISTS")), "IFEXISTS=TRUE is required");
        require("FALSE".equals(opts.get("DB_CLOSE_ON_EXIT")), "DB_CLOSE_ON_EXIT=FALSE is required");
        require(Set.of("AUTO_COMPACT_FILL_RATE","IFEXISTS","DB_CLOSE_ON_EXIT").containsAll(opts.keySet()), "unapproved file JDBC options");
        return file(url);
    }
    public static String writerUrl(Path file) throws Exception {
        String p=file.toRealPath().toString().replace('\\','/');
        require(p.endsWith(".mv.db") && !p.contains(";"), "existing .mv.db file required");
        return "jdbc:h2:file:"+p.substring(0,p.length()-6)+";IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE;AUTO_COMPACT_FILL_RATE=0";
    }
    public static String readUrl(Path file) throws Exception { return writerUrl(file)+";ACCESS_MODE_DATA=r"; }
    public static String hash(Path file) throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        try(var input=Files.newInputStream(file)){byte[] b=new byte[65536];int n;while((n=input.read(b))>0)digest.update(b,0,n);}
        return HexFormat.of().formatHex(digest.digest());
    }
    public static Path runtimeJar() throws Exception {
        String cp=System.getProperty("java.class.path","");
        require(!cp.contains(java.io.File.pathSeparator) && cp.endsWith(".jar"), "frozen single-JAR runtime required");
        return Path.of(cp).toRealPath();
    }
    public static Flyway flyway(String url,String username,String password,String target) {
        var config=Flyway.configure().dataSource(url,username,password).locations(LOCATION).cleanDisabled(true);
        if(target!=null)config.target(target);
        return config.load();
    }
    public static List<Map<String,Object>> migrations(Flyway flyway) {
        var out=new ArrayList<Map<String,Object>>();
        for(var m:flyway.info().all()) {
            require(m.getVersion()!=null,"repeatable/unversioned migrations are not approved");
            var row=new LinkedHashMap<String,Object>();row.put("version",m.getVersion().getVersion());row.put("script",m.getScript());row.put("checksum",m.getChecksum());out.add(row);
        }
        require(out.size()==10,"exactly V1 through V10 required");
        for(int n=0;n<10;n++)require(Integer.toString(n+1).equals(out.get(n).get("version")),"migration sequence mismatch");
        return out;
    }
    public static List<Map<String,Object>> history(Connection c) throws Exception {
        var out=new ArrayList<Map<String,Object>>();
        try(var s=c.createStatement();var r=s.executeQuery("SELECT \"version\",\"script\",\"checksum\",\"success\",\"type\" FROM \"flyway_schema_history\" ORDER BY \"installed_rank\"")) {
            while(r.next()) {require(r.getBoolean(4),"failed migration history");
                if(r.getString(1)==null) {require("TABLE".equals(r.getString(5)),"unexpected unversioned history entry");continue;}
                var row=new LinkedHashMap<String,Object>();row.put("version",r.getString(1));row.put("script",r.getString(2));row.put("checksum",r.getObject(3));out.add(row);}
        }
        return out;
    }
    public static Map<String,List<String>> columns(Connection c) throws Exception {
        var out=new TreeMap<String,List<String>>();
        try(var tables=c.getMetaData().getTables(null,"PUBLIC","%",new String[]{"TABLE"})) {
            while(tables.next()) {
                String table=tables.getString("TABLE_NAME");if(table.equals("flyway_schema_history"))continue;
                var cols=new ArrayList<String>();try(var fields=c.getMetaData().getColumns(null,"PUBLIC",table,"%")){while(fields.next())cols.add(fields.getString("COLUMN_NAME"));}out.put(table,cols);
            }
        }
        return out;
    }
    public static Map<String,Object> fingerprint(Connection c,Map<String,List<String>> tables) throws Exception {
        var out=new TreeMap<String,Object>();
        for(var table:tables.entrySet()) {
            var rows=new ArrayList<String>();
            try(var s=c.createStatement();var r=s.executeQuery("SELECT "+String.join(",",table.getValue())+" FROM "+table.getKey())) {
                while(r.next()) {
                    var row=new StringBuilder();
                    for(int i=1;i<=table.getValue().size();i++) {
                        int type=r.getMetaData().getColumnType(i);boolean binary=Set.of(Types.BLOB,Types.BINARY,Types.VARBINARY,Types.LONGVARBINARY).contains(type);
                        String value=binary?null:r.getString(i);byte[] bytes=binary?r.getBytes(i):(value==null?null:value.getBytes(StandardCharsets.UTF_8));
                        row.append(bytes==null?"NULL":Base64.getEncoder().encodeToString(bytes)).append('|');
                    }
                    rows.add(row.toString());
                }
            }
            Collections.sort(rows);String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\n",rows).getBytes(StandardCharsets.UTF_8)));
            out.put(table.getKey(),Map.of("rows",rows.size(),"sha256",digest));
        }
        return out;
    }
    public static Map<String,Object> inspect(Path file,String user,String password) throws Exception {
        file=file.toRealPath();String before=hash(file);var out=new LinkedHashMap<String,Object>();
        out.put("path",file.toString());out.put("sha256",before);out.put("size",Files.size(file));out.put("modified",Files.getLastModifiedTime(file).toInstant().toString());
        out.put("jdbcUrl",readUrl(file));out.put("pid",ProcessHandle.current().pid());
        try(var c=DriverManager.getConnection(readUrl(file),user,password)) {
            out.put("history",history(c));var cols=columns(c);out.put("columns",cols);out.put("fingerprints",fingerprint(c,cols));
        }
        require(before.equals(hash(file)),"read-only inspection changed database bytes");
        return out;
    }
    public static void requireV10(Path file,String user,String password) throws Exception {
        var f=flyway(readUrl(file),user,password,null);f.validate();
        require(f.info().current()!=null&&"10".equals(f.info().current().getVersion().getVersion())&&f.info().pending().length==0,"normal runtime accepts V10 only; use approved one-shot cutover");
    }
    public static void requireReceipt(Path file,Path receipt,String jarHash) throws Exception {
        var r=JSON.readTree(receipt.toFile());
        require(r.path("status").asText().equals("MIGRATED_V10"),"missing completed cutover receipt");
        require(file.toRealPath().toString().equals(r.path("databasePath").asText()),"receipt database path mismatch");
        require(jarHash.equals(r.path("jarSha256").asText()),"receipt runtime checksum mismatch");
        require(r.path("workaround").asText().equals("AUTO_COMPACT_FILL_RATE=0"),"receipt workaround mismatch");
    }
}
