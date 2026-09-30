<template>
  <div class="page-container">
    <el-card shadow="never" class="page-card">
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="关联任务">
          <el-select v-model="queryParams.taskId" placeholder="全部" clearable filterable style="width: 200px">
            <el-option v-for="t in tasks" :key="t.taskId" :label="t.taskTitle" :value="t.taskId" />
          </el-select>
        </el-form-item>
        <el-form-item label="记录人">
          <el-select v-model="queryParams.userId" placeholder="全部" clearable filterable style="width: 150px">
            <el-option v-for="u in users" :key="u.userId" :label="u.nickname || u.username" :value="u.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="工作日期">
          <el-date-picker v-model="queryParams.workDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 160px" />
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="queryParams.keyword" placeholder="正文/标题" clearable style="width: 160px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" :icon="Plus" @click="handleAdd">写工作日志</el-button>
        <el-button :icon="Calendar" @click="addToday">写今天的日志</el-button>
        <el-button :icon="Refresh" @click="fetchList">刷新</el-button>
      </div>

      <el-row :gutter="16">
        <el-col :span="18">
          <el-table v-loading="loading" :data="rows" border>
            <el-table-column label="日期" prop="workDate" width="110" align="center" />
            <el-table-column label="记录人" prop="displayName" width="100" />
            <el-table-column label="关联任务" prop="taskTitle" min-width="150" show-overflow-tooltip />
            <el-table-column label="标题" prop="logTitle" min-width="140" show-overflow-tooltip />
            <el-table-column label="内容" prop="content" min-width="240" show-overflow-tooltip />
            <el-table-column label="工时" prop="hoursSpent" width="80" align="center" />
            <el-table-column label="进度" width="80" align="center">
              <template #default="{ row }">{{ row.progressSnap }}%</template>
            </el-table-column>
            <el-table-column label="类型" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ typeLabel(row.logType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" :icon="View" @click="viewDetail(row)">查看</el-button>
                <el-button link type="primary" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
                <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <div class="empty-tip">暂无工作日志</div>
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
        </el-col>

        <el-col :span="6">
          <el-card shadow="never">
            <div class="panel-title">最近 7 天日志量</div>
            <div ref="chartRef" style="height: 260px"></div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <!-- 新增/编辑 -->
    <el-dialog v-model="open" :title="title" width="700px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工作日期" prop="workDate">
              <el-date-picker v-model="form.workDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联任务">
              <el-select v-model="form.taskId" placeholder="可选" clearable filterable style="width: 100%">
                <el-option v-for="t in tasks" :key="t.taskId" :label="t.taskTitle" :value="t.taskId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="标题">
              <el-input v-model="form.logTitle" placeholder="一句话概括当天工作" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="工作内容" prop="content">
              <el-input v-model="form.content" type="textarea" :rows="5" placeholder="做了什么、产出是什么" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="投入工时">
              <el-input-number v-model="form.hours" :min="0" :max="24" :step="0.5" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="进度">
              <el-input-number v-model="form.progress" :min="0" :max="100" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="类型">
              <el-select v-model="form.logType" style="width: 100%">
                <el-option label="日报" value="DAILY" />
                <el-option label="周报" value="WEEKLY" />
                <el-option label="会议" value="MEETING" />
                <el-option label="其他" value="OTHER" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="成果">
              <el-input v-model="form.result" placeholder="可交付物、链接等" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="阻塞与风险">
              <el-input v-model="form.blockers" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="下一步计划">
              <el-input v-model="form.planNext" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取 消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailOpen" title="日志详情" size="42%">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="日期">{{ current.workDate }}</el-descriptions-item>
        <el-descriptions-item label="记录人">{{ current.displayName || current.username }}</el-descriptions-item>
        <el-descriptions-item label="关联任务">{{ current.taskTitle || '-' }}</el-descriptions-item>
        <el-descriptions-item label="投入工时">{{ current.hoursSpent }} h</el-descriptions-item>
        <el-descriptions-item label="进度">{{ current.progressSnap }}%</el-descriptions-item>
        <el-descriptions-item label="类型">{{ typeLabel(current.logType) }}</el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ current.logTitle || '-' }}</el-descriptions-item>
        <el-descriptions-item label="工作内容" :span="2">
          <div style="white-space: pre-wrap">{{ current.content }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="成果" :span="2">{{ current.result || '-' }}</el-descriptions-item>
        <el-descriptions-item label="阻塞与风险" :span="2">{{ current.blockers || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下一步计划" :span="2">{{ current.planNext || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, View, Calendar } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import * as echarts from 'echarts'
import { listLogs, addLog, editLog, delLog, logStat } from '@/api/work'
import { listTasks } from '@/api/work'
import { summaryMembers } from '@/api/work'

const loading = ref(false)
const submitting = ref(false)
const rows = ref([])
const total = ref(0)
const tasks = ref([])
const users = ref([])
const dateRange = ref([])
const open = ref(false)
const detailOpen = ref(false)
const title = ref('')
const formRef = ref()
const chartRef = ref()
const current = ref({})

const queryParams = reactive({ pageNum: 1, pageSize: 10, taskId: undefined, userId: undefined, workDate: '', keyword: '' })
const defaultForm = () => ({
  logId: undefined,
  workDate: dayjs().format('YYYY-MM-DD'),
  taskId: undefined,
  logTitle: '',
  content: '',
  hours: 8,
  progress: 0,
  logType: 'DAILY',
  result: '',
  blockers: '',
  planNext: ''
})
const form = ref(defaultForm())
const rules = {
  workDate: [{ required: true, message: '请选择工作日期', trigger: 'change' }],
  content: [{ required: true, message: '工作内容不能为空', trigger: 'blur' }]
}

function typeLabel(t) {
  return { DAILY: '日报', WEEKLY: '周报', MEETING: '会议', OTHER: '其他' }[t] || t
}

function fetchList() {
  loading.value = true
  const params = { ...queryParams }
  if (dateRange.value && dateRange.value.length === 2) {
    params.beginTime = dateRange.value[0]
    params.endTime = dateRange.value[1]
  }
  listLogs(params)
    .then((res) => {
      rows.value = res.rows || []
      total.value = res.total || 0
    })
    .finally(() => {
      loading.value = false
    })
}

function fetchTasks() {
  listTasks({ pageNum: 1, pageSize: -1 }).then((res) => {
    tasks.value = res.rows || []
  })
}
function fetchUsers() {
  summaryMembers().then((res) => {
    users.value = res || []
  })
}
function renderChart() {
  logStat().then((res) => {
    const days = res.days || []
    nextTick(() => {
      if (!chartRef.value) return
      const chart = echarts.init(chartRef.value)
      chart.setOption({
        grid: { left: 40, right: 16, top: 20, bottom: 30 },
        tooltip: { trigger: 'axis' },
        xAxis: {
          type: 'category',
          data: days.map((d) => String(d.workDate).slice(5)),
          axisLabel: { fontSize: 11 }
        },
        yAxis: { type: 'value', minInterval: 1 },
        series: [
          {
            type: 'bar',
            data: days.map((d) => d.cnt),
            itemStyle: { color: '#409eff', borderRadius: [4, 4, 0, 0] },
            barWidth: '55%'
          }
        ]
      })
      window.addEventListener('resize', () => chart.resize())
    })
  })
}

function handleQuery() {
  queryParams.pageNum = 1
  fetchList()
}
function resetQuery() {
  queryParams.taskId = undefined
  queryParams.userId = undefined
  queryParams.workDate = ''
  queryParams.keyword = ''
  dateRange.value = []
  handleQuery()
}
function handleAdd() {
  form.value = defaultForm()
  title.value = '新增工作日志'
  open.value = true
}
function addToday() {
  form.value = defaultForm()
  title.value = '新增工作日志（今天）'
  open.value = true
}
function handleEdit(row) {
  form.value = {
    ...defaultForm(),
    ...row,
    hours: row.hoursSpent,
    progress: row.progressSnap
  }
  title.value = '修改工作日志'
  open.value = true
}
function submitForm() {
  formRef.value.validate((valid) => {
    if (!valid) return
    submitting.value = true
    const api = form.value.logId ? editLog(form.value) : addLog(form.value)
    api
      .then(() => {
        ElMessage.success('保存成功')
        open.value = false
        fetchList()
        renderChart()
      })
      .finally(() => {
        submitting.value = false
      })
  })
}
function viewDetail(row) {
  current.value = row
  detailOpen.value = true
}
function handleDelete(row) {
  ElMessageBox.confirm('确认删除该条工作日志吗？', '提示', { type: 'warning' }).then(() => {
    delLog(row.logId).then(() => {
      ElMessage.success('删除成功')
      fetchList()
      renderChart()
    })
  })
}

onMounted(() => {
  fetchTasks()
  fetchUsers()
  fetchList()
  renderChart()
})
</script>

<style scoped>
.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
</style>
