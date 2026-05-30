package com.enterprise.quota.service;

import com.enterprise.quota.config.DeepSeekConfig;
import com.enterprise.quota.dto.AiReviewRequest;
import com.enterprise.quota.dto.AiReviewResponse;
import com.enterprise.quota.entity.AiReviewLog;
import com.enterprise.quota.entity.EnterpriseQuota;
import com.enterprise.quota.entity.ProjectItem;
import com.enterprise.quota.repository.AiReviewLogRepository;
import com.enterprise.quota.repository.EnterpriseQuotaRepository;
import com.enterprise.quota.repository.ProjectItemRepository;
import com.enterprise.quota.util.KeywordExtractor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 复核服务
 * 核心流程：预筛选候选定额 → 检索 Few-Shot 示例 → 调用 DeepSeek API → 解析结果 → 应用差异
 */
@Service
public class AiReviewService {

    @Autowired
    private DeepSeekConfig config;

    @Autowired
    private OkHttpClient httpClient;

    @Autowired
    private EnterpriseQuotaRepository quotaRepository;

    @Autowired
    private ProjectItemRepository itemRepository;

    @Autowired
    private AiReviewLogRepository reviewLogRepository;

    @Autowired
    private FewShotRetriever fewShotRetriever;

    @Autowired
    private TokenUsageTracker tokenTracker;

    @Value("${ai.review.min-score:0.25}")
    private double minReviewScore;

    @Value("${ai.review.max-score:0.60}")
    private double maxReviewScore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[\\s\\S]*\\}");

    /** DeepSeek System Prompt */
    private static final String SYSTEM_PROMPT =
        "你是建筑工程项目预算工程师，精通企业定额套用。\n" +
        "任务：从候选定额中为每个清单项选择最匹配的企业定额。\n\n" +
        "匹配规则（优先级从高到低）：\n" +
        "1. 名称语义匹配：清单项名称与定额名称的语义相似度\n" +
        "2. 特征值一致：规格型号、技术参数等描述一致\n" +
        "3. 单位匹配：单位应相同或可转换\n" +
        "4. 工作内容整体判断\n\n" +
        "如果候选中没有明显匹配的，matchedQuotaId 设为 null。\n" +
        "严格按以下JSON格式返回（不要包含markdown标记）：\n" +
        "{\"results\":[{\"itemCode\":\"...\",\"matchedQuotaId\":ID或null,\"confidence\":0.0~1.0,\"reasoning\":\"理由(20字内)\"}]}";

    /**
     * 异步执行 AI 复核
     * @param items 需要复核的清单项（模糊区间内的）
     * @param allQuotas 所有定额
     * @param quotaKeywordsCache 定额关键词缓存
     * @param quotaNameCache 定额名称缓存
     * @param quotaFeatureCache 定额特征值缓存
     * @param userId 当前用户ID
     */
    @Async("asyncTaskExecutor")
    public void reviewAsync(List<ProjectItem> items, List<EnterpriseQuota> allQuotas,
                            Map<Long, List<String>> quotaKeywordsCache,
                            Map<Long, String> quotaNameCache,
                            Map<Long, String> quotaFeatureCache,
                            Long userId) {
        try {
            reviewItems(items, allQuotas, quotaKeywordsCache, quotaNameCache, quotaFeatureCache, userId);
        } catch (Exception e) {
            System.err.println("AI 复核失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 同步执行 AI 复核
     */
    @Transactional
    public int reviewItems(List<ProjectItem> items, List<EnterpriseQuota> allQuotas,
                           Map<Long, List<String>> quotaKeywordsCache,
                           Map<Long, String> quotaNameCache,
                           Map<Long, String> quotaFeatureCache,
                           Long userId) {
        if (!config.isEnabled() || !config.isApiKeyConfigured()) {
            System.out.println("AI 复核未启用或 API Key 未配置");
            return 0;
        }

        if (items.isEmpty()) {
            return 0;
        }

        System.out.println("开始 AI 复核，共 " + items.size() + " 条清单项");

        // 1. 为每个 item 预筛选 Top-N 候选定额并构建请求
        List<AiReviewRequest.AiReviewItem> reviewItems = new ArrayList<>();
        for (ProjectItem item : items) {
            AiReviewRequest.AiReviewItem reviewItem = buildReviewItem(item, allQuotas,
                    quotaKeywordsCache, quotaNameCache, quotaFeatureCache);
            if (reviewItem != null) {
                reviewItems.add(reviewItem);
            }
        }

        if (reviewItems.isEmpty()) {
            return 0;
        }

        // 2. 按每批 itemsPerBatchPrompt 分组
        int batchSize = config.getItemsPerBatchPrompt();
        int totalReviewed = 0;

        for (int i = 0; i < reviewItems.size(); i += batchSize) {
            int end = Math.min(i + batchSize, reviewItems.size());
            List<AiReviewRequest.AiReviewItem> batch = reviewItems.subList(i, end);

            try {
                int reviewed = processBatch(batch, userId);
                totalReviewed += reviewed;

                // 速率控制：每批之间间隔 1 秒
                if (end < reviewItems.size()) {
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                System.err.println("AI 复核批次 " + (i / batchSize + 1) + " 失败: " + e.getMessage());
                tokenTracker.recordFailure();
            }
        }

        System.out.println("AI 复核完成，共复核 " + totalReviewed + " 条");
        return totalReviewed;
    }

    /**
     * 处理一批清单项
     */
    private int processBatch(List<AiReviewRequest.AiReviewItem> batch, Long userId) {
        // 1. 检索 Few-Shot 示例
        List<Map<String, String>> fewShots = Collections.emptyList();
        if (!batch.isEmpty()) {
            AiReviewRequest.AiReviewItem first = batch.get(0);
            fewShots = fewShotRetriever.retrieve(first.getItemName(), first.getFeatureValue());
        }

        // 2. 构建 Prompt
        String userPrompt = buildBatchPrompt(batch, fewShots);

        // 3. 调用 DeepSeek API
        String llmResponse = callDeepSeekApi(SYSTEM_PROMPT, userPrompt);
        if (llmResponse == null) {
            return 0;
        }

        // 4. 解析响应
        List<AiReviewResponse.AiReviewResult> results = parseResponse(llmResponse);
        if (results.isEmpty()) {
            return 0;
        }

        // 5. 应用结果
        return applyResults(batch, results, userId);
    }

    /**
     * 构建单个清单项的复核请求（包含预筛选候选定额）
     */
    private AiReviewRequest.AiReviewItem buildReviewItem(ProjectItem item, List<EnterpriseQuota> allQuotas,
                                                          Map<Long, List<String>> quotaKeywordsCache,
                                                          Map<Long, String> quotaNameCache,
                                                          Map<Long, String> quotaFeatureCache) {
        AiReviewRequest.AiReviewItem reviewItem = new AiReviewRequest.AiReviewItem();
        reviewItem.setItemId(item.getId());
        reviewItem.setItemCode(item.getItemCode());
        reviewItem.setItemName(item.getItemName());
        reviewItem.setFeatureValue(item.getFeatureValue());
        reviewItem.setUnit(item.getUnit());
        reviewItem.setQuantity(item.getQuantity());
        reviewItem.setOriginalQuotaId(item.getMatchedQuotaId());
        reviewItem.setOriginalQuotaCode(item.getMatchedQuotaCode());
        reviewItem.setOriginalQuotaName(item.getMatchedQuotaName());

        // 预筛选 Top-N 候选定额
        List<AiReviewRequest.CandidateQuota> candidates = filterTopCandidates(item, allQuotas,
                quotaKeywordsCache, quotaNameCache, quotaFeatureCache, config.getCandidatesPerItem());
        reviewItem.setCandidates(candidates);

        // 计算传统算法得分作为参考
        if (item.getMatchedQuotaId() != null && quotaKeywordsCache.containsKey(item.getMatchedQuotaId())) {
            List<String> itemKeywords = new ArrayList<>();
            if (item.getItemName() != null) itemKeywords.addAll(KeywordExtractor.extractKeywords(item.getItemName()));
            if (item.getFeatureValue() != null) itemKeywords.addAll(KeywordExtractor.extractKeywords(item.getFeatureValue()));
            double score = KeywordExtractor.calculateSimilarity(itemKeywords,
                    quotaKeywordsCache.get(item.getMatchedQuotaId()));
            reviewItem.setOriginalScore(score);
        } else {
            // 未匹配项：记录与最佳候选的 Jaccard 得分，供日志参考
            if (reviewItem.getCandidates() != null && !reviewItem.getCandidates().isEmpty()) {
                List<String> itemKeywords = new ArrayList<>();
                if (item.getItemName() != null) itemKeywords.addAll(KeywordExtractor.extractKeywords(item.getItemName()));
                if (item.getFeatureValue() != null) itemKeywords.addAll(KeywordExtractor.extractKeywords(item.getFeatureValue()));
                double bestScore = 0;
                for (AiReviewRequest.CandidateQuota c : reviewItem.getCandidates()) {
                    List<String> kw = quotaKeywordsCache.get(c.getQuotaId());
                    if (kw != null) {
                        bestScore = Math.max(bestScore, KeywordExtractor.calculateSimilarity(itemKeywords, kw));
                    }
                }
                reviewItem.setOriginalScore(bestScore);
            }
        }

        return reviewItem;
    }

    /**
     * 预筛选 Top-N 候选定额（使用 Jaccard 相似度）
     */
    private List<AiReviewRequest.CandidateQuota> filterTopCandidates(ProjectItem item,
                                                                      List<EnterpriseQuota> allQuotas,
                                                                      Map<Long, List<String>> quotaKeywordsCache,
                                                                      Map<Long, String> quotaNameCache,
                                                                      Map<Long, String> quotaFeatureCache,
                                                                      int topN) {
        List<String> itemKeywords = new ArrayList<>();
        String itemName = item.getItemName() != null ? item.getItemName().trim() : "";
        String itemFeature = item.getFeatureValue() != null ? item.getFeatureValue().trim() : "";
        if (!itemName.isEmpty()) itemKeywords.addAll(KeywordExtractor.extractKeywords(itemName));
        if (!itemFeature.isEmpty()) itemKeywords.addAll(KeywordExtractor.extractKeywords(itemFeature));

        List<Map.Entry<EnterpriseQuota, Double>> scored = new ArrayList<>();

        for (EnterpriseQuota quota : allQuotas) {
            List<String> quotaKeywords = quotaKeywordsCache.get(quota.getId());
            if (quotaKeywords == null || quotaKeywords.isEmpty()) continue;
            double score = KeywordExtractor.calculateSimilarity(itemKeywords, quotaKeywords);
            // 未匹配项降低阈值到 0.05，确保能筛选出候选供 AI 语义匹配
            double minThreshold = (item.getMatchedQuotaId() == null) ? 0.05 : 0.1;
            if (score > minThreshold) {
                scored.add(new AbstractMap.SimpleEntry<>(quota, score));
            }
        }

        scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<AiReviewRequest.CandidateQuota> candidates = new ArrayList<>();
        for (int i = 0; i < Math.min(topN, scored.size()); i++) {
            EnterpriseQuota q = scored.get(i).getKey();
            AiReviewRequest.CandidateQuota c = new AiReviewRequest.CandidateQuota();
            c.setQuotaId(q.getId());
            c.setQuotaCode(q.getQuotaCode());
            c.setQuotaName(q.getQuotaName());
            c.setFeatureValue(q.getFeatureValue());
            c.setUnit(q.getUnit());
            c.setUnitPrice(q.getUnitPrice());
            candidates.add(c);
        }

        // 确保原匹配的定额在候选列表中（如果不在top-N中）
        if (item.getMatchedQuotaId() != null) {
            boolean hasOriginal = candidates.stream()
                    .anyMatch(c -> c.getQuotaId().equals(item.getMatchedQuotaId()));
            if (!hasOriginal) {
                EnterpriseQuota original = allQuotas.stream()
                        .filter(q -> q.getId().equals(item.getMatchedQuotaId()))
                        .findFirst().orElse(null);
                if (original != null) {
                    AiReviewRequest.CandidateQuota c = new AiReviewRequest.CandidateQuota();
                    c.setQuotaId(original.getId());
                    c.setQuotaCode(original.getQuotaCode());
                    c.setQuotaName(original.getQuotaName());
                    c.setFeatureValue(original.getFeatureValue());
                    c.setUnit(original.getUnit());
                    c.setUnitPrice(original.getUnitPrice());
                    candidates.add(c);
                }
            }
        }

        return candidates;
    }

    /**
     * 构建批量 Prompt
     */
    private String buildBatchPrompt(List<AiReviewRequest.AiReviewItem> batch,
                                    List<Map<String, String>> fewShots) {
        StringBuilder sb = new StringBuilder();

        // Few-Shot 示例
        if (!fewShots.isEmpty()) {
            sb.append("参考案例：\n");
            for (int i = 0; i < fewShots.size(); i++) {
                Map<String, String> shot = fewShots.get(i);
                sb.append("[案例").append(i + 1).append("] ");
                sb.append("清单\"").append(shot.get("itemName"));
                String feat = shot.get("itemFeature");
                if (feat != null && !feat.isEmpty()) {
                    sb.append("/").append(feat);
                }
                sb.append("\" → 定额\"").append(shot.get("quotaName")).append("\"");
                if (shot.get("accepted") != null && !shot.get("accepted").isEmpty()) {
                    sb.append(" ").append(shot.get("accepted"));
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // 当前批次清单项
        sb.append("请为以下").append(batch.size()).append("个项目清单项匹配最合适的定额：\n\n");

        try {
            sb.append(objectMapper.writeValueAsString(batch));
        } catch (JsonProcessingException e) {
            // 降级为手动构建
            sb.append("[\n");
            for (AiReviewRequest.AiReviewItem item : batch) {
                sb.append("  {\"itemCode\":\"").append(escapeJson(item.getItemCode()))
                  .append("\",\"itemName\":\"").append(escapeJson(item.getItemName()))
                  .append("\",\"featureValue\":\"").append(escapeJson(item.getFeatureValue()))
                  .append("\",\"unit\":\"").append(escapeJson(item.getUnit()))
                  .append("\",\"candidates\":").append(buildCandidatesJson(item.getCandidates()))
                  .append("},\n");
            }
            sb.append("]");
        }

        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String buildCandidatesJson(List<AiReviewRequest.CandidateQuota> candidates) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < candidates.size(); i++) {
            AiReviewRequest.CandidateQuota c = candidates.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"quotaId\":").append(c.getQuotaId())
              .append(",\"quotaCode\":\"").append(escapeJson(c.getQuotaCode())).append("\"")
              .append(",\"quotaName\":\"").append(escapeJson(c.getQuotaName())).append("\"")
              .append(",\"featureValue\":\"").append(escapeJson(c.getFeatureValue())).append("\"")
              .append(",\"unit\":\"").append(escapeJson(c.getUnit())).append("\"")
              .append(",\"unitPrice\":").append(c.getUnitPrice())
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 调用 DeepSeek API
     */
    private String callDeepSeekApi(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", config.getModel());
            requestBody.put("temperature", config.getTemperature());
            requestBody.put("max_tokens", config.getMaxTokens());

            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> sysMsg = new LinkedHashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.add(sysMsg);

            Map<String, String> userMsg = new LinkedHashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messages.add(userMsg);

            requestBody.put("messages", messages);

            String json = objectMapper.writeValueAsString(requestBody);

            Request request = new Request.Builder()
                    .url(config.getBaseUrl())
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            // 带重试的调用
            for (int attempt = 0; attempt <= config.getMaxRetries(); attempt++) {
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        JsonNode root = objectMapper.readTree(responseBody);

                        // 提取 Token 用量
                        JsonNode usage = root.get("usage");
                        if (usage != null) {
                            int promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
                            int completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0;
                            tokenTracker.recordUsage(promptTokens, completionTokens);
                        }

                        // 提取 content
                        JsonNode choices = root.get("choices");
                        if (choices != null && choices.size() > 0) {
                            JsonNode message = choices.get(0).get("message");
                            if (message != null && message.has("content")) {
                                return message.get("content").asText();
                            }
                        }
                        return null;

                    } else if (response.code() == 429) {
                        // 速率限制 - 指数退避
                        long waitMs = (long) Math.pow(2, attempt) * 1000;
                        System.out.println("DeepSeek API 限流，等待 " + waitMs + "ms 后重试...");
                        Thread.sleep(waitMs);
                    } else {
                        String errorBody = response.body() != null ? response.body().string() : "";
                        System.err.println("DeepSeek API 错误 " + response.code() + ": " + errorBody);
                        if (attempt < config.getMaxRetries()) {
                            Thread.sleep(1000);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("调用 DeepSeek API 异常: " + e.getMessage());
        }
        return null;
    }

    /**
     * 解析 LLM 响应 JSON
     */
    List<AiReviewResponse.AiReviewResult> parseResponse(String llmResponse) {
        if (llmResponse == null || llmResponse.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            // 尝试直接解析
            AiReviewResponse aiResponse = objectMapper.readValue(llmResponse, AiReviewResponse.class);
            return aiResponse.getResults() != null ? aiResponse.getResults() : Collections.emptyList();
        } catch (Exception e) {
            // 尝试从 markdown 代码块中提取 JSON
            try {
                Matcher m = JSON_PATTERN.matcher(llmResponse);
                if (m.find()) {
                    String jsonStr = m.group();
                    AiReviewResponse aiResponse = objectMapper.readValue(jsonStr, AiReviewResponse.class);
                    return aiResponse.getResults() != null ? aiResponse.getResults() : Collections.emptyList();
                }
            } catch (Exception ex) {
                System.err.println("解析 AI 响应 JSON 失败: " + ex.getMessage());
                System.err.println("原始响应: " + llmResponse.substring(0, Math.min(500, llmResponse.length())));
            }
        }
        return Collections.emptyList();
    }

    /**
     * 应用 AI 匹配结果
     */
    @Transactional
    int applyResults(List<AiReviewRequest.AiReviewItem> batch,
                     List<AiReviewResponse.AiReviewResult> results, Long userId) {
        int changedCount = 0;

        // 构建 itemCode -> item 的映射
        Map<String, AiReviewRequest.AiReviewItem> itemByCode = new HashMap<>();
        for (AiReviewRequest.AiReviewItem item : batch) {
            if (item.getItemCode() != null) {
                itemByCode.put(item.getItemCode(), item);
            }
        }

        for (AiReviewResponse.AiReviewResult result : results) {
            AiReviewRequest.AiReviewItem reviewItem = itemByCode.get(result.getItemCode());
            if (reviewItem == null) continue;

            ProjectItem item = itemRepository.findById(reviewItem.getItemId()).orElse(null);
            if (item == null) continue;

            // 跳过人工修正的项（matchStatus=2 或 3）
            if (item.getMatchStatus() != null && (item.getMatchStatus() == 2 || item.getMatchStatus() == 3)) {
                continue;
            }

            Long aiQuotaId = result.getMatchedQuotaId();
            Long originalQuotaId = reviewItem.getOriginalQuotaId();

            // 保存 AI 复核日志
            AiReviewLog log = new AiReviewLog();
            log.setUserId(userId);
            log.setItemId(item.getId());
            log.setItemName(item.getItemName());
            log.setItemFeatureValue(item.getFeatureValue());
            log.setOriginalQuotaId(originalQuotaId);
            log.setOriginalQuotaName(reviewItem.getOriginalQuotaName());
            log.setOriginalScore(reviewItem.getOriginalScore());
            log.setAiConfidence(result.getConfidence());
            log.setAiReasoning(result.getReasoning());

            // 序列化关键词
            try {
                List<String> keywords = KeywordExtractor.extractKeywords(
                        (item.getItemName() != null ? item.getItemName() : "") + " " +
                        (item.getFeatureValue() != null ? item.getFeatureValue() : ""));
                log.setItemKeywords(objectMapper.writeValueAsString(keywords));
            } catch (Exception ignored) {}

            // 比较 AI 结果 vs 传统结果
            boolean aiHasSuggestion = aiQuotaId != null && result.getConfidence() != null && result.getConfidence() >= 0.5;
            boolean sameAsOriginal = aiQuotaId != null && originalQuotaId != null && aiQuotaId.equals(originalQuotaId);

            if (aiHasSuggestion && !sameAsOriginal) {
                // AI 建议不同 → 写入 aiSuggest 字段 + matchStatus=4
                log.setAiSuggestQuotaId(aiQuotaId);

                EnterpriseQuota suggestQuota = quotaRepository.findById(aiQuotaId).orElse(null);
                if (suggestQuota != null) {
                    log.setAiSuggestQuotaName(suggestQuota.getQuotaName());

                    item.setAiSuggestQuotaId(suggestQuota.getId());
                    item.setAiSuggestQuotaCode(suggestQuota.getQuotaCode());
                    item.setAiSuggestQuotaName(suggestQuota.getQuotaName());
                    item.setAiSuggestQuotaFeatureValue(suggestQuota.getFeatureValue());
                    item.setAiSuggestConfidence(result.getConfidence());
                    item.setAiSuggestReasoning(result.getReasoning());
                    item.setMatchStatus(4); // AI 建议复核
                    itemRepository.save(item);
                    changedCount++;
                }
            } else {
                log.setAiSuggestQuotaId(aiQuotaId);
                if (sameAsOriginal && originalQuotaId != null) {
                    // AI 同意原匹配
                    EnterpriseQuota oq = quotaRepository.findById(originalQuotaId).orElse(null);
                    log.setAiSuggestQuotaName(oq != null ? oq.getQuotaName() : null);
                }
            }
            log.setIsAccepted(sameAsOriginal); // AI 同意原匹配视为已确认
            reviewLogRepository.save(log);
        }

        return changedCount;
    }

    /**
     * 接受 AI 建议：将 AI 建议的定额设为正式匹配
     */
    @Transactional
    public void acceptAiSuggestion(Long itemId) {
        ProjectItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("项目清单不存在"));

        if (item.getMatchStatus() == null || item.getMatchStatus() != 4) {
            throw new RuntimeException("该清单项没有待处理的 AI 建议");
        }

        if (item.getAiSuggestQuotaId() == null) {
            throw new RuntimeException("AI 建议的定额不存在");
        }

        EnterpriseQuota quota = quotaRepository.findById(item.getAiSuggestQuotaId())
                .orElseThrow(() -> new RuntimeException("AI 建议的定额不存在"));

        // 写入正式匹配字段
        item.setMatchedQuotaId(quota.getId());
        item.setMatchedQuotaCode(quota.getQuotaCode());
        item.setMatchedQuotaName(quota.getQuotaName());
        item.setMatchedQuotaFeatureValue(quota.getFeatureValue());
        item.setMatchedUnitPrice(quota.getUnitPrice());
        item.setMatchStatus(1);

        if (item.getQuantity() != null && quota.getUnitPrice() != null) {
            item.setTotalPrice(item.getQuantity().multiply(quota.getUnitPrice()));
        }

        // 清除 AI 建议字段
        clearAiSuggestFields(item);
        itemRepository.save(item);

        // 标记日志为已采纳
        List<AiReviewLog> logs = reviewLogRepository.findByItemIdOrderByCreateTimeDesc(itemId);
        if (!logs.isEmpty()) {
            AiReviewLog latest = logs.get(0);
            latest.setIsAccepted(true);
            reviewLogRepository.save(latest);
        }
    }

    /**
     * 拒绝 AI 建议：恢复原匹配
     */
    @Transactional
    public void rejectAiSuggestion(Long itemId) {
        ProjectItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("项目清单不存在"));

        if (item.getMatchStatus() == null || item.getMatchStatus() != 4) {
            throw new RuntimeException("该清单项没有待处理的 AI 建议");
        }

        // 恢复 matchStatus=1
        item.setMatchStatus(1);
        clearAiSuggestFields(item);
        itemRepository.save(item);

        // 标记日志为拒绝
        List<AiReviewLog> logs = reviewLogRepository.findByItemIdOrderByCreateTimeDesc(itemId);
        if (!logs.isEmpty()) {
            AiReviewLog latest = logs.get(0);
            latest.setIsAccepted(false);
            reviewLogRepository.save(latest);
        }
    }

    private void clearAiSuggestFields(ProjectItem item) {
        item.setAiSuggestQuotaId(null);
        item.setAiSuggestQuotaCode(null);
        item.setAiSuggestQuotaName(null);
        item.setAiSuggestQuotaFeatureValue(null);
        item.setAiSuggestConfidence(null);
        item.setAiSuggestReasoning(null);
    }

    /** 判断是否在 AI 复核的模糊区间内 */
    public boolean isInFuzzyRange(double score) {
        return score >= minReviewScore && score <= maxReviewScore;
    }

    /** 判断 AI 复核是否可用 */
    public boolean isAvailable() {
        return config.isEnabled() && config.isApiKeyConfigured();
    }
}
