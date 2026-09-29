CREATE DATABASE IF NOT EXISTS ai_chat_db DEFAULT CHARACTER SET utf8mb4;
USE ai_chat_db;

-- 坐席用户表
CREATE TABLE sys_user (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                          username VARCHAR(50) NOT NULL UNIQUE,
                          password VARCHAR(100) NOT NULL,
                          role VARCHAR(20) NOT NULL COMMENT 'agent/admin',
                          status TINYINT DEFAULT 0 COMMENT '0离线1在线',
                          create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 会话表
CREATE TABLE chat_session (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                              session_id VARCHAR(64) NOT NULL UNIQUE,
                              visitor_id VARCHAR(64),
                              agent_id BIGINT NULL,
                              session_status VARCHAR(20) NOT NULL COMMENT 'AI/ARTIFICIAL/END',
                              create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                              end_time DATETIME NULL
);

-- 消息表
CREATE TABLE chat_message (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                              session_id VARCHAR(64) NOT NULL,
                              sender_type VARCHAR(20) NOT NULL COMMENT 'VISITOR / AI / AGENT',
                              content TEXT NOT NULL,
                              create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- LLM调用日志
CREATE TABLE llm_call_log (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                              session_id VARCHAR(64),
                              prompt TEXT,
                              token_count INT,
                              call_status VARCHAR(20),
                              create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 插入测试坐席账号 admin / 123456
INSERT INTO sys_user(username,password,role) VALUES('admin','123456','admin');

-- 登录功能测试账号（库中已存在同名账号时忽略报错即可）
INSERT INTO sys_user(username,password,role) VALUES('user1','123456','USER');
INSERT INTO sys_user(username,password,role) VALUES('agent1','123456','AGENT');
