package com.enterprise.quota.service;

import com.enterprise.quota.entity.AiReviewLog;
import com.enterprise.quota.repository.AiReviewLogRepository;
import com.enterprise.quota.util.KeywordExtractor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Few-Shot 示例检索器
 * 从历史成功案例中检索与当前清单项最相似的前 3 个案例，
 * 注入到 AI Prompt 中作为参考示例
 */
@Component
public class FewShotRetriever {

    @Autowired
    private AiReviewLogRepository reviewLogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 缓存已确认的案例（启动时加载，每小时刷新）*/
    private volatile List<CachedCase> caseCache = new ArrayList<>();
    private volatile long lastRefreshTime = 0;
    private static final long REFRESH_INTERVAL_MS = 3600_000L; // 1 小时

    /**
     * 检索与当前清单项最相似的历史案例
     * @param itemName 清单项名称
     * @param itemFeature 清单项特征值
     * @return Top-3 相似案例
     */
    public List<Map<String, String>> retrieve(String itemName, String itemFeature) {
        refreshCacheIfNeeded();

        if (caseCache.isEmpty()) {
            return Collections.emptyList();
        }

        // 提取当前清单项的关键词
        List<String> itemKeywords = new ArrayList<>();
        if (itemName != null && !itemName.trim().isEmpty()) {
            itemKeywords.addAll(KeywordExtractor.extractKeywords(itemName));
        }
        if (itemFeature != null && !itemFeature.trim().isEmpty()) {
            itemKeywords.addAll(KeywordExtractor.extractKeywords(itemFeature));
        }

        if (itemKeywords.isEmpty()) {
            return Collections.emptyList();
        }

        // 计算 Jaccard 相似度，排序取 Top-3
        Set<String> itemKeywordSet = new HashSet<>(itemKeywords);

        return caseCache.stream()
                .map(c -> {
                    double similarity = calculateJaccardSimilarity(itemKeywordSet, c.keywords);
                    return new AbstractMap.SimpleEntry<>(c, similarity);
                })
                .filter(e -> e.getValue() > 0.3)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(3)
                .map(e -> {
                    Map<String, String> result = new HashMap<>();
                    CachedCase c = e.getKey();
                    result.put("itemName", c.itemName);
                    result.put("itemFeature", c.itemFeatureValue != null ? c.itemFeatureValue : "");
                    result.put("quotaName", c.quotaName);
                    result.put("accepted", c.isAccepted ? "✓用户采纳" : "");
                    return result;
                })
                .collect(Collectors.toList());
    }

    /** 获取缓存案例总数 */
    public int getCachedCaseCount() {
        refreshCacheIfNeeded();
        return caseCache.size();
    }

    private void refreshCacheIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshTime > REFRESH_INTERVAL_MS) {
            synchronized (this) {
                if (now - lastRefreshTime > REFRESH_INTERVAL_MS) {
                    loadCache();
                    lastRefreshTime = now;
                }
            }
        }
    }

    private void loadCache() {
        try {
            List<AiReviewLog> confirmedCases = reviewLogRepository.findByIsAcceptedTrueOrderByCreateTimeDesc();
            // 只保留最近 500 条
            List<AiReviewLog> recentCases = confirmedCases.stream()
                    .limit(500)
                    .collect(Collectors.toList());

            caseCache = recentCases.stream()
                    .map(c -> {
                        Set<String> keywords = new HashSet<>();
                        if (c.getItemKeywords() != null) {
                            try {
                                List<String> kw = objectMapper.readValue(c.getItemKeywords(),
                                        new TypeReference<List<String>>() {});
                                keywords.addAll(kw);
                            } catch (Exception ignored) {}
                        }
                        // 额外从快照字段提取
                        if (c.getItemName() != null) {
                            keywords.addAll(KeywordExtractor.extractKeywords(c.getItemName()));
                        }
                        if (c.getItemFeatureValue() != null) {
                            keywords.addAll(KeywordExtractor.extractKeywords(c.getItemFeatureValue()));
                        }
                        return new CachedCase(
                                c.getItemName(),
                                c.getItemFeatureValue(),
                                c.getAiSuggestQuotaName() != null ? c.getAiSuggestQuotaName() : c.getOriginalQuotaName(),
                                keywords,
                                c.getIsAccepted() != null && c.getIsAccepted()
                        );
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("加载 Few-Shot 案例缓存失败: " + e.getMessage());
        }
    }

    private double calculateJaccardSimilarity(Set<String> keywords1, Set<String> keywords2) {
        if (keywords1.isEmpty() || keywords2.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(keywords1);
        intersection.retainAll(keywords2);
        Set<String> union = new HashSet<>(keywords1);
        union.addAll(keywords2);
        return (double) intersection.size() / union.size();
    }

    private static class CachedCase {
        final String itemName;
        final String itemFeatureValue;
        final String quotaName;
        final Set<String> keywords;
        final boolean isAccepted;

        CachedCase(String itemName, String itemFeatureValue, String quotaName,
                   Set<String> keywords, boolean isAccepted) {
            this.itemName = itemName;
            this.itemFeatureValue = itemFeatureValue;
            this.quotaName = quotaName;
            this.keywords = keywords;
            this.isAccepted = isAccepted;
        }
    }
}
