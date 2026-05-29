package com.enterprise.quota.util;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;

import java.io.*;
import java.util.Map;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Word Document Processor for placeholder replacement
 * Supports both .docx and .doc formats
 * Enhanced with loop replacement and improved placeholder recognition
 */
public class WordDocumentProcessor {

    // 正则表达式匹配 ${...} 格式的占位符，支持各种变体
    // 匹配: ${key}, ${ key }, ${key }, ${ key} 等格式
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\s*\\{\\s*([^}]+?)\\s*\\}");

    /**
     * Process Word document and replace specified text
     * @param templatePath Template file path
     * @param outputPath Output file path
     * @param replacements Replacement rules mapping
     * @throws Exception Processing exception
     */
    public void processDocument(String templatePath, String outputPath, Map<String, String> replacements) throws Exception {
        if (templatePath.toLowerCase().endsWith(".docx")) {
            processDocxDocument(templatePath, outputPath, replacements);
        } else if (templatePath.toLowerCase().endsWith(".doc")) {
            processDocDocument(templatePath, outputPath, replacements);
        } else {
            throw new IllegalArgumentException("Unsupported file format, please use .docx or .doc files");
        }
    }

    /**
     * Process .docx document with loop replacement
     */
    private void processDocxDocument(String templatePath, String outputPath, Map<String, String> replacements) throws Exception {
        try (FileInputStream fis = new FileInputStream(templatePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            int maxIterations = 100; // 防止无限循环
            int iteration = 0;
            boolean hasPlaceholders = true;

            // 循环替换直到没有占位符
            while (hasPlaceholders && iteration < maxIterations) {
                iteration++;
                hasPlaceholders = false;

                // Process paragraphs
                List<XWPFParagraph> paragraphs = document.getParagraphs();
                for (XWPFParagraph paragraph : paragraphs) {
                    if (processParagraphWithLoop(paragraph, replacements)) {
                        hasPlaceholders = true; // 还有未匹配的占位符
                    }
                }

                // Process tables
                List<XWPFTable> tables = document.getTables();
                for (XWPFTable table : tables) {
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            for (XWPFParagraph paragraph : cell.getParagraphs()) {
                                if (processParagraphWithLoop(paragraph, replacements)) {
                                    hasPlaceholders = true; // 还有未匹配的占位符
                                }
                            }
                        }
                    }
                }

                // Process headers
                List<XWPFHeader> headers = document.getHeaderList();
                for (XWPFHeader header : headers) {
                    for (XWPFParagraph paragraph : header.getParagraphs()) {
                        if (processParagraphWithLoop(paragraph, replacements)) {
                            hasPlaceholders = true; // 还有未匹配的占位符
                        }
                    }
                }

                // Process footers
                List<XWPFFooter> footers = document.getFooterList();
                for (XWPFFooter footer : footers) {
                    for (XWPFParagraph paragraph : footer.getParagraphs()) {
                        if (processParagraphWithLoop(paragraph, replacements)) {
                            hasPlaceholders = true; // 还有未匹配的占位符
                        }
                    }
                }
                
                // 如果这一轮没有找到任何占位符，检查是否还有未匹配的占位符
                // 通过再次扫描整个文档来确认
                if (!hasPlaceholders) {
                    hasPlaceholders = checkForRemainingPlaceholders(document, replacements);
                }
            }

            // Save document
            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                document.write(fos);
            }
        }
    }

    /**
     * Check if there are any remaining placeholders in the document
     */
    private boolean checkForRemainingPlaceholders(XWPFDocument document, Map<String, String> replacements) {
        // Check paragraphs
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            if (hasPlaceholders(paragraph, replacements)) {
                return true;
            }
        }
        
        // Check tables
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        if (hasPlaceholders(paragraph, replacements)) {
                            return true;
                        }
                    }
                }
            }
        }
        
        // Check headers
        for (XWPFHeader header : document.getHeaderList()) {
            for (XWPFParagraph paragraph : header.getParagraphs()) {
                if (hasPlaceholders(paragraph, replacements)) {
                    return true;
                }
            }
        }
        
        // Check footers
        for (XWPFFooter footer : document.getFooterList()) {
            for (XWPFParagraph paragraph : footer.getParagraphs()) {
                if (hasPlaceholders(paragraph, replacements)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if paragraph contains any placeholders
     */
    private boolean hasPlaceholders(XWPFParagraph paragraph, Map<String, String> replacements) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty()) {
            return false;
        }

        // 合并所有 run 的文本
        StringBuilder fullText = new StringBuilder();
        for (XWPFRun run : runs) {
            String text = run.getText(0);
            if (text != null) {
                fullText.append(text);
            }
        }

        String combinedText = fullText.toString();
        if (combinedText.isEmpty()) {
            return false;
        }

        // 检查是否有未匹配的占位符
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(combinedText);
        while (matcher.find()) {
            String fullPlaceholder = matcher.group(0);
            String key = matcher.group(1).trim();
            String standardPlaceholder = "${" + key + "}";
            
            // 如果占位符不在替换列表中，说明还有未匹配的占位符
            if (!replacements.containsKey(standardPlaceholder) && !replacements.containsKey(fullPlaceholder)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Process paragraph with loop replacement and improved placeholder recognition
     * Preserves formatting (underline, font, color, etc.) from placeholder runs
     * Returns true if placeholders were found and replaced
     */
    private boolean processParagraphWithLoop(XWPFParagraph paragraph, Map<String, String> replacements) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty()) {
            return false;
        }

        // 构建 run 信息列表，包含文本和格式
        List<RunInfo> runInfos = new java.util.ArrayList<>();
        int totalLength = 0;
        for (XWPFRun run : runs) {
            String text = run.getText(0);
            if (text == null) {
                text = "";
            }
            RunInfo info = new RunInfo(run, text, totalLength, totalLength + text.length());
            runInfos.add(info);
            totalLength += text.length();
        }

        // 合并所有文本以查找占位符
        StringBuilder fullText = new StringBuilder();
        for (RunInfo info : runInfos) {
            fullText.append(info.text);
        }

        String combinedText = fullText.toString();
        if (combinedText.isEmpty()) {
            return false;
        }

        boolean hasChanges = false;
        boolean hasRemainingPlaceholders = false;

        // 先尝试精确匹配（用户输入的格式）
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String placeholder = entry.getKey();
            String replacement = entry.getValue();
            
            int index = combinedText.indexOf(placeholder);
            while (index >= 0) {
                // 找到占位符所在的 run(s)
                replacePlaceholderInRuns(runInfos, index, placeholder.length(), replacement, replacements);
                hasChanges = true;
                // 更新 combinedText 以继续查找
                combinedText = combinedText.substring(0, index) + replacement + combinedText.substring(index + placeholder.length());
                index = combinedText.indexOf(placeholder, index + replacement.length());
            }
        }

        // 使用正则表达式查找所有 ${...} 格式的占位符并替换
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(combinedText);
        List<ReplacementInfo> replacementsToApply = new java.util.ArrayList<>();
        
        while (matcher.find()) {
            String fullPlaceholder = matcher.group(0); // 完整的 ${key} 或 ${ key } 等
            String key = matcher.group(1).trim(); // 提取 key 并去除空格
            int start = matcher.start();
            int end = matcher.end();
            
            // 尝试匹配标准格式 ${key}
            String standardPlaceholder = "${" + key + "}";
            String replacement = replacements.get(standardPlaceholder);
            
            if (replacement == null) {
                // 如果标准格式没找到，尝试直接匹配找到的占位符
                replacement = replacements.get(fullPlaceholder);
            }
            
            if (replacement != null) {
                replacementsToApply.add(new ReplacementInfo(start, end, replacement));
                hasChanges = true;
            } else {
                // 仍然有未匹配的占位符
                hasRemainingPlaceholders = true;
            }
        }
        
        // 从后往前替换，避免索引偏移问题
        replacementsToApply.sort((a, b) -> Integer.compare(b.start, a.start));
        for (ReplacementInfo repInfo : replacementsToApply) {
            replacePlaceholderInRuns(runInfos, repInfo.start, repInfo.end - repInfo.start, repInfo.replacement, replacements);
        }

        // 应用所有更改到实际的 run
        if (hasChanges) {
            applyRunChanges(paragraph, runInfos);
        }

        return hasRemainingPlaceholders;
    }

    /**
     * Replace placeholder in runs while preserving format
     */
    private void replacePlaceholderInRuns(List<RunInfo> runInfos, int startIndex, int length, String replacement, Map<String, String> replacements) {
        int endIndex = startIndex + length;
        
        // 找到占位符所在的 run(s)
        RunInfo firstRun = null;
        List<RunInfo> affectedRuns = new java.util.ArrayList<>();
        
        for (RunInfo info : runInfos) {
            if (info.startIndex < endIndex && info.endIndex > startIndex) {
                affectedRuns.add(info);
                if (firstRun == null) {
                    firstRun = info;
                }
            }
        }
        
        if (firstRun == null) {
            return;
        }
        
        // 保存第一个 run 的格式（占位符的格式）
        XWPFRun formatSource = firstRun.run;
        
        // 计算需要删除的文本
        for (RunInfo info : affectedRuns) {
            int runStart = Math.max(0, startIndex - info.startIndex);
            int runEnd = Math.min(info.text.length(), endIndex - info.startIndex);
            
            if (runStart < runEnd) {
                // 删除占位符文本
                String before = info.text.substring(0, runStart);
                String after = info.text.substring(runEnd);
                info.text = before + after;
                // 更新索引
                int deletedLength = runEnd - runStart;
                info.endIndex -= deletedLength;
                for (RunInfo other : runInfos) {
                    if (other.startIndex > info.startIndex) {
                        other.startIndex -= deletedLength;
                        other.endIndex -= deletedLength;
                    }
                }
            }
        }
        
        // 在第一个 run 中插入替换文本
        int insertPos = startIndex - firstRun.startIndex;
        if (insertPos < 0) insertPos = 0;
        if (insertPos > firstRun.text.length()) insertPos = firstRun.text.length();
        
        firstRun.text = firstRun.text.substring(0, insertPos) + replacement + firstRun.text.substring(insertPos);
        firstRun.endIndex += replacement.length();
        
        // 更新后续 run 的索引
        for (RunInfo info : runInfos) {
            if (info.startIndex > firstRun.startIndex) {
                info.startIndex += replacement.length();
                info.endIndex += replacement.length();
            }
        }
        
        // 标记需要应用格式
        firstRun.needsFormat = true;
        firstRun.formatSource = formatSource;
    }

    /**
     * Apply changes to actual runs, preserving format
     */
    private void applyRunChanges(XWPFParagraph paragraph, List<RunInfo> runInfos) {
        // 先清空所有 run
        for (RunInfo info : runInfos) {
            info.run.setText("", 0);
        }
        
        // 重新设置文本，保留格式
        for (RunInfo info : runInfos) {
            if (info.text != null && !info.text.isEmpty()) {
                if (info.needsFormat && info.formatSource != null) {
                    // 先复制格式（在设置文本之前），这样可以确保格式正确应用
                    copyRunFormat(info.formatSource, info.run);
                    // 然后设置文本
                    info.run.setText(info.text, 0);
                } else {
                    info.run.setText(info.text, 0);
                }
            }
        }
    }

    /**
     * Copy format from source run to target run
     * Preserves all formatting including font size, especially for table cells
     */
    private void copyRunFormat(XWPFRun source, XWPFRun target) {
        // 先保存源run的关键格式属性
        Double sourceFontSize = null;
        try {
            sourceFontSize = source.getFontSizeAsDouble();
        } catch (Exception e) {
            // 忽略
        }
        
        String sourceFontFamily = source.getFontFamily();
        String sourceColor = source.getColor();
        org.apache.poi.xwpf.usermodel.UnderlinePatterns sourceUnderline = source.getUnderline();
        boolean sourceBold = source.isBold();
        boolean sourceItalic = source.isItalic();
        
        // 优先使用 XML 复制来保留所有格式属性（包括表格内的字体大小）
        // 这样可以确保所有格式属性（包括字体大小的精确值）都被正确保留
        try {
            if (source.getCTR() != null && source.getCTR().getRPr() != null) {
                // 复制整个 RPr（运行属性）以保留所有格式
                org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr sourceRPr = source.getCTR().getRPr();
                if (target.getCTR().getRPr() == null) {
                    target.getCTR().addNewRPr();
                }
                // 使用 XML 复制来保留所有格式属性（包括字体大小的精确值，如10.5pt）
                // 这对于表格内的字体大小特别重要
                // XML复制会保留所有格式属性，包括精确的字体大小值（以half-points存储）
                org.apache.xmlbeans.XmlObject sourceRPrCopy = sourceRPr.copy();
                target.getCTR().setRPr((org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr) sourceRPrCopy);
                
                // XML复制后，不要再次设置字体大小，因为XML复制已经保留了精确值
                // 如果再次设置，可能会将精确值（如10.5pt）转换为整数值（如10pt或11pt）
                // 只设置其他可能不在XML中的属性
                if (sourceFontFamily != null) {
                    target.setFontFamily(sourceFontFamily);
                }
                if (sourceColor != null) {
                    target.setColor(sourceColor);
                }
                if (sourceUnderline != null) {
                    target.setUnderline(sourceUnderline);
                }
                target.setBold(sourceBold);
                target.setItalic(sourceItalic);
                
                return; // XML复制成功，直接返回（字体大小已经在XML中正确保留）
            }
        } catch (Exception e) {
            // 如果 XML 复制失败，使用高级 API 逐个复制
        }
        
        // 如果 XML 复制失败，使用高级 API 逐个复制格式
        try {
            // 复制字体大小（重要：保留表格内的字体大小）
            if (sourceFontSize != null) {
                target.setFontSize(sourceFontSize.intValue());
            }
        } catch (Exception e) {
            // 忽略字体大小复制错误
        }
        
        // 复制字体族
        if (sourceFontFamily != null) {
            target.setFontFamily(sourceFontFamily);
        }
        
        // 复制颜色
        if (sourceColor != null) {
            target.setColor(sourceColor);
        }
        
        // 复制下划线（重要：保留占位符的下划线格式）
        if (sourceUnderline != null) {
            target.setUnderline(sourceUnderline);
        }
        
        // 复制粗体、斜体
        target.setBold(sourceBold);
        target.setItalic(sourceItalic);
    }

    /**
     * Helper class to store run information
     */
    private static class RunInfo {
        XWPFRun run;
        String text;
        int startIndex;
        int endIndex;
        boolean needsFormat = false;
        XWPFRun formatSource = null;

        RunInfo(XWPFRun run, String text, int startIndex, int endIndex) {
            this.run = run;
            this.text = text;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }

    /**
     * Helper class to store replacement information
     */
    private static class ReplacementInfo {
        int start;
        int end;
        String replacement;

        ReplacementInfo(int start, int end, String replacement) {
            this.start = start;
            this.end = end;
            this.replacement = replacement;
        }
    }

    /**
     * Process .doc format document with loop replacement
     */
    private void processDocDocument(String templatePath, String outputPath, Map<String, String> replacements) throws Exception {
        try (FileInputStream fis = new FileInputStream(templatePath);
             HWPFDocument document = new HWPFDocument(fis)) {

            Range range = document.getRange();
            String documentText = range.text();

            int maxIterations = 100; // 防止无限循环
            int iteration = 0;
            boolean hasPlaceholders = true;
            String processedText = documentText;

            // 循环替换直到没有占位符
            while (hasPlaceholders && iteration < maxIterations) {
                iteration++;
                hasPlaceholders = false;
                String previousText = processedText;

                // 先尝试精确匹配（用户输入的格式）
                for (Map.Entry<String, String> entry : replacements.entrySet()) {
                    String placeholder = entry.getKey();
                    String replacement = entry.getValue();
                    
                    if (processedText.contains(placeholder)) {
                        processedText = processedText.replace(placeholder, replacement);
                    }
                }

                // 使用正则表达式查找所有 ${...} 格式的占位符并替换
                Matcher matcher = PLACEHOLDER_PATTERN.matcher(processedText);
                StringBuffer result = new StringBuffer();
                boolean foundPlaceholder = false;
                
                while (matcher.find()) {
                    foundPlaceholder = true;
                    String fullPlaceholder = matcher.group(0); // 完整的 ${key} 或 ${ key } 等
                    String key = matcher.group(1).trim(); // 提取 key 并去除空格
                    
                    // 尝试匹配标准格式 ${key}
                    String standardPlaceholder = "${" + key + "}";
                    String replacement = replacements.get(standardPlaceholder);
                    
                    if (replacement != null) {
                        matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
                    } else {
                        // 如果标准格式没找到，尝试直接匹配找到的占位符
                        replacement = replacements.get(fullPlaceholder);
                        if (replacement != null) {
                            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
                        } else {
                            // 仍然有未匹配的占位符，保留原样
                            matcher.appendReplacement(result, Matcher.quoteReplacement(fullPlaceholder));
                            hasPlaceholders = true;
                        }
                    }
                }
                
                if (foundPlaceholder) {
                    matcher.appendTail(result);
                    processedText = result.toString();
                }

                // 检查是否有变化
                if (!processedText.equals(previousText)) {
                    hasPlaceholders = true; // 继续下一轮，因为替换可能产生新的占位符
                }
            }

            // Replace the entire document text
            range.replaceText(documentText, processedText);

            // Save document
            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                document.write(fos);
            }
        }
    }

    /**
     * Validate if file exists and is readable
     */
    public boolean validateTemplateFile(String filePath) {
        File file = new File(filePath);
        boolean exists = file.exists();
        boolean readable = file.canRead();
        boolean validFormat = filePath.toLowerCase().endsWith(".docx") || filePath.toLowerCase().endsWith(".doc");
        return exists && readable && validFormat;
    }

    /**
     * Validate if output path is writable
     */
    public boolean validateOutputPath(String filePath) {
        File file = new File(filePath);
        File parentDir = file.getParentFile();

        if (parentDir == null) {
            return false;
        }

        boolean parentExists = parentDir.exists();
        boolean parentWritable = parentDir.canWrite();

        return parentExists && parentWritable;
    }
}

