package db.migration.h2;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.*;

/** Frozen V8 conversion. Never derives identity from content or array position. */
public class V8__page_block_identity extends BaseJavaMigration {
    private static final ObjectMapper JSON=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private record Document(long pageId,long revision,String source,ArrayNode blocks) {}
    private record Change(String table,String key,long pageId,String source,String target) {}
    private record Identity(long pageId,boolean retired) {}
    @Override public Integer getChecksum(){return 804202609;}
    @Override public void migrate(Context context)throws Exception {upgrade(context.getConnection());}

    public static void upgrade(Connection connection)throws Exception {
        var drafts=read(connection,"site_pages","id");
        var publications=read(connection,"page_publications","page_id");
        var changes=new ArrayList<Change>();var identities=new LinkedHashMap<String,Identity>();
        // Complete preflight before any DDL or row writes.
        for(var draft:drafts.values()) {
            var pub=publications.get(draft.pageId());
            boolean same=pub!=null&&draft.revision()==pub.revision()&&draft.blocks().equals(pub.blocks());
            ArrayNode next=convert(draft.blocks());
            recordIds(identities,next,draft.pageId(),false);
            changes.add(new Change("site_pages","id",draft.pageId(),draft.source(),JSON.writeValueAsString(next)));
            if(pub!=null) {
                var published=same?next.deepCopy():convert(pub.blocks());
                recordIds(identities,published,draft.pageId(),true);
                changes.add(new Change("page_publications","page_id",pub.pageId(),pub.source(),JSON.writeValueAsString(published)));
            }
        }
        if(!drafts.keySet().containsAll(publications.keySet()))throw new IllegalStateException("Publication without page");
        try(var statement=connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS page_block_identities (block_id VARCHAR(42) PRIMARY KEY, page_id BIGINT REFERENCES site_pages(id) ON DELETE SET NULL, retired BOOLEAN NOT NULL DEFAULT FALSE)");
        }
        for(var entry:identities.entrySet()) {
            try(var query=connection.prepareStatement("SELECT page_id,retired FROM page_block_identities WHERE block_id=?")) {
                query.setString(1,entry.getKey());try(var rs=query.executeQuery()) {
                    if(rs.next()) {
                        if(!Objects.equals(rs.getObject(1,Long.class),entry.getValue().pageId())||rs.getBoolean(2)!=entry.getValue().retired())
                            throw new IllegalStateException("Inconsistent existing block identity: "+entry.getKey());
                        continue;
                    }
                }
            }
            try(var insert=connection.prepareStatement("INSERT INTO page_block_identities(block_id,page_id,retired) VALUES(?,?,?)")) {
                insert.setString(1,entry.getKey());insert.setLong(2,entry.getValue().pageId());insert.setBoolean(3,entry.getValue().retired());insert.executeUpdate();
            }
        }
        for(var change:changes)if(!change.source().equals(change.target())) {
            try(var update=connection.prepareStatement("UPDATE "+change.table()+" SET sections_json=? WHERE "+change.key()+"=?")) {
                update.setString(1,change.target());update.setLong(2,change.pageId());update.executeUpdate();
            }
        }
    }
    private static Map<Long,Document> read(Connection connection,String table,String key)throws Exception {
        var result=new LinkedHashMap<Long,Document>();
        try(var statement=connection.createStatement();var rs=statement.executeQuery("SELECT "+key+",revision,sections_json FROM "+table+" ORDER BY "+key)) {
            while(rs.next()) {
                String source=rs.getString(3);JsonNode node=JSON.readTree(source);
                if(!node.isArray()||node.size()>30)throw new IllegalStateException("Invalid blocks: "+table+" #"+rs.getLong(1));
                result.put(rs.getLong(1),new Document(rs.getLong(1),rs.getLong(2),source,(ArrayNode)node));
            }
        }
        return result;
    }
    private static ArrayNode convert(ArrayNode original) {
        var result=original.deepCopy();var local=new HashSet<String>();
        var fields=Set.of("type","heading","body","bodyDoc","imageId","categoryId","link","label","visible","id","schemaVersion","variation");
        for(var value:result) {
            if(!value.isObject()||!Set.of("HERO","TEXT","IMAGE","POSTS","CTA").contains(value.path("type").asText()))throw new IllegalStateException("Unsupported legacy block");
            var block=(ObjectNode)value;block.fieldNames().forEachRemaining(name->{if(!fields.contains(name))throw new IllegalStateException("Unknown block field: "+name);});
            if(!block.has("id")&&!block.has("schemaVersion")&&!block.has("variation")) {
                block.put("id","block_"+UUID.randomUUID());block.put("schemaVersion",2);block.put("variation","default");
            }
            String id=block.path("id").asText();
            if(!id.matches("block_[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")||!local.add(id)
                ||!block.path("schemaVersion").isInt()||block.path("schemaVersion").asInt()!=2||!"default".equals(block.path("variation").asText()))
                throw new IllegalStateException("Invalid or duplicate versioned block identity");
        }
        return result;
    }
    private static void recordIds(Map<String,Identity> identities,ArrayNode blocks,long pageId,boolean publication) {
        for(var block:blocks) {
            String id=block.path("id").asText();var old=identities.get(id);
            if(old!=null&&(old.pageId()!=pageId||!publication))throw new IllegalStateException("Block ID belongs to another page");
            if(old==null)identities.put(id,new Identity(pageId,publication));
        }
    }
}
