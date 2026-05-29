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
import com.enterprise.quota.service.MatchingLearningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/quota")
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
    
    @Autowired
    private MatchingLearningService learningService;
    
    @PostMapping("/import-quotas")
    public ResponseEntity<Map<String, Object>> importQuotas(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "versionId", required = false) Long versionId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<EnterpriseQuota> quotas = importService.importEnterpriseQuotas(file, versionId);
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
    public ResponseEntity<Map<String, Object>> importItems(@RequestParam("file") MultipartFile file, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = (Long) session.getAttribute("userId");
            if (userId == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
            }
            
            List<ProjectItem> items = importService.importProjectItems(file);
            // 为导入的项目清单设置用户ID和排序字段
            List<ProjectItem> existingItems = itemRepository.findByUserIdOrderBySortOrderAsc(userId);
            Integer maxSortOrder = existingItems.stream()
                    .map(ProjectItem::getSortOrder)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(0);
            
            for (int i = 0; i < items.size(); i++) {
                ProjectItem item = items.get(i);
                item.setUserId(userId);
                // 为导入的项目按顺序分配sortOrder值
                item.setSortOrder(maxSortOrder + i + 1);
            }
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
            @RequestParam(value = "versionId", required = false) Long versionId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = (Long) session.getAttribute("userId");
            if (userId == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
            }
            
            int matchedCount = matchingService.batchMatchQuotasForUser(userId, versionId);
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
    public ResponseEntity<List<ProjectItem>> getAllItems(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        // 只返回当前用户的项目清单，按排序字段升序排列
        List<ProjectItem> items = itemRepository.findByUserIdOrderBySortOrderAsc(userId);
        
        // 添加调试信息
        System.out.println("=== 获取项目清单数据 ===");
        System.out.println("用户ID: " + userId);
        System.out.println("返回项目数量: " + items.size());
        for (int i = 0; i < items.size(); i++) {
            ProjectItem item = items.get(i);
            System.out.println("项目 " + i + ": ID=" + item.getId() + 
                             ", 名称=" + item.getItemName() + 
                             ", sortOrder=" + item.getSortOrder());
        }
        
        return ResponseEntity.ok(items);
    }
    
    /**
     * 在指定位置插入项目清单
     */
    @PostMapping("/items/insert")
    @Transactional
    public ResponseEntity<Map<String, Object>> insertItem(
            @RequestBody Map<String, Object> request, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            System.out.println("=== 开始处理插入请求 ===");
            System.out.println("收到完整请求: " + request);
            
            Long userId = (Long) session.getAttribute("userId");
            System.out.println("用户ID: " + userId);
            if (userId == null) {
                System.out.println("用户未登录");
                result.put("success", false);
                result.put("message", "请先登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
            }
            
            // 获取插入位置参数
            Object insertAfterIndexObj = request.get("insertAfterIndex");
            Integer insertAfterIndex = null;
            if (insertAfterIndexObj != null) {
                if (insertAfterIndexObj instanceof Integer) {
                    insertAfterIndex = (Integer) insertAfterIndexObj;
                } else if (insertAfterIndexObj instanceof Number) {
                    insertAfterIndex = ((Number) insertAfterIndexObj).intValue();
                } else {
                    try {
                        insertAfterIndex = Integer.parseInt(insertAfterIndexObj.toString());
                    } catch (NumberFormatException e) {
                        System.out.println("插入位置转换失败: " + insertAfterIndexObj);
                    }
                }
            }
            System.out.println("插入位置: " + insertAfterIndex);
            
            // 从请求中获取项目数据
            Object itemDataObj = request.get("item");
            System.out.println("项目数据对象类型: " + (itemDataObj != null ? itemDataObj.getClass().getName() : "null"));
            System.out.println("项目数据对象值: " + itemDataObj);
            
            Map<String, Object> itemData = null;
            if (itemDataObj instanceof Map) {
                itemData = (Map<String, Object>) itemDataObj;
            } else {
                System.out.println("项目数据不是Map类型");
                result.put("success", false);
                result.put("message", "项目数据格式错误");
                return ResponseEntity.badRequest().body(result);
            }
            
            System.out.println("转换后的项目数据: " + itemData);
            
            if (itemData == null) {
                System.out.println("项目数据为null");
                result.put("success", false);
                result.put("message", "项目数据不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            
            // 创建新项目
            ProjectItem newItem = new ProjectItem();
            newItem.setItemCode((String) itemData.get("itemCode"));
            newItem.setItemName((String) itemData.get("itemName"));
            newItem.setFeatureValue((String) itemData.get("featureValue"));
            newItem.setUnit((String) itemData.get("unit"));
            newItem.setRemark((String) itemData.get("remark"));
            
            // 处理数量字段
            Object quantityObj = itemData.get("quantity");
            if (quantityObj != null) {
                if (quantityObj instanceof Number) {
                    newItem.setQuantity(BigDecimal.valueOf(((Number) quantityObj).doubleValue()));
                } else {
                    try {
                        newItem.setQuantity(new BigDecimal(quantityObj.toString()));
                    } catch (NumberFormatException e) {
                        newItem.setQuantity(BigDecimal.ZERO);
                    }
                }
            } else {
                newItem.setQuantity(BigDecimal.ZERO);
            }
            newItem.setUserId(userId);
            
            // 设置默认匹配状态
            newItem.setMatchStatus(0);
            newItem.setMatchedQuotaId(null);
            newItem.setMatchedQuotaCode(null);
            newItem.setMatchedQuotaName(null);
            newItem.setMatchedQuotaFeatureValue(null);
            newItem.setMatchedUnitPrice(null);
            newItem.setTotalPrice(null);
            
            // 获取当前用户的项目清单并按排序字段排列
            List<ProjectItem> existingItems = itemRepository.findByUserIdOrderBySortOrderAsc(userId);
            System.out.println("用户现有项目数量: " + existingItems.size());
            System.out.println("请求的插入位置: " + insertAfterIndex);
            
            if (insertAfterIndex == null || insertAfterIndex < 0) {
                System.out.println("插入位置为null或负数，将在末尾添加");
                // 如果没有指定插入位置，在末尾添加
                Integer maxSortOrder = existingItems.stream()
                        .map(ProjectItem::getSortOrder)
                        .filter(Objects::nonNull)
                        .max(Integer::compareTo)
                        .orElse(0);
                newItem.setSortOrder(maxSortOrder + 1);
            } else {
                System.out.println("进入指定位置插入逻辑");
                System.out.println("现有项目数量: " + existingItems.size());
                System.out.println("请求插入索引: " + insertAfterIndex);
                
                // 打印所有现有项目的详细信息
                System.out.println("=== 现有项目详细信息 ===");
                for (int i = 0; i < existingItems.size(); i++) {
                    ProjectItem item = existingItems.get(i);
                    System.out.println("索引 " + i + ": ID=" + item.getId() + 
                                     ", 名称=" + item.getItemName() + 
                                     ", sortOrder=" + item.getSortOrder() +
                                     ", itemCode=" + item.getItemCode());
                }
                System.out.println("=======================");
                
                // 确保插入位置有效
                int validInsertIndex = Math.min(insertAfterIndex, existingItems.size() - 1);
                validInsertIndex = Math.max(validInsertIndex, -1);
                System.out.println("原始插入索引: " + insertAfterIndex + ", 有效插入索引: " + validInsertIndex);
                
                if (validInsertIndex == -1) {
                    System.out.println("插入到开头位置");
                    // 插入到开头
                    newItem.setSortOrder(0);
                    // 将所有现有项目的排序值增加1
                    for (ProjectItem item : existingItems) {
                        item.setSortOrder(item.getSortOrder() + 1);
                    }
                    itemRepository.saveAll(existingItems);
                } else {
                    // 插入到指定位置之后
                    ProjectItem afterItem = existingItems.get(validInsertIndex);
                    System.out.println("参考项目: ID=" + afterItem.getId() + ", sortOrder=" + afterItem.getSortOrder());
                    
                    // 处理sortOrder为null的情况
                    int afterItemSortOrder = afterItem.getSortOrder() != null ? afterItem.getSortOrder() : 0;
                    int newSortOrder = afterItemSortOrder + 1;
                    newItem.setSortOrder(newSortOrder);
                    
                    System.out.println("新项目排序值: " + newSortOrder);
                    
                    // 将指定位置之后的所有项目的排序值增加1
                    for (int i = validInsertIndex + 1; i < existingItems.size(); i++) {
                        ProjectItem item = existingItems.get(i);
                        Integer itemSortOrder = item.getSortOrder();
                        if (itemSortOrder != null && itemSortOrder >= newSortOrder) {
                            item.setSortOrder(itemSortOrder + 1);
                            System.out.println("更新项目ID=" + item.getId() + "的排序值为" + (itemSortOrder + 1));
                        }
                    }
                    itemRepository.saveAll(existingItems);
                }
            }
            
            // 保存新项目
            ProjectItem saved = itemRepository.save(newItem);
            result.put("success", true);
            result.put("message", "插入项目成功");
            result.put("item", saved);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            System.err.println("插入项目时发生错误:");
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "插入项目失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 新增项目清单（基础信息）
     */
    @PostMapping("/items")
    public ResponseEntity<Map<String, Object>> createItem(@RequestBody ProjectItem request, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = (Long) session.getAttribute("userId");
            if (userId == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
            }
            
            ProjectItem item = new ProjectItem();
            item.setItemCode(request.getItemCode());
            item.setItemName(request.getItemName());
            item.setFeatureValue(request.getFeatureValue());
            item.setUnit(request.getUnit());
            item.setQuantity(request.getQuantity());
            item.setUserId(userId); // 设置用户ID
            
            // 新增清单默认未匹配
            item.setMatchStatus(0);
            item.setMatchedQuotaId(null);
            item.setMatchedQuotaCode(null);
            item.setMatchedQuotaName(null);
            item.setMatchedQuotaFeatureValue(null);
            item.setMatchedUnitPrice(null);
            item.setTotalPrice(null);
            
            // 设置排序字段为最大值+1（添加到末尾）
            Integer maxSortOrder = itemRepository.findByUserIdOrderBySortOrderAsc(userId)
                    .stream()
                    .map(ProjectItem::getSortOrder)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(0);
            item.setSortOrder(maxSortOrder + 1);
            
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
            @PathVariable Long itemId, @RequestBody Map<String, Object> updates, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = (Long) session.getAttribute("userId");
            if (userId == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
            }
            
            ProjectItem item = itemRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("项目清单不存在"));
            
            // 验证用户是否有权限编辑此项目清单
            if (!item.getUserId().equals(userId)) {
                result.put("success", false);
                result.put("message", "无权限编辑此项目清单");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }
            
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
    public ResponseEntity<Map<String, Object>> deleteItem(@PathVariable Long itemId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = (Long) session.getAttribute("userId");
            if (userId == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
            }
            
            ProjectItem item = itemRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("项目清单不存在"));
            
            // 验证用户是否有权限删除此项目清单
            if (!item.getUserId().equals(userId)) {
                result.put("success", false);
                result.put("message", "无权限删除此项目清单");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }
            
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
        if (versionId != null) {
            return ResponseEntity.ok(quotaRepository.findByVersionIdIncludingUnassigned(versionId));
        }
        return ResponseEntity.ok(quotaRepository.findAll());
    }
    
    @GetMapping("/quotas/search")
    public ResponseEntity<List<EnterpriseQuota>> searchQuotas(
            @RequestParam String keyword,
            @RequestParam(value = "versionId", required = false) Long versionId) {
        if (versionId != null) {
            return ResponseEntity.ok(quotaRepository.findByVersionIdAndKeywordIncludingUnassigned(versionId, keyword));
        }
        return ResponseEntity.ok(quotaRepository.findByKeyword(keyword));
    }
    
    @PutMapping("/items/{itemId}/match")
    public ResponseEntity<Map<String, Object>> updateMatchedQuota(
            @PathVariable Long itemId, @RequestParam Long quotaId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = (Long) session.getAttribute("userId");
            if (userId == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
            }
            
            ProjectItem item = itemRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("项目清单不存在"));
            
            // 验证用户是否有权限编辑此项目清单
            if (!item.getUserId().equals(userId)) {
                result.put("success", false);
                result.put("message", "无权限编辑此项目清单");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }
            
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
            @PathVariable Long itemId, @RequestParam BigDecimal unitPrice, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = (Long) session.getAttribute("userId");
            if (userId == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
            }
            
            ProjectItem item = itemRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("项目清单不存在"));
            
            // 验证用户是否有权限编辑此项目清单
            if (!item.getUserId().equals(userId)) {
                result.put("success", false);
                result.put("message", "无权限编辑此项目清单");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }
            
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
            @PathVariable Long itemId, @PathVariable Long quotaId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = (Long) session.getAttribute("userId");
            if (userId == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
            }
            
            ProjectItem item = itemRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("项目清单不存在"));
            
            // 验证用户是否有权限编辑此项目清单
            if (!item.getUserId().equals(userId)) {
                result.put("success", false);
                result.put("message", "无权限编辑此项目清单");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }
            
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
            @PathVariable Long itemId, @PathVariable Long itemQuotaId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = (Long) session.getAttribute("userId");
            if (userId == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
            }
            
            ProjectItem item = itemRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("项目清单不存在"));
            
            // 验证用户是否有权限编辑此项目清单
            if (!item.getUserId().equals(userId)) {
                result.put("success", false);
                result.put("message", "无权限编辑此项目清单");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }
            
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
    public ResponseEntity<Map<String, Object>> clearItemQuotas(@PathVariable Long itemId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = (Long) session.getAttribute("userId");
            if (userId == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
            }
            
            ProjectItem item = itemRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("项目清单不存在"));
            
            // 验证用户是否有权限编辑此项目清单
            if (!item.getUserId().equals(userId)) {
                result.put("success", false);
                result.put("message", "无权限编辑此项目清单");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }
            
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
            
            // 先删除该版本关联的所有定额
            quotaRepository.deleteByVersionId(versionId);
            
            // 再删除版本本身
            versionRepository.deleteById(versionId);
            
            result.put("success", true);
            result.put("message", "删除成功，已同步删除关联的定额数据");
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
            
            List<EnterpriseQuota> quotas = importService.importEnterpriseQuotas(file, versionId);
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
            int deletedQuotaCount = 0;
            for (Long versionId : versionIds) {
                // 先删除该版本关联的所有定额
                List<EnterpriseQuota> quotas = quotaRepository.findByVersionId(versionId);
                deletedQuotaCount += quotas.size();
                quotaRepository.deleteByVersionId(versionId);
                
                // 再删除版本本身
                versionRepository.deleteById(versionId);
            }
            result.put("success", true);
            result.put("message", "批量删除成功，共删除 " + versionIds.size() + " 个版本和 " + deletedQuotaCount + " 条关联定额数据");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "批量删除失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 手动触发学习分析
     */
    @PostMapping("/learning/analyze")
    public ResponseEntity<Map<String, Object>> triggerLearningAnalysis() {
        Map<String, Object> result = new HashMap<>();
        try {
            learningService.analyzeAndUpdateWeights();
            learningService.discoverSynonyms();
            result.put("success", true);
            result.put("message", "学习分析完成");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "学习分析失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 收集所有历史匹配数据
     */
    @PostMapping("/learning/collect")
    public ResponseEntity<Map<String, Object>> collectAllMatchData() {
        Map<String, Object> result = new HashMap<>();
        try {
            learningService.collectAllMatchData();
            result.put("success", true);
            result.put("message", "数据收集完成");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "数据收集失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
}

