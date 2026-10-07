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
          <el-button
            v-if="selectedEdge"
            size="small"
            type="danger"
            :icon="Delete"
            style="width: 100%; margin: 6px 0 0"
            @click="removeSelectedEdge"
          >
            删除选中的连线
          </el-button>
          <el-divider />
          <div class="panel-title">连线方法</div>
          <div class="tip">1. 鼠标移到节点<b>右侧的圆点</b>，指针变成十字后按住左键；</div>
          <div class="tip">2. 拖到目标节点<b>左侧的圆点</b>上松手，两个节点即建立关联；</div>
          <div class="tip">3. 单击连线可选中，按 <b>Delete / Backspace</b> 或点左侧按钮删除；</div>
          <div class="tip">4. 条件节点每个分支各有一个出口，从哪个点拖出就走哪个分支，右侧属性面板的「走向」会自动同步。</div>
          <el-divider />
          <div class="tip">拖拽或点击添加节点；点击节点配置属性。</div>
          <div class="tip">画布左下角按钮：放大 / 缩小 / 适应画布（鼠标悬停有文字提示；按住 Ctrl + 滚轮可缩放，空格拖拽平移）。</div>
        </aside>

        <!-- 中间画布 -->
        <div class="canvas" @drop="onDrop" @dragover.prevent>
          <VueFlow
            v-model:nodes="nodes"
            v-model:edges="edges"
            :default-viewport="{ zoom: 0.9 }"
            :min-zoom="0.3"
            :max-zoom="2"
            :delete-key-code="['Delete', 'Backspace']"
            :connection-line-style="{ stroke: '#409eff', strokeWidth: 2 }"
            @connect="onConnect"
            @node-click="onNodeClick"
            @edge-click="onEdgeClick"
            @pane-click="selectedEdge = null"
          >
            <template #node-default="props">
              <div
                class="wf-node"
                :class="{ 'is-selected': selected && selected.id === props.id }"
                :style="{ borderColor: colorOf(props.data.nodeType) }"
                @click="selectNode(props.id)"
              >
                <!-- 入口端点：除「开始」外每个节点都能被连进来 -->
                <Handle
                  v-if="props.data.nodeType !== 'start'"
                  type="target"
                  :position="Position.Left"
                  class="wf-handle"
                />

                <div class="wf-node-head" :style="{ background: colorOf(props.data.nodeType) }">
                  <span>{{ labelOf(props.data.nodeType) }}</span>
                </div>
                <div class="wf-node-body">
                  <div class="wf-node-name">{{ props.data.name || props.id }}</div>
                  <div v-if="props.data.outputVar" class="wf-node-var">→ {{ props.data.outputVar }}</div>
                </div>

                <!-- 条件节点：每个分支一个出口端点，拖出去的线就是这个分支的走向 -->
                <div v-if="props.data.nodeType === 'condition'" class="wf-branches">
                  <div v-for="(b, i) in props.data.branches" :key="i" class="wf-branch-row">
                    <span class="wf-branch-text">{{ b.label || b.condition || '分支' + (i + 1) }}</span>
                    <Handle
                      :id="'branch_' + i"
                      type="source"
                      :position="Position.Right"
                      class="wf-handle wf-handle-branch"
                    />
                  </div>
                  <div class="wf-branch-row is-default">
                    <span class="wf-branch-text">默认</span>
                    <Handle
                      id="branch_default"
                      type="source"
                      :position="Position.Right"
                      class="wf-handle wf-handle-branch"
                    />
                  </div>
                </div>

                <!-- 出口端点：结束节点不再往下连 -->
                <Handle
                  v-if="props.data.nodeType !== 'end' && props.data.nodeType !== 'condition'"
                  id="out"
                  type="source"
                  :position="Position.Right"
                  class="wf-handle"
                />
              </div>
            </template>
            <Background :gap="18" pattern="dots" />
            <Controls position="bottom-left" :show-interactive="false">
              <ControlButton title="放大" @click="zoomIn({ duration: 200 })">
                <el-icon :size="16"><ZoomIn /></el-icon>
              </ControlButton>
              <ControlButton title="缩小" @click="zoomOut({ duration: 200 })">
                <el-icon :size="16"><ZoomOut /></el-icon>
              </ControlButton>
              <ControlButton title="适应画布（一键查看全部节点）" @click="fitView({ padding: 0.2, duration: 300 })">
                <el-icon :size="16"><FullScreen /></el-icon>
              </ControlButton>
            </Controls>
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
                  <el-select
                    v-model="b.target"
                    placeholder="也可直接从画布上拖线"
                    clearable
                    style="width: 100%"
                    @change="onBranchTargetChange(i)"
                  >
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
                <el-select
                  v-model="selected.data.defaultTarget"
                  placeholder="都不满足时"
                  clearable
                  style="width: 100%"
                  @change="onDefaultTargetChange"
                >
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

            <!-- 知识库：优先按检索词从用户上传的文档中召回片段 -->
            <template v-if="selected.data.nodeType === 'knowledge'">
              <el-form-item label="知识库">
                <el-select
                  v-model="selected.data.baseIds"
                  multiple
                  filterable
                  placeholder="选择知识库，可多选"
                  style="width: 100%"
                  @change="updateNode"
                >
                  <el-option v-for="k in knowledgeList" :key="k.baseId" :label="k.baseName" :value="k.baseId">
                    <span>{{ k.baseName }}</span>
                    <span style="float: right; color: #909399; font-size: 12px">
                      {{ k.docCount || 0 }} 篇 / {{ k.chunkCount || 0 }} 段
                    </span>
                  </el-option>
                </el-select>
                <div class="form-tip">文档在「知识库 → 知识库管理」上传，解析后的分片即可被这里检索</div>
              </el-form-item>
              <el-form-item label="检索词">
                <el-input
                  v-model="selected.data.query"
                  type="textarea"
                  :rows="2"
                  placeholder="支持 {{变量名}}，如 {{rawText}}；留空默认用 {{rawText}}"
                  @input="updateNode"
                />
              </el-form-item>
              <el-form-item label="召回条数">
                <el-input-number v-model="selected.data.topK" :min="1" :max="20" @change="updateNode" />
              </el-form-item>
              <el-form-item label="兜底知识">
                <el-input
                  v-model="selected.data.content"
                  type="textarea"
                  :rows="4"
                  placeholder="检索无结果时使用这段静态知识（可留空）"
                  @input="updateNode"
                />
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
import { Document, Promotion, VideoPlay, Back, Delete, Plus, ZoomIn, ZoomOut, FullScreen } from '@element-plus/icons-vue'
import { VueFlow, useVueFlow, Handle, Position } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls, ControlButton } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import { getDef, addDef, editDef, publishDef, runDef } from '@/api/workflow'
import { modelOptions } from '@/api/llm'
import { skillOptions, skillBinding } from '@/api/skill'
import { knowledgeBaseOptions } from '@/api/knowledge'

const route = useRoute()
const router = useRouter()

// 画布缩放控制（供左下角 Controls 按钮使用）
const { zoomIn, zoomOut, fitView } = useVueFlow()

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
const selectedEdge = ref(null)
const modelList = ref([])
const skillList = ref([])
const knowledgeList = ref([])
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
  { type: 'knowledge', label: '知识注入', desc: '从知识库检索文档片段', color: '#16a085' },
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
  if (type === 'knowledge')
    return { ...base, baseIds: [], query: '', topK: 3, content: '', outputVar: 'knowledge' }
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

/**
 * 连线建立：从一个节点的出口端点拖到另一个节点的入口端点时触发
 * 条件节点从某个分支端点拖出，会把该分支的「走向」直接写进 node.data，省得再去面板里选
 */
function onConnect(params) {
  if (!params?.source || !params?.target) return
  if (params.source === params.target) {
    ElMessage.warning('不能把节点连到自己')
    return
  }
  const sourceNode = nodes.value.find((n) => n.id === params.source)
  if (!sourceNode || !nodes.value.some((n) => n.id === params.target)) return

  const handle = params.sourceHandle || null
  const dup = edges.value.some(
    (e) => e.source === params.source && e.target === params.target && (e.sourceHandle || null) === handle
  )
  if (dup) {
    ElMessage.warning('这两个节点之间已经有同样的连线了')
    return
  }
  // 一个出口只保留一条连线：改连另一个节点时，旧的自动替换
  edges.value = edges.value.filter((e) => !(e.source === params.source && (e.sourceHandle || null) === handle))
  edges.value.push({
    id: 'edge_' + Date.now() + '_' + seq++,
    source: params.source,
    target: params.target,
    sourceHandle: handle,
    targetHandle: params.targetHandle || null,
    label: handleLabel(params.source, handle)
  })
  applyHandleTarget(params.source, handle, params.target)
  updateNode()
}

/** 分支端点 -> 写回 condition 节点的 target / defaultTarget */
function applyHandleTarget(sourceId, handle, targetId) {
  if (!handle || !handle.startsWith('branch_')) return
  const node = nodes.value.find((n) => n.id === sourceId)
  if (!node || node.data.nodeType !== 'condition') return
  if (handle === 'branch_default') {
    node.data.defaultTarget = targetId
    return
  }
  const idx = Number(handle.slice('branch_'.length))
  if (node.data.branches && node.data.branches[idx]) {
    node.data.branches[idx].target = targetId
  }
}

/** 端点对应的显示名：条件分支显示分支名，其余留空 */
function handleLabel(sourceId, handle) {
  if (!handle || !handle.startsWith('branch_')) return ''
  if (handle === 'branch_default') return '默认'
  const node = nodes.value.find((n) => n.id === sourceId)
  const idx = Number(handle.slice('branch_'.length))
  return node?.data?.branches?.[idx]?.label || '分支' + (idx + 1)
}

/** 面板里改了某个分支的走向：同步画布上的连线 */
function onBranchTargetChange(i) {
  if (!selected.value) return
  syncBranchEdge(selected.value.id, 'branch_' + i, selected.value.data.branches[i].target, handleLabel(selected.value.id, 'branch_' + i))
  updateNode()
}
function onDefaultTargetChange() {
  if (!selected.value) return
  syncBranchEdge(selected.value.id, 'branch_default', selected.value.data.defaultTarget, '默认')
  updateNode()
}
function syncBranchEdge(nodeId, handle, targetId, label) {
  edges.value = edges.value.filter((e) => !(e.source === nodeId && (e.sourceHandle || null) === handle))
  if (targetId) {
    edges.value.push({
      id: 'edge_' + Date.now() + '_' + seq++,
      source: nodeId,
      target: targetId,
      sourceHandle: handle,
      label
    })
  }
}

function onEdgeClick({ edge }) {
  selectedEdge.value = edge?.id || null
}
function removeSelectedEdge() {
  if (!selectedEdge.value) return
  const id = selectedEdge.value
  const edge = edges.value.find((e) => e.id === id)
  // 同步清掉条件节点里指向该目标的分支走向
  if (edge && edge.sourceHandle && edge.sourceHandle.startsWith('branch_')) {
    const node = nodes.value.find((n) => n.id === edge.source)
    if (node?.data?.nodeType === 'condition') {
      if (edge.sourceHandle === 'branch_default') {
        node.data.defaultTarget = ''
      } else {
        const idx = Number(edge.sourceHandle.slice('branch_'.length))
        if (node.data.branches && node.data.branches[idx]) node.data.branches[idx].target = ''
      }
    }
  }
  edges.value = edges.value.filter((e) => e.id !== id)
  selectedEdge.value = null
  updateNode()
}

function onNodeClick({ node }) {
  selectNode(node.id)
  selectedEdge.value = null
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
  // 清掉其它条件节点里指向已删节点的走向，避免留悬空引用
  nodes.value.forEach((n) => {
    if (n.data?.nodeType !== 'condition') return
    let dirty = false
    ;(n.data.branches || []).forEach((b) => {
      if (b.target === id) {
        b.target = ''
        dirty = true
      }
    })
    if (n.data.defaultTarget === id) {
      n.data.defaultTarget = ''
      dirty = true
    }
    if (dirty) updateNode()
  })
  selected.value = null
  selectedEdge.value = null
}
function clearCanvas() {
  ElMessageBox.confirm('确认清空画布上的所有节点和连线？', '提示', { type: 'warning' }).then(() => {
    nodes.value = []
    edges.value = []
    selected.value = null
    selectedEdge.value = null
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
      targetHandle: e.targetHandle,
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
    targetHandle: e.targetHandle,
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
  // 知识库选项：知识注入节点按库检索
  knowledgeBaseOptions()
    .then((res) => {
      knowledgeList.value = Array.isArray(res) ? res : []
    })
    .catch(() => {
      knowledgeList.value = []
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
/* 隐藏 Vue Flow 默认的 3 个无提示按钮，只保留自定义的放大/缩小/适应画布（带 title 提示） */
:deep(.vue-flow__controls-zoomin),
:deep(.vue-flow__controls-zoomout),
:deep(.vue-flow__controls-fitview) {
  display: none;
}
/* 自定义按钮内的 Element 图标颜色与默认样式一致 */
:deep(.vue-flow__controls-button .el-icon) {
  display: block;
  color: inherit;
}
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
.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: #909399;
}
</style>
