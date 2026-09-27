package egovframework.backoffice.mvp.cms;

import org.egovframe.rte.psl.dataaccess.EgovAbstractMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PageBlockMapper extends EgovAbstractMapper {
    public PageBlockService.Identity find(String id){return selectOne("PageBlockMapper.find",id);}
    public void register(PageBlockService.Identity value){insert("PageBlockMapper.register",value);}
    public void retire(String id){update("PageBlockMapper.retire",id);}
}
