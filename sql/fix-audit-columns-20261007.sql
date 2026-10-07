-- =============================================================
-- 补齐 BaseEntity 审计字段缺失列（2026-10-07）
--
-- 背景：所有继承 BaseEntity 的实体都会被 MyBatis-Plus 拼上
--       create_by / create_time / update_by / update_time，
--       但 wf_instance、skill_script_log 建表时漏了这些列，
--       导致任何走 MP 自动生成 SQL 的调用（selectById 等）
--       都会报 Unknown column 'xxx' in 'SELECT'。
--       现象：运行实例的「详情」「重跑」按钮点开就 500。
--
-- 本脚本幂等，可重复执行。
-- =============================================================

-- -------------------- wf_instance --------------------
SET @exist := (SELECT COUNT(1) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_instance' AND COLUMN_NAME = 'create_by');
SET @sql := IF(@exist = 0,
    'ALTER TABLE wf_instance ADD COLUMN create_by VARCHAR(64) DEFAULT NULL COMMENT ''创建人'' AFTER username',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(1) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_instance' AND COLUMN_NAME = 'create_time');
SET @sql := IF(@exist = 0,
    'ALTER TABLE wf_instance ADD COLUMN create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'' AFTER create_by',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(1) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_instance' AND COLUMN_NAME = 'update_by');
SET @sql := IF(@exist = 0,
    'ALTER TABLE wf_instance ADD COLUMN update_by VARCHAR(64) DEFAULT NULL COMMENT ''更新人'' AFTER create_time',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(1) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wf_instance' AND COLUMN_NAME = 'update_time');
SET @sql := IF(@exist = 0,
    'ALTER TABLE wf_instance ADD COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER update_by',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 历史数据：把创建时间回填成实例开始时间，避免列表里出现空值
UPDATE wf_instance SET create_time = start_time WHERE create_time IS NULL;

-- -------------------- skill_script_log --------------------
SET @exist := (SELECT COUNT(1) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skill_script_log' AND COLUMN_NAME = 'update_by');
SET @sql := IF(@exist = 0,
    'ALTER TABLE skill_script_log ADD COLUMN update_by VARCHAR(64) DEFAULT NULL COMMENT ''更新人'' AFTER create_time',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(1) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skill_script_log' AND COLUMN_NAME = 'update_time');
SET @sql := IF(@exist = 0,
    'ALTER TABLE skill_script_log ADD COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER update_by',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(1) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skill_script_log' AND COLUMN_NAME = 'remark');
SET @sql := IF(@exist = 0,
    'ALTER TABLE skill_script_log ADD COLUMN remark VARCHAR(500) DEFAULT NULL COMMENT ''备注'' AFTER update_time',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------- 自检结果 --------------------
SELECT t.TABLE_NAME,
       SUM(c.COLUMN_NAME = 'create_by')   AS has_create_by,
       SUM(c.COLUMN_NAME = 'create_time') AS has_create_time,
       SUM(c.COLUMN_NAME = 'update_by')   AS has_update_by,
       SUM(c.COLUMN_NAME = 'update_time') AS has_update_time
FROM information_schema.COLUMNS c
JOIN information_schema.TABLES t
  ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME
WHERE c.TABLE_SCHEMA = DATABASE()
  AND c.TABLE_NAME IN ('wf_instance', 'skill_script_log')
GROUP BY t.TABLE_NAME;
