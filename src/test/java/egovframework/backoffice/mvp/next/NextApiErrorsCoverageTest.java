package egovframework.backoffice.mvp.next;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import static org.assertj.core.api.Assertions.assertThat;

/** Every REST controller of the React API must be listed in NextApiErrors, or its errors render as the Thymeleaf error page. */
class NextApiErrorsCoverageTest {
    @Test void everyNextRestControllerGetsJsonErrors() {
        var scanner=new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> controllers=scanner.findCandidateComponents(NextApiErrors.class.getPackageName()).stream().map(b->b.getBeanClassName()).collect(Collectors.toSet());
        Set<String> covered=Arrays.stream(NextApiErrors.class.getAnnotation(RestControllerAdvice.class).assignableTypes()).map(Class::getName).collect(Collectors.toSet());
        assertThat(controllers).isNotEmpty();
        assertThat(covered).containsAll(controllers);
    }
}
