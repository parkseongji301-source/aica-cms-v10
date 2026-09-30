package egovframework.backoffice.mvp.security;

import egovframework.backoffice.mvp.account.AccountMapper;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    /** Public reads never load an administrator's session or invoke account-session validation. */
    @Bean @org.springframework.core.annotation.Order(1)
    SecurityFilterChain publicApiSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/public/**")
            .authorizeHttpRequests(auth->auth
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/public/v1/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.HEAD,"/api/public/v1/**").permitAll()
                .anyRequest().denyAll())
            .sessionManagement(session->session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .securityContext(context->context.securityContextRepository(new org.springframework.security.web.context.NullSecurityContextRepository()))
            .requestCache(cache->cache.requestCache(new NullRequestCache()))
            .exceptionHandling(errors->errors
                .authenticationEntryPoint((request,response,error)->ApiSecurityResponse.write(response,403,"READ_ONLY"))
                .accessDeniedHandler((request,response,error)->ApiSecurityResponse.write(response,403,"READ_ONLY")))
            .headers(headers->headers.contentSecurityPolicy(csp->csp.policyDirectives("default-src 'none'; sandbox; frame-ancestors 'none'")))
            .build();
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, AccountDetailsService details,
                                                  PasswordEncoder encoder, AccountMapper accounts) throws Exception {
        var provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(details);
        provider.setPasswordEncoder(encoder);
        return http.authenticationProvider(provider)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/login", "/css/**", "/js/**", "/error").permitAll()
                        // Legacy Thymeleaf screens (comparison and recovery) keep the authorities they had under /admin.
                        .requestMatchers("/admin/legacy/pages/new", "/admin/legacy/menus", "/admin/legacy/menus/**", "/admin/legacy/categories", "/admin/legacy/categories/**", "/admin/legacy/design/**", "/admin/legacy/settings/**").hasAuthority(AccessPolicy.Capability.MANAGE_SITE.name())
                        .requestMatchers("/admin/legacy/posts/*/delete-confirm", "/admin/legacy/pages/*/delete-confirm").hasAuthority(AccessPolicy.Capability.DELETE_PERMANENT.name())
                        .requestMatchers("/admin/legacy/pages", "/admin/legacy/pages/**").hasAuthority(AccessPolicy.Capability.ALL_POSTS.name())
                        .requestMatchers("/admin/legacy/activity", "/admin/legacy/roles", "/admin/legacy/accounts", "/admin/legacy/accounts/**").hasAuthority(AccessPolicy.Capability.MANAGE_ACCOUNTS.name())
                        .requestMatchers("/admin/legacy", "/admin/legacy/posts", "/admin/legacy/posts/**", "/admin/legacy/media", "/admin/legacy/media/**").authenticated()
                        // Any signed-in account receives the static React shell; it carries no data, and each API keeps its own 401/403.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/admin", "/admin/", "/admin/dashboard", "/admin/trash", "/admin/posts", "/admin/posts/*/edit", "/admin/media", "/admin/pages", "/admin/pages/*/edit", "/admin/menus", "/admin/design/style", "/admin/design/components", "/admin/design/templates", "/admin/design/writing-templates", "/admin/accounts", "/admin/roles", "/admin/activity", "/admin/settings/basic", "/admin/settings/links", "/admin/settings/system", "/next-app/**").authenticated()
                        // Old addresses redirect to React (or to the legacy screen that has no React equivalent); the target enforces its rule.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/admin-next", "/admin-next/**", "/admin/posts/new", "/admin/posts/*", "/admin/posts/*/publication", "/admin/pages/new", "/admin/pages/*/preview", "/admin/accounts/new", "/admin/accounts/issued", "/admin/accounts/*/role", "/admin/categories").authenticated()
                        .requestMatchers("/admin/pages/new", "/admin/menus", "/admin/menus/**", "/admin/categories", "/admin/categories/**", "/admin/structure/**", "/admin/design/**", "/admin/settings/**", "/api/admin/next/menus", "/api/admin/next/menus/**", "/api/admin/next/links", "/api/admin/next/links/**", "/api/admin/next/categories", "/api/admin/next/categories/**", "/api/admin/next/settings/**").hasAuthority(AccessPolicy.Capability.MANAGE_SITE.name())
                        .requestMatchers("/admin/posts/*/delete", "/admin/posts/*/delete-confirm", "/admin/pages/*/delete", "/admin/pages/*/delete-confirm").hasAuthority(AccessPolicy.Capability.DELETE_PERMANENT.name())
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE,"/api/admin/next/pages/*").hasAuthority(AccessPolicy.Capability.DELETE_PERMANENT.name())
                        .requestMatchers("/api/admin/next/pages/*/delete-impact").hasAuthority(AccessPolicy.Capability.DELETE_PERMANENT.name())
                        .requestMatchers(org.springframework.http.HttpMethod.PUT,"/api/admin/next/pages/*/placement","/api/admin/next/page-order","/api/admin/next/pages/*/composition").hasAuthority(AccessPolicy.Capability.MANAGE_SITE.name())
                        .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/admin/next/page-groups").hasAuthority(AccessPolicy.Capability.MANAGE_SITE.name())
                        .requestMatchers("/api/admin/next/posts/trash", "/api/admin/next/posts/*/trash", "/api/admin/next/posts/*/restore").hasAuthority(AccessPolicy.Capability.DELETE_PERMANENT.name())
                        .requestMatchers("/admin/posts/*/unpublish").hasAuthority(AccessPolicy.Capability.PUBLISH_POSTS.name())
                        .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/admin/next/posts/*/publish").hasAuthority(AccessPolicy.Capability.PUBLISH_POSTS.name())
                        .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/admin/next/posts/*/unpublish").hasAuthority(AccessPolicy.Capability.PUBLISH_POSTS.name())
                        .requestMatchers("/api/admin/next/version-baseline").hasAuthority(AccessPolicy.Capability.MANAGE_ACCOUNTS.name())
                        .requestMatchers("/api/admin/next/posts/*/versions", "/api/admin/next/posts/*/versions/**").authenticated()
                        .requestMatchers("/api/admin/next/page-templates", "/api/admin/next/page-templates/**").hasAuthority(AccessPolicy.Capability.MANAGE_ACCOUNTS.name())
                        .requestMatchers("/api/admin/next/accounts", "/api/admin/next/accounts/**", "/api/admin/next/roles", "/api/admin/next/activity").hasAuthority(AccessPolicy.Capability.MANAGE_ACCOUNTS.name())
                        .requestMatchers("/api/admin/next/pages", "/api/admin/next/pages/**", "/api/admin/next/page-components", "/api/admin/next/page-structure", "/api/admin/next/menus", "/api/admin/next/menus/**", "/api/admin/next/links", "/api/admin/next/links/**", "/api/admin/next/settings/**").hasAuthority(AccessPolicy.Capability.ALL_POSTS.name())
                        .requestMatchers("/api/admin/next/bootstrap", "/api/admin/next/dashboard", "/api/admin/next/posts", "/api/admin/next/posts/*", "/api/admin/next/posts/*/preview", "/api/admin/next/posts/*/publication", "/api/admin/next/posts/*/publication/view", "/api/admin/next/classifications", "/api/admin/next/media", "/api/admin/next/media/**").authenticated()
                        .requestMatchers("/admin/pages", "/admin/pages/**", "/admin/categories", "/admin/categories/**", "/admin/menus", "/admin/menus/**", "/admin/structure/**", "/admin/design/**", "/admin/settings/**").hasAuthority(AccessPolicy.Capability.ALL_POSTS.name())
                        .requestMatchers("/admin/activity").hasAuthority(AccessPolicy.Capability.MANAGE_ACCOUNTS.name())
                        .requestMatchers("/admin/roles").hasAuthority(AccessPolicy.Capability.MANAGE_ACCOUNTS.name())
                        .requestMatchers("/admin/accounts/**").hasAuthority(AccessPolicy.Capability.MANAGE_ACCOUNTS.name())
                        .requestMatchers("/", "/admin", "/account/password", "/admin/posts", "/admin/posts/**", "/admin/media", "/admin/media/**").authenticated()
                        .requestMatchers("/api/admin/next/writing-templates/manage", "/api/admin/next/writing-templates/manage/**").hasAuthority(AccessPolicy.Capability.MANAGE_SITE.name())
                        .requestMatchers("/api/admin/next/writing-templates", "/api/admin/next/writing-templates/*", "/api/admin/next/writing-templates/*/prepare").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                    .authenticationEntryPoint((request,response,error) -> {
                        if(ApiSecurityResponse.isNextApi(request))ApiSecurityResponse.write(response,401,"AUTH_REQUIRED");
                        else response.sendRedirect(request.getContextPath()+"/login");
                    })
                    .accessDeniedHandler((request,response,error) -> {
                        if(ApiSecurityResponse.isNextApi(request))ApiSecurityResponse.write(response,403,"FORBIDDEN_OR_CSRF");
                        else response.sendError(403);
                    }))
                .formLogin(form -> form.loginPage("/login").loginProcessingUrl("/login")
                        .failureUrl("/login?error").successHandler((request, response, authentication) -> {
                            var principal = (AccountPrincipal) authentication.getPrincipal();
                            response.sendRedirect(request.getContextPath() +
                                    (principal.isPasswordChangeRequired() ? "/account/password" : "/admin"));
                        }).permitAll())
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID"))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .httpBasic(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.contentSecurityPolicy(csp ->
                        csp.policyDirectives("default-src 'self'; style-src 'self'; form-action 'self'; frame-ancestors 'none'")))
                .addFilterBefore(new AccountSessionFilter(accounts), AuthorizationFilter.class)
                .build();
    }
}
