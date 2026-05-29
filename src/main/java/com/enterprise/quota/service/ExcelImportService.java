package com.enterprise.quota.service;

import com.enterprise.quota.entity.EnterpriseQuota;
import com.enterprise.quota.entity.ProjectItem;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcelImportService {
    
    public List<EnterpriseQuota> importEnterpriseQuotas(MultipartFile file, Long versionId) throws IOException {
        List<EnterpriseQuota> quotas = new ArrayList<>();
        
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                EnterpriseQuota quota = new EnterpriseQuota();
                quota.setQuotaCode(getCellValue(row.getCell(0)));
                quota.setQuotaName(getCellValue(row.getCell(1)));
                quota.setFeatureValue(getCellValue(row.getCell(2)));
                quota.setUnit(getCellValue(row.getCell(3)));
                quota.setUnitPrice(getNumericValue(row.getCell(4)));
                quota.setLaborCost(getNumericValue(row.getCell(5)));
                quota.setMaterialCost(getNumericValue(row.getCell(6)));
                quota.setMachineCost(getNumericValue(row.getCell(7)));
                if (row.getLastCellNum() > 8) {
                    quota.setRemark(getCellValue(row.getCell(8)));
                }
                // 设置版本ID
                quota.setVersionId(versionId);
                quotas.add(quota);
            }
        }
        
        return quotas;
    }
    
    /**
     * 兼容旧版本的导入方法（不设置版本ID）
     */
    public List<EnterpriseQuota> importEnterpriseQuotas(MultipartFile file) throws IOException {
        return importEnterpriseQuotas(file, null);
    }
    
    public List<ProjectItem> importProjectItems(MultipartFile file) throws IOException {
        List<ProjectItem> items = new ArrayList<>();
        
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            // 读取表头，确定列索引
            Row headerRow = sheet.getRow(0);
            int serialIndex = -1;          // 序号
            int itemCodeIndex = -1;
            int itemNameIndex = -1;
            int featureValueIndex = -1;
            int unitIndex = -1;
            int quantityIndex = -1;
            int remarkIndex = -1;
            int matchedQuotaCodeIndex = -1;     // 匹配定额编码
            int matchedQuotaNameIndex = -1;     // 匹配定额名称
            int matchedQuotaFeatureIndex = -1;  // 定额项目特征
            int unitPriceIndex = -1;            // 单价
            int totalPriceIndex = -1;           // 合价
            int matchStatusIndex = -1;          // 匹配状态
            int multiQuotaDetailIndex = -1;     // 多定额明细
            
            if (headerRow != null) {
                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    Cell cell = headerRow.getCell(i);
                    if (cell == null) continue;
                    String headerValue = getCellValue(cell).trim();
                    
                    // 根据表头文本确定列索引（精确匹配优先，避免误匹配）
                    
                    // 序号列（用于排序）
                    if (serialIndex == -1 && (headerValue.equals("序号") || headerValue.equalsIgnoreCase("serial"))) {
                        serialIndex = i;
                    }
                    
                    // 清单编码：优先完全匹配，避免匹配到"匹配定额编码"
                    if (itemCodeIndex == -1) {
                        if (headerValue.equals("清单编码") || headerValue.equalsIgnoreCase("itemCode")) {
                            itemCodeIndex = i;
                        } else if (headerValue.contains("清单编码") && !headerValue.contains("匹配")) {
                            itemCodeIndex = i;
                        }
                    }
                    // 清单名称：优先完全匹配，避免匹配到"匹配定额名称"
                    if (itemNameIndex == -1) {
                        if (headerValue.equals("清单名称") || headerValue.equalsIgnoreCase("itemName")) {
                            itemNameIndex = i;
                        } else if (headerValue.contains("清单名称") && !headerValue.contains("匹配")) {
                            itemNameIndex = i;
                        }
                    }
                    // 项目特征值：优先完全匹配
                    if (featureValueIndex == -1) {
                        if (headerValue.equals("项目特征值") || headerValue.equalsIgnoreCase("featureValue")) {
                            featureValueIndex = i;
                        } else if (headerValue.contains("项目特征") && !headerValue.contains("定额项目特征")) {
                            featureValueIndex = i;
                        }
                    }
                    // 单位：完全匹配
                    if (unitIndex == -1 && (headerValue.equals("单位") || headerValue.equalsIgnoreCase("unit"))) {
                        unitIndex = i;
                    }
                    // 工程量：优先完全匹配
                    if (quantityIndex == -1) {
                        if (headerValue.equals("工程量") || headerValue.equalsIgnoreCase("quantity")) {
                            quantityIndex = i;
                        } else if (headerValue.contains("工程量")) {
                            quantityIndex = i;
                        }
                    }
                    // 备注：完全匹配，避免误匹配
                    if (remarkIndex == -1 && (headerValue.equals("备注") || headerValue.equalsIgnoreCase("remark"))) {
                        remarkIndex = i;
                    }
                    
                    // 匹配相关信息列
                    if (matchedQuotaCodeIndex == -1 && (headerValue.equals("匹配定额编码") || headerValue.equalsIgnoreCase("matchedQuotaCode"))) {
                        matchedQuotaCodeIndex = i;
                    }
                    if (matchedQuotaNameIndex == -1 && (headerValue.equals("匹配定额名称") || headerValue.equalsIgnoreCase("matchedQuotaName"))) {
                        matchedQuotaNameIndex = i;
                    }
                    if (matchedQuotaFeatureIndex == -1 && (headerValue.equals("定额项目特征") || headerValue.equalsIgnoreCase("matchedQuotaFeature"))) {
                        matchedQuotaFeatureIndex = i;
                    }
                    if (unitPriceIndex == -1 && (headerValue.equals("单价") || headerValue.equalsIgnoreCase("unitPrice"))) {
                        unitPriceIndex = i;
                    }
                    if (totalPriceIndex == -1 && (headerValue.equals("合价") || headerValue.equalsIgnoreCase("totalPrice"))) {
                        totalPriceIndex = i;
                    }
                    if (matchStatusIndex == -1 && (headerValue.equals("匹配状态") || headerValue.equalsIgnoreCase("matchStatus"))) {
                        matchStatusIndex = i;
                    }
                    if (multiQuotaDetailIndex == -1 && (headerValue.equals("多定额明细") || headerValue.equalsIgnoreCase("multiQuotaDetail"))) {
                        multiQuotaDetailIndex = i;
                    }
                }
            }
            
            // 如果表头识别失败，使用默认列索引
            int columnCount = headerRow != null ? headerRow.getLastCellNum() : 0;
            boolean isExportFormat = columnCount >= 14;  // 导出格式至少14列
            
            if (!isExportFormat) {
                // 兼容旧格式（6列标准格式）
                if (itemCodeIndex == -1) itemCodeIndex = 0;
                if (itemNameIndex == -1) itemNameIndex = 1;
                if (featureValueIndex == -1) featureValueIndex = 2;
                if (unitIndex == -1) unitIndex = 3;
                if (quantityIndex == -1) quantityIndex = 4;
                if (remarkIndex == -1 && columnCount <= 6) {
                    remarkIndex = 5;
                }
            } else {
                // 导出格式：使用识别到的索引，未识别到的保持-1
                // 不设置默认值，避免读取错误列
            }
            
            // 从第二行开始读取数据
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                ProjectItem item = new ProjectItem();
                
                // 使用识别到的列索引读取基础数据
                if (itemCodeIndex >= 0 && itemCodeIndex < row.getLastCellNum()) {
                    item.setItemCode(getCellValue(row.getCell(itemCodeIndex)));
                }
                if (itemNameIndex >= 0 && itemNameIndex < row.getLastCellNum()) {
                    item.setItemName(getCellValue(row.getCell(itemNameIndex)));
                }
                if (featureValueIndex >= 0 && featureValueIndex < row.getLastCellNum()) {
                    item.setFeatureValue(getCellValue(row.getCell(featureValueIndex)));
                }
                if (unitIndex >= 0 && unitIndex < row.getLastCellNum()) {
                    item.setUnit(getCellValue(row.getCell(unitIndex)));
                }
                if (quantityIndex >= 0 && quantityIndex < row.getLastCellNum()) {
                    item.setQuantity(getNumericValue(row.getCell(quantityIndex)));
                }
                // 备注：只在识别到备注列且该列存在时才读取
                if (remarkIndex >= 0 && remarkIndex < row.getLastCellNum()) {
                    Cell remarkCell = row.getCell(remarkIndex);
                    if (remarkCell != null) {
                        String remarkValue = getCellValue(remarkCell);
                        if (remarkValue != null && !remarkValue.trim().isEmpty()) {
                            item.setRemark(remarkValue);
                        }
                    }
                }
                
                // 读取匹配相关信息（如果存在）
                if (matchedQuotaCodeIndex >= 0 && matchedQuotaCodeIndex < row.getLastCellNum()) {
                    String quotaCode = getCellValue(row.getCell(matchedQuotaCodeIndex));
                    if (quotaCode != null && !quotaCode.trim().isEmpty()) {
                        item.setMatchedQuotaCode(quotaCode);
                    }
                }
                if (matchedQuotaNameIndex >= 0 && matchedQuotaNameIndex < row.getLastCellNum()) {
                    String quotaName = getCellValue(row.getCell(matchedQuotaNameIndex));
                    if (quotaName != null && !quotaName.trim().isEmpty()) {
                        item.setMatchedQuotaName(quotaName);
                    }
                }
                if (matchedQuotaFeatureIndex >= 0 && matchedQuotaFeatureIndex < row.getLastCellNum()) {
                    String quotaFeature = getCellValue(row.getCell(matchedQuotaFeatureIndex));
                    if (quotaFeature != null && !quotaFeature.trim().isEmpty()) {
                        item.setMatchedQuotaFeatureValue(quotaFeature);
                    }
                }
                if (unitPriceIndex >= 0 && unitPriceIndex < row.getLastCellNum()) {
                    BigDecimal unitPrice = getNumericValue(row.getCell(unitPriceIndex));
                    if (unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) > 0) {
                        item.setMatchedUnitPrice(unitPrice);
                    }
                }
                if (totalPriceIndex >= 0 && totalPriceIndex < row.getLastCellNum()) {
                    BigDecimal totalPrice = getNumericValue(row.getCell(totalPriceIndex));
                    if (totalPrice != null && totalPrice.compareTo(BigDecimal.ZERO) > 0) {
                        item.setTotalPrice(totalPrice);
                    }
                }
                
                // 处理匹配状态
                if (matchStatusIndex >= 0 && matchStatusIndex < row.getLastCellNum()) {
                    String statusStr = getCellValue(row.getCell(matchStatusIndex));
                    if (statusStr != null && !statusStr.trim().isEmpty()) {
                        Integer matchStatus = parseMatchStatus(statusStr.trim());
                        item.setMatchStatus(matchStatus);
                    }
                }
                
                // 设置默认值
                if (item.getMatchStatus() == null) {
                    item.setMatchStatus(0); // 默认未匹配
                }
                
                // 只添加至少包含清单编码和清单名称的项目
                if ((item.getItemCode() != null && !item.getItemCode().trim().isEmpty()) ||
                    (item.getItemName() != null && !item.getItemName().trim().isEmpty())) {
                    items.add(item);
                }
            }
        }
        
        return items;
    }
    
    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue().toString();
                    } else {
                        double numericValue = cell.getNumericCellValue();
                        if (numericValue == (long) numericValue) {
                            return String.valueOf((long) numericValue);
                        } else {
                            return String.valueOf(numericValue);
                        }
                    }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    // 对于公式单元格，尝试获取计算后的值
                    try {
                        switch (cell.getCachedFormulaResultType()) {
                            case STRING:
                                return cell.getStringCellValue().trim();
                            case NUMERIC:
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    return cell.getDateCellValue().toString();
                                } else {
                                    double numericValue = cell.getNumericCellValue();
                                    if (numericValue == (long) numericValue) {
                                        return String.valueOf((long) numericValue);
                                    } else {
                                        return String.valueOf(numericValue);
                                    }
                                }
                            case BOOLEAN:
                                return String.valueOf(cell.getBooleanCellValue());
                            default:
                                return cell.getCellFormula();
                        }
                    } catch (Exception e) {
                        // 如果获取公式值失败，返回公式本身
                        return cell.getCellFormula();
                    }
                default:
                    return "";
            }
        } catch (Exception e) {
            // 处理任何异常，返回空字符串，避免导入失败
            return "";
        }
    }
    
    private BigDecimal getNumericValue(Cell cell) {
        if (cell == null) return BigDecimal.ZERO;
        
        switch (cell.getCellType()) {
            case NUMERIC:
                return BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING:
                try {
                    return new BigDecimal(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    return BigDecimal.ZERO;
                }
            default:
                return BigDecimal.ZERO;
        }
    }
    
    /**
     * 解析匹配状态字符串
     */
    private Integer parseMatchStatus(String statusStr) {
        switch (statusStr) {
            case "未匹配":
            case "0":
                return 0;
            case "已匹配":
            case "1":
                return 1;
            case "手动修改":
            case "2":
                return 2;
            case "多定额匹配":
            case "3":
                return 3;
            default:
                return 0; // 默认未匹配
        }
    }
}

