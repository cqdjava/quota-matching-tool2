package com.enterprise.quota.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 助手聊天响应
 */
public class ChatResponse {
    private String answer;
    private List<SourceInfo> sources = new ArrayList<>();
    private List<QuotaRef> quotaReferences = new ArrayList<>();

    public ChatResponse() {}

    public ChatResponse(String answer) {
        this.answer = answer;
    }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public List<SourceInfo> getSources() { return sources; }
    public void setSources(List<SourceInfo> sources) { this.sources = sources; }

    public List<QuotaRef> getQuotaReferences() { return quotaReferences; }
    public void setQuotaReferences(List<QuotaRef> quotaReferences) { this.quotaReferences = quotaReferences; }

    /**
     * 来源信息
     */
    public static class SourceInfo {
        private String type;  // "quota" 或 "knowledge"
        private String label;

        public SourceInfo() {}

        public SourceInfo(String type, String label) {
            this.type = type;
            this.label = label;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }

    /**
     * 定额引用
     */
    public static class QuotaRef {
        private String quotaCode;
        private String quotaName;
        private String versionName;

        public QuotaRef() {}

        public QuotaRef(String quotaCode, String quotaName, String versionName) {
            this.quotaCode = quotaCode;
            this.quotaName = quotaName;
            this.versionName = versionName;
        }

        public String getQuotaCode() { return quotaCode; }
        public void setQuotaCode(String quotaCode) { this.quotaCode = quotaCode; }

        public String getQuotaName() { return quotaName; }
        public void setQuotaName(String quotaName) { this.quotaName = quotaName; }

        public String getVersionName() { return versionName; }
        public void setVersionName(String versionName) { this.versionName = versionName; }
    }
}
