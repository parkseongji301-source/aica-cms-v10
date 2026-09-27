package egovframework.backoffice.mvp.account;

import egovframework.backoffice.mvp.common.*;
import egovframework.backoffice.mvp.security.*;
import java.security.SecureRandom;
import java.util.*;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService extends EgovAbstractServiceImpl {
    private final AccountMapper accounts;
    private final CurrentAccount current;
    private final AccessPolicy policy;
    private final PasswordEncoder encoder;
    private final egovframework.backoffice.mvp.cms.ActivityService audit;
    private final SecureRandom random = new SecureRandom();

    public AccountService(AccountMapper accounts, CurrentAccount current, AccessPolicy policy, PasswordEncoder encoder, egovframework.backoffice.mvp.cms.ActivityService audit) {
        this.accounts = accounts; this.current = current; this.policy = policy; this.encoder = encoder; this.audit = audit;
    }
    @Transactional(readOnly = true)
    public List<Account> list(AccountPrincipal principal) {
        policy.requireAccountManagement(current.require(principal, false));
        return accounts.findAll();
    }
    @Transactional(readOnly = true)
    public Account get(AccountPrincipal principal, long id) {
        policy.requireAccountManagement(current.require(principal, false));
        return target(id);
    }
    @Transactional
    public void bootstrap(String email, String name, String password) {
        accounts.lockChanges();
        if (accounts.count() != 0) return;
        InputRules.password(password);
        accounts.create(InputRules.email(email), InputRules.text(name, 80, "이름"),
                encoder.encode(password), Role.SUPER_ADMIN);
    }
    @Transactional
    public IssuedCredential create(AccountPrincipal principal, String email, String name, Role role) {
        accounts.lockChanges();
        policy.requireAccountManagement(current.require(principal, false));
        if (role == null || !policy.canCreateAccount(role))
            throw new BusinessException("신규 계정은 ADMIN 또는 SUPPORTER로만 생성할 수 있습니다.");
        String normalized = InputRules.email(email);
        String displayName = InputRules.text(name, 80, "이름");
        String password = temporaryPassword();
        try {
            long id = accounts.create(normalized, displayName, encoder.encode(password), role);
            audit.record(current.require(principal,false),"계정 발급","계정 #"+id,displayName+" / "+role.name());
            return new IssuedCredential(id, normalized, password);
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(processException("account.duplicate").getMessage());
        }
    }
    @Transactional
    public void deactivate(AccountPrincipal principal, long id) {
        accounts.lockChanges();
        var actor = current.require(principal, false);
        policy.requireAccountManagement(actor);
        requireOther(actor, id);
        var target = target(id);
        protectLastSuper(target);
        accounts.deactivate(id);
        audit.record(actor,"계정 비활성화","계정 #"+id,target.displayName());
    }
    @Transactional
    public void changeRole(AccountPrincipal principal, long id, Role role) {
        accounts.lockChanges();
        var actor = current.require(principal, false);
        policy.requireAccountManagement(actor);
        requireOther(actor, id);
        if (role == null) throw new BusinessException("변경할 역할을 선택하세요.");
        var target = target(id);
        if (target.role() == role) return;
        if (role != Role.SUPER_ADMIN) protectLastSuper(target);
        accounts.changeRole(id, role);
        audit.record(actor,"계정 역할 변경","계정 #"+id,target.role()+" → "+role);
    }
    @Transactional
    public IssuedCredential resetPassword(AccountPrincipal principal, long id) {
        accounts.lockChanges();
        var actor = current.require(principal, false);
        policy.requireAccountManagement(actor);
        requireOther(actor, id);
        var target = target(id);
        if (!target.active()) throw new BusinessException("비활성 계정의 비밀번호는 초기화할 수 없습니다.");
        String password = temporaryPassword();
        accounts.changePassword(id, encoder.encode(password), true);
        audit.record(actor,"비밀번호 초기화","계정 #"+id,"임시 비밀번호 발급");
        return new IssuedCredential(id, target.email(), password);
    }
    @Transactional
    public void changeOwnPassword(AccountPrincipal principal, String oldPassword, String password, String confirm) {
        accounts.lockChanges();
        var actor = current.require(principal, true);
        if (oldPassword == null || !encoder.matches(oldPassword, actor.passwordHash()))
            throw new BusinessException("현재 비밀번호가 올바르지 않습니다.");
        InputRules.password(password);
        if (!password.equals(confirm)) throw new BusinessException("새 비밀번호 확인이 일치하지 않습니다.");
        if (encoder.matches(password, actor.passwordHash()))
            throw new BusinessException("현재 비밀번호와 다른 새 비밀번호를 입력하세요.");
        accounts.changePassword(actor.id(), encoder.encode(password), false);
        audit.record(actor,"본인 비밀번호 변경","계정 #"+actor.id(),"");
    }
    private Account target(long id) {
        var target = accounts.findById(id);
        if (target == null) throw new BusinessException("계정을 찾을 수 없습니다.");
        return target;
    }
    private void requireOther(Account actor, long id) {
        if (actor.id() == id) throw new BusinessException("본인의 역할·활성 상태·임시 비밀번호는 여기서 변경할 수 없습니다.");
    }
    private void protectLastSuper(Account target) {
        if (target.active() && target.role() == Role.SUPER_ADMIN && accounts.activeSuperCount() <= 1)
            throw new BusinessException("마지막 활성 SUPER_ADMIN은 비활성화하거나 강등할 수 없습니다.");
    }
    private String temporaryPassword() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
