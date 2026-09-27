package egovframework.backoffice.mvp.security;

import egovframework.backoffice.mvp.account.AccountMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs inside the Security chain, before authorization; not a servlet filter bean. */
public class AccountSessionFilter extends OncePerRequestFilter {
    private final AccountMapper accounts;
    public AccountSessionFilter(AccountMapper accounts) { this.accounts = accounts; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            var current = accounts.findById(principal.getId());
            if (current == null || !current.active() || current.authVersion() != principal.getAuthVersion()) {
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                if(ApiSecurityResponse.isNextApi(request))ApiSecurityResponse.write(response,401,"SESSION_EXPIRED");
                else response.sendRedirect(request.getContextPath() + "/login?expired");
                return;
            }
            String path = request.getServletPath();
            boolean permitted = path.equals("/account/password") || path.equals("/logout")
                    || path.equals("/login") || path.equals("/error") || path.startsWith("/css/") || path.startsWith("/js/");
            if (current.passwordChangeRequired() && !permitted) {
                if(ApiSecurityResponse.isNextApi(request))ApiSecurityResponse.write(response,403,"PASSWORD_CHANGE_REQUIRED");
                else if ("GET".equals(request.getMethod()))
                    response.sendRedirect(request.getContextPath() + "/account/password");
                else response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
