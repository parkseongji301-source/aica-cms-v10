package egovframework.backoffice.mvp.operations;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static egovframework.backoffice.mvp.operations.FileDatabaseSafety.*;

/** Invoke via the RC's PropertiesLauncher. Never starts a web server or seeds application data. */
public final class CutoverTool {
    public static void main(String[] args) throws Exception {
        require(args.length==3,"usage: inspect|plan|migrate database-or-plan output");
        String user=System.getenv().getOrDefault("AICA_DB_USER","sa"),password=System.getenv().getOrDefault("AICA_DB_PASSWORD","");
        Path jar=runtimeJar();String jarHash=hash(jar);Path output=Path.of(args[2]).toAbsolutePath();
        require(!Files.exists(output),"output already exists; never overwrite evidence");
        if(args[0].equals("inspect")) {
            var data=inspect(Path.of(args[1]),user,password);data.put("jarSha256",jarHash);write(output,data);return;
        }
        if(args[0].equals("plan")) {
            Path db=Path.of(args[1]).toRealPath();var data=inspect(db,user,password);
            var f=flyway(readUrl(db),user,password,null);flyway(readUrl(db),user,password,"3").validate();var resolved=migrations(f);
            require(JSON.valueToTree(data.get("history")).equals(JSON.valueToTree(resolved.subList(0,3))),"V3 and matching V1-V3 checksums required");
            var plan=new LinkedHashMap<String,Object>();plan.put("format",1);plan.put("databasePath",db.toString());plan.put("database",data);plan.put("jarSha256",jarHash);plan.put("migrations",resolved);plan.put("migrationLocation",LOCATION);
            plan.put("jdbcUrl",writerUrl(db));plan.put("expiresAt",Instant.now().plusSeconds(86400).toString());plan.put("dictionaryMode","DEFER");
            write(output,plan);System.out.println("Prepared READ-ONLY plan; separate flag and pinned plan hash are required to execute.");return;
        }
        require(args[0].equals("migrate"),"unknown operation");
        require("true".equals(System.getenv("AICA_CUTOVER_ENABLED")),"explicit cutover flag required");
        Path planFile=Path.of(args[1]).toRealPath();String planHash=hash(planFile);
        require(planHash.equals(System.getenv("AICA_CUTOVER_APPROVAL_SHA256")),"approved plan checksum mismatch");
        JsonNode plan=JSON.readTree(planFile.toFile());Path db=Path.of(plan.path("databasePath").asText()).toRealPath();
        validatePlan(plan,db,jarHash,user,password);
        // CREATE_NEW makes this approval one-shot even after a partial failure. Never retry a spent plan.
        Path spent=Path.of(planFile+".spent");Files.writeString(spent,"started="+Instant.now()+"\nplanSha256="+planHash+"\n",StandardOpenOption.CREATE_NEW);
        var steps=new ArrayList<Map<String,Object>>();
        try(var c=DriverManager.getConnection(plan.path("jdbcUrl").asText(),user,password)) {
            require(plan.path("database").path("fingerprints").equals(JSON.valueToTree(fingerprint(c,columns(c)))),"database changed between preflight and write connection");
            require(plan.path("database").path("history").equals(JSON.valueToTree(history(c))),"history changed between preflight and write connection");
            var projection=columns(c);projection.get("SITE_PAGES").remove("SECTIONS_JSON");projection.get("PAGE_PUBLICATIONS").remove("SECTIONS_JSON");
            var before=fingerprint(c,projection);var blocks=pageBlocks(c);
            for(int n=4;n<=10;n++) {
                var f=flyway(plan.path("jdbcUrl").asText(),user,password,Integer.toString(n));
                require(f.migrate().migrationsExecuted==1,"one migration expected for V"+n);f.validate();
                require(before.equals(fingerprint(c,projection)),"legacy data changed at V"+n);
                require(blocks.equals(pageBlocks(c)),"page block content changed at V"+n);
                require(plan.path("database").path("history").equals(JSON.valueToTree(history(c).subList(0,3))),"legacy Flyway history changed");
                steps.add(Map.of("version",n,"legacyDataPreserved",true));
            }
        }
        // Reopen only after the final writer has closed: catches the previously diagnosed persistence bug.
        requireV10(db,user,password);var after=inspect(db,user,password);
        var receipt=new LinkedHashMap<String,Object>();receipt.put("status","MIGRATED_V10");receipt.put("databasePath",db.toString());receipt.put("jarSha256",jarHash);receipt.put("approvalSha256",planHash);receipt.put("workaround","AUTO_COMPACT_FILL_RATE=0");receipt.put("steps",steps);receipt.put("migrations",plan.path("migrations"));receipt.put("after",after);receipt.put("completedAt",OffsetDateTime.now().toString());receipt.put("dictionaryMode","DEFER");
        write(output,receipt);Files.writeString(spent,"completed="+Instant.now()+"\n",StandardOpenOption.APPEND);
        System.out.println("V3 -> V10 complete; cutover process exits. Normal runtime validates only.");
    }
    public static void validatePlan(JsonNode p,Path db,String jarHash,String user,String password) throws Exception {
        require(p.path("format").asInt()==1 && LOCATION.equals(p.path("migrationLocation").asText()),"unsupported approval format/location");
        require(Instant.parse(p.path("expiresAt").asText()).isAfter(Instant.now()),"approval expired");
        require(db.toString().equals(p.path("databasePath").asText()),"exact absolute database path mismatch");
        require(requireWriter(p.path("jdbcUrl").asText()).equals(db),"datasource path mismatch");
        require(writerUrl(db).equals(p.path("jdbcUrl").asText()),"noncanonical cutover URL");
        require(jarHash.equals(p.path("jarSha256").asText()),"running RC checksum mismatch");
        require("DEFER".equals(p.path("dictionaryMode").asText()),"operating dictionary is not approved in this release");
        require(hash(db).equals(p.path("database").path("sha256").asText()),"database byte hash mismatch");
        var current=inspect(db,user,password);
        require(p.path("database").path("fingerprints").equals(JSON.valueToTree(current.get("fingerprints"))),"database fingerprint mismatch");
        var f=flyway(readUrl(db),user,password,null);flyway(readUrl(db),user,password,"3").validate();var resolved=JSON.valueToTree(migrations(f));
        require(resolved.equals(p.path("migrations")),"approved migration manifest mismatch");
        var expected=JSON.createArrayNode();for(int n=0;n<3;n++)expected.add(resolved.get(n));
        require(expected.equals(JSON.valueToTree(current.get("history")))&&expected.equals(p.path("database").path("history")),"current V3 / V1-V3 checksum mismatch: expected="+expected+", actual="+JSON.valueToTree(current.get("history")));
    }
    private static Map<String,JsonNode> pageBlocks(Connection c) throws Exception {
        var out=new TreeMap<String,JsonNode>();
        for(String table:List.of("site_pages","page_publications"))try(var s=c.createStatement();var r=s.executeQuery("SELECT "+(table.equals("site_pages")?"id":"page_id")+",sections_json FROM "+table)) {
            while(r.next()) {var blocks=JSON.readTree(r.getString(2));for(var block:blocks)((ObjectNode)block).remove(List.of("id","schemaVersion","variation"));out.put(table+":"+r.getLong(1),blocks);}
        }
        return out;
    }
    private static void write(Path path,Object value) throws Exception {
        Files.writeString(path,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value),StandardOpenOption.CREATE_NEW);
    }
}
