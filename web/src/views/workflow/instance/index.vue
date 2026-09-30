<template>
  <div class="page-container">
    <el-card shadow="never" class="page-card">
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="流程">
          <el-select v-model="queryParams.defId" placeholder="全部" clearable filterable style="width: 200px">
            <el-option v-for="d in defs" :key="d.defId" :label="d.defName" :value="d.defId" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="运行中" value="RUNNING" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
            <el-option label="已停止" value="STOPPED" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button :icon="Refresh" @click="fetchList">刷新</el-button>
        <el-button type="danger" :icon="Delete" :disabled="!multiple" @click="handleDelete()">删除</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border @selection-change="onSelectionChange">
        <el-table-column type="selection" width="45" align="center" />
        <el-table-column label="实例ID" prop="instanceId" width="90" align="center" />
        <el-table-column label="流程名称" prop="defName" min-width="170" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="触发方式" prop="triggerType" width="100" align="center">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ triggerLabel(row.triggerType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100" align="center">
          <template #default="{ row }">{{ row.costMs }} ms</template>
        </el-table-column>
        <el-table-column label="Token" prop="tokenTotal" width="90" align="center" />
        <el-table-column label="操作人" prop="username" width="120" show-overflow-tooltip />
        <el-table-column label="开始时间" prop="startTime" width="170" />
        <el-table-column label="操作" width="230" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="openDetail(row)">详情</el-button>
            <el-button link type="warning" :icon="RefreshRight" @click="handleRetry(row)">重跑</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-tip">暂无运行记录</div>
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

    <el-drawer v-model="detailOpen" title="运行详情" size="52%">
      <el-descriptions v-if="detail.instance" :column="2" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="流程">{{ detail.instance.defName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(detail.instance.status)" size="small">{{ statusLabel(detail.instance.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.instance.costMs }} ms</el-descriptions-item>
        <el-descriptions-item label="Token">{{ detail.instance.tokenTotal }}</el-descriptions-item>
        <el-descriptions-item label="入参" :span="2">
          <pre class="pre-block">{{ pretty(detail.instance.inputData) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.instance.errorMsg" label="错误" :span="2">
          <span style="color: #f56c6c">{{ detail.instance.errorMsg }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="输出" :span="2">
          <pre class="pre-block">{{ pretty(detail.instance.outputData) }}</pre>
        </el-descriptions-item>
      </el-descriptions>

      <div class="panel-title" style="margin-bottom: 10px">节点执行轨迹</div>
      <el-timeline>
        <el-timeline-item
          v-for="n in detail.nodeRuns"
          :key="n.runId"
          :type="timelineType(n.status)"
          :hollow="n.status === 'SKIPPED'"
          :timestamp="n.startTime"
          placement="top"
        >
          <div class="tl-card">
            <div class="tl-head">
              <span class="tl-name">{{ n.nodeName || n.nodeId }}</span>
              <el-tag size="small" :type="statusType(n.status)">{{ n.nodeType }}</el-tag>
              <span class="tl-cost">{{ n.costMs }} ms</span>
            </div>
            <div v-if="n.errorMsg" class="tl-error">错误：{{ n.errorMsg }}</div>
            <el-collapse v-if="n.inputData || n.outputData">
              <el-collapse-item title="查看入参/出参" name="io">
                <div class="tl-io">
                  <div><b>入参</b><pre class="pre-block">{{ pretty(n.inputData) }}</pre></div>
                  <div><b>出参</b><pre class="pre-block">{{ pretty(n.outputData) }}</pre></div>
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>
        </el-timeline-item>
      </el-timeline>
      <div v-if="!detail.nodeRuns || detail.nodeRuns.length === 0" class="empty-tip">暂无节点记录</div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete, View, RefreshRight } from '@element-plus/icons-vue'
import { listInstances, getInstanceDetail, retryInstance, delInstance } from '@/api/workflow'
import { listDefs } from '@/api/workflow'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const defs = ref([])
const dateRange = ref([])
const detailOpen = ref(false)
const detail = ref({ instance: null, nodeRuns: [] })
const selectedIds = ref([])
const multiple = ref(false)

const queryParams = reactive({ pageNum: 1, pageSize: 10, defId: undefined, status: '' })

function statusLabel(s) {
  return { RUNNING: '运行中', SUCCESS: '成功', FAILED: '失败', STOPPED: '已停止' }[s] || s
}
function statusType(s) {
  return { RUNNING: 'primary', SUCCESS: 'success', FAILED: 'danger', STOPPED: 'info' }[s] || 'info'
}
function triggerLabel(t) {
  return { MANUAL: '手动', API: '接口', SCHEDULE: '定时' }[t] || t
}
function timelineType(s) {
  return { SUCCESS: 'success', FAILED: 'danger', RUNNING: 'primary', SKIPPED: 'info' }[s] || 'info'
}
function pretty(v) {
  if (v == null) return '-'
  if (typeof v === 'string') {
    try {
      return JSON.stringify(JSON.parse(v), null, 2)
    } catch (e) {
      return v
    }
  }
  return JSON.stringify(v, null, 2)
}
function onSelectionChange(sel) {
  selectedIds.value = sel.map((s) => s.instanceId)
  multiple.value = sel.length > 0
}

function fetchList() {
  loading.value = true
  const params = { ...queryParams }
  if (dateRange.value && dateRange.value.length === 2) {
    params.beginTime = dateRange.value[0]
    params.endTime = dateRange.value[1]
  }
  listInstances(params)
    .then((res) => {
      rows.value = res.rows || []
      total.value = res.total || 0
    })
    .finally(() => {
      loading.value = false
    })
}

function fetchDefs() {
  listDefs({ pageNum: 1, pageSize: -1 }).then((res) => {
    defs.value = res.rows || []
  })
}

function handleQuery() {
  queryParams.pageNum = 1
  fetchList()
}
function resetQuery() {
  queryParams.defId = undefined
  queryParams.status = ''
  dateRange.value = []
  handleQuery()
}

function openDetail(row) {
  detail.value = { instance: null, nodeRuns: [] }
  detailOpen.value = true
  getInstanceDetail(row.instanceId).then((res) => {
    detail.value = res || {}
  })
}

function handleRetry(row) {
  if (row.status === 'RUNNING') {
    ElMessage.warning('运行中的实例不能重跑')
    return
  }
  retryInstance(row.instanceId).then(() => {
    ElMessage.success('已重新执行')
    fetchList()
  })
}

function handleDelete(row) {
  const ids = row ? row.instanceId : selectedIds.value.join(',')
  if (!ids) return
  ElMessageBox.confirm('确认删除选中的运行记录吗？', '提示', { type: 'warning' }).then(() => {
    delInstance(ids).then(() => {
      ElMessage.success('删除成功')
      fetchList()
    })
  })
}

onMounted(() => {
  fetchDefs()
  fetchList()
})
</script>

<style scoped>
.pre-block {
  margin: 0;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  max-height: 220px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.tl-card {
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px;
}
.tl-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tl-name {
  font-weight: 600;
  color: #303133;
}
.tl-cost {
  color: #909399;
  font-size: 12px;
}
.tl-error {
  margin-top: 6px;
  color: #f56c6c;
  font-size: 12px;
}
.tl-io {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
