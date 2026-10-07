import { http } from '@/utils/request'

/**
 * 知识库管理
 *   知识库  /knowledge/base   GET 列表 / {id} 详情 / POST 新增 / PUT 修改 / DELETE /{ids}
 *   文档    /knowledge/doc    GET 列表 / POST upload / reparse / chunks / embedding
 *   检索    /knowledge/search GET 单库 / POST 多库
 */

// ---------- 知识库 ----------

// GET /knowledge/base  query {pageNum,pageSize,baseName,baseCode,status}
export function listKnowledgeBases(params) {
  return http.get('/knowledge/base', params)
}

// GET /knowledge/base/{baseId}
export function getKnowledgeBase(baseId) {
  return http.get('/knowledge/base/' + baseId)
}

// GET /knowledge/base/options  下拉选项（工作流/智能体绑定用）
export function knowledgeBaseOptions() {
  return http.get('/knowledge/base/options')
}

// POST /knowledge/base
export function addKnowledgeBase(data) {
  return http.post('/knowledge/base', data)
}

// PUT /knowledge/base
export function editKnowledgeBase(data) {
  return http.put('/knowledge/base', data)
}

// DELETE /knowledge/base/{ids}
export function delKnowledgeBase(ids) {
  const idStr = Array.isArray(ids) ? ids.join(',') : ids
  return http.del('/knowledge/base/' + idStr)
}

// ---------- 文档 ----------

// GET /knowledge/doc  query {baseId}
export function listKnowledgeDocs(baseId) {
  return http.get('/knowledge/doc', { baseId })
}

// GET /knowledge/doc/supported  支持的文件类型
export function knowledgeSupportedTypes() {
  return http.get('/knowledge/doc/supported')
}

/**
 * POST /knowledge/doc/upload  上传文档（可多选）
 * @param {number} baseId 知识库ID
 * @param {File[]} files 文件列表
 */
export function uploadKnowledgeDocs(baseId, files, onProgress) {
  const form = new FormData()
  form.append('baseId', String(baseId))
  files.forEach((f) => form.append('files', f))
  return http.post('/knowledge/doc/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000,
    onUploadProgress: onProgress
  })
}

// POST /knowledge/doc/reparse/{docId}
export function reparseKnowledgeDoc(docId) {
  return http.post('/knowledge/doc/reparse/' + docId)
}

// PUT /knowledge/doc/status/{docId}?status=0|1
export function changeDocStatus(docId, status) {
  return http.put('/knowledge/doc/status/' + docId, null, { params: { status } })
}

// DELETE /knowledge/doc/{ids}
export function delKnowledgeDocs(ids) {
  const idStr = Array.isArray(ids) ? ids.join(',') : ids
  return http.del('/knowledge/doc/' + idStr)
}

// GET /knowledge/doc/chunks/{docId}
export function knowledgeDocChunks(docId) {
  return http.get('/knowledge/doc/chunks/' + docId)
}

// POST /knowledge/doc/embedding/build  query {baseId}
export function buildKnowledgeEmbedding(baseId) {
  return http.post('/knowledge/doc/embedding/build', null, { params: { baseId } })
}

// POST /knowledge/doc/embedding/clear  query {baseId}
export function clearKnowledgeEmbedding(baseId) {
  return http.post('/knowledge/doc/embedding/clear', null, { params: { baseId } })
}

// ---------- 检索 ----------

// GET /knowledge/search  query {baseId, query, topK}
export function searchKnowledge(params) {
  return http.get('/knowledge/search', params)
}

// POST /knowledge/search  body {baseIds, query, topK}
export function searchKnowledgeMulti(data) {
  return http.post('/knowledge/search', data)
}
