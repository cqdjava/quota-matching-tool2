package com.enterprise.quota.config;

import com.enterprise.quota.repository.UserRepository;
import com.enterprise.quota.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 初始化默认管理员用户
 */
@Component
public class UserInitializer implements CommandLineRunner {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserService userService;
    
    @Override
    public void run(String... args) throws Exception {
        // 检查是否已有用户
        if (userRepository.count() == 0) {
            // 创建默认管理员用户
            userService.createUser(
                "admin",
                "admin123",
                "系统管理员",
                "admin@example.com"
            );
            System.out.println("==========================================");
            System.out.println("默认管理员用户已创建：");
            System.out.println("用户名：admin");
            System.out.println("密码：admin123");
            System.out.println("==========================================");
        }
    }
}

