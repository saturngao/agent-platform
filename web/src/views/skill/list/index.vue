<template>
  <div class="page-container">
    <div class="page-card">
      <!-- 搜索栏 -->
      <el-form :model="queryParams" inline class="search-form" @submit.prevent>
        <el-form-item label="名称/编码">
          <el-input v-model="queryParams.skillName" placeholder="技能名称或编码" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="queryParams.category" placeholder="全部分类" clearable style="width: 130px">
            <el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="queryParams.source" placeholder="全部来源" clearable style="width: 130px">
            <el-option label="官方内置" value="OFFICIAL" />
            <el-option label="导入" value="IMPORT" />
            <el-option label="自建" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 120px">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 工具条 -->
      <div class="table-toolbar">
        <div>
          <el-button type="primary" icon="Plus" @click="handleAdd">新建技能</el-button>
          <el-button type="warning" icon="Upload" @click="importOpen = true">导入技能</el-button>
          <el-button icon="Goods" @click="marketOpen = true">内置技能市场</el-button>
          <el-button
            type="danger"
            icon="Delete"
            :disabled="ids.length === 0"
            @click="handleDelete()"
          >
            删除
          </el-button>
        </div>
        <div class="table-toolbar__right">
          <el-tooltip content="刷新" placement="top">
            <el-button icon="Refresh" circle @click="getList" />
          </el-tooltip>
        </div>
      </div>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="list" row-key="skillId" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="46" align="center" />
        <el-table-column label="技能" min-width="220">
          <template #default="{ row }">
            <div class="skill-cell">
              <span class="skill-icon">{{ row.icon || '🧩' }}</span>
              <div class="skill-meta">
                <div class="skill-name">{{ row.skillName }}</div>
                <div class="skill-code">{{ row.skillCode }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分类" prop="category" width="90" align="center">
          <template #default="{ row }">{{ row.category || '-' }}</template>
        </el-table-column>
        <el-table-column label="来源" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="sourceType(row.source)">{{ sourceLabel(row.source) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="简介" prop="summary" min-width="220" show-overflow-tooltip />
        <el-table-column label="被引用" width="130" align="center">
          <template #default="{ row }">
            <span v-if="(row.agentCount || 0) + (row.workflowCount || 0) === 0" class="ref-none">未使用</span>
            <span v-else>
              智能体 {{ row.agentCount || 0 }} · 流程 {{ row.workflowCount || 0 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 0"
              @change="(val) => handleStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" icon="View" @click="handlePreview(row)">查看</el-button>
            <el-button link type="primary" icon="Edit" @click="handleUpdate(row)">修改</el-button>
            <el-button
              link
              type="danger"
              icon="Delete"
              :disabled="row.source === 'OFFICIAL'"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-tip">暂无技能，可点击「新建技能」「导入技能」或从「内置技能市场」添加</div>
        </template>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="getList"
          @current-change="getList"
        />
      </div>
    </div>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="open" :title="title" width="920px" append-to-body destroy-on-close>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="base">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="86px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="技能名称" prop="skillName">
              <el-input v-model="form.skillName" placeholder="如：周报撰写助手" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="技能编码" prop="skillCode">
              <el-input v-model="form.skillCode" placeholder="英文唯一标识，如 weekly-report" :disabled="form.skillId != null" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="图标">
              <el-input v-model="form.icon" placeholder="emoji" maxlength="8" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="分类">
              <el-select v-model="form.category" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="版本">
              <el-input v-model="form.version" placeholder="1.0.0" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="一句话简介">
              <el-input v-model="form.summary" placeholder="用一句话说明这个技能做什么" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="标签">
              <el-select v-model="form.tagList" multiple filterable allow-create placeholder="回车创建标签" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="技能内容" prop="content">
              <el-input
                v-model="form.content"
                type="textarea"
                :rows="14"
                placeholder="用 Markdown 写清楚：角色、适用场景、输入输出规范、约束条件。这段内容会注入到大模型的系统提示词中。"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="技能内容即提示词"
          description="保存后可在【智能体管理】编辑智能体时挂载该技能，也可在工作流设计器中为流程挂载。挂载后调用大模型时会自动注入这段内容。"
        />
      </el-form>
        </el-tab-pane>

        <!-- 脚本页签：给技能挂 Python 脚本，对话中可被模型自动调用 -->
        <el-tab-pane name="script">
          <template #label>
            脚本<span v-if="scripts.length" class="tab-badge">{{ scripts.length }}</span>
          </template>

          <el-empty v-if="!form.skillId" description="技能保存后即可添加脚本" :image-size="70" />
          <template v-else>
            <div class="script-toolbar">
              <el-button type="primary" icon="Plus" size="small" @click="openScript()">新增脚本</el-button>
              <el-button icon="Refresh" size="small" @click="loadScripts">刷新</el-button>
              <span class="script-tip">
                脚本编码会被注入提示词，智能体对话时模型可自主决定调用哪个脚本
              </span>
            </div>
            <el-table :data="scripts" size="small" border>
              <el-table-column prop="scriptCode" label="编码" width="150" />
              <el-table-column prop="scriptName" label="名称" min-width="130" />
              <el-table-column prop="description" label="用途说明" min-width="180" show-overflow-tooltip />
              <el-table-column prop="timeoutSec" label="超时(秒)" width="90" align="center" />
              <el-table-column label="状态" width="80" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="Number(row.status) === 0 ? 'success' : 'info'">
                    {{ Number(row.status) === 0 ? '正常' : '停用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="190" align="center">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openScript(row)">编辑</el-button>
                  <el-button link type="success" @click="openScript(row, true)">运行</el-button>
                  <el-button link type="danger" @click="removeScript(row)">删除</el-button>
                </template>
              </el-table-column>
              <template #empty>
                <div class="empty-tip">该技能还没有脚本，点击「新增脚本」添加一个 Python 脚本</div>
              </template>
            </el-table>
          </template>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="open = false">取 消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 脚本编辑 / 运行弹窗 -->
    <el-dialog v-model="scriptOpen" :title="scriptTitle" width="880px" append-to-body destroy-on-close>
      <el-form :model="scriptForm" label-width="92px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="脚本名称">
              <el-input v-model="scriptForm.scriptName" placeholder="如：SQL 成本估算" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="脚本编码">
              <el-input
                v-model="scriptForm.scriptCode"
                :disabled="!!scriptForm.scriptId"
                placeholder="英文标识，如 explain_cost"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="用途说明">
          <el-input
            v-model="scriptForm.description"
            placeholder="给大模型看：什么时候该调用这个脚本"
          />
        </el-form-item>
        <el-form-item label="参数说明">
          <el-input
            v-model="scriptForm.paramDesc"
            type="textarea"
            :rows="2"
            placeholder='JSON 样例，如 {"table_rows": 100000, "selectivity": 0.01}'
          />
        </el-form-item>
        <el-form-item label="超时(秒)">
          <el-input-number v-model="scriptForm.timeoutSec" :min="1" :max="120" />
        </el-form-item>
        <el-form-item label="脚本内容">
          <el-input
            v-model="scriptForm.content"
            type="textarea"
            :rows="14"
            class="code-area"
            placeholder="在这里写 Python 代码"
          />
        </el-form-item>
      </el-form>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="入参与出参约定"
        description="脚本同目录会生成 input.json，环境变量 SKILL_PARAMS 传入同样的 JSON 串，二选一读取即可；print 到 stdout 的内容会作为执行结果返回。"
        style="margin-bottom: 12px"
      />

      <el-divider content-position="left">运行测试</el-divider>
      <el-input
        v-model="scriptForm.testParams"
        type="textarea"
        :rows="3"
        class="code-area"
        placeholder='入参 JSON，如 {"table_rows": 100000, "selectivity": 0.01, "has_index": true}'
      />
      <div class="run-bar">
        <el-button type="success" icon="VideoPlay" :loading="runLoading" @click="runTest">
          运行脚本
        </el-button>
        <span v-if="runResult" class="run-stat">
          <el-tag size="small" :type="runResult.success ? 'success' : 'danger'">
            {{ runResult.success ? '成功' : '失败' }}
          </el-tag>
          退出码 {{ runResult.exitCode }} · 耗时 {{ runResult.costMs }}ms
          <el-tag v-if="runResult.timedOut" size="small" type="warning">超时终止</el-tag>
        </span>
      </div>
      <div v-if="runResult" class="run-result">
        <div v-if="runResult.stdout">
          <div class="res-label">标准输出</div>
          <pre>{{ runResult.stdout }}</pre>
        </div>
        <div v-if="runResult.stderr">
          <div class="res-label">错误输出</div>
          <pre class="err">{{ runResult.stderr }}</pre>
        </div>
        <div v-if="runResult.errorMsg">
          <div class="res-label">说明</div>
          <pre class="err">{{ runResult.errorMsg }}</pre>
        </div>
      </div>

      <template #footer>
        <el-button @click="scriptOpen = false">取 消</el-button>
        <el-button type="primary" :loading="scriptLoading" @click="submitScript">保 存</el-button>
      </template>
    </el-dialog>

    <!-- 导入弹窗 -->
    <el-dialog v-model="importOpen" title="导入技能" width="700px" append-to-body destroy-on-close>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="支持粘贴 SKILL.md 全文"
        description="若内容含 YAML frontmatter（--- 包裹的 name / description），系统会自动解析出名称与简介。"
        style="margin-bottom: 14px"
      />
      <el-form :model="importForm" label-width="86px">
        <el-form-item label="技能内容" required>
          <el-input
            v-model="importForm.content"
            type="textarea"
            :rows="14"
            placeholder="把别人发布的 SKILL.md 内容整段粘贴到这里"
          />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="importForm.skillName" placeholder="留空则自动从 frontmatter 解析" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="importForm.category" placeholder="请选择" clearable style="width: 100%">
            <el-option v-for="c in categories" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importOpen = false">取 消</el-button>
        <el-button type="primary" :loading="importLoading" @click="submitImport">导入</el-button>
      </template>
    </el-dialog>

    <!-- 内置技能市场 -->
    <el-dialog v-model="marketOpen" title="内置技能市场" width="820px" append-to-body destroy-on-close>
      <div class="market-grid">
        <div v-for="s in officialSkills" :key="s.skillId" class="market-card">
          <div class="market-head">
            <span class="market-icon">{{ s.icon }}</span>
            <div>
              <div class="market-name">{{ s.skillName }}</div>
              <el-tag size="small" type="primary">{{ s.category }}</el-tag>
            </div>
          </div>
          <p class="market-summary">{{ s.summary }}</p>
          <div class="market-foot">
            <el-button link type="primary" @click="handlePreview(s)">查看内容</el-button>
            <el-button link type="success" @click="cloneAsCustom(s)">复制为自建技能</el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="marketOpen = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- 内容预览抽屉 -->
    <el-drawer v-model="previewOpen" :title="preview.skillName || '技能内容'" size="52%">
      <div class="preview-head">
        <span class="preview-icon">{{ preview.icon || '🧩' }}</span>
        <div>
          <div class="preview-name">{{ preview.skillName }}</div>
          <div class="preview-sub">
            {{ preview.skillCode }} · {{ sourceLabel(preview.source) }} · v{{ preview.version || '1.0.0' }}
          </div>
        </div>
      </div>
      <el-divider />
      <div class="md-body" v-html="renderMd(previewContent)"></div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import {
  listSkills,
  addSkill,
  editSkill,
  delSkill,
  changeSkillStatus,
  skillCategories,
  importSkill,
  listSkillScripts,
  addSkillScript,
  editSkillScript,
  delSkillScript,
  runSkillScript,
  runInlineScript
} from '@/api/skill'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return '<pre class="hljs"><code>' + hljs.highlight(code, { language: lang, ignoreIllegals: true }).value + '</code></pre>'
      } catch (e) {
        /* ignore */
      }
    }
    return '<pre class="hljs"><code>' + md.utils.escapeHtml(code) + '</code></pre>'
  }
})
const renderMd = (t) => (t ? md.render(t) : '')

const loading = ref(false)
const submitLoading = ref(false)
const importLoading = ref(false)
const list = ref([])
const total = ref(0)
const ids = ref([])
const open = ref(false)
const importOpen = ref(false)
const marketOpen = ref(false)
const previewOpen = ref(false)
const title = ref('')
const categories = ref([])
const formRef = ref()

const preview = ref({})
const previewContent = computed(() => preview.value.content || '')

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  skillName: '',
  category: undefined,
  source: undefined,
  status: undefined
})

const defaultForm = () => ({
  skillId: null,
  skillName: '',
  skillCode: '',
  category: undefined,
  icon: '🧩',
  version: '1.0.0',
  summary: '',
  content: '',
  tagList: []
})
const form = ref(defaultForm())

const importForm = reactive({
  content: '',
  skillName: '',
  category: undefined
})

/* ------------------------------ 技能脚本 ------------------------------ */
/** 编辑弹窗当前页签 */
const activeTab = ref('base')
const scripts = ref([])
const scriptOpen = ref(false)
const scriptTitle = ref('')
const scriptLoading = ref(false)
const runLoading = ref(false)
const runResult = ref(null)
/** 打开脚本时的原始内容，用于判断运行时该调「已保存脚本」还是「调试执行」 */
let originContent = ''

const defaultScriptForm = () => ({
  scriptId: null,
  skillId: null,
  scriptName: '',
  scriptCode: '',
  description: '',
  paramDesc: '',
  language: 'python',
  content: '',
  timeoutSec: 30,
  testParams: '{}'
})
const scriptForm = ref(defaultScriptForm())

async function loadScripts() {
  if (!form.value.skillId) {
    scripts.value = []
    return
  }
  try {
    scripts.value = (await listSkillScripts(form.value.skillId)) || []
  } catch (e) {
    scripts.value = []
  }
}

/** 打开脚本编辑弹窗；runOnly=true 时直接进入运行态 */
function openScript(row, runOnly = false) {
  runResult.value = null
  scriptForm.value = {
    ...defaultScriptForm(),
    ...(row || {}),
    skillId: form.value.skillId,
    timeoutSec: Number(row?.timeoutSec) || 30
  }
  originContent = row?.content || ''
  scriptTitle.value = row?.scriptId ? '编辑脚本' : '新增脚本'
  scriptOpen.value = true
  if (runOnly) {
    nextTick(() => runTest())
  }
}

async function submitScript() {
  if (!scriptForm.value.scriptName || !scriptForm.value.scriptCode) {
    ElMessage.warning('脚本名称与编码不能为空')
    return
  }
  if (!scriptForm.value.content || !scriptForm.value.content.trim()) {
    ElMessage.warning('脚本内容不能为空')
    return
  }
  scriptLoading.value = true
  try {
    const payload = { ...scriptForm.value, skillId: form.value.skillId }
    delete payload.testParams
    if (scriptForm.value.scriptId) {
      await editSkillScript(payload)
      ElMessage.success('脚本修改成功')
    } else {
      await addSkillScript(payload)
      ElMessage.success('脚本新增成功')
    }
    originContent = scriptForm.value.content
    scriptOpen.value = false
    await loadScripts()
  } catch (e) {
    /* 统一提示 */
  } finally {
    scriptLoading.value = false
  }
}

async function removeScript(row) {
  try {
    await ElMessageBox.confirm(`确认删除脚本「${row.scriptName}」？`, '警告', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  await delSkillScript(row.scriptId)
  ElMessage.success('删除成功')
  await loadScripts()
}

/** 运行测试：内容未改动时跑已保存脚本（落执行记录），否则以调试模式跑当前编辑内容 */
async function runTest() {
  if (!scriptForm.value.content || !scriptForm.value.content.trim()) {
    ElMessage.warning('请先填写脚本内容')
    return
  }
  let params = scriptForm.value.testParams
  if (!params || !params.trim()) {
    params = '{}'
  }
  runLoading.value = true
  runResult.value = null
  try {
    const unchanged = scriptForm.value.scriptId && scriptForm.value.content === originContent
    runResult.value = unchanged
      ? await runSkillScript({ scriptId: scriptForm.value.scriptId, params })
      : await runInlineScript({
          code: scriptForm.value.content,
          params,
          timeoutSec: scriptForm.value.timeoutSec,
          skillId: form.value.skillId
        })
  } catch (e) {
    /* 统一提示 */
  } finally {
    runLoading.value = false
  }
}

/** 打开技能弹窗后同步加载脚本列表 */
async function afterOpenSkill() {
  activeTab.value = 'base'
  runResult.value = null
  await loadScripts()
}

const rules = {
  skillName: [{ required: true, message: '技能名称不能为空', trigger: 'blur' }],
  skillCode: [{ required: true, message: '技能编码不能为空', trigger: 'blur' }],
  content: [{ required: true, message: '技能内容不能为空', trigger: 'blur' }]
}

/** 内置（官方）技能，用于「技能市场」展示 */
const officialSkills = computed(() => list.value.filter((s) => s.source === 'OFFICIAL'))

const sourceLabel = (s) => ({ OFFICIAL: '官方内置', IMPORT: '导入', CUSTOM: '自建' }[s] || '自建')
const sourceType = (s) => ({ OFFICIAL: 'primary', IMPORT: 'warning', CUSTOM: 'success' }[s] || 'info')

async function getList() {
  loading.value = true
  try {
    const res = await listSkills(queryParams)
    list.value = (res?.rows || []).map((s) => ({ ...s, status: Number(s.status) }))
    total.value = res?.total || 0
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}
function resetQuery() {
  queryParams.skillName = ''
  queryParams.category = undefined
  queryParams.source = undefined
  queryParams.status = undefined
  queryParams.pageNum = 1
  getList()
}
function handleSelectionChange(selection) {
  ids.value = selection.map((i) => i.skillId)
}

function handleAdd() {
  form.value = defaultForm()
  title.value = '新建技能'
  open.value = true
  afterOpenSkill()
}

async function handleUpdate(row) {
  title.value = '修改技能'
  form.value = {
    ...defaultForm(),
    ...row,
    tagList: row.tagList || []
  }
  open.value = true
  await afterOpenSkill()
}

function submitForm() {
  formRef.value?.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.value.skillId) {
        await editSkill(form.value)
        ElMessage.success('修改成功')
      } else {
        await addSkill(form.value)
        ElMessage.success('新增成功')
      }
      open.value = false
      getList()
    } catch (e) {
      /* 统一提示 */
    } finally {
      submitLoading.value = false
    }
  })
}

async function submitImport() {
  if (!importForm.content || !importForm.content.trim()) {
    ElMessage.warning('请粘贴技能内容')
    return
  }
  importLoading.value = true
  try {
    await importSkill({
      content: importForm.content,
      skillName: importForm.skillName,
      category: importForm.category,
      source: 'IMPORT'
    })
    ElMessage.success('导入成功')
    importOpen.value = false
    importForm.content = ''
    importForm.skillName = ''
    getList()
  } catch (e) {
    /* 统一提示 */
  } finally {
    importLoading.value = false
  }
}

/** 把官方技能复制成一份自建技能，便于二次编辑 */
function cloneAsCustom(s) {
  form.value = {
    ...defaultForm(),
    skillName: s.skillName + '（副本）',
    skillCode: s.skillCode + '-copy-' + Date.now().toString().slice(-5),
    category: s.category,
    icon: s.icon,
    version: s.version,
    summary: s.summary,
    content: s.content,
    tagList: []
  }
  title.value = '复制为自建技能'
  marketOpen.value = false
  open.value = true
  afterOpenSkill()
}

async function handleDelete(row) {
  const skillIds = row?.skillId ? String(row.skillId) : ids.value.join(',')
  if (!skillIds) return
  try {
    await ElMessageBox.confirm(
      row?.skillId ? `确认删除技能「${row.skillName}」？` : `确认删除选中的 ${ids.value.length} 个技能？`,
      '警告',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  await delSkill(skillIds)
  ElMessage.success('删除成功')
  getList()
}

async function handleStatusChange(row, val) {
  const next = val ? 0 : 1
  const text = val ? '启用' : '停用'
  const prev = row.status
  row.status = next
  try {
    await changeSkillStatus({ skillId: row.skillId, status: next })
    ElMessage.success(`${text}成功`)
  } catch (e) {
    row.status = prev
  }
}

function handlePreview(row) {
  preview.value = row || {}
  previewOpen.value = true
}

onMounted(async () => {
  try {
    categories.value = (await skillCategories()) || []
  } catch (e) {
    categories.value = ['办公', '研发', '运维', '数据', '写作', '其他'].map((v) => ({ label: v, value: v }))
  }
  getList()
})
</script>

<style scoped lang="scss">
.skill-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.skill-icon {
  font-size: 22px;
  line-height: 1;
}
.skill-name {
  font-weight: 600;
  color: #303133;
}
.skill-code {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.ref-none {
  color: #c0c4cc;
}
.market-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
}
.market-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px;
  background: #fafafa;
}
.market-head {
  display: flex;
  gap: 10px;
  align-items: center;
}
.market-icon {
  font-size: 26px;
}
.market-name {
  font-weight: 600;
  margin-bottom: 4px;
}
.market-summary {
  font-size: 12px;
  color: #606266;
  line-height: 1.6;
  margin: 10px 0;
  min-height: 40px;
}
.market-foot {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}
.preview-head {
  display: flex;
  gap: 12px;
  align-items: center;
}
.preview-icon {
  font-size: 30px;
}
.preview-name {
  font-size: 16px;
  font-weight: 600;
}
.preview-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

/* ---------- 脚本页签 ---------- */
.tab-badge {
  display: inline-block;
  margin-left: 4px;
  padding: 0 6px;
  border-radius: 9px;
  background: #409eff;
  color: #fff;
  font-size: 11px;
  line-height: 16px;
}
.script-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.script-tip {
  font-size: 12px;
  color: #909399;
}
.code-area :deep(textarea) {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12.5px;
  line-height: 1.65;
}
.run-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 10px 0;
}
.run-stat {
  font-size: 12px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 6px;
}
.run-result pre {
  margin: 4px 0 10px;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12.5px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 220px;
  overflow: auto;
}
.run-result pre.err {
  background: #fef0f0;
  color: #c45656;
}
.res-label {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}
</style>
