package com.aichat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("llm_call_log")
public class LlmCallLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private String prompt;
    private Integer tokenCount;
    private String callStatus;
    private LocalDateTime createTime;
}
