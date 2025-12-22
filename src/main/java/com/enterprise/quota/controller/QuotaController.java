package com.enterprise.quota.controller;

import com.enterprise.quota.entity.EnterpriseQuota;
import com.enterprise.quota.entity.ProjectItem;
import com.enterprise.quota.entity.ProjectItemQuota;
import com.enterprise.quota.repository.EnterpriseQuotaRepository;
import com.enterprise.quota.repository.ProjectItemRepository;
import com.enterprise.quota.service.ExcelExportService;
import com.enterprise.quota.service.ExcelImportService;
import com.enterprise.quota.service.QuotaMatchingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private ExcelImportService importService;
    
    @Autowired
    private QuotaMatchingService matchingService;
    
    @Autowired
    private ExcelExportService exportService;
    
    @PostMapping("/import-quotas")
    public ResponseEntity<Map<String, Object>> importQuotas(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<EnterpriseQuota> quotas = importService.importEnterpriseQuotas(file);
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
    public ResponseEntity<Map<String, Object>> matchQuotas() {
        Map<String, Object> result = new HashMap<>();
        try {
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
    
    @GetMapping("/quotas")
    public ResponseEntity<List<EnterpriseQuota>> getAllQuotas() {
        return ResponseEntity.ok(quotaRepository.findAll());
    }
    
    @GetMapping("/quotas/search")
    public ResponseEntity<List<EnterpriseQuota>> searchQuotas(@RequestParam String keyword) {
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
            itemRepository.deleteAll();
            quotaRepository.deleteAll();
            result.put("success", true);
            result.put("message", "清空成功");
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
}

