package com.aichat.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class LlmTaskDTO implements Serializable {
    private String sessionId;
    private String question;
}
