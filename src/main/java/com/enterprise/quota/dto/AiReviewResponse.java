package com.enterprise.quota.dto;

import java.util.List;

/**
 * AI 复核响应 DTO
 */
public class AiReviewResponse {

    private List<AiReviewResult> results;

    public List<AiReviewResult> getResults() { return results; }
    public void setResults(List<AiReviewResult> results) { this.results = results; }

    public static class AiReviewResult {
        private String itemCode;
        private Long matchedQuotaId;
        private Double confidence;
        private String reasoning;

        public String getItemCode() { return itemCode; }
        public void setItemCode(String itemCode) { this.itemCode = itemCode; }
        public Long getMatchedQuotaId() { return matchedQuotaId; }
        public void setMatchedQuotaId(Long matchedQuotaId) { this.matchedQuotaId = matchedQuotaId; }
        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }
        public String getReasoning() { return reasoning; }
        public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    }
}
