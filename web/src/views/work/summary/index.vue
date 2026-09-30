<template>
  <div class="page-container">
    <!-- 生成总结 -->
    <el-card shadow="never" class="page-card" style="margin-bottom: 16px">
      <template #header>
        <div class="card-header">
          <span>生成 AI 工作总结</span>
          <el-tag v-if="mailConfigured" type="success" size="small">邮件已配置</el-tag>
          <el-tag v-else type="warning" size="small">邮件未配置</el-tag>
        </div>
      </template>

      <el-alert
        v-if="!mailConfigured"
        type="warning"
        :closable="false"
        show-icon
        title="邮件服务未配置"
        description="如需把总结邮件发送给他人，请到【系统管理 → 参数配置】设置 sys.mail.host / sys.mail.port / sys.mail.username / sys.mail.password"
        style="margin-bottom: 14px"
      />

      <el-form :model="genForm" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="总结标题">
              <el-input v-model="genForm.title" placeholder="留空自动生成" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="统计维度">
              <el-radio-group v-model="genForm.summaryType">
                <el-radio-button value="USER">本人</el-radio-button>
                <el-radio-button value="DEPT">本部门</el-radio-button>
                <el-radio-button value="TEAM">自选成员</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="14">
            <el-form-item label="时间范围">
              <el-date-picker
                v-model="genForm.range"
                type="daterange"
                value-format="YYYY-MM-DD"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                style="width: 100%"
                :shortcuts="shortcuts"
              />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="使用模型">
              <el-select v-model="genForm.modelId" placeholder="留空使用默认模型" clearable style="width: 100%">
                <el-option v-for="m in modelList" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24" v-if="genForm.summaryType === 'TEAM'">
            <el-form-item label="选择成员">
              <el-select v-model="genForm.memberIds" multiple filterable placeholder="可多选" style="width: 100%">
                <el-option v-for="u in members" :key="u.userId" :label="(u.nickname || u.username) + (u.email ? ' <' + u.email + '>' : '')" :value="u.userId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24" v-if="genForm.summaryType === 'DEPT'">
            <el-form-item label="选择部门">
              <el-tree-select
                v-model="genForm.deptId"
                :data="deptTree"
                :props="{ label: 'label', children: 'children' }"
                value-key="id"
                check-strictly
                placeholder="留空则使用当前用户所在部门"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <div style="text-align: right">
          <el-button type="primary" :icon="MagicStick" :loading="generating" @click="doGenerate">
            {{ generating ? '正在整理，请稍候…' : '生成总结' }}
          </el-button>
        </div>
      </el-form>

      <el-alert
        v-if="generating"
        type="info"
        :closable="false"
        show-icon
        title="正在调用大模型整理工作日志"
        description="这一步需要几十秒，请耐心等待，不要重复点击。"
        style="margin-top: 12px"
      />
    </el-card>

    <!-- 历史列表 -->
    <el-card shadow="never" class="page-card">
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="统计维度">
          <el-select v-model="queryParams.summaryType" placeholder="全部" clearable style="width: 130px">
            <el-option label="本人" value="USER" />
            <el-option label="本部门" value="DEPT" />
            <el-option label="自选成员" value="TEAM" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" @click="fetchList">刷新</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column label="标题" prop="summaryTitle" min-width="200" show-overflow-tooltip />
        <el-table-column label="维度" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ scopeLabel(row.scopeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间范围" width="190" align="center">
          <template #default="{ row }">{{ row.beginDate }} ~ {{ row.endDate }}</template>
        </el-table-column>
        <el-table-column label="日志数" prop="logCount" width="90" align="center" />
        <el-table-column label="模型" prop="modelName" width="140" show-overflow-tooltip />
        <el-table-column label="耗时" width="100" align="center">
          <template #default="{ row }">{{ row.costMs }} ms</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="邮件" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.mailSent === 1" type="success" size="small">已发送</el-tag>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="生成时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="290" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="viewDetail(row)">查看</el-button>
            <el-button link type="warning" :icon="RefreshRight" @click="handleRegen(row)">重新生成</el-button>
            <el-button link type="success" :icon="Message" @click="openMail(row)">发邮件</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-tip">暂无总结记录</div>
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

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailOpen" :title="current.summaryTitle || '总结详情'" size="60%">
      <el-descriptions :column="3" border size="small" style="margin-bottom: 14px">
        <el-descriptions-item label="统计范围">{{ current.beginDate }} ~ {{ current.endDate }}</el-descriptions-item>
        <el-descriptions-item label="维度">{{ scopeLabel(current.scopeType) }}</el-descriptions-item>
        <el-descriptions-item label="纳入日志">{{ current.logCount }} 条</el-descriptions-item>
        <el-descriptions-item label="使用模型">{{ current.modelName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Token">{{ current.tokens || 0 }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ current.costMs || 0 }} ms</el-descriptions-item>
      </el-descriptions>

      <div class="md-body" v-html="renderMd(current.contentMd || '')"></div>

      <el-collapse v-if="current.sourceData" style="margin-top: 16px">
        <el-collapse-item title="查看参与统计的原始数据" name="src">
          <pre class="pre-block">{{ prettySource(current.sourceData) }}</pre>
        </el-collapse-item>
      </el-collapse>

      <template #footer>
        <div style="display: flex; gap: 8px">
          <el-button :icon="DocumentCopy" @click="copyMd">复制 Markdown</el-button>
          <el-button type="primary" :icon="Message" @click="openMail(current)">发送邮件</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 发送邮件 -->
    <el-dialog v-model="mailOpen" title="发送总结邮件" width="620px" append-to-body>
      <el-form :model="mailForm" label-width="90px">
        <el-form-item label="邮件主题">
          <el-input v-model="mailForm.subject" />
        </el-form-item>
        <el-form-item label="收件人">
          <el-select v-model="mailForm.receivers" multiple filterable allow-create default-first-option placeholder="可从成员中选择，也可直接输入邮箱后回车" style="width: 100%">
            <el-option v-for="u in members" :key="u.userId" :label="u.email" :value="u.email" :disabled="!u.email">
              <span>{{ u.nickname || u.username }}</span>
              <span style="float: right; color: #909399; font-size: 12px">{{ u.email || '无邮箱' }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="抄送">
          <el-select v-model="mailForm.cc" multiple filterable allow-create default-first-option placeholder="可选" style="width: 100%">
            <el-option v-for="u in members" :key="u.userId" :label="u.email" :value="u.email" :disabled="!u.email" />
          </el-select>
        </el-form-item>
        <el-form-item label="预览">
          <div class="mail-preview md-body" v-html="renderMd(current.contentMd || '')"></div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mailOpen = false">取 消</el-button>
        <el-button type="primary" :loading="mailSending" @click="doSendMail">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete, View, RefreshRight, Message, MagicStick, DocumentCopy } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import dayjs from 'dayjs'
import {
  generateSummary,
  listSummaries,
  getSummary,
  regenerateSummary,
  delSummary,
  sendSummaryMail,
  mailStatus,
  summaryMembers
} from '@/api/work'
import { modelOptions } from '@/api/llm'
import { deptTree as fetchDeptTree } from '@/api/system'

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
function renderMd(t) {
  return t ? md.render(t) : '<span style="color:#c0c4cc">暂无内容</span>'
}
function prettySource(v) {
  if (!v) return '-'
  if (typeof v === 'string') {
    try {
      return JSON.stringify(JSON.parse(v), null, 2)
    } catch (e) {
      return v
    }
  }
  return JSON.stringify(v, null, 2)
}

const loading = ref(false)
const generating = ref(false)
const mailSending = ref(false)
const rows = ref([])
const total = ref(0)
const modelList = ref([])
const members = ref([])
const deptTree = ref([])
const mailConfigured = ref(false)
const detailOpen = ref(false)
const mailOpen = ref(false)
const current = ref({})

const genForm = reactive({
  title: '',
  summaryType: 'USER',
  range: [dayjs().subtract(6, 'day').format('YYYY-MM-DD'), dayjs().format('YYYY-MM-DD')],
  modelId: undefined,
  memberIds: [],
  deptId: undefined
})
const mailForm = reactive({ subject: '', receivers: [], cc: [] })
const queryParams = reactive({ pageNum: 1, pageSize: 10, summaryType: '' })

const shortcuts = [
  {
    text: '最近一周',
    value: () => [dayjs().subtract(6, 'day').toDate(), dayjs().toDate()]
  },
  {
    text: '最近一个月',
    value: () => [dayjs().subtract(30, 'day').toDate(), dayjs().toDate()]
  },
  {
    text: '本月',
    value: () => [dayjs().startOf('month').toDate(), dayjs().toDate()]
  }
]

function scopeLabel(s) {
  return { USER: '本人', DEPT: '本部门', TEAM: '自选成员' }[s] || s
}
function statusLabel(s) {
  return { GENERATING: '生成中', SUCCESS: '成功', FAILED: '失败' }[s] || s
}
function statusType(s) {
  return { GENERATING: 'primary', SUCCESS: 'success', FAILED: 'danger' }[s] || 'info'
}

function fetchList() {
  loading.value = true
  listSummaries(queryParams)
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

function doGenerate() {
  if (!genForm.range || genForm.range.length !== 2) {
    ElMessage.warning('请选择统计时间范围')
    return
  }
  generating.value = true
  generateSummary({
    title: genForm.title,
    summaryType: genForm.summaryType,
    beginDate: genForm.range[0],
    endDate: genForm.range[1],
    memberIds: genForm.summaryType === 'TEAM' ? genForm.memberIds : [],
    deptId: genForm.summaryType === 'DEPT' ? genForm.deptId : undefined,
    modelId: genForm.modelId
  })
    .then((res) => {
      ElMessage.success('总结已生成')
      current.value = res || {}
      detailOpen.value = true
      fetchList()
    })
    .finally(() => {
      generating.value = false
    })
}

function viewDetail(row) {
  getSummary(row.summaryId).then((res) => {
    current.value = res || row
    detailOpen.value = true
  })
}

function handleRegen(row) {
  ElMessageBox.confirm('将使用相同条件重新生成该总结，原内容会被覆盖。继续？', '提示', { type: 'warning' }).then(() => {
    generating.value = true
    regenerateSummary(row.summaryId)
      .then((res) => {
        ElMessage.success('已重新生成')
        current.value = res || {}
        detailOpen.value = true
        fetchList()
      })
      .finally(() => {
        generating.value = false
      })
  })
}

function handleDelete(row) {
  ElMessageBox.confirm(`确认删除总结「${row.summaryTitle}」吗？`, '提示', { type: 'warning' }).then(() => {
    delSummary(row.summaryId).then(() => {
      ElMessage.success('删除成功')
      fetchList()
    })
  })
}

function openMail(row) {
  current.value = row
  mailForm.subject = row.summaryTitle || 'AI 工作总结'
  mailForm.receivers = []
  mailForm.cc = []
  mailOpen.value = true
}

function doSendMail() {
  if (!mailForm.receivers || mailForm.receivers.length === 0) {
    ElMessage.warning('请至少填写一个收件人')
    return
  }
  mailSending.value = true
  sendSummaryMail({
    summaryId: current.value.summaryId,
    receivers: mailForm.receivers,
    cc: mailForm.cc,
    subject: mailForm.subject
  })
    .then(() => {
      ElMessage.success('邮件已发送')
      mailOpen.value = false
      fetchList()
    })
    .finally(() => {
      mailSending.value = false
    })
}

function copyMd() {
  const text = current.value.contentMd || ''
  navigator.clipboard
    .writeText(text)
    .then(() => ElMessage.success('已复制到剪贴板'))
    .catch(() => ElMessage.error('复制失败，请手动选择文本'))
}

onMounted(() => {
  modelOptions().then((res) => {
    modelList.value = res || []
  })
  summaryMembers().then((res) => {
    members.value = (res || []).filter((u) => u.email)
  })
  fetchDeptTree().then((res) => {
    deptTree.value = res || []
  })
  mailStatus().then((res) => {
    mailConfigured.value = !!res?.configured
  })
  fetchList()
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}
.pre-block {
  margin: 0;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
}
.mail-preview {
  max-height: 260px;
  overflow: auto;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
}
.md-body :deep(h1) {
  font-size: 20px;
  margin: 12px 0 8px;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 6px;
}
.md-body :deep(h2) {
  font-size: 17px;
  margin: 14px 0 8px;
  color: #409eff;
}
.md-body :deep(h3) {
  font-size: 15px;
  margin: 10px 0 6px;
}
.md-body :deep(p) {
  margin: 6px 0;
  line-height: 1.8;
}
.md-body :deep(ul),
.md-body :deep(ol) {
  padding-left: 24px;
  margin: 6px 0;
}
.md-body :deep(li) {
  margin: 3px 0;
}
.md-body :deep(strong) {
  color: #303133;
}
.md-body :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
}
.md-body :deep(th),
.md-body :deep(td) {
  border: 1px solid #dcdfe6;
  padding: 6px 12px;
}
.md-body :deep(blockquote) {
  margin: 8px 0;
  padding: 6px 12px;
  border-left: 3px solid #409eff;
  background: #f5f7fa;
  color: #606266;
}
.md-body :deep(pre.hljs) {
  background: #282c34;
  color: #abb2bf;
  padding: 10px 12px;
  border-radius: 6px;
  overflow: auto;
}
</style>
