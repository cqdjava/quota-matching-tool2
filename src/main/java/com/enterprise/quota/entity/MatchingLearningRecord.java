package com.enterprise.quota.entity;

import javax.persistence.*;
import java.util.Date;

/**
 * 匹配学习记录实体类
 * 用于记录每次匹配的结果，供学习分析使用
 */
@Entity
@Table(name = "matching_learning_record")
public class MatchingLearningRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "item_name", columnDefinition = "TEXT")
    private String itemName;
    
    @Column(name = "item_feature_value", columnDefinition = "LONGTEXT")
    private String itemFeatureValue;
    
    @Column(name = "quota_name", columnDefinition = "TEXT")
    private String quotaName;
    
    @Column(name = "quota_feature_value", columnDefinition = "LONGTEXT")
    private String quotaFeatureValue;
    
    @Column(name = "match_score")
    private Double matchScore;
    
    @Column(name = "match_type")
    private Integer matchType; // 1=自动匹配，2=手动修改，3=多定额
    
    @Column(name = "item_keywords", columnDefinition = "TEXT")
    private String itemKeywords; // JSON格式存储关键词列表
    
    @Column(name = "quota_keywords", columnDefinition = "TEXT")
    private String quotaKeywords; // JSON格式存储关键词列表
    
    @Column(name = "common_keywords", columnDefinition = "TEXT")
    private String commonKeywords; // JSON格式存储共同关键词列表
    
    @Column(name = "learning_weight")
    private Double learningWeight = 1.0; // 学习权重，手动修改的权重更高
    
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
    
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    
    public String getItemFeatureValue() { return itemFeatureValue; }
    public void setItemFeatureValue(String itemFeatureValue) { this.itemFeatureValue = itemFeatureValue; }
    
    public String getQuotaName() { return quotaName; }
    public void setQuotaName(String quotaName) { this.quotaName = quotaName; }
    
    public String getQuotaFeatureValue() { return quotaFeatureValue; }
    public void setQuotaFeatureValue(String quotaFeatureValue) { this.quotaFeatureValue = quotaFeatureValue; }
    
    public Double getMatchScore() { return matchScore; }
    public void setMatchScore(Double matchScore) { this.matchScore = matchScore; }
    
    public Integer getMatchType() { return matchType; }
    public void setMatchType(Integer matchType) { this.matchType = matchType; }
    
    public String getItemKeywords() { return itemKeywords; }
    public void setItemKeywords(String itemKeywords) { this.itemKeywords = itemKeywords; }
    
    public String getQuotaKeywords() { return quotaKeywords; }
    public void setQuotaKeywords(String quotaKeywords) { this.quotaKeywords = quotaKeywords; }
    
    public String getCommonKeywords() { return commonKeywords; }
    public void setCommonKeywords(String commonKeywords) { this.commonKeywords = commonKeywords; }
    
    public Double getLearningWeight() { return learningWeight; }
    public void setLearningWeight(Double learningWeight) { this.learningWeight = learningWeight; }
    
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}

