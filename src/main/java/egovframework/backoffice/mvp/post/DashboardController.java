package egovframework.backoffice.mvp.post;

import egovframework.backoffice.mvp.security.AccountPrincipal;
import egovframework.backoffice.mvp.security.AccessPolicy;
import egovframework.backoffice.mvp.analytics.TrafficPreview;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {
    private final PostService posts;
    private final TrafficPreview traffic;
    private final AccessPolicy policy;
    public DashboardController(PostService posts, TrafficPreview traffic, AccessPolicy policy) {
        this.posts = posts; this.traffic = traffic; this.policy = policy;
    }

    @GetMapping("/admin")
    public String dashboard(@AuthenticationPrincipal AccountPrincipal principal, Model model) {
        model.addAttribute("overview", posts.dashboard(principal));
        if (policy.canManageAllPosts(principal.getRole())) model.addAttribute("traffic", traffic.overview(7));
        model.addAttribute("days", 7);
        return "dashboard";
    }
}
