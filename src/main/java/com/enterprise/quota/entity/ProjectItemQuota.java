package com.enterprise.quota.entity;

import javax.persistence.*;
import java.math.BigDecimal;

/**
 * 清单项与定额的多对多关系实体
 * 用于支持一条清单对应多个定额的功能
 */
@Entity
@Table(name = "project_item_quota")
public class ProjectItemQuota {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "project_item_id", nullable = false)
    private Long projectItemId;
    
    @Column(name = "quota_id", nullable = false)
    private Long quotaId;
    
    @Column(name = "quota_code", length = 100)
    private String quotaCode;
    
    @Column(name = "quota_name", length = 500)
    private String quotaName;
    
    @Column(name = "quota_feature_value", length = 1000)
    private String quotaFeatureValue;
    
    @Column(name = "unit_price", precision = 18, scale = 2)
    private BigDecimal unitPrice;
    
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getProjectItemId() {
        return projectItemId;
    }
    
    public void setProjectItemId(Long projectItemId) {
        this.projectItemId = projectItemId;
    }
    
    public Long getQuotaId() {
        return quotaId;
    }
    
    public void setQuotaId(Long quotaId) {
        this.quotaId = quotaId;
    }
    
    public String getQuotaCode() {
        return quotaCode;
    }
    
    public void setQuotaCode(String quotaCode) {
        this.quotaCode = quotaCode;
    }
    
    public String getQuotaName() {
        return quotaName;
    }
    
    public void setQuotaName(String quotaName) {
        this.quotaName = quotaName;
    }
    
    public String getQuotaFeatureValue() {
        return quotaFeatureValue;
    }
    
    public void setQuotaFeatureValue(String quotaFeatureValue) {
        this.quotaFeatureValue = quotaFeatureValue;
    }
    
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
    
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }
    
    public Integer getSortOrder() {
        return sortOrder;
    }
    
    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}

