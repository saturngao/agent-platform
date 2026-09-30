<template>
  <div class="page-container">
    <el-alert
      v-if="!loading && rows.length === 0"
      type="warning"
      :closable="false"
      show-icon
      title="尚未配置任何大模型"
      description="工作流、智能体、AI 总结都依赖模型配置。请先新增一个 chat 类型模型并完成连通性测试。"
      style="margin-bottom: 16px"
    />

    <el-card shadow="never" class="page-card">
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="模型名称">
          <el-input v-model="queryParams.modelName" placeholder="名称/编码" clearable style="width: 180px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="模型类型">
          <el-select v-model="queryParams.modelType" placeholder="全部" clearable style="width: 130px">
            <el-option label="对话模型" value="chat" />
            <el-option label="向量模型" value="embedding" />
            <el-option label="重排模型" value="rerank" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" :icon="Plus" @click="handleAdd">新增模型</el-button>
        <el-button :icon="Refresh" @click="fetchList">刷新</el-button>
        <el-button :icon="Star" @click="handleSetDefault()">设为默认（选中行）</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border highlight-current-row @current-change="onCurrentChange">
        <el-table-column label="模型名称" prop="modelName" min-width="150" show-overflow-tooltip />
        <el-table-column label="编码" prop="modelCode" width="130" show-overflow-tooltip />
        <el-table-column label="供应商" prop="providerName" width="120" show-overflow-tooltip />
        <el-table-column label="上游模型" prop="modelNameId" width="140" show-overflow-tooltip />
        <el-table-column label="请求地址" prop="endpoint" min-width="220" show-overflow-tooltip />
        <el-table-column label="温度" prop="temperature" width="80" align="center" />
        <el-table-column label="最大输出" prop="maxTokens" width="100" align="center" />
        <el-table-column label="默认" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault === 1" type="success" effect="dark" size="small">默认</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="0" :inactive-value="1" @change="(val) => handleStatusChange(row, val)" />
          </template>
        </el-table-column>
        <el-table-column label="最近测试" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="!row.lastTestAt" class="empty-tip">未测试</span>
            <span v-else>
              <el-tag :type="row.lastTestMsg && row.lastTestMsg.indexOf('正常') >= 0 ? 'success' : 'danger'" size="small">
                {{ row.lastTestMsg && row.lastTestMsg.indexOf('正常') >= 0 ? '正常' : '失败' }}
              </el-tag>
              <span style="margin-left: 6px; color: #909399">{{ row.lastTestAt }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Connection" @click="handleTest(row)">测试</el-button>
            <el-button link type="primary" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="warning" :icon="Star" @click="handleSetDefault(row)">默认</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-tip">暂无模型配置</div>
        </template>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="open" :title="title" width="820px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模型名称" prop="modelName">
              <el-input v-model="form.modelName" placeholder="如 通义千问-Plus" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型编码" prop="modelCode">
              <el-input v-model="form.modelCode" placeholder="唯一标识，如 qwen-plus" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商" prop="providerId">
              <el-select v-model="form.providerId" placeholder="请选择" clearable style="width: 100%" @change="onProviderChange">
                <el-option v-for="p in providers" :key="p.providerId" :label="p.providerName" :value="p.providerId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型类型" prop="modelType">
              <el-select v-model="form.modelType" style="width: 100%">
                <el-option label="对话模型" value="chat" />
                <el-option label="向量模型" value="embedding" />
                <el-option label="重排模型" value="rerank" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="请求地址" prop="endpoint">
              <el-input v-model="form.endpoint" placeholder="https://xxx/v1/chat/completions（只填根地址会自动补全）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上游模型标识" prop="modelNameId">
              <el-input v-model="form.modelNameId" placeholder="如 qwen-plus / gpt-4o" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="API Key" prop="apiKey">
              <el-input v-model="form.apiKey" type="password" show-password placeholder="留空表示不修改" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="系统提示词">
              <el-input v-model="form.systemPrompt" type="textarea" :rows="2" placeholder="可选，作为该模型的默认 system 提示词" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="温度">
              <el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" show-input :show-input-controls="false" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Top P">
              <el-slider v-model="form.topP" :min="0" :max="1" :step="0.05" show-input :show-input-controls="false" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大输出">
              <el-input-number v-model="form.maxTokens" :min="1" :max="200000" :step="256" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="超时(毫秒)">
              <el-input-number v-model="form.timeoutMs" :min="1000" :step="1000" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="支持流式">
              <el-switch v-model="form.supportStream" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="JSON输出">
              <el-switch v-model="form.supportJson" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="设为默认">
              <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
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

    <!-- 连通性测试 -->
    <el-dialog v-model="testOpen" title="连通性测试" width="620px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="测试提示">
          <el-input v-model="testPrompt" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="testResult"
        :type="testResult.success ? 'success' : 'error'"
        :closable="false"
        show-icon
        :title="testResult.success ? '连通正常' : '调用失败'"
        style="margin-bottom: 12px"
      />
      <el-descriptions v-if="testResult" :column="2" border size="small">
        <el-descriptions-item label="模型">{{ testResult.modelName }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ testResult.costMs }} ms</el-descriptions-item>
        <el-descriptions-item label="Token">{{ testResult.tokens || 0 }}</el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">{{ testResult.endpoint }}</el-descriptions-item>
        <el-descriptions-item label="返回内容" :span="2">
          <div style="white-space: pre-wrap; max-height: 220px; overflow: auto">{{ testResult.content || testResult.errorMsg }}</div>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="testOpen = false">关 闭</el-button>
        <el-button type="primary" :loading="testing" @click="doTest">开始测试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, Connection, Star } from '@element-plus/icons-vue'
import { listModels, addModel, editModel, delModel, testModel, setDefaultModel, providerOptions } from '@/api/llm'

const loading = ref(false)
const submitting = ref(false)
const testing = ref(false)
const rows = ref([])
const total = ref(0)
const open = ref(false)
const testOpen = ref(false)
const title = ref('')
const providers = ref([])
const currentRow = ref(null)
const testPrompt = ref('你好，请用一句话介绍你自己。')
const testResult = ref(null)
const testingModelId = ref(null)
const formRef = ref()

const queryParams = reactive({ pageNum: 1, pageSize: 10, modelName: '', modelType: '', status: undefined })

const defaultForm = () => ({
  modelId: undefined,
  modelName: '',
  modelCode: '',
  providerId: undefined,
  modelType: 'chat',
  endpoint: '',
  modelNameId: '',
  apiKey: '',
  systemPrompt: '',
  temperature: 0.7,
  topP: 1.0,
  maxTokens: 4096,
  timeoutMs: 120000,
  supportStream: 1,
  supportJson: 0,
  isDefault: 0,
  status: 0,
  remark: ''
})
const form = ref(defaultForm())

const rules = {
  modelName: [{ required: true, message: '模型名称不能为空', trigger: 'blur' }],
  modelCode: [{ required: true, message: '模型编码不能为空', trigger: 'blur' }],
  endpoint: [{ required: true, message: '请求地址不能为空', trigger: 'blur' }]
}

function fetchList() {
  loading.value = true
  listModels(queryParams)
    .then((res) => {
      rows.value = res.rows || []
      total.value = res.total || 0
    })
    .finally(() => {
      loading.value = false
    })
}

function fetchProviders() {
  providerOptions().then((res) => {
    providers.value = res || []
  })
}

function onProviderChange(val) {
  const p = providers.value.find((x) => x.providerId === val)
  if (p && p.baseUrl && !form.value.endpoint) {
    form.value.endpoint = p.baseUrl + '/chat/completions'
  }
}

function onCurrentChange(row) {
  currentRow.value = row
}

function handleQuery() {
  queryParams.pageNum = 1
  fetchList()
}

function resetQuery() {
  queryParams.modelName = ''
  queryParams.modelType = ''
  queryParams.status = undefined
  handleQuery()
}

function handleAdd() {
  form.value = defaultForm()
  title.value = '新增模型'
  open.value = true
}

function handleEdit(row) {
  form.value = { ...defaultForm(), ...row, apiKey: '' }
  title.value = '修改模型'
  open.value = true
}

function submitForm() {
  formRef.value.validate((valid) => {
    if (!valid) return
    submitting.value = true
    const api = form.value.modelId ? editModel(form.value) : addModel(form.value)
    api
      .then(() => {
        ElMessage.success(form.value.modelId ? '修改成功' : '新增成功')
        open.value = false
        fetchList()
      })
      .finally(() => {
        submitting.value = false
      })
  })
}

function handleStatusChange(row, val) {
  editModel({ modelId: row.modelId, status: val }).then(() => {
    ElMessage.success(val === 0 ? '已启用' : '已停用')
  })
}

function handleSetDefault(row) {
  const target = row || currentRow.value
  if (!target) {
    ElMessage.warning('请先选择一行')
    return
  }
  setDefaultModel({ modelId: target.modelId }).then(() => {
    ElMessage.success('已设为默认模型')
    fetchList()
  })
}

function handleDelete(row) {
  ElMessageBox.confirm(`确认删除模型「${row.modelName}」吗？`, '提示', { type: 'warning' }).then(() => {
    delModel(row.modelId).then(() => {
      ElMessage.success('删除成功')
      fetchList()
    })
  })
}

function handleTest(row) {
  testingModelId.value = row.modelId
  testResult.value = null
  testOpen.value = true
}

function doTest() {
  testing.value = true
  testResult.value = null
  testModel({ modelId: testingModelId.value, prompt: testPrompt.value })
    .then((res) => {
      testResult.value = res
      fetchList()
    })
    .catch((e) => {
      testResult.value = { success: false, errorMsg: e?.message || '调用失败', costMs: 0 }
    })
    .finally(() => {
      testing.value = false
    })
}

onMounted(() => {
  fetchProviders()
  fetchList()
})
</script>

<style scoped>
.search-form {
  margin-bottom: 0;
}
</style>
