package com.enterprise.quota.util;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;

import java.io.*;
import java.util.Map;
import java.util.List;

/**
 * Word Document Processor for placeholder replacement
 * Supports both .docx and .doc formats
 */
public class WordDocumentProcessor {

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
     * Process .docx document using the most reliable method
     */
    private void processDocxDocument(String templatePath, String outputPath, Map<String, String> replacements) throws Exception {
        try (FileInputStream fis = new FileInputStream(templatePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            // Process paragraphs
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (XWPFParagraph paragraph : paragraphs) {
                processParagraphSimple(paragraph, replacements);
            }

            // Process tables
            List<XWPFTable> tables = document.getTables();
            for (XWPFTable table : tables) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
                            processParagraphSimple(paragraph, replacements);
                        }
                    }
                }
            }

            // Process headers
            List<XWPFHeader> headers = document.getHeaderList();
            for (XWPFHeader header : headers) {
                for (XWPFParagraph paragraph : header.getParagraphs()) {
                    processParagraphSimple(paragraph, replacements);
                }
            }

            // Process footers
            List<XWPFFooter> footers = document.getFooterList();
            for (XWPFFooter footer : footers) {
                for (XWPFParagraph paragraph : footer.getParagraphs()) {
                    processParagraphSimple(paragraph, replacements);
                }
            }

            // Save document
            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                document.write(fos);
            }
        }
    }

    /**
     * Simple paragraph processing that preserves ALL formatting
     */
    private void processParagraphSimple(XWPFParagraph paragraph, Map<String, String> replacements) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty()) {
            return;
        }

        // Process each run
        for (XWPFRun run : runs) {
            String text = run.getText(0);
            if (text != null) {
                String processedText = text;
                boolean hasChanges = false;

                // Execute replacements
                for (Map.Entry<String, String> entry : replacements.entrySet()) {
                    String originalPattern = entry.getKey();
                    String replacementText = entry.getValue();

                    if (processedText.contains(originalPattern)) {
                        processedText = processedText.replace(originalPattern, replacementText);
                        hasChanges = true;
                    }
                }

                // Update text if changes were made
                if (hasChanges) {
                    run.setText(processedText, 0);
                }
            }
        }
    }

    /**
     * Process .doc format document
     */
    private void processDocDocument(String templatePath, String outputPath, Map<String, String> replacements) throws Exception {
        try (FileInputStream fis = new FileInputStream(templatePath);
             HWPFDocument document = new HWPFDocument(fis)) {

            Range range = document.getRange();
            String documentText = range.text();

            String processedText = documentText;

            // Execute all replacements
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                String originalText = entry.getKey();
                String replacementText = entry.getValue();
                if (processedText.contains(originalText)) {
                    processedText = processedText.replace(originalText, replacementText);
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

