<template>
  <div class="page-container">
    <el-row :gutter="12" style="margin-bottom: 16px">
      <el-col :span="4" v-for="c in statCards" :key="c.label">
        <el-card shadow="never" class="page-card" style="text-align: center">
          <div style="font-size: 13px; color: #909399">{{ c.label }}</div>
          <div style="font-size: 24px; font-weight: 600; margin-top: 6px" :style="{ color: c.color }">{{ c.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="page-card">
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="任务名称">
          <el-input v-model="queryParams.taskName" placeholder="请输入" clearable style="width: 180px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="queryParams.assignee" placeholder="全部" clearable filterable style="width: 160px">
            <el-option v-for="u in users" :key="u.userId" :label="u.nickname || u.username" :value="u.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="queryParams.priority" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="p in priorityOptions" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" :icon="Plus" @click="handleAdd">新建任务</el-button>
        <el-button :icon="Refresh" @click="fetchList">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column label="任务名称" prop="taskTitle" min-width="180" show-overflow-tooltip />
        <el-table-column label="负责人" prop="assigneeName" width="110" />
        <el-table-column label="优先级" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="priorityType(row.priority)" size="small">{{ priorityLabel(row.priority) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="160">
          <template #default="{ row }">
            <el-progress :percentage="row.progress || 0" :stroke-width="14" :color="progressColor(row.progress)" text-inside />
          </template>
        </el-table-column>
        <el-table-column label="截止日期" width="130" align="center">
          <template #default="{ row }">
            <span :style="{ color: dueColor(row) }">{{ row.dueDate || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="标签" prop="tags" width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="230" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="warning" :icon="RefreshRight" @click="openProgress(row)">进度</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-tip">暂无任务</div>
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

    <el-dialog v-model="open" :title="title" width="700px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="form.taskName" placeholder="如 搭建 RBAC 权限中台" />
        </el-form-item>
        <el-form-item label="任务内容">
          <el-input v-model="form.content" type="textarea" :rows="3" placeholder="具体要求、验收标准" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="负责人">
              <el-select v-model="form.assignee" placeholder="请选择" clearable filterable style="width: 100%">
                <el-option v-for="u in users" :key="u.userId" :label="u.nickname || u.username" :value="u.userId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-select v-model="form.priority" style="width: 100%">
                <el-option v-for="p in priorityOptions" :key="p.value" :label="p.label" :value="p.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="进度">
              <el-input-number v-model="form.progress" :min="0" :max="100" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开始日期">
              <el-date-picker v-model="form.startTime" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="截止日期">
              <el-date-picker v-model="form.endTime" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="标签">
              <el-input v-model="form.tags" placeholder="多个用逗号分隔，如 后端,权限" />
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

    <el-dialog v-model="progressOpen" title="更新进度" width="420px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="状态">
          <el-select v-model="progressForm.status" style="width: 100%">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="进度">
          <el-slider v-model="progressForm.progress" :min="0" :max="100" show-input />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="progressOpen = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitProgress">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, RefreshRight } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { listTasks, addTask, editTask, delTask, taskStat, changeTaskStatus, changeTaskProgress } from '@/api/work'
import { summaryMembers } from '@/api/work'

const loading = ref(false)
const submitting = ref(false)
const rows = ref([])
const total = ref(0)
const users = ref([])
const open = ref(false)
const progressOpen = ref(false)
const title = ref('')
const formRef = ref()
const stat = ref({})

const statusOptions = [
  { label: '待开始', value: 'TODO' },
  { label: '进行中', value: 'DOING' },
  { label: '待验收', value: 'REVIEW' },
  { label: '已完成', value: 'DONE' },
  { label: '已取消', value: 'CANCELLED' }
]
const priorityOptions = [
  { label: '低', value: 'LOW' },
  { label: '普通', value: 'NORMAL' },
  { label: '高', value: 'HIGH' },
  { label: '紧急', value: 'URGENT' }
]

const queryParams = reactive({ pageNum: 1, pageSize: 10, taskName: '', assignee: undefined, status: '', priority: '' })
const defaultForm = () => ({
  taskId: undefined,
  taskName: '',
  content: '',
  assignee: undefined,
  priority: 'NORMAL',
  status: 'TODO',
  progress: 0,
  startTime: null,
  endTime: null,
  tags: '',
  remark: ''
})
const form = ref(defaultForm())
const progressForm = reactive({ taskId: undefined, status: 'DOING', progress: 0 })
const rules = { taskName: [{ required: true, message: '任务名称不能为空', trigger: 'blur' }] }

const statCards = computed(() => [
  { label: '全部任务', value: stat.value.total ?? 0, color: '#409eff' },
  { label: '待开始', value: stat.value.TODO ?? 0, color: '#909399' },
  { label: '进行中', value: stat.value.DOING ?? 0, color: '#409eff' },
  { label: '待验收', value: stat.value.REVIEW ?? 0, color: '#e6a23c' },
  { label: '已完成', value: stat.value.DONE ?? 0, color: '#67c23a' },
  { label: '已取消', value: stat.value.CANCELLED ?? 0, color: '#c0c4cc' }
])

function statusLabel(s) {
  return statusOptions.find((o) => o.value === s)?.label || s
}
function statusType(s) {
  return { TODO: 'info', DOING: 'primary', REVIEW: 'warning', DONE: 'success', CANCELLED: 'info' }[s] || 'info'
}
function priorityLabel(p) {
  return priorityOptions.find((o) => o.value === p)?.label || p
}
function priorityType(p) {
  return { LOW: 'info', NORMAL: 'primary', HIGH: 'warning', URGENT: 'danger' }[p] || 'info'
}
function progressColor(v) {
  if (v >= 100) return '#67c23a'
  if (v >= 60) return '#409eff'
  if (v >= 30) return '#e6a23c'
  return '#909399'
}
function dueColor(row) {
  if (!row.dueDate || row.status === 'DONE') return '#606266'
  const today = dayjs().format('YYYY-MM-DD')
  if (row.dueDate < today) return '#f56c6c'
  if (dayjs(row.dueDate).diff(dayjs(today), 'day') <= 2) return '#e6a23c'
  return '#606266'
}

function fetchList() {
  loading.value = true
  listTasks(queryParams)
    .then((res) => {
      rows.value = res.rows || []
      total.value = res.total || 0
    })
    .finally(() => {
      loading.value = false
    })
}
function fetchStat() {
  taskStat().then((res) => {
    stat.value = res || {}
  })
}
function fetchUsers() {
  summaryMembers().then((res) => {
    users.value = res || []
  })
}
function handleQuery() {
  queryParams.pageNum = 1
  fetchList()
}
function resetQuery() {
  queryParams.taskName = ''
  queryParams.assignee = undefined
  queryParams.status = ''
  queryParams.priority = ''
  handleQuery()
}
function handleAdd() {
  form.value = defaultForm()
  title.value = '新建任务'
  open.value = true
}
function handleEdit(row) {
  form.value = {
    ...defaultForm(),
    ...row,
    taskName: row.taskTitle,
    content: row.taskContent,
    assignee: row.assigneeId,
    startTime: row.startDate,
    endTime: row.dueDate
  }
  title.value = '修改任务'
  open.value = true
}
function submitForm() {
  formRef.value.validate((valid) => {
    if (!valid) return
    submitting.value = true
    const api = form.value.taskId ? editTask(form.value) : addTask(form.value)
    api
      .then(() => {
        ElMessage.success('操作成功')
        open.value = false
        fetchList()
        fetchStat()
      })
      .finally(() => {
        submitting.value = false
      })
  })
}
function openProgress(row) {
  progressForm.taskId = row.taskId
  progressForm.status = row.status
  progressForm.progress = row.progress || 0
  progressOpen.value = true
}
function submitProgress() {
  submitting.value = true
  Promise.all([
    changeTaskProgress({ taskId: progressForm.taskId, progress: progressForm.progress }),
    changeTaskStatus({ taskId: progressForm.taskId, status: progressForm.status })
  ])
    .then(() => {
      ElMessage.success('已更新')
      progressOpen.value = false
      fetchList()
      fetchStat()
    })
    .finally(() => {
      submitting.value = false
    })
}
function handleDelete(row) {
  ElMessageBox.confirm(`确认删除任务「${row.taskTitle}」吗？`, '提示', { type: 'warning' }).then(() => {
    delTask(row.taskId).then(() => {
      ElMessage.success('删除成功')
      fetchList()
      fetchStat()
    })
  })
}

onMounted(() => {
  fetchUsers()
  fetchList()
  fetchStat()
})
</script>
