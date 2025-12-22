package com.enterprise.quota.service;

import com.enterprise.quota.entity.EnterpriseQuota;
import com.enterprise.quota.entity.ProjectItem;
import com.enterprise.quota.entity.ProjectItemQuota;
import com.enterprise.quota.repository.EnterpriseQuotaRepository;
import com.enterprise.quota.repository.ProjectItemRepository;
import com.enterprise.quota.repository.ProjectItemQuotaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class QuotaMatchingService {
    
    @Autowired
    private EnterpriseQuotaRepository quotaRepository;
    
    @Autowired
    private ProjectItemRepository itemRepository;
    
    @Autowired
    private ProjectItemQuotaRepository itemQuotaRepository;
    
    @Transactional
    public int batchMatchQuotas() {
        List<ProjectItem> items = itemRepository.findAll();
        int matchedCount = 0;
        
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
            
            EnterpriseQuota matchedQuota = findBestMatch(item);
            
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
    
    private EnterpriseQuota findBestMatch(ProjectItem item) {
        if (item.getItemName() != null && !item.getItemName().trim().isEmpty()) {
            List<EnterpriseQuota> nameMatches = quotaRepository.findByQuotaNameContaining(item.getItemName());
            if (!nameMatches.isEmpty()) {
                return nameMatches.get(0);
            }
        }
        
        if (item.getFeatureValue() != null && !item.getFeatureValue().trim().isEmpty()) {
            List<EnterpriseQuota> featureMatches = quotaRepository.findByFeatureValueContaining(item.getFeatureValue());
            if (!featureMatches.isEmpty()) {
                return featureMatches.get(0);
            }
        }
        
        if (item.getItemName() != null && !item.getItemName().trim().isEmpty()) {
            String keyword = item.getItemName();
            List<EnterpriseQuota> keywordMatches = quotaRepository.findByKeyword(keyword);
            if (!keywordMatches.isEmpty()) {
                return keywordMatches.get(0);
            }
        }
        
        return null;
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

