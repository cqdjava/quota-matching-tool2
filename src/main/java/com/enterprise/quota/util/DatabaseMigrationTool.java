package com.enterprise.quota.util;

import com.enterprise.quota.entity.ProjectItem;
import com.enterprise.quota.repository.ProjectItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 独立的数据库迁移工具
 * 可以单独运行来执行数据库迁移
 * 注意：此类不用于Spring Boot应用启动
 */
public class DatabaseMigrationTool {
    
    @Autowired
    private ProjectItemRepository itemRepository;
    
    @Transactional
    public void executeMigration() {
        System.out.println("开始执行数据库迁移...");
        
        try {
            // 获取所有项目清单
            List<ProjectItem> items = itemRepository.findAll();
            
            if (items.isEmpty()) {
                System.out.println("没有项目清单需要初始化排序字段");
                return;
            }
            
            System.out.println("发现 " + items.size() + " 个项目清单，正在初始化排序字段...");
            
            // 为每个项目设置排序值（按ID顺序）
            boolean needsUpdate = false;
            for (int i = 0; i < items.size(); i++) {
                ProjectItem item = items.get(i);
                if (item.getSortOrder() == null) {
                    item.setSortOrder(i + 1);
                    needsUpdate = true;
                }
            }
            
            // 只有在需要更新时才保存
            if (needsUpdate) {
                itemRepository.saveAll(items);
                System.out.println("排序字段初始化完成");
            } else {
                System.out.println("排序字段已经初始化，无需更新");
            }
            
        } catch (Exception e) {
            System.err.println("初始化排序字段时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("数据库迁移完成");
    }
    
    public static void main(String[] args) {
        // 创建Spring应用上下文
        ConfigurableApplicationContext context = SpringApplication.run(DatabaseMigrationTool.class, args);
        
        // 获取迁移工具实例并执行迁移
        DatabaseMigrationTool migrationTool = context.getBean(DatabaseMigrationTool.class);
        migrationTool.executeMigration();
        
        // 关闭应用上下文
        context.close();
        
        System.out.println("迁移工具执行完毕，程序退出");
    }
}