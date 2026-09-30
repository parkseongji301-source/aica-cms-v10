package egovframework.backoffice.mvp.next;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Keeps bookmarks working after React moved to /admin. Redirects are 302 so they can be withdrawn
 * without browsers caching them; each target still enforces its own authorization rule.
 */
@Controller
public class AdminRouteRedirectController {
    /** /admin-next/** was the React preview address; the same screen now lives under /admin. */
    @GetMapping({"/admin-next", "/admin-next/", "/admin-next/**"})
    public String preview(HttpServletRequest request) {
        String path = request.getServletPath().substring("/admin-next".length());
        if (path.equals("/")) path = "";
        return "redirect:/admin" + path + query(request);
    }

    /** Legacy-only screens without a React address open the React screen that does the same work. */
    @GetMapping({"/admin/posts/new"}) public String newPost() { return "redirect:/admin/posts"; }
    @GetMapping({"/admin/posts/{id:[0-9]+}", "/admin/posts/{id:[0-9]+}/publication"})
    public String post(@PathVariable long id) { return "redirect:/admin/posts/" + id + "/edit"; }
    @GetMapping("/admin/pages/new") public String newPage() { return "redirect:/admin/pages"; }
    @GetMapping("/admin/pages/{id:[0-9]+}/preview")
    public String pagePreview(@PathVariable long id) { return "redirect:/admin/pages/" + id + "/edit"; }
    @GetMapping({"/admin/accounts/new", "/admin/accounts/issued", "/admin/accounts/{id:[0-9]+}/role"})
    public String account() { return "redirect:/admin/accounts"; }
    /** Categories are being retired and have no React screen; the legacy screen stays reachable. */
    @GetMapping("/admin/categories")
    public String categories(HttpServletRequest request) { return "redirect:/admin/legacy/categories" + query(request); }

    private static String query(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null || query.isEmpty() ? "" : "?" + query;
    }
}
