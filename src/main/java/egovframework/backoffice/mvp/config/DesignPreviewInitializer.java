package egovframework.backoffice.mvp.config;
import egovframework.backoffice.mvp.account.*;
import egovframework.backoffice.mvp.post.PostService;
import egovframework.backoffice.mvp.security.AccountPrincipal;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Public demo credentials, ONLY in an explicitly selected, in-memory preview profile. */
@Component
@Profile("design-preview")
public class DesignPreviewInitializer implements ApplicationRunner {
    private final AccountService accounts;
    private final AccountMapper mapper;
    private final PostService posts;
    private final PasswordEncoder encoder;
    public DesignPreviewInitializer(AccountService accounts, AccountMapper mapper, PostService posts, DataSourceProperties source, PasswordEncoder encoder) {
        if (!"jdbc:h2:mem:aica-design-preview;DB_CLOSE_DELAY=-1".equals(source.getUrl()))
            throw new IllegalStateException("디자인 미리보기는 전용 메모리 DB에서만 실행할 수 있습니다.");
        this.accounts = accounts; this.mapper = mapper; this.posts = posts; this.encoder = encoder;
    }
    @Override public void run(ApplicationArguments args) {
        String email = "1234", password = "Aica-Preview-2026!";
        // Short credentials are fixtures confined to the guarded in-memory preview.
        String previewHash = encoder.encode("1234");
        long previewId = mapper.create(email, "운영 관리자", previewHash, Role.SUPER_ADMIN);
        mapper.changePassword(previewId, previewHash, false);
        var root = new AccountPrincipal(mapper.findByEmail(email));
        var issued = accounts.create(root, "editor@aica.local", "콘텐츠 담당자", Role.ADMIN);
        var member = new AccountPrincipal(mapper.findByEmail(issued.email()));
        accounts.changeOwnPassword(member, issued.temporaryPassword(), password, password);
        member = new AccountPrincipal(mapper.findByEmail(issued.email()));
        String[] titles = {
            "인공지능 사관학교 교육 공간을 소개합니다",
            "현업 멘토와 함께하는 AI 프로젝트 이야기",
            "교육생을 위한 학습 자료 이용 안내",
            "함께 성장하는 사관학교, 프로젝트 데모데이 현장",
            "AI 개발자를 위한 커리어 특강 안내",
            "새로운 도전의 시작, 교육과정 안내",
            "인공지능 사관학교 홈페이지 이용 안내",
            "인공지능 사관학교의 새로운 소식을 전합니다"
        };
        for (int i = 0; i < titles.length; i++)
            posts.create(i % 3 == 0 ? member : root, titles[i],
                "디자인 검토를 위한 예시 콘텐츠입니다.\n\n" + titles[i]
                + "\n\n인공지능 사관학교의 다양한 소식과 교육 이야기를 이곳에 담습니다. 목록, 상세, 수정 화면의 구성을 확인해 보세요.");
    }
}
