package com.enterprise.quota.service;

import com.enterprise.quota.entity.EnterpriseQuota;
import com.enterprise.quota.entity.ProjectItem;
import com.enterprise.quota.repository.EnterpriseQuotaRepository;
import com.enterprise.quota.repository.ProjectItemRepository;
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
    
    @Transactional
    public int batchMatchQuotas() {
        List<ProjectItem> items = itemRepository.findAll();
        int matchedCount = 0;
        
        for (ProjectItem item : items) {
            if (item.getMatchStatus() != null && item.getMatchStatus() == 2) {
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
        
        item.setMatchedQuotaId(quota.getId());
        item.setMatchedQuotaCode(quota.getQuotaCode());
        item.setMatchedQuotaName(quota.getQuotaName());
        item.setMatchedQuotaFeatureValue(quota.getFeatureValue());
        item.setMatchedUnitPrice(quota.getUnitPrice());
        item.setMatchStatus(2);
        
        if (item.getQuantity() != null && quota.getUnitPrice() != null) {
            item.setTotalPrice(item.getQuantity().multiply(quota.getUnitPrice()));
        }
        
        itemRepository.save(item);
    }
    
    @Transactional
    public void updateItemPrice(Long itemId, BigDecimal unitPrice) {
        ProjectItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("项目清单不存在"));
        
        item.setMatchedUnitPrice(unitPrice);
        item.setMatchStatus(2);
        
        if (item.getQuantity() != null && unitPrice != null) {
            item.setTotalPrice(item.getQuantity().multiply(unitPrice));
        }
        
        itemRepository.save(item);
    }
}

