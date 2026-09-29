package com.aichat.service;

import com.aichat.config.RabbitConfig;
import com.aichat.dto.LlmTaskDTO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmService {

    @Value("${llm.ollama.url}")
    private String ollamaUrl;

    @Value("${llm.ollama.model-name}")
    private String modelName;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private final RestTemplate restTemplate = new RestTemplate();

    // 投递LLM任务到MQ，由消费端异步调用模型
    public void sendLlmTask(String sessionId, String question){
        LlmTaskDTO dto = new LlmTaskDTO();
        dto.setSessionId(sessionId);
        dto.setQuestion(question);
        rabbitTemplate.convertAndSend(RabbitConfig.LLM_EXCHANGE, RabbitConfig.LLM_ROUTING_KEY, dto);
    }

    // 同步请求本地Ollama，返回完整回答
    public String callLlm(String sessionId, String question){
        // 构造Ollama请求体
        Map<String,Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        // 对话消息
        List<Map<String,String>> messages = new ArrayList<>();
        Map<String,String> userMsg = new HashMap<>();
        userMsg.put("role","user");
        userMsg.put("content", question);
        messages.add(userMsg);
        requestBody.put("messages", messages);
        // 关闭流式输出，一次性返回完整结果
        requestBody.put("stream", false);

        // 请求本地Qwen
        Map<String,Object> resp = restTemplate.postForObject(ollamaUrl, requestBody, Map.class);
        Map<String,Object> message = (Map<String, Object>) ((Map<String,Object>)resp.get("message"));
        return (String) message.get("content");
    }
}
