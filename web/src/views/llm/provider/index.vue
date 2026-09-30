<template>
  <div class="page-container">
    <el-card shadow="never" class="page-card">
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="供应商名称">
          <el-input v-model="queryParams.providerName" placeholder="请输入" clearable style="width: 180px" @keyup.enter="handleQuery" />
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
        <el-button type="primary" :icon="Plus" @click="handleAdd">新增供应商</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column label="编码" prop="providerCode" width="130" />
        <el-table-column label="名称" prop="providerName" min-width="150" show-overflow-tooltip />
        <el-table-column label="默认地址" prop="baseUrl" min-width="260" show-overflow-tooltip />
        <el-table-column label="协议" prop="protocol" width="100" align="center" />
        <el-table-column label="Key 示例" prop="apiKeyHint" width="130" />
        <el-table-column label="排序" prop="sortNum" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">{{ row.status === 0 ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-tip">暂无数据</div>
        </template>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <el-dialog v-model="open" :title="title" width="620px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="供应商编码" prop="providerCode">
          <el-input v-model="form.providerCode" placeholder="如 deepseek / qwen" />
        </el-form-item>
        <el-form-item label="供应商名称" prop="providerName">
          <el-input v-model="form.providerName" placeholder="如 DeepSeek" />
        </el-form-item>
        <el-form-item label="默认接口地址">
          <el-input v-model="form.baseUrl" placeholder="https://api.deepseek.com" />
        </el-form-item>
        <el-form-item label="协议">
          <el-input v-model="form.protocol" placeholder="openai" />
        </el-form-item>
        <el-form-item label="Key 示例">
          <el-input v-model="form.apiKeyHint" placeholder="sk-xxx" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortNum" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { listProviders, addProvider, editProvider, delProvider } from '@/api/llm'

const loading = ref(false)
const submitting = ref(false)
const rows = ref([])
const total = ref(0)
const open = ref(false)
const title = ref('')
const formRef = ref()

const queryParams = reactive({ pageNum: 1, pageSize: 10, providerName: '', status: undefined })
const defaultForm = () => ({
  providerId: undefined,
  providerCode: '',
  providerName: '',
  baseUrl: '',
  protocol: 'openai',
  apiKeyHint: '',
  sortNum: 0,
  status: 0,
  remark: ''
})
const form = ref(defaultForm())
const rules = {
  providerCode: [{ required: true, message: '编码不能为空', trigger: 'blur' }],
  providerName: [{ required: true, message: '名称不能为空', trigger: 'blur' }]
}

function fetchList() {
  loading.value = true
  listProviders(queryParams)
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
  queryParams.providerName = ''
  queryParams.status = undefined
  handleQuery()
}
function handleAdd() {
  form.value = defaultForm()
  title.value = '新增供应商'
  open.value = true
}
function handleEdit(row) {
  form.value = { ...defaultForm(), ...row }
  title.value = '修改供应商'
  open.value = true
}
function submitForm() {
  formRef.value.validate((valid) => {
    if (!valid) return
    submitting.value = true
    const api = form.value.providerId ? editProvider(form.value) : addProvider(form.value)
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
function handleDelete(row) {
  ElMessageBox.confirm(`确认删除「${row.providerName}」吗？`, '提示', { type: 'warning' }).then(() => {
    delProvider(row.providerId).then(() => {
      ElMessage.success('删除成功')
      fetchList()
    })
  })
}

onMounted(fetchList)
</script>
