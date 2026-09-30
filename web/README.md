# AI 工作流与智能体平台 · 管理后台（web）

Vue 3.4 + Vite 5 + Element Plus 2.7 的管理后台前端脚手架，包含登录、动态路由、布局框架与系统管理 CRUD 页面。

## 技术栈

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| vue | 3.4.x | Composition API + `<script setup>` |
| vite | 5.x | 构建 / dev server（端口 5173） |
| vue-router | 4.x | 静态路由 + 后端动态路由 |
| pinia | 2.x | 状态管理 |
| element-plus | 2.7.x | UI 组件库（浅色主题、中文语言包） |
| axios | 1.x | 请求封装（baseURL `/api`） |
| @vue-flow/core | 1.x | 工作流画布（含 background / controls） |
| echarts | 5.x | 首页看板图表 |
| dayjs | 1.x | 时间格式化 |
| markdown-it / highlight.js | - | 富文本 / 代码高亮（智能体会话等场景预留） |

## 快速开始

```bash
# 安装依赖
npm install

# 启动开发服务器（http://localhost:5173）
npm run dev

# 生产构建
npm run build
```

dev server 已配置代理：`/api/*` → `http://localhost:8088/*`（见 `vite.config.js`，可用 `.env.development` 中的 `VITE_PROXY_TARGET` 覆盖）。

后端需先启动在 `http://localhost:8088`，否则登录后无法获取用户信息与菜单。

## 目录约定

```
src/
├── api/          # 接口层：auth/system 已实现，llm/workflow/agent/work 为预留模块
├── layout/       # 经典后台布局（Sidebar + Navbar + AppMain）
├── router/       # 静态路由（/login、/404、/ -> Layout）
├── stores/       # pinia：user / app / permission
├── styles/       # 全局样式（index.scss / variables.scss / sidebar.scss）
├── utils/        # request(axios 封装) / auth(token) / dict(字典 hook) / index(通用工具)
├── views/        # 页面，按后端 component 字段路径放置
├── permission.js # 路由守卫（token 校验 + 动态路由注入）
└── settings.js   # 站点标题、侧栏主题等配置
```

### 组件映射规则（重要）

后端 `/api/auth/routers` 返回的 `RouterVO.component`：

| component 值 | 映射到 |
| --- | --- |
| `Layout` | `src/layout/index.vue` |
| `system/user/index` | `src/views/system/user/index.vue` |
| 目录形式 `system/user` | 自动尝试 `system/user/index.vue` |
| 未命中 | 回退到 `src/views/error/404.vue` |

> 新增页面时，**文件路径必须等于后端菜单配置里的 component 值 + `.vue`**，例如菜单 component 填 `llm/model/index`，则在 `src/views/llm/model/index.vue` 建页面。

## 接口约定

- 统一响应体 `{ code, msg, data }`：`code=200` 成功、`401` 未登录、`其它` 失败。
- 请求成功时，axios 响应拦截器只返回 `data` 部分，页面里直接 `res.rows / res.total`。
- 分页参数统一 `query: { pageNum, pageSize }`，返回 `{ rows, total, pageNum, pageSize, totalPages }`。
- 删除接口 ids 逗号分隔放 path，例如 `DELETE /system/user/1,2,3`。
- 登录成功后 token 写入 Cookie（`Admin-Token`）与 localStorage，每次请求带 `Authorization: Bearer <token>`。

## 通用用法

```js
// 1. 请求
import { http } from '@/utils/request'
const data = await http.get('/system/user', { pageNum: 1, pageSize: 10 })

// 2. 接口模块
import { listUser } from '@/api/system'

// 3. 字典
import { useDict } from '@/utils/dict'
const { sys_user_sex } = useDict('sys_user_sex') // ref 数组，带内存缓存

// 4. 工具函数
import { parseTime, formatDate, addDateRange, deepClone, buildTree, download } from '@/utils/index'

// 5. token
import { getToken, setToken, removeToken } from '@/utils/auth'
```

## 页面规范（后续开发者请遵循）

1. 统一 `<script setup>` + Composition API，不写 Options API，不引入 TypeScript。
2. 页面根节点使用 `<div class="page-container">`，卡片用 `.page-card`，搜索表单用 `.search-form`，工具条用 `.table-toolbar`，分页用 `.pagination-container`（样式已在 `src/styles/index.scss` 全局定义）。
3. 列表页必备：搜索 / 重置、新增 / 删除、表格 `v-loading`、分页、删除二次确认、空状态 `#empty`。
4. 状态列用 `el-tag` 或 `el-switch`，日期列用 `parseTime` 格式化。
5. 新增与修改共用同一个 `el-dialog`（`destroy-on-close`），保存前走 `formRef.validate()`。
