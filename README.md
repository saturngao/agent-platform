# AI 工作流与智能体平台（Agent Platform）

一个对标 Dify 定位的企业级 AI 应用平台：可视化编排工作流、定义智能体，并配套完整的
**用户 / 角色 / 菜单 / 部门 / 岗位** 权限体系、**大模型配置中心**、**工作日志** 与 **AI 总结 + 邮件推送**。

> 与既有 `ops-agent`（运维告警 Agent）相互独立，后续可通过 API 把运维能力接入为平台里的一个工作流/智能体。

---

## 一、技术栈

| 层 | 选型 |
|---|---|
| 前端 | Vue 3.4 + Vite 5 + Vue Router 4 + Pinia 2 + Element Plus 2.7 + Vue Flow + ECharts 5 + markdown-it |
| 后端 | Spring Boot 3.2.5 + MyBatis-Plus 3.5.7 + Spring Security + JWT(JJWT 0.12) + Hutool |
| 数据库 | MySQL 5.7+ / MariaDB 10.6+（已在 MariaDB 11.8 验证，未使用 MySQL 8 专有语法） |
| 大模型 | 任意 **OpenAI 兼容协议**（OpenAI / DeepSeek / 通义千问 / 智谱 / Ollama / 自建网关） |

---

## 二、功能清单

### 1. 系统管理（RBAC 五件套）
- 用户管理：增删改查、重置密码、启停、绑定角色与岗位、部门归属
- 角色管理：菜单授权树、**数据权限范围**（全部 / 自定义 / 本部门 / 本部门及以下 / 仅本人）
- 菜单管理：目录 / 菜单 / 按钮三级，58 个内置按钮权限标识
- 部门管理：树形结构，带祖级路径 `ancestors`
- 岗位管理、字典管理、系统参数配置（含邮件 SMTP 参数）

### 2. 大模型配置中心（本平台的中枢）
- 供应商管理：OpenAI / DeepSeek / 通义千问 / 智谱 / Agicto / Ollama / 自定义
- 模型实例：endpoint、apiKey、上游模型标识、温度、topP、最大输出、超时、上下文窗口、流式/JSON 输出开关
- 一键设为默认模型；**连通性测试**真实发一条请求并展示返回内容与耗时
- 调用日志：按业务类型（工作流/智能体/总结/测试）记录耗时、token、成功失败

### 3. 工作流编排
- Vue Flow 可视化画布：8 种节点 **开始 / 大模型 / 条件分支 / HTTP / 数据变换 / 知识注入 / 智能体 / 结束**
- 自研 Java DAG 执行引擎：拓扑遍历、变量上下文 `{{变量}}` 渲染、环路保护、节点级运行日志
- 条件分支极简求值器（支持 `== null`、`== 'x'`、数字比较、`&&`、`||`），异常一律按不匹配处理
- 运行实例 + 节点时间线（入参/出参/耗时/错误）

### 4. 智能体（Agent）
- 定义：系统提示词、开场白、绑定模型（可覆盖温度与最大输出）、工具集、发布/停用
- 多轮对话：会话管理、历史消息落库、Markdown 渲染 + 代码高亮、SSE 流式输出

### 5. 工作日志
- 任务看板：状态流转（待开始/进行中/待验收/已完成/已取消）、优先级、进度、负责人、截止日期临期标红
- 工作日志：按日期/任务/人员记录，含工时、进度快照、成果、阻塞、下一步计划；最近 7 天日志量柱状图

### 6. AI 总结 + 邮件推送（核心亮点）
- 按 **本人 / 本部门 / 自选成员** + **时间范围** 聚合工作日志
- 自动拼装结构化原文（含任务进展），调用大模型生成五段式 Markdown 报告：
  **本期概览 / 关键进展 / 风险与阻塞 / 下期计划 / 给管理者的建议**
- 结果保存为记录（含原文快照、token、耗时），支持 **重新生成**
- 一键邮件推送：主题、收件人、抄送（可从成员中选择或手输邮箱），发送后记录状态
- 邮件参数可在【系统管理 → 参数配置】动态设置，无需重启

---

## 三、快速开始

### 1. 初始化数据库
```bash
mysql -u root -p < sql/schema.sql              # 建库建表 + 演示数据
mysql -u root -p < sql/init-permissions.sql    # 按钮级权限与角色授权
```
> 默认连接 `root/root`（ops-agent 同款）。如需修改，编辑 `server/src/main/resources/application.yml`。

### 2. 启动后端
```bash
cd server
mvn spring-boot:run                 # 默认 8088 端口
```
启动后可见控制台 ASCII Logo。

### 3. 启动前端
```bash
cd web
npm install        # 依赖已安装则可跳过
npm run dev        # http://localhost:5173，已配置 /api 代理到 8088
```

### 4. 登录
| 账号 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin123` | 超级管理员（全部权限） |
| `zhangwei` | `admin123` | 部门主管 |
| `lisi` | `admin123` | 普通员工 |

---

## 四、首次使用路线（建议按顺序）

1. **模型中心 → 模型配置 → 新增模型**
   选择供应商（如 Agicto / DeepSeek），填 endpoint 与 apiKey，保存后点「测试」验证连通性，并「设为默认」。
   > 没有可用模型时，工作流 LLM 节点、智能体对话、AI 总结都会提示"尚未配置可用的大模型"。
2. **工作流 → 流程列表 → 新建流程**
   进入画布拖入节点并连线，LLM 节点选择模型、填写提示词（支持 `{{变量}}`），保存 → 发布 → 运行。
3. **智能体 → 新建智能体**，绑定模型后点「对话」体验多轮会话。
4. **工作管理 → 工作日志** 先记录几条日志，再到 **AI 总结** 选择时间范围生成周报。
5. 如需邮件推送：到 **系统管理 → 参数配置** 设置 `sys.mail.host/port/username/password`。

---

## 五、目录结构

```
agent-platform/
├── sql/
│   ├── schema.sql              27 张表 + 演示数据（任务/日志/示例工作流/示例智能体）
│   └── init-permissions.sql    58 个按钮权限 + 三套角色授权
├── server/                     Spring Boot 后端
│   └── src/main/java/com/aip/platform/
│       ├── common/             统一响应 R / 分页 / 异常 / SecurityUtils
│       ├── config/             Security / MyBatis-Plus / Web / 异步线程池
│       ├── security/           JWT 签发与解析、登录主体 LoginUser
│       ├── system/             RBAC：用户 角色 菜单 部门 岗位 字典 参数
│       ├── llm/                模型配置 + 统一客户端 LlmClient（同步/流式/日志）
│       ├── workflow/           工作流定义 + DAG 引擎 + 8 类节点执行器
│       ├── agent/              智能体定义 + 会话 + 消息
│       └── work/               任务 / 日志 / AI总结 / 邮件服务
└── web/                        Vue3 前端
    └── src/
        ├── api/                接口层（auth / system / llm / workflow / agent / work）
        ├── layout/             后台布局（侧边栏、顶栏、主区）
        ├── router/             静态路由 + 后端动态路由注入
        └── views/              system / llm / workflow / agent / work 各业务页面
```

---

## 六、接口约定

- 统一响应：`{ code: 200, msg, data }`；`code=401` 未登录，`500` 失败
- 分页：入参 `{pageNum, pageSize}`（传 0 或负数为不分页全量）；返回 `{rows, total, pageNum, pageSize, totalPages}`
- 鉴权：请求头 `Authorization: Bearer <token>`
- 写操作用 `@PreAuthorize("@permissionChecker.has('xxx')")`，超管自动放行

主要前缀：`/api/auth/**`、`/api/system/**`、`/api/llm/**`、`/api/workflow/**`、`/api/agent/**`、`/api/work/**`

---

## 七、已知边界（MVP 范围）

- `CodeExecutor` 只支持模板渲染 / 取值路径 / 数组拼接三种受控变换，**不执行任意脚本**（安全考虑，后续可接入 GraalVM）
- `KnowledgeExecutor` 为静态知识片段注入，尚未接入向量检索
- `AgentExecutor` 在 MVP 阶段复用 LLM 节点逻辑
- 模型 apiKey 以明文存储，生产环境请改为加密或接入 KMS
- 工作流执行走同步调用，长流程建议接入消息队列（引擎已预留 `runAsync`）

---

## 八、常见问题

**Q：登录报"服务器异常"？**
检查数据库是否已执行 `schema.sql`，以及 `application.yml` 中的连接账号密码。

**Q：新增模型后测试失败？**
确认 endpoint 是否指向 `/chat/completions`（只填根地址会自动补全），以及 apiKey 是否正确、网络是否可达。

**Q：AI 总结提示"所选时间范围内没有可总结的工作日志"？**
演示数据的日志日期是建库当天往前推 5 天，请选择覆盖该区间的时间范围，或先在【工作日志】新增几条。

**Q：邮件发送失败？**
确认 `sys.mail.*` 参数完整；QQ 邮箱需使用**授权码**而非登录密码，端口 465 走 SSL。

---

## 九、捐献作者

作者为兼职做开源，平时还需要工作，如果帮到了您可以请作者吃个盒饭 🍱

| 微信支付 | 支付宝 |
|---|---|
| ![微信收款码](docs/images/wechat-pay.jpg) | ![支付宝收款码](docs/images/alipay.jpg) |
