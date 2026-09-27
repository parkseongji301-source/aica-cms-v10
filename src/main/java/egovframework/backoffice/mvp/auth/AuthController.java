package egovframework.backoffice.mvp.auth;

import egovframework.backoffice.mvp.account.AccountService;
import egovframework.backoffice.mvp.common.BusinessException;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {
    private final AccountService accounts;
    public AuthController(AccountService accounts) { this.accounts = accounts; }
    @GetMapping("/") public String home() { return "redirect:/admin"; }
    @GetMapping("/login") public String login() { return "auth/login"; }
    @GetMapping("/account/password") public String password() { return "auth/password"; }

    @PostMapping("/account/password")
    public String changePassword(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam String currentPassword, @RequestParam String newPassword,
            @RequestParam String confirmPassword, Authentication authentication,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        try {
            accounts.changeOwnPassword(principal, currentPassword, newPassword, confirmPassword);
        } catch (BusinessException error) {
            response.setStatus(400);
            model.addAttribute("formError", error.getMessage());
            return "auth/password";
        }
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/login?changed";
    }
}
