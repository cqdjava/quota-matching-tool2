package com.enterprise.quota.service;

import com.enterprise.quota.entity.ProjectItem;
import com.enterprise.quota.entity.ProjectItemQuota;
import com.enterprise.quota.repository.ProjectItemRepository;
import com.enterprise.quota.repository.ProjectItemQuotaRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ExcelExportService {
    
    @Autowired
    private ProjectItemRepository itemRepository;
    
    @Autowired
    private ProjectItemQuotaRepository itemQuotaRepository;
    
    public byte[] exportMatchedItems() throws IOException {
        List<ProjectItem> items = itemRepository.findAll();
        
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("匹配结果");
            
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "序号", "清单编码", "清单名称", "项目特征值", "单位", "工程量",
                "匹配定额编码", "匹配定额名称", "定额项目特征", "单价", "合价", "匹配状态", "备注", "多定额明细"
            };
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int rowNum = 1;
            int sequenceNum = 1;
            for (ProjectItem item : items) {
                Row row = sheet.createRow(rowNum++);
                
                // 序号列
                row.createCell(0).setCellValue(sequenceNum++);
                
                row.createCell(1).setCellValue(item.getItemCode() != null ? item.getItemCode() : "");
                row.createCell(2).setCellValue(item.getItemName() != null ? item.getItemName() : "");
                row.createCell(3).setCellValue(item.getFeatureValue() != null ? item.getFeatureValue() : "");
                row.createCell(4).setCellValue(item.getUnit() != null ? item.getUnit() : "");
                row.createCell(5).setCellValue(item.getQuantity() != null ? item.getQuantity().doubleValue() : 0);
                
                // 如果是多定额匹配，显示汇总信息
                if (item.getMatchStatus() != null && item.getMatchStatus() == 3) {
                    List<ProjectItemQuota> quotas = itemQuotaRepository.findByProjectItemIdOrderBySortOrderAsc(item.getId());
                    if (!quotas.isEmpty()) {
                        StringBuilder quotaCodes = new StringBuilder();
                        StringBuilder quotaNames = new StringBuilder();
                        for (ProjectItemQuota quota : quotas) {
                            if (quotaCodes.length() > 0) quotaCodes.append("; ");
                            quotaCodes.append(quota.getQuotaCode());
                            if (quotaNames.length() > 0) quotaNames.append("; ");
                            quotaNames.append(quota.getQuotaName());
                        }
                        row.createCell(6).setCellValue(quotaCodes.toString());
                        row.createCell(7).setCellValue(quotaNames.toString());
                        row.createCell(8).setCellValue("多定额组合");
                    } else {
                        row.createCell(6).setCellValue("");
                        row.createCell(7).setCellValue("");
                        row.createCell(8).setCellValue("");
                    }
                } else {
                    row.createCell(6).setCellValue(item.getMatchedQuotaCode() != null ? item.getMatchedQuotaCode() : "");
                    row.createCell(7).setCellValue(item.getMatchedQuotaName() != null ? item.getMatchedQuotaName() : "");
                    row.createCell(8).setCellValue(item.getMatchedQuotaFeatureValue() != null ? item.getMatchedQuotaFeatureValue() : "");
                }
                
                row.createCell(9).setCellValue(item.getMatchedUnitPrice() != null ? item.getMatchedUnitPrice().doubleValue() : 0);
                row.createCell(10).setCellValue(item.getTotalPrice() != null ? item.getTotalPrice().doubleValue() : 0);
                
                String status = "";
                if (item.getMatchStatus() != null) {
                    switch (item.getMatchStatus()) {
                        case 0: status = "未匹配"; break;
                        case 1: status = "已匹配"; break;
                        case 2: status = "手动修改"; break;
                        case 3: status = "多定额匹配"; break;
                    }
                }
                row.createCell(11).setCellValue(status);
                row.createCell(12).setCellValue(item.getRemark() != null ? item.getRemark() : "");
                
                // 多定额明细
                if (item.getMatchStatus() != null && item.getMatchStatus() == 3) {
                    List<ProjectItemQuota> quotas = itemQuotaRepository.findByProjectItemIdOrderBySortOrderAsc(item.getId());
                    if (!quotas.isEmpty()) {
                        StringBuilder detail = new StringBuilder();
                        for (int i = 0; i < quotas.size(); i++) {
                            ProjectItemQuota quota = quotas.get(i);
                            if (i > 0) detail.append("\n");
                            detail.append(String.format("%d. %s - %s (单价: %.2f元)", 
                                i + 1, quota.getQuotaCode(), quota.getQuotaName(), 
                                quota.getUnitPrice() != null ? quota.getUnitPrice().doubleValue() : 0));
                        }
                        row.createCell(13).setCellValue(detail.toString());
                    } else {
                        row.createCell(13).setCellValue("");
                    }
                } else {
                    row.createCell(13).setCellValue("");
                }
            }
            
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) < 3000) {
                    sheet.setColumnWidth(i, 3000);
                }
            }
            
            workbook.write(out);
            return out.toByteArray();
        }
    }
}

