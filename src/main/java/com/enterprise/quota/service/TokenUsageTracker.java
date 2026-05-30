package com.enterprise.quota.service;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token 用量统计器
 * 记录 AI 复核的 API 调用次数和 Token 消耗
 */
@Component
public class TokenUsageTracker {

    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);
    private final AtomicInteger totalCalls = new AtomicInteger(0);
    private final AtomicInteger failedCalls = new AtomicInteger(0);

    public void recordUsage(int promptTokens, int completionTokens) {
        totalPromptTokens.addAndGet(promptTokens);
        totalCompletionTokens.addAndGet(completionTokens);
        totalCalls.incrementAndGet();
    }

    public void recordFailure() {
        failedCalls.incrementAndGet();
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCalls", totalCalls.get());
        stats.put("failedCalls", failedCalls.get());
        stats.put("totalPromptTokens", totalPromptTokens.get());
        stats.put("totalCompletionTokens", totalCompletionTokens.get());
        stats.put("totalTokens", totalPromptTokens.get() + totalCompletionTokens.get());
        return stats;
    }

    public void reset() {
        totalPromptTokens.set(0);
        totalCompletionTokens.set(0);
        totalCalls.set(0);
        failedCalls.set(0);
    }

    public long getTotalTokens() {
        return totalPromptTokens.get() + totalCompletionTokens.get();
    }

    public int getTotalCalls() {
        return totalCalls.get();
    }
}
