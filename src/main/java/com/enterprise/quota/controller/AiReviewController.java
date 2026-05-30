package com.enterprise.quota.controller;

import com.enterprise.quota.entity.EnterpriseQuota;
import com.enterprise.quota.entity.ProjectItem;
import com.enterprise.quota.repository.EnterpriseQuotaRepository;
import com.enterprise.quota.repository.ProjectItemRepository;
import com.enterprise.quota.service.AiReviewService;
import com.enterprise.quota.service.TokenUsageTracker;
import com.enterprise.quota.util.KeywordExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 复核 API 控制器
 */
@RestController
@RequestMapping("/api/ai")
public class AiReviewController {

    @Autowired
    private AiReviewService aiReviewService;

    @Autowired
    private ProjectItemRepository itemRepository;

    @Autowired
    private EnterpriseQuotaRepository quotaRepository;

    @Autowired
    private TokenUsageTracker tokenTracker;

    /**
     * 手动触发 AI 复核
     */
    @PostMapping("/review")
    public ResponseEntity<Map<String, Object>> startAiReview(
            @RequestParam(value = "versionId", required = false) Long versionId,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = (Long) session.getAttribute("userId");
            if (userId == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
            }

            if (!aiReviewService.isAvailable()) {
                result.put("success", false);
                result.put("message", "AI 复核未启用或 API Key 未配置");
                return ResponseEntity.ok(result);
            }

            // 加载当前用户的清单项
            List<ProjectItem> allItems = itemRepository.findByUserIdOrderBySortOrderAsc(userId);

            // 只复核模糊区间的项目（matchStatus=1）
            List<EnterpriseQuota> allQuotas;
            if (versionId != null) {
                allQuotas = quotaRepository.findByVersionId(versionId);
            } else {
                allQuotas = quotaRepository.findAll();
            }

            // 预缓存定额关键词
            Map<Long, List<String>> quotaKeywordsCache = new ConcurrentHashMap<>();
            Map<Long, String> quotaNameCache = new ConcurrentHashMap<>();
            Map<Long, String> quotaFeatureCache = new ConcurrentHashMap<>();
            for (EnterpriseQuota quota : allQuotas) {
                List<String> keywords = new ArrayList<>();
                if (quota.getQuotaName() != null && !quota.getQuotaName().trim().isEmpty()) {
                    keywords.addAll(KeywordExtractor.extractKeywords(quota.getQuotaName()));
                    quotaNameCache.put(quota.getId(), quota.getQuotaName());
                }
                if (quota.getFeatureValue() != null && !quota.getFeatureValue().trim().isEmpty()) {
                    keywords.addAll(KeywordExtractor.extractKeywords(quota.getFeatureValue()));
                    quotaFeatureCache.put(quota.getId(), quota.getFeatureValue());
                }
                quotaKeywordsCache.put(quota.getId(), keywords);
            }

            // 收集模糊区间项目
            List<ProjectItem> fuzzyItems = new ArrayList<>();
            for (ProjectItem item : allItems) {
                if (item.getMatchStatus() == null || item.getMatchStatus() != 1) continue;
                if (item.getMatchedQuotaId() == null) continue;

                List<String> itemKeywords = new ArrayList<>();
                if (item.getItemName() != null) itemKeywords.addAll(KeywordExtractor.extractKeywords(item.getItemName()));
                if (item.getFeatureValue() != null) itemKeywords.addAll(KeywordExtractor.extractKeywords(item.getFeatureValue()));

                List<String> quotaKeywords = quotaKeywordsCache.get(item.getMatchedQuotaId());
                if (quotaKeywords == null) continue;

                double score = KeywordExtractor.calculateSimilarity(itemKeywords, quotaKeywords);
                if (aiReviewService.isInFuzzyRange(score)) {
                    fuzzyItems.add(item);
                }
            }

            if (fuzzyItems.isEmpty()) {
                result.put("success", true);
                result.put("message", "没有需要 AI 复核的模糊区间项目");
                result.put("reviewedCount", 0);
                return ResponseEntity.ok(result);
            }

            // 同步执行复核
            int changedCount = aiReviewService.reviewItems(fuzzyItems, allQuotas,
                    quotaKeywordsCache, quotaNameCache, quotaFeatureCache, userId);

            result.put("success", true);
            result.put("message", "AI 复核完成，共复核 " + fuzzyItems.size() + " 条，建议修改 " + changedCount + " 条");
            result.put("reviewedCount", fuzzyItems.size());
            result.put("changedCount", changedCount);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "AI 复核失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 获取 AI 状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> aiStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", aiReviewService.isAvailable());
        result.put("tokenUsage", tokenTracker.getStats());
        return ResponseEntity.ok(result);
    }

    /**
     * 接受 AI 建议
     */
    @PostMapping("/accept/{itemId}")
    public ResponseEntity<Map<String, Object>> acceptSuggestion(
            @PathVariable Long itemId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = (Long) session.getAttribute("userId");
            if (userId == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
            }

            aiReviewService.acceptAiSuggestion(itemId);
            result.put("success", true);
            result.put("message", "已接受 AI 建议");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "接受失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 拒绝 AI 建议
     */
    @PostMapping("/reject/{itemId}")
    public ResponseEntity<Map<String, Object>> rejectSuggestion(
            @PathVariable Long itemId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = (Long) session.getAttribute("userId");
            if (userId == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
            }

            aiReviewService.rejectAiSuggestion(itemId);
            result.put("success", true);
            result.put("message", "已拒绝 AI 建议");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "拒绝失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
}
