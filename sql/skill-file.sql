-- ===========================================================
-- Skill 文件化：技能以目录形式存放（SKILL.md + scripts/*.py）
-- 1) skill_script 增加 script_path（脚本文件相对技能目录的路径）
-- 2) skill 增加 dir_flag（是否已在磁盘建立技能目录）
-- ===========================================================
USE ai_platform;

-- ---------------------- 脚本文件路径 ----------------------
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skill_script' AND COLUMN_NAME = 'script_path');

SET @sql := IF(@exists = 0,
  'ALTER TABLE skill_script ADD COLUMN script_path VARCHAR(255) DEFAULT NULL COMMENT ''脚本文件相对技能目录的路径，如 scripts/cost.py'' AFTER language',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------- 技能目录标记 ----------------------
SET @exists2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skill' AND COLUMN_NAME = 'dir_flag');

SET @sql2 := IF(@exists2 = 0,
  'ALTER TABLE skill ADD COLUMN dir_flag TINYINT NOT NULL DEFAULT 0 COMMENT ''是否已建立技能目录 0否 1是''',
  'SELECT 1');
PREPARE stmt2 FROM @sql2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

-- 目录已存在的技能补标记（以 skill_code 同名目录为准，实际以服务端扫描结果为准）
UPDATE skill SET dir_flag = 1 WHERE dir_flag = 0 AND skill_code IS NOT NULL;

-- 校验
SELECT script_id, skill_id, script_code, script_path, status FROM skill_script;
SELECT skill_id, skill_code, skill_name, dir_flag FROM skill;
