package com.enterprise.quota.entity;

import javax.persistence.*;
import java.util.Date;

/**
 * 关键词权重实体类
 * 用于存储学习到的关键词权重信息
 */
@Entity
@Table(name = "keyword_weight")
public class KeywordWeight {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "keyword", length = 200, unique = true)
    private String keyword;
    
    @Column(name = "weight")
    private Double weight = 1.0; // 权重值，默认1.0
    
    @Column(name = "match_count")
    private Integer matchCount = 0; // 匹配成功次数
    
    @Column(name = "total_count")
    private Integer totalCount = 0; // 出现总次数
    
    @Column(name = "success_rate")
    private Double successRate = 0.0; // 成功率
    
    @Column(name = "is_core_concept")
    private Boolean isCoreConcept = false; // 是否为核心概念词
    
    @Column(name = "update_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateTime;
    
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updateTime = new Date();
        // 自动计算成功率
        if (totalCount > 0) {
            successRate = (double) matchCount / totalCount;
        }
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    
    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }
    
    public Integer getMatchCount() { return matchCount; }
    public void setMatchCount(Integer matchCount) { this.matchCount = matchCount; }
    
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    
    public Double getSuccessRate() { return successRate; }
    public void setSuccessRate(Double successRate) { this.successRate = successRate; }
    
    public Boolean getIsCoreConcept() { return isCoreConcept; }
    public void setIsCoreConcept(Boolean isCoreConcept) { this.isCoreConcept = isCoreConcept; }
    
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}

