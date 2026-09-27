package egovframework.backoffice.mvp.common;

/** Keep a list location without allowing a client-supplied external redirect. */
public final class ListLocation {
    private ListLocation() {}
    public static String posts(String value) {
        if(value==null || value.length()>2000 || value.indexOf('#')>=0 || value.indexOf('\\')>=0 || value.chars().anyMatch(Character::isISOControl)) return "/admin/posts";
        return value.equals("/admin/posts") || value.startsWith("/admin/posts?") ? value : "/admin/posts";
    }
}
