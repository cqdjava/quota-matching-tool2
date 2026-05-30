package com.enterprise.quota.util;

import org.apache.poi.xwpf.usermodel.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

/**
 * 专利交底书 Word 文档生成器
 * 使用方式: mvn exec:java -Dexec.mainClass="com.enterprise.quota.util.PatentDocGenerator"
 */
public class PatentDocGenerator {

    public static void main(String[] args) throws Exception {
        String mdPath = "docs/专利交底书-企业定额智能匹配.md";
        String outputPath = "docs/专利交底书-企业定额智能匹配.docx";

        XWPFDocument doc = new XWPFDocument();
        List<String> lines = Files.readAllLines(Paths.get(mdPath), StandardCharsets.UTF_8);

        boolean inCodeBlock = false;
        StringBuilder codeBuffer = new StringBuilder();

        for (String line : lines) {
            if (line.trim().startsWith("```")) {
                if (inCodeBlock) {
                    XWPFParagraph p = doc.createParagraph();
                    XWPFRun r = p.createRun();
                    r.setFontFamily("Consolas");
                    r.setFontSize(9);
                    r.setText(codeBuffer.toString());
                    inCodeBlock = false;
                    codeBuffer.setLength(0);
                } else {
                    inCodeBlock = true;
                }
                continue;
            }
            if (inCodeBlock) {
                codeBuffer.append(line).append("\n");
                continue;
            }
            if (line.trim().isEmpty()) {
                XWPFParagraph p = doc.createParagraph();
                p.setSpacingAfter(80);
                continue;
            }

            // 各级标题
            if (line.startsWith("# ")) {
                addHeading(doc, line.substring(2).trim(), 22, "1a237e");
            } else if (line.startsWith("## ")) {
                addHeading(doc, line.substring(3).trim(), 16, "0d47a1");
            } else if (line.startsWith("### ")) {
                addHeading(doc, line.substring(4).trim(), 13, "333333");
            } else if (line.startsWith("#### ")) {
                addHeading(doc, line.substring(5).trim(), 12, "555555");
            } else if (line.trim().equals("---")) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun r = p.createRun();
                r.setText(repeat("-", 60));
                r.setColor("cccccc");
                r.setFontSize(8);
            } else if (line.startsWith("|") && line.contains("|")) {
                if (!line.matches("\\|[\\s\\-:]+\\|.*")) {
                    addTableRow(doc, line);
                }
            } else if (line.trim().matches("^[-*]\\s.*") || line.trim().matches("^\\d+[\\.\\)]\\s.*")) {
                addListItem(doc, line.trim());
            } else if (line.trim().startsWith(">")) {
                addQuote(doc, line.substring(1).trim());
            } else {
                addParagraph(doc, line);
            }
        }

        try (FileOutputStream out = new FileOutputStream(outputPath)) {
            doc.write(out);
        }
        doc.close();
        System.out.println("专利交底书已生成: " + outputPath);
    }

    private static void addHeading(XWPFDocument doc, String text, int fontSize, String color) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(200);
        p.setSpacingAfter(120);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontSize(fontSize);
        r.setFontFamily("黑体");
        r.setColor(color);
    }

    private static void addParagraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(100);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setFontFamily("宋体");
        r.setFontSize(11);
    }

    private static void addListItem(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setIndentationLeft(400);
        p.setSpacingAfter(60);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setFontFamily("宋体");
        r.setFontSize(10.5);
    }

    private static void addQuote(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setIndentationLeft(300);
        p.setSpacingAfter(60);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setFontFamily("楷体");
        r.setFontSize(10);
        r.setItalic(true);
        r.setColor("666666");
    }

    private static void addTableRow(XWPFDocument doc, String line) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(40);
        XWPFRun r = p.createRun();
        r.setText(line.replace("|", " │ "));
        r.setFontFamily("宋体");
        r.setFontSize(9);
    }

    private static String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(s);
        return sb.toString();
    }
}
