<template>
  <div class="page-container">
    <el-card shadow="never" class="page-card">
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="流程名称">
          <el-input v-model="queryParams.defName" placeholder="请输入" clearable style="width: 180px" @keyup.enter="handleQuery" />
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
        <el-button type="primary" :icon="Plus" @click="handleDesign()">新建流程</el-button>
        <el-button :icon="Refresh" @click="fetchList">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column label="流程名称" prop="defName" min-width="180" show-overflow-tooltip />
        <el-table-column label="编码" prop="defCode" min-width="140" show-overflow-tooltip />
        <el-table-column label="分类" prop="category" width="130" show-overflow-tooltip />
        <el-table-column label="版本" prop="version" width="80" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="描述" prop="description" min-width="200" show-overflow-tooltip />
        <el-table-column label="创建时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" :icon="EditPen" @click="handleDesign(row)">设计</el-button>
            <el-button link type="warning" :icon="VideoPlay" @click="openRun(row)">运行</el-button>
            <el-button link type="success" :icon="Promotion" @click="handlePublish(row)">发布</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-tip">暂无流程</div>
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

    <el-dialog v-model="runOpen" :title="'运行流程 - ' + runDefName" width="640px" append-to-body>
      <el-alert type="info" :closable="false" show-icon title="入参 JSON" description="入参会写入流程上下文，节点中可用 {{变量名}} 引用。" style="margin-bottom: 12px" />
      <el-input v-model="runParams" type="textarea" :rows="6" />
      <el-alert
        v-if="runResult"
        :type="runResult.status === 'SUCCESS' ? 'success' : 'error'"
        :closable="false"
        show-icon
        :title="runResult.status === 'SUCCESS' ? '执行成功，耗时 ' + (runResult.costMs || 0) + ' ms' : '执行失败'"
        style="margin-top: 12px"
      />
      <el-input v-if="runResult" :model-value="formatOutput(runResult)" type="textarea" :rows="8" readonly style="margin-top: 8px" />
      <template #footer>
        <el-button @click="runOpen = false">关 闭</el-button>
        <el-button v-if="runResult" link type="primary" @click="goInstance">查看实例</el-button>
        <el-button type="primary" :loading="running" @click="doRun">执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Delete, EditPen, VideoPlay, Promotion } from '@element-plus/icons-vue'
import { listDefs, delDef, publishDef, runDef } from '@/api/workflow'

const router = useRouter()
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const runOpen = ref(false)
const running = ref(false)
const runParams = ref('{\n  "rawText": "把这里替换成要整理的内容"\n}')
const runResult = ref(null)
const runDefId = ref(null)
const runDefName = ref('')
const runInstanceId = ref(null)

const queryParams = reactive({ pageNum: 1, pageSize: 10, defName: '', status: undefined })

function statusLabel(s) {
  return { 0: '草稿', 1: '已发布', 2: '已停用' }[s] || '草稿'
}
function statusType(s) {
  return { 0: 'info', 1: 'success', 2: 'danger' }[s] || 'info'
}

function fetchList() {
  loading.value = true
  listDefs(queryParams)
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
  queryParams.defName = ''
  queryParams.status = undefined
  handleQuery()
}

function handleDesign(row) {
  if (row && row.defId) {
    router.push('/workflow/designer/' + row.defId)
  } else {
    router.push('/workflow/designer')
  }
}

function handlePublish(row) {
  publishDef({ defId: row.defId }).then(() => {
    ElMessage.success('发布成功')
    fetchList()
  })
}

function handleDelete(row) {
  ElMessageBox.confirm(`确认删除流程「${row.defName}」吗？`, '提示', { type: 'warning' }).then(() => {
    delDef(row.defId).then(() => {
      ElMessage.success('删除成功')
      fetchList()
    })
  })
}

function openRun(row) {
  runDefId.value = row.defId
  runDefName.value = row.defName
  runResult.value = null
  runOpen.value = true
}

function formatOutput(r) {
  if (!r) return ''
  const out = r.output
  if (out == null) return r.errorMsg || ''
  return typeof out === 'string' ? out : JSON.stringify(out, null, 2)
}

function doRun() {
  running.value = true
  runResult.value = null
  let params = {}
  try {
    params = JSON.parse(runParams.value)
  } catch (e) {
    ElMessage.error('入参不是合法 JSON')
    running.value = false
    return
  }
  runDef({ defId: runDefId.value, params })
    .then((res) => {
      runResult.value = res
      runInstanceId.value = res.instanceId
      ElMessage.success('执行完成')
    })
    .finally(() => {
      running.value = false
    })
}

function goInstance() {
  runOpen.value = false
  router.push('/workflow/instance')
}

onMounted(fetchList)
</script>
