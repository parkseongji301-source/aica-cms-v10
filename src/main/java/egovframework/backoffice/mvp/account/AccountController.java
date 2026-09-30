package egovframework.backoffice.mvp.account;

import egovframework.backoffice.mvp.common.BusinessException;
import egovframework.backoffice.mvp.security.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
// GET screens moved to /admin/legacy/accounts (React owns /admin/accounts); POST paths are unchanged.
public class AccountController {
    private final AccountService accounts;
    private final AccessPolicy policy;
    public AccountController(AccountService accounts, AccessPolicy policy) { this.accounts = accounts; this.policy = policy; }

    @GetMapping("/admin/legacy/accounts")
    public String list(@AuthenticationPrincipal AccountPrincipal principal, Model model) {
        model.addAttribute("accounts", accounts.list(principal));
        return "accounts/list";
    }
    @GetMapping("/admin/legacy/accounts/new")
    public String form(Model model) {
        model.addAttribute("roles", policy.creatableRoles());
        return "accounts/form";
    }
    @PostMapping("/admin/accounts")
    public String create(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam String email, @RequestParam String displayName,
            @RequestParam Role role, Model model, HttpServletResponse response, RedirectAttributes redirect) {
        try {
            var credential = accounts.create(principal, email, displayName, role);
            redirect.addFlashAttribute("credential", credential);
            return "redirect:/admin/legacy/accounts/issued";
        } catch (BusinessException error) {
            response.setStatus(400);
            model.addAttribute("formError", error.getMessage());
            model.addAttribute("selectedRole",role);model.addAttribute("email", email); model.addAttribute("displayName", displayName);
            return form(model);
        }
    }
    @GetMapping("/admin/legacy/accounts/issued")
    public String issued(Model model) {
        return model.containsAttribute("credential") ? "accounts/issued" : "redirect:/admin/legacy/accounts";
    }
    @GetMapping("/admin/legacy/accounts/{id}/role")
    public String roleForm(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id, Model model) {
        model.addAttribute("account", accounts.get(principal, id));
        model.addAttribute("roles", Role.values());
        return "accounts/role";
    }
    @PostMapping("/admin/accounts/{id}/role")
    public String changeRole(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id,
            @RequestParam Role role, RedirectAttributes redirect) {
        accounts.changeRole(principal, id, role);
        redirect.addFlashAttribute("notice", "역할을 변경했습니다. 해당 계정은 다시 로그인해야 합니다.");
        return "redirect:/admin/legacy/accounts";
    }
    @PostMapping("/admin/accounts/{id}/deactivate")
    public String deactivate(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id,
            RedirectAttributes redirect) {
        accounts.deactivate(principal, id);
        redirect.addFlashAttribute("notice", "계정을 비활성화했습니다.");
        return "redirect:/admin/legacy/accounts";
    }
    @PostMapping("/admin/accounts/{id}/reset-password")
    public String reset(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id,
            RedirectAttributes redirect) {
        redirect.addFlashAttribute("credential", accounts.resetPassword(principal, id));
        return "redirect:/admin/legacy/accounts/issued";
    }
}
