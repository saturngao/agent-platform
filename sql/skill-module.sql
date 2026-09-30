-- ===========================================================
-- Skill 技能模块：建表 + 菜单 + 内置官方技能
-- 技能可被智能体（Agent）和工作流（Workflow）挂载，调用大模型时
-- 把技能内容注入 systemPrompt，从而让模型具备特定领域的做事方法。
-- ===========================================================
USE ai_platform;

-- ---------------------------- 技能表 ----------------------------
DROP TABLE IF EXISTS skill;
CREATE TABLE skill (
  skill_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '技能ID',
  skill_code  VARCHAR(64)  NOT NULL                COMMENT '技能编码（唯一）',
  skill_name  VARCHAR(128) NOT NULL                COMMENT '技能名称',
  category    VARCHAR(64)  DEFAULT NULL            COMMENT '分类：办公/研发/运维/数据/写作',
  icon        VARCHAR(32)  DEFAULT '🧩'                COMMENT '图标 emoji',
  version     VARCHAR(32)  DEFAULT '1.0.0'         COMMENT '版本',
  author      VARCHAR(64)  DEFAULT NULL            COMMENT '作者/来源方',
  source      VARCHAR(16)  NOT NULL DEFAULT 'CUSTOM' COMMENT '来源：OFFICIAL官方/IMPORT导入/CUSTOM自建',
  summary     VARCHAR(500) DEFAULT NULL            COMMENT '一句话简介',
  content     MEDIUMTEXT                           COMMENT '技能正文（SKILL.md 全文）',
  tags        VARCHAR(255) DEFAULT NULL            COMMENT '标签，逗号分隔',
  remark      VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  status      TINYINT      NOT NULL DEFAULT 0      COMMENT '状态 0正常 1停用',
  del_flag    TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标记 0存在 2删除',
  dept_id     BIGINT       DEFAULT NULL            COMMENT '归属部门（数据权限）',
  create_by   VARCHAR(64)  DEFAULT NULL,
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_by   VARCHAR(64)  DEFAULT NULL,
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (skill_id),
  UNIQUE KEY uk_skill_code (skill_code),
  KEY idx_skill_status (status, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技能表';

-- ------------------------ 技能绑定表 ------------------------
-- 记录某个技能被挂到哪个智能体 / 工作流上
DROP TABLE IF EXISTS skill_binding;
CREATE TABLE skill_binding (
  bind_id     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '绑定ID',
  skill_id    BIGINT      NOT NULL                COMMENT '技能ID',
  biz_type    VARCHAR(16) NOT NULL                COMMENT '业务类型：AGENT 智能体 / WORKFLOW 工作流',
  biz_id      BIGINT      NOT NULL                COMMENT '业务对象ID（agent_id 或 def_id）',
  create_by   VARCHAR(64) DEFAULT NULL,
  create_time DATETIME    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (bind_id),
  UNIQUE KEY uk_skill_biz (skill_id, biz_type, biz_id),
  KEY idx_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技能与业务对象绑定表';

-- ---------------------------- 菜单 ----------------------------
DELETE FROM sys_role_menu WHERE menu_id IN (600, 6000, 6001, 6002, 6003);
DELETE FROM sys_menu WHERE menu_id IN (600, 6000, 6001, 6002, 6003);

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, remark, create_by)
VALUES
  (600, '技能库',   0, 6, '/skill', NULL,                 'M', 0, 0, '',                 'Grid',    '技能库目录', 'admin'),
  (6000,'技能管理', 600, 1, 'list',  'skill/list/index',  'C', 0, 0, 'skill:def:list',   'MagicStick','技能管理', 'admin');

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, menu_type, visible, status, perms, create_by)
VALUES
  (6001, '技能查询', 6000, 1, 'F', 0, 0, 'skill:def:list',   'admin'),
  (6002, '技能新增', 6000, 2, 'F', 0, 0, 'skill:def:add',    'admin'),
  (6003, '技能修改', 6000, 3, 'F', 0, 0, 'skill:def:edit',   'admin'),
  (6004, '技能删除', 6000, 4, 'F', 0, 0, 'skill:def:remove', 'admin');

-- 授权：超级管理员与部门主管
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.menu_id IN (600, 6000, 6001, 6002, 6003, 6004)
WHERE r.role_id IN (1, 2)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- 普通员工只读
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, m.menu_id
FROM sys_menu m
WHERE m.menu_id IN (600, 6000, 6001)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 3 AND rm.menu_id = m.menu_id);

-- ---------------------- 内置官方技能示例 ----------------------
DELETE FROM skill WHERE source = 'OFFICIAL';
INSERT INTO skill
  (skill_code, skill_name, category, icon, version, author, source, summary, content, tags, status, create_by)
VALUES
('weekly-report', '周报撰写助手', '办公', '📝', '1.0.0', '平台内置', 'OFFICIAL',
 '把零散的工作记录整理成结构清晰的周报',
 '# 周报撰写助手\n\n## 角色\n你是一名擅长归纳总结的项目助理。\n\n## 输入\n用户会提供一段时间内的零散工作记录、日志或任务进展。\n\n## 输出规范\n请按以下结构输出 Markdown 周报：\n\n## 一、本周概览\n用 2-3 句话概括本周整体进展。\n\n## 二、关键进展\n按重要性列出 3-5 条，每条包含：做了什么、结果如何。\n\n## 三、风险与阻塞\n列出遇到的阻碍，没有就写"无"。\n\n## 四、下周计划\n列出 3-5 条具体可执行的下一步。\n\n## 约束\n- 只依据用户提供的信息，不要编造未提及的事实\n- 语言简洁，多用动词开头\n- 涉及数字时保留原文中的数字\n',
 '周报,总结,办公', 0, 'admin'),

('meeting-minutes', '会议纪要整理', '办公', '🗒️', '1.0.0', '平台内置', 'OFFICIAL',
 '把会议记录整理成标准纪要，含结论与待办',
 '# 会议纪要整理\n\n## 角色\n你是专业的会议记录员。\n\n## 输入\n用户提供会议讨论的原始记录（可能很零散、口语化）。\n\n## 输出规范\n\n## 会议信息\n- 主题：\n- 参会人：\n- 时间：\n\n## 核心结论\n逐条列出会议达成的结论。\n\n## 待办事项\n用表格输出：| 事项 | 负责人 | 截止时间 |\n\n## 待决议问题\n记录尚未达成共识、需要后续跟进的问题。\n\n## 约束\n- 事实与观点要区分开\n- 待办必须可指派、可验收\n',
 '会议,纪要,协作', 0, 'admin'),

('code-review', '代码评审助手', '研发', '🔍', '1.0.0', '平台内置', 'OFFICIAL',
 '对给定代码给出结构化的评审意见',
 '# 代码评审助手\n\n## 角色\n你是一名资深工程师，负责代码评审。\n\n## 输入\n用户提供一段代码（可能附带语言与背景说明）。\n\n## 输出规范\n\n## 总体评价\n一句话总结代码质量。\n\n## 问题清单\n按严重程度分组输出：\n- **阻塞**：会导致 bug 或安全问题\n- **建议**：可读性、性能、规范方面\n- **可选**：风格偏好\n\n每条给出：位置、问题、修改建议（附代码片段）。\n\n## 亮点\n指出写得好的地方。\n\n## 约束\n- 不要为了凑数而编造问题\n- 修改建议必须可直接套用\n',
 '代码,评审,研发', 0, 'admin'),

('sql-expert', 'SQL 编写与优化', '数据', '🗄️', '1.0.0', '平台内置', 'OFFICIAL',
 '根据需求编写 SQL，并给出优化建议',
 '# SQL 编写与优化\n\n## 角色\n你是资深数据库工程师，精通 MySQL。\n\n## 输入\n用户描述查询需求，可能提供表结构。\n\n## 输出规范\n\n## SQL\n给出可直接执行的 SQL，用 ```sql 代码块包裹。\n\n## 思路说明\n简要解释关键逻辑。\n\n## 索引建议\n指出应该建立或调整的索引。\n\n## 性能提示\n指出潜在的全表扫描、隐式类型转换、分页深翻等问题。\n\n## 约束\n- 默认 MySQL 语法\n- 涉及删除/更新时必须提醒先备份或在事务中验证\n',
 'SQL,数据库,优化', 0, 'admin'),

('trouble-shoot', '故障排查助手', '运维', '🚨', '1.0.0', '平台内置', 'OFFICIAL',
 '按结构化方法定位系统故障根因',
 '# 故障排查助手\n\n## 角色\n你是资深 SRE，擅长系统性定位故障。\n\n## 输入\n用户提供故障现象、报错信息、或监控指标异常。\n\n## 输出规范\n\n## 影响范围\n先判断影响面与紧急程度。\n\n## 排查路径\n按"最可能 → 最不可能"给出 3-5 步排查清单，每步写明：\n- 要执行的命令/检查项\n- 期望看到什么\n- 结果如何解读\n\n## 可能根因\n给出 2-3 个候选根因及判断依据。\n\n## 止血方案\n给出可立即执行的恢复动作（与根治方案分开）。\n\n## 约束\n- 优先给出无损、可回滚的操作\n- 涉及生产环境的操作必须提醒风险\n',
 '运维,故障,排查', 0, 'admin');

-- 校验
SELECT skill_id, skill_code, skill_name, source, status FROM skill;
SELECT m.menu_id, m.menu_name, m.path, m.component, m.perms FROM sys_menu m WHERE m.menu_id = 600 OR m.parent_id = 600;
