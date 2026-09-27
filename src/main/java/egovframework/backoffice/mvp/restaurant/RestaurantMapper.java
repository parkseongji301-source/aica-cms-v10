package egovframework.backoffice.mvp.restaurant;

import org.egovframe.rte.psl.dataaccess.EgovAbstractMapper;
import org.springframework.stereotype.Repository;

@Repository
public class RestaurantMapper extends EgovAbstractMapper {
    public RestaurantDetailsService.Details draft(long id){return selectOne("Restaurant.draft",id);}
    public RestaurantDetailsService.Details published(long id){return selectOne("Restaurant.published",id);}
    public void change(String statement,Object value){update("Restaurant."+statement,value);}
}
