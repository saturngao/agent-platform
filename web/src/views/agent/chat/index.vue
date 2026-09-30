<template>
  <div class="page-container">
    <div class="chat-wrap">
      <!-- 左侧会话列表 -->
      <el-card shadow="never" class="conv-panel">
        <div class="conv-head">
          <span class="conv-title">{{ agent.agentName || '智能体' }}</span>
          <el-tag size="small" type="info">{{ agent.modelName || '默认模型' }}</el-tag>
        </div>
        <el-button type="primary" :icon="Plus" size="small" style="width: 100%" @click="newConversation">新建会话</el-button>
        <div class="conv-list">
          <div
            v-for="c in conversations"
            :key="c.convId"
            class="conv-item"
            :class="{ active: c.convId === currentConvId }"
            @click="selectConversation(c)"
          >
            <div class="conv-item-title">{{ c.title || '未命名会话' }}</div>
            <div class="conv-item-meta">{{ c.messageCount || 0 }} 条 · {{ formatTime(c.updateTime) }}</div>
            <el-button link type="danger" size="small" class="conv-del" @click.stop="delConv(c)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
        <div v-if="conversations.length === 0" class="empty-tip" style="padding: 20px 0">暂无会话</div>
      </el-card>

      <!-- 右侧对话区 -->
      <el-card shadow="never" class="chat-panel" :body-style="{ padding: 0, height: '100%' }">
        <div class="chat-body" ref="bodyRef">
          <div v-if="messages.length === 0 && agent.prologue" class="msg-row">
            <div class="msg-avatar">{{ agent.avatar || '🤖' }}</div>
            <div class="msg-bubble assistant md-body" v-html="renderMd(agent.prologue)"></div>
          </div>
          <div v-for="m in messages" :key="m.msgId" class="msg-row" :class="m.role === 'user' ? 'me' : ''">
            <div class="msg-avatar">{{ m.role === 'user' ? '我' : agent.avatar || '🤖' }}</div>
            <div class="msg-bubble" :class="m.role === 'user' ? 'user' : 'assistant'">
              <div v-if="m.role === 'assistant'" class="md-body" v-html="renderMd(m.content)"></div>
              <div v-else class="plain-text">{{ m.content }}</div>
              <div v-if="m.status === 1" class="msg-error">调用失败：{{ m.errorMsg }}</div>
              <div v-if="m.role === 'assistant' && m.tokens" class="msg-meta">{{ m.modelName }} · {{ m.tokens }} tokens · {{ m.costMs }}ms</div>
            </div>
          </div>
          <div v-if="sending" class="msg-row">
            <div class="msg-avatar">{{ agent.avatar || '🤖' }}</div>
            <div class="msg-bubble assistant">
              <span class="typing">正在思考<span>.</span><span>.</span><span>.</span></span>
            </div>
          </div>
        </div>

        <div class="chat-input">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="3"
            resize="none"
            placeholder="输入内容，Enter 发送，Shift+Enter 换行"
            @keydown.enter.exact.prevent="send"
          />
          <div class="chat-input-foot">
            <el-button link :icon="Delete" @click="clearInput">清空</el-button>
            <el-button type="primary" :icon="Promotion" :loading="sending" :disabled="!inputText.trim()" @click="send">发送</el-button>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Promotion } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import { getAgent, listConversations, listMessages, delConversation } from '@/api/agent'
import { fetchStream } from '@/api/agent'
import { sendMessage } from '@/api/agent'

const route = useRoute()
const router = useRouter()
const agentId = Number(route.params.agentId)

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

function renderMd(text) {
  if (!text) return ''
  return md.render(text)
}

const agent = ref({})
const conversations = ref([])
const messages = ref([])
const currentConvId = ref(null)
const inputText = ref('')
const sending = ref(false)
const bodyRef = ref()

function formatTime(t) {
  if (!t) return ''
  return String(t).slice(5, 16)
}

function scrollBottom() {
  nextTick(() => {
    if (bodyRef.value) {
      bodyRef.value.scrollTop = bodyRef.value.scrollHeight
    }
  })
}

function fetchAgent() {
  getAgent(agentId).then((res) => {
    agent.value = res || {}
  })
}

function fetchConversations() {
  return listConversations({ agentId, pageNum: 1, pageSize: 50 }).then((res) => {
    conversations.value = res.rows || []
  })
}

function fetchMessages() {
  if (!currentConvId.value) {
    messages.value = []
    return
  }
  listMessages({ conversationId: currentConvId.value, pageNum: 1, pageSize: 200 }).then((res) => {
    messages.value = res.rows || []
    scrollBottom()
  })
}

function selectConversation(c) {
  currentConvId.value = c.convId
  fetchMessages()
}

function newConversation() {
  currentConvId.value = null
  messages.value = []
  inputText.value = ''
}

function delConv(c) {
  ElMessageBox.confirm('删除该会话及其全部消息？', '提示', { type: 'warning' }).then(() => {
    delConversation(c.convId).then(() => {
      ElMessage.success('已删除')
      if (currentConvId.value === c.convId) {
        newConversation()
      }
      fetchConversations()
    })
  })
}

function clearInput() {
  inputText.value = ''
}

function send() {
  const content = inputText.value.trim()
  if (!content || sending.value) return
  sending.value = true
  inputText.value = ''

  // 先把用户消息渲染出来
  messages.value.push({ msgId: 'tmp_' + Date.now(), role: 'user', content, status: 0 })
  const aiMsg = { msgId: 'ai_' + Date.now(), role: 'assistant', content: '', status: 0, modelName: '', tokens: 0, costMs: 0 }
  messages.value.push(aiMsg)
  scrollBottom()

  const payload = { agentId, conversationId: currentConvId.value, content, stream: false }
  fetchStream(
    payload,
    (chunk) => {
      aiMsg.content += chunk
      scrollBottom()
    },
    () => {
      sending.value = false
      // 新建会话走流式时拿不到 conversationId（后端不在流里返回），
      // 从刚刷新的会话列表头部取最新一条，再加载消息
      fetchConversations()
        .then(() => {
          if (!currentConvId.value && conversations.value.length) {
            currentConvId.value = conversations.value[0].convId
          }
        })
        .finally(() => {
          fetchMessages()
        })
    },
    (err) => {
      const errMsg = err?.message || '调用失败'
      // 服务端已给出明确原因（如未配置 API Key）或请求超时时，
      // 不再重复调一次同步接口（会造成二次长时间等待），直接把原因显示出来
      const noRetry = errMsg.includes('未配置 API Key') || errMsg.includes('超时') || errMsg.startsWith('HTTP ')
      if (noRetry) {
        aiMsg.status = 1
        aiMsg.errorMsg = errMsg
        sending.value = false
        scrollBottom()
        return
      }
      // 其他流式失败：退回同步接口再试一次
      sendMessage(payload)
        .then((res) => {
          if (res && res.content) {
            aiMsg.content = res.content
            aiMsg.modelName = res.modelName
            aiMsg.tokens = res.tokens
            aiMsg.costMs = res.costMs
            currentConvId.value = res.conversationId || currentConvId.value
            fetchConversations()
          } else if (res && res.success === false) {
            aiMsg.status = 1
            aiMsg.errorMsg = res.errorMsg || '调用失败'
          }
        })
        .catch((e) => {
          aiMsg.status = 1
          aiMsg.errorMsg = e?.message || '调用失败'
        })
        .finally(() => {
          sending.value = false
          scrollBottom()
        })
    }
  )
}

onMounted(() => {
  fetchAgent()
  fetchConversations()
})
</script>

<style scoped>
.chat-wrap {
  display: flex;
  gap: 12px;
  height: calc(100vh - 190px);
  min-height: 520px;
}
.conv-panel {
  width: 260px;
  flex-shrink: 0;
}
.conv-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.conv-title {
  font-weight: 600;
  color: #303133;
}
.conv-list {
  margin-top: 10px;
  max-height: calc(100% - 100px);
  overflow-y: auto;
}
.conv-item {
  position: relative;
  padding: 8px 26px 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
}
.conv-item:hover {
  background: #f5f7fa;
}
.conv-item.active {
  background: #ecf5ff;
}
.conv-item-title {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-item-meta {
  font-size: 11px;
  color: #909399;
}
.conv-del {
  position: absolute;
  right: 4px;
  top: 8px;
}
.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #fafafa;
}
.msg-row {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.msg-row.me {
  flex-direction: row-reverse;
}
.msg-avatar {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: #ecf5ff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
}
.msg-bubble {
  max-width: 72%;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}
.msg-bubble.user {
  background: #409eff;
  color: #fff;
}
.msg-bubble.assistant {
  background: #fff;
  color: #303133;
  border: 1px solid #ebeef5;
}
.plain-text {
  white-space: pre-wrap;
}
.msg-error {
  margin-top: 6px;
  color: #f56c6c;
  font-size: 12px;
}
.msg-meta {
  margin-top: 8px;
  font-size: 11px;
  color: #909399;
  border-top: 1px dashed #eee;
  padding-top: 6px;
}
.chat-input {
  border-top: 1px solid #ebeef5;
  padding: 10px 12px;
}
.chat-input-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.typing span {
  animation: blink 1.4s infinite;
}
.typing span:nth-child(2) {
  animation-delay: 0.2s;
}
.typing span:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes blink {
  0%, 20% { opacity: 0.2; }
  50% { opacity: 1; }
  100% { opacity: 0.2; }
}
.md-body :deep(pre.hljs) {
  background: #282c34;
  color: #abb2bf;
  padding: 10px 12px;
  border-radius: 6px;
  overflow: auto;
  font-size: 12px;
}
.md-body :deep(pre.hljs code) {
  background: transparent;
  color: inherit;
}
.md-body :deep(p) {
  margin: 6px 0;
}
.md-body :deep(ul),
.md-body :deep(ol) {
  padding-left: 22px;
  margin: 6px 0;
}
.md-body :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
}
.md-body :deep(th),
.md-body :deep(td) {
  border: 1px solid #dcdfe6;
  padding: 5px 10px;
}
.md-body :deep(blockquote) {
  margin: 6px 0;
  padding-left: 10px;
  border-left: 3px solid #dcdfe6;
  color: #909399;
}
.md-body :deep(code) {
  background: #f5f7fa;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
}
</style>
