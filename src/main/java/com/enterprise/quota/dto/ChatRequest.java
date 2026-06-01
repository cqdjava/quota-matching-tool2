package com.enterprise.quota.dto;

/**
 * AI 助手聊天请求
 */
public class ChatRequest {
    private String question;

    public ChatRequest() {}

    public ChatRequest(String question) {
        this.question = question;
    }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
}
