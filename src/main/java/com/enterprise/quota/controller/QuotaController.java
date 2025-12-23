package com.enterprise.quota.controller;

import com.enterprise.quota.entity.EnterpriseQuota;
import com.enterprise.quota.entity.ProjectItem;
import com.enterprise.quota.entity.ProjectItemQuota;
import com.enterprise.quota.entity.QuotaVersion;
import com.enterprise.quota.repository.EnterpriseQuotaRepository;
import com.enterprise.quota.repository.ProjectItemQuotaRepository;
import com.enterprise.quota.repository.ProjectItemRepository;
import com.enterprise.quota.repository.QuotaVersionRepository;
import com.enterprise.quota.service.ExcelExportService;
import com.enterprise.quota.service.ExcelImportService;
import com.enterprise.quota.service.QuotaMatchingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quota")
@CrossOrigin(origins = "*")
public class QuotaController {
    
    @Autowired
    private EnterpriseQuotaRepository quotaRepository;
    
    @Autowired
    private ProjectItemRepository itemRepository;
    
    @Autowired
    private ProjectItemQuotaRepository itemQuotaRepository;
    
    @Autowired
    private ExcelImportService importService;
    
    @Autowired
    private QuotaMatchingService matchingService;
    
    @Autowired
    private ExcelExportService exportService;
    
    @Autowired
    private QuotaVersionRepository versionRepository;
    
    @PostMapping("/import-quotas")
    public ResponseEntity<Map<String, Object>> importQuotas(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "versionId", required = false) Long versionId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<EnterpriseQuota> quotas = importService.importEnterpriseQuotas(file);
            // 暂时忽略版本ID，因为EnterpriseQuota没有versionId字段
            quotaRepository.saveAll(quotas);
            result.put("success", true);
            result.put("message", "导入成功，共导入 " + quotas.size() + " 条企业定额数据");
            result.put("count", quotas.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "导入失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    @PostMapping("/import-items")
    public ResponseEntity<Map<String, Object>> importItems(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<ProjectItem> items = importService.importProjectItems(file);
            itemRepository.saveAll(items);
            result.put("success", true);
            result.put("message", "导入成功，共导入 " + items.size() + " 条项目清单数据");
            result.put("count", items.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "导入失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    @PostMapping("/match")
    public ResponseEntity<Map<String, Object>> matchQuotas(
            @RequestParam(value = "versionId", required = false) Long versionId) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 暂时忽略版本ID，因为匹配服务不支持版本
            int matchedCount = matchingService.batchMatchQuotas();
            result.put("success", true);
            result.put("message", "匹配完成，共匹配 " + matchedCount + " 条项目清单");
            result.put("matchedCount", matchedCount);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "匹配失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    @GetMapping("/items")
    public ResponseEntity<List<ProjectItem>> getAllItems() {
        return ResponseEntity.ok(itemRepository.findAll());
    }
    
    /**
     * 新增项目清单（基础信息）
     */
    @PostMapping("/items")
    public ResponseEntity<Map<String, Object>> createItem(@RequestBody ProjectItem request) {
        Map<String, Object> result = new HashMap<>();
        try {
            ProjectItem item = new ProjectItem();
            item.setItemCode(request.getItemCode());
            item.setItemName(request.getItemName());
            item.setFeatureValue(request.getFeatureValue());
            item.setUnit(request.getUnit());
            item.setQuantity(request.getQuantity());
            
            // 新增清单默认未匹配
            item.setMatchStatus(0);
            item.setMatchedQuotaId(null);
            item.setMatchedQuotaCode(null);
            item.setMatchedQuotaName(null);
            item.setMatchedQuotaFeatureValue(null);
            item.setMatchedUnitPrice(null);
            item.setTotalPrice(null);
            
            ProjectItem saved = itemRepository.save(item);
            result.put("success", true);
            result.put("message", "新增清单成功");
            result.put("item", saved);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "新增清单失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 更新项目清单（基础信息）
     */
    @PutMapping("/items/{itemId}")
    public ResponseEntity<Map<String, Object>> updateItem(
            @PathVariable Long itemId, @RequestBody Map<String, Object> updates) {
        Map<String, Object> result = new HashMap<>();
        try {
            ProjectItem item = itemRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("项目清单不存在"));
            
            // 只更新传入的字段（支持部分更新）
            if (updates.containsKey("itemCode")) {
                item.setItemCode((String) updates.get("itemCode"));
            }
            if (updates.containsKey("itemName")) {
                String itemName = (String) updates.get("itemName");
                if (itemName == null || itemName.trim().isEmpty()) {
                    result.put("success", false);
                    result.put("message", "清单名称不能为空");
                    return ResponseEntity.badRequest().body(result);
                }
                item.setItemName(itemName);
            }
            if (updates.containsKey("featureValue")) {
                item.setFeatureValue((String) updates.get("featureValue"));
            }
            if (updates.containsKey("unit")) {
                item.setUnit((String) updates.get("unit"));
            }
            if (updates.containsKey("quantity")) {
                Object qtyObj = updates.get("quantity");
                if (qtyObj != null) {
                    BigDecimal quantity;
                    if (qtyObj instanceof Number) {
                        quantity = BigDecimal.valueOf(((Number) qtyObj).doubleValue());
                    } else {
                        quantity = new BigDecimal(qtyObj.toString());
                    }
                    item.setQuantity(quantity);
                }
            }
            if (updates.containsKey("remark")) {
                item.setRemark((String) updates.get("remark"));
            }
            
            // 如果已经有匹配单价，则根据数量重新计算合价
            if (item.getMatchedUnitPrice() != null && item.getQuantity() != null) {
                item.setTotalPrice(item.getQuantity().multiply(item.getMatchedUnitPrice()));
            }
            
            ProjectItem saved = itemRepository.save(item);
            result.put("success", true);
            result.put("message", "更新清单成功");
            result.put("item", saved);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "更新清单失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 删除项目清单
     */
    @DeleteMapping("/items/{itemId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteItem(@PathVariable Long itemId) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!itemRepository.existsById(itemId)) {
                result.put("success", false);
                result.put("message", "项目清单不存在");
                return ResponseEntity.badRequest().body(result);
            }
            
            // 先删除关联的定额关系
            try {
                itemQuotaRepository.deleteByProjectItemId(itemId);
            } catch (Exception e) {
                // 如果删除关联关系失败，记录日志但继续删除清单
                System.err.println("删除清单关联关系失败: " + e.getMessage());
            }
            
            // 再删除清单本身
            itemRepository.deleteById(itemId);
            
            result.put("success", true);
            result.put("message", "删除成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "删除失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    @GetMapping("/quotas")
    public ResponseEntity<List<EnterpriseQuota>> getAllQuotas(
            @RequestParam(value = "versionId", required = false) Long versionId) {
        // 暂时忽略版本ID，返回所有定额
        return ResponseEntity.ok(quotaRepository.findAll());
    }
    
    @GetMapping("/quotas/search")
    public ResponseEntity<List<EnterpriseQuota>> searchQuotas(
            @RequestParam String keyword,
            @RequestParam(value = "versionId", required = false) Long versionId) {
        // 暂时忽略版本ID，返回所有匹配的定额
        return ResponseEntity.ok(quotaRepository.findByKeyword(keyword));
    }
    
    @PutMapping("/items/{itemId}/match")
    public ResponseEntity<Map<String, Object>> updateMatchedQuota(
            @PathVariable Long itemId, @RequestParam Long quotaId) {
        Map<String, Object> result = new HashMap<>();
        try {
            matchingService.updateMatchedQuota(itemId, quotaId);
            result.put("success", true);
            result.put("message", "更新成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "更新失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    @PutMapping("/items/{itemId}/price")
    public ResponseEntity<Map<String, Object>> updateItemPrice(
            @PathVariable Long itemId, @RequestParam BigDecimal unitPrice) {
        Map<String, Object> result = new HashMap<>();
        try {
            matchingService.updateItemPrice(itemId, unitPrice);
            result.put("success", true);
            result.put("message", "更新成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "更新失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportMatchedItems() {
        try {
            byte[] excelData = exportService.exportMatchedItems();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "匹配结果.xlsx");
            return ResponseEntity.ok().headers(headers).body(excelData);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearAll() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 仅清理项目清单及其匹配关系，不删除企业定额数据
            itemQuotaRepository.deleteAll();
            itemRepository.deleteAll();
            result.put("success", true);
            result.put("message", "已清理所有项目清单数据（企业定额数据已保留）");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "清空失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 添加定额到清单项（支持多定额）
     */
    @PostMapping("/items/{itemId}/quotas/{quotaId}")
    public ResponseEntity<Map<String, Object>> addQuotaToItem(
            @PathVariable Long itemId, @PathVariable Long quotaId) {
        Map<String, Object> result = new HashMap<>();
        try {
            matchingService.addQuotaToItem(itemId, quotaId);
            result.put("success", true);
            result.put("message", "添加成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "添加失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 获取清单项的所有定额
     */
    @GetMapping("/items/{itemId}/quotas")
    public ResponseEntity<List<ProjectItemQuota>> getItemQuotas(@PathVariable Long itemId) {
        return ResponseEntity.ok(matchingService.getItemQuotas(itemId));
    }
    
    /**
     * 从清单项中移除定额
     */
    @DeleteMapping("/items/{itemId}/quotas/{itemQuotaId}")
    public ResponseEntity<Map<String, Object>> removeQuotaFromItem(
            @PathVariable Long itemId, @PathVariable Long itemQuotaId) {
        Map<String, Object> result = new HashMap<>();
        try {
            matchingService.removeQuotaFromItem(itemId, itemQuotaId);
            result.put("success", true);
            result.put("message", "移除成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "移除失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 清空清单项的所有定额关联
     */
    @DeleteMapping("/items/{itemId}/quotas")
    public ResponseEntity<Map<String, Object>> clearItemQuotas(@PathVariable Long itemId) {
        Map<String, Object> result = new HashMap<>();
        try {
            matchingService.clearItemQuotas(itemId);
            result.put("success", true);
            result.put("message", "清空成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "清空失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    // ==================== 定额管理模块接口 ====================
    
    /**
     * 创建企业定额
     */
    @PostMapping("/quotas")
    public ResponseEntity<Map<String, Object>> createQuota(@RequestBody EnterpriseQuota quota) {
        Map<String, Object> result = new HashMap<>();
        try {
            EnterpriseQuota saved = quotaRepository.save(quota);
            result.put("success", true);
            result.put("message", "创建成功");
            result.put("quota", saved);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "创建失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 更新企业定额
     */
    @PutMapping("/quotas/{quotaId}")
    public ResponseEntity<Map<String, Object>> updateQuota(
            @PathVariable Long quotaId, @RequestBody EnterpriseQuota quota) {
        Map<String, Object> result = new HashMap<>();
        try {
            EnterpriseQuota existing = quotaRepository.findById(quotaId)
                    .orElseThrow(() -> new RuntimeException("企业定额不存在"));
            
            existing.setQuotaCode(quota.getQuotaCode());
            existing.setQuotaName(quota.getQuotaName());
            existing.setFeatureValue(quota.getFeatureValue());
            existing.setUnit(quota.getUnit());
            existing.setUnitPrice(quota.getUnitPrice());
            existing.setLaborCost(quota.getLaborCost());
            existing.setMaterialCost(quota.getMaterialCost());
            existing.setMachineCost(quota.getMachineCost());
            existing.setRemark(quota.getRemark());
            
            EnterpriseQuota saved = quotaRepository.save(existing);
            result.put("success", true);
            result.put("message", "更新成功");
            result.put("quota", saved);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "更新失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 删除企业定额
     */
    @DeleteMapping("/quotas/{quotaId}")
    public ResponseEntity<Map<String, Object>> deleteQuota(@PathVariable Long quotaId) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!quotaRepository.existsById(quotaId)) {
                result.put("success", false);
                result.put("message", "企业定额不存在");
                return ResponseEntity.badRequest().body(result);
            }
            
            quotaRepository.deleteById(quotaId);
            result.put("success", true);
            result.put("message", "删除成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 批量删除企业定额
     */
    @DeleteMapping("/quotas/batch")
    public ResponseEntity<Map<String, Object>> deleteQuotas(@RequestBody List<Long> quotaIds) {
        Map<String, Object> result = new HashMap<>();
        try {
            quotaRepository.deleteAllById(quotaIds);
            result.put("success", true);
            result.put("message", "批量删除成功，共删除 " + quotaIds.size() + " 条记录");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "批量删除失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 获取单个企业定额
     */
    @GetMapping("/quotas/{quotaId}")
    public ResponseEntity<EnterpriseQuota> getQuota(@PathVariable Long quotaId) {
        return quotaRepository.findById(quotaId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 导出企业定额数据
     */
    @GetMapping("/quotas/export")
    public ResponseEntity<byte[]> exportQuotas() {
        try {
            byte[] excelData = exportService.exportQuotas();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "企业定额数据.xlsx");
            return ResponseEntity.ok().headers(headers).body(excelData);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // ==================== 版本管理模块接口 ====================
    
    /**
     * 获取所有版本
     */
    @GetMapping("/versions")
    public ResponseEntity<List<QuotaVersion>> getAllVersions() {
        return ResponseEntity.ok(versionRepository.findAllByOrderByCreateTimeDesc());
    }
    
    /**
     * 创建版本
     */
    @PostMapping("/versions")
    public ResponseEntity<Map<String, Object>> createVersion(@RequestBody QuotaVersion version) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (version.getVersionName() == null || version.getVersionName().trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "版本名称不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            
            // 检查版本名称是否已存在
            QuotaVersion existing = versionRepository.findByVersionName(version.getVersionName());
            if (existing != null) {
                result.put("success", false);
                result.put("message", "版本名称已存在");
                return ResponseEntity.badRequest().body(result);
            }
            
            QuotaVersion saved = versionRepository.save(version);
            result.put("success", true);
            result.put("message", "创建成功");
            result.put("version", saved);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "创建失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 更新版本
     */
    @PutMapping("/versions/{versionId}")
    public ResponseEntity<Map<String, Object>> updateVersion(
            @PathVariable Long versionId, @RequestBody QuotaVersion version) {
        Map<String, Object> result = new HashMap<>();
        try {
            QuotaVersion existing = versionRepository.findById(versionId)
                    .orElseThrow(() -> new RuntimeException("版本不存在"));
            
            if (version.getVersionName() != null && !version.getVersionName().trim().isEmpty()) {
                // 检查新版本名称是否与其他版本冲突
                QuotaVersion duplicate = versionRepository.findByVersionName(version.getVersionName());
                if (duplicate != null && !duplicate.getId().equals(versionId)) {
                    result.put("success", false);
                    result.put("message", "版本名称已存在");
                    return ResponseEntity.badRequest().body(result);
                }
                existing.setVersionName(version.getVersionName());
            }
            
            if (version.getDescription() != null) {
                existing.setDescription(version.getDescription());
            }
            
            QuotaVersion saved = versionRepository.save(existing);
            result.put("success", true);
            result.put("message", "更新成功");
            result.put("version", saved);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "更新失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 删除版本（暂时只删除版本本身，不删除定额）
     */
    @DeleteMapping("/versions/{versionId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteVersion(@PathVariable Long versionId) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!versionRepository.existsById(versionId)) {
                result.put("success", false);
                result.put("message", "版本不存在");
                return ResponseEntity.badRequest().body(result);
            }
            
            // 由于EnterpriseQuota没有versionId字段，暂时只删除版本本身
            versionRepository.deleteById(versionId);
            
            result.put("success", true);
            result.put("message", "删除成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 获取单个版本
     */
    @GetMapping("/versions/{versionId}")
    public ResponseEntity<QuotaVersion> getVersion(@PathVariable Long versionId) {
        return versionRepository.findById(versionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 导入定额到指定版本
     */
    @PostMapping("/versions/{versionId}/import-quotas")
    public ResponseEntity<Map<String, Object>> importQuotasToVersion(
            @PathVariable Long versionId,
            @RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!versionRepository.existsById(versionId)) {
                result.put("success", false);
                result.put("message", "版本不存在");
                return ResponseEntity.badRequest().body(result);
            }
            
            List<EnterpriseQuota> quotas = importService.importEnterpriseQuotas(file);
            // 由于EnterpriseQuota没有versionId字段，暂时只导入定额，不关联版本
            quotaRepository.saveAll(quotas);
            
            result.put("success", true);
            result.put("message", "导入成功，共导入 " + quotas.size() + " 条企业定额数据");
            result.put("count", quotas.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "导入失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 批量删除版本
     */
    @DeleteMapping("/versions/batch")
    @Transactional
    public ResponseEntity<Map<String, Object>> batchDeleteVersions(@RequestBody List<Long> versionIds) {
        Map<String, Object> result = new HashMap<>();
        try {
            for (Long versionId : versionIds) {
                // 由于EnterpriseQuota没有versionId字段，暂时只删除版本本身
                versionRepository.deleteById(versionId);
            }
            result.put("success", true);
            result.put("message", "批量删除成功，共删除 " + versionIds.size() + " 个版本");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "批量删除失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
}

