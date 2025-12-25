package com.enterprise.quota.service;

import com.enterprise.quota.entity.EnterpriseQuota;
import com.enterprise.quota.entity.ProjectItem;
import com.enterprise.quota.entity.ProjectItemQuota;
import com.enterprise.quota.repository.EnterpriseQuotaRepository;
import com.enterprise.quota.repository.ProjectItemRepository;
import com.enterprise.quota.repository.ProjectItemQuotaRepository;
import com.enterprise.quota.util.KeywordExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class QuotaMatchingService {
    
    @Autowired
    private EnterpriseQuotaRepository quotaRepository;
    
    @Autowired
    private ProjectItemRepository itemRepository;
    
    @Autowired
    private ProjectItemQuotaRepository itemQuotaRepository;
    
    @Autowired
    private MatchingLearningService learningService;
    
    @Autowired
    @Qualifier("matchingTaskExecutor")
    private Executor matchingTaskExecutor;
    
    @Value("${quota.matching.batch-size:200}")
    private int matchingBatchSize;
    
    @Value("${quota.matching.save-batch-size:100}")
    private int saveBatchSize;
    
    /**
     * 多线程并行匹配（优化版本，充分利用多核CPU）
     */
    @Transactional(timeout = 3600) // 增加事务超时时间到1小时
    public int batchMatchQuotas(Long versionId) {
        long startTime = System.currentTimeMillis();
        
        // 获取所有需要匹配的项目清单
        List<ProjectItem> allItems = itemRepository.findAll();
        List<ProjectItem> itemsToMatch = allItems.stream()
                .filter(item -> {
                    // 跳过手动修改（单定额）和多定额匹配的项目
                    if (item.getMatchStatus() != null && (item.getMatchStatus() == 2 || item.getMatchStatus() == 3)) {
                        return false;
                    }
                    // 只有单位列有数据才进行匹配
                    return item.getUnit() != null && !item.getUnit().trim().isEmpty();
                })
                .collect(Collectors.toList());
        
        // 处理没有单位的项目
        List<ProjectItem> itemsWithoutUnit = allItems.stream()
                .filter(item -> item.getUnit() == null || item.getUnit().trim().isEmpty())
                .filter(item -> item.getMatchStatus() == null || item.getMatchStatus() != 2 && item.getMatchStatus() != 3)
                .collect(Collectors.toList());
        for (ProjectItem item : itemsWithoutUnit) {
            item.setMatchStatus(0);
        }
        
        if (itemsToMatch.isEmpty()) {
            // 如果没有需要匹配的项目，只保存没有单位的项目
            if (!itemsWithoutUnit.isEmpty()) {
                itemRepository.saveAll(itemsWithoutUnit);
            }
            return 0;
        }
        
        // 获取所有定额（如果指定了版本，则只获取该版本的定额）
        List<EnterpriseQuota> allQuotas;
        if (versionId != null) {
            allQuotas = quotaRepository.findByVersionId(versionId);
        } else {
            allQuotas = quotaRepository.findAll();
        }
        
        if (allQuotas.isEmpty()) {
            // 如果没有定额，标记所有项目为未匹配
            for (ProjectItem item : itemsToMatch) {
                item.setMatchStatus(0);
            }
            itemRepository.saveAll(itemsToMatch);
            if (!itemsWithoutUnit.isEmpty()) {
                itemRepository.saveAll(itemsWithoutUnit);
            }
            return 0;
        }
        
        // 性能优化：预提取所有定额的关键词并缓存（只执行一次）
        Map<Long, List<String>> quotaKeywordsCache = new ConcurrentHashMap<>();
        Map<Long, String> quotaNameCache = new ConcurrentHashMap<>();
        Map<Long, String> quotaFeatureCache = new ConcurrentHashMap<>();
        
        System.out.println("开始预提取定额关键词，定额数量: " + allQuotas.size());
        for (EnterpriseQuota quota : allQuotas) {
            List<String> keywords = new ArrayList<>();
            if (quota.getQuotaName() != null && !quota.getQuotaName().trim().isEmpty()) {
                keywords.addAll(KeywordExtractor.extractKeywords(quota.getQuotaName()));
                quotaNameCache.put(quota.getId(), quota.getQuotaName());
            }
            if (quota.getFeatureValue() != null && !quota.getFeatureValue().trim().isEmpty()) {
                keywords.addAll(KeywordExtractor.extractKeywords(quota.getFeatureValue()));
                quotaFeatureCache.put(quota.getId(), quota.getFeatureValue());
            }
            quotaKeywordsCache.put(quota.getId(), keywords);
        }
        System.out.println("定额关键词预提取完成");
        
        // 将项目清单分割成多个批次，用于并行处理
        int totalItems = itemsToMatch.size();
        int batchCount = Math.max(1, (totalItems + matchingBatchSize - 1) / matchingBatchSize);
        System.out.println("开始并行匹配，总项目数: " + totalItems + ", 批次数量: " + batchCount);
        
        // 使用线程安全的集合收集结果
        List<ProjectItem> allMatchedItems = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger matchedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(batchCount);
        
        // 提交并行匹配任务
        for (int i = 0; i < batchCount; i++) {
            final int batchIndex = i;
            final int start = i * matchingBatchSize;
            final int end = Math.min(start + matchingBatchSize, totalItems);
            final List<ProjectItem> batchItems = itemsToMatch.subList(start, end);
            
            matchingTaskExecutor.execute(() -> {
                try {
                    int batchMatched = processBatch(batchItems, allQuotas, quotaKeywordsCache, 
                            quotaNameCache, quotaFeatureCache, allMatchedItems);
                    matchedCount.addAndGet(batchMatched);
                } catch (Exception e) {
                    System.err.println("批次 " + batchIndex + " 处理失败: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有批次完成
        try {
            boolean finished = latch.await(30, TimeUnit.MINUTES);
            if (!finished) {
                System.err.println("警告: 匹配任务超时，部分批次可能未完成");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("匹配任务被中断");
        }
        
        // 合并所有结果
        List<ProjectItem> allItemsToSave = new ArrayList<>(allMatchedItems);
        allItemsToSave.addAll(itemsWithoutUnit);
        
        // 批量保存所有结果
        System.out.println("开始批量保存，共 " + allItemsToSave.size() + " 条记录");
        saveItemsInBatches(allItemsToSave, saveBatchSize);
        
        long endTime = System.currentTimeMillis();
        int finalMatchedCount = matchedCount.get();
        System.out.println("匹配完成，共匹配 " + finalMatchedCount + " 条，耗时: " + (endTime - startTime) + "ms");
        
        return finalMatchedCount;
    }
    
    /**
     * 处理一个批次的匹配任务
     */
    private int processBatch(List<ProjectItem> batchItems, List<EnterpriseQuota> allQuotas,
                            Map<Long, List<String>> quotaKeywordsCache,
                            Map<Long, String> quotaNameCache,
                            Map<Long, String> quotaFeatureCache,
                            List<ProjectItem> resultList) {
        int matchedCount = 0;
        List<ProjectItem> batchResults = new ArrayList<>();
        
        for (ProjectItem item : batchItems) {
            // 提取项目清单的关键词（只提取一次）
            List<String> itemKeywords = new ArrayList<>();
            String itemName = item.getItemName() != null ? item.getItemName().trim() : "";
            String itemFeature = item.getFeatureValue() != null ? item.getFeatureValue().trim() : "";
            
            if (!itemName.isEmpty()) {
                itemKeywords.addAll(KeywordExtractor.extractKeywords(itemName));
            }
            if (!itemFeature.isEmpty()) {
                itemKeywords.addAll(KeywordExtractor.extractKeywords(itemFeature));
            }
            
            if (itemKeywords.isEmpty()) {
                item.setMatchStatus(0);
                batchResults.add(item);
                continue;
            }
            
            // 使用优化的双向匹配算法找到最佳匹配
            EnterpriseQuota matchedQuota = findBestMatchOptimized(item, allQuotas, 
                    quotaKeywordsCache, quotaNameCache, quotaFeatureCache);
            
            if (matchedQuota != null) {
                item.setMatchedQuotaId(matchedQuota.getId());
                item.setMatchedQuotaCode(matchedQuota.getQuotaCode());
                item.setMatchedQuotaName(matchedQuota.getQuotaName());
                item.setMatchedQuotaFeatureValue(matchedQuota.getFeatureValue());
                item.setMatchedUnitPrice(matchedQuota.getUnitPrice());
                item.setMatchStatus(1);
                
                if (item.getQuantity() != null && matchedQuota.getUnitPrice() != null) {
                    item.setTotalPrice(item.getQuantity().multiply(matchedQuota.getUnitPrice()));
                }
                
                // 异步收集学习数据（不阻塞主流程）
                collectLearningDataAsync(item, matchedQuota, itemKeywords, itemName, itemFeature,
                        quotaKeywordsCache.get(matchedQuota.getId()),
                        quotaNameCache.get(matchedQuota.getId()),
                        quotaFeatureCache.get(matchedQuota.getId()));
                
                matchedCount++;
            } else {
                item.setMatchStatus(0);
            }
            
            batchResults.add(item);
        }
        
        // 将批次结果添加到总结果列表
        resultList.addAll(batchResults);
        
        return matchedCount;
    }
    
    /**
     * 异步收集学习数据
     */
    @Async("asyncTaskExecutor")
    public void collectLearningDataAsync(ProjectItem item, EnterpriseQuota matchedQuota,
                                        List<String> itemKeywords, String itemName, String itemFeature,
                                        List<String> quotaKeywords, String quotaName, String quotaFeature) {
        try {
            double matchScore = calculateBidirectionalMatchScoreOptimized(
                item, matchedQuota, 
                itemKeywords, itemName, itemFeature,
                quotaKeywords, quotaName, quotaFeature
            );
            learningService.collectMatchData(item, matchedQuota, matchScore, 1);
        } catch (Exception e) {
            // 学习失败不影响匹配
            System.err.println("收集学习数据失败 for item " + item.getId() + ": " + e.getMessage());
        }
    }
    
    /**
     * 分批保存项目清单
     */
    private void saveItemsInBatches(List<ProjectItem> items, int batchSize) {
        int total = items.size();
        for (int i = 0; i < total; i += batchSize) {
            int end = Math.min(i + batchSize, total);
            List<ProjectItem> batch = items.subList(i, end);
            try {
                itemRepository.saveAll(batch);
            } catch (Exception e) {
                // 如果批量保存失败，尝试逐条保存
                System.err.println("批量保存失败，尝试逐条保存: " + e.getMessage());
                for (ProjectItem saveItem : batch) {
                    try {
                        itemRepository.save(saveItem);
                    } catch (Exception ex) {
                        System.err.println("保存项目失败: " + saveItem.getId() + ", 错误: " + ex.getMessage());
                    }
                }
            }
        }
    }
    
    /**
     * 使用优化的双向匹配算法找到最佳匹配
     * 优化点：使用缓存的关键词，早期退出机制
     */
    private EnterpriseQuota findBestMatchOptimized(ProjectItem item, List<EnterpriseQuota> quotas,
                                                    Map<Long, List<String>> quotaKeywordsCache,
                                                    Map<Long, String> quotaNameCache,
                                                    Map<Long, String> quotaFeatureCache) {
        if (quotas.isEmpty()) {
            return null;
        }
        
        // 提取项目清单的关键词（只提取一次）
        List<String> itemKeywords = new ArrayList<>();
        String itemName = item.getItemName() != null ? item.getItemName().trim() : "";
        String itemFeature = item.getFeatureValue() != null ? item.getFeatureValue().trim() : "";
        
        if (!itemName.isEmpty()) {
            itemKeywords.addAll(KeywordExtractor.extractKeywords(itemName));
        }
        if (!itemFeature.isEmpty()) {
            itemKeywords.addAll(KeywordExtractor.extractKeywords(itemFeature));
        }
        
        if (itemKeywords.isEmpty()) {
            return null;
        }
        
        // 计算每个定额的匹配得分，使用早期退出机制
        MatchScore bestMatch = null;
        double highScoreThreshold = 0.8; // 高匹配度阈值，达到此值可提前退出
        
        for (EnterpriseQuota quota : quotas) {
            double score = calculateBidirectionalMatchScoreOptimized(
                item, quota, itemKeywords, itemName, itemFeature,
                quotaKeywordsCache.get(quota.getId()),
                quotaNameCache.get(quota.getId()),
                quotaFeatureCache.get(quota.getId())
            );
            
            if (score > 0 && (bestMatch == null || score > bestMatch.score)) {
                bestMatch = new MatchScore(quota, score);
                
                // 早期退出：如果找到高匹配度的结果，提前结束
                if (score >= highScoreThreshold) {
                    break;
                }
            }
        }
        
        // 只返回得分大于阈值的匹配
        if (bestMatch != null && bestMatch.score >= 0.3) {
            return bestMatch.quota;
        }
        
        return null;
    }
    
    /**
     * 使用双向匹配算法找到最佳匹配（保留原方法以兼容）
     */
    private EnterpriseQuota findBestMatchWithBidirectionalMatching(ProjectItem item, List<EnterpriseQuota> quotas) {
        if (quotas.isEmpty()) {
            return null;
        }
        
        // 提取项目清单的关键词
        List<String> itemKeywords = new ArrayList<>();
        if (item.getItemName() != null && !item.getItemName().trim().isEmpty()) {
            itemKeywords.addAll(KeywordExtractor.extractKeywords(item.getItemName()));
        }
        if (item.getFeatureValue() != null && !item.getFeatureValue().trim().isEmpty()) {
            itemKeywords.addAll(KeywordExtractor.extractKeywords(item.getFeatureValue()));
        }
        
        if (itemKeywords.isEmpty()) {
            return null;
        }
        
        // 计算每个定额的匹配得分
        List<MatchScore> scores = new ArrayList<>();
        
        for (EnterpriseQuota quota : quotas) {
            double score = calculateBidirectionalMatchScore(item, quota, itemKeywords);
            if (score > 0) {
                scores.add(new MatchScore(quota, score));
            }
        }
        
        // 如果没有匹配，返回null
        if (scores.isEmpty()) {
            return null;
        }
        
        // 按得分排序，返回得分最高的（得分需大于阈值0.3）
        scores.sort((a, b) -> Double.compare(b.score, a.score));
        MatchScore bestMatch = scores.get(0);
        
        // 只返回得分大于阈值的匹配
        if (bestMatch.score >= 0.3) {
            return bestMatch.quota;
        }
        
        return null;
    }
    
    /**
     * 计算双向匹配得分（优化版本，使用缓存和学习结果）
     */
    private double calculateBidirectionalMatchScoreWithLearning(ProjectItem item, EnterpriseQuota quota,
                                                                 List<String> itemKeywords, String itemName, String itemFeature,
                                                                 List<String> quotaKeywords, String quotaName, String quotaFeature,
                                                                 Map<String, Double> learnedWeights) {
        double score = 0.0;
        double totalWeight = 0.0;
        
        // 方向1：从项目清单匹配到定额（权重0.7）
        if (!itemName.isEmpty() && quotaName != null && !quotaName.isEmpty()) {
            double nameScore = KeywordExtractor.calculateTextMatchScore(itemName, quotaName);
            score += nameScore * 0.4;
            totalWeight += 0.4;
        }
        
        if (!itemFeature.isEmpty() && quotaFeature != null && !quotaFeature.isEmpty()) {
            double featureScore = KeywordExtractor.calculateTextMatchScore(itemFeature, quotaFeature);
            score += featureScore * 0.3;
            totalWeight += 0.3;
        }
        
        // 方向2：从定额匹配到项目清单（权重0.3，使用缓存的关键词和学习到的权重）
        if (quotaKeywords != null && !quotaKeywords.isEmpty()) {
            double keywordScore = calculateSimilarityWithLearning(itemKeywords, quotaKeywords, learnedWeights);
            score += keywordScore * 0.3;
            totalWeight += 0.3;
        }
        
        // 归一化得分
        if (totalWeight > 0) {
            score = score / totalWeight;
        }
        
        return score;
    }
    
    /**
     * 计算相似度（使用学习到的权重）
     */
    private double calculateSimilarityWithLearning(List<String> keywords1, List<String> keywords2, 
                                                    Map<String, Double> learnedWeights) {
        if (keywords1.isEmpty() || keywords2.isEmpty()) {
            return 0.0;
        }
        
        Set<String> set1 = new HashSet<>(keywords1);
        Set<String> set2 = new HashSet<>(keywords2);
        
        double intersection = 0.0;
        double totalWeight = 0.0;
        
        for (String k : set1) {
            double weight = learnedWeights.getOrDefault(k, 1.0);
            totalWeight += weight;
            
            if (set2.contains(k)) {
                intersection += weight;
            }
        }
        
        if (totalWeight == 0) {
            return 0.0;
        }
        
        return intersection / totalWeight;
    }
    
    /**
     * 计算双向匹配得分（优化版本，使用缓存）
     */
    private double calculateBidirectionalMatchScoreOptimized(ProjectItem item, EnterpriseQuota quota,
                                                             List<String> itemKeywords, String itemName, String itemFeature,
                                                             List<String> quotaKeywords, String quotaName, String quotaFeature) {
        double score = 0.0;
        double totalWeight = 0.0;
        
        // 方向1：从项目清单匹配到定额（权重0.7）
        if (!itemName.isEmpty() && quotaName != null && !quotaName.isEmpty()) {
            double nameScore = KeywordExtractor.calculateTextMatchScore(itemName, quotaName);
            score += nameScore * 0.4;
            totalWeight += 0.4;
        }
        
        if (!itemFeature.isEmpty() && quotaFeature != null && !quotaFeature.isEmpty()) {
            double featureScore = KeywordExtractor.calculateTextMatchScore(itemFeature, quotaFeature);
            score += featureScore * 0.3;
            totalWeight += 0.3;
        }
        
        // 方向2：从定额匹配到项目清单（权重0.3，使用缓存的关键词）
        if (quotaKeywords != null && !quotaKeywords.isEmpty()) {
            double keywordScore = KeywordExtractor.calculateSimilarity(itemKeywords, quotaKeywords);
            score += keywordScore * 0.3;
            totalWeight += 0.3;
        }
        
        // 归一化得分
        if (totalWeight > 0) {
            score = score / totalWeight;
        }
        
        return score;
    }
    
    /**
     * 计算双向匹配得分（原方法，保留以兼容）
     */
    private double calculateBidirectionalMatchScore(ProjectItem item, EnterpriseQuota quota, List<String> itemKeywords) {
        double score = 0.0;
        double totalWeight = 0.0;
        
        // 方向1：从项目清单匹配到定额（权重0.6）
        if (item.getItemName() != null && !item.getItemName().trim().isEmpty()) {
            double nameScore = KeywordExtractor.calculateTextMatchScore(item.getItemName(), quota.getQuotaName());
            score += nameScore * 0.4;
            totalWeight += 0.4;
        }
        
        if (item.getFeatureValue() != null && !item.getFeatureValue().trim().isEmpty() 
                && quota.getFeatureValue() != null && !quota.getFeatureValue().trim().isEmpty()) {
            double featureScore = KeywordExtractor.calculateTextMatchScore(item.getFeatureValue(), quota.getFeatureValue());
            score += featureScore * 0.3;
            totalWeight += 0.3;
        }
        
        // 方向2：从定额匹配到项目清单（权重0.3）
        List<String> quotaKeywords = new ArrayList<>();
        if (quota.getQuotaName() != null && !quota.getQuotaName().trim().isEmpty()) {
            quotaKeywords.addAll(KeywordExtractor.extractKeywords(quota.getQuotaName()));
        }
        if (quota.getFeatureValue() != null && !quota.getFeatureValue().trim().isEmpty()) {
            quotaKeywords.addAll(KeywordExtractor.extractKeywords(quota.getFeatureValue()));
        }
        
        if (!quotaKeywords.isEmpty()) {
            double keywordScore = KeywordExtractor.calculateSimilarity(itemKeywords, quotaKeywords);
            score += keywordScore * 0.3;
            totalWeight += 0.3;
        }
        
        // 归一化得分
        if (totalWeight > 0) {
            score = score / totalWeight;
        }
        
        return score;
    }
    
    /**
     * 匹配得分内部类
     */
    private static class MatchScore {
        EnterpriseQuota quota;
        double score;
        
        MatchScore(EnterpriseQuota quota, double score) {
            this.quota = quota;
            this.score = score;
        }
    }
    
    /**
     * 兼容旧版本的匹配方法（不使用版本ID）
     */
    @Transactional
    public int batchMatchQuotas() {
        return batchMatchQuotas(null);
    }
    
    @Transactional
    public void updateMatchedQuota(Long itemId, Long quotaId) {
        ProjectItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("项目清单不存在"));
        
        EnterpriseQuota quota = quotaRepository.findById(quotaId)
                .orElseThrow(() -> new RuntimeException("企业定额不存在"));
        
        // 清除多定额关联（如果存在）
        itemQuotaRepository.deleteByProjectItemId(itemId);
        
        item.setMatchedQuotaId(quota.getId());
        item.setMatchedQuotaCode(quota.getQuotaCode());
        item.setMatchedQuotaName(quota.getQuotaName());
        item.setMatchedQuotaFeatureValue(quota.getFeatureValue());
        item.setMatchedUnitPrice(quota.getUnitPrice());
        item.setMatchStatus(2); // 2 表示单定额手动修改
        
        if (item.getQuantity() != null && quota.getUnitPrice() != null) {
            item.setTotalPrice(item.getQuantity().multiply(quota.getUnitPrice()));
        }
        
        itemRepository.save(item);
        
        // 收集学习数据（手动修改，权重更高）
        try {
            learningService.collectMatchData(item, quota, 1.0, 2);
        } catch (Exception e) {
            // 学习失败不影响主流程
        }
    }
    
    @Transactional
    public void updateItemPrice(Long itemId, BigDecimal unitPrice) {
        ProjectItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("项目清单不存在"));
        
        // 清除多定额关联（如果存在）
        itemQuotaRepository.deleteByProjectItemId(itemId);
        
        item.setMatchedUnitPrice(unitPrice);
        item.setMatchStatus(2); // 2 表示单定额手动修改
        
        if (item.getQuantity() != null && unitPrice != null) {
            item.setTotalPrice(item.getQuantity().multiply(unitPrice));
        }
        
        itemRepository.save(item);
    }
    
    /**
     * 添加定额到清单项（支持多定额）
     */
    @Transactional
    public void addQuotaToItem(Long itemId, Long quotaId) {
        ProjectItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("项目清单不存在"));
        
        EnterpriseQuota quota = quotaRepository.findById(quotaId)
                .orElseThrow(() -> new RuntimeException("企业定额不存在"));
        
        // 检查是否已存在
        List<ProjectItemQuota> existing = itemQuotaRepository.findByProjectItemIdOrderBySortOrderAsc(itemId);
        boolean exists = existing.stream().anyMatch(eq -> eq.getQuotaId().equals(quotaId));
        if (exists) {
            throw new RuntimeException("该定额已添加");
        }
        
        // 创建新的关联
        ProjectItemQuota itemQuota = new ProjectItemQuota();
        itemQuota.setProjectItemId(itemId);
        itemQuota.setQuotaId(quota.getId());
        itemQuota.setQuotaCode(quota.getQuotaCode());
        itemQuota.setQuotaName(quota.getQuotaName());
        itemQuota.setQuotaFeatureValue(quota.getFeatureValue());
        itemQuota.setUnitPrice(quota.getUnitPrice());
        itemQuota.setSortOrder(existing.size());
        
        itemQuotaRepository.save(itemQuota);
        
        // 更新清单项状态和价格
        item.setMatchStatus(3); // 3 表示多定额手动匹配
        calculateAndUpdateTotalPrice(item);
        itemRepository.save(item);
    }
    
    /**
     * 从清单项中移除定额
     */
    @Transactional
    public void removeQuotaFromItem(Long itemId, Long itemQuotaId) {
        ProjectItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("项目清单不存在"));
        
        itemQuotaRepository.deleteById(itemQuotaId);
        
        // 重新计算总价
        calculateAndUpdateTotalPrice(item);
        itemRepository.save(item);
    }
    
    /**
     * 获取清单项的所有定额
     */
    public List<ProjectItemQuota> getItemQuotas(Long itemId) {
        return itemQuotaRepository.findByProjectItemIdOrderBySortOrderAsc(itemId);
    }
    
    /**
     * 清空清单项的所有定额关联
     */
    @Transactional
    public void clearItemQuotas(Long itemId) {
        itemQuotaRepository.deleteByProjectItemId(itemId);
        ProjectItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("项目清单不存在"));
        item.setMatchStatus(0);
        item.setMatchedUnitPrice(null);
        item.setTotalPrice(null);
        itemRepository.save(item);
    }
    
    /**
     * 计算并更新清单项的总价（多定额求和）
     */
    private void calculateAndUpdateTotalPrice(ProjectItem item) {
        List<ProjectItemQuota> quotas = itemQuotaRepository.findByProjectItemIdOrderBySortOrderAsc(item.getId());
        
        if (quotas.isEmpty()) {
            item.setMatchedUnitPrice(null);
            item.setTotalPrice(null);
            return;
        }
        
        // 计算所有定额单价之和
        BigDecimal totalUnitPrice = quotas.stream()
                .map(ProjectItemQuota::getUnitPrice)
                .filter(price -> price != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        item.setMatchedUnitPrice(totalUnitPrice);
        
        // 计算合价
        if (item.getQuantity() != null && totalUnitPrice != null) {
            item.setTotalPrice(item.getQuantity().multiply(totalUnitPrice));
        } else {
            item.setTotalPrice(null);
        }
    }
}

