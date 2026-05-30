package com.enterprise.quota.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * AI 复核日志实体
 * 记录每次 AI 复核的详细信息，支撑 Few-Shot 学习机制
 */
@Entity
@Table(name = "ai_review_log")
public class AiReviewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "item_name", columnDefinition = "LONGTEXT")
    private String itemName;

    @Column(name = "item_feature_value", columnDefinition = "LONGTEXT")
    private String itemFeatureValue;

    @Column(name = "original_quota_id")
    private Long originalQuotaId;

    @Column(name = "original_quota_name")
    private String originalQuotaName;

    @Column(name = "ai_suggest_quota_id")
    private Long aiSuggestQuotaId;

    @Column(name = "ai_suggest_quota_name")
    private String aiSuggestQuotaName;

    @Column(name = "original_score")
    private Double originalScore;

    @Column(name = "ai_confidence")
    private Double aiConfidence;

    @Column(name = "ai_reasoning", columnDefinition = "LONGTEXT")
    private String aiReasoning;

    @Column(name = "item_keywords", columnDefinition = "LONGTEXT")
    private String itemKeywords;

    @Column(name = "is_accepted")
    private Boolean isAccepted;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        this.createTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getItemFeatureValue() { return itemFeatureValue; }
    public void setItemFeatureValue(String itemFeatureValue) { this.itemFeatureValue = itemFeatureValue; }
    public Long getOriginalQuotaId() { return originalQuotaId; }
    public void setOriginalQuotaId(Long originalQuotaId) { this.originalQuotaId = originalQuotaId; }
    public String getOriginalQuotaName() { return originalQuotaName; }
    public void setOriginalQuotaName(String originalQuotaName) { this.originalQuotaName = originalQuotaName; }
    public Long getAiSuggestQuotaId() { return aiSuggestQuotaId; }
    public void setAiSuggestQuotaId(Long aiSuggestQuotaId) { this.aiSuggestQuotaId = aiSuggestQuotaId; }
    public String getAiSuggestQuotaName() { return aiSuggestQuotaName; }
    public void setAiSuggestQuotaName(String aiSuggestQuotaName) { this.aiSuggestQuotaName = aiSuggestQuotaName; }
    public Double getOriginalScore() { return originalScore; }
    public void setOriginalScore(Double originalScore) { this.originalScore = originalScore; }
    public Double getAiConfidence() { return aiConfidence; }
    public void setAiConfidence(Double aiConfidence) { this.aiConfidence = aiConfidence; }
    public String getAiReasoning() { return aiReasoning; }
    public void setAiReasoning(String aiReasoning) { this.aiReasoning = aiReasoning; }
    public String getItemKeywords() { return itemKeywords; }
    public void setItemKeywords(String itemKeywords) { this.itemKeywords = itemKeywords; }
    public Boolean getIsAccepted() { return isAccepted; }
    public void setIsAccepted(Boolean isAccepted) { this.isAccepted = isAccepted; }
    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
