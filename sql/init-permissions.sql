-- =============================================================
--  按钮级权限初始化（sys_menu 中 menu_type='F' 的记录）
--  后端各写操作接口使用 @PreAuthorize("@permissionChecker.has('...')") 校验，
--  超管(role_key=admin)自动放行，其余角色按此表授权。
-- =============================================================

USE ai_platform;

SET FOREIGN_KEY_CHECKS = 0;

-- 先清理旧的按钮记录，保证可重复执行
DELETE FROM sys_role_menu WHERE menu_id >= 1000;
DELETE FROM sys_menu WHERE menu_id >= 1000;

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, menu_type, visible, perms, status, remark) VALUES
-- 系统管理-用户
(1000, '用户查询',   100, 1, 'F', 1, 'system:user:list',   0, ''),
(1001, '用户新增',   100, 2, 'F', 1, 'system:user:add',    0, ''),
(1002, '用户修改',   100, 3, 'F', 1, 'system:user:edit',   0, ''),
(1003, '用户删除',   100, 4, 'F', 1, 'system:user:remove', 0, ''),
-- 系统管理-角色
(1010, '角色查询',   101, 1, 'F', 1, 'system:role:list',   0, ''),
(1011, '角色新增',   101, 2, 'F', 1, 'system:role:add',    0, ''),
(1012, '角色修改',   101, 3, 'F', 1, 'system:role:edit',   0, ''),
(1013, '角色删除',   101, 4, 'F', 1, 'system:role:remove', 0, ''),
-- 系统管理-菜单
(1020, '菜单查询',   102, 1, 'F', 1, 'system:menu:list',   0, ''),
(1021, '菜单新增',   102, 2, 'F', 1, 'system:menu:add',    0, ''),
(1022, '菜单修改',   102, 3, 'F', 1, 'system:menu:edit',   0, ''),
(1023, '菜单删除',   102, 4, 'F', 1, 'system:menu:remove', 0, ''),
-- 系统管理-部门
(1030, '部门查询',   103, 1, 'F', 1, 'system:dept:list',   0, ''),
(1031, '部门新增',   103, 2, 'F', 1, 'system:dept:add',    0, ''),
(1032, '部门修改',   103, 3, 'F', 1, 'system:dept:edit',   0, ''),
(1033, '部门删除',   103, 4, 'F', 1, 'system:dept:remove', 0, ''),
-- 系统管理-岗位
(1040, '岗位查询',   104, 1, 'F', 1, 'system:post:list',   0, ''),
(1041, '岗位新增',   104, 2, 'F', 1, 'system:post:add',    0, ''),
(1042, '岗位修改',   104, 3, 'F', 1, 'system:post:edit',   0, ''),
(1043, '岗位删除',   104, 4, 'F', 1, 'system:post:remove', 0, ''),
-- 系统管理-字典
(1050, '字典查询',   105, 1, 'F', 1, 'system:dict:list',   0, ''),
(1051, '字典新增',   105, 2, 'F', 1, 'system:dict:add',    0, ''),
(1052, '字典修改',   105, 3, 'F', 1, 'system:dict:edit',   0, ''),
(1053, '字典删除',   105, 4, 'F', 1, 'system:dict:remove', 0, ''),
-- 模型中心
(2000, '模型查询',   200, 1, 'F', 1, 'llm:model:list',     0, ''),
(2001, '模型新增',   200, 2, 'F', 1, 'llm:model:add',      0, ''),
(2002, '模型修改',   200, 3, 'F', 1, 'llm:model:edit',     0, ''),
(2003, '模型删除',   200, 4, 'F', 1, 'llm:model:remove',   0, ''),
(2010, '供应商查询', 201, 1, 'F', 1, 'llm:provider:list',  0, ''),
(2011, '日志查询',   202, 1, 'F', 1, 'llm:log:list',       0, ''),
-- 工作流
(3000, '流程查询',   300, 1, 'F', 1, 'workflow:def:list',    0, ''),
(3001, '流程新增',   300, 2, 'F', 1, 'workflow:def:add',     0, ''),
(3002, '流程修改',   300, 3, 'F', 1, 'workflow:def:edit',    0, ''),
(3003, '流程删除',   300, 4, 'F', 1, 'workflow:def:remove',  0, ''),
(3004, '流程发布',   300, 5, 'F', 1, 'workflow:def:publish', 0, ''),
(3005, '流程执行',   300, 6, 'F', 1, 'workflow:def:run',     0, ''),
(3010, '实例查询',   301, 1, 'F', 1, 'workflow:inst:list',   0, ''),
(3011, '实例重跑',   301, 2, 'F', 1, 'workflow:inst:run',    0, ''),
(3012, '实例停止',   301, 3, 'F', 1, 'workflow:inst:stop',   0, ''),
(3013, '实例删除',   301, 4, 'F', 1, 'workflow:inst:remove', 0, ''),
-- 智能体
(4000, '智能体查询', 400, 1, 'F', 1, 'agent:def:list',    0, ''),
(4001, '智能体新增', 400, 2, 'F', 1, 'agent:def:add',     0, ''),
(4002, '智能体修改', 400, 3, 'F', 1, 'agent:def:edit',    0, ''),
(4003, '智能体删除', 400, 4, 'F', 1, 'agent:def:remove',  0, ''),
(4004, '智能体发布', 400, 5, 'F', 1, 'agent:def:publish', 0, ''),
(4005, '会话删除',   400, 6, 'F', 1, 'agent:conv:remove', 0, ''),
-- 工作管理
(5000, '任务查询',   500, 1, 'F', 1, 'work:task:list',   0, ''),
(5001, '任务新增',   500, 2, 'F', 1, 'work:task:add',    0, ''),
(5002, '任务修改',   500, 3, 'F', 1, 'work:task:edit',   0, ''),
(5003, '任务删除',   500, 4, 'F', 1, 'work:task:remove', 0, ''),
(5010, '日志查询',   501, 1, 'F', 1, 'work:log:list',    0, ''),
(5011, '日志新增',   501, 2, 'F', 1, 'work:log:add',     0, ''),
(5012, '日志修改',   501, 3, 'F', 1, 'work:log:edit',    0, ''),
(5013, '日志删除',   501, 4, 'F', 1, 'work:log:remove',  0, ''),
(5020, '总结查询',   502, 1, 'F', 1, 'work:summary:list',   0, ''),
(5021, '总结生成',   502, 2, 'F', 1, 'work:summary:add',    0, ''),
(5022, '总结删除',   502, 3, 'F', 1, 'work:summary:remove', 0, ''),
(5023, '总结发邮件', 502, 4, 'F', 1, 'work:summary:mail',   0, '');

-- 超级管理员：拥有全部菜单与按钮
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu;

-- 部门主管：系统管理只读 + 模型/工作流/智能体/工作 全部可操作
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 2, menu_id FROM sys_menu
WHERE (menu_id >= 1000 AND menu_id < 1060)
   OR (menu_id >= 2000 AND menu_id < 2100)
   OR (menu_id >= 3000 AND menu_id < 3100)
   OR (menu_id >= 4000 AND menu_id < 4100)
   OR (menu_id >= 5000 AND menu_id < 5100)
   OR menu_id IN (1, 2, 3, 4, 5, 100, 103, 200, 201, 202, 300, 301, 400, 500, 501, 502);

-- 普通员工：工作日志可维护、模型只读、智能体与工作流只读
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 3, menu_id FROM sys_menu
WHERE menu_id IN (5, 500, 501, 502,
                   5000, 5010, 5011, 5012, 5013, 5020, 5021, 5023,
                   4, 400, 4000,
                   3, 300, 301, 3000, 3010,
                   2, 200, 201, 2000, 2010);

SET FOREIGN_KEY_CHECKS = 1;

SELECT COUNT(1) AS button_permissions FROM sys_menu WHERE menu_type = 'F';
