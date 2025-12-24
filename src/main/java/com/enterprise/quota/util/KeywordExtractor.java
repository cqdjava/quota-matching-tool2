package com.enterprise.quota.util;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 关键词提取工具类
 * 用于从文本中提取关键信息，去除停用词，提取核心关键词
 */
public class KeywordExtractor {
    
    // 中文停用词列表
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "的", "了", "在", "是", "和", "与", "或", "及", "等", "为", "由", "从", "到",
        "有", "无", "不", "非", "未", "已", "将", "要", "可", "能", "应", "该",
        "一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "百", "千", "万",
        "m", "m²", "m³", "kg", "t", "mm", "cm", "dm", "km", "g", "个", "项", "套",
        "元", "块", "片", "根", "条", "张", "台", "辆", "座", "处", "段", "层", "级",
        "含", "包括", "包含", "配", "件", "块", "个", "套", "台", "路", "T", "G", "M"
    ));
    
    // 同义词映射表（用于识别相同概念的不同表达）
    private static final Map<String, Set<String>> SYNONYMS = new HashMap<>();
    static {
        // 录像机相关
        SYNONYMS.put("NVR", new HashSet<>(Arrays.asList("NVR", "网络硬盘录像机", "硬盘录像机", "录像机")));
        SYNONYMS.put("网络硬盘录像机", new HashSet<>(Arrays.asList("NVR", "网络硬盘录像机", "硬盘录像机", "录像机")));
        SYNONYMS.put("硬盘录像机", new HashSet<>(Arrays.asList("NVR", "网络硬盘录像机", "硬盘录像机", "录像机")));
        SYNONYMS.put("录像机", new HashSet<>(Arrays.asList("NVR", "网络硬盘录像机", "硬盘录像机", "录像机")));
        
        // 摄像机相关
        SYNONYMS.put("摄像机", new HashSet<>(Arrays.asList("摄像机", "摄像头", "监控摄像头", "监控摄像机")));
        SYNONYMS.put("摄像头", new HashSet<>(Arrays.asList("摄像机", "摄像头", "监控摄像头", "监控摄像机")));
        SYNONYMS.put("监控摄像头", new HashSet<>(Arrays.asList("摄像机", "摄像头", "监控摄像头", "监控摄像机")));
        SYNONYMS.put("监控摄像机", new HashSet<>(Arrays.asList("摄像机", "摄像头", "监控摄像头", "监控摄像机")));
    }
    
    // 核心概念词（设备类型关键词，权重更高）
    private static final Set<String> CORE_CONCEPTS = new HashSet<>(Arrays.asList(
        "摄像机", "摄像头", "录像机", "NVR", "硬盘录像机", "网络硬盘录像机",
        "监控", "设备", "系统", "装置", "机", "器", "仪", "表"
    ));
    
    // 分隔符模式：中英文标点、空格、数字等
    private static final Pattern SPLIT_PATTERN = Pattern.compile("[\\s\\p{Punct}\\d]+");
    
    /**
     * 从文本中提取关键词
     * @param text 输入文本
     * @return 关键词列表（去重、去停用词）
     */
    public static List<String> extractKeywords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        // 清理文本：去除多余空格、数字、规格和特殊字符
        String cleaned = text.trim()
                .replaceAll("[\\s\\p{Z}]+", " ")  // 多个空格合并为一个
                .replaceAll("[（）()【】\\[\\]《》<>]", "")  // 去除括号
                .replaceAll("\\d+[路块个套台TGMK]+", "")  // 去除数字+单位（如"16路"、"8T"、"16块"）
                .replaceAll("\\d+", "");  // 去除纯数字
        
        // 提取中文词汇（2-6个字符）
        List<String> keywords = new ArrayList<>();
        
        // 方法1：按分隔符分割，提取有意义的部分
        String[] parts = SPLIT_PATTERN.split(cleaned);
        for (String part : parts) {
            if (part.length() >= 2 && part.length() <= 20) {
                // 提取连续的中文字符和英文
                String chinesePart = extractChineseAndEnglishWords(part);
                if (chinesePart.length() >= 2) {
                    keywords.add(chinesePart);
                }
            }
        }
        
        // 方法2：滑动窗口提取词组（2-4字词组）
        extractPhrases(cleaned, keywords);
        
        // 提取核心概念词（设备类型）
        extractCoreConcepts(text, keywords);
        
        // 去重、去停用词、按重要性排序
        return keywords.stream()
                .filter(k -> k.length() >= 2)
                .filter(k -> !STOP_WORDS.contains(k))
                .distinct()
                .sorted((a, b) -> {
                    // 核心概念词优先
                    boolean aIsCore = isCoreConcept(a);
                    boolean bIsCore = isCoreConcept(b);
                    if (aIsCore && !bIsCore) return -1;
                    if (!aIsCore && bIsCore) return 1;
                    // 其次按长度排序
                    int lenCompare = Integer.compare(b.length(), a.length());
                    if (lenCompare != 0) return lenCompare;
                    return a.compareTo(b);
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 提取中文字符和英文单词
     */
    private static String extractChineseAndEnglishWords(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (isChinese(c) || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    
    /**
     * 提取核心概念词（设备类型）
     */
    private static void extractCoreConcepts(String text, List<String> keywords) {
        for (String concept : CORE_CONCEPTS) {
            if (text.contains(concept)) {
                keywords.add(concept);
            }
        }
        
        // 提取NVR（不区分大小写）
        if (text.toUpperCase().contains("NVR")) {
            keywords.add("NVR");
        }
    }
    
    /**
     * 判断是否为核心概念词
     */
    private static boolean isCoreConcept(String keyword) {
        return CORE_CONCEPTS.contains(keyword) || keyword.equalsIgnoreCase("NVR");
    }
    
    /**
     * 判断是否为中文字符
     */
    private static boolean isChinese(char c) {
        return c >= 0x4E00 && c <= 0x9FA5;
    }
    
    /**
     * 使用滑动窗口提取词组
     */
    private static void extractPhrases(String text, List<String> keywords) {
        // 提取2-4字词组
        for (int len = 4; len >= 2; len--) {
            for (int i = 0; i <= text.length() - len; i++) {
                String phrase = text.substring(i, i + len);
                // 只保留全中文的词组
                boolean allChinese = true;
                for (char c : phrase.toCharArray()) {
                    if (!isChinese(c)) {
                        allChinese = false;
                        break;
                    }
                }
                if (allChinese) {
                    keywords.add(phrase);
                }
            }
        }
    }
    
    /**
     * 计算两个关键词列表的相似度
     * @param keywords1 关键词列表1
     * @param keywords2 关键词列表2
     * @return 相似度得分（0-1之间）
     */
    public static double calculateSimilarity(List<String> keywords1, List<String> keywords2) {
        if (keywords1.isEmpty() || keywords2.isEmpty()) {
            return 0.0;
        }
        
        // 计算交集
        Set<String> set1 = new HashSet<>(keywords1);
        Set<String> set2 = new HashSet<>(keywords2);
        
        // 交集大小（考虑同义词）
        double intersection = 0.0;
        Set<String> matched = new HashSet<>();
        
        for (String k : set1) {
            // 完全匹配
            if (set2.contains(k)) {
                intersection += isCoreConcept(k) ? 2.0 : 1.0;  // 核心概念词权重更高
                matched.add(k);
            } else {
                // 检查同义词匹配
                boolean synonymMatched = false;
                for (String k2 : set2) {
                    if (areSynonyms(k, k2)) {
                        intersection += isCoreConcept(k) ? 1.5 : 0.8;  // 同义词匹配得分稍低
                        matched.add(k2);
                        synonymMatched = true;
                        break;
                    }
                }
                
                // 如果同义词未匹配，检查部分匹配（包含关系）
                if (!synonymMatched) {
                    for (String k2 : set2) {
                        if (k.contains(k2) || k2.contains(k)) {
                            intersection += 0.5;
                            break;
                        }
                    }
                }
            }
        }
        
        // 使用改进的Jaccard相似度：交集/并集（考虑权重）
        int union = set1.size() + set2.size();
        if (union == 0) return 0.0;
        
        // 归一化得分
        double similarity = intersection / union;
        
        // 如果核心概念词匹配，提高得分
        boolean hasCoreMatch = false;
        for (String k : set1) {
            if (isCoreConcept(k) && set2.contains(k)) {
                hasCoreMatch = true;
                break;
            }
        }
        if (hasCoreMatch) {
            similarity = Math.min(1.0, similarity * 1.3);
        }
        
        return similarity;
    }
    
    /**
     * 判断两个词是否为同义词
     */
    private static boolean areSynonyms(String word1, String word2) {
        if (word1.equalsIgnoreCase(word2)) {
            return true;
        }
        
        for (Map.Entry<String, Set<String>> entry : SYNONYMS.entrySet()) {
            Set<String> synonyms = entry.getValue();
            if (synonyms.contains(word1) && synonyms.contains(word2)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 计算两个文本的匹配度得分
     * @param text1 文本1
     * @param text2 文本2
     * @return 匹配度得分（0-1之间）
     */
    public static double calculateTextMatchScore(String text1, String text2) {
        if (text1 == null || text2 == null || text1.trim().isEmpty() || text2.trim().isEmpty()) {
            return 0.0;
        }
        
        // 去除括号内容后再比较（括号内通常是配件、规格等次要信息）
        String cleanText1 = text1.replaceAll("[（(].*?[）)]", "").trim();
        String cleanText2 = text2.replaceAll("[（(].*?[）)]", "").trim();
        
        List<String> keywords1 = extractKeywords(cleanText1);
        List<String> keywords2 = extractKeywords(cleanText2);
        
        if (keywords1.isEmpty() || keywords2.isEmpty()) {
            // 如果关键词提取失败，使用简单的包含匹配
            if (cleanText1.contains(cleanText2) || cleanText2.contains(cleanText1)) {
                return 0.3;
            }
            return 0.0;
        }
        
        // 检查是否有冲突的类型词（如果一方有"热成像"另一方有"抓拍"，不应该匹配）
        if (hasConflictingTypeWords(cleanText1, cleanText2)) {
            return 0.0;
        }
        
        // 计算相似度
        double similarity = calculateSimilarity(keywords1, keywords2);
        
        // 如果完全匹配，得分更高
        if (cleanText1.equals(cleanText2)) {
            return 1.0;
        }
        
        // 如果一方包含另一方，得分较高
        if (cleanText1.contains(cleanText2) || cleanText2.contains(cleanText1)) {
            return Math.max(similarity, 0.7);
        }
        
        return similarity;
    }
    
    /**
     * 检查是否有冲突的类型词
     * 例如："热成像摄像机"和"抓拍摄像机"不应该匹配
     */
    private static boolean hasConflictingTypeWords(String text1, String text2) {
        // 定义冲突的类型词对
        String[][] conflicts = {
            {"热成像", "抓拍"},
            {"热成像", "普通"},
            {"抓拍", "热成像"},
            {"抓拍", "普通"},
            {"普通", "热成像"},
            {"普通", "抓拍"}
        };
        
        for (String[] conflict : conflicts) {
            if (text1.contains(conflict[0]) && text2.contains(conflict[1])) {
                return true;
            }
        }
        
        return false;
    }
}

