# 性能优化说明文档

## 📊 优化目标

针对 **4核CPU、4G内存** 的服务器进行性能优化，充分利用硬件资源，提升匹配速度和系统吞吐量。

## 🚀 优化内容

### 1. 多线程并行匹配

**优化前：**
- 单线程顺序处理所有项目清单
- CPU利用率低（仅使用1个核心）
- 匹配2000条数据需要数分钟

**优化后：**
- 使用线程池并行处理多个批次
- 充分利用4核CPU（默认4个核心线程，最大8个）
- 匹配速度提升 **3-4倍**

**实现方式：**
- 将项目清单分割成多个批次（默认每批200条）
- 使用 `ThreadPoolTaskExecutor` 并行处理
- 使用 `CountDownLatch` 同步等待所有批次完成
- 使用线程安全的集合收集结果

**配置参数：**
```properties
quota.matching.thread-pool.core-size=4      # 核心线程数（等于CPU核心数）
quota.matching.thread-pool.max-size=8        # 最大线程数
quota.matching.thread-pool.queue-capacity=1000 # 队列容量
quota.matching.batch-size=200                # 每批处理数量
```

### 2. 数据库连接池优化

**优化前：**
- 使用默认连接池配置
- 连接数较少，可能成为瓶颈

**优化后：**
- 配置 HikariCP 连接池
- 最小连接数：5
- 最大连接数：20（适合4核服务器）
- 连接泄漏检测：60秒

**配置参数：**
```properties
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
spring.datasource.hikari.leak-detection-threshold=60000
```

### 3. JPA批量处理优化

**优化前：**
- 默认批量大小较小
- 每次保存可能产生多次数据库交互

**优化后：**
- 批量大小：50
- 启用批量插入和更新排序
- 启用版本化数据批量处理

**配置参数：**
```properties
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
spring.jpa.properties.hibernate.jdbc.batch_versioned_data=true
```

### 4. JVM参数优化

**优化前：**
- 默认堆内存较小（512m-1g）
- 使用串行GC或并行GC
- GC暂停时间不可控

**优化后：**
- 初始堆内存：2G
- 最大堆内存：3G（为系统保留1G）
- 使用 G1 垃圾回收器（适合多核服务器）
- 最大GC暂停时间：200ms
- GC并行线程数：4（等于CPU核心数）
- 启用字符串去重和压缩指针

**JVM参数：**
```bash
-Xms2g                                    # 初始堆内存
-Xmx3g                                    # 最大堆内存
-XX:+UseG1GC                              # 使用G1垃圾回收器
-XX:MaxGCPauseMillis=200                  # 最大GC暂停时间
-XX:ParallelGCThreads=4                   # GC并行线程数
-XX:ConcGCThreads=2                       # 并发GC线程数
-XX:+UseStringDeduplication               # 字符串去重
-XX:+OptimizeStringConcat                 # 优化字符串连接
-XX:+UseCompressedOops                    # 使用压缩指针
-XX:+UseCompressedClassPointers           # 使用压缩类指针
```

### 5. 异步处理学习数据

**优化前：**
- 学习数据收集在主线程中同步执行
- 阻塞匹配流程

**优化后：**
- 使用 `@Async` 异步收集学习数据
- 不阻塞主匹配流程
- 使用独立的异步线程池

**实现方式：**
- 创建独立的异步任务线程池（2-4个线程）
- 使用 `@Async("asyncTaskExecutor")` 注解
- 学习数据收集失败不影响匹配结果

### 6. 批量保存优化

**优化前：**
- 每批保存100条
- 可能产生较多数据库交互

**优化后：**
- 可配置的批量保存大小（默认100条）
- 批量保存失败时自动降级为逐条保存
- 使用事务保证数据一致性

**配置参数：**
```properties
quota.matching.save-batch-size=100  # 批量保存大小
```

## 📈 性能提升

### 预期提升效果

| 指标 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| 匹配速度（2000条） | 3-5分钟 | 1-2分钟 | **2-3倍** |
| CPU利用率 | 25%（1核） | 80-90%（4核） | **3-4倍** |
| 内存利用率 | 低 | 优化 | **更高效** |
| GC暂停时间 | 不可控 | <200ms | **可控** |
| 数据库连接效率 | 低 | 高 | **提升** |

### 实际测试建议

1. **小规模测试**（100-500条）
   - 验证功能正确性
   - 检查多线程同步是否正确

2. **中规模测试**（1000-2000条）
   - 验证性能提升
   - 监控CPU和内存使用情况

3. **大规模测试**（5000+条）
   - 验证系统稳定性
   - 检查是否有内存泄漏

## 🔧 使用方法

### 1. 使用优化启动脚本

**Linux服务器：**
```bash
chmod +x start-optimized.sh
./start-optimized.sh
```

**Windows开发环境：**
```cmd
start-optimized.bat
```

### 2. 使用Systemd服务（生产环境）

部署脚本已自动配置优化的JVM参数：
```bash
./deploy-linux.sh
```

服务文件中的JVM参数已优化为：
```ini
Environment="JAVA_OPTS=-Xms2g -Xmx3g -XX:+UseG1GC ..."
```

### 3. 手动配置JVM参数

如果使用其他方式启动，请确保包含以下JVM参数：
```bash
java -Xms2g -Xmx3g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
     -XX:ParallelGCThreads=4 -XX:ConcGCThreads=2 \
     -XX:+UseStringDeduplication -XX:+OptimizeStringConcat \
     -XX:+UseCompressedOops -XX:+UseCompressedClassPointers \
     -Djava.awt.headless=true -Dfile.encoding=UTF-8 \
     -Duser.timezone=Asia/Shanghai \
     -jar quota-matching-tool-1.0.0.jar
```

## 📝 配置调优建议

### 根据实际负载调整

**如果CPU利用率仍然较低：**
- 增加 `quota.matching.thread-pool.core-size`（但不超过CPU核心数）
- 减少 `quota.matching.batch-size`（增加并行度）

**如果内存使用过高：**
- 减少 `-Xmx` 参数（但至少保留2G）
- 减少 `spring.datasource.hikari.maximum-pool-size`

**如果数据库成为瓶颈：**
- 增加 `spring.datasource.hikari.maximum-pool-size`
- 优化数据库索引
- 考虑读写分离

**如果GC暂停时间过长：**
- 减少 `-Xmx` 参数
- 调整 `-XX:MaxGCPauseMillis`
- 考虑使用 ZGC（Java 11+）

## ⚠️ 注意事项

1. **内存限制**
   - 最大堆内存设置为3G，为系统和其他进程保留1G
   - 如果系统内存不足4G，需要相应调整

2. **线程数限制**
   - 核心线程数不应超过CPU核心数
   - 最大线程数建议为核心数的2倍

3. **数据库连接数**
   - 连接数过多可能导致数据库压力过大
   - 建议根据数据库服务器性能调整

4. **监控和日志**
   - 建议启用应用监控（如Spring Boot Actuator）
   - 定期检查GC日志和性能指标

## 🔍 性能监控

### 查看JVM内存使用
```bash
jstat -gc <pid> 1000 10
```

### 查看线程状态
```bash
jstack <pid>
```

### 查看GC日志
在JVM参数中添加：
```bash
-Xlog:gc*:file=gc.log:time,level,tags
```

### Spring Boot Actuator
访问：`http://localhost:8080/actuator/metrics`

## 📚 参考资料

- [G1垃圾回收器调优](https://docs.oracle.com/javase/9/gctuning/g1-garbage-collector-tuning.htm)
- [HikariCP配置指南](https://github.com/brettwooldridge/HikariCP)
- [Spring Boot性能优化](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.spring-application.application-properties.performance)

