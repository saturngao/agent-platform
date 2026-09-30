<template>
  <div class="page-container">
    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <el-card shadow="never" class="page-card">
          <div style="text-align: center">
            <div style="font-size: 13px; color: #909399">{{ card.label }}</div>
            <div style="font-size: 24px; font-weight: 600; margin-top: 6px" :style="{ color: card.color }">{{ card.value }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="page-card">
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="业务类型">
          <el-select v-model="queryParams.bizType" placeholder="全部" clearable style="width: 140px">
            <el-option label="工作流" value="workflow" />
            <el-option label="智能体" value="agent" />
            <el-option label="工作总结" value="summary" />
            <el-option label="连通测试" value="test" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="成功" :value="0" />
            <el-option label="失败" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          <el-button type="danger" :icon="Delete" @click="handleClean">清理日志</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column label="ID" prop="callId" width="80" align="center" />
        <el-table-column label="业务类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="bizTagType(row.bizType)" size="small">{{ bizLabel(row.bizType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="模型" prop="modelName" min-width="140" show-overflow-tooltip />
        <el-table-column label="关联ID" prop="bizId" width="120" show-overflow-tooltip />
        <el-table-column label="Token" width="140" align="center">
          <template #default="{ row }">
            <span v-if="row.totalTokens">{{ row.promptTokens || 0 }} / {{ row.completionTokens || 0 }} = {{ row.totalTokens }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100" align="center">
          <template #default="{ row }">
            <span :style="{ color: row.costMs > 10000 ? '#f56c6c' : '#606266' }">{{ row.costMs }} ms</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">{{ row.status === 0 ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="错误信息" prop="errorMsg" min-width="180" show-overflow-tooltip />
        <el-table-column label="调用时间" prop="callTime" width="170" />
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
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete } from '@element-plus/icons-vue'
import { listCallLogs, callLogStat, cleanCallLogs } from '@/api/llm'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const stat = ref({ overview: {}, byBizType: [] })
const queryParams = reactive({ pageNum: 1, pageSize: 10, bizType: '', status: undefined })

const cards = computed(() => {
  const o = stat.value.overview || {}
  return [
    { label: '总调用次数', value: o.total ?? 0, color: '#409eff' },
    { label: '失败次数', value: o.failed ?? 0, color: '#f56c6c' },
    { label: '平均耗时(ms)', value: o.avgCost ?? 0, color: '#67c23a' },
    { label: '累计 Token', value: o.totalTokens ?? 0, color: '#e6a23c' }
  ]
})

function bizLabel(v) {
  return { workflow: '工作流', agent: '智能体', summary: '工作总结', test: '连通测试' }[v] || v || '-'
}
function bizTagType(v) {
  return { workflow: 'primary', agent: 'success', summary: 'warning', test: 'info' }[v] || 'info'
}

function fetchList() {
  loading.value = true
  listCallLogs(queryParams)
    .then((res) => {
      rows.value = res.rows || []
      total.value = res.total || 0
    })
    .finally(() => {
      loading.value = false
    })
}

function fetchStat() {
  callLogStat().then((res) => {
    stat.value = res || { overview: {}, byBizType: [] }
  })
}

function handleQuery() {
  queryParams.pageNum = 1
  fetchList()
}
function resetQuery() {
  queryParams.bizType = ''
  queryParams.status = undefined
  handleQuery()
}
function handleClean() {
  ElMessageBox.prompt('清理多少天之前的调用日志？', '清理日志', {
    inputValue: '30',
    inputPattern: /^[1-9]\d*$/,
    inputErrorMessage: '请输入正整数',
    type: 'warning'
  }).then(({ value }) => {
    cleanCallLogs(value).then(() => {
      ElMessage.success('清理完成')
      fetchList()
      fetchStat()
    })
  })
}

onMounted(() => {
  fetchList()
  fetchStat()
})
</script>
