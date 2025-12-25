-- 更新数据库表结构脚本
-- 将长文本字段从 VARCHAR 改为 LONGTEXT 类型（支持最大 4GB 文本）
-- 适用于 MySQL 5.7+

USE quota_db;

-- 更新 enterprise_quota 表
ALTER TABLE enterprise_quota 
MODIFY COLUMN quota_name LONGTEXT,
MODIFY COLUMN feature_value LONGTEXT,
MODIFY COLUMN remark LONGTEXT;

-- 更新 project_item 表
ALTER TABLE project_item 
MODIFY COLUMN item_name LONGTEXT,
MODIFY COLUMN feature_value LONGTEXT,
MODIFY COLUMN matched_quota_name LONGTEXT,
MODIFY COLUMN matched_quota_feature_value LONGTEXT,
MODIFY COLUMN remark LONGTEXT;

-- 更新 project_item_quota 表（如果存在）
ALTER TABLE project_item_quota 
MODIFY COLUMN quota_name LONGTEXT,
MODIFY COLUMN quota_feature_value LONGTEXT;

-- 验证表结构
DESCRIBE enterprise_quota;
DESCRIBE project_item;
DESCRIBE project_item_quota;

