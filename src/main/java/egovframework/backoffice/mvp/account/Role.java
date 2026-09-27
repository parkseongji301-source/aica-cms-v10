package egovframework.backoffice.mvp.account;

/** Roles use the operating policy confirmed in phase 5B-2A; no implicit hierarchy. */
public enum Role {
    SUPER_ADMIN, ADMIN, SUPPORTER;
    public String getLabel() { return switch(this) {case SUPER_ADMIN -> "최상위 관리자";case ADMIN -> "관리자";case SUPPORTER -> "서포터즈";}; }
}
