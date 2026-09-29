package com.aichat.service;

import com.aichat.entity.ChatMessage;
import com.aichat.entity.ChatSession;
import com.aichat.mapper.ChatMessageMapper;
import com.aichat.mapper.ChatSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class WebSocketService {
    // 保存在线ws连接 sessionId -> websocketSession（访客）
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();
    // 坐席ws连接 sessionId -> 该会话的坐席连接集合
    private final Map<String, Set<WebSocketSession>> agentSessionMap = new ConcurrentHashMap<>();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private LlmService llmService;
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    @Autowired
    private ChatSessionMapper chatSessionMapper;

    public void addSession(String sessionId, WebSocketSession wsSession) {
        sessionMap.put(sessionId, wsSession);
    }

    public void removeSession(String sessionId) {
        sessionMap.remove(sessionId);
    }

    public void addAgentSession(String sessionId, WebSocketSession wsSession) {
        agentSessionMap.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(wsSession);
    }

    public void removeAgentSession(String sessionId, WebSocketSession wsSession) {
        Set<WebSocketSession> set = agentSessionMap.get(sessionId);
        if (set == null) {
            return;
        }
        set.remove(wsSession);
        if (set.isEmpty()) {
            agentSessionMap.remove(sessionId);
        }
    }

    // 处理访客发来消息
    public void handleVisitorMsg(String sessionId, String content) {
        log.info("收到访客消息 sessionId={}, msg={}", sessionId, content);
        // 消息入库
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setSenderType("VISITOR");
        msg.setContent(content);
        msg.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(msg);

        // 判断会话状态（sessionId是业务列不是主键，不能用selectById）
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getSessionId, sessionId);
        ChatSession chatSession = chatSessionMapper.selectOne(wrapper);
        if (chatSession == null || "AI".equals(chatSession.getSessionStatus())) {
            // AI接待模式，投递MQ异步调用LLM
            llmService.sendLlmTask(sessionId, content);
        } else {
            // 人工接待模式：实时推送给接待该会话的坐席
            Map<String, String> payload = new HashMap<>();
            payload.put("senderType", "VISITOR");
            payload.put("content", content);
            payload.put("sessionId", sessionId);
            sendToAgents(sessionId, payload);
        }
    }

    // 处理坐席发来消息：入库、推送给访客、并回显给在线坐席工作台
    public void handleAgentMsg(String sessionId, String content) {
        log.info("收到坐席消息 sessionId={}, msg={}", sessionId, content);
        sendMsgToVisitor(sessionId, content, "AGENT");
        Map<String, String> payload = new HashMap<>();
        payload.put("senderType", "AGENT");
        payload.put("content", content);
        sendToAgents(sessionId, payload);
    }

    // 通知所有在线坐席刷新会话列表（转人工/认领/释放时调用）
    public void broadcastToAgents(String event, String sessionId) {
        Map<String, String> payload = new HashMap<>();
        payload.put("event", event);
        payload.put("sessionId", sessionId);
        String json = toJson(payload);
        agentSessionMap.values().forEach(set -> set.forEach(ws -> send(ws, json)));
    }

    // 推送消息给该会话的在线坐席
    private void sendToAgents(String sessionId, Map<String, String> payload) {
        Set<WebSocketSession> set = agentSessionMap.get(sessionId);
        if (set == null || set.isEmpty()) {
            log.warn("无在线坐席接待该会话，消息仅入库 sessionId={}", sessionId);
            return;
        }
        String json = toJson(payload);
        set.forEach(ws -> send(ws, json));
    }

    // 推送消息给访客前端：无论WS是否在线都先入库，避免访客断线期间回复丢失
    public void sendMsgToVisitor(String sessionId, String content, String senderType) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSenderType(senderType);
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(message);

        WebSocketSession ws = sessionMap.get(sessionId);
        if (ws != null && ws.isOpen()) {
            // 推送JSON格式，前端据此区分AI/坐席/系统消息
            Map<String, String> payload = new HashMap<>();
            payload.put("senderType", senderType);
            payload.put("content", content);
            send(ws, toJson(payload));
        } else {
            log.warn("访客不在线，消息仅入库 sessionId={}", sessionId);
        }
    }

    private void send(WebSocketSession ws, String json) {
        if (ws == null || !ws.isOpen()) {
            return;
        }
        try {
            ws.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("推送消息失败", e);
        }
    }

    private String toJson(Map<String, String> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("序列化消息失败", e);
            return "{}";
        }
    }
}
