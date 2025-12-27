package com.enterprise.quota.entity;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "project_item")
public class ProjectItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id")
    private Long userId;
    
    @Column(name = "item_code", length = 100)
    private String itemCode;
    
    @Column(name = "item_name", columnDefinition = "LONGTEXT")
    private String itemName;
    
    @Column(name = "feature_value", columnDefinition = "LONGTEXT")
    private String featureValue;
    
    @Column(name = "unit", length = 50)
    private String unit;
    
    @Column(name = "quantity", precision = 18, scale = 2)
    private BigDecimal quantity;
    
    @Column(name = "matched_quota_id")
    private Long matchedQuotaId;
    
    @Column(name = "matched_quota_code", length = 100)
    private String matchedQuotaCode;
    
    @Column(name = "matched_quota_name", columnDefinition = "LONGTEXT")
    private String matchedQuotaName;
    
    @Column(name = "matched_quota_feature_value", columnDefinition = "LONGTEXT")
    private String matchedQuotaFeatureValue;
    
    @Column(name = "matched_unit_price", precision = 18, scale = 2)
    private BigDecimal matchedUnitPrice;
    
    @Column(name = "total_price", precision = 18, scale = 2)
    private BigDecimal totalPrice;
    
    @Column(name = "match_status")
    private Integer matchStatus = 0;
    
    @Column(name = "remark", columnDefinition = "LONGTEXT")
    private String remark;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getFeatureValue() { return featureValue; }
    public void setFeatureValue(String featureValue) { this.featureValue = featureValue; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public Long getMatchedQuotaId() { return matchedQuotaId; }
    public void setMatchedQuotaId(Long matchedQuotaId) { this.matchedQuotaId = matchedQuotaId; }
    public String getMatchedQuotaCode() { return matchedQuotaCode; }
    public void setMatchedQuotaCode(String matchedQuotaCode) { this.matchedQuotaCode = matchedQuotaCode; }
    public String getMatchedQuotaName() { return matchedQuotaName; }
    public void setMatchedQuotaName(String matchedQuotaName) { this.matchedQuotaName = matchedQuotaName; }
    public String getMatchedQuotaFeatureValue() { return matchedQuotaFeatureValue; }
    public void setMatchedQuotaFeatureValue(String matchedQuotaFeatureValue) { this.matchedQuotaFeatureValue = matchedQuotaFeatureValue; }
    public BigDecimal getMatchedUnitPrice() { return matchedUnitPrice; }
    public void setMatchedUnitPrice(BigDecimal matchedUnitPrice) { this.matchedUnitPrice = matchedUnitPrice; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
    public Integer getMatchStatus() { return matchStatus; }
    public void setMatchStatus(Integer matchStatus) { this.matchStatus = matchStatus; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}

