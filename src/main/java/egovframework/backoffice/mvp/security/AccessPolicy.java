package egovframework.backoffice.mvp.security;

import egovframework.backoffice.mvp.account.Account;
import egovframework.backoffice.mvp.account.Role;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/** Change role-to-action assignments here; no implicit role hierarchy. */
@Component
public class AccessPolicy {
    public enum Capability { MANAGE_ACCOUNTS, ALL_POSTS, OWN_POSTS, PUBLISH_POSTS, MANAGE_SITE, DELETE_PERMANENT }
    private static final Map<Role, Set<Capability>> RULES = Map.of(
            Role.SUPER_ADMIN, Set.of(Capability.MANAGE_ACCOUNTS, Capability.ALL_POSTS, Capability.OWN_POSTS, Capability.PUBLISH_POSTS, Capability.MANAGE_SITE, Capability.DELETE_PERMANENT),
            Role.ADMIN, Set.of(Capability.ALL_POSTS, Capability.OWN_POSTS, Capability.PUBLISH_POSTS),
            Role.SUPPORTER, Set.of(Capability.OWN_POSTS));
    private static final Set<Role> CREATABLE = Set.of(Role.ADMIN, Role.SUPPORTER);

    public static List<SimpleGrantedAuthority> authorities(Role role) {
        return RULES.getOrDefault(role, Set.of()).stream()
                .map(c -> new SimpleGrantedAuthority(c.name())).toList();
    }
    public boolean canManageAccounts(Role role) { return allows(role, Capability.MANAGE_ACCOUNTS); }
    public boolean canManageAllPosts(Role role) { return allows(role, Capability.ALL_POSTS); }
    public boolean canPublish(Role role) { return allows(role, Capability.PUBLISH_POSTS); }
    public boolean canManageSite(Role role) { return allows(role, Capability.MANAGE_SITE); }
    public boolean canDelete(Role role) { return allows(role, Capability.DELETE_PERMANENT); }
    public void requirePublish(Account actor) {
        if (!canPublish(actor.role())) throw new AccessDeniedException("발행·공개 중단 권한이 없습니다.");
    }
    public void requireStructure(Account actor) {
        if (!canManageSite(actor.role())) throw new AccessDeniedException("사이트 구조·공통 설정 변경은 SUPER_ADMIN만 가능합니다.");
    }
    public void requireDelete(Account actor) {
        if (!canDelete(actor.role())) throw new AccessDeniedException("영구 삭제는 SUPER_ADMIN만 가능합니다.");
    }
    public boolean canCreateAccount(Role role) { return CREATABLE.contains(role); }
    public List<Role> creatableRoles() { return List.of(Role.ADMIN, Role.SUPPORTER); }
    private boolean allows(Role role, Capability capability) {
        return RULES.getOrDefault(role, Set.of()).contains(capability);
    }
    public void requireAccountManagement(Account actor) {
        if (!canManageAccounts(actor.role())) throw new AccessDeniedException("계정 관리 권한이 없습니다.");
    }
    public void requirePostAccess(Account actor, long authorId) {
        if (!canManageAllPosts(actor.role())
                && !(allows(actor.role(), Capability.OWN_POSTS) && actor.id() == authorId))
            throw new AccessDeniedException("해당 게시물에 접근할 권한이 없습니다.");
    }
}
