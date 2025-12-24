# 企业定额自动套用工具

一个基于Java Spring Boot开发的企业定额自动套用工具，支持批量导入企业定额数据和项目清单，自动匹配定额单价，并提供前端界面进行查看和编辑。

## 📋 目录

- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [Excel导入格式说明](#excel导入格式说明)
- [使用流程](#使用流程)
- [API接口说明](#api接口说明)
- [数据库配置](#数据库配置)
- [匹配规则说明](#匹配规则说明)
- [IDE运行指南](#ide运行指南)
- [常见问题](#常见问题)
- [开发说明](#开发说明)

## ✨ 功能特性

### 1. 批量导入功能
- ✅ 支持Excel格式（.xlsx, .xls）批量导入企业定额数据
- ✅ 支持Excel格式批量导入项目清单数据
- ✅ 自动解析Excel文件，跳过表头，从第二行开始读取数据
- ✅ 支持数据验证和错误提示

### 2. 智能匹配功能
- ✅ 根据项目清单名称自动匹配企业定额
- ✅ 根据项目特征值自动匹配企业定额
- ✅ 支持批量匹配操作，一键完成所有清单的匹配
- ✅ 多策略匹配算法，提高匹配准确率

### 3. 数据管理功能
- ✅ 前端页面实时展示所有项目清单及匹配结果
- ✅ 支持手动修改匹配的企业定额
- ✅ 支持直接修改单价（不关联定额）
- ✅ 支持搜索和过滤功能，快速定位数据
- ✅ 显示匹配状态（未匹配/已匹配/手动修改）

### 4. 数据导出功能
- ✅ 导出匹配后的清单数据为Excel格式
- ✅ 包含完整的匹配信息（定额编码、名称、单价、合价等）
- ✅ 自动格式化Excel表格，包含表头样式

## 🛠 技术栈

- **后端框架**: Spring Boot 2.7.14
- **编程语言**: Java 8+
- **数据访问**: Spring Data JPA
- **数据库**: H2 (内存数据库，可切换为MySQL)
- **Excel处理**: Apache POI 5.2.3
- **前端技术**: HTML5, CSS3, JavaScript (原生，无需框架)
- **构建工具**: Maven 3.6+
- **开发工具**: IntelliJ IDEA / Eclipse

## 📁 项目结构

```
quota-matching-tool/
├── src/
│   ├── main/
│   │   ├── java/com/enterprise/quota/
│   │   │   ├── QuotaMatchingApplication.java    # 主启动类
│   │   │   ├── config/
│   │   │   │   └── WebConfig.java                # Web配置（CORS、静态资源）
│   │   │   ├── controller/
│   │   │   │   └── QuotaController.java          # REST API控制器
│   │   │   ├── entity/
│   │   │   │   ├── EnterpriseQuota.java          # 企业定额实体
│   │   │   │   └── ProjectItem.java              # 项目清单实体
│   │   │   ├── repository/
│   │   │   │   ├── EnterpriseQuotaRepository.java  # 企业定额数据访问
│   │   │   │   └── ProjectItemRepository.java      # 项目清单数据访问
│   │   │   └── service/
│   │   │       ├── ExcelImportService.java       # Excel导入服务
│   │   │       ├── ExcelExportService.java       # Excel导出服务
│   │   │       └── QuotaMatchingService.java     # 匹配服务
│   │   └── resources/
│   │       ├── application.properties            # 应用配置
│   │       └── static/
│   │           ├── index.html                    # 前端页面
│   │           ├── style.css                     # 样式文件
│   │           └── app.js                        # 前端脚本
├── pom.xml                                       # Maven配置文件
├── README.md                                     # 项目说明文档
└── .gitignore                                    # Git忽略文件
```

## 🚀 快速开始

### 环境要求

- **JDK**: 1.8 或更高版本
- **Maven**: 3.6 或更高版本
- **IDE**: IntelliJ IDEA / Eclipse（推荐）

### 方式一：使用Maven命令行

1. **克隆或下载项目**
   ```bash
   cd C:\Users\11621\quota-matching-tool
   ```

2. **编译项目**
   ```bash
   mvn clean install
   ```

3. **运行项目**
   ```bash
   mvn spring-boot:run
   ```

4. **访问应用**
   打开浏览器访问：http://localhost:8080

### 方式二：使用IDE（推荐）

1. **打开项目**
   - IntelliJ IDEA: File → Open → 选择项目目录
   - Eclipse: File → Import → Existing Maven Projects

2. **等待依赖下载**
   - IDE会自动识别Maven项目并下载依赖
   - 首次下载可能需要几分钟

3. **运行项目**
   - 找到 `QuotaMatchingApplication.java`
   - 右键 → Run 'QuotaMatchingApplication.main()'
   - 或点击类旁边的绿色运行按钮

4. **访问应用**
   - 启动成功后，在浏览器访问：http://localhost:8080
   - 看到"企业定额自动套用工具"页面即表示启动成功

## 📊 Excel导入格式说明

### 企业定额数据格式

Excel文件应包含以下列（**第一行为表头**，必须严格按照顺序）：

| 列序号 | 列名 | 说明 | 是否必填 | 示例 |
|--------|------|------|---------|------|
| 1 | 定额编码 | 企业定额的唯一编码 | 必填 | Q001 |
| 2 | 定额名称 | 定额的名称（用于匹配） | 必填 | 混凝土浇筑 |
| 3 | 项目特征值 | 项目的特征描述（用于匹配） | 可选 | C30,厚度200mm |
| 4 | 单位 | 计量单位 | 可选 | m³ |
| 5 | 单价 | 定额单价 | 必填 | 350.00 |
| 6 | 人工费 | 人工费用 | 可选 | 120.00 |
| 7 | 材料费 | 材料费用 | 可选 | 200.00 |
| 8 | 机械费 | 机械费用 | 可选 | 30.00 |
| 9 | 备注 | 其他说明信息 | 可选 | 标准混凝土 |

**示例数据**：
```
定额编码	定额名称	项目特征值	单位	单价	人工费	材料费	机械费	备注
Q001	混凝土浇筑	C30厚度200mm	m³	350.00	120.00	200.00	30.00	标准混凝土
Q002	钢筋绑扎	HRB400直径12mm	t	4500.00	1500.00	2800.00	200.00	三级钢筋
```

### 项目清单数据格式

Excel文件应包含以下列（**第一行为表头**，必须严格按照顺序）：

| 列序号 | 列名 | 说明 | 是否必填 | 示例 |
|--------|------|------|---------|------|
| 1 | 清单编码 | 项目清单的唯一编码 | 必填 | I001 |
| 2 | 清单名称 | 清单的名称（用于匹配企业定额） | 必填 | 混凝土浇筑 |
| 3 | 项目特征值 | 项目的特征描述（用于匹配企业定额） | 可选 | C30,厚度200mm |
| 4 | 单位 | 计量单位 | 可选 | m³ |
| 5 | 工程量 | 工程数量 | 必填 | 100.00 |
| 6 | 备注 | 其他说明信息 | 可选 | 基础混凝土 |

**示例数据**：
```
清单编码	清单名称	项目特征值	单位	工程量	备注
I001	混凝土浇筑	C30厚度200mm	m³	100.00	基础混凝土
I002	钢筋绑扎	HRB400直径12mm	t	50.00	主体钢筋
```

### Excel文件要求

- **文件格式**: .xlsx 或 .xls
- **表头位置**: 第一行必须是表头
- **数据位置**: 从第二行开始是数据
- **编码格式**: 建议使用UTF-8编码保存Excel文件
- **空行处理**: 空行会被自动跳过

## 📖 使用流程

### 1. 导入企业定额数据

1. 准备符合格式的Excel文件
2. 在页面上点击"导入企业定额数据"区域的文件选择按钮
3. 选择Excel文件
4. 点击"导入定额"按钮
5. 等待导入完成提示（显示导入的记录数）

### 2. 导入项目清单数据

1. 准备符合格式的Excel文件
2. 在页面上点击"导入项目清单数据"区域的文件选择按钮
3. 选择Excel文件
4. 点击"导入清单"按钮
5. 等待导入完成提示，导入成功后列表会自动刷新

### 3. 批量匹配定额

1. 确保已导入企业定额和项目清单数据
2. 点击"开始批量匹配"按钮
3. 系统会自动根据清单名称和特征值匹配企业定额
4. 匹配完成后会显示匹配数量
5. 列表会自动刷新显示匹配结果

### 4. 查看和编辑匹配结果

1. **查看列表**: 在项目清单列表中查看所有数据及匹配结果
2. **搜索过滤**: 使用搜索框输入关键词，实时过滤数据
3. **编辑匹配**:
   - 点击某条记录的"编辑"按钮
   - 在弹出的对话框中：
     - 可以搜索企业定额并选择新的匹配项
     - 或直接输入单价进行修改
   - 点击"更新单价"或选择定额后自动更新

### 5. 导出匹配结果

1. 点击"导出匹配结果"按钮
2. 系统会自动下载Excel文件（文件名：匹配结果.xlsx）
3. Excel文件包含所有匹配信息，可直接使用

### 6. 清空数据（可选）

- 点击"清空所有数据"按钮可以清空所有导入的数据
- **注意**: 此操作不可恢复，请谨慎使用

## 🔌 API接口说明

### 导入接口

#### 导入企业定额数据
```
POST /api/quota/import-quotas
Content-Type: multipart/form-data

参数:
- file: Excel文件（multipart/form-data）

返回:
{
  "success": true,
  "message": "导入成功，共导入 10 条企业定额数据",
  "count": 10
}
```

#### 导入项目清单数据
```
POST /api/quota/import-items
Content-Type: multipart/form-data

参数:
- file: Excel文件（multipart/form-data）

返回:
{
  "success": true,
  "message": "导入成功，共导入 5 条项目清单数据",
  "count": 5
}
```

### 匹配接口

#### 批量匹配定额
```
POST /api/quota/match

返回:
{
  "success": true,
  "message": "匹配完成，共匹配 5 条项目清单",
  "matchedCount": 5
}
```

### 查询接口

#### 获取所有项目清单
```
GET /api/quota/items

返回: ProjectItem[] 数组
```

#### 获取所有企业定额
```
GET /api/quota/quotas

返回: EnterpriseQuota[] 数组
```

#### 搜索企业定额
```
GET /api/quota/quotas/search?keyword=混凝土

参数:
- keyword: 搜索关键词

返回: EnterpriseQuota[] 数组
```

### 更新接口

#### 更新项目清单的匹配定额
```
PUT /api/quota/items/{itemId}/match?quotaId={quotaId}

参数:
- itemId: 项目清单ID（路径参数）
- quotaId: 企业定额ID（查询参数）

返回:
{
  "success": true,
  "message": "更新成功"
}
```

#### 更新项目清单的单价
```
PUT /api/quota/items/{itemId}/price?unitPrice={price}

参数:
- itemId: 项目清单ID（路径参数）
- unitPrice: 单价（查询参数）

返回:
{
  "success": true,
  "message": "更新成功"
}
```

### 导出接口

#### 导出匹配结果
```
GET /api/quota/export

返回: Excel文件流（application/octet-stream）
```

### 管理接口

#### 清空所有数据
```
DELETE /api/quota/clear

返回:
{
  "success": true,
  "message": "清空成功"
}
```

## 🗄️ 数据库配置

### 使用H2数据库（默认）

项目默认使用H2内存数据库，**无需额外配置**。

**特点**:
- ✅ 无需安装数据库
- ✅ 开箱即用
- ⚠️ 数据仅保存在内存中，应用重启后会丢失
- ⚠️ 适合开发测试环境

**访问H2控制台**（开发时使用）:
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:quota_db`
- 用户名: `sa`
- 密码: （空）

### 使用MySQL数据库（生产环境推荐）

#### 1. 安装MySQL

确保已安装MySQL 5.7+ 或 MySQL 8.0+

#### 2. 创建数据库

```sql
CREATE DATABASE quota_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

#### 3. 修改配置文件

编辑 `src/main/resources/application.properties`：

```properties
# 注释或删除H2配置
# spring.datasource.url=jdbc:h2:mem:quota_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
# spring.datasource.driverClassName=org.h2.Driver
# spring.jpa.database-platform=org.hibernate.dialect.H2Dialect

# 启用MySQL配置
spring.datasource.url=jdbc:mysql://localhost:3306/quota_db?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=your_password
spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect
```

**注意**: 
- 将 `your_password` 替换为你的MySQL密码
- MySQL 5.7使用 `MySQL57Dialect`，MySQL 8.0使用 `MySQL8Dialect`

#### 4. 重启应用

修改配置后需要重启应用才能生效。

## 🎯 匹配规则说明

系统采用**多策略匹配算法**，按以下优先级顺序匹配：

### 策略1：清单名称匹配（优先级最高）
- 使用项目清单名称在企业定额名称中查找包含关系
- 例如：清单名称"混凝土浇筑"会匹配定额名称包含"混凝土浇筑"的定额

### 策略2：特征值匹配
- 如果名称匹配失败，使用项目特征值在企业定额特征值中查找
- 例如：清单特征值"C30"会匹配定额特征值包含"C30"的定额

### 策略3：关键词综合匹配
- 使用清单名称作为关键词，在定额名称或特征值中进行模糊匹配
- 作为兜底策略，提高匹配成功率

### 匹配特点

- ✅ **自动匹配**: 匹配到第一个结果即停止，返回该企业定额
- ✅ **跳过手动修改**: 已手动修改的项目不会被自动匹配覆盖
- ✅ **状态标记**: 自动匹配的项目状态为"已匹配"，手动修改的为"手动修改"
自动提取关键词双向匹配算法
1. 关键词提取工具类 (KeywordExtractor.java)
提取中文关键词（2-6字词组）
去除停用词（的、了、在、是等）
使用滑动窗口提取词组
计算关键词相似度（Jaccard相似度）
计算文本匹配得分
2. 双向匹配算法
方向1：从项目清单匹配到定额（权重40%）
清单名称与定额名称匹配
清单特征值与定额特征值匹配
方向2：从定额匹配到项目清单（权重30%）
提取定额关键词，与清单关键词计算相似度
方向3：特征值匹配（权重30%）
清单特征值与定额特征值的关键词匹配
3. 匹配评分系统
使用加权评分，综合多个维度的匹配度
设置匹配阈值（0.3），只返回得分高于阈值的匹配
按得分排序，选择最佳匹配
4. 版本支持
支持按版本ID过滤定额进行匹配
更新了Repository查询方法
更新了Controller以传递版本ID
5. 向后兼容
保留了无版本ID的匹配方法，确保兼容性
匹配流程
提取项目清单的关键词（从清单名称和特征值）
获取所有定额（如果指定版本，则只获取该版本的定额）
对每个定额计算双向匹配得分
选择得分最高的定额（得分需≥0.3）
更新项目清单的匹配信息
该算法提高了匹配准确性，通过关键词提取和双向匹配，能更好地处理同义词和相似表达。
### 提高匹配准确率的建议

1. **统一命名规范**: 企业定额和项目清单使用相同的命名规范
2. **详细特征值**: 填写详细的项目特征值，包含关键信息
3. **关键词一致**: 确保清单和定额中的关键词保持一致
4. **人工审核**: 重要数据建议人工审核匹配结果

## 💻 IDE运行指南

### IntelliJ IDEA

1. **导入项目**
   - File → Open → 选择项目目录 `C:\Users\11621\quota-matching-tool`
   - 等待Maven自动导入依赖

2. **配置JDK**
   - File → Project Structure → Project
   - 设置Project SDK为JDK 1.8或更高版本
   - 设置Project language level为8或更高

3. **运行项目**
   - 找到 `src/main/java/com/enterprise/quota/QuotaMatchingApplication.java`
   - 右键 → Run 'QuotaMatchingApplication.main()'
   - 或点击类旁边的绿色运行按钮 ▶️

4. **查看日志**
   - 在Run窗口查看启动日志
   - 看到 "Started QuotaMatchingApplication" 表示启动成功

### Eclipse

1. **导入项目**
   - File → Import → Existing Maven Projects
   - 选择项目目录
   - 点击Finish

2. **配置JDK**
   - 右键项目 → Properties → Java Build Path
   - 确保JDK版本为1.8或更高

3. **运行项目**
   - 找到 `QuotaMatchingApplication.java`
   - 右键 → Run As → Java Application

### 常见IDE问题

**问题1**: Maven依赖下载失败
- **解决**: 检查网络连接，或配置Maven镜像源

**问题2**: 端口8080被占用
- **解决**: 修改 `application.properties` 中的 `server.port=8080` 为其他端口

**问题3**: 编译错误
- **解决**: 确保JDK版本正确，执行 `mvn clean install` 重新编译

## ❓ 常见问题

### Q1: 导入Excel时提示格式错误？

**A**: 请检查以下几点：
1. Excel文件第一行必须是表头
2. 列的顺序必须严格按照格式要求
3. 文件格式必须是.xlsx或.xls
4. 确保没有合并单元格
5. 检查数据中是否有特殊字符导致解析失败

### Q2: 匹配结果不准确？

**A**: 可以采取以下措施：
1. 手动编辑匹配结果（点击"编辑"按钮）
2. 调整企业定额数据中的名称和特征值
3. 确保清单和定额使用相同的关键词
4. 对于重要数据，建议人工审核

### Q3: 如何提高匹配准确率？

**A**: 建议：
1. 在企业定额数据中填写详细的名称和特征值
2. 与项目清单保持一致的关键词
3. 使用统一的命名规范
4. 特征值中包含关键信息（如材料规格、尺寸等）

### Q4: 应用启动失败？

**A**: 检查：
1. JDK版本是否正确（需要1.8+）
2. 端口8080是否被占用
3. Maven依赖是否下载完整
4. 查看控制台错误信息

### Q5: 数据丢失了？

**A**: 
- 如果使用H2内存数据库，应用重启后数据会丢失，这是正常现象
- 生产环境建议使用MySQL等持久化数据库

### Q6: 如何修改端口？

**A**: 修改 `src/main/resources/application.properties`：
```properties
server.port=8080  # 改为其他端口，如8081
```

### Q7: 前端页面无法访问？

**A**: 检查：
1. 应用是否成功启动
2. 访问地址是否正确（http://localhost:8080）
3. 浏览器控制台是否有错误
4. 检查网络连接

## 🔧 开发说明

### 项目架构

本项目采用标准的Spring Boot分层架构：

```
Controller层 (QuotaController)
    ↓
Service层 (ExcelImportService, QuotaMatchingService, ExcelExportService)
    ↓
Repository层 (EnterpriseQuotaRepository, ProjectItemRepository)
    ↓
Entity层 (EnterpriseQuota, ProjectItem)
    ↓
Database (H2/MySQL)
```

### 代码结构说明

- **Entity**: 实体类，定义数据模型，使用JPA注解
- **Repository**: 数据访问层，继承JpaRepository，提供CRUD和自定义查询
- **Service**: 业务逻辑层，处理导入、匹配、导出等业务逻辑
- **Controller**: REST API接口层，处理HTTP请求
- **Config**: 配置类，配置CORS、静态资源等
- **前端**: 静态HTML页面，使用原生JavaScript，无需构建工具

### 扩展建议

1. **添加更多匹配策略**: 在 `QuotaMatchingService.findBestMatch()` 中添加
2. **支持更多Excel格式**: 修改 `ExcelImportService` 中的解析逻辑
3. **添加用户认证**: 集成Spring Security
4. **添加日志记录**: 使用Logback或Log4j2
5. **优化前端**: 可以使用Vue.js或React重构前端

### 性能优化建议

1. **大数据量处理**: 对于大量数据，考虑分批导入和匹配
2. **数据库索引**: 在生产环境为常用查询字段添加索引
3. **缓存机制**: 对于频繁查询的数据可以添加缓存
4. **异步处理**: 对于耗时的匹配操作可以考虑异步处理

## 📝 更新日志

### v1.0.0 (2025-12-20)
- ✅ 初始版本发布
- ✅ 支持企业定额和项目清单的批量导入
- ✅ 实现智能匹配功能
- ✅ 提供前端界面进行数据管理
- ✅ 支持Excel格式导出

## 📄 许可证

本项目仅供学习和参考使用。

## 👥 贡献

欢迎提交Issue和Pull Request！

## 📮 联系方式

如有问题或建议，欢迎反馈。

---

**祝使用愉快！** 🎉

