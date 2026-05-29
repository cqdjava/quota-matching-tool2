-- 重新为所有项目清单分配唯一的sortOrder值
-- 按照ID顺序分配1,2,3,4...的排序值

SET @row_number = 0;
UPDATE project_item 
SET sort_order = (@row_number := @row_number + 1)
ORDER BY id;

-- 验证结果
SELECT id, item_name, sort_order 
FROM project_item 
ORDER BY sort_order 
LIMIT 10;