-- ===========================================================
-- Skill 脚本能力：技能可挂载多个 Python 脚本
-- 1) skill_script      技能脚本定义
-- 2) skill_script_log  脚本执行记录
-- 3) sys_menu 增加「脚本执行」按钮权限并授权
-- ===========================================================
USE ai_platform;

-- ---------------------- 技能脚本表 ----------------------
CREATE TABLE IF NOT EXISTS skill_script (
  script_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '脚本ID',
  skill_id     BIGINT       NOT NULL                COMMENT '所属技能ID',
  script_code  VARCHAR(64)  NOT NULL                COMMENT '脚本编码（技能内唯一，模型调用时使用）',
  script_name  VARCHAR(128) NOT NULL                COMMENT '脚本名称',
  description  VARCHAR(500) DEFAULT NULL            COMMENT '用途说明（给大模型看，决定何时调用）',
  param_desc   TEXT                                 COMMENT '参数说明，建议写 JSON 样例',
  language     VARCHAR(16)  NOT NULL DEFAULT 'python' COMMENT '脚本语言，当前仅支持 python',
  content      MEDIUMTEXT                           COMMENT '脚本源码',
  timeout_sec  INT          NOT NULL DEFAULT 30     COMMENT '超时秒数',
  status       TINYINT      NOT NULL DEFAULT 0      COMMENT '状态 0正常 1停用',
  del_flag     TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标记 0存在 2删除',
  create_by    VARCHAR(64)  DEFAULT NULL,
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_by    VARCHAR(64)  DEFAULT NULL,
  update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  remark       VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  PRIMARY KEY (script_id),
  UNIQUE KEY uk_skill_script_code (skill_id, script_code),
  KEY idx_skill (skill_id, status, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技能脚本表';

-- -------------------- 脚本执行记录表 --------------------
CREATE TABLE IF NOT EXISTS skill_script_log (
  log_id      BIGINT      NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  script_id   BIGINT      DEFAULT NULL            COMMENT '脚本ID（在线调试时为空）',
  skill_id    BIGINT      DEFAULT NULL            COMMENT '所属技能ID',
  script_code VARCHAR(64) DEFAULT NULL            COMMENT '脚本编码快照',
  biz_type    VARCHAR(32) DEFAULT NULL            COMMENT '触发来源：MANUAL手工/AGENT对话',
  biz_id      VARCHAR(64) DEFAULT NULL            COMMENT '触发对象ID（智能体ID等）',
  input_params TEXT                               COMMENT '入参 JSON',
  exit_code   INT         DEFAULT NULL            COMMENT '进程退出码',
  output_text MEDIUMTEXT                          COMMENT '标准输出',
  error_text  MEDIUMTEXT                          COMMENT '错误输出',
  cost_ms     INT         DEFAULT NULL            COMMENT '耗时毫秒',
  status      TINYINT     NOT NULL DEFAULT 0      COMMENT '0成功 1失败',
  create_by   VARCHAR(64) DEFAULT NULL,
  create_time DATETIME    DEFAULT CURRENT_TIMESTAMP,
  update_by   VARCHAR(64) DEFAULT NULL           COMMENT '更新人（BaseEntity 审计字段）',
  update_time DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  remark      VARCHAR(500) DEFAULT NULL           COMMENT '备注',
  PRIMARY KEY (log_id),
  KEY idx_script (script_id, log_id),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技能脚本执行记录';

-- ---------------------- 按钮权限 ----------------------
DELETE FROM sys_role_menu WHERE menu_id IN (6005, 6006);
DELETE FROM sys_menu WHERE menu_id IN (6005, 6006);

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, menu_type, visible, status, perms, create_by)
VALUES
  (6005, '脚本执行', 6000, 5, 'F', 0, 0, 'skill:script:run',   'admin'),
  (6006, '脚本维护', 6000, 6, 'F', 0, 0, 'skill:script:edit',  'admin');

-- 授权：超级管理员与部门主管
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.menu_id IN (6005, 6006)
WHERE r.role_id IN (1, 2)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- ------------------ 示例：数据分析技能脚本 ------------------
-- 给「SQL 编写与优化」技能挂一个可执行的示例脚本（若该技能存在）
SET @skill_id := (SELECT skill_id FROM skill WHERE skill_code = 'sql-expert' AND del_flag = 0 LIMIT 1);

INSERT INTO skill_script
  (skill_id, script_code, script_name, description, param_desc, language, content, timeout_sec, status, create_by)
SELECT
  @skill_id,
  'explain_cost',
  'SQL 成本估算',
  '根据传入的表行数与过滤条件估算 SQL 的扫描行数与大致执行成本，输出 JSON。',
  '{"table_rows": 100000, "selectivity": 0.01, "has_index": true}',
  'python',
  '# 示例脚本：入参从同目录 input.json 读取，也可读环境变量 SKILL_PARAMS\n'
  'import json, os\n'
  '\n'
  'raw = os.environ.get("SKILL_PARAMS") or "{}"\n'
  'try:\n'
  '    with open("input.json", "r", encoding="utf-8") as f:\n'
  '        raw = f.read()\n'
  'except Exception:\n'
  '    pass\n'
  '\n'
  'try:\n'
  '    args = json.loads(raw)\n'
  'except Exception:\n'
  '    args = {}\n'
  '\n'
  'rows = float(args.get("table_rows", 0) or 0)\n'
  'sel = float(args.get("selectivity", 1) or 1)\n'
  'has_index = bool(args.get("has_index", False))\n'
  '\n'
  'scanned = rows if not has_index else max(1.0, rows * sel)\n'
  'cost = round(scanned / 10000.0, 2)\n'
  '\n'
  'print(json.dumps({\n'
  '    "table_rows": rows,\n'
  '    "has_index": has_index,\n'
  '    "estimated_scanned_rows": int(scanned),\n'
  '    "estimated_cost": cost,\n'
  '    "advice": "命中索引，扫描量可控" if has_index and scanned < rows * 0.1 else "建议补充或调整索引"\n'
  '}, ensure_ascii=False))\n',
  20,
  0,
  'admin'
WHERE @skill_id IS NOT NULL;

-- 校验
SELECT script_id, skill_id, script_code, script_name, status FROM skill_script;
