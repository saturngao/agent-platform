<template>
  <div class="page-container">
    <el-card shadow="never" class="page-card">
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="名称">
          <el-input v-model="queryParams.agentName" placeholder="请输入" clearable style="width: 180px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" :value="0" />
            <el-option label="已发布" :value="1" />
            <el-option label="已停用" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" :icon="Plus" @click="handleAdd">新增智能体</el-button>
        <el-button :icon="Refresh" @click="fetchList">刷新</el-button>
      </div>

      <el-row :gutter="16" v-loading="loading">
        <el-col :span="8" v-for="a in rows" :key="a.agentId" style="margin-bottom: 16px">
          <el-card shadow="hover" class="agent-card">
            <div class="agent-head">
              <div class="agent-avatar">{{ a.avatar || '🤖' }}</div>
              <div class="agent-meta">
                <div class="agent-name">{{ a.agentName }}</div>
                <div class="agent-code">{{ a.agentCode }}</div>
              </div>
              <el-tag :type="statusType(a.status)" size="small">{{ statusLabel(a.status) }}</el-tag>
            </div>
            <div class="agent-desc">{{ a.prologue || a.systemPrompt || '暂无开场白' }}</div>
            <div class="agent-foot">
              <span>模型：{{ a.modelName || '默认模型' }}</span>
              <span>{{ a.category || '未分类' }}</span>
            </div>
            <div class="agent-actions">
              <el-button size="small" type="primary" :icon="ChatDotRound" @click="goChat(a)">对话</el-button>
              <el-button size="small" :icon="Promotion" @click="handlePublish(a)">发布</el-button>
              <el-button size="small" :icon="Edit" @click="handleEdit(a)">编辑</el-button>
              <el-button size="small" type="danger" :icon="Delete" @click="handleDelete(a)">删除</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <div v-if="!loading && rows.length === 0" class="empty-tip" style="padding: 40px 0">暂无智能体</div>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[6, 12, 24]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <el-dialog v-model="open" :title="title" width="720px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="名称" prop="agentName">
              <el-input v-model="form.agentName" placeholder="如 工作助手" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="编码" prop="agentKey">
              <el-input v-model="form.agentKey" placeholder="如 work_assistant" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="头像">
              <el-input v-model="form.avatar" placeholder="一个 emoji，如 🤖" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类">
              <el-input v-model="form.category" placeholder="如 办公" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="绑定模型">
              <el-select v-model="form.modelId" placeholder="留空则使用默认模型" clearable style="width: 100%">
                <el-option v-for="m in modelList" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
              </el-select>
              <!-- 下拉为空时给出明确原因，避免用户不知道为什么选不到 -->
              <div v-if="modelList.length === 0" class="form-tip">
                暂无可选模型：请到「模型中心 → 模型配置」新增模型。只有<b>类型=对话(chat)</b>且<b>状态=正常</b>的模型才会出现在这里（停用的模型不会列出）。
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="关联技能">
              <el-select
                v-model="form.skillIds"
                multiple
                filterable
                placeholder="可挂载多个技能，技能内容会自动注入系统提示词"
                style="width: 100%"
              >
                <el-option v-for="s in skillList" :key="s.skillId" :label="s.skillName" :value="s.skillId">
                  <span>{{ s.icon }} {{ s.skillName }}</span>
                  <span style="float: right; color: #909399; font-size: 12px">{{ s.category }}</span>
                </el-option>
              </el-select>
              <div v-if="skillList.length === 0" class="form-tip">
                暂无可用技能，可到「技能库 → 技能管理」新建或从内置市场导入。
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="系统提示词">
              <el-input v-model="form.systemPrompt" type="textarea" :rows="5" placeholder="定义智能体的身份、语气与边界" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="开场白">
              <el-input v-model="form.prologue" type="textarea" :rows="2" placeholder="用户进入对话时智能体的第一句话" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="温度">
              <el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大输出">
              <el-input-number v-model="form.maxTokens" :min="1" :step="256" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="启用工具">
              <el-checkbox-group v-model="form.tools">
                <el-checkbox v-for="t in toolOptions" :key="t" :value="t" :label="t" />
              </el-checkbox-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, Promotion, ChatDotRound } from '@element-plus/icons-vue'
import { listAgents, addAgent, editAgent, delAgent, publishAgent } from '@/api/agent'
import { modelOptions } from '@/api/llm'
import { skillOptions, skillBinding } from '@/api/skill'

const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const rows = ref([])
const total = ref(0)
const open = ref(false)
const title = ref('')
const modelList = ref([])
const skillList = ref([])
const formRef = ref()
const toolOptions = ['datetime', 'calculator', 'search', 'websearch', 'worklog']

const queryParams = reactive({ pageNum: 1, pageSize: 6, agentName: '', status: undefined })
const defaultForm = () => ({
  agentId: undefined,
  agentName: '',
  agentKey: '',
  avatar: '🤖',
  category: '',
  modelId: undefined,
  systemPrompt: '',
  prologue: '',
  temperature: 0.7,
  maxTokens: 2048,
  tools: [],
  skillIds: [],
  status: 1,
  remark: ''
})
const form = ref(defaultForm())
const rules = {
  agentName: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  agentKey: [{ required: true, message: '编码不能为空', trigger: 'blur' }]
}

function statusLabel(s) {
  return { 0: '草稿', 1: '已发布', 2: '已停用' }[s] || '草稿'
}
function statusType(s) {
  return { 0: 'info', 1: 'success', 2: 'danger' }[s] || 'info'
}

function fetchList() {
  loading.value = true
  listAgents(queryParams)
    .then((res) => {
      rows.value = res.rows || []
      total.value = res.total || 0
    })
    .finally(() => {
      loading.value = false
    })
}
function handleQuery() {
  queryParams.pageNum = 1
  fetchList()
}
function resetQuery() {
  queryParams.agentName = ''
  queryParams.status = undefined
  handleQuery()
}
/**
 * 拉取可选模型（后端只返回 status=0 正常 且 model_type=chat 的模型）
 * 打开弹窗时也重新拉一次，避免"先开页面、后配模型"导致下拉为空
 */
function loadModelOptions() {
  // 同时刷新技能选项，保证新建的技能能立刻在下拉里选到
  skillOptions()
    .then((res) => {
      skillList.value = Array.isArray(res) ? res : []
    })
    .catch(() => {
      skillList.value = []
    })
  return modelOptions()
    .then((res) => {
      modelList.value = Array.isArray(res) ? res : []
    })
    .catch(() => {
      modelList.value = []
    })
}

/** 读取某个智能体已挂载的技能 */
function loadSkillBinding(agentId) {
  if (!agentId) return
  skillBinding({ bizType: 'AGENT', bizId: agentId })
    .then((res) => {
      form.value.skillIds = Array.isArray(res) ? res : []
    })
    .catch(() => {
      form.value.skillIds = []
    })
}
function handleAdd() {
  form.value = defaultForm()
  title.value = '新增智能体'
  open.value = true
  loadModelOptions()
}
function handleEdit(row) {
  form.value = {
    ...defaultForm(),
    ...row,
    agentKey: row.agentCode || row.agentKey,
    tools: row.tools || [],
    skillIds: row.skillIds || []
  }
  title.value = '修改智能体'
  open.value = true
  loadModelOptions()
  // 行数据里可能没带 skillIds（如列表接口版本差异），这里以绑定接口为准再拉一次
  loadSkillBinding(row.agentId)
}
function submitForm() {
  formRef.value.validate((valid) => {
    if (!valid) return
    submitting.value = true
    const api = form.value.agentId ? editAgent(form.value) : addAgent(form.value)
    api
      .then(() => {
        ElMessage.success('操作成功')
        open.value = false
        fetchList()
      })
      .finally(() => {
        submitting.value = false
      })
  })
}
function handlePublish(row) {
  publishAgent(row.agentId).then(() => {
    ElMessage.success('发布成功')
    fetchList()
  })
}
function handleDelete(row) {
  ElMessageBox.confirm(`确认删除智能体「${row.agentName}」吗？其会话与消息也会一并删除。`, '提示', { type: 'warning' }).then(() => {
    delAgent(row.agentId).then(() => {
      ElMessage.success('删除成功')
      fetchList()
    })
  })
}
function goChat(row) {
  router.push('/agent/chat/' + row.agentId)
}

onMounted(() => {
  loadModelOptions()
  fetchList()
})
</script>

<style scoped>
.form-tip {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #e6a23c;
}
.agent-card {
  height: 100%;
}
.agent-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.agent-avatar {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  background: #ecf5ff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}
.agent-meta {
  flex: 1;
  min-width: 0;
}
.agent-name {
  font-weight: 600;
  color: #303133;
}
.agent-code {
  font-size: 12px;
  color: #909399;
}
.agent-desc {
  margin: 10px 0;
  font-size: 12px;
  color: #606266;
  line-height: 1.6;
  height: 38px;
  overflow: hidden;
}
.agent-foot {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f0f0;
}
.agent-actions {
  display: flex;
  gap: 6px;
  margin-top: 10px;
  flex-wrap: wrap;
}
</style>
