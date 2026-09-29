package com.aichat.listener;

import com.aichat.dto.LlmTaskDTO;
import com.aichat.service.LlmService;
import com.aichat.service.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LlmTaskListener {
    @Autowired
    private LlmService llmService;
    @Autowired
    private WebSocketService webSocketService;

    @RabbitListener(queues = com.aichat.config.RabbitConfig.LLM_QUEUE)
    public void consume(LlmTaskDTO task){
        log.info("消费LLM任务 session:{}",task.getSessionId());
        String answer = llmService.callLlm(task.getSessionId(), task.getQuestion());
        // 把AI结果通过websocket推送到前端
        webSocketService.sendMsgToVisitor(task.getSessionId(),answer,"AI");
    }
}
