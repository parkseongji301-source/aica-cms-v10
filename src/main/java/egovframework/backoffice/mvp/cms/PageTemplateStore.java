package egovframework.backoffice.mvp.cms;

import java.util.*;
import org.egovframe.rte.psl.dataaccess.EgovAbstractMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PageTemplateStore extends EgovAbstractMapper {
 public List<PageTemplateService.Stored> list(){return selectList("PageTemplates.list");}
 public PageTemplateService.Stored get(long id){return selectOne("PageTemplates.get",id);}
 public long insert(Map<String,Object> values){insert("PageTemplates.create",values);return ((Number)values.get("id")).longValue();}
 public void edit(Map<String,Object> values){update("PageTemplates.edit",values);}
}
