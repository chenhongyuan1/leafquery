package com.example.leafquery.security;

import com.example.leafquery.controller.AdminController;
import com.example.leafquery.entity.AdminUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {
    private static final String PRINCIPAL_ATTRIBUTE = AdminAuthInterceptor.class.getName() + ".principal";
    private final AdminSessionService sessions;
    private final ObjectMapper objectMapper;

    public AdminAuthInterceptor(AdminSessionService sessions, ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // Check the resolved controller rather than trusting a client-supplied URL or header.
        if (!(handler instanceof HandlerMethod method)
                || !AdminController.class.isAssignableFrom(method.getBeanType())) {
            return true;
        }
        response.setHeader("Cache-Control", "no-store");
        if ("login".equals(method.getMethod().getName()) || "OPTIONS".equals(request.getMethod())) {
            return true;
        }
        AdminUser admin = sessions.resolve(bearerToken(request));
        if (admin == null) {
            return reject(response, 401, "请先登录，或登录已失效");
        }
        if (method.hasMethodAnnotation(RequireSuperAdmin.class) && !"super_admin".equals(admin.getRole())) {
            return reject(response, 403, "权限不足，仅系统管理员可操作");
        }
        request.setAttribute(PRINCIPAL_ATTRIBUTE, admin);
        return true;
    }

    public static AdminUser currentAdmin(HttpServletRequest request) {
        Object admin = request.getAttribute(PRINCIPAL_ATTRIBUTE);
        if (!(admin instanceof AdminUser verified)) {
            throw new IllegalStateException("Verified administrator is required");
        }
        return verified;
    }

    public static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = header.substring(7);
        return token.matches("[A-Za-z0-9_-]{43}") ? token : null;
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of("code", status, "message", message));
        return false;
    }
}
