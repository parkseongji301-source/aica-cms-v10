package egovframework.backoffice.mvp.security;

import jakarta.servlet.http.*;
import java.io.IOException;

public final class ApiSecurityResponse {
    private ApiSecurityResponse() {}
    public static boolean isNextApi(HttpServletRequest request) {return request.getServletPath().startsWith("/api/admin/next/");}
    public static void write(HttpServletResponse response,int status,String code) throws IOException {
        response.setStatus(status);response.setContentType("application/json;charset=UTF-8");response.setHeader("Cache-Control","no-store");
        response.getWriter().write("{\"code\":\""+code+"\",\"message\":\""+(status==401?"로그인이 만료되었습니다. 다시 로그인한 뒤 저장을 재시도하세요.":"권한 또는 보안 토큰을 확인하세요.")+"\"}");
    }
}
