package com.aichat.common;

import com.aichat.entity.SysUser;
import com.aichat.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.PrintWriter;

// 登录鉴权拦截器：校验Authorization头中的token，客服接口校验角色
@Component
public class AuthInterceptor implements HandlerInterceptor {

    // 校验通过后存放当前用户的request属性名
    public static final String LOGIN_USER_ATTR = "LOGIN_USER";

    private final AuthService authService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行CORS预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        SysUser user = authService.resolve(extractToken(request));
        if (user == null) {
            writeJson(response, 401, "未登录或登录已过期");
            return false;
        }
        // 客服专属接口要求AGENT/ADMIN角色
        if (request.getRequestURI().startsWith("/chat/agent/") && !authService.isAgent(user)) {
            writeJson(response, 403, "无客服权限");
            return false;
        }
        request.setAttribute(LOGIN_USER_ATTR, user);
        return true;
    }

    // 从Authorization: Bearer xxx中提取token
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private void writeJson(HttpServletResponse response, int code, String msg) throws Exception {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = new Result<>(code, msg, null);
        PrintWriter writer = response.getWriter();
        writer.write(OBJECT_MAPPER.writeValueAsString(result));
        writer.flush();
    }
}
