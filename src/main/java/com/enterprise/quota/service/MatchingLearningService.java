package com.enterprise.quota.service;

import com.enterprise.quota.entity.*;
import com.enterprise.quota.repository.*;
import com.enterprise.quota.util.KeywordExtractor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 匹配学习服务
 * 负责收集匹配数据、分析学习、应用学习结果
 */
@Service
public class MatchingLearningService {
    
    @Autowired
    private MatchingLearningRecordRepository learningRecordRepository;
    
    @Autowired
    private KeywordWeightRepository keywordWeightRepository;
    
    @Autowired
    private MatchingRuleRepository matchingRuleRepository;
    
    @Autowired
    private ProjectItemRepository itemRepository;
    
    @Autowired
    private EnterpriseQuotaRepository quotaRepository;
    
    @Autowired
    private ProjectItemQuotaRepository itemQuotaRepository;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 学习权重配置
    private static final double AUTO_MATCH_WEIGHT = 0.5; // 自动匹配权重
    private static final double MANUAL_MATCH_WEIGHT = 1.0; // 手动修改权重
    
    /**
     * 收集匹配数据（在匹配完成后调用）
     */
    @Transactional
    public void collectMatchData(ProjectItem item, EnterpriseQuota quota, double matchScore, int matchType) {
        try {
            MatchingLearningRecord record = new MatchingLearningRecord();
            record.setItemName(item.getItemName());
            record.setItemFeatureValue(item.getFeatureValue());
            record.setQuotaName(quota.getQuotaName());
            record.setQuotaFeatureValue(quota.getFeatureValue());
            record.setMatchScore(matchScore);
            record.setMatchType(matchType);
            
            // 提取关键词
            List<String> itemKeywords = new ArrayList<>();
            if (item.getItemName() != null && !item.getItemName().trim().isEmpty()) {
                itemKeywords.addAll(KeywordExtractor.extractKeywords(item.getItemName()));
            }
            if (item.getFeatureValue() != null && !item.getFeatureValue().trim().isEmpty()) {
                itemKeywords.addAll(KeywordExtractor.extractKeywords(item.getFeatureValue()));
            }
            
            List<String> quotaKeywords = new ArrayList<>();
            if (quota.getQuotaName() != null && !quota.getQuotaName().trim().isEmpty()) {
                quotaKeywords.addAll(KeywordExtractor.extractKeywords(quota.getQuotaName()));
            }
            if (quota.getFeatureValue() != null && !quota.getFeatureValue().trim().isEmpty()) {
                quotaKeywords.addAll(KeywordExtractor.extractKeywords(quota.getFeatureValue()));
            }
            
            // 计算共同关键词
            Set<String> itemKeywordSet = new HashSet<>(itemKeywords);
            Set<String> quotaKeywordSet = new HashSet<>(quotaKeywords);
            List<String> commonKeywords = itemKeywordSet.stream()
                    .filter(quotaKeywordSet::contains)
                    .collect(Collectors.toList());
            
            // 保存关键词（JSON格式）
            record.setItemKeywords(objectMapper.writeValueAsString(itemKeywords));
            record.setQuotaKeywords(objectMapper.writeValueAsString(quotaKeywords));
            record.setCommonKeywords(objectMapper.writeValueAsString(commonKeywords));
            
            // 设置学习权重
            if (matchType == 2 || matchType == 3) {
                record.setLearningWeight(MANUAL_MATCH_WEIGHT); // 手动修改权重更高
            } else {
                record.setLearningWeight(AUTO_MATCH_WEIGHT);
            }
            
            learningRecordRepository.save(record);
        } catch (Exception e) {
            // 记录错误但不影响主流程
            System.err.println("收集学习数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 分析学习数据，更新关键词权重
     */
    @Transactional
    public void analyzeAndUpdateWeights() {
        List<MatchingLearningRecord> records = learningRecordRepository.findAllByOrderByCreateTimeDesc();
        
        if (records.size() < 10) {
            // 数据太少，暂不学习
            return;
        }
        
        // 统计关键词权重
        Map<String, KeywordWeight> keywordWeightMap = new HashMap<>();
        
        for (MatchingLearningRecord record : records) {
            try {
                List<String> commonKeywords = objectMapper.readValue(
                    record.getCommonKeywords(), 
                    new TypeReference<List<String>>() {}
                );
                
                double weight = record.getLearningWeight();
                
                for (String keyword : commonKeywords) {
                    KeywordWeight kw = keywordWeightMap.get(keyword);
                    if (kw == null) {
                        kw = keywordWeightRepository.findByKeyword(keyword)
                            .orElse(new KeywordWeight());
                        kw.setKeyword(keyword);
                        kw.setWeight(1.0);
                        kw.setMatchCount(0);
                        kw.setTotalCount(0);
                    }
                    
                    // 更新统计
                    kw.setTotalCount(kw.getTotalCount() + 1);
                    kw.setMatchCount((int)(kw.getMatchCount() + weight));
                    
                    // 计算新权重：成功率越高，权重越高
                    double successRate = kw.getMatchCount() / (double) kw.getTotalCount();
                    kw.setSuccessRate(successRate);
                    
                    // 权重 = 1.0 + (成功率 - 0.5) * 2.0
                    // 成功率0.5时权重1.0，成功率1.0时权重2.0
                    double newWeight = 1.0 + (successRate - 0.5) * 2.0;
                    kw.setWeight(Math.max(0.5, Math.min(2.0, newWeight))); // 限制在0.5-2.0之间
                    
                    keywordWeightMap.put(keyword, kw);
                }
            } catch (Exception e) {
                System.err.println("分析学习记录失败: " + e.getMessage());
            }
        }
        
        // 保存更新的权重
        keywordWeightRepository.saveAll(keywordWeightMap.values());
    }
    
    /**
     * 发现同义词关系
     */
    @Transactional
    public void discoverSynonyms() {
        // 获取手动修改的记录（这些是"正确答案"）
        List<MatchingLearningRecord> manualRecords = learningRecordRepository
            .findByMatchTypeOrderByCreateTimeDesc(2);
        
        if (manualRecords.size() < 5) {
            return;
        }
        
        // 按定额分组，找出匹配到相同定额的不同项目
        Map<String, List<MatchingLearningRecord>> quotaGroups = new HashMap<>();
        
        for (MatchingLearningRecord record : manualRecords) {
            String quotaKey = record.getQuotaName() + "|" + record.getQuotaFeatureValue();
            quotaGroups.computeIfAbsent(quotaKey, k -> new ArrayList<>()).add(record);
        }
        
        // 分析每组，找出可能的同义词
        for (Map.Entry<String, List<MatchingLearningRecord>> entry : quotaGroups.entrySet()) {
            List<MatchingLearningRecord> group = entry.getValue();
            if (group.size() >= 2) {
                // 提取项目名称中的关键词，找出相似的部分
                analyzeGroupForSynonyms(group);
            }
        }
    }
    
    /**
     * 分析一组记录，发现同义词
     */
    private void analyzeGroupForSynonyms(List<MatchingLearningRecord> group) {
        try {
            // 提取所有项目名称的关键词
            Map<String, Set<String>> keywordMap = new HashMap<>();
            
            for (MatchingLearningRecord record : group) {
                List<String> keywords = objectMapper.readValue(
                    record.getItemKeywords(),
                    new TypeReference<List<String>>() {}
                );
                
                for (String keyword : keywords) {
                    keywordMap.computeIfAbsent(keyword, k -> new HashSet<>()).add(record.getItemName());
                }
            }
            
            // 找出在多个项目中都出现的关键词（可能是同义词）
            for (Map.Entry<String, Set<String>> entry : keywordMap.entrySet()) {
                if (entry.getValue().size() >= 2) {
                    // 检查是否已存在同义词规则
                    String keyword = entry.getKey();
                    List<MatchingRule> existingRules = matchingRuleRepository
                        .findByRuleTypeAndConfidenceGreaterThanOrderByConfidenceDesc("synonym", 0.7);
                    
                    boolean exists = existingRules.stream()
                        .anyMatch(rule -> rule.getSourceText().equals(keyword) || 
                                        rule.getTargetText().equals(keyword));
                    
                    if (!exists && entry.getValue().size() >= group.size() * 0.6) {
                        // 创建同义词规则
                        MatchingRule rule = new MatchingRule();
                        rule.setRuleType("synonym");
                        rule.setSourceText(keyword);
                        rule.setTargetText(keyword); // 同义词指向自己
                        rule.setConfidence(0.7 + (entry.getValue().size() / (double) group.size()) * 0.3);
                        rule.setRuleValue(objectMapper.writeValueAsString(entry.getValue()));
                        matchingRuleRepository.save(rule);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("发现同义词失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取学习到的关键词权重
     */
    public Map<String, Double> getLearnedKeywordWeights() {
        List<KeywordWeight> weights = keywordWeightRepository.findAll();
        Map<String, Double> weightMap = new HashMap<>();
        
        for (KeywordWeight kw : weights) {
            weightMap.put(kw.getKeyword(), kw.getWeight());
        }
        
        return weightMap;
    }
    
    /**
     * 获取学习到的同义词映射
     */
    public Map<String, Set<String>> getLearnedSynonyms() {
        List<MatchingRule> rules = matchingRuleRepository
            .findByRuleTypeAndConfidenceGreaterThanOrderByConfidenceDesc("synonym", 0.7);
        
        Map<String, Set<String>> synonymMap = new HashMap<>();
        
        for (MatchingRule rule : rules) {
            try {
                Set<String> synonyms = objectMapper.readValue(
                    rule.getRuleValue(),
                    new TypeReference<Set<String>>() {}
                );
                synonymMap.put(rule.getSourceText(), synonyms);
            } catch (Exception e) {
                // 忽略解析错误
            }
        }
        
        return synonymMap;
    }
    
    /**
     * 批量收集所有匹配数据（用于初始化）
     */
    @Transactional
    public void collectAllMatchData() {
        List<ProjectItem> items = itemRepository.findAll();
        
        for (ProjectItem item : items) {
            if (item.getMatchStatus() != null && item.getMatchStatus() > 0) {
                if (item.getMatchedQuotaId() != null) {
                    EnterpriseQuota quota = quotaRepository.findById(item.getMatchedQuotaId()).orElse(null);
                    if (quota != null) {
                        collectMatchData(item, quota, 0.5, item.getMatchStatus());
                    }
                } else if (item.getMatchStatus() == 3) {
                    // 多定额匹配
                    List<ProjectItemQuota> quotas = itemQuotaRepository
                        .findByProjectItemIdOrderBySortOrderAsc(item.getId());
                    for (ProjectItemQuota itemQuota : quotas) {
                        EnterpriseQuota quota = quotaRepository.findById(itemQuota.getQuotaId()).orElse(null);
                        if (quota != null) {
                            collectMatchData(item, quota, 0.5, 3);
                        }
                    }
                }
            }
        }
    }
}

