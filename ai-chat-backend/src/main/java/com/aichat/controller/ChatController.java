package com.aichat.controller;

import com.aichat.common.AuthInterceptor;
import com.aichat.common.Result;
import com.aichat.entity.ChatMessage;
import com.aichat.entity.ChatSession;
import com.aichat.entity.SysUser;
import com.aichat.mapper.ChatMessageMapper;
import com.aichat.mapper.ChatSessionMapper;
import com.aichat.service.AuthService;
import com.aichat.service.WebSocketService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/chat")
public class ChatController {
    @Autowired
    private ChatSessionMapper chatSessionMapper;
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    @Autowired
    private AuthService authService;
    @Autowired
    private WebSocketService webSocketService;

    // 统一登录入口（用户/客服/管理员）
    @PostMapping("/login")
    public Result<Map<String, Object>> login(String username, String password) {
        String token = authService.login(username, password);
        if (token == null) {
            return Result.fail("用户名或密码错误");
        }
        SysUser user = authService.resolve(token);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("role", user.getRole());
        return Result.success(data);
    }

    // 退出登录
    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            authService.logout(header.substring(7));
        }
        return Result.success("已退出");
    }

    // 创建访客会话，拿到sessionId，前端ws连接使用
    @GetMapping("/createSession")
    public Result<String> createSession(HttpServletRequest request) {
        SysUser user = currentUser(request);
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        // 会话绑定登录用户名，便于客服端展示归属
        session.setVisitorId(user.getUsername());
        session.setSessionStatus("AI");
        chatSessionMapper.insert(session);
        return Result.success(sessionId);
    }

    // 拉取会话历史消息（用户只能看自己的会话，客服可看全部）
    @GetMapping("/messages")
    public Result<List<ChatMessage>> messages(String sessionId, HttpServletRequest request) {
        SysUser user = currentUser(request);
        ChatSession session = findBySessionId(sessionId);
        if (session == null) {
            return Result.fail("会话不存在");
        }
        if (!authService.isAgent(user) && !user.getUsername().equals(session.getVisitorId())) {
            return Result.fail("无权查看该会话");
        }
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId).orderByAsc(ChatMessage::getCreateTime);
        return Result.success(chatMessageMapper.selectList(wrapper));
    }

    // 转人工接口
    @PostMapping("/transferAgent")
    public Result<String> transferAgent(String sessionId) {
        ChatSession session = findBySessionId(sessionId);
        if (session == null) {
            return Result.fail("会话不存在");
        }
        session.setSessionStatus("ARTIFICIAL");
        chatSessionMapper.updateById(session);
        // 通知在线客服有新会话待认领
        webSocketService.broadcastToAgents("TRANSFER", sessionId);
        return Result.success("已成功转人工");
    }

    // 客服会话列表：待认领 + 我接待的
    @GetMapping("/agent/sessions")
    public Result<Map<String, Object>> agentSessions(HttpServletRequest request) {
        SysUser user = currentUser(request);
        LambdaQueryWrapper<ChatSession> pendingWrapper = new LambdaQueryWrapper<>();
        pendingWrapper.eq(ChatSession::getSessionStatus, "ARTIFICIAL")
                .isNull(ChatSession::getAgentId)
                .orderByAsc(ChatSession::getCreateTime);
        LambdaQueryWrapper<ChatSession> mineWrapper = new LambdaQueryWrapper<>();
        mineWrapper.eq(ChatSession::getSessionStatus, "ARTIFICIAL")
                .eq(ChatSession::getAgentId, user.getId())
                .orderByAsc(ChatSession::getCreateTime);

        Map<String, Object> data = new HashMap<>();
        data.put("pending", toSessionViews(chatSessionMapper.selectList(pendingWrapper)));
        data.put("mine", toSessionViews(chatSessionMapper.selectList(mineWrapper)));
        return Result.success(data);
    }

    // 认领会话：where条件限制未认领状态，数据库原子更新防多客服抢单
    @PostMapping("/agent/claim")
    public Result<String> claim(String sessionId, HttpServletRequest request) {
        SysUser user = currentUser(request);
        ChatSession update = new ChatSession();
        update.setAgentId(user.getId());
        int rows = chatSessionMapper.update(update, new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getSessionStatus, "ARTIFICIAL")
                .isNull(ChatSession::getAgentId));
        if (rows == 0) {
            return Result.fail("认领失败，会话不存在或已被其他客服认领");
        }
        webSocketService.broadcastToAgents("CLAIM", sessionId);
        return Result.success("认领成功");
    }

    // 释放会话回待认领池（只能释放自己接待的）
    @PostMapping("/agent/release")
    public Result<String> release(String sessionId, HttpServletRequest request) {
        SysUser user = currentUser(request);
        // entity传null时字段不更新，置空agentId必须用wrapper的set
        int rows = chatSessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getAgentId, user.getId())
                .set(ChatSession::getAgentId, null));
        if (rows == 0) {
            return Result.fail("释放失败，会话不存在或不属于你");
        }
        webSocketService.broadcastToAgents("RELEASE", sessionId);
        return Result.success("已释放");
    }

    private SysUser currentUser(HttpServletRequest request) {
        return (SysUser) request.getAttribute(AuthInterceptor.LOGIN_USER_ATTR);
    }

    private ChatSession findBySessionId(String sessionId) {
        // sessionId是业务列不是主键，不能用selectById
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getSessionId, sessionId);
        return chatSessionMapper.selectOne(wrapper);
    }

    // 会话列表附带最后一条消息预览
    private List<Map<String, Object>> toSessionViews(List<ChatSession> sessions) {
        return sessions.stream().map(s -> {
            Map<String, Object> view = new HashMap<>();
            view.put("sessionId", s.getSessionId());
            view.put("visitorId", s.getVisitorId());
            view.put("createTime", s.getCreateTime());
            LambdaQueryWrapper<ChatMessage> msgWrapper = new LambdaQueryWrapper<>();
            msgWrapper.eq(ChatMessage::getSessionId, s.getSessionId())
                    .orderByDesc(ChatMessage::getCreateTime)
                    .last("LIMIT 1");
            ChatMessage last = chatMessageMapper.selectOne(msgWrapper);
            view.put("lastMessage", last == null ? "" : last.getContent());
            return view;
        }).collect(Collectors.toList());
    }
}
