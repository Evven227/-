package com.adc.dental.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录态拦截器：除白名单外，所有 /api/** 请求必须携带
 * Authorization: Bearer <token>。解析成功则写入 UserContext。
 */
public class AuthInterceptor implements HandlerInterceptor {

    /** 不需要登录的精确路径。 */
    private static final java.util.Set<String> WHITELIST = java.util.Set.of(
            "/api/health",
            "/api/auth/login",
            "/api/auth/register-patient");

    private final JwtService jwtService;

    public AuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = request.getRequestURI();
        if (WHITELIST.contains(path)) {
            return true;
        }
        // CORS 预检请求不携带 token，直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"未登录或登录已过期\"}");
            return false;
        }
        try {
            CurrentUser user = jwtService.parse(header.substring(7));
            UserContext.set(user);
            request.setAttribute("currentUser", user);
            return true;
        } catch (RuntimeException e) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"登录态无效，请重新登录\"}");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        UserContext.clear();
    }
}
