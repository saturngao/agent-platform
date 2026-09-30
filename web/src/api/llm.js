import { http } from '@/utils/request'

/**
 * 大模型管理
 *   模型      /llm/model          GET 列表 / POST 新增 / PUT 修改 / DELETE /llm/model/{ids}
 *   默认模型  /llm/model/default  GET 查询 / PUT 设置  body {modelId}
 *   连通测试  /llm/model/test     POST body {modelId, prompt}
 *   供应商    /llm/provider       GET 列表
 *   调用日志  /llm/callLog        GET 列表  query {pageNum,pageSize,modelId,status,bizType}
 */

// GET /llm/model  query {pageNum,pageSize,modelName,status,providerId,modelType}
export function listModels(params) {
  return http.get('/llm/model', params)
}

// GET /llm/model/{modelId}
export function getModel(modelId) {
  return http.get(`/llm/model/${modelId}`)
}

// POST /llm/model  body LlmModel
export function addModel(data) {
  return http.post('/llm/model', data)
}

// PUT /llm/model  body {modelId, ...}
export function editModel(data) {
  return http.put('/llm/model', data)
}

// DELETE /llm/model/{ids}
export function delModel(ids) {
  return http.del(`/llm/model/${ids}`)
}

// POST /llm/model/test  body {modelId, prompt}
export function testModel(data) {
  return http.post('/llm/model/test', data)
}

// GET /llm/model/default
export function getDefaultModel() {
  return http.get('/llm/model/default')
}

// PUT /llm/model/default  body {modelId}
export function setDefaultModel(data) {
  return http.put('/llm/model/default', data)
}

// GET /llm/model/optionselect  -> [{modelId, modelCode, modelName, modelNameId, isDefault}]
export function modelOptions(params) {
  return http.get('/llm/model/optionselect', params)
}

// GET /llm/provider
export function listProviders(params) {
  return http.get('/llm/provider', params)
}

// GET /llm/provider/optionselect
export function providerOptions() {
  return http.get('/llm/provider/optionselect')
}

export function addProvider(data) {
  return http.post('/llm/provider', data)
}

export function editProvider(data) {
  return http.put('/llm/provider', data)
}

export function delProvider(ids) {
  return http.del(`/llm/provider/${ids}`)
}

// GET /llm/callLog  query {pageNum,pageSize,modelId,status,bizType}
export function listCallLogs(params) {
  return http.get('/llm/callLog', params)
}

// GET /llm/callLog/stat -> {overview:{total,failed,avgCost,totalTokens}, byBizType:[]}
export function callLogStat() {
  return http.get('/llm/callLog/stat')
}

// GET /llm/callLog/recent?limit=20
export function recentCallLogs(limit) {
  return http.get('/llm/callLog/recent', { limit })
}

// DELETE /llm/callLog/clean?days=30
export function cleanCallLogs(days) {
  return http.del('/llm/callLog/clean', { days })
}
