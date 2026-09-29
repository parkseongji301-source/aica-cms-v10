package egovframework.backoffice.mvp.next;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** React navigation shares existing services; /admin remains available. */
@Controller
public class NextAdminController {
    @GetMapping({"/admin-next", "/admin-next/", "/admin-next/dashboard", "/admin-next/trash", "/admin-next/posts", "/admin-next/posts/{id:[0-9]+}/edit", "/admin-next/media", "/admin-next/pages", "/admin-next/pages/{id:[0-9]+}/edit", "/admin-next/menus", "/admin-next/design/style", "/admin-next/design/components", "/admin-next/design/templates", "/admin-next/accounts", "/admin-next/roles", "/admin-next/activity", "/admin-next/settings/basic", "/admin-next/settings/links", "/admin-next/settings/system"})
    public String shell() { return "forward:/next-app/index.html"; }
}
