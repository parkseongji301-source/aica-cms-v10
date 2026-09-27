package egovframework.backoffice.mvp.classification;
import java.util.List;
import org.egovframe.rte.psl.dataaccess.EgovAbstractMapper;
import org.springframework.stereotype.Repository;

/** Internal prepared statements; statement names are never accepted from HTTP input. */
@Repository
public class ClassificationMapper extends EgovAbstractMapper {
    public <T> T one(String statement,Object value){return selectOne("Classification."+statement,value);}
    public <T> List<T> all(String statement,Object value){return selectList("Classification."+statement,value);}
    public void change(String statement,Object value){update("Classification."+statement,value);}
}
