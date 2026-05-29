package com.enterprise.quota.util;

import com.enterprise.quota.entity.ProjectItem;
import com.enterprise.quota.repository.ProjectItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 数据库初始化和迁移工具类
 * 在应用程序启动时自动执行数据库迁移
 */
@Component
public class DatabaseMigrationRunner implements CommandLineRunner {
    
    @Autowired
    private ProjectItemRepository itemRepository;
    
    @Override
    @Transactional
    public void run(String... args) throws Exception {
        System.out.println("=== 数据库迁移工具启动 ===");
        System.out.println("当前时间: " + new java.util.Date());
        
        try {
            // 检查是否需要初始化排序字段
            initializeSortOrder();
            System.out.println("数据库迁移完成");
        } catch (Exception e) {
            System.err.println("数据库迁移过程中发生错误:");
            e.printStackTrace();
        }
        
        System.out.println("=== 数据库迁移工具结束 ===");
    }
    
    /**
     * 初始化项目清单的排序字段
     */
    private void initializeSortOrder() {
        try {
            System.out.println("开始查询项目清单...");
            
            // 获取所有项目清单
            List<ProjectItem> items = itemRepository.findAll();
            System.out.println("查询完成，找到 " + items.size() + " 个项目清单");
            
            if (items.isEmpty()) {
                System.out.println("没有项目清单需要初始化排序字段");
                return;
            }
            
            System.out.println("发现 " + items.size() + " 个项目清单，正在初始化排序字段...");
            
            // 统计需要更新的记录数
            int updateCount = 0;
            
            // 为每个项目设置排序值（按ID顺序）
            for (int i = 0; i < items.size(); i++) {
                ProjectItem item = items.get(i);
                if (item.getSortOrder() == null) {
                    item.setSortOrder(i + 1);
                    updateCount++;
                    System.out.println("更新项目 ID: " + item.getId() + ", 设置排序值: " + (i + 1));
                }
            }
            
            System.out.println("需要更新 " + updateCount + " 条记录");
            
            if (updateCount > 0) {
                // 批量保存更新
                System.out.println("开始保存更新...");
                itemRepository.saveAll(items);
                System.out.println("保存完成");
            }
            
            System.out.println("排序字段初始化完成");
            
        } catch (Exception e) {
            System.err.println("初始化排序字段时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}