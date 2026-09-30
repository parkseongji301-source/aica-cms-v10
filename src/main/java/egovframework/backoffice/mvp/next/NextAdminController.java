package egovframework.backoffice.mvp.next;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** The React admin owns /admin; legacy Thymeleaf screens remain under /admin/legacy for comparison and recovery. */
@Controller
public class NextAdminController {
    /** Static shell only: permission-specific data is loaded through /api/admin/next, which keeps its own 401/403. */
    @GetMapping({"/admin", "/admin/", "/admin/dashboard", "/admin/trash", "/admin/posts", "/admin/posts/{id:[0-9]+}/edit", "/admin/media", "/admin/pages", "/admin/pages/{id:[0-9]+}/edit", "/admin/menus", "/admin/design/style", "/admin/design/components", "/admin/design/templates", "/admin/design/writing-templates", "/admin/accounts", "/admin/roles", "/admin/activity", "/admin/settings/basic", "/admin/settings/links", "/admin/settings/system"})
    public String shell() { return "forward:/next-app/index.html"; }
}
