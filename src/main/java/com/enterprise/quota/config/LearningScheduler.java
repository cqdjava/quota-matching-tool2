package com.enterprise.quota.config;

import com.enterprise.quota.service.MatchingLearningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 学习定时任务
 * 定期分析学习数据，更新权重和规则
 */
@Component
public class LearningScheduler {
    
    @Autowired
    private MatchingLearningService learningService;
    
    /**
     * 每天凌晨2点执行学习分析
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyLearningAnalysis() {
        try {
            System.out.println("开始执行学习分析任务...");
            
            // 分析并更新关键词权重
            learningService.analyzeAndUpdateWeights();
            
            // 发现同义词
            learningService.discoverSynonyms();
            
            System.out.println("学习分析任务完成");
        } catch (Exception e) {
            System.err.println("学习分析任务失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

