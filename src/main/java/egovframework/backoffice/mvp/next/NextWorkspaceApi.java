package egovframework.backoffice.mvp.next;

import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.cms.*;
import egovframework.backoffice.mvp.common.BusinessException;
import egovframework.backoffice.mvp.post.*;
import egovframework.backoffice.mvp.classification.ClassificationModels.Classification;
import egovframework.backoffice.mvp.security.*;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** HTTP adapters only: existing services retain validation, authorization and all writes. */
@RestController
@RequestMapping("/api/admin/next")
public class NextWorkspaceApi {
    private final java.time.Clock clock;
    private final CmsAccess access;
    private final AccessPolicy policy;
    private final PageService pages;
    private final PostService posts;
    private final SiteService site;
    private final MediaService media;
    private final AccountService accounts;
    private final UsageService usages;

    public NextWorkspaceApi(CmsAccess access, AccessPolicy policy, PageService pages, PostService posts,
                            SiteService site, MediaService media, AccountService accounts, UsageService usages,java.time.Clock clock) {
        this.clock=clock;
        this.access=access; this.policy=policy; this.pages=pages; this.posts=posts;
        this.site=site; this.media=media; this.accounts=accounts; this.usages=usages;
    }

    /** parentId null = top level. Rows come in sibling order (sortOrder, then id). */
    public record PageRow(long id, String title, String slug, String status, long revision,
                          boolean pending, LocalDateTime updatedAt, Long parentId, int sortOrder) {}
    public record PlacementInput(Long parentId, Long expectedParentId) {}
    public record PageOrderInput(Long parentId, List<Long> pageIds) {}
    public record PostRow(long id, String title, long authorId, String authorName, Long categoryId,
                          String status, boolean pending, LocalDateTime updatedAt,Classification classification) {}
    public record AccountRow(long id, String email, String displayName, String role, String roleLabel,
                             boolean active, boolean passwordChangeRequired, LocalDateTime createdAt) {}
    public record MenuInput(String label, String kind, Long targetId, String url, boolean visible) {}
    public record LinkInput(String label, String url) {}
    public record MediaInput(String name, String alt) {}
    public record OrderInput(List<Long> ids) {}
    public record RoleRow(String code, String label, boolean manageAccounts, boolean allPosts,boolean publish,boolean structure,boolean permanentDelete) {}
    public record ActivityRow(long id, String actorName, String action, String target, String detail,
                              LocalDateTime createdAt) {}

    @ModelAttribute public void noStore(HttpServletResponse response) { response.setHeader("Cache-Control","no-store"); }

    @GetMapping("/bootstrap")
    public Map<String,Object> bootstrap(@AuthenticationPrincipal AccountPrincipal principal, CsrfToken csrf) {
        var actor=access.actor(principal);
        boolean manager=policy.canManageAllPosts(actor.role()), operator=policy.canManageAccounts(actor.role());
        return Map.of(
            "user", Map.of("id",actor.id(),"name",actor.displayName(),"role",actor.role().name(),"roleLabel",actor.role().getLabel()),
            "timeZone",clock.getZone().getId(),
            "permissions", Map.of("site",manager,"operations",operator,"templateManage",PageTemplateService.canManage(actor.role()),"templateUse",PageTemplateService.canUse(actor.role()),"structure",policy.canManageSite(actor.role()),"publish",policy.canPublish(actor.role()),"permanentDelete",policy.canDelete(actor.role())),
            "csrf", Map.of("headerName",csrf.getHeaderName(),"token",csrf.getToken()),
            "pages", manager?pageRows(principal):List.of(),
            "menus", manager?site.menus(principal):List.of(),
            "categories", site.categories(),
            "images", media.list(principal,"").stream().filter(m->m.mime().startsWith("image/")).toList());
    }

    @GetMapping("/pages")
    public List<PageRow> pageRows(@AuthenticationPrincipal AccountPrincipal actor) {
        return rows(pages.list(actor));
    }
    private static List<PageRow> rows(List<CmsModels.Page> list) {
        return list.stream().map(p->new PageRow(p.id(),p.title(),p.slug(),p.status(),p.revision(),p.pending(),p.updatedAt(),p.parentId(),p.sortOrder())).toList();
    }
    @PutMapping("/pages/{id}/placement")
    public List<PageRow> placement(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody PlacementInput input) {
        return rows(pages.place(actor,id,input.parentId(),input.expectedParentId()));
    }
    @PutMapping("/page-order")
    public List<PageRow> pageOrder(@AuthenticationPrincipal AccountPrincipal actor,@RequestBody PageOrderInput input) {
        return rows(pages.reorder(actor,input.parentId(),input.pageIds()));
    }

    @GetMapping("/dashboard")
    public Map<String,Object> dashboard(@AuthenticationPrincipal AccountPrincipal actor) {
        var overview=posts.dashboard(actor);
        return Map.of("total",overview.total(),"todayCount",overview.todayCount(),"weekCount",overview.weekCount(),
            "days",overview.days(),"recentPosts",overview.recentPosts().stream().map(p->postRow(actor,p)).toList(),
            "trafficStatus","NOT_CONNECTED");
    }

    @GetMapping("/posts")
    public Map<String,Object> posts(@AuthenticationPrincipal AccountPrincipal actor,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="") String q,
        @RequestParam(defaultValue="") String status, @RequestParam(required=false) Long categoryId,
        @RequestParam(required=false) List<String> typeCodes,@RequestParam(required=false) List<Long> cohortIds,
        @RequestParam(required=false) List<Long> topicIds) {
        var result=posts.list(actor,page,q,status,categoryId,typeCodes,cohortIds,topicIds);
        return Map.of("items",result.items().stream().map(p->postRow(actor,p)).toList(),"page",result.page(),
            "pageSize",result.pageSize(),"total",result.total(),"totalPages",result.totalPages());
    }
    private PostRow postRow(AccountPrincipal actor,Post p) {return new PostRow(p.id(),p.title(),p.authorId(),p.authorName(),p.categoryId(),p.status(),p.pending(),p.updatedAt(),posts.classification(actor,p.id()));}

    @GetMapping("/media")
    public List<CmsModels.Media> media(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(defaultValue="") String q) { return media.list(actor,q); }
    @PostMapping(value="/media",consumes="multipart/form-data")
    public CmsModels.Media upload(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam MultipartFile file,@RequestParam(defaultValue="") String alt) {
        return media.required(media.upload(actor,file,alt));
    }
    @PutMapping("/media/{id}")
    public CmsModels.Media editMedia(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody MediaInput input) {
        media.edit(actor,id,input.name(),input.alt()); return media.required(id);
    }
    @DeleteMapping("/media/{id}")
    public Map<String,Boolean> deleteMedia(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id) {media.delete(actor,id); return Map.of("ok",true);}
    @GetMapping("/media/{id}/usage")
    public List<UsageService.Usage> mediaUsage(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id) {return usages.find(actor,"media",id);}

    @GetMapping("/menus")
    public List<CmsModels.Menu> menus(@AuthenticationPrincipal AccountPrincipal actor) {return site.menus(actor);}
    @PostMapping("/menus")
    public List<CmsModels.Menu> createMenu(@AuthenticationPrincipal AccountPrincipal actor,@RequestBody MenuInput input) {return saveMenu(actor,null,input);}
    @PutMapping("/menus/{id}")
    public List<CmsModels.Menu> editMenu(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody MenuInput input) {return saveMenu(actor,id,input);}
    private List<CmsModels.Menu> saveMenu(AccountPrincipal actor,Long id,MenuInput input) {
        site.menu(actor,id,input.label(),Objects.toString(input.kind(),""),input.targetId(),input.url(),input.visible());return site.menus(actor);
    }
    @DeleteMapping("/menus/{id}")
    public List<CmsModels.Menu> deleteMenu(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id) {site.deleteItem(actor,"menus",id);return site.menus(actor);}
    @PutMapping("/menus/order")
    public List<CmsModels.Menu> menuOrder(@AuthenticationPrincipal AccountPrincipal actor,@RequestBody OrderInput input) {site.reorder(actor,"menus",input.ids());return site.menus(actor);}

    @GetMapping("/links")
    public List<CmsModels.Link> links(@AuthenticationPrincipal AccountPrincipal actor) {access.structure(actor);return site.links();}
    @PostMapping("/links")
    public List<CmsModels.Link> createLink(@AuthenticationPrincipal AccountPrincipal actor,@RequestBody LinkInput input) {return saveLink(actor,null,input);}
    @PutMapping("/links/{id}")
    public List<CmsModels.Link> editLink(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody LinkInput input) {return saveLink(actor,id,input);}
    private List<CmsModels.Link> saveLink(AccountPrincipal actor,Long id,LinkInput input) {site.link(actor,id,input.label(),input.url());return site.links();}
    @DeleteMapping("/links/{id}")
    public List<CmsModels.Link> deleteLink(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id) {site.deleteItem(actor,"links",id);return site.links();}
    @PutMapping("/links/order")
    public List<CmsModels.Link> linkOrder(@AuthenticationPrincipal AccountPrincipal actor,@RequestBody OrderInput input) {site.reorder(actor,"links",input.ids());return site.links();}
    // Existing categories: same SiteService rules as the legacy screen (structure permission, usage-protected delete).
    public record CategoryInput(String name) {}
    @GetMapping("/categories")
    public List<CmsModels.Category> categories(@AuthenticationPrincipal AccountPrincipal actor) {access.structure(actor);return site.categories();}
    @PostMapping("/categories")
    public List<CmsModels.Category> createCategory(@AuthenticationPrincipal AccountPrincipal actor,@RequestBody CategoryInput input) {site.category(actor,null,input.name());return site.categories();}
    @PutMapping("/categories/{id}")
    public List<CmsModels.Category> editCategory(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody CategoryInput input) {site.category(actor,id,input.name());return site.categories();}
    @DeleteMapping("/categories/{id}")
    public List<CmsModels.Category> deleteCategory(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id) {site.deleteCategory(actor,id);return site.categories();}
    @GetMapping("/categories/{id}/usage")
    public List<UsageService.Usage> categoryUsage(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id) {access.structure(actor);return usages.find(actor,"categories",id);}
    @PutMapping("/categories/order")
    public List<CmsModels.Category> categoryOrder(@AuthenticationPrincipal AccountPrincipal actor,@RequestBody OrderInput input) {site.reorder(actor,"categories",input.ids());return site.categories();}

    @GetMapping("/settings/{group}")
    public Map<String,String> settings(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable String group) {
        access.structure(actor);settingGroup(group);return site.settings();
    }
    @PutMapping("/settings/{group}")
    public Map<String,String> saveSettings(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable String group,@RequestBody Map<String,String> input) {
        settingGroup(group);site.saveSettings(actor,group,input);return site.settings();
    }
    private void settingGroup(String group) {if(!Set.of("basic","style","components","system").contains(group))throw new BusinessException("설정 항목을 확인하세요.");}

    @GetMapping("/accounts")
    public List<AccountRow> accounts(@AuthenticationPrincipal AccountPrincipal actor) {
        return accounts.list(actor).stream().map(a->new AccountRow(a.id(),a.email(),a.displayName(),a.role().name(),a.role().getLabel(),a.active(),a.passwordChangeRequired(),a.createdAt())).toList();
    }
    // Account changes reuse AccountService: last SUPER_ADMIN protection, no self-change, audit and session invalidation stay there.
    public record AccountInput(String email,String displayName,String role) {}
    public record RoleInput(String role) {}
    public record RoleOption(String code,String label) {}
    /** The temporary password is returned only in this no-store response and cannot be read again. */
    public record IssuedAccount(long accountId,String email,String temporaryPassword) {}
    @GetMapping("/accounts/creatable-roles")
    public List<RoleOption> creatableRoles(@AuthenticationPrincipal AccountPrincipal actor) {accounts.list(actor);return policy.creatableRoles().stream().map(r->new RoleOption(r.name(),r.getLabel())).toList();}
    @PostMapping("/accounts")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public IssuedAccount createAccount(@AuthenticationPrincipal AccountPrincipal actor,@RequestBody AccountInput input) {
        return issued(accounts.create(actor,input.email(),input.displayName(),role(input.role())));
    }
    @PutMapping("/accounts/{id}/role")
    public List<AccountRow> changeRole(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id,@RequestBody RoleInput input) {
        accounts.changeRole(actor,id,role(input.role()));return accounts(actor);
    }
    @PostMapping("/accounts/{id}/deactivate")
    public List<AccountRow> deactivate(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id) {accounts.deactivate(actor,id);return accounts(actor);}
    @PostMapping("/accounts/{id}/reset-password")
    public IssuedAccount resetPassword(@AuthenticationPrincipal AccountPrincipal actor,@PathVariable long id) {return issued(accounts.resetPassword(actor,id));}
    private static IssuedAccount issued(egovframework.backoffice.mvp.account.IssuedCredential c) {return new IssuedAccount(c.accountId(),c.email(),c.temporaryPassword());}
    private static egovframework.backoffice.mvp.account.Role role(String value) {
        if(value==null||value.isBlank())throw new BusinessException("역할을 선택하세요.");
        try{return egovframework.backoffice.mvp.account.Role.valueOf(value);}catch(IllegalArgumentException e){throw new BusinessException("역할을 확인하세요.");}
    }
    @GetMapping("/roles")
    public List<RoleRow> roles(@AuthenticationPrincipal AccountPrincipal actor) {
        access.operator(actor);return Arrays.stream(Role.values()).map(r->new RoleRow(r.name(),r.getLabel(),policy.canManageAccounts(r),policy.canManageAllPosts(r),policy.canPublish(r),policy.canManageSite(r),policy.canDelete(r))).toList();
    }
    @GetMapping("/activity")
    public Map<String,Object> activity(@AuthenticationPrincipal AccountPrincipal actor,@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="") String q,@RequestParam(defaultValue="false") boolean drafts) {
        var rows=site.activities(actor,page,q,drafts).stream().map(a->new ActivityRow(a.id(),a.actorName(),a.actionLabel(),a.targetLabel(),a.detailLabel(),a.createdAt())).toList();
        return Map.of("items",rows,"page",page,"pageSize",30,"total",site.activityCount(actor,q,drafts));
    }
}
