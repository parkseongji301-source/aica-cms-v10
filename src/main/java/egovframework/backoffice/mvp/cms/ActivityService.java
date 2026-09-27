package egovframework.backoffice.mvp.cms;
import egovframework.backoffice.mvp.account.Account;
import org.springframework.stereotype.Service;
import static egovframework.backoffice.mvp.cms.CmsStore.values;
@Service
public class ActivityService {
    private final CmsStore store;
    public ActivityService(CmsStore store) { this.store=store; }
    public long record(Account actor, String action, String target, String detail) {
        return store.create("audit", values("actorId",actor.id(),"actorName",actor.displayName(),
            "action",action,"target",target,"detail",detail));
    }
}

