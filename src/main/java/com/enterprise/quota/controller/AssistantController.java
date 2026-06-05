package com.enterprise.quota.controller;

import com.enterprise.quota.service.AssistantService;
import com.enterprise.quota.service.TokenUsageTracker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 助手 API 控制器
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    @Autowired
    private AssistantService assistantService;

    @Autowired
    private TokenUsageTracker tokenTracker;

    /**
     * 发送聊天消息
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> request, HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        try {
            Long userId = (Long) session.getAttribute("userId");
            if (userId == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
            }

            String question = request.get("question");
            if (question == null || question.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "问题不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            Map<String, Object> chatResult = assistantService.chat(question.trim(), userId);
            return ResponseEntity.ok(chatResult);

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "AI 助手服务暂时不可用，请稍后重试");
            result.put("answer", "抱歉，处理您的问题时出错了，请稍后重试。");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 获取助手状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> result = new HashMap<>();
        result.put("available", assistantService.isAvailable());
        result.put("tokenUsage", tokenTracker.getStats());
        return ResponseEntity.ok(result);
    }
}
