package com.enterprise.quota.service;

import com.enterprise.quota.config.DeepSeekConfig;
import com.enterprise.quota.dto.ChatResponse;
import com.enterprise.quota.entity.EnterpriseQuota;
import com.enterprise.quota.entity.QuotaVersion;
import com.enterprise.quota.repository.EnterpriseQuotaRepository;
import com.enterprise.quota.repository.QuotaVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI 助手服务 v2 — 全量定额送入 DeepSeek 做语义匹配
 */
@Service
public class AssistantService {

    @Autowired
    private DeepSeekConfig config;

    @Autowired
    private OkHttpClient httpClient;

    @Autowired
    private EnterpriseQuotaRepository quotaRepository;

    @Autowired
    private QuotaVersionRepository versionRepository;

    @Autowired
    private TokenUsageTracker tokenTracker;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 定额超过此数量时启用关键词粗筛 */
    private static final int MAX_QUOTAS_IN_PROMPT = 2000;

    /** 粗筛时保留的候选数 */
    private static final int ROUGH_FILTER_SIZE = 100;

    /** System Prompt（全量定额索引） */
    private static final String SYSTEM_PROMPT =
        "你是建筑工程预算工程师助手，精通企业定额套用和工程造价。\n" +
        "以下是系统中所有企业定额数据（格式：编码 | 名称 | 特征值 | 单位 | 单价 | 备注）：\n\n" +
        "%s\n\n" +
        "回答规则（必须严格遵守）：\n" +
        "1. 【强制】每条价格信息必须标注出处：回答中提及的任何单价、合价、施工费、人工费、材料费、" +
        "机械费等涉及金额的数字，都必须紧接着标注来自哪个定额版本，格式：\"【版本名称】\"。" +
        "示例：\"电缆敷设单价为125.60元/m【2024省定额】\"\n" +
        "2. 根据用户问题，从上述定额数据中做语义匹配，找出所有相关的定额，不得遗漏任何版本\n" +
        "3. 匹配时重点关注：定额名称的语义相似度、项目特征值的技术参数一致性、单位是否匹配\n" +
        "4. 【强制】所有匹配到的定额必须全部列出，按版本分组呈现。每条定额严格按以下格式输出：\n" +
        "   - 定额名称：<编码> <名称>\n" +
        "   - 项目特征：<提炼后的特征描述>\n" +
        "   - 单价：<单价> <单位>\n" +
        "   - 备注：<定额数据中的备注信息>\n" +
        "   示例：\n" +
        "   - 定额名称：DE2-101 电缆敷设(截面120mm²以下)\n" +
        "   - 项目特征：铜芯电力电缆，截面120mm²，敷设方式综合考虑\n" +
        "   - 单价：125.60元/m\n" +
        "   - 备注：【2024省定额】含电缆敷设、终端头制作安装\n" +
        "5. 如果确实没有相关定额，回复\"暂未在定额库中找到与该问题相关的定额\"\n" +
        "6. 绝对不要使用定额数据以外的知识进行猜测或补充\n" +
        "7. 回答简洁专业，不要输出多余的解释性文字，使用中文\n" +
        "8. 用户指定版本时（如\"用新建定额\"），只使用该版本的数据\n" +
        "9. 【强制】用户未指定版本时，必须在所有可用版本中全面搜索，各版本中符合条件的定额" +
        "都要列出，不得对特定版本产生偏好";

    /**
     * 判断服务是否可用
     */
    public boolean isAvailable() {
        return config.isEnabled() && config.isApiKeyConfigured();
    }

    /**
     * 处理用户问题，返回回答
     */
    public Map<String, Object> chat(String question, Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!isAvailable()) {
            result.put("success", false);
            result.put("message", "AI 助手服务未启用，请配置 DEEPSEEK_API_KEY 环境变量");
            result.put("answer", "抱歉，AI 助手服务暂未启用。请联系管理员配置 DeepSeek API Key。");
            return result;
        }

        if (question == null || question.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "问题不能为空");
            result.put("answer", "请提出您的问题，我会尽力为您解答。");
            return result;
        }

        // 1. 加载所有定额，构建索引
        List<EnterpriseQuota> allQuotas = quotaRepository.findAll();
        if (allQuotas.isEmpty()) {
            result.put("success", true);
            result.put("answer", "当前系统中还没有上传企业定额数据，请先在「定额管理」中导入定额后再提问。");
            result.put("sources", Collections.emptyList());
            result.put("quotaReferences", Collections.emptyList());
            result.put("timestamp", System.currentTimeMillis());
            return result;
        }

        // 2. 构建版本名映射
        Map<Long, String> versionNameMap = buildVersionNameMap(allQuotas);

        // 2.5 检测用户是否指定了特定版本，如果有则预过滤
        List<EnterpriseQuota> versionFilteredQuotas = filterByVersion(question, allQuotas, versionNameMap);

        // 3. 如果定额太多，做一次粗筛；否则全量送入
        List<EnterpriseQuota> quotasForPrompt;
        if (versionFilteredQuotas.size() > MAX_QUOTAS_IN_PROMPT) {
            quotasForPrompt = roughFilter(question, versionFilteredQuotas, versionNameMap);
        } else {
            quotasForPrompt = versionFilteredQuotas;
        }

        // 4. 构建定额索引文本
        String quotaIndex = buildQuotaIndex(quotasForPrompt, versionNameMap);

        // 5. 构建 System Prompt
        String systemPrompt = String.format(SYSTEM_PROMPT, quotaIndex);

        // 6. 调用 DeepSeek API（追加指令：全版本覆盖、精简格式）
        String enhancedQuestion = "请回答以下问题。核心要求：\n" +
                "1. 在所有可用版本中搜索，各版本符合条件的定额都要列出，按版本分组呈现\n" +
                "2. 每条定额只输出四个字段：定额名称、项目特征、单价、备注\n" +
                "3. 不要输出多余的解释性文字，不要遗漏任何版本\n" +
                "\n问题：" + question;
        String llmResponse = callDeepSeekApi(systemPrompt, enhancedQuestion);

        // 7. 解析响应
        ChatResponse chatResponse;
        if (llmResponse != null && !llmResponse.trim().isEmpty()) {
            chatResponse = parseResponse(llmResponse, quotasForPrompt, versionNameMap);
        } else {
            chatResponse = new ChatResponse();
            chatResponse.setAnswer("抱歉，AI 服务暂时无法响应，请稍后重试。");
        }

        result.put("success", true);
        result.put("answer", chatResponse.getAnswer());
        result.put("sources", chatResponse.getSources());
        result.put("quotaReferences", chatResponse.getQuotaReferences());
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 构建定额精简索引文本（每行一条，供 DeepSeek 语义匹配）
     */
    private String buildQuotaIndex(List<EnterpriseQuota> quotas, Map<Long, String> versionNameMap) {
        StringBuilder sb = new StringBuilder();
        // 按版本分组（排序保证中立，避免 LLM 偏好排在前面的版本）
        Map<String, List<EnterpriseQuota>> byVersion = new TreeMap<>();
        for (EnterpriseQuota q : quotas) {
            String vName = versionNameMap.getOrDefault(q.getVersionId(), "未分类");
            byVersion.computeIfAbsent(vName, k -> new ArrayList<>()).add(q);
        }

        for (Map.Entry<String, List<EnterpriseQuota>> entry : byVersion.entrySet()) {
            sb.append("【").append(entry.getKey()).append("】\n");
            for (EnterpriseQuota q : entry.getValue()) {
                sb.append(q.getQuotaCode() != null ? q.getQuotaCode() : "-").append(" | ");
                sb.append(q.getQuotaName() != null ? q.getQuotaName() : "-").append(" | ");
                sb.append(q.getFeatureValue() != null ? q.getFeatureValue() : "").append(" | ");
                sb.append(q.getUnit() != null ? q.getUnit() : "").append(" | ");
                sb.append(q.getUnitPrice() != null ? q.getUnitPrice() : "-").append(" | ");
                sb.append(q.getRemark() != null ? q.getRemark() : "").append("\n");
            }
            sb.append("\n");
        }

        sb.append("共 ").append(quotas.size()).append(" 条定额");
        return sb.toString();
    }

    /**
     * 粗筛：定额太多时，用简单关键词筛出 top-N 候选
     */
    private List<EnterpriseQuota> roughFilter(String question, List<EnterpriseQuota> allQuotas,
                                               Map<Long, String> versionNameMap) {
        // 提取问题中的中文关键词作为搜索词
        Set<String> searchTerms = new LinkedHashSet<>();

        // 中文片段（2-4字）
        String chinese = question.replaceAll("[^\\u4e00-\\u9fa5]+", "");
        for (int len = Math.min(chinese.length(), 4); len >= 2; len--) {
            for (int i = 0; i <= chinese.length() - len; i++) {
                searchTerms.add(chinese.substring(i, i + len));
            }
        }

        // 规格代码
        String codePattern = "([A-Za-z]+[0-9*\\-+]+[A-Za-z0-9*\\-+]*)|(\\d+[*\\-+]\\d+([*\\-+]?\\d+)*)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(codePattern).matcher(question);
        while (m.find()) {
            String code = m.group();
            if (code.length() >= 3) searchTerms.add(code);
        }

        // 问题片段
        for (String seg : question.split("[，。？?！!\\s、：:；;]+")) {
            String t = seg.trim();
            if (t.length() >= 3 && t.length() <= 30) searchTerms.add(t);
        }

        // 对每条定额计算命中次数，取 top-N
        Map<EnterpriseQuota, Integer> hitCounts = new LinkedHashMap<>();
        for (EnterpriseQuota q : allQuotas) {
            String versionName = versionNameMap.getOrDefault(q.getVersionId(), "");
            String text = (q.getQuotaCode() != null ? q.getQuotaCode() : "") + " " +
                         (q.getQuotaName() != null ? q.getQuotaName() : "") + " " +
                         (q.getFeatureValue() != null ? q.getFeatureValue() : "") + " " +
                         (q.getUnit() != null ? q.getUnit() : "") + " " +
                         versionName;
            int hits = 0;
            for (String term : searchTerms) {
                if (term.length() >= 2 && text.contains(term)) {
                    hits++;
                }
            }
            if (hits > 0) {
                hitCounts.put(q, hits);
            }
        }

        return hitCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(ROUGH_FILTER_SIZE)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 构建版本名映射
     */
    private Map<Long, String> buildVersionNameMap(List<EnterpriseQuota> quotas) {
        Map<Long, String> map = new HashMap<>();
        Set<Long> versionIds = new LinkedHashSet<>();
        for (EnterpriseQuota q : quotas) {
            if (q.getVersionId() != null) {
                versionIds.add(q.getVersionId());
            }
        }
        if (!versionIds.isEmpty()) {
            List<QuotaVersion> versions = versionRepository.findAllById(versionIds);
            for (QuotaVersion v : versions) {
                map.put(v.getId(), v.getVersionName());
            }
        }
        return map;
    }

    /**
     * 检测用户问题中是否指定了特定版本，如果有则只保留该版本的定额
     * 避免 LLM 在用户明确要求某版本时仍混入其他版本数据
     */
    private List<EnterpriseQuota> filterByVersion(String question, List<EnterpriseQuota> allQuotas,
                                                   Map<Long, String> versionNameMap) {
        // 收集所有版本名，去重
        Set<String> versionNames = new LinkedHashSet<>(versionNameMap.values());
        if (versionNames.size() <= 1) {
            return allQuotas; // 只有一个版本，无需过滤
        }

        // 第一轮：精确匹配 — 问题中是否包含完整版本名
        String matchedVersion = null;
        for (String vName : versionNames) {
            if (question.contains(vName)) {
                matchedVersion = vName;
                break;
            }
        }

        // 第二轮：模糊匹配 — 提取版本名中有区分度的关键词（2+字中文），
        // 看问题中提到了哪个版本的独特词
        if (matchedVersion == null) {
            Set<String> commonWords = new HashSet<>(Arrays.asList("定额", "版本", "工程"));
            Map<String, Set<String>> versionKeywords = new LinkedHashMap<>();
            for (String vName : versionNames) {
                Set<String> keywords = new LinkedHashSet<>();
                // 提取版本名中的所有中文 2+ 字片段
                String chinese = vName.replaceAll("[^\\u4e00-\\u9fa5]+", "");
                for (int len = Math.min(chinese.length(), 4); len >= 2; len--) {
                    for (int i = 0; i <= chinese.length() - len; i++) {
                        String kw = chinese.substring(i, i + len);
                        if (!commonWords.contains(kw)) {
                            keywords.add(kw);
                        }
                    }
                }
                versionKeywords.put(vName, keywords);
            }

            // 找哪些版本的关键词在问题中出现
            List<String> candidates = new ArrayList<>();
            for (Map.Entry<String, Set<String>> entry : versionKeywords.entrySet()) {
                for (String kw : entry.getValue()) {
                    if (question.contains(kw)) {
                        candidates.add(entry.getKey());
                        break;
                    }
                }
            }

            // 只有唯一匹配时才过滤，多个匹配说明关键词太泛，不冒险
            if (candidates.size() == 1) {
                matchedVersion = candidates.get(0);
            }
        }

        if (matchedVersion == null) {
            return allQuotas; // 用户未指定版本，返回全部
        }

        // 找到该版本对应的 versionId
        Long matchedVersionId = null;
        for (Map.Entry<Long, String> entry : versionNameMap.entrySet()) {
            if (matchedVersion.equals(entry.getValue())) {
                matchedVersionId = entry.getKey();
                break;
            }
        }

        if (matchedVersionId == null) {
            return allQuotas;
        }

        // 过滤：只保留该版本的定额
        final Long targetId = matchedVersionId;
        List<EnterpriseQuota> filtered = new ArrayList<>();
        for (EnterpriseQuota q : allQuotas) {
            if (targetId.equals(q.getVersionId())) {
                filtered.add(q);
            }
        }

        if (!filtered.isEmpty()) {
            System.out.println("[AI助手] 检测到版本指定：" + matchedVersion
                    + "，过滤前 " + allQuotas.size() + " 条 → 过滤后 " + filtered.size() + " 条");
            return filtered;
        }

        // 匹配到了版本名但没有定额（异常情况），返回全部
        return allQuotas;
    }

    /**
     * 调用 DeepSeek API
     */
    private String callDeepSeekApi(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", config.getModel());
            requestBody.put("temperature", 0.3);
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

            for (int attempt = 0; attempt <= config.getMaxRetries(); attempt++) {
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        JsonNode root = objectMapper.readTree(responseBody);

                        JsonNode usage = root.get("usage");
                        if (usage != null) {
                            int promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
                            int completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0;
                            tokenTracker.recordUsage(promptTokens, completionTokens);
                        }

                        JsonNode choices = root.get("choices");
                        if (choices != null && choices.size() > 0) {
                            JsonNode message = choices.get(0).get("message");
                            if (message != null && message.has("content")) {
                                return message.get("content").asText();
                            }
                        }
                        return null;

                    } else if (response.code() == 429) {
                        long waitMs = (long) Math.pow(2, attempt) * 1000;
                        Thread.sleep(waitMs);
                    } else if (response.code() >= 500) {
                        if (attempt < config.getMaxRetries()) {
                            Thread.sleep(1000);
                        }
                    } else {
                        // 4xx 客户端错误，重试无意义
                        String errorBody = response.body() != null ? response.body().string() : "";
                        System.err.println("[AI助手] API 请求错误 " + response.code() + ": " + errorBody);
                        return null;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[AI助手] DeepSeek API 调用异常: " + e.getMessage());
        }
        return null;
    }

    /**
     * 解析 LLM 响应，提取来源信息
     */
    private ChatResponse parseResponse(String llmResponse, List<EnterpriseQuota> allQuotas,
                                       Map<Long, String> versionNameMap) {
        ChatResponse response = new ChatResponse();
        response.setAnswer(llmResponse.trim());

        List<ChatResponse.SourceInfo> sources = new ArrayList<>();
        List<ChatResponse.QuotaRef> quotaRefs = new ArrayList<>();
        Set<String> refKeys = new LinkedHashSet<>();

        // 检查回答中引用了哪些定额编码
        for (EnterpriseQuota q : allQuotas) {
            if (q.getQuotaCode() != null && llmResponse.contains(q.getQuotaCode())) {
                String key = q.getQuotaCode();
                if (refKeys.add(key)) {
                    String vName = versionNameMap.getOrDefault(q.getVersionId(), "定额库");
                    sources.add(new ChatResponse.SourceInfo("quota",
                            "根据【" + vName + "】" + q.getQuotaCode() + " " + q.getQuotaName()));
                    quotaRefs.add(new ChatResponse.QuotaRef(q.getQuotaCode(), q.getQuotaName(), vName));
                }
            }
        }

        if (sources.isEmpty()) {
            sources.add(new ChatResponse.SourceInfo("quota", "基于企业定额数据回答"));
        }

        response.setSources(sources);
        response.setQuotaReferences(quotaRefs);
        return response;
    }
}
