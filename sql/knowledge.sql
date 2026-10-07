-- ============================================================
-- 知识库模块：知识库 / 文档 / 分片 三张表 + 菜单权限
--
-- 设计要点：
--   1) 知识库（kb_base）是检索的范围单位，可被工作流知识节点、智能体引用；
--   2) 文档（kb_document）保存用户上传的原始文件元信息，原文落盘到
--      server/data/knowledge/<base_code>/ 下，数据库中不存二进制；
--   3) 分片（kb_chunk）是检索的最小单位，保存纯文本与可选向量（JSON 数组字符串）。
--      MySQL 无原生向量类型，检索时先关键词粗召回再在内存中做余弦重排。
-- ============================================================
USE ai_platform;

-- ---------------------------- 知识库 ----------------------------
DROP TABLE IF EXISTS kb_base;
CREATE TABLE kb_base (
  base_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '知识库ID',
  base_code    VARCHAR(64)  NOT NULL                COMMENT '知识库编码（唯一，同时作为文件目录名）',
  base_name    VARCHAR(128) NOT NULL                COMMENT '知识库名称',
  description  VARCHAR(500) DEFAULT NULL            COMMENT '描述',
  embed_model_id BIGINT     DEFAULT NULL            COMMENT '使用的 embedding 模型ID，为空表示纯关键词检索',
  chunk_size   INT          NOT NULL DEFAULT 500    COMMENT '分片大小（字符）',
  chunk_overlap INT         NOT NULL DEFAULT 80     COMMENT '分片重叠（字符）',
  top_k        INT          NOT NULL DEFAULT 5      COMMENT '默认召回条数',
  score_threshold DECIMAL(5,2) NOT NULL DEFAULT 0.15 COMMENT '召回最低分数，低于则视为未命中',
  doc_count    INT          NOT NULL DEFAULT 0      COMMENT '文档数（冗余，列表展示）',
  chunk_count  INT          NOT NULL DEFAULT 0      COMMENT '分片数（冗余，列表展示）',
  status       TINYINT      NOT NULL DEFAULT 0      COMMENT '状态 0正常 1停用',
  del_flag     TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标记 0存在 2删除',
  dept_id      BIGINT       DEFAULT NULL            COMMENT '归属部门（数据权限）',
  remark       VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  create_by    VARCHAR(64)  DEFAULT NULL,
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_by    VARCHAR(64)  DEFAULT NULL,
  update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (base_id),
  UNIQUE KEY uk_base_code (base_code),
  KEY idx_base_status (status, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

-- ---------------------------- 文档 ----------------------------
DROP TABLE IF EXISTS kb_document;
CREATE TABLE kb_document (
  doc_id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文档ID',
  base_id      BIGINT       NOT NULL                COMMENT '所属知识库ID',
  doc_name     VARCHAR(255) NOT NULL                COMMENT '原始文件名',
  file_type    VARCHAR(16)  DEFAULT NULL            COMMENT '扩展名：pdf/docx/xlsx/csv/txt...',
  file_size    BIGINT       DEFAULT 0               COMMENT '文件字节数',
  store_path   VARCHAR(500) DEFAULT NULL            COMMENT '相对知识库根目录的存储路径',
  char_count   INT          DEFAULT 0               COMMENT '解析出的字符数',
  chunk_count  INT          DEFAULT 0               COMMENT '切出的分片数',
  parse_status TINYINT      NOT NULL DEFAULT 0      COMMENT '解析状态 0待解析 1解析中 2成功 3失败',
  parse_msg    VARCHAR(500) DEFAULT NULL            COMMENT '解析结果说明/失败原因',
  status       TINYINT      NOT NULL DEFAULT 0      COMMENT '状态 0启用 1停用（停用不参与检索）',
  del_flag     TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标记 0存在 2删除',
  remark       VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  create_by    VARCHAR(64)  DEFAULT NULL,
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_by    VARCHAR(64)  DEFAULT NULL,
  update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (doc_id),
  KEY idx_doc_base (base_id, del_flag),
  KEY idx_doc_status (parse_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';

-- ---------------------------- 分片 ----------------------------
DROP TABLE IF EXISTS kb_chunk;
CREATE TABLE kb_chunk (
  chunk_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '分片ID',
  base_id      BIGINT       NOT NULL                COMMENT '知识库ID',
  doc_id       BIGINT       NOT NULL                COMMENT '文档ID',
  seq_no       INT          NOT NULL DEFAULT 0      COMMENT '分片序号',
  title        VARCHAR(255) DEFAULT NULL            COMMENT '分片标题（取自首个非空行/工作表名）',
  content      TEXT                                 COMMENT '分片正文',
  char_count   INT          DEFAULT 0               COMMENT '字符数',
  embedding    MEDIUMTEXT                           COMMENT '向量，JSON float 数组；为空表示未生成',
  embed_model  VARCHAR(64)  DEFAULT NULL            COMMENT '生成向量的模型标识',
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (chunk_id),
  KEY idx_chunk_base (base_id),
  KEY idx_chunk_doc (doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库分片表';

-- ---------------------------- 菜单 ----------------------------
DELETE FROM sys_role_menu WHERE menu_id IN (700, 7000);
DELETE FROM sys_menu WHERE menu_id IN (700, 7000);

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, remark, create_by)
VALUES
  (700, '知识库',   0, 7, '/knowledge', NULL,                        'M', 0, 0, '',                    'Collection', '知识库目录', 'admin'),
  (7000,'知识库管理', 700, 1, 'base',   'knowledge/base/index',      'C', 0, 0, 'knowledge:base:list', 'FolderOpened','知识库与文档管理', 'admin');

-- 按钮级权限
DELETE FROM sys_menu WHERE menu_id IN (7001, 7002, 7003, 7004);
INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, remark, create_by)
VALUES
  (7001,'知识库新增', 7000, 1, '', NULL, 'F', 0, 0, 'knowledge:base:add',    '#', '知识库新增', 'admin'),
  (7002,'知识库修改', 7000, 2, '', NULL, 'F', 0, 0, 'knowledge:base:edit',   '#', '知识库修改', 'admin'),
  (7003,'知识库删除', 7000, 3, '', NULL, 'F', 0, 0, 'knowledge:base:remove', '#', '知识库删除', 'admin'),
  (7004,'文档管理',   7000, 4, '', NULL, 'F', 0, 0, 'knowledge:doc:edit',    '#', '文档上传/删除/重建', 'admin');

-- 授予管理员角色（role_id = 1）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id IN (700, 7000, 7001, 7002, 7003, 7004);
