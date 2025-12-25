package com.enterprise.quota.entity;

import javax.persistence.*;
import java.util.Date;

/**
 * 匹配规则实体类
 * 用于存储学习到的匹配规则
 */
@Entity
@Table(name = "matching_rule")
public class MatchingRule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "rule_type", length = 50)
    private String ruleType; // synonym, pattern, weight
    
    @Column(name = "source_text", columnDefinition = "TEXT")
    private String sourceText; // 源文本
    
    @Column(name = "target_text", columnDefinition = "TEXT")
    private String targetText; // 目标文本
    
    @Column(name = "rule_value", columnDefinition = "TEXT")
    private String ruleValue; // 规则值（JSON格式）
    
    @Column(name = "confidence")
    private Double confidence = 0.0; // 置信度
    
    @Column(name = "usage_count")
    private Integer usageCount = 0; // 使用次数
    
    @Column(name = "create_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createTime;
    
    @Column(name = "update_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateTime;
    
    @PrePersist
    protected void onCreate() {
        createTime = new Date();
        updateTime = new Date();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updateTime = new Date();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
    
    public String getSourceText() { return sourceText; }
    public void setSourceText(String sourceText) { this.sourceText = sourceText; }
    
    public String getTargetText() { return targetText; }
    public void setTargetText(String targetText) { this.targetText = targetText; }
    
    public String getRuleValue() { return ruleValue; }
    public void setRuleValue(String ruleValue) { this.ruleValue = ruleValue; }
    
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    
    public Integer getUsageCount() { return usageCount; }
    public void setUsageCount(Integer usageCount) { this.usageCount = usageCount; }
    
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}

