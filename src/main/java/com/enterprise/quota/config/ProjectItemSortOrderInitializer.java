package com.enterprise.quota.config;

import com.enterprise.quota.entity.ProjectItem;
import com.enterprise.quota.repository.ProjectItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 初始化项目清单排序字段
 * 确保所有项目清单都有唯一的sortOrder值
 */
@Component
@Order(2) // 确保在UserInitializer之后执行
public class ProjectItemSortOrderInitializer implements CommandLineRunner {
    
    @Autowired
    private ProjectItemRepository projectItemRepository;
    
    @Override
    @Transactional
    public void run(String... args) throws Exception {
        System.out.println("=== 项目清单排序初始化器启动 ===");
        System.out.println("当前时间: " + LocalDateTime.now());
        
        // 获取所有项目清单
        List<ProjectItem> allItems = projectItemRepository.findAll();
        System.out.println("找到 " + allItems.size() + " 个项目清单");
        
        if (allItems.isEmpty()) {
            System.out.println("没有项目清单需要处理");
            System.out.println("=== 项目清单排序初始化器结束 ===");
            return;
        }
        
        // 检查是否需要重新排序
        boolean needReorder = false;
        for (int i = 0; i < allItems.size(); i++) {
            ProjectItem item = allItems.get(i);
            if (item.getSortOrder() == null || item.getSortOrder() != (i + 1)) {
                needReorder = true;
                break;
            }
        }
        
        if (needReorder) {
            System.out.println("发现排序问题，正在重新分配sortOrder值...");
            
            // 按ID顺序重新分配sortOrder值
            for (int i = 0; i < allItems.size(); i++) {
                ProjectItem item = allItems.get(i);
                item.setSortOrder(i + 1);
                // 每100条记录保存一次，避免内存问题
                if (i % 100 == 0 || i == allItems.size() - 1) {
                    projectItemRepository.saveAll(allItems.subList(Math.max(0, i - 99), i + 1));
                    System.out.println("已处理 " + (i + 1) + " / " + allItems.size() + " 条记录");
                }
            }
            
            System.out.println("排序字段重新分配完成");
        } else {
            System.out.println("排序字段已经正确，无需重新分配");
        }
        
        // 验证结果
        System.out.println("=== 验证排序结果 ===");
        List<ProjectItem> sortedItems = projectItemRepository.findAllByOrderBySortOrderAsc();
        System.out.println("按sortOrder排序后前10条记录:");
        for (int i = 0; i < Math.min(10, sortedItems.size()); i++) {
            ProjectItem item = sortedItems.get(i);
            System.out.println("  " + (i + 1) + ". ID=" + item.getId() + 
                             ", 名称=" + item.getItemName() + 
                             ", sortOrder=" + item.getSortOrder());
        }
        
        System.out.println("=== 项目清单排序初始化器结束 ===");
    }
}