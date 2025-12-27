-- 更新admin用户的角色为管理员
UPDATE sys_user 
SET role = 'admin' 
WHERE username = 'admin';

-- 检查所有用户的角色设置
SELECT id, username, role, status FROM sys_user;