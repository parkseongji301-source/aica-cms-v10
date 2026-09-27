package egovframework.backoffice.mvp.cms;
import egovframework.backoffice.mvp.common.BusinessException;
import java.net.URI;
import java.util.*;
public final class CmsRules {
    private CmsRules() {}
    public static String optional(String value, int max, String label) {
        String clean = value == null ? "" : value.strip();
        if (clean.length() > max) throw new BusinessException(label + "은(는) " + max + "자 이하로 입력하세요.");
        return clean;
    }
    public static String url(String value, boolean required) {
        String clean = optional(value, 1000, "링크");
        if (clean.isEmpty() && !required) return "";
        if(clean.startsWith("/") && !clean.startsWith("//") && !clean.contains("\\") && !clean.contains("\r") && !clean.contains("\n")) {
            try { URI uri=URI.create(clean); if(uri.getRawAuthority()==null)return clean; } catch(IllegalArgumentException ignored) {}
        }
        try {
            URI uri = URI.create(clean);
            if (Set.of("http", "https").contains(Objects.toString(uri.getScheme(),"").toLowerCase(Locale.ROOT))
                    && uri.getHost() != null && uri.getUserInfo() == null) return clean;
        } catch (IllegalArgumentException ignored) {}
        throw new BusinessException("링크는 http(s) 주소 또는 /로 시작하는 내부 경로로 입력하세요.");
    }
    public static void revision(long actual, Long expected) {
        if (expected == null) throw new BusinessException("저장 버전이 필요합니다. 다시 조회하세요.");
        if (actual != expected)
            throw new BusinessException("다른 작업에서 변경된 내용이 있습니다. 새로고침 후 다시 수정하세요.");
    }
    public static List<Long> ids(List<Long> ids, int maximum) {
        if (ids == null) return List.of();
        if (ids.size() > maximum || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new BusinessException("선택한 항목을 확인하세요. 최대 " + maximum + "개까지 가능합니다.");
        return ids.stream().distinct().toList();
    }
}

