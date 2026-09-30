package egovframework.backoffice.mvp.cms;

import egovframework.backoffice.mvp.security.AccountPrincipal;
import static egovframework.backoffice.mvp.cms.CmsModels.*;
import static egovframework.backoffice.mvp.cms.CmsStore.values;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class UsageService {
    public record Usage(String label, String href) {}
    private final CmsStore store;
    private final CmsAccess access;
    private final PageService pages;
    private final MediaService media;
    private final TemplateReferences templates;
    private final egovframework.backoffice.mvp.version.VersionMediaReferences versionMedia;
    public UsageService(CmsStore store, CmsAccess access, PageService pages, MediaService media,TemplateReferences templates,egovframework.backoffice.mvp.version.VersionMediaReferences versionMedia) {
        this.versionMedia=versionMedia;
        this.store=store; this.access=access; this.pages=pages; this.media=media;this.templates=templates;
    }
    public List<Usage> find(AccountPrincipal actor, String type, long id) {
        Long owner=null;
        if(type.equals("media")) {media.validate(actor,List.of(id));owner=access.owner(actor);}
        else access.manager(actor);
        var uses=new LinkedHashSet<Usage>();
        if(type.equals("media"))uses.addAll(versionMedia.uses(actor,id));
        if(type.equals("media"))uses.addAll(templates.media(id).stream().map(u->new Usage(u.label(),access.actor(actor).role()==egovframework.backoffice.mvp.account.Role.SUPER_ADMIN?u.href():null)).toList());
        if(type.equals("categories") || type.equals("media"))
            uses.addAll(store.<Usage>all(type.equals("media")?"mediaPostUses":"categoryPostUses",values("id",id,"owner",owner)));
        if(type.equals("media") && owner==null) uses.addAll(store.<Usage>all("mediaPageUses",id));
        if(type.equals("categories")) {
            for(Page page:pages.list(actor)) {
                PublishedPage published=store.one("anyPagePublication",page.id());
                if(hasCategory(page.sectionsJson(),id) || (published!=null && hasCategory(published.sectionsJson(),id)))
                    uses.add(new Usage("페이지 · "+page.title(),"/admin/pages/"+page.id()+"/edit"));
            }
        }
        if(type.equals("pages")) for(Page child:store.<Page>all("pages",null)) if(Objects.equals(child.parentId(),id))
            uses.add(new Usage("하위 페이지 · "+child.title(),"/admin/pages/"+child.id()+"/edit"));
        if(type.equals("pages") && store.<Long>one("publishedStructureReferences",id)>0) uses.add(new Usage("게시된 사이트 구성",null));
        if(type.equals("categories") || type.equals("pages")) {
            String kind=type.equals("pages")?"PAGE":"CATEGORY";
            for(Menu menu:store.<Menu>all("menus",null)) if(menu.kind().equals(kind) && Objects.equals(menu.targetId(),id))
                uses.add(new Usage("메뉴 · "+menu.label(),"/admin/menus?edit="+menu.id()));
        }
        if(owner==null) for(Setting setting:store.<Setting>all("settings",null)) {
            if(!setting.settingValue().equals(String.valueOf(id)))continue;
            if(type.equals("pages") && setting.settingKey().equals("homePageId")) uses.add(new Usage("홈페이지 첫 화면","/admin/settings/basic"));
            if(type.equals("media") && setting.settingKey().equals("logoId")) uses.add(new Usage("사이트 로고","/admin/design/style"));
        }
        if(access.actor(actor).role()!=egovframework.backoffice.mvp.account.Role.SUPER_ADMIN)
            return uses.stream().map(use->"/admin/trash".equals(use.href())?new Usage("휴지통의 콘텐츠에서 사용 중",null):use).distinct().toList();
        return List.copyOf(uses);
    }
    private boolean hasCategory(String document,long id) {
        return pages.sections(document).stream().anyMatch(s->Objects.equals(s.categoryId(),id));
    }
}
