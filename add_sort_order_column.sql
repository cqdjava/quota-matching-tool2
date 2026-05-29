-- 为project_item表添加sort_order字段
ALTER TABLE project_item ADD COLUMN sort_order INT DEFAULT 0;

-- 为现有记录设置排序值（按id顺序）
SET @row_number = 0;
UPDATE project_item 
SET sort_order = (@row_number := @row_number + 1) 
ORDER BY id;

-- 创建索引以提高排序查询性能
CREATE INDEX idx_project_item_user_sort ON project_item (user_id, sort_order);