package com.enterprise.quota.entity;

import javax.persistence.*;
import java.math.BigDecimal;

/**
 * 企业定额实体类
 */
@Entity
@Table(name = "enterprise_quota")
public class EnterpriseQuota {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "quota_code", length = 100)
    private String quotaCode;
    
    @Column(name = "quota_name", length = 500)
    private String quotaName;
    
    @Column(name = "feature_value", length = 1000)
    private String featureValue;
    
    @Column(name = "unit", length = 50)
    private String unit;
    
    @Column(name = "unit_price", precision = 18, scale = 2)
    private BigDecimal unitPrice;
    
    @Column(name = "labor_cost", precision = 18, scale = 2)
    private BigDecimal laborCost;
    
    @Column(name = "material_cost", precision = 18, scale = 2)
    private BigDecimal materialCost;
    
    @Column(name = "machine_cost", precision = 18, scale = 2)
    private BigDecimal machineCost;
    
    @Column(name = "remark", length = 1000)
    private String remark;
    
    @Column(name = "version_id")
    private Long versionId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQuotaCode() { return quotaCode; }
    public void setQuotaCode(String quotaCode) { this.quotaCode = quotaCode; }
    public String getQuotaName() { return quotaName; }
    public void setQuotaName(String quotaName) { this.quotaName = quotaName; }
    public String getFeatureValue() { return featureValue; }
    public void setFeatureValue(String featureValue) { this.featureValue = featureValue; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getLaborCost() { return laborCost; }
    public void setLaborCost(BigDecimal laborCost) { this.laborCost = laborCost; }
    public BigDecimal getMaterialCost() { return materialCost; }
    public void setMaterialCost(BigDecimal materialCost) { this.materialCost = materialCost; }
    public BigDecimal getMachineCost() { return machineCost; }
    public void setMachineCost(BigDecimal machineCost) { this.machineCost = machineCost; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
}

