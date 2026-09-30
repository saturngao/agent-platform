-- =============================================================
--  AI 工作流与智能体平台 (Agent Platform)  数据库初始化脚本
--  兼容 MySQL 5.7+ / MariaDB 10.6+ (已在 MariaDB 11.8 验证)
--  语法约束：仅使用通用 SQL，不使用 MySQL 8 专有特性
-- =============================================================

CREATE DATABASE IF NOT EXISTS ai_platform
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE ai_platform;

SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================
--  一、系统管理模块（RBAC）
-- =============================================================

-- 部门(组织)表
DROP TABLE IF EXISTS sys_dept;
CREATE TABLE sys_dept (
  dept_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '部门ID',
  parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父部门ID',
  ancestors   VARCHAR(500) NOT NULL DEFAULT '' COMMENT '祖级路径 0,100,101',
  dept_name   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '部门名称',
  leader      VARCHAR(64)           DEFAULT NULL COMMENT '负责人',
  phone       VARCHAR(32)           DEFAULT NULL COMMENT '联系电话',
  email       VARCHAR(128)          DEFAULT NULL COMMENT '邮箱',
  order_num   INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0正常 1停用',
  del_flag    TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标记 0存在 2删除',
  remark      VARCHAR(500)          DEFAULT NULL COMMENT '备注',
  create_by   VARCHAR(64)           DEFAULT NULL,
  create_time DATETIME              DEFAULT CURRENT_TIMESTAMP,
  update_by   VARCHAR(64)           DEFAULT NULL,
  update_time DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (dept_id)
) ENGINE=InnoDB COMMENT='部门表';

-- 用户表
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  user_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  dept_id     BIGINT                DEFAULT NULL COMMENT '部门ID',
  username    VARCHAR(64)  NOT NULL COMMENT '登录账号',
  nickname    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '用户昵称',
  user_type   VARCHAR(16)  NOT NULL DEFAULT 'sys_user' COMMENT '用户类型 sys_user普通账号',
  email       VARCHAR(128)          DEFAULT NULL COMMENT '邮箱',
  phonenumber VARCHAR(32)           DEFAULT NULL COMMENT '手机号',
  sex         TINYINT               DEFAULT 0 COMMENT '性别 0未知 1男 2女',
  avatar      VARCHAR(512)          DEFAULT NULL COMMENT '头像',
  password    VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'BCrypt 密码',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0正常 1停用',
  del_flag    TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标记 0存在 2删除',
  login_ip    VARCHAR(64)           DEFAULT NULL COMMENT '最后登录IP',
  login_date  DATETIME              DEFAULT NULL COMMENT '最后登录时间',
  remark      VARCHAR(500)          DEFAULT NULL COMMENT '备注',
  create_by   VARCHAR(64)           DEFAULT NULL,
  create_time DATETIME              DEFAULT CURRENT_TIMESTAMP,
  update_by   VARCHAR(64)           DEFAULT NULL,
  update_time DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB COMMENT='用户表';

-- 岗位表
DROP TABLE IF EXISTS sys_post;
CREATE TABLE sys_post (
  post_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
  post_code   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '岗位编码',
  post_name   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '岗位名称',
  post_sort   INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0正常 1停用',
  remark      VARCHAR(500)          DEFAULT NULL,
  create_by   VARCHAR(64)           DEFAULT NULL,
  create_time DATETIME              DEFAULT CURRENT_TIMESTAMP,
  update_by   VARCHAR(64)           DEFAULT NULL,
  update_time DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (post_id)
) ENGINE=InnoDB COMMENT='岗位表';

-- 角色表
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
  role_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  role_name   VARCHAR(64)  NOT NULL COMMENT '角色名称',
  role_key    VARCHAR(100) NOT NULL COMMENT '角色权限字符串',
  role_sort   INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
  data_scope  VARCHAR(16)  NOT NULL DEFAULT '1' COMMENT '数据范围 1全部 2自定义 3本部门 4本部门及以下 5仅本人',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0正常 1停用',
  del_flag    TINYINT      NOT NULL DEFAULT 0,
  remark      VARCHAR(500)          DEFAULT NULL,
  create_by   VARCHAR(64)           DEFAULT NULL,
  create_time DATETIME              DEFAULT CURRENT_TIMESTAMP,
  update_by   VARCHAR(64)           DEFAULT NULL,
  update_time DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (role_id),
  UNIQUE KEY uk_role_key (role_key)
) ENGINE=InnoDB COMMENT='角色表';

-- 菜单(权限)表
DROP TABLE IF EXISTS sys_menu;
CREATE TABLE sys_menu (
  menu_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  menu_name   VARCHAR(64)  NOT NULL COMMENT '菜单名称',
  parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID',
  order_num   INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
  path        VARCHAR(200)          DEFAULT NULL COMMENT '路由地址',
  component   VARCHAR(255)          DEFAULT NULL COMMENT '组件路径',
  query       VARCHAR(255)          DEFAULT NULL COMMENT '路由参数',
  is_frame    TINYINT      NOT NULL DEFAULT 1 COMMENT '是否外链 0是 1否',
  is_cache    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否缓存 0缓存 1不缓存',
  menu_type   VARCHAR(16)  NOT NULL DEFAULT 'C' COMMENT '菜单类型 M目录 C菜单 F按钮',
  visible     TINYINT      NOT NULL DEFAULT 0 COMMENT '显示状态 0显示 1隐藏',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0正常 1停用',
  perms       VARCHAR(200)          DEFAULT NULL COMMENT '权限标识',
  icon        VARCHAR(128)          DEFAULT NULL COMMENT '图标',
  remark      VARCHAR(500)          DEFAULT NULL,
  create_by   VARCHAR(64)           DEFAULT NULL,
  create_time DATETIME              DEFAULT CURRENT_TIMESTAMP,
  update_by   VARCHAR(64)           DEFAULT NULL,
  update_time DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (menu_id),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB COMMENT='菜单权限表';

DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
  user_id BIGINT NOT NULL COMMENT '用户ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB COMMENT='用户角色关联';

DROP TABLE IF EXISTS sys_user_post;
CREATE TABLE sys_user_post (
  user_id BIGINT NOT NULL COMMENT '用户ID',
  post_id BIGINT NOT NULL COMMENT '岗位ID',
  PRIMARY KEY (user_id, post_id)
) ENGINE=InnoDB COMMENT='用户岗位关联';

DROP TABLE IF EXISTS sys_role_menu;
CREATE TABLE sys_role_menu (
  role_id BIGINT NOT NULL COMMENT '角色ID',
  menu_id BIGINT NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB COMMENT='角色菜单关联';

DROP TABLE IF EXISTS sys_role_dept;
CREATE TABLE sys_role_dept (
  role_id BIGINT NOT NULL COMMENT '角色ID',
  dept_id BIGINT NOT NULL COMMENT '部门ID(自定义数据权限范围)',
  PRIMARY KEY (role_id, dept_id)
) ENGINE=InnoDB COMMENT='角色数据权限关联';

-- 字典
DROP TABLE IF EXISTS sys_dict_type;
CREATE TABLE sys_dict_type (
  dict_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '字典主键',
  dict_name   VARCHAR(100) NOT NULL DEFAULT '' COMMENT '字典名称',
  dict_type   VARCHAR(100) NOT NULL DEFAULT '' COMMENT '字典类型',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0正常 1停用',
  remark      VARCHAR(500)          DEFAULT NULL,
  create_by   VARCHAR(64)           DEFAULT NULL,
  create_time DATETIME              DEFAULT CURRENT_TIMESTAMP,
  update_by   VARCHAR(64)           DEFAULT NULL,
  update_time DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (dict_id),
  UNIQUE KEY uk_dict_type (dict_type)
) ENGINE=InnoDB COMMENT='字典类型表';

DROP TABLE IF EXISTS sys_dict_data;
CREATE TABLE sys_dict_data (
  dict_code   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '字典编码',
  dict_type   VARCHAR(100) NOT NULL DEFAULT '' COMMENT '字典类型',
  dict_label  VARCHAR(100) NOT NULL DEFAULT '' COMMENT '字典标签',
  dict_value  VARCHAR(100) NOT NULL DEFAULT '' COMMENT '字典键值',
  dict_sort   INT          NOT NULL DEFAULT 0 COMMENT '字典排序',
  list_class  VARCHAR(64)           DEFAULT NULL COMMENT '表格回显样式',
  is_default  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认 1是 0否',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0正常 1停用',
  remark      VARCHAR(500)          DEFAULT NULL,
  create_by   VARCHAR(64)           DEFAULT NULL,
  create_time DATETIME              DEFAULT CURRENT_TIMESTAMP,
  update_by   VARCHAR(64)           DEFAULT NULL,
  update_time DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (dict_code),
  KEY idx_dict_type (dict_type)
) ENGINE=InnoDB COMMENT='字典数据表';

-- 系统参数
DROP TABLE IF EXISTS sys_config;
CREATE TABLE sys_config (
  config_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '参数主键',
  config_name  VARCHAR(100) NOT NULL DEFAULT '' COMMENT '参数名称',
  config_key   VARCHAR(100) NOT NULL DEFAULT '' COMMENT '参数键名',
  config_value VARCHAR(500) NOT NULL DEFAULT '' COMMENT '参数键值',
  config_type  TINYINT      NOT NULL DEFAULT 0 COMMENT '系统内置 1是 0否',
  remark       VARCHAR(500)          DEFAULT NULL,
  create_by    VARCHAR(64)           DEFAULT NULL,
  create_time  DATETIME              DEFAULT CURRENT_TIMESTAMP,
  update_by    VARCHAR(64)           DEFAULT NULL,
  update_time  DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (config_id),
  UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB COMMENT='系统参数表';

-- 日志
DROP TABLE IF EXISTS sys_login_log;
CREATE TABLE sys_login_log (
  info_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '访问ID',
  username     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '登录账号',
  ipaddr       VARCHAR(64)           DEFAULT NULL COMMENT '登录IP',
  browser      VARCHAR(64)           DEFAULT NULL COMMENT '浏览器',
  os           VARCHAR(64)           DEFAULT NULL COMMENT '操作系统',
  status       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0成功 1失败',
  msg          VARCHAR(255)          DEFAULT NULL COMMENT '提示消息',
  login_time   DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '访问时间',
  PRIMARY KEY (info_id),
  KEY idx_login_time (login_time)
) ENGINE=InnoDB COMMENT='登录日志表';

DROP TABLE IF EXISTS sys_oper_log;
CREATE TABLE sys_oper_log (
  oper_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志主键',
  title          VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '模块标题',
  business_type  TINYINT      NOT NULL DEFAULT 0 COMMENT '业务类型 0其它 1新增 2修改 3删除',
  method         VARCHAR(200)          DEFAULT NULL COMMENT '方法名称',
  request_method VARCHAR(16)           DEFAULT NULL COMMENT '请求方式',
  oper_name      VARCHAR(64)           DEFAULT NULL COMMENT '操作人员',
  oper_url       VARCHAR(255)          DEFAULT NULL COMMENT '请求URL',
  oper_ip        VARCHAR(64)           DEFAULT NULL COMMENT '请求IP',
  oper_param     TEXT                  COMMENT '请求参数',
  json_result    TEXT                  COMMENT '返回结果',
  status         TINYINT      NOT NULL DEFAULT 0 COMMENT '操作状态 0正常 1异常',
  error_msg      VARCHAR(2000)         DEFAULT NULL COMMENT '错误消息',
  cost_time      INT                   DEFAULT 0 COMMENT '耗时(ms)',
  oper_time      DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (oper_id),
  KEY idx_oper_time (oper_time)
) ENGINE=InnoDB COMMENT='操作日志表';

-- =============================================================
--  二、大模型配置中心
-- =============================================================

-- 模型供应商（OpenAI / 阿里云通义 / 智谱 / 本地 Ollama / 自定义 OpenAI 兼容）
DROP TABLE IF EXISTS llm_provider;
CREATE TABLE llm_provider (
  provider_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '供应商ID',
  provider_code VARCHAR(64)  NOT NULL COMMENT '供应商编码 openai/deepseek/qwen/zhipu/ollama/custom',
  provider_name VARCHAR(64)  NOT NULL COMMENT '供应商名称',
  base_url      VARCHAR(512)          DEFAULT NULL COMMENT '默认 API 根地址',
  protocol      VARCHAR(32)  NOT NULL DEFAULT 'openai' COMMENT '协议类型 openai',
  api_key_hint  VARCHAR(64)           DEFAULT NULL COMMENT 'Key 示例说明',
  logo          VARCHAR(512)          DEFAULT NULL COMMENT '图标',
  sort_num      INT          NOT NULL DEFAULT 0 COMMENT '排序',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0正常 1停用',
  remark        VARCHAR(500)          DEFAULT NULL,
  create_by     VARCHAR(64)           DEFAULT NULL,
  create_time   DATETIME              DEFAULT CURRENT_TIMESTAMP,
  update_by     VARCHAR(64)           DEFAULT NULL,
  update_time   DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (provider_id),
  UNIQUE KEY uk_provider_code (provider_code)
) ENGINE=InnoDB COMMENT='大模型供应商表';

-- 模型实例（真正被工作流/Agent 引用的对象）
DROP TABLE IF EXISTS llm_model;
CREATE TABLE llm_model (
  model_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '模型配置ID',
  provider_id   BIGINT                DEFAULT NULL COMMENT '供应商ID',
  model_code    VARCHAR(64)  NOT NULL COMMENT '模型唯一编码(业务内引用)',
  model_name    VARCHAR(128) NOT NULL COMMENT '展示名称',
  model_type    VARCHAR(32)  NOT NULL DEFAULT 'chat' COMMENT '模型类型 chat/embedding/rerank',
  endpoint      VARCHAR(512) NOT NULL COMMENT '完整请求地址 /chat/completions',
  api_key       VARCHAR(512)          DEFAULT NULL COMMENT '密钥(明文存储，生产建议加密)',
  model_name_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '上游模型标识 gpt-4o / qwen-plus',
  system_prompt TEXT                  COMMENT '默认系统提示词',
  temperature   DECIMAL(3,2)          DEFAULT 0.70 COMMENT '温度 0-2',
  top_p         DECIMAL(3,2)          DEFAULT 1.00 COMMENT 'top_p',
  max_tokens    INT                   DEFAULT 4096 COMMENT '最大输出 tokens',
  timeout_ms    INT                   DEFAULT 120000 COMMENT '超时毫秒',
  rate_limit    INT                   DEFAULT 0 COMMENT '每分钟调用上限 0不限',
  context_window INT                  DEFAULT 0 COMMENT '上下文窗口',
  support_stream TINYINT     NOT NULL DEFAULT 1 COMMENT '是否支持流式 1是 0否',
  support_json  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否支持 JSON 输出 1是 0否',
  is_default    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认模型 1是 0否',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0正常 1停用',
  last_test_at  DATETIME              DEFAULT NULL COMMENT '最近测试时间',
  last_test_msg VARCHAR(500)          DEFAULT NULL COMMENT '最近测试结果',
  dept_id       BIGINT                DEFAULT NULL COMMENT '归属部门',
  create_by     VARCHAR(64)           DEFAULT NULL,
  create_time   DATETIME              DEFAULT CURRENT_TIMESTAMP,
  update_by     VARCHAR(64)           DEFAULT NULL,
  update_time   DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  remark        VARCHAR(500)          DEFAULT NULL,
  PRIMARY KEY (model_id),
  UNIQUE KEY uk_model_code (model_code),
  KEY idx_provider (provider_id)
) ENGINE=InnoDB COMMENT='模型实例配置表';

-- 模型可见范围（哪些角色可用该模型）
DROP TABLE IF EXISTS llm_model_scope;
CREATE TABLE llm_model_scope (
  id        BIGINT NOT NULL AUTO_INCREMENT,
  model_id  BIGINT NOT NULL COMMENT '模型配置ID',
  scope_type VARCHAR(16) NOT NULL DEFAULT 'ALL' COMMENT 'ALL全部 DEPT部门 ROLE角色 USER用户',
  scope_id  BIGINT NOT NULL DEFAULT 0 COMMENT '范围对象ID',
  PRIMARY KEY (id),
  KEY idx_model (model_id)
) ENGINE=InnoDB COMMENT='模型授权范围表';

-- 模型调用日志（可观测性）
DROP TABLE IF EXISTS llm_call_log;
CREATE TABLE llm_call_log (
  call_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '调用ID',
  biz_type       VARCHAR(32)  NOT NULL DEFAULT 'unknown' COMMENT '来源 workflow/agent/summary/test',
  biz_id         VARCHAR(64)           DEFAULT NULL COMMENT '关联业务ID',
  model_id       BIGINT                DEFAULT NULL COMMENT '模型配置ID',
  model_name     VARCHAR(128)          DEFAULT NULL COMMENT '冗余模型名',
  request_body   MEDIUMTEXT            COMMENT '请求体',
  response_body  MEDIUMTEXT            COMMENT '响应体',
  prompt_tokens  INT                   DEFAULT 0,
  completion_tokens INT                DEFAULT 0,
  total_tokens   INT                   DEFAULT 0,
  cost_ms        INT                   DEFAULT 0 COMMENT '耗时毫秒',
  status         TINYINT      NOT NULL DEFAULT 0 COMMENT '0成功 1失败',
  error_msg      VARCHAR(2000)         DEFAULT NULL,
  user_id        BIGINT                DEFAULT NULL COMMENT '调用人',
  call_time      DATETIME              DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (call_id),
  KEY idx_call_time (call_time),
  KEY idx_biz (biz_type, biz_id)
) ENGINE=InnoDB COMMENT='大模型调用日志表';

-- =============================================================
--  三、工作流编排
-- =============================================================

DROP TABLE IF EXISTS wf_definition;
CREATE TABLE wf_definition (
  def_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '定义ID',
  def_code      VARCHAR(64)  NOT NULL COMMENT '唯一编码',
  def_name      VARCHAR(128) NOT NULL COMMENT '工作流名称',
  category      VARCHAR(64)           DEFAULT NULL COMMENT '分类',
  icon          VARCHAR(128)          DEFAULT NULL COMMENT '图标',
  description   VARCHAR(500)          DEFAULT NULL COMMENT '描述',
  graph_json    MEDIUMTEXT            COMMENT '画布定义 nodes/edges JSON',
  input_schema  TEXT                  COMMENT '入参定义 JSON 数组',
  version       INT          NOT NULL DEFAULT 1 COMMENT '版本号',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0草稿 1发布 2停用',
  published     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否曾发布',
  dept_id       BIGINT                DEFAULT NULL COMMENT '归属部门',
  create_by     VARCHAR(64)           DEFAULT NULL,
  create_time   DATETIME              DEFAULT CURRENT_TIMESTAMP,
  update_by     VARCHAR(64)           DEFAULT NULL,
  update_time   DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (def_id),
  UNIQUE KEY uk_def_code (def_code)
) ENGINE=InnoDB COMMENT='工作流定义表';

DROP TABLE IF EXISTS wf_instance;
CREATE TABLE wf_instance (
  instance_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '实例ID',
  def_id        BIGINT       NOT NULL COMMENT '定义ID',
  def_code      VARCHAR(64)           DEFAULT NULL COMMENT '冗余编码',
  biz_no        VARCHAR(64)           DEFAULT NULL COMMENT '业务单号(外部关联)',
  input_data    TEXT                  COMMENT '入参 JSON',
  output_data   MEDIUMTEXT            COMMENT '出参 JSON',
  variables     MEDIUMTEXT            COMMENT '运行期变量快照 JSON',
  status        VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED/STOPPED',
  error_msg     VARCHAR(2000)         DEFAULT NULL,
  trigger_type  VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/API/SCHEDULE',
  cost_ms       INT                   DEFAULT 0,
  token_total   INT                   DEFAULT 0,
  start_time    DATETIME              DEFAULT CURRENT_TIMESTAMP,
  end_time      DATETIME              DEFAULT NULL,
  user_id       BIGINT                DEFAULT NULL,
  username      VARCHAR(64)           DEFAULT NULL,
  PRIMARY KEY (instance_id),
  KEY idx_def (def_id),
  KEY idx_start (start_time)
) ENGINE=InnoDB COMMENT='工作流运行实例表';

DROP TABLE IF EXISTS wf_node_run;
CREATE TABLE wf_node_run (
  run_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '节点运行ID',
  instance_id   BIGINT       NOT NULL COMMENT '实例ID',
  node_id       VARCHAR(64)  NOT NULL COMMENT '画布节点ID',
  node_name     VARCHAR(128)          DEFAULT NULL,
  node_type     VARCHAR(32)           DEFAULT NULL COMMENT 'start/llm/code/condition/http/knowledge/agent/end',
  input_data    MEDIUMTEXT            COMMENT '节点入参',
  output_data   MEDIUMTEXT            COMMENT '节点出参',
  branch_taken  VARCHAR(64)           DEFAULT NULL COMMENT '条件节点实际走向分支',
  status        VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED/SKIPPED',
  error_msg     VARCHAR(2000)         DEFAULT NULL,
  model_id      BIGINT                DEFAULT NULL COMMENT '使用的模型ID',
  tokens        INT                   DEFAULT 0,
  cost_ms       INT                   DEFAULT 0,
  seq_no        INT                   DEFAULT 0 COMMENT '执行顺序',
  start_time    DATETIME              DEFAULT CURRENT_TIMESTAMP,
  end_time      DATETIME              DEFAULT NULL,
  PRIMARY KEY (run_id),
  KEY idx_instance (instance_id)
) ENGINE=InnoDB COMMENT='工作流节点运行记录表';

-- =============================================================
--  四、Agent 智能体
-- =============================================================

DROP TABLE IF EXISTS agent_definition;
CREATE TABLE agent_definition (
  agent_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '智能体ID',
  agent_code    VARCHAR(64)  NOT NULL COMMENT '唯一编码',
  agent_name    VARCHAR(128) NOT NULL COMMENT '名称',
  avatar        VARCHAR(512)          DEFAULT NULL COMMENT '头像图标',
  category      VARCHAR(64)           DEFAULT NULL COMMENT '分类',
  model_id      BIGINT                DEFAULT NULL COMMENT '绑定模型配置ID',
  system_prompt MEDIUMTEXT            COMMENT '系统提示词',
  prologue      VARCHAR(1000)         DEFAULT NULL COMMENT '开场白',
  temperature   DECIMAL(3,2)          DEFAULT NULL COMMENT '覆盖模型的温度',
  max_tokens    INT                   DEFAULT NULL COMMENT '覆盖模型的最大输出',
  tools_json    TEXT                  COMMENT '启用的工具 JSON 数组',
  knowledge_ids VARCHAR(255)          DEFAULT NULL COMMENT '关联工作流ID集合(逗号分隔)',
  variables     TEXT                  COMMENT '变量定义 JSON 数组',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿 1发布 2停用',
  dept_id       BIGINT                DEFAULT NULL,
  create_by     VARCHAR(64)           DEFAULT NULL,
  create_time   DATETIME              DEFAULT CURRENT_TIMESTAMP,
  update_by     VARCHAR(64)           DEFAULT NULL,
  update_time   DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  remark        VARCHAR(500)          DEFAULT NULL,
  PRIMARY KEY (agent_id),
  UNIQUE KEY uk_agent_code (agent_code)
) ENGINE=InnoDB COMMENT='智能体定义表';

DROP TABLE IF EXISTS agent_conversation;
CREATE TABLE agent_conversation (
  conv_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  agent_id    BIGINT       NOT NULL COMMENT '智能体ID',
  user_id     BIGINT                DEFAULT NULL,
  title       VARCHAR(255)          DEFAULT NULL COMMENT '会话标题',
  summary     TEXT                  COMMENT '上下文摘要',
  message_count INT                 DEFAULT 0,
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1归档',
  create_time DATETIME              DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (conv_id),
  KEY idx_agent (agent_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='智能体会话表';

DROP TABLE IF EXISTS agent_message;
CREATE TABLE agent_message (
  msg_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  conv_id     BIGINT       NOT NULL COMMENT '会话ID',
  role        VARCHAR(16)  NOT NULL COMMENT 'user/assistant/system',
  content     MEDIUMTEXT            COMMENT '消息内容',
  tokens      INT                   DEFAULT 0,
  model_name  VARCHAR(128)          DEFAULT NULL,
  cost_ms     INT                   DEFAULT 0,
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1失败',
  error_msg   VARCHAR(1000)         DEFAULT NULL,
  create_time DATETIME              DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (msg_id),
  KEY idx_conv (conv_id)
) ENGINE=InnoDB COMMENT='智能体消息表';

-- =============================================================
--  五、工作日志 & AI 总结
-- =============================================================

DROP TABLE IF EXISTS work_task;
CREATE TABLE work_task (
  task_id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  task_title    VARCHAR(255) NOT NULL COMMENT '任务标题',
  task_content  MEDIUMTEXT            COMMENT '任务内容/要求',
  assignee_id   BIGINT                DEFAULT NULL COMMENT '负责人ID',
  assignee_name VARCHAR(64)           DEFAULT NULL COMMENT '负责人姓名(冗余)',
  dept_id       BIGINT                DEFAULT NULL COMMENT '执行部门',
  assigner_id   BIGINT                DEFAULT NULL COMMENT '派发人ID',
  priority      VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT 'LOW/NORMAL/HIGH/URGENT',
  status        VARCHAR(16)  NOT NULL DEFAULT 'TODO' COMMENT 'TODO/DOING/DONE/CANCELLED/REVIEW',
  progress      INT          NOT NULL DEFAULT 0 COMMENT '进度百分比',
  start_date    DATE                  DEFAULT NULL COMMENT '计划开始',
  due_date      DATE                  DEFAULT NULL COMMENT '计划截止',
  finish_date   DATETIME              DEFAULT NULL COMMENT '实际完成时间',
  tags          VARCHAR(255)          DEFAULT NULL COMMENT '标签',
  parent_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '父任务ID',
  del_flag      TINYINT      NOT NULL DEFAULT 0,
  create_by     VARCHAR(64)           DEFAULT NULL,
  create_time   DATETIME              DEFAULT CURRENT_TIMESTAMP,
  update_by     VARCHAR(64)           DEFAULT NULL,
  update_time   DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (task_id),
  KEY idx_assignee (assignee_id),
  KEY idx_dept (dept_id),
  KEY idx_due (due_date)
) ENGINE=InnoDB COMMENT='工作任务表';

DROP TABLE IF EXISTS work_log;
CREATE TABLE work_log (
  log_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  task_id       BIGINT                DEFAULT NULL COMMENT '关联任务ID',
  log_title     VARCHAR(255)          DEFAULT NULL COMMENT '日志标题',
  content       MEDIUMTEXT   NOT NULL COMMENT '工作内容',
  work_date     DATE         NOT NULL COMMENT '工作日期',
  hours_spent   DECIMAL(5,1)          DEFAULT 0 COMMENT '投入工时',
  progress_snap INT                   DEFAULT 0 COMMENT '当时进度快照',
  log_type      VARCHAR(16)  NOT NULL DEFAULT 'DAILY' COMMENT 'DAILY日报 WEEKLY周报 MEETING会议 OTHER其他',
  result        VARCHAR(255)          DEFAULT NULL COMMENT '成果产出',
  blockers      MEDIUMTEXT            COMMENT '阻塞与风险',
  plan_next     MEDIUMTEXT            COMMENT '下一步计划',
  user_id       BIGINT       NOT NULL COMMENT '记录人',
  username      VARCHAR(64)           DEFAULT NULL,
  dept_id       BIGINT                DEFAULT NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL正常 DELETED已删除',
  summarized    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已被 AI 总结纳入 1是 0否',
  del_flag      TINYINT      NOT NULL DEFAULT 0,
  create_by     VARCHAR(64)           DEFAULT NULL,
  create_time   DATETIME              DEFAULT CURRENT_TIMESTAMP,
  update_by     VARCHAR(64)           DEFAULT NULL,
  update_time   DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (log_id),
  KEY idx_user_date (user_id, work_date),
  KEY idx_dept_date (dept_id, work_date),
  KEY idx_task (task_id)
) ENGINE=InnoDB COMMENT='工作日志表';

DROP TABLE IF EXISTS work_summary;
CREATE TABLE work_summary (
  summary_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '总结ID',
  summary_title VARCHAR(255) NOT NULL COMMENT '总结标题',
  scope_type    VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '统计维度 USER个人 DEPT部门 TEAM自定义',
  begin_date    DATE         NOT NULL COMMENT '统计开始日期',
  end_date      DATE         NOT NULL COMMENT '统计结束日期',
  target_ids    VARCHAR(500)          DEFAULT NULL COMMENT '统计对象 userIds/deptIds 逗号分隔',
  model_id      BIGINT                DEFAULT NULL COMMENT '使用的模型ID',
  model_name    VARCHAR(128)          DEFAULT NULL,
  prompt_tpl    MEDIUMTEXT            COMMENT '本次使用的提示词模板',
  source_data   MEDIUMTEXT            COMMENT '参与统计的原始数据快照',
  content_md    MEDIUMTEXT            COMMENT '大模型生成的总结正文(Markdown)',
  content_html  MEDIUMTEXT            COMMENT '渲染后的 HTML',
  log_count     INT                   DEFAULT 0 COMMENT '纳入统计的日志条数',
  tokens        INT                   DEFAULT 0,
  cost_ms       INT                   DEFAULT 0,
  status        VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'GENERATING/SUCCESS/FAILED',
  error_msg     VARCHAR(2000)         DEFAULT NULL,
  mail_sent     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已发送邮件 1是',
  mail_to       VARCHAR(500)          DEFAULT NULL COMMENT '收件人',
  mail_sent_at  DATETIME              DEFAULT NULL COMMENT '邮件发送时间',
  user_id       BIGINT                DEFAULT NULL COMMENT '生成人',
  create_time   DATETIME              DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (summary_id),
  KEY idx_range (begin_date, end_date)
) ENGINE=InnoDB COMMENT='工作总结记录表';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
--  初始化数据
-- =============================================================

-- 部门
INSERT INTO sys_dept (dept_id, parent_id, ancestors, dept_name, order_num, status, del_flag)
VALUES
 (100, 0, '0',    '总公司',        1, 0, 0),
 (101, 100, '0,100', '技术研发中心',  1, 0, 0),
 (102, 100, '0,100', '产品设计部',    2, 0, 0),
 (103, 100, '0,100', '市场运营部',    3, 0, 0),
 (104, 101, '0,100,101', '平台研发组', 1, 0, 0),
 (105, 101, '0,100,101', 'AI 算法组',  2, 0, 0);

-- 岗位
INSERT INTO sys_post (post_id, post_code, post_name, post_sort, status) VALUES
 (1, 'ceo',      '总经理',      1, 0),
 (2, 'tech_dir', '技术总监',    2, 0),
 (3, 'pm',       '产品经理',    3, 0),
 (4, 'se',       '后端工程师',  4, 0),
 (5, 'fe',       '前端工程师',  5, 0),
 (6, 'ai_eng',   '算法工程师',  6, 0);

-- 角色（admin 为超级管理员，拥有全部权限：由角色 role_key='admin' 特判）
INSERT INTO sys_role (role_id, role_name, role_key, role_sort, data_scope, status, remark) VALUES
 (1, '超级管理员', 'admin',    1, '1', 0, '系统内置，拥有全部权限'),
 (2, '部门主管',   'manager',  2, '4', 0, '可管理部门及下级数据'),
 (3, '普通员工',   'common',   3, '5', 0, '仅可查看本人数据');

-- 用户：密码统一为 admin123 (BCrypt)
-- $2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2 == admin123
INSERT INTO sys_user (user_id, dept_id, username, nickname, email, phonenumber, sex, password, status, del_flag, remark)
VALUES
 (1, 100, 'admin', '超级管理员', 'admin@aip.local',  '13800000001', 1,
  '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 0, 0, '系统内置管理员'),
 (2, 101, 'zhangwei', '张伟(研发负责人)', 'zhangwei@aip.local', '13800000002', 1,
  '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 0, 0, ''),
 (3, 104, 'lisi', '李四(后端)', 'lisi@aip.local', '13800000003', 1,
  '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 0, 0, ''),
 (4, 105, 'wangfang', '王芳(算法)', 'wangfang@aip.local', '13800000004', 2,
  '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 0, 0, '');

INSERT INTO sys_user_role (user_id, role_id) VALUES
 (1, 1), (2, 2), (3, 3), (4, 3);

INSERT INTO sys_user_post (user_id, post_id) VALUES
 (1, 1), (2, 2), (3, 4), (4, 6);

-- 菜单：目录 / 菜单 / 按钮三级
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, menu_type, visible, perms, icon, remark)
VALUES
 (1,  '系统管理',   0, 1, '/system',       NULL,                             'M', 0, '',            'Setting',      '系统管理目录'),
 (100,'用户管理',   1, 1, 'user',          'system/user/index',              'C', 0, 'system:user:list',     'User',   '用户管理菜单'),
 (101,'角色管理',   1, 2, 'role',          'system/role/index',              'C', 0, 'system:role:list',     'UserFilled', '角色管理菜单'),
 (102,'菜单管理',   1, 3, 'menu',          'system/menu/index',              'C', 0, 'system:menu:list',     'Menu',   '菜单管理菜单'),
 (103,'部门管理',   1, 4, 'dept',          'system/dept/index',              'C', 0, 'system:dept:list',     'OfficeBuilding','部门管理菜单'),
 (104,'岗位管理',   1, 5, 'post',          'system/post/index',              'C', 0, 'system:post:list',     'Postcard','岗位管理菜单'),
 (105,'字典管理',   1, 6, 'dict',          'system/dict/index',              'C', 0, 'system:dict:list',     'Collection','字典管理菜单'),
 (106,'参数配置',   1, 7, 'config',        'system/config/index',            'C', 0, 'system:config:list',   'Tools','系统参数配置（含邮件 SMTP 参数）'),

 (2,  '模型中心',   0, 2, '/llm',          NULL,                             'M', 0, '',            'Cpu',          '大模型配置管理'),
 (200,'模型配置',   2, 1, 'model',         'llm/model/index',                'C', 0, 'llm:model:list',       'MagicStick','模型实例管理'),
 (201,'供应商管理', 2, 2, 'provider',      'llm/provider/index',             'C', 0, 'llm:provider:list',    'Shop',   '供应商管理'),
 (202,'调用日志',   2, 3, 'calllog',       'llm/calllog/index',              'C', 0, 'llm:log:list',         'Document','调用日志'),

 (3,  '工作流',     0, 3, '/workflow',     NULL,                             'M', 0, '',            'Connection',   '工作流编排'),
 (300,'流程列表',   3, 1, 'list',          'workflow/list/index',            'C', 0, 'workflow:def:list',    'Tickets','工作流定义列表'),
 (301,'运行实例',   3, 2, 'instance',      'workflow/instance/index',        'C', 0, 'workflow:inst:list',   'List',   '运行实例'),

 (4,  '智能体',     0, 4, '/agent',        NULL,                             'M', 0, '',            'Avatar',       'Agent 智能体'),
 (400,'智能体管理', 4, 1, 'list',          'agent/list/index',               'C', 0, 'agent:def:list',       'Robot',  '智能体管理'),

 (5,  '工作管理',   0, 5, '/work',         NULL,                             'M', 0, '',            'Notebook',     '工作日志与总结'),
 (500,'任务看板',   5, 1, 'task',          'work/task/index',                'C', 0, 'work:task:list',       'Files',  '任务分配与跟踪'),
 (501,'工作日志',   5, 2, 'log',           'work/log/index',                 'C', 0, 'work:log:list',        'EditPen','工作日志'),
 (502,'AI 总结',    5, 3, 'summary',       'work/summary/index',             'C', 0, 'work:summary:list',    'DataAnalysis','AI 工作总结');

-- 超级管理员拥有全部菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu;

-- 普通员工：查看类菜单
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
 (3, 5), (3, 500), (3, 501), (3, 502), (3, 4), (3, 400);

-- 部门主管：多加模型中心与工作流
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
 (2, 1), (2, 100), (2, 103),
 (2, 2), (2, 200), (2, 201),
 (2, 3), (2, 300), (2, 301),
 (2, 4), (2, 400),
 (2, 5), (2, 500), (2, 501), (2, 502);

-- 字典类型
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, remark) VALUES
 (1, '用户性别',   'sys_user_sex',   0, '用户性别列表'),
 (2, '系统开关',   'sys_normal_flag', 0, '系统开关列表'),
 (3, '系统状态',   'sys_status',     0, '系统状态列表'),
 (4, '任务状态',   'work_task_status', 0, '工作任务状态'),
 (5, '任务优先级', 'work_task_priority', 0, '工作任务优先级'),
 (6, '模型类型',   'llm_model_type', 0, '大模型类型');

INSERT INTO sys_dict_data (dict_code, dict_type, dict_label, dict_value, dict_sort, list_class, status) VALUES
 (1, 'sys_user_sex',      '未知', '0', 1, 'info',    0),
 (2, 'sys_user_sex',      '男',   '1', 2, 'primary', 0),
 (3, 'sys_user_sex',      '女',   '2', 3, 'danger',  0),
 (4, 'sys_normal_flag',   '正常', '0', 1, 'success', 0),
 (5, 'sys_normal_flag',   '停用', '1', 2, 'danger',  0),
 (6, 'sys_status',        '正常', '0', 1, 'success', 0),
 (7, 'sys_status',        '停用', '1', 2, 'danger',  0),
 (8, 'work_task_status',  '待开始', 'TODO', 1, 'info',    0),
 (9, 'work_task_status',  '进行中', 'DOING', 2, 'primary', 0),
 (10,'work_task_status',  '待验收', 'REVIEW', 3, 'warning', 0),
 (11,'work_task_status',  '已完成', 'DONE', 4, 'success', 0),
 (12,'work_task_status',  '已取消', 'CANCELLED', 5, 'info', 0),
 (13,'work_task_priority','低',   'LOW', 1, 'info',    0),
 (14,'work_task_priority','普通', 'NORMAL', 2, 'primary', 0),
 (15,'work_task_priority','高',   'HIGH', 3, 'warning', 0),
 (16,'work_task_priority','紧急', 'URGENT', 4, 'danger', 0),
 (17,'llm_model_type',    '对话模型','chat', 1, 'primary', 0),
 (18,'llm_model_type',    '向量模型','embedding', 2, 'success', 0),
 (19,'llm_model_type',    '重排模型','rerank', 3, 'warning', 0);

-- 系统参数（含邮件与默认模型）
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type, remark) VALUES
 (1, '主框架页-默认皮肤', 'sys.index.skinName', 'skin-blue', 1, '风格 skin-blue / skin-dark'),
 (2, '用户初始密码',      'sys.user.initPassword', 'admin123', 1, '新建用户默认密码'),
 (3, '邮件服务器地址',    'sys.mail.host',     'smtp.qq.com', 0, 'SMTP 服务器地址'),
 (4, '邮件服务端口',      'sys.mail.port',     '465',         0, 'SMTP 端口(SSL)'),
 (5, '邮件发送账号',      'sys.mail.username', '',            0, '发件人邮箱'),
 (6, '邮件发送密码',      'sys.mail.password', '',            0, '邮箱授权码'),
 (7, '默认发件人昵称',    'sys.mail.fromName', 'AI 工作助手',  0, '发件人显示名'),
 (8, '默认总结模型',      'sys.summary.modelId', '',          0, 'AI 总结默认使用的模型ID');

-- 模型供应商
INSERT INTO llm_provider (provider_id, provider_code, provider_name, base_url, protocol, api_key_hint, sort_num, status, remark)
VALUES
 (1, 'openai',   'OpenAI',        'https://api.openai.com/v1',     'openai', 'sk-xxx',            1, 0, '官方接口'),
 (2, 'deepseek', 'DeepSeek',      'https://api.deepseek.com',      'openai', 'sk-xxx',            2, 0, '国产高性价比'),
 (3, 'qwen',     '阿里云通义千问', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'openai', 'sk-xxx', 3, 0, '通义千问'),
 (4, 'zhipu',    '智谱 GLM',      'https://open.bigmodel.cn/api/paas/v4', 'openai', 'id.secret',  4, 0, 'GLM 系列'),
 (5, 'agicto',   'Agicto 聚合',   'https://api.agicto.cn/v1',      'openai', 'sk-xxx',            5, 0, 'OpenAI 兼容聚合网关'),
 (6, 'ollama',   'Ollama 本地',   'http://127.0.0.1:11434/v1',     'openai', 'ollama',            6, 0, '本地私有化部署'),
 (7, 'custom',   '自定义(兼容 OpenAI)', NULL,                       'openai', '留空则免鉴权',        9, 0, '任意 OpenAI 兼容端点');

-- 演示工作任务
INSERT INTO work_task (task_id, task_title, task_content, assignee_id, assignee_name, dept_id, priority, status, progress, due_date, tags, parent_id, create_by)
VALUES
 (1, '搭建 RBAC 权限中台', '完成用户/角色/菜单/部门/岗位五件套的后端 API 与前端页面，支持数据权限隔离。',
     2, '张伟', 101, 'HIGH', 'DOING', 60, '2026-10-15', '后端,权限', 0, 'admin'),
 (2, '接入大模型网关', '统一模型配置中心，支持 OpenAI 兼容协议，实现同步与流式两种调用方式。',
     3, '李四', 104, 'HIGH', 'DOING', 75, '2026-10-12', '后端,LLM', 0, 'admin'),
 (3, '设计工作流画布交互', '基于 Vue Flow 实现节点拖拽、连线校验、参数配置抽屉。',
     4, '王芳', 102, 'NORMAL', 'TODO', 20, '2026-10-20', '前端,交互', 0, 'admin'),
 (4, 'AI 周报自动总结', '按时间范围聚合工作日志，调用大模型生成周报，并支持邮件推送给主管。',
     3, '李四', 104, 'URGENT', 'REVIEW', 90, '2026-10-10', '后端,AI', 0, 'admin');

-- 演示工作日志（近 5 天，便于测试周报总结）
INSERT INTO work_log (log_id, task_id, log_title, content, work_date, hours_spent, progress_snap, log_type, blockers, plan_next, user_id, username, dept_id, status)
VALUES
 (1, 2, '完成模型配置表设计与 CRUD', '设计 llm_provider / llm_model / llm_model_scope 三张表；完成后端 CRUD 与连通性测试接口；验证 api.agicto.cn 网关可正常返回流式数据。',
     DATE_SUB(CURDATE(), INTERVAL 4 DAY), 7.5, 80, 'DAILY', '流式响应在网关层有偶发断包，已加重试', '联调前端模型列表页', 3, '李四', 104, 'NORMAL'),
 (2, 2, '实现 LlmClient 统一客户端', '基于 WebClient 封装 chat/completions 调用，统一超时、重试、调用日志记录；支持 temperature/max_tokens 覆盖。',
     DATE_SUB(CURDATE(), INTERVAL 3 DAY), 6.0, 85, 'DAILY', '', '编写单元测试覆盖异常分支', 3, '李四', 104, 'NORMAL'),
 (3, 1, 'RBAC 数据权限方案落地', '实现 data_scope 五种范围：全部/自定义/本部门/本部门及以下/仅本人；通过 MyBatis-Plus 数据权限拦截器注入 SQL 片段。',
     DATE_SUB(CURDATE(), INTERVAL 3 DAY), 8.0, 55, 'DAILY', '部门树递归查询在数据量上来后需加缓存', '补充部门树 caches', 2, '张伟', 101, 'NORMAL'),
 (4, 1, '角色授权前端联调', '完成角色-菜单授权树、用户-角色绑定界面的联调，修复勾选半选状态回显异常。',
     DATE_SUB(CURDATE(), INTERVAL 2 DAY), 5.5, 70, 'DAILY', '', '开始写岗位管理', 2, '张伟', 101, 'NORMAL'),
 (5, 4, 'AI 总结提示词调优', '迭代三版提示词，最终确定 结构化 Markdown 输出格式，包含 本周概览/关键进展/风险阻塞/下周计划 四段。',
     DATE_SUB(CURDATE(), INTERVAL 2 DAY), 6.5, 90, 'DAILY', '长日志被截断，需按重要性做摘要预处理', '实现日志预处理分片', 4, '王芳', 105, 'NORMAL'),
 (6, 3, 'Vue Flow 画布原型', '搭建画布骨架，实现左右侧节点面板拖拽、节点选中态与删除，连线暂未做校验。',
     DATE_SUB(CURDATE(), INTERVAL 1 DAY), 7.0, 40, 'DAILY', '节点过多时画布缩放卡顿，考虑虚拟化', '加锚点吸附与连线校验', 4, '王芳', 105, 'NORMAL'),
 (7, 2, '调用日志模块上线', '记录每次请求的耗时、token 消耗与错误信息，前端提供按模型维度的分钟级趋势图。',
     CURDATE(), 4.0, 85, 'DAILY', '', '接入告警阈值', 3, '李四', 104, 'NORMAL'),
 (8, 1, '字典与参数配置模块', '完成字典类型/字典数据/系统参数三个模块，统一前端 DictTag 组件渲染。',
     CURDATE(), 5.0, 75, 'DAILY', '', '开始写操作日志', 2, '张伟', 101, 'NORMAL');

-- 演示工作流定义（一个简单的「需求→摘要→邮件」流程）
INSERT INTO wf_definition (def_id, def_code, def_name, category, icon, description, graph_json, input_schema, version, status, published, dept_id, create_by)
VALUES
 (1, 'weekly_digest', '周报自动摘要流程', '办公自动化', 'DataAnalysis',
     '输入本周工作日志，调用大模型生成周报摘要，并按分支决定是否发送邮件通知主管。',
     '{"nodes":[{"id":"start","type":"start","name":"开始","position":{"x":80,"y":200},"data":{"outputs":["rawText"]}},{"id":"n_llm","type":"llm","name":"生成摘要","position":{"x":320,"y":200},"data":{"modelId":null,"modelCode":"","systemPrompt":"你是一名善于提炼重点的项目管理助理，输出简洁的中文报告。","userPrompt":"请把以下工作日志整理为一份周报，包含【本周概览】【关键进展】【风险阻塞】【下周计划】四个小节：\\n{{rawText}}","temperature":0.4,"maxTokens":1500,"outputVar":"summaryMd"}},{"id":"n_cond","type":"condition","name":"摘要是否为空","position":{"x":560,"y":200},"data":{"branches":[{"condition":"{{summaryMd}} != null && {{summaryMd}} != \u0027\u0027","target":"n_end_ok","label":"有内容"},{"condition":"true","target":"n_end_fail","label":"为空"}],"defaultTarget":"n_end_fail"}},{"id":"n_end_ok","type":"end","name":"输出结果","position":{"x":800,"y":120},"data":{"outputTemplate":"{{summaryMd}}"}},{"id":"n_end_fail","type":"end","name":"摘要失败","position":{"x":800,"y":300},"data":{"outputTemplate":"摘要生成失败"}}],"edges":[{"id":"e1","source":"start","target":"n_llm"},{"id":"e2","source":"n_llm","target":"n_cond"},{"id":"e3","source":"n_cond","target":"n_end_ok","sourceHandle":"有内容"},{"id":"e4","source":"n_cond","target":"n_end_fail","sourceHandle":"为空"}]}',
     '[{"name":"rawText","label":"原始日志文本","type":"string","required":true}]',
     1, 1, 1, 101, 'admin');

-- 演示智能体
INSERT INTO agent_definition (agent_id, agent_code, agent_name, avatar, category, system_prompt, prologue, tools_json, variables, status, dept_id, create_by, remark)
VALUES
 (1, 'work_assistant', '工作助手', '🤖', '办公',
     '你是企业内部的工作助手，回答要简洁、结构化，优先使用 Markdown 列表。当信息不足时应主动追问。',
     '你好，我是你的工作助手，可以帮你梳理任务、起草周报、整理会议纪要。',
     '["datetime","calculator"]',
     '[{"name":"deptName","label":"所属部门","type":"string","required":false}]',
     1, 101, 'admin', '');

-- 完成标记
SELECT 'AI Platform schema initialized.' AS result;
