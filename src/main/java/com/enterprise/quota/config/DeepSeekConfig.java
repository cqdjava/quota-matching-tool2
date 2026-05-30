package com.enterprise.quota.config;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * DeepSeek API 配置类
 * 支持从 application.properties 或环境变量 DEEPSEEK_API_KEY 读取
 */
@Configuration
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekConfig {

    private String apiKey;
    private String baseUrl = "https://api.deepseek.com/v1/chat/completions";
    private String model = "deepseek-chat";
    private int maxTokens = 2048;
    private double temperature = 0.1;
    private int timeoutSeconds = 120;
    private int maxRetries = 3;
    private int candidatesPerItem = 5;
    private int itemsPerBatchPrompt = 10;
    private boolean enabled = true;
    private int rateLimitRpm = 60;

    @PostConstruct
    public void init() {
        // 兜底：如果 @ConfigurationProperties 没绑上，直接从环境变量读
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = System.getenv("DEEPSEEK_API_KEY");
        }
        System.out.println("DeepSeek 配置初始化: enabled=" + enabled + ", apiKey=" + maskApiKey(apiKey));
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() < 8) return "***EMPTY***";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    @Bean
    public OkHttpClient deepSeekHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(5, 1, TimeUnit.MINUTES))
                .build();
    }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public int getCandidatesPerItem() { return candidatesPerItem; }
    public void setCandidatesPerItem(int candidatesPerItem) { this.candidatesPerItem = candidatesPerItem; }
    public int getItemsPerBatchPrompt() { return itemsPerBatchPrompt; }
    public void setItemsPerBatchPrompt(int itemsPerBatchPrompt) { this.itemsPerBatchPrompt = itemsPerBatchPrompt; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getRateLimitRpm() { return rateLimitRpm; }
    public void setRateLimitRpm(int rateLimitRpm) { this.rateLimitRpm = rateLimitRpm; }

    public boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }
}
