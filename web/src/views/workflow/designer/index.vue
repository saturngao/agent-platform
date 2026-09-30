<template>
  <div class="page-container">
    <el-card shadow="never" class="page-card" style="margin-bottom: 12px">
      <el-form :inline="true" :model="def">
        <el-form-item label="流程名称">
          <el-input v-model="def.defName" placeholder="如 周报自动摘要流程" style="width: 220px" />
        </el-form-item>
        <el-form-item label="流程编码">
          <el-input v-model="def.defKey" placeholder="如 weekly_digest" style="width: 180px" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="def.category" placeholder="办公自动化" style="width: 160px" />
        </el-form-item>
        <el-form-item label="关联技能">
          <el-select
            v-model="def.skillIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="挂载技能，LLM 节点自动获得该能力"
            style="width: 240px"
          >
            <el-option v-for="s in skillList" :key="s.skillId" :label="s.skillName" :value="s.skillId">
              <span>{{ s.icon }} {{ s.skillName }}</span>
              <span style="float: right; color: #909399; font-size: 12px">{{ s.category }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Document" :loading="saving" @click="handleSave">保存</el-button>
          <el-button type="success" :icon="Promotion" @click="handlePublish">发布</el-button>
          <el-button type="warning" :icon="VideoPlay" @click="runOpen = true">运行</el-button>
          <el-button :icon="Back" @click="goBack">返回</el-button>
        </el-form-item>
      </el-form>
      <el-input v-model="def.description" placeholder="流程描述（可选）" style="max-width: 640px" />
    </el-card>

    <el-card shadow="never" class="page-card" :body-style="{ padding: 0 }">
      <div class="designer">
        <!-- 左侧节点面板 -->
        <aside class="node-panel">
          <div class="panel-title">节点类型</div>
          <div
            v-for="n in nodeTypes"
            :key="n.type"
            class="node-item"
            :style="{ borderLeftColor: n.color }"
            draggable="true"
            @dragstart="onDragStart($event, n)"
            @click="addNode(n)"
          >
            <span class="node-dot" :style="{ background: n.color }" />
            <div>
              <div class="node-label">{{ n.label }}</div>
              <div class="node-desc">{{ n.desc }}</div>
            </div>
          </div>
          <el-divider />
          <div class="panel-title">操作</div>
          <el-button size="small" :icon="Delete" style="width: 100%" @click="clearCanvas">清空画布</el-button>
          <div class="tip">拖拽或点击添加节点；点击节点配置属性；拖动节点端点连线。</div>
        </aside>

        <!-- 中间画布 -->
        <div class="canvas" @drop="onDrop" @dragover.prevent>
          <VueFlow v-model:nodes="nodes" v-model:edges="edges" :default-viewport="{ zoom: 0.9 }" :min-zoom="0.3" :max-zoom="2" @connect="onConnect" @node-click="onNodeClick">
            <template #node-default="props">
              <div class="wf-node" :style="{ borderColor: colorOf(props.data.nodeType) }" @click="selectNode(props.id)">
                <div class="wf-node-head" :style="{ background: colorOf(props.data.nodeType) }">
                  <span>{{ labelOf(props.data.nodeType) }}</span>
                </div>
                <div class="wf-node-body">
                  <div class="wf-node-name">{{ props.data.name || props.id }}</div>
                  <div v-if="props.data.outputVar" class="wf-node-var">→ {{ props.data.outputVar }}</div>
                </div>
              </div>
            </template>
            <Background :gap="18" pattern="dots" />
            <Controls />
          </VueFlow>
          <div v-if="nodes.length === 0" class="canvas-empty">从左侧拖入或点击节点类型开始编排</div>
        </div>

        <!-- 右侧属性面板 -->
        <aside class="prop-panel">
          <div class="panel-title">属性配置</div>
          <div v-if="!selected" class="empty-tip" style="padding: 20px 0">未选中节点</div>
          <el-form v-else label-width="100px" size="small">
            <el-form-item label="节点ID">
              <el-input :model-value="selected.id" disabled />
            </el-form-item>
            <el-form-item label="节点名称">
              <el-input v-model="selected.data.name" placeholder="节点名称" @input="updateNode" />
            </el-form-item>

            <!-- LLM / Agent 通用 -->
            <template v-if="selected.data.nodeType === 'llm' || selected.data.nodeType === 'agent'">
              <el-form-item label="使用模型">
                <el-select v-model="selected.data.modelId" placeholder="留空则用默认模型" clearable style="width: 100%" @change="updateNode">
                  <el-option v-for="m in modelList" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
                </el-select>
              </el-form-item>
              <el-form-item label="系统提示词">
                <el-input v-model="selected.data.systemPrompt" type="textarea" :rows="3" @input="updateNode" />
              </el-form-item>
              <el-form-item label="用户提示词">
                <el-input v-model="selected.data.userPrompt" type="textarea" :rows="5" placeholder="可用 {{变量名}} 引用上游输出" @input="updateNode" />
              </el-form-item>
              <el-form-item label="温度">
                <el-slider v-model="selected.data.temperature" :min="0" :max="2" :step="0.1" @input="updateNode" />
              </el-form-item>
              <el-form-item label="最大输出">
                <el-input-number v-model="selected.data.maxTokens" :min="1" :step="256" style="width: 100%" @change="updateNode" />
              </el-form-item>
            </template>

            <!-- 条件 -->
            <template v-if="selected.data.nodeType === 'condition'">
              <el-divider content-position="left">分支</el-divider>
              <div v-for="(b, i) in selected.data.branches" :key="i" class="branch-box">
                <el-form-item label="条件">
                  <el-input v-model="b.condition" placeholder="如 {{summary}} != null" @input="updateNode" />
                </el-form-item>
                <el-form-item label="走向">
                  <el-select v-model="b.target" placeholder="目标节点" clearable style="width: 100%" @change="updateNode">
                    <el-option v-for="n in nodes" :key="n.id" :label="n.data.name || n.id" :value="n.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="分支名">
                  <el-input v-model="b.label" @input="updateNode" />
                </el-form-item>
                <el-button link type="danger" size="small" @click="removeBranch(i)">删除该分支</el-button>
              </div>
              <el-button size="small" :icon="Plus" @click="addBranch">新增分支</el-button>
              <el-form-item label="默认走向" style="margin-top: 10px">
                <el-select v-model="selected.data.defaultTarget" placeholder="都不满足时" clearable style="width: 100%" @change="updateNode">
                  <el-option v-for="n in nodes" :key="n.id" :label="n.data.name || n.id" :value="n.id" />
                </el-select>
              </el-form-item>
            </template>

            <!-- HTTP -->
            <template v-if="selected.data.nodeType === 'http'">
              <el-form-item label="方法">
                <el-select v-model="selected.data.method" style="width: 100%" @change="updateNode">
                  <el-option label="GET" value="GET" />
                  <el-option label="POST" value="POST" />
                  <el-option label="PUT" value="PUT" />
                  <el-option label="DELETE" value="DELETE" />
                </el-select>
              </el-form-item>
              <el-form-item label="URL">
                <el-input v-model="selected.data.url" @input="updateNode" />
              </el-form-item>
              <el-form-item label="请求体">
                <el-input v-model="selected.data.body" type="textarea" :rows="4" @input="updateNode" />
              </el-form-item>
            </template>

            <!-- 代码 -->
            <template v-if="selected.data.nodeType === 'code'">
              <el-form-item label="变换方式">
                <el-select v-model="selected.data.transformType" style="width: 100%" @change="updateNode">
                  <el-option label="模板渲染" value="template" />
                  <el-option label="取值路径" value="jsonPath" />
                  <el-option label="数组拼接" value="join" />
                </el-select>
              </el-form-item>
              <el-form-item label="表达式">
                <el-input v-model="selected.data.expression" type="textarea" :rows="3" @input="updateNode" />
              </el-form-item>
            </template>

            <!-- 知识库 -->
            <template v-if="selected.data.nodeType === 'knowledge'">
              <el-form-item label="知识内容">
                <el-input v-model="selected.data.content" type="textarea" :rows="6" @input="updateNode" />
              </el-form-item>
            </template>

            <!-- 开始 -->
            <template v-if="selected.data.nodeType === 'start'">
              <el-form-item label="输出变量">
                <el-input v-model="selected.data.outputsText" placeholder="多个用逗号分隔，如 rawText,userId" @input="syncOutputs" />
              </el-form-item>
            </template>

            <!-- 结束 -->
            <template v-if="selected.data.nodeType === 'end'">
              <el-form-item label="输出模板">
                <el-input v-model="selected.data.outputTemplate" type="textarea" :rows="3" placeholder="如 {{summaryMd}}" @input="updateNode" />
              </el-form-item>
            </template>

            <el-form-item label="输出变量">
              <el-input v-model="selected.data.outputVar" placeholder="结果写入的变量名" @input="updateNode" />
            </el-form-item>

            <el-button type="danger" size="small" :icon="Delete" style="width: 100%" @click="removeSelected">删除该节点</el-button>
          </el-form>
        </aside>
      </div>
    </el-card>

    <!-- 运行弹窗 -->
    <el-dialog v-model="runOpen" title="运行流程" width="620px" append-to-body>
      <el-alert type="info" :closable="false" show-icon title="请输入入参（JSON 格式）" description="入参会写入流程上下文，可用 {{变量名}} 在节点中引用。" style="margin-bottom: 12px" />
      <el-input v-model="runParams" type="textarea" :rows="6" />
      <el-alert v-if="runResult" :type="runResult.status === 'SUCCESS' ? 'success' : 'error'" :closable="false" show-icon :title="runResult.status === 'SUCCESS' ? '执行成功' : '执行失败'" style="margin-top: 12px" />
      <el-input v-if="runResult" :model-value="formatOutput(runResult)" type="textarea" :rows="8" readonly style="margin-top: 8px" />
      <template #footer>
        <el-button @click="runOpen = false">关 闭</el-button>
        <el-button type="primary" :loading="running" @click="doRun">执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, Promotion, VideoPlay, Back, Delete, Plus } from '@element-plus/icons-vue'
import { VueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import { getDef, addDef, editDef, publishDef, runDef } from '@/api/workflow'
import { modelOptions } from '@/api/llm'
import { skillOptions, skillBinding } from '@/api/skill'

const route = useRoute()
const router = useRouter()

const def = reactive({
  defId: undefined,
  defName: '',
  defKey: '',
  category: '',
  description: '',
  status: 0,
  skillIds: []
})
const nodes = ref([])
const edges = ref([])
const selected = ref(null)
const modelList = ref([])
const skillList = ref([])
const saving = ref(false)
const running = ref(false)
const runOpen = ref(false)
const runParams = ref('{\n  "rawText": "把这里替换成要整理的内容"\n}')
const runResult = ref(null)

const nodeTypes = [
  { type: 'start', label: '开始', desc: '流程入口，定义入参', color: '#67c23a' },
  { type: 'llm', label: '大模型', desc: '调用大模型生成内容', color: '#409eff' },
  { type: 'condition', label: '条件分支', desc: '按条件走向不同节点', color: '#e6a23c' },
  { type: 'http', label: 'HTTP 请求', desc: '调用外部接口', color: '#909399' },
  { type: 'code', label: '数据变换', desc: '模板/取值/拼接', color: '#9b59b6' },
  { type: 'knowledge', label: '知识注入', desc: '注入静态知识片段', color: '#16a085' },
  { type: 'agent', label: '智能体', desc: '复用智能体能力', color: '#36cfc9' },
  { type: 'end', label: '结束', desc: '输出最终结果', color: '#f56c6c' }
]

const defaultData = (type) => {
  const base = { nodeType: type, name: nodeTypes.find((n) => n.type === type)?.label || type }
  if (type === 'start') return { ...base, outputs: [], outputsText: '' }
  if (type === 'llm' || type === 'agent')
    return { ...base, modelId: undefined, systemPrompt: '', userPrompt: '', temperature: 0.7, maxTokens: 1024, outputVar: 'llmOutput' }
  if (type === 'condition') return { ...base, branches: [{ condition: 'true', target: '', label: '默认' }], defaultTarget: '' }
  if (type === 'http') return { ...base, method: 'GET', url: '', body: '', outputVar: 'httpResult' }
  if (type === 'code') return { ...base, transformType: 'template', expression: '', outputVar: 'codeOutput' }
  if (type === 'knowledge') return { ...base, content: '', outputVar: 'knowledge' }
  return { ...base, outputTemplate: '' }
}

function labelOf(type) {
  return nodeTypes.find((n) => n.type === type)?.label || type
}
function colorOf(type) {
  return nodeTypes.find((n) => n.type === type)?.color || '#409eff'
}

let seq = 1
function addNode(n) {
  const id = 'node_' + Date.now() + '_' + seq++
  nodes.value.push({
    id,
    type: 'default',
    position: { x: 60 + ((seq - 1) % 4) * 210, y: 60 + Math.floor((seq - 1) / 4) * 130 },
    data: defaultData(n.type)
  })
  selected.value = nodes.value[nodes.value.length - 1]
}

function onDragStart(event, n) {
  event.dataTransfer.setData('application/vueflow', n.type)
  event.dataTransfer.effectAllowed = 'move'
}
function onDrop(event) {
  const type = event.dataTransfer.getData('application/vueflow')
  if (!type) return
  const t = nodeTypes.find((n) => n.type === type)
  if (t) addNode(t)
}

function onConnect(params) {
  edges.value.push({
    id: 'edge_' + Date.now() + '_' + seq++,
    source: params.source,
    target: params.target,
    label: ''
  })
}

function onNodeClick({ node }) {
  selectNode(node.id)
}
function selectNode(id) {
  selected.value = nodes.value.find((n) => n.id === id) || null
}
function updateNode() {
  // 受控数组已由 v-model 绑定，这里仅触发响应式刷新
  nodes.value = [...nodes.value]
}
function syncOutputs() {
  const t = selected.value?.data?.outputsText || ''
  selected.value.data.outputs = t.split(',').map((s) => s.trim()).filter(Boolean)
  updateNode()
}
function addBranch() {
  selected.value.data.branches.push({ condition: 'true', target: '', label: '分支' + (selected.value.data.branches.length + 1) })
  updateNode()
}
function removeBranch(i) {
  selected.value.data.branches.splice(i, 1)
  updateNode()
}
function removeSelected() {
  if (!selected.value) return
  const id = selected.value.id
  nodes.value = nodes.value.filter((n) => n.id !== id)
  edges.value = edges.value.filter((e) => e.source !== id && e.target !== id)
  selected.value = null
}
function clearCanvas() {
  ElMessageBox.confirm('确认清空画布上的所有节点和连线？', '提示', { type: 'warning' }).then(() => {
    nodes.value = []
    edges.value = []
    selected.value = null
  })
}

/** 画布 -> 后端 graph */
function buildGraph() {
  return {
    nodes: nodes.value.map((n) => {
      const d = { ...n.data }
      const nodeType = d.nodeType
      const name = d.name
      delete d.nodeType
      delete d.name
      delete d.outputsText
      if (nodeType === 'start' && Array.isArray(d.outputs)) {
        // outputs 保留
      }
      return { id: n.id, type: nodeType, name, position: n.position, data: d }
    }),
    edges: edges.value.map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      sourceHandle: e.sourceHandle,
      label: e.label
    }))
  }
}

/** 后端 graph -> 画布 */
function loadGraph(graph) {
  if (!graph || !Array.isArray(graph.nodes)) return
  nodes.value = graph.nodes.map((n) => ({
    id: n.id,
    type: 'default',
    position: n.position || { x: 60, y: 60 },
    data: {
      ...defaultData(n.type),
      ...(n.data || {}),
      nodeType: n.type,
      name: n.name,
      outputsText: Array.isArray(n.data?.outputs) ? n.data.outputs.join(',') : ''
    }
  }))
  edges.value = (graph.edges || []).map((e) => ({
    id: e.id,
    source: e.source,
    target: e.target,
    sourceHandle: e.sourceHandle,
    label: e.label
  }))
  seq = nodes.value.length + 1
}

function handleSave() {
  if (!def.defName) {
    ElMessage.warning('请填写流程名称')
    return
  }
  if (!def.defKey) {
    ElMessage.warning('请填写流程编码')
    return
  }
  saving.value = true
  const payload = {
    defId: def.defId,
    defName: def.defName,
    defKey: def.defKey,
    category: def.category,
    description: def.description,
    graph: buildGraph(),
    status: def.status,
    skillIds: def.skillIds || []
  }
  const api = def.defId ? editDef(payload) : addDef(payload)
  api
    .then((res) => {
      ElMessage.success('保存成功')
      if (!def.defId) {
        def.defId = res?.defId || res?.data?.defId
        router.replace('/workflow/designer/' + def.defId)
      }
    })
    .finally(() => {
      saving.value = false
    })
}

function handlePublish() {
  if (!def.defId) {
    ElMessage.warning('请先保存流程')
    return
  }
  publishDef({ defId: def.defId }).then(() => {
    ElMessage.success('发布成功')
  })
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
  runDef({ defId: def.defId, params })
    .then((res) => {
      runResult.value = res
      ElMessage.success('执行完成，耗时 ' + (res.costMs || 0) + ' ms')
    })
    .finally(() => {
      running.value = false
    })
}

function goBack() {
  router.push('/workflow/list')
}

onMounted(() => {
  modelOptions().then((res) => {
    modelList.value = res || []
  })
  // 技能选项：挂载后 LLM 节点会自动获得技能能力
  skillOptions()
    .then((res) => {
      skillList.value = Array.isArray(res) ? res : []
    })
    .catch(() => {
      skillList.value = []
    })
  const id = route.params.defId
  if (id) {
    def.defId = Number(id)
    getDef(def.defId).then((res) => {
      Object.assign(def, {
        defName: res.defName,
        defKey: res.defCode || res.defKey,
        category: res.category,
        description: res.description,
        status: res.status,
        skillIds: res.skillIds || []
      })
      loadGraph(res.graph)
    })
    // 以绑定接口为准再拉一次，避免详情接口版本差异导致回显缺失
    skillBinding({ bizType: 'WORKFLOW', bizId: def.defId })
      .then((res) => {
        if (Array.isArray(res)) def.skillIds = res
      })
      .catch(() => {})
  } else {
    // 新建时预置开始/结束节点
    addNode({ type: 'start' })
    addNode({ type: 'end' })
    selected.value = null
  }
})
</script>

<style scoped>
.designer {
  display: flex;
  height: calc(100vh - 330px);
  min-height: 460px;
}
.node-panel {
  width: 200px;
  flex-shrink: 0;
  border-right: 1px solid #ebeef5;
  padding: 12px;
  overflow-y: auto;
  background: #fafafa;
}
.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
.node-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  margin-bottom: 6px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-left: 3px solid #409eff;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
}
.node-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}
.node-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.node-label {
  font-size: 13px;
  color: #303133;
}
.node-desc {
  font-size: 11px;
  color: #909399;
}
.tip {
  margin-top: 10px;
  font-size: 11px;
  color: #909399;
  line-height: 1.6;
}
.canvas {
  flex: 1;
  position: relative;
}
.canvas-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 14px;
  pointer-events: none;
}
.prop-panel {
  width: 340px;
  flex-shrink: 0;
  border-left: 1px solid #ebeef5;
  padding: 12px;
  overflow-y: auto;
  background: #fafafa;
}
.branch-box {
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  padding: 8px;
  margin-bottom: 8px;
  background: #fff;
}
.wf-node {
  width: 170px;
  border: 1px solid #409eff;
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  font-size: 12px;
}
.wf-node-head {
  padding: 4px 8px;
  color: #fff;
  font-size: 11px;
}
.wf-node-body {
  padding: 8px;
}
.wf-node-name {
  color: #303133;
  font-weight: 500;
}
.wf-node-var {
  margin-top: 4px;
  color: #909399;
  font-size: 11px;
}
</style>
