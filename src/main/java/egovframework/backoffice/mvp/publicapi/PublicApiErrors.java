package egovframework.backoffice.mvp.publicapi;

import egovframework.backoffice.mvp.common.BusinessException;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@Order(-110)
@RestControllerAdvice(assignableTypes=PublicSiteApi.class)
public class PublicApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException error){return failure(error.getStatusCode().value(),error.getStatusCode().value()==404?"NOT_FOUND":"INVALID_REQUEST");}
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> parameter(){return failure(400,"INVALID_REQUEST");}
    @ExceptionHandler({BusinessException.class,Exception.class})
    public ResponseEntity<?> unavailable(Exception error) {
        LoggerFactory.getLogger(PublicApiErrors.class).error("Public snapshot could not be served",error);
        return failure(503,"PUBLICATION_UNAVAILABLE");
    }
    private ResponseEntity<?> failure(int status,String code){return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("code",code));}
}
