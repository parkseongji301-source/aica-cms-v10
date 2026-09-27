package egovframework.backoffice.mvp.version;
import java.util.*;
import java.time.LocalDateTime;
import org.egovframe.rte.psl.dataaccess.EgovAbstractMapper;
import org.springframework.stereotype.Repository;
import static egovframework.backoffice.mvp.cms.CmsStore.values;
@Repository
public class VersionStore extends EgovAbstractMapper {
 public boolean baselineComplete(){return this.<Long>selectOne("Versions.baselineComplete")>0;}
 public void markBaseline(long actor){insert("Versions.markBaseline",actor);}
 public record Entry(long id,long targetId,int snapshotSchemaVersion,String snapshotJson,String reason,long sourceRevision,Long sourceVersionId,String operationId,long createdBy,String creatorName,Long activityLogId,LocalDateTime createdAt){}
 public record MediaUse(long targetId,long versionId,String reason,LocalDateTime createdAt){}
 private Map<String,Object> args(VersionKind kind,long id){return values("table",kind.table(),"mediaTable",kind.mediaTable(),"owner",kind.ownerColumn,"targetId",id);}
 public Entry get(VersionKind k,long id,long version){var p=args(k,id);p.put("versionId",version);return selectOne("Versions.get",p);}
 public List<Entry> list(VersionKind k,long id,int offset){var p=args(k,id);p.put("offset",offset);return selectList("Versions.list",p);}
 public long count(VersionKind k,long id){return selectOne("Versions.count",args(k,id));}
 public long insert(VersionKind k,long id,Map<String,Object> fields){var p=args(k,id);p.putAll(fields);insert("Versions.insert",p);return ((Number)p.get("id")).longValue();}
 public void media(VersionKind k,long version,long media){var p=args(k,0);p.put("versionId",version);p.put("mediaId",media);insert("Versions.media",p);}
 public boolean hasReason(VersionKind k,long id,String reason){var p=args(k,id);p.put("reason",reason);return this.<Long>selectOne("Versions.reasonCount",p)>0;}
 public Entry operation(VersionKind k,long id,String operation,String reason){if(operation==null)return null;var p=args(k,id);p.put("operationId",operation);p.put("reason",reason);return selectOne("Versions.operation",p);}
 public void prune(VersionKind k,long id,int keep){var p=args(k,id);p.put("keep",keep);delete("Versions.prune",p);}
 public void purge(VersionKind k,long id){delete("Versions.purge",args(k,id));}
 public List<Long> mediaIds(VersionKind k,long id){return selectList("Versions.mediaIds",args(k,id));}
 public List<MediaUse> uses(VersionKind k,long media){var p=args(k,0);p.put("mediaId",media);return selectList("Versions.uses",p);}
 public boolean references(VersionKind k,long id,long version,long media){var p=args(k,id);p.put("versionId",version);p.put("mediaId",media);return this.<Long>selectOne("Versions.references",p)>0;}
}

