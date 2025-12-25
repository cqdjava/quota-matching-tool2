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
            
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                ProjectItem item = new ProjectItem();
                item.setItemCode(getCellValue(row.getCell(0)));
                item.setItemName(getCellValue(row.getCell(1)));
                item.setFeatureValue(getCellValue(row.getCell(2)));
                item.setUnit(getCellValue(row.getCell(3)));
                item.setQuantity(getNumericValue(row.getCell(4)));
                if (row.getLastCellNum() > 5) {
                    item.setRemark(getCellValue(row.getCell(5)));
                }
                items.add(item);
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
}

