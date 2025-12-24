package com.enterprise.quota.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 登录拦截器
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                           Object handler) throws Exception {
        // 允许的公开路径
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/") || 
            path.startsWith("/api/user/register") ||  // 允许注册接口
            path.equals("/login.html") ||
            (path.endsWith(".html") && path.contains("login")) ||
            path.endsWith(".css") ||
            path.endsWith(".js") ||
            path.endsWith(".png") ||
            path.endsWith(".jpg") ||
            path.endsWith(".ico") ||
            path.startsWith("/static/") ||
            path.startsWith("/css/") ||
            path.startsWith("/js/") ||
            path.startsWith("/images/")) {
            return true;
        }
        
        // 对于根路径，如果未登录则重定向到登录页
        if (path.equals("/") || path.equals("")) {
            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute("userId") == null) {
                response.sendRedirect("/login.html");
                return false;
            }
            return true;
        }
        
        // 检查Session
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userId") != null) {
            return true;
        }
        
        // 未登录，返回401
        if (path.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"请先登录\"}");
        } else {
            // 重定向到登录页
            response.sendRedirect("/login.html");
        }
        return false;
    }
}

