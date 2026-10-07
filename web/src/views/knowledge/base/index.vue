<template>
  <div class="knowledge-page">
    <!-- ====================== 知识库列表 ====================== -->
    <el-card shadow="never" class="search-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="名称">
          <el-input v-model="query.baseName" placeholder="知识库名称" clearable style="width: 180px" @keyup.enter="getList" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="query.baseCode" placeholder="知识库编码" clearable style="width: 180px" @keyup.enter="getList" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getList">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" icon="Plus" @click="openBase()">新建知识库</el-button>
        <el-button icon="Refresh" @click="getList">刷新</el-button>
        <span class="tip">上传 TXT / Markdown / PDF / Word / Excel / PPT / CSV 等文档，解析后即可被工作流与智能体检索引用</span>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="baseName" label="知识库名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="baseCode" label="编码" width="150" />
        <el-table-column label="文档 / 分片" width="130" align="center">
          <template #default="{ row }">
            <span>{{ row.docCount || 0 }} 篇 / {{ row.chunkCount || 0 }} 段</span>
          </template>
        </el-table-column>
        <el-table-column label="检索方式" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.vectorReady ? 'success' : 'info'">
              {{ row.vectorReady ? '向量+关键词' : '关键词' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="embedModelName" label="向量模型" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.embedModelName">{{ row.embedModelName }}</span>
            <span v-else class="text-muted">未配置</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="Number(row.status) === 0 ? 'success' : 'info'">
              {{ Number(row.status) === 0 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="230" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" icon="FolderOpened" @click="openDocs(row)">文档</el-button>
            <el-button link type="warning" icon="Edit" @click="openBase(row)">编辑</el-button>
            <el-button link type="danger" icon="Delete" @click="removeBase(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-tip">还没有知识库，点击「新建知识库」创建一个，然后上传文档</div>
        </template>
      </el-table>

      <el-pagination
        class="pager"
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @change="getList"
      />
    </el-card>

    <!-- ====================== 知识库新增/编辑 ====================== -->
    <el-dialog v-model="baseOpen" :title="baseForm.baseId ? '编辑知识库' : '新建知识库'" width="620px" append-to-body destroy-on-close>
      <el-form :model="baseForm" label-width="120px">
        <el-form-item label="知识库名称" required>
          <el-input v-model="baseForm.baseName" placeholder="如：产品手册库" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="baseForm.baseCode" :disabled="!!baseForm.baseId" placeholder="留空自动生成，创建后不可修改" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="baseForm.description" type="textarea" :rows="2" placeholder="这个库放什么内容" />
        </el-form-item>
        <el-form-item label="向量模型">
          <el-select v-model="baseForm.embedModelId" placeholder="不配置则只用关键词检索" clearable style="width: 100%">
            <el-option v-for="m in embedModels" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
          </el-select>
          <div class="form-tip">
            需要在「模型中心」新增一个 modelType = embedding 的模型。配置后检索会做向量重排，语义匹配更准；未配置或调用失败会自动降级为关键词检索。
          </div>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="分片大小">
              <el-input-number v-model="baseForm.chunkSize" :min="100" :max="2000" :step="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分片重叠">
              <el-input-number v-model="baseForm.chunkOverlap" :min="0" :max="500" :step="10" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="召回条数">
              <el-input-number v-model="baseForm.topK" :min="1" :max="20" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最低分">
              <el-input-number v-model="baseForm.scoreThreshold" :min="0" :max="1" :step="0.05" :precision="2" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态">
          <el-radio-group v-model="baseForm.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="baseOpen = false">取 消</el-button>
        <el-button type="primary" :loading="baseLoading" @click="submitBase">确 定</el-button>
      </template>
    </el-dialog>

    <!-- ====================== 文档管理抽屉 ====================== -->
    <el-drawer v-model="docOpen" :title="`文档管理 · ${currentBase.baseName || ''}`" size="72%" destroy-on-close>
      <div class="doc-layout">
        <!-- 上传区 -->
        <el-card shadow="never" class="upload-card">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            :title="`支持 ${supported.accept || '多种格式'}`"
            :description="`单个文件最大 ${supported.maxSizeMb || 50}MB；PDF 扫描件没有文本层会解析为空，需先 OCR。`"
            style="margin-bottom: 12px"
          />
          <el-upload
            ref="uploadRef"
            drag
            multiple
            :auto-upload="false"
            :accept="supported.accept"
            :show-file-list="true"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
          >
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div class="el-upload__text">把文档拖到这里，或<em>点击选择文件</em></div>
            <template #tip>
              <div class="el-upload__tip">可一次选多个文件，点击「开始上传」后逐个解析入库</div>
            </template>
          </el-upload>
          <div class="upload-actions">
            <el-button type="primary" :loading="uploading" :disabled="!fileList.length" @click="submitUpload">
              开始上传（{{ fileList.length }}）
            </el-button>
            <el-button :disabled="!fileList.length" @click="clearFiles">清空列表</el-button>
            <el-progress v-if="uploading" :percentage="uploadPercent" :text-inside="true" :stroke-width="16" style="width: 220px" />
          </div>

          <el-divider content-position="left">检索测试</el-divider>
          <div class="search-test">
            <el-input v-model="testQuery" placeholder="输入问题测试检索效果，如：报销流程是怎样的" @keyup.enter="doSearch" />
            <el-button type="success" :loading="searching" @click="doSearch">检索</el-button>
          </div>
          <div v-if="searchHits.length" class="hit-list">
            <div v-for="(h, i) in searchHits" :key="i" class="hit-item">
              <div class="hit-head">
                <span class="hit-src">{{ h.docName || '知识库' }}</span>
                <el-tag size="small" type="primary">得分 {{ Number(h.score || 0).toFixed(3) }}</el-tag>
                <el-tag v-if="h.vectorScore != null" size="small" type="info">向量 {{ Number(h.vectorScore).toFixed(2) }}</el-tag>
              </div>
              <div class="hit-body">{{ h.content }}</div>
            </div>
          </div>
          <el-empty v-else-if="searched" description="没有命中任何片段，试试换关键词或降低最低分" :image-size="60" />
        </el-card>

        <!-- 文档列表 -->
        <div class="doc-list">
          <div class="toolbar">
            <el-button icon="Refresh" size="small" @click="loadDocs">刷新</el-button>
            <el-button
              v-if="currentBase.embedModelId"
              size="small"
              type="warning"
              :loading="embedding"
              @click="doBuildEmbedding"
            >
              生成向量
            </el-button>
            <span class="tip">停用的文档不参与检索</span>
          </div>
          <el-table v-loading="docLoading" :data="docs" size="small" border>
            <el-table-column prop="docName" label="文件名" min-width="200" show-overflow-tooltip />
            <el-table-column prop="fileType" label="类型" width="70" align="center" />
            <el-table-column prop="fileSizeText" label="大小" width="90" align="center" />
            <el-table-column label="解析状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="parseTag(row.parseStatus).type">{{ parseTag(row.parseStatus).text }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="分片" width="70" align="center">
              <template #default="{ row }">{{ row.chunkCount || 0 }}</template>
            </el-table-column>
            <el-table-column label="启用" width="70" align="center">
              <template #default="{ row }">
                <el-switch
                  :model-value="Number(row.status) === 0"
                  size="small"
                  @change="(v) => toggleDoc(row, v)"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click="viewChunks(row)">分片</el-button>
                <el-button link type="warning" @click="reparse(row)">重解析</el-button>
                <el-button link type="danger" @click="removeDoc(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <div class="empty-tip">还没有文档，从左侧上传一个试试</div>
            </template>
          </el-table>
          <div v-if="docs.some((d) => Number(d.parseStatus) === 3)" class="parse-errors">
            <div v-for="d in docs.filter((d) => Number(d.parseStatus) === 3)" :key="d.docId" class="err-line">
              <b>{{ d.docName }}</b>：{{ d.parseMsg }}
            </div>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- ====================== 分片预览 ====================== -->
    <el-dialog v-model="chunkOpen" :title="`分片预览 · ${chunkDoc.docName || ''}`" width="760px" append-to-body destroy-on-close>
      <el-table :data="chunks" size="small" border max-height="480">
        <el-table-column prop="seqNo" label="#" width="50" align="center" />
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="320">
          <template #default="{ row }">
            <div class="chunk-text">{{ row.content }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="charCount" label="字符" width="70" align="center" />
      </el-table>
      <template #footer>
        <el-button @click="chunkOpen = false">关 闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  listKnowledgeBases,
  addKnowledgeBase,
  editKnowledgeBase,
  delKnowledgeBase,
  listKnowledgeDocs,
  knowledgeSupportedTypes,
  uploadKnowledgeDocs,
  reparseKnowledgeDoc,
  changeDocStatus,
  delKnowledgeDocs,
  knowledgeDocChunks,
  buildKnowledgeEmbedding,
  searchKnowledge
} from '@/api/knowledge'
import { listModels } from '@/api/llm'

/* ------------------------------ 列表 ------------------------------ */
const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, baseName: '', baseCode: '', status: undefined })

async function getList() {
  loading.value = true
  try {
    const res = await listKnowledgeBases(query)
    list.value = res?.rows || res?.list || []
    total.value = Number(res?.total || 0)
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.baseName = ''
  query.baseCode = ''
  query.status = undefined
  query.pageNum = 1
  getList()
}

/* ------------------------------ 知识库表单 ------------------------------ */
const baseOpen = ref(false)
const baseLoading = ref(false)
const embedModels = ref([])
const defaultBaseForm = () => ({
  baseId: undefined,
  baseName: '',
  baseCode: '',
  description: '',
  embedModelId: undefined,
  chunkSize: 500,
  chunkOverlap: 80,
  topK: 5,
  scoreThreshold: 0.15,
  status: 0
})
const baseForm = ref(defaultBaseForm())

async function loadEmbedModels() {
  try {
    const res = await listModels({ pageNum: 1, pageSize: 100 })
    const rows = res?.rows || res?.list || []
    embedModels.value = rows.filter((m) => m.modelType === 'embedding')
  } catch (e) {
    embedModels.value = []
  }
}

function openBase(row) {
  baseForm.value = { ...defaultBaseForm(), ...(row || {}) }
  if (row?.scoreThreshold != null) {
    baseForm.value.scoreThreshold = Number(row.scoreThreshold)
  }
  baseOpen.value = true
}

async function submitBase() {
  if (!baseForm.value.baseName?.trim()) {
    ElMessage.warning('知识库名称不能为空')
    return
  }
  baseLoading.value = true
  try {
    if (baseForm.value.baseId) {
      await editKnowledgeBase(baseForm.value)
      ElMessage.success('修改成功')
    } else {
      await addKnowledgeBase(baseForm.value)
      ElMessage.success('创建成功')
    }
    baseOpen.value = false
    getList()
  } catch (e) {
    /* 统一提示 */
  } finally {
    baseLoading.value = false
  }
}

async function removeBase(row) {
  try {
    await ElMessageBox.confirm(`删除知识库「${row.baseName}」会同时删除其中的文档与分片，确定继续？`, '提示', {
      type: 'warning'
    })
  } catch {
    return
  }
  await delKnowledgeBase(row.baseId)
  ElMessage.success('删除成功')
  getList()
}

/* ------------------------------ 文档管理 ------------------------------ */
const docOpen = ref(false)
const docLoading = ref(false)
const docs = ref([])
const currentBase = ref({})
const supported = reactive({ accept: '', ext: [], maxSizeMb: 50 })
const fileList = ref([])
const uploadRef = ref()
const uploading = ref(false)
const uploadPercent = ref(0)
const embedding = ref(false)

const testQuery = ref('')
const searching = ref(false)
const searched = ref(false)
const searchHits = ref([])

async function openDocs(row) {
  currentBase.value = row
  fileList.value = []
  searchHits.value = []
  searched.value = false
  testQuery.value = ''
  docOpen.value = true
  if (!supported.accept) {
    try {
      const s = await knowledgeSupportedTypes()
      if (s) {
        supported.accept = s.accept || ''
        supported.ext = s.ext || []
        supported.maxSizeMb = s.maxSizeMb || 50
      }
    } catch (e) {
      /* 忽略，用默认提示 */
    }
  }
  await loadDocs()
}

async function loadDocs() {
  if (!currentBase.value?.baseId) return
  docLoading.value = true
  try {
    docs.value = (await listKnowledgeDocs(currentBase.value.baseId)) || []
  } catch (e) {
    docs.value = []
  } finally {
    docLoading.value = false
  }
}

/** 把不合规的文件从组件内部列表里移除（组件内部状态才是展示源） */
function dropFile(file) {
  fileList.value = fileList.value.filter((f) => f !== file)
  try {
    uploadRef.value?.handleRemove(file)
  } catch (e) {
    /* 组件方法不可用时忽略 */
  }
}

function onFileChange(file, files) {
  fileList.value = files || []
  if (!file?.raw) return
  const name = file.name || ''
  const size = file.size || 0
  if (supported.ext?.length && !supported.ext.some((e) => name.toLowerCase().endsWith(e))) {
    ElMessage.warning(`不支持的文件类型：${name}`)
    dropFile(file)
    return
  }
  if (size > (supported.maxSizeMb || 50) * 1024 * 1024) {
    ElMessage.warning(`${name} 超过 ${supported.maxSizeMb}MB 上限`)
    dropFile(file)
  }
}

function onFileRemove(file, files) {
  fileList.value = files || []
}

function clearFiles() {
  try {
    uploadRef.value?.clearFiles()
  } catch (e) {
    /* 忽略 */
  }
  fileList.value = []
}

async function submitUpload() {
  if (!fileList.value.length) return
  uploading.value = true
  uploadPercent.value = 0
  try {
    const files = fileList.value.map((f) => f.raw || f).filter(Boolean)
    const result = await uploadKnowledgeDocs(currentBase.value.baseId, files, (e) => {
      if (e?.total) uploadPercent.value = Math.min(99, Math.round((e.loaded / e.total) * 100))
    })
    uploadPercent.value = 100
    const rows = Array.isArray(result) ? result : []
    const ok = rows.filter((r) => r.ok).length
    const fail = rows.filter((r) => !r.ok)
    if (fail.length) {
      fail.forEach((f) => ElMessage.error(`${f.name}：${f.msg}`))
    }
    ElMessage.success(`上传完成，成功 ${ok} 个${fail.length ? `，失败 ${fail.length} 个` : ''}`)
    fileList.value = []
    await loadDocs()
    getList()
  } catch (e) {
    /* 统一提示 */
  } finally {
    uploading.value = false
  }
}

function parseTag(status) {
  const s = Number(status)
  if (s === 2) return { type: 'success', text: '成功' }
  if (s === 3) return { type: 'danger', text: '失败' }
  if (s === 1) return { type: 'warning', text: '解析中' }
  return { type: 'info', text: '待解析' }
}

async function toggleDoc(row, val) {
  await changeDocStatus(row.docId, val ? 0 : 1)
  row.status = val ? 0 : 1
  ElMessage.success(val ? '已启用' : '已停用')
}

async function reparse(row) {
  try {
    const doc = await reparseKnowledgeDoc(row.docId)
    ElMessage.success(doc?.parseMsg || '已重新解析')
    await loadDocs()
    getList()
  } catch (e) {
    await loadDocs()
  }
}

async function removeDoc(row) {
  try {
    await ElMessageBox.confirm(`删除文档「${row.docName}」及其分片？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  await delKnowledgeDocs(row.docId)
  ElMessage.success('删除成功')
  await loadDocs()
  getList()
}

async function doBuildEmbedding() {
  embedding.value = true
  try {
    const res = await buildKnowledgeEmbedding(currentBase.value.baseId)
    ElMessage.success(`已生成 ${res?.count || 0} 条向量`)
  } catch (e) {
    /* 统一提示 */
  } finally {
    embedding.value = false
  }
}

async function doSearch() {
  if (!testQuery.value.trim()) {
    ElMessage.warning('请输入检索内容')
    return
  }
  searching.value = true
  searched.value = true
  try {
    searchHits.value =
      (await searchKnowledge({ baseId: currentBase.value.baseId, query: testQuery.value.trim() })) || []
  } catch (e) {
    searchHits.value = []
  } finally {
    searching.value = false
  }
}

/* ------------------------------ 分片预览 ------------------------------ */
const chunkOpen = ref(false)
const chunkDoc = ref({})
const chunks = ref([])

async function viewChunks(row) {
  chunkDoc.value = row
  chunkOpen.value = true
  chunks.value = []
  try {
    chunks.value = (await knowledgeDocChunks(row.docId)) || []
  } catch (e) {
    chunks.value = []
  }
}

onMounted(() => {
  getList()
  loadEmbedModels()
})
</script>

<style scoped>
.knowledge-page {
  padding: 16px;
}
.search-card {
  margin-bottom: 12px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.tip {
  margin-left: auto;
  font-size: 12px;
  color: #909399;
}
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
.empty-tip {
  padding: 18px 0;
  color: #909399;
  font-size: 13px;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  margin-top: 4px;
}
.text-muted {
  color: #c0c4cc;
}

/* 文档抽屉：左侧上传 + 右侧列表 */
.doc-layout {
  display: flex;
  gap: 14px;
  height: 100%;
}
.upload-card {
  width: 380px;
  flex: none;
}
.doc-list {
  flex: 1;
  min-width: 0;
}
.upload-icon {
  font-size: 48px;
  color: #c0c4cc;
}
.upload-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}
.search-test {
  display: flex;
  gap: 8px;
}
.hit-list {
  margin-top: 10px;
  max-height: 320px;
  overflow: auto;
}
.hit-item {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 8px;
}
.hit-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #606266;
  margin-bottom: 4px;
}
.hit-src {
  font-weight: 600;
}
.hit-body {
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 140px;
  overflow: auto;
  color: #303133;
}
.parse-errors {
  margin-top: 10px;
  font-size: 12px;
  color: #f56c6c;
}
.err-line {
  line-height: 1.8;
}
.chunk-text {
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 120px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
}
</style>
