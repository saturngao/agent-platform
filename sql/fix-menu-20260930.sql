-- ===========================================================
-- 修复：补齐「参数配置」菜单 + 清理测试残留角色
-- 背景：系统管理下缺少「参数配置」菜单，导致 /system/config 无路由 -> 跳 404
-- ===========================================================
USE ai_platform;

-- 1. 清理冒烟测试遗留的角色
DELETE FROM sys_user_role WHERE role_id IN (SELECT role_id FROM sys_role WHERE role_key = 'smoke_role');
DELETE FROM sys_role_dept WHERE role_id IN (SELECT role_id FROM sys_role WHERE role_key = 'smoke_role');
DELETE FROM sys_role_menu WHERE role_id IN (SELECT role_id FROM sys_role WHERE role_key = 'smoke_role');
DELETE FROM sys_role WHERE role_key = 'smoke_role';

-- 2. 补齐「参数配置」菜单（挂在 系统管理 menu_id=1 下）
INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, remark, create_by)
VALUES
  (106, '参数配置', 1, 7, 'config', 'system/config/index', 'C', 0, 0, 'system:config:list', 'Tools', '系统参数配置（含邮件 SMTP 参数）', 'admin');

-- 3. 参数配置的按钮级权限
INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, menu_type, visible, status, perms, create_by)
VALUES
  (1060, '参数查询', 106, 1, 'F', 0, 0, 'system:config:list',   'admin'),
  (1061, '参数新增', 106, 2, 'F', 0, 0, 'system:config:add',    'admin'),
  (1062, '参数修改', 106, 3, 'F', 0, 0, 'system:config:edit',   'admin'),
  (1063, '参数删除', 106, 4, 'F', 0, 0, 'system:config:remove', 'admin');

-- 4. 授权给 超级管理员(1) 与 部门主管(2)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.menu_id IN (106, 1060, 1061, 1062, 1063)
WHERE r.role_id IN (1, 2)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );

-- 5. 邮件 SMTP 参数占位（供 AI 总结发邮件使用，值为空表示未配置）
INSERT INTO sys_config (config_name, config_key, config_value, config_type, remark, create_by)
SELECT * FROM (
  SELECT '邮件服务器地址' AS config_name, 'sys.mail.host'     AS config_key, '' AS config_value, 'N' AS config_type, '如 smtp.qq.com' AS remark, 'admin' AS create_by
  UNION ALL SELECT '邮件服务器端口', 'sys.mail.port',     '465',  'N', 'SSL 一般 465', 'admin'
  UNION ALL SELECT '邮件发件账号',   'sys.mail.username', '',     'N', '发件邮箱地址', 'admin'
  UNION ALL SELECT '邮件密码/授权码','sys.mail.password', '',     'N', '邮箱授权码，非登录密码', 'admin'
  UNION ALL SELECT '发件人显示名',   'sys.mail.fromName', 'AI 工作流平台', 'N', '邮件 sender 显示名', 'admin'
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.config_key = t.config_key);

-- 校验
SELECT menu_id, menu_name, parent_id, path, component, perms, menu_type
FROM sys_menu WHERE menu_id = 106 OR parent_id = 106 ORDER BY menu_id;
SELECT r.role_key, COUNT(*) AS granted
FROM sys_role_menu rm JOIN sys_role r ON r.role_id = rm.role_id
WHERE rm.menu_id IN (106, 1060, 1061, 1062, 1063)
GROUP BY r.role_key;
