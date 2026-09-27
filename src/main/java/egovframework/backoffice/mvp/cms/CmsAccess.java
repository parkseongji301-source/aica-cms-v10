package egovframework.backoffice.mvp.cms;
import egovframework.backoffice.mvp.account.Account;
import egovframework.backoffice.mvp.security.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
@Component
public class CmsAccess {
 private final CurrentAccount current;
 private final AccessPolicy policy;
 public CmsAccess(CurrentAccount current, AccessPolicy policy) { this.current=current; this.policy=policy; }
 public Account actor(AccountPrincipal principal) { return current.require(principal,false); }
 public Account manager(AccountPrincipal principal) {
  var actor=actor(principal);
  if (!policy.canManageAllPosts(actor.role())) throw new AccessDeniedException("사이트 관리 권한이 없습니다.");
  return actor;
 }
 public Account operator(AccountPrincipal principal) {
  var actor=actor(principal); policy.requireAccountManagement(actor); return actor;
 }
 public Account structure(AccountPrincipal principal) {var user=actor(principal);policy.requireStructure(user);return user;}
 public Account permanentDelete(AccountPrincipal principal) {var user=actor(principal);policy.requireDelete(user);return user;}
 public Long owner(AccountPrincipal principal) {
  var actor=actor(principal); return policy.canManageAllPosts(actor.role()) ? null : actor.id();
 }
 public void media(AccountPrincipal principal, long ownerId) {
  var actor=actor(principal);
  if (!policy.canManageAllPosts(actor.role()) && actor.id()!=ownerId)
   throw new AccessDeniedException("본인이 올린 미디어만 관리할 수 있습니다.");
 }
}

