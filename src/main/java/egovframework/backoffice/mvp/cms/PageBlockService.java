package egovframework.backoffice.mvp.cms;

import egovframework.backoffice.mvp.common.BusinessException;
import egovframework.backoffice.mvp.cms.CmsModels.Section;
import java.util.*;
import org.springframework.stereotype.Service;

/** Identity changes run inside the existing PageService lock and transaction. */
@Service
public class PageBlockService {
    public record Identity(String blockId,Long pageId,boolean retired) {}
    private final PageBlockMapper mapper;
    public PageBlockService(PageBlockMapper mapper){this.mapper=mapper;}
    public static String newId(){return "block_"+UUID.randomUUID();}
    public static boolean hasStableId(String id){return id!=null&&id.matches("block_[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");}
    public static Section duplicate(Section source) {
        return identified(metadata(List.of(source),false).get(0),newId());
    }
    /** Template copies have no page identity. Full validation follows in PageService. */
    public static Section copyWithNewId(Section s) {
        return new Section(s.type(),s.heading(),s.body(),s.imageId(),s.categoryId(),s.link(),s.label(),s.visible(),s.bodyDoc(),newId(),s.schemaVersion(),s.variation(),s.sourceMode(),s.query(),s.manual());
    }
    private static Section identified(Section s,String id) {
        return new Section(s.type(),s.heading(),s.body(),s.imageId(),s.categoryId(),s.link(),s.label(),s.visible(),s.bodyDoc(),id,s.schemaVersion()==null?2:s.schemaVersion(),s.variation()==null?"default":s.variation(),s.sourceMode(),s.query(),s.manual());
    }
    public static List<Section> metadata(List<Section> source,boolean newPage) {
        var result=new ArrayList<Section>();var ids=new HashSet<String>();
        if(source==null||source.size()>30)throw new BusinessException("섹션은 최대 30개까지 추가할 수 있습니다.");
        for(var raw:source) {
            if(raw==null)throw new BusinessException("블록 내용을 확인하세요.");
            var s=raw;
            if(newPage&&s.id()==null&&s.schemaVersion()==null&&s.variation()==null)s=identified(s,newId());
            if(!hasStableId(s.id()))
                throw new BusinessException("블록 ID가 없거나 올바르지 않습니다. 내용을 보관한 뒤 페이지를 다시 열어 주세요.");
            PageComponentRegistry.validate(s.type(),s.schemaVersion(),s.variation());
            if(!ids.add(s.id()))throw new BusinessException("중복된 블록 ID입니다. 복제한 블록에는 새 ID가 필요합니다.");
            result.add(s);
        }
        return result;
    }
    public void validate(Long pageId,List<Section> current,List<Section> next) {
        var currentIds=new HashSet<String>();current.forEach(s->currentIds.add(s.id()));
        for(var s:next) {
            var identity=mapper.find(s.id());
            if(identity!=null&&(!Objects.equals(identity.pageId(),pageId)||identity.retired()||!currentIds.contains(s.id())))
                throw new BusinessException("다른 페이지의 블록 또는 삭제된 블록 ID는 재사용할 수 없습니다.");
            if(identity==null&&currentIds.contains(s.id()))throw new BusinessException("블록 등록 기록을 확인할 수 없습니다. 저장하지 않았습니다.");
        }
    }
    public void synchronize(long pageId,List<Section> current,List<Section> next) {
        var nextIds=new HashSet<String>();next.forEach(s->nextIds.add(s.id()));
        for(var s:current)if(!nextIds.contains(s.id()))mapper.retire(s.id());
        for(var s:next)if(mapper.find(s.id())==null)mapper.register(new Identity(s.id(),pageId,false));
    }
    public record Restored(List<Section> sections,Map<String,String> changedIds) {}
    /** Never resurrect a retired ID or infer correspondence from text/position. */
    public Restored restore(long pageId,List<Section> current,List<Section> historic) {
        var currentIds=new HashSet<String>();current.forEach(s->currentIds.add(s.id()));
        var result=new ArrayList<Section>();var mapping=new LinkedHashMap<String,String>();
        for(var s:metadata(historic,false)){
            var identity=mapper.find(s.id());
            if(identity==null||!Objects.equals(identity.pageId(),pageId)||currentIds.contains(s.id())&&identity.retired())
                throw new BusinessException("과거 블록의 소속 또는 등록 기록을 확인할 수 없습니다. 복구하지 않았습니다.");
            if(currentIds.contains(s.id()))result.add(s);
            else {var copy=copyWithNewId(s);result.add(copy);mapping.put(s.id(),copy.id());}
        }
        return new Restored(List.copyOf(result),Map.copyOf(mapping));
    }
}
