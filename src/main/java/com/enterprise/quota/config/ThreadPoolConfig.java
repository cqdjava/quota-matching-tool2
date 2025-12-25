package com.enterprise.quota.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置类
 * 用于优化多线程并行匹配性能
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig {
    
    @Value("${quota.matching.thread-pool.core-size:4}")
    private int corePoolSize;
    
    @Value("${quota.matching.thread-pool.max-size:8}")
    private int maxPoolSize;
    
    @Value("${quota.matching.thread-pool.queue-capacity:1000}")
    private int queueCapacity;
    
    @Value("${quota.matching.thread-pool.keep-alive-seconds:60}")
    private int keepAliveSeconds;
    
    /**
     * 匹配任务线程池
     * 用于并行处理匹配任务
     */
    @Bean(name = "matchingTaskExecutor")
    public Executor matchingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("matching-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
    
    /**
     * 异步任务线程池
     * 用于异步处理学习数据收集等非关键任务
     */
    @Bean(name = "asyncTaskExecutor")
    public Executor asyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

