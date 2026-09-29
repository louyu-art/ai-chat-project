package com.aichat.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String LLM_QUEUE = "llm_task_queue";
    public static final String LLM_EXCHANGE = "llm_task_exchange";
    public static final String LLM_ROUTING_KEY = "llm.task";

    @Bean
    public Queue llmQueue() {
        return QueueBuilder.durable(LLM_QUEUE).build();
    }
    @Bean
    public DirectExchange llmExchange() {
        return ExchangeBuilder.directExchange(LLM_EXCHANGE).durable(true).build();
    }
    @Bean
    public Binding llmBinding(Queue llmQueue, DirectExchange llmExchange) {
        return BindingBuilder.bind(llmQueue).to(llmExchange).with(LLM_ROUTING_KEY);
    }

    // json序列化：必须声明为MessageConverter Bean，
    // 自动配置的RabbitTemplate（生产端）和@RabbitListener容器工厂（消费端）才会都使用它
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(){
        return new Jackson2JsonMessageConverter();
    }
}
