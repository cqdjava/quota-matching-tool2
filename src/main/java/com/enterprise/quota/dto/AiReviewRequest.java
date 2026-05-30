package com.enterprise.quota.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 复核请求 DTO（单批）
 */
public class AiReviewRequest {

    private List<AiReviewItem> items;

    public List<AiReviewItem> getItems() { return items; }
    public void setItems(List<AiReviewItem> items) { this.items = items; }

    public static class AiReviewItem {
        private Long itemId;
        private String itemCode;
        private String itemName;
        private String featureValue;
        private String unit;
        private BigDecimal quantity;
        private Long originalQuotaId;
        private String originalQuotaCode;
        private String originalQuotaName;
        private Double originalScore;
        private List<CandidateQuota> candidates;

        public Long getItemId() { return itemId; }
        public void setItemId(Long itemId) { this.itemId = itemId; }
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
        public Long getOriginalQuotaId() { return originalQuotaId; }
        public void setOriginalQuotaId(Long originalQuotaId) { this.originalQuotaId = originalQuotaId; }
        public String getOriginalQuotaCode() { return originalQuotaCode; }
        public void setOriginalQuotaCode(String originalQuotaCode) { this.originalQuotaCode = originalQuotaCode; }
        public String getOriginalQuotaName() { return originalQuotaName; }
        public void setOriginalQuotaName(String originalQuotaName) { this.originalQuotaName = originalQuotaName; }
        public Double getOriginalScore() { return originalScore; }
        public void setOriginalScore(Double originalScore) { this.originalScore = originalScore; }
        public List<CandidateQuota> getCandidates() { return candidates; }
        public void setCandidates(List<CandidateQuota> candidates) { this.candidates = candidates; }
    }

    public static class CandidateQuota {
        private Long quotaId;
        private String quotaCode;
        private String quotaName;
        private String featureValue;
        private String unit;
        private BigDecimal unitPrice;

        public Long getQuotaId() { return quotaId; }
        public void setQuotaId(Long quotaId) { this.quotaId = quotaId; }
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
    }
}
