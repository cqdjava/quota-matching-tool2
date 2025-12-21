package com.enterprise.quota.service;

import com.enterprise.quota.entity.ProjectItem;
import com.enterprise.quota.repository.ProjectItemRepository;
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
                "清单编码", "清单名称", "项目特征值", "单位", "工程量",
                "匹配定额编码", "匹配定额名称", "定额项目特征", "单价", "合价", "匹配状态", "备注"
            };
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int rowNum = 1;
            for (ProjectItem item : items) {
                Row row = sheet.createRow(rowNum++);
                
                row.createCell(0).setCellValue(item.getItemCode() != null ? item.getItemCode() : "");
                row.createCell(1).setCellValue(item.getItemName() != null ? item.getItemName() : "");
                row.createCell(2).setCellValue(item.getFeatureValue() != null ? item.getFeatureValue() : "");
                row.createCell(3).setCellValue(item.getUnit() != null ? item.getUnit() : "");
                row.createCell(4).setCellValue(item.getQuantity() != null ? item.getQuantity().doubleValue() : 0);
                row.createCell(5).setCellValue(item.getMatchedQuotaCode() != null ? item.getMatchedQuotaCode() : "");
                row.createCell(6).setCellValue(item.getMatchedQuotaName() != null ? item.getMatchedQuotaName() : "");
                row.createCell(7).setCellValue(item.getMatchedQuotaFeatureValue() != null ? item.getMatchedQuotaFeatureValue() : "");
                row.createCell(8).setCellValue(item.getMatchedUnitPrice() != null ? item.getMatchedUnitPrice().doubleValue() : 0);
                row.createCell(9).setCellValue(item.getTotalPrice() != null ? item.getTotalPrice().doubleValue() : 0);
                
                String status = "";
                if (item.getMatchStatus() != null) {
                    switch (item.getMatchStatus()) {
                        case 0: status = "未匹配"; break;
                        case 1: status = "已匹配"; break;
                        case 2: status = "手动修改"; break;
                    }
                }
                row.createCell(10).setCellValue(status);
                row.createCell(11).setCellValue(item.getRemark() != null ? item.getRemark() : "");
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

