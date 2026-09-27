package egovframework.backoffice.mvp.version;
import egovframework.backoffice.mvp.common.BusinessException;
public enum VersionKind {
 POST("posts","post","post_id"), PAGE("pages","page","page_id"), TEMPLATE("page-templates","page_template","template_id");
 public final String route, prefix, ownerColumn;
 VersionKind(String route,String prefix,String ownerColumn){this.route=route;this.prefix=prefix;this.ownerColumn=ownerColumn;}
 public String table(){return prefix+"_versions";}
 public String mediaTable(){return prefix+"_version_media";}
 public static VersionKind route(String route){for(var k:values())if(k.route.equals(route))return k;throw new BusinessException("버전 이력 대상을 확인하세요.");}
}

