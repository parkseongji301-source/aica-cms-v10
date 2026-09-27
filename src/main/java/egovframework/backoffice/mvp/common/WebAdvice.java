package egovframework.backoffice.mvp.common;

import egovframework.backoffice.mvp.security.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;

@ControllerAdvice
public class WebAdvice {
    private final AccessPolicy policy;
    private final java.time.Clock clock;
    @Value("${backoffice.preview:false}") private boolean preview;
    public WebAdvice(AccessPolicy policy,java.time.Clock clock) { this.policy = policy;this.clock=clock; }

    @ModelAttribute
    public void common(Model model, HttpServletRequest request) {
        String path = request.getServletPath();
        String active = path.startsWith("/admin/posts") ? "posts"
                : path.startsWith("/admin/media") ? "media" : path.startsWith("/admin/pages") ? "pages"
                : path.startsWith("/admin/categories") ? "categories" : path.startsWith("/admin/menus") ? "menus"
                : path.startsWith("/admin/design/") ? path.substring(path.lastIndexOf('/')+1)
                : path.startsWith("/admin/settings/") ? path.substring(path.lastIndexOf('/')+1)
                : path.startsWith("/admin/accounts") ? "accounts" : path.equals("/admin/roles") ? "roles"
                : path.equals("/admin/activity") ? "activity" : path.equals("/account/password") ? "password" : "dashboard";
        if(active.equals("components"))active="style";if(active.equals("system"))active="basic";if(active.equals("roles"))active="accounts";
        model.addAttribute("activeNav", active);
        model.addAttribute("sectionLabel", switch(active) {
            case "posts", "media", "categories" -> "콘텐츠 관리";
            case "pages" -> "페이지 관리";
            case "menus" -> "메뉴 관리";
            case "style", "components" -> "디자인 관리";
            case "accounts", "roles", "activity" -> "운영 관리"; case "basic", "links", "system" -> "사이트 설정";
            case "password" -> "내 계정 설정"; default -> "대시보드";
        });
        model.addAttribute("previewMode", preview);
        model.addAttribute("operatingTimeZone",clock.getZone().getId());
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AccountPrincipal principal) {
            model.addAttribute("currentUser", principal);
            model.addAttribute("roleLabel", switch (principal.getRole()) {
                case SUPER_ADMIN -> "최상위 관리자"; case ADMIN -> "관리자"; case SUPPORTER -> "서포터즈";
            });
            model.addAttribute("canManageAccounts", policy.canManageAccounts(principal.getRole()));
            model.addAttribute("canManageAllPosts", policy.canManageAllPosts(principal.getRole()));
            model.addAttribute("canPublish",policy.canPublish(principal.getRole()));
            model.addAttribute("canManageSite",policy.canManageSite(principal.getRole()));
            model.addAttribute("canDeletePermanent",policy.canDelete(principal.getRole()));
        }
    }
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String duplicate(Model model) {
        model.addAttribute("errorTitle", "이미 사용 중인 이름입니다");
        model.addAttribute("errorMessage", "다른 이름이나 주소를 입력해 주세요.");
        return "error";
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String uploadSize(Model model) {
        model.addAttribute("errorTitle", "파일 크기를 확인하세요");
        model.addAttribute("errorMessage", "파일당 5MB 이하의 이미지 또는 문서 파일을 선택하세요.");
        return "error";
    }
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String business(BusinessException error, Model model) {
        model.addAttribute("errorTitle", "입력 내용을 확인하세요");
        model.addAttribute("errorMessage", error.getMessage());
        return "error";
    }
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String denied(Model model) {
        model.addAttribute("errorTitle", "접근 권한이 없습니다");
        model.addAttribute("errorMessage", "허용된 계정과 게시물 범위에서만 이용할 수 있습니다.");
        return "error";
    }
    @ExceptionHandler(ResponseStatusException.class)
    public String missing(ResponseStatusException error, Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setStatus(error.getStatusCode().value());
        model.addAttribute("errorTitle", "요청한 항목을 찾을 수 없습니다");
        model.addAttribute("errorMessage", error.getReason());
        return "error";
    }
}
