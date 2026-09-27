package egovframework.backoffice.mvp.next;

import egovframework.backoffice.mvp.common.BusinessException;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Order(-100)
@RestControllerAdvice(assignableTypes={NextPageApi.class,NextWorkspaceApi.class,NextPostApi.class,NextClassificationApi.class})
public class NextApiErrors {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> business(BusinessException error) {
        boolean conflict=error.getMessage().startsWith("다른 작업에서 변경");
        return failure(conflict?409:400,conflict?"REVISION_CONFLICT":"VALIDATION_ERROR",error.getMessage());
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException error) {return failure(error.getStatusCode().value(),error.getStatusCode().value()==409?"REVISION_CONFLICT":"NOT_FOUND",error.getReason()==null?"요청을 확인하세요.":error.getReason());}
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> denied() {return failure(403,"FORBIDDEN","해당 기능을 사용할 권한이 없습니다.");}
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> malformed() {return failure(400,"INVALID_JSON","입력 형식을 확인하세요.");}
    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class, org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public ResponseEntity<?> invalidParameter() {return failure(400,"INVALID_PARAMETER","입력 항목을 확인하세요.");}
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<?> tooLarge() {return failure(400,"FILE_TOO_LARGE","파일은 5MB 이하로 선택하세요.");}
    private ResponseEntity<?> failure(int status,String code,String message) {return ResponseEntity.status(status).header("Cache-Control","no-store").body(Map.of("code",code,"message",message));}
}
