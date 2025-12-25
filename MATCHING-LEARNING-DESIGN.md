# 匹配精度自我学习机制设计方案

## 📋 概述

基于历史匹配结果（导出的最终清单），系统可以自动学习匹配模式，逐步改进匹配精度。本方案设计了一个完整的自我学习机制。

---

## 🎯 核心思路

### 1. 数据来源
- **已匹配数据**：`matchStatus = 1`（自动匹配成功）
- **手动修改数据**：`matchStatus = 2`（用户手动修改，视为"正确答案"）
- **多定额匹配**：`matchStatus = 3`（用户手动组合，视为"正确答案"）

### 2. 学习目标
- 识别成功匹配的关键词模式
- 学习同义词和相似表达
- 调整匹配权重参数
- 建立匹配规则库

---

## 📊 数据结构设计

### 1. 匹配学习记录表（`matching_learning_record`）

```sql
CREATE TABLE matching_learning_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    item_name VARCHAR(500),              -- 项目清单名称
    item_feature_value TEXT,             -- 项目特征值
    quota_name VARCHAR(500),             -- 匹配的定额名称
    quota_feature_value TEXT,            -- 匹配的定额特征值
    match_score DOUBLE,                   -- 匹配得分
    match_type INT,                      -- 匹配类型：1=自动匹配，2=手动修改，3=多定额
    item_keywords TEXT,                  -- 项目清单提取的关键词（JSON格式）
    quota_keywords TEXT,                 -- 定额提取的关键词（JSON格式）
    common_keywords TEXT,                 -- 共同关键词（JSON格式）
    learning_weight DOUBLE DEFAULT 1.0,   -- 学习权重（手动修改的权重更高）
    create_time DATETIME,
    update_time DATETIME
);
```

### 2. 关键词权重表（`keyword_weight`）

```sql
CREATE TABLE keyword_weight (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    keyword VARCHAR(200) UNIQUE,         -- 关键词
    weight DOUBLE DEFAULT 1.0,            -- 权重值
    match_count INT DEFAULT 0,           -- 匹配成功次数
    total_count INT DEFAULT 0,            -- 出现总次数
    success_rate DOUBLE DEFAULT 0.0,     -- 成功率
    is_core_concept BOOLEAN DEFAULT FALSE, -- 是否为核心概念词
    update_time DATETIME
);
```

### 3. 匹配规则表（`matching_rule`）

```sql
CREATE TABLE matching_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_type VARCHAR(50),                -- 规则类型：synonym, pattern, weight
    source_text VARCHAR(500),              -- 源文本
    target_text VARCHAR(500),             -- 目标文本
    rule_value TEXT,                      -- 规则值（JSON格式）
    confidence DOUBLE DEFAULT 0.0,         -- 置信度
    usage_count INT DEFAULT 0,            -- 使用次数
    create_time DATETIME,
    update_time DATETIME
);
```

---

## 🔄 学习流程设计

### 阶段1：数据收集（每次匹配后）

```
1. 扫描所有匹配结果
   ├─ 自动匹配（matchStatus = 1）
   │  └─ 记录匹配信息，权重 = 0.5
   ├─ 手动修改（matchStatus = 2）
   │  └─ 记录匹配信息，权重 = 1.0（高权重）
   └─ 多定额匹配（matchStatus = 3）
      └─ 记录每个定额的匹配信息，权重 = 1.0

2. 提取关键词
   ├─ 项目清单关键词
   ├─ 定额关键词
   └─ 共同关键词

3. 保存到 learning_record 表
```

### 阶段2：模式分析（定期执行，如每天一次）

```
1. 分析成功匹配模式
   ├─ 统计高频共同关键词
   ├─ 识别关键词组合模式
   └─ 计算关键词匹配成功率

2. 更新关键词权重
   ├─ 成功匹配次数多的关键词 → 提高权重
   ├─ 失败匹配次数多的关键词 → 降低权重
   └─ 计算：weight = (success_count / total_count) * base_weight

3. 发现同义词关系
   ├─ 分析相似文本的匹配结果
   ├─ 识别可能的同义词对
   └─ 保存到 matching_rule 表（type = 'synonym'）
```

### 阶段3：规则应用（下次匹配时）

```
1. 加载学习到的规则
   ├─ 关键词权重表
   ├─ 同义词映射表
   └─ 匹配模式规则

2. 应用到匹配算法
   ├─ 使用动态权重替代固定权重
   ├─ 使用学习到的同义词扩展匹配
   └─ 优先使用高置信度的规则
```

---

## 🧠 学习算法设计

### 1. 关键词权重学习

```java
// 伪代码
for (LearningRecord record : records) {
    List<String> keywords = record.getCommonKeywords();
    double weight = record.getLearningWeight(); // 手动修改=1.0，自动匹配=0.5
    
    for (String keyword : keywords) {
        KeywordWeight kw = keywordWeightMap.get(keyword);
        if (kw == null) {
            kw = new KeywordWeight(keyword);
        }
        
        // 更新统计
        kw.setTotalCount(kw.getTotalCount() + 1);
        kw.setMatchCount(kw.getMatchCount() + weight);
        kw.setSuccessRate(kw.getMatchCount() / kw.getTotalCount());
        
        // 计算新权重：成功率越高，权重越高
        double newWeight = 1.0 + (kw.getSuccessRate() - 0.5) * 2.0;
        kw.setWeight(newWeight);
    }
}
```

### 2. 同义词发现算法

```java
// 伪代码
// 1. 找出相似的项目清单名称
Map<String, List<LearningRecord>> similarItems = groupBySimilarity(records);

// 2. 分析它们匹配到的定额
for (List<LearningRecord> group : similarItems.values()) {
    // 如果相似的项目都匹配到相同的定额，说明它们可能是同义词
    Map<String, Integer> quotaCount = countQuotas(group);
    
    // 如果某个定额出现频率 > 80%，认为是同义词关系
    for (String quota : quotaCount.keySet()) {
        if (quotaCount.get(quota) / group.size() > 0.8) {
            // 提取项目名称中的差异部分作为同义词
            extractSynonyms(group);
        }
    }
}
```

### 3. 匹配模式学习

```java
// 伪代码
// 分析成功匹配的特征
for (LearningRecord record : successRecords) {
    // 提取匹配模式
    MatchPattern pattern = extractPattern(
        record.getItemName(),
        record.getQuotaName(),
        record.getCommonKeywords()
    );
    
    // 如果模式出现频率高，保存为规则
    if (pattern.getFrequency() > threshold) {
        saveMatchingRule(pattern);
    }
}
```

---

## 📈 应用学习结果

### 1. 动态权重调整

```java
// 在匹配算法中使用学习到的权重
private double calculateMatchScoreWithLearning(
    List<String> itemKeywords,
    List<String> quotaKeywords,
    Map<String, Double> keywordWeights) {
    
    double score = 0.0;
    for (String keyword : itemKeywords) {
        if (quotaKeywords.contains(keyword)) {
            // 使用学习到的权重，而不是固定权重
            double weight = keywordWeights.getOrDefault(keyword, 1.0);
            score += weight;
        }
    }
    return score;
}
```

### 2. 同义词扩展

```java
// 使用学习到的同义词扩展匹配
private List<String> expandWithSynonyms(
    List<String> keywords,
    Map<String, Set<String>> synonymMap) {
    
    List<String> expanded = new ArrayList<>(keywords);
    for (String keyword : keywords) {
        Set<String> synonyms = synonymMap.get(keyword);
        if (synonyms != null) {
            expanded.addAll(synonyms);
        }
    }
    return expanded;
}
```

### 3. 模式匹配优先

```java
// 优先使用学习到的匹配模式
private EnterpriseQuota matchWithLearnedPatterns(
    ProjectItem item,
    List<EnterpriseQuota> quotas,
    List<MatchingRule> rules) {
    
    // 先尝试应用高置信度的规则
    for (MatchingRule rule : rules) {
        if (rule.getConfidence() > 0.8) {
            EnterpriseQuota matched = applyRule(item, rule, quotas);
            if (matched != null) {
                return matched;
            }
        }
    }
    
    // 如果没有规则匹配，使用常规算法
    return findBestMatch(item, quotas);
}
```

---

## 🔧 实现步骤

### 第一步：数据收集模块
1. 创建学习记录实体类
2. 在匹配完成后自动收集数据
3. 保存到数据库

### 第二步：分析模块
1. 定期分析学习记录（定时任务）
2. 计算关键词权重
3. 发现同义词关系
4. 提取匹配模式

### 第三步：应用模块
1. 加载学习结果到内存缓存
2. 在匹配算法中应用动态权重
3. 使用同义词扩展匹配
4. 优先应用高置信度规则

### 第四步：反馈机制
1. 记录每次匹配的准确率
2. 对比学习前后的匹配效果
3. 自动调整学习参数

---

## 📊 预期效果

### 短期（1-2周）
- 收集100-500条匹配记录
- 识别常见关键词权重
- 发现10-20组同义词

### 中期（1-2月）
- 收集1000-5000条匹配记录
- 建立关键词权重库
- 发现50-100组同义词
- 匹配准确率提升10-20%

### 长期（3-6月）
- 建立完整的匹配规则库
- 匹配准确率提升30-50%
- 减少手动修改工作量

---

## ⚙️ 配置参数

```properties
# 学习机制配置
matching.learning.enabled=true
matching.learning.auto-match-weight=0.5
matching.learning.manual-match-weight=1.0
matching.learning.min-confidence=0.7
matching.learning.analysis-interval=86400  # 每天分析一次（秒）
matching.learning.min-records=100  # 最少需要100条记录才开始学习
```

---

## 🎯 使用建议

1. **初始阶段**：系统先运行1-2周，收集足够的匹配数据
2. **学习阶段**：定期执行学习分析，逐步建立规则库
3. **优化阶段**：根据匹配效果反馈，调整学习参数
4. **维护阶段**：定期清理过时的规则，保持规则库质量

---

## 📝 注意事项

1. **数据质量**：优先使用手动修改的数据（matchStatus=2），这些是"正确答案"
2. **权重平衡**：避免过度依赖学习结果，保持基础算法的稳定性
3. **规则验证**：新发现的规则需要验证，避免错误规则影响匹配
4. **性能考虑**：学习分析可以异步执行，避免影响正常匹配性能

---

## 🔄 后续扩展

1. **用户反馈机制**：允许用户标记匹配错误，用于学习
2. **A/B测试**：对比不同学习策略的效果
3. **可视化分析**：展示学习过程和匹配效果
4. **规则导出/导入**：支持规则库的备份和迁移

