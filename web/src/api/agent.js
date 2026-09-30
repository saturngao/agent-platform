import { http } from '@/utils/request'

/**
 * 智能体管理
 *   智能体     /agent                  GET 列表 / POST 新增 / PUT 修改 / DELETE /agent/{ids}
 *   状态/发布  /agent/status/{id}      PUT ?status=1   ;  /agent/publish/{id}  PUT
 *   会话       /agent/conversation     GET 列表  query {agentId}   DELETE /agent/conversation/{ids}
 *   消息       /agent/message          GET 列表  query {conversationId}
 *   发送消息   /agent/message/send     POST body {agentId, conversationId, content, stream}
 *   流式发送   /agent/message/sendStream  SSE
 */

// GET /agent  query {pageNum,pageSize,agentName,status}
export function listAgents(params) {
  return http.get('/agent', params)
}

// GET /agent/{agentId}
export function getAgent(agentId) {
  return http.get(`/agent/${agentId}`)
}

// POST /agent  body {agentName, agentKey, modelId, systemPrompt, prologue, tools:[], category, avatar, status, remark}
export function addAgent(data) {
  return http.post('/agent', data)
}

// PUT /agent  body {agentId, ...}
export function editAgent(data) {
  return http.put('/agent', data)
}

// DELETE /agent/{ids}
export function delAgent(ids) {
  return http.del(`/agent/${ids}`)
}

// PUT /agent/status/{agentId}?status=1
export function changeAgentStatus(agentId, status) {
  return http.put(`/agent/status/${agentId}`, null, { params: { status } })
}

// PUT /agent/publish/{agentId}
export function publishAgent(agentId) {
  return http.put(`/agent/publish/${agentId}`)
}

// GET /agent/conversation  query {agentId, pageNum, pageSize}
export function listConversations(params) {
  return http.get('/agent/conversation', params)
}

// DELETE /agent/conversation/{ids}
export function delConversation(ids) {
  return http.del(`/agent/conversation/${ids}`)
}

// GET /agent/message  query {conversationId, pageNum, pageSize}
export function listMessages(params) {
  return http.get('/agent/message', params)
}

// POST /agent/message/send  body {agentId, conversationId, content, stream}
export function sendMessage(data) {
  return http.post('/agent/message/send', data)
}

/**
 * 流式对话：直接返回原始 EventSource，不走 axios 拦截器
 * 注意：token 需自行拼接
 */
export function sendMessageStream(data, onChunk, onDone, onError) {
  const token = localStorage.getItem('Admin-Token') || ''
  const url = `/api/agent/message/sendStream?token=${encodeURIComponent(token)}`
  const source = new EventSource(url, { withCredentials: false })
  // EventSource 只能发 GET，这里退化为：先用 POST 建流不可行，
  // 因此流式能力通过服务端返回的 SSE 端点配合 fetch 实现（见下方 fetchStream）
  source.close()
  return fetchStream(data, onChunk, onDone, onError)
}

/**
 * 用 fetch 读取 SSE 文本流（兼容 POST）
 *
 * 健壮性要点：
 * 1. 带超时兜底 —— 服务端异常时 SSE 连接可能既不返回数据也不关闭，
 *    没有超时的话页面会永远卡在"正在思考"。
 * 2. 识别服务端发来的 [ERROR] 标记，转成 onError 回调，
 *    这样后端把失败原因（如未配置 API Key）写在流里也能被前端感知。
 * 3. settled 保证 onDone / onError 只触发一次。
 */
export function fetchStream(data, onChunk, onDone, onError, timeoutMs = 120000) {
  const token = localStorage.getItem('Admin-Token') || ''
  const controller = new AbortController()
  let settled = false

  const settle = (fn, arg) => {
    if (settled) return
    settled = true
    clearTimeout(timer)
    if (fn) fn(arg)
  }
  const timer = setTimeout(() => {
    controller.abort()
    settle(onError, new Error('模型响应超时（' + Math.round(timeoutMs / 1000) + 's），请检查模型配置或稍后重试'))
  }, timeoutMs)

  fetch('/api/agent/message/sendStream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: 'Bearer ' + token
    },
    body: JSON.stringify(data),
    signal: controller.signal
  })
    .then((res) => {
      if (!res.ok) throw new Error('HTTP ' + res.status)
      const reader = res.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      const read = () => {
        reader.read().then(({ done, value }) => {
          if (done) {
            settle(onDone)
            return
          }
          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop()
          for (const line of lines) {
            if (!line.startsWith('data:')) continue
            const payload = line.slice(5).trim()
            // 收到结束标记就立即收尾：SSE 连接可能是 keep-alive 不会马上关闭，
            // 若一直等 reader done，页面会永远停在"正在思考"（内容其实已经出完了）
            if (payload === '[DONE]') {
              settle(onDone)
              try {
                controller.abort()
              } catch (e) {
                /* ignore */
              }
              return
            }
            if (!payload) continue
            // 服务端把错误写进流里：以 [ERROR] 开头
            if (payload.startsWith('[ERROR]')) {
              settle(onError, new Error(payload.slice(7).trim() || '流式调用失败'))
              try { controller.abort() } catch (e) { /* ignore */ }
              return
            }
            if (onChunk) onChunk(payload)
          }
          read()
        }).catch((e) => settle(onError, e))
      }
      read()
    })
    .catch((e) => {
      if (e && e.name === 'AbortError') return // 已由超时逻辑处理
      settle(onError, e)
    })
}
