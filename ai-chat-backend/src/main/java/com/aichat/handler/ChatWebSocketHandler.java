package com.aichat.handler;

import com.aichat.entity.SysUser;
import com.aichat.service.AuthService;
import com.aichat.service.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final WebSocketService webSocketService;
    private final AuthService authService;

    public ChatWebSocketHandler(WebSocketService webSocketService, AuthService authService) {
        this.webSocketService = webSocketService;
        this.authService = authService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = extractParam(session, "sessionId");
        if (sessionId == null) {
            log.warn("websocket连接缺少sessionId参数，直接关闭 uri={}", session.getUri());
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        if ("agent".equals(extractParam(session, "role"))) {
            // 坐席连接必须携带有效token且角色为AGENT/ADMIN
            SysUser user = authService.resolve(extractParam(session, "token"));
            if (!authService.isAgent(user)) {
                log.warn("坐席websocket鉴权失败，直接关闭 uri={}", session.getUri());
                session.close(CloseStatus.NOT_ACCEPTABLE);
                return;
            }
            session.getAttributes().put("role", "agent");
            log.info("坐席websocket建立连接:{} agent={}", sessionId, user.getUsername());
            webSocketService.addAgentSession(sessionId, session);
            return;
        }
        session.getAttributes().put("role", "visitor");
        log.info("websocket建立连接:{}", sessionId);
        webSocketService.addSession(sessionId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = extractParam(session, "sessionId");
        if (sessionId == null) {
            return;
        }
        String payload = message.getPayload();
        // 按连接角色分流：坐席消息推给访客，访客消息走AI或转坐席
        if ("agent".equals(session.getAttributes().get("role"))) {
            webSocketService.handleAgentMsg(sessionId, payload);
        } else {
            webSocketService.handleVisitorMsg(sessionId, payload);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = extractParam(session, "sessionId");
        if (sessionId == null) {
            return;
        }
        if ("agent".equals(session.getAttributes().get("role"))) {
            webSocketService.removeAgentSession(sessionId, session);
        } else {
            webSocketService.removeSession(sessionId);
        }
    }

    // 从连接地址如 /ws/chat?sessionId=xxx&role=agent 中解析指定参数，兼容多个查询参数
    private String extractParam(WebSocketSession session, String name) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String param : uri.getQuery().split("&")) {
            int idx = param.indexOf('=');
            if (idx > 0 && name.equals(param.substring(0, idx))) {
                return URLDecoder.decode(param.substring(idx + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
