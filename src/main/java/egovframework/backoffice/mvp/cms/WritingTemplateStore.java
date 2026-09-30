package egovframework.backoffice.mvp.cms;

import java.util.*;
import org.egovframe.rte.psl.dataaccess.EgovAbstractMapper;
import org.springframework.stereotype.Repository;

@Repository
public class WritingTemplateStore extends EgovAbstractMapper {
 public List<WritingTemplateService.Stored> list(){return selectList("WritingTemplates.list");}
 public WritingTemplateService.Stored get(long id){return selectOne("WritingTemplates.get",id);}
 public long create(Map<String,Object> values){insert("WritingTemplates.create",values);return ((Number)values.get("id")).longValue();}
 public void edit(Map<String,Object> values){update("WritingTemplates.edit",values);}
 public void remove(long id){delete("WritingTemplates.remove",id);}
}
