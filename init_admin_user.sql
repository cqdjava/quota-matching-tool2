-- 初始化管理员用户角色
-- 如果admin用户不存在，则创建一个
INSERT INTO sys_user (username, password, real_name, email, status, role, create_time, update_time) 
SELECT 'admin', 
       '8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918', -- 密码 'admin'
       '系统管理员', 
       'admin@company.com', 
       1, 
       'admin',
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');

-- 确保已存在的admin用户具有正确的角色
UPDATE sys_user 
SET role = 'admin' 
WHERE username = 'admin';

-- 验证用户表中的用户信息
SELECT id, username, real_name, email, status, role, create_time, update_time 
FROM sys_user 
WHERE username = 'admin';