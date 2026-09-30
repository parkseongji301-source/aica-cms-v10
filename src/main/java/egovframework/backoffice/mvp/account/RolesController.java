package egovframework.backoffice.mvp.account;
import egovframework.backoffice.mvp.security.AccessPolicy;
import java.util.Arrays;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class RolesController {
    private final AccessPolicy policy;
    public RolesController(AccessPolicy policy) { this.policy = policy; }
    public record RoleCard(String code, String label, boolean manageAccounts, boolean allPosts,boolean publish,boolean structure,boolean permanentDelete) {}
    @GetMapping("/admin/legacy/roles")
    public String roles(Model model) {
        model.addAttribute("roleCards", Arrays.stream(Role.values()).map(role -> new RoleCard(role.name(),
            switch (role) { case SUPER_ADMIN -> "최상위 관리자"; case ADMIN -> "관리자"; case SUPPORTER -> "서포터즈"; },
            policy.canManageAccounts(role), policy.canManageAllPosts(role),policy.canPublish(role),policy.canManageSite(role),policy.canDelete(role))).toList());
        return "accounts/roles";
    }
}
