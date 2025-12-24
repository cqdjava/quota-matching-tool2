package com.enterprise.quota.service;

import com.enterprise.quota.entity.EnterpriseQuota;
import com.enterprise.quota.entity.ProjectItem;
import com.enterprise.quota.entity.ProjectItemQuota;
import com.enterprise.quota.repository.EnterpriseQuotaRepository;
import com.enterprise.quota.repository.ProjectItemRepository;
import com.enterprise.quota.repository.ProjectItemQuotaRepository;
import com.enterprise.quota.util.KeywordExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuotaMatchingService {
    
    @Autowired
    private EnterpriseQuotaRepository quotaRepository;
    
    @Autowired
    private ProjectItemRepository itemRepository;
    
    @Autowired
    private ProjectItemQuotaRepository itemQuotaRepository;
    
    @Transactional
    public int batchMatchQuotas(Long versionId) {
        List<ProjectItem> items = itemRepository.findAll();
        int matchedCount = 0;
        
        // 获取所有定额（如果指定了版本，则只获取该版本的定额）
        List<EnterpriseQuota> allQuotas;
        if (versionId != null) {
            allQuotas = quotaRepository.findByVersionId(versionId);
        } else {
            allQuotas = quotaRepository.findAll();
        }
        
        for (ProjectItem item : items) {
            // 跳过手动修改（单定额）和多定额匹配的项目
            if (item.getMatchStatus() != null && (item.getMatchStatus() == 2 || item.getMatchStatus() == 3)) {
                continue;
            }
            
            // 只有单位列有数据才进行匹配
            if (item.getUnit() == null || item.getUnit().trim().isEmpty()) {
                item.setMatchStatus(0);
                itemRepository.save(item);
                continue;
            }
            
            // 使用双向匹配算法找到最佳匹配
            EnterpriseQuota matchedQuota = findBestMatchWithBidirectionalMatching(item, allQuotas);
            
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
                
                itemRepository.save(item);
                matchedCount++;
            } else {
                item.setMatchStatus(0);
                itemRepository.save(item);
            }
        }
        
        return matchedCount;
    }
    
    /**
     * 使用双向匹配算法找到最佳匹配
     * 双向匹配：既从项目清单匹配到定额，也从定额匹配到项目清单，选择匹配度最高的
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
     * 计算双向匹配得分
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

