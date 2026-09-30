import { http } from '@/utils/request'

/**
 * 工作流管理
 *   流程定义   /workflow/def              GET 列表 / POST 新增 / PUT 修改 / DELETE /workflow/def/{ids}
 *   发布       /workflow/def/publish      PUT  body {defId}
 *   执行       /workflow/def/run          POST body {defId, params}
 *   流程实例   /workflow/instance         GET 列表
 *   实例详情   /workflow/instance/{id}    GET ；/{id}/detail 节点明细
 */

// GET /workflow/def  query {pageNum,pageSize,defName,status}
export function listDefs(params) {
  return http.get('/workflow/def', params)
}

// GET /workflow/def/{defId}  -> {defId, defName, defKey, graph:{nodes,edges}, ...}
export function getDef(defId) {
  return http.get(`/workflow/def/${defId}`)
}

// POST /workflow/def  body {defName, defKey, category, description, graph, status}
export function addDef(data) {
  return http.post('/workflow/def', data)
}

// PUT /workflow/def  body {defId, ...}
export function editDef(data) {
  return http.put('/workflow/def', data)
}

// DELETE /workflow/def/{ids}
export function delDef(ids) {
  return http.del(`/workflow/def/${ids}`)
}

// PUT /workflow/def/publish  body {defId}
export function publishDef(data) {
  return http.put('/workflow/def/publish', data)
}

// POST /workflow/def/run  body {defId, params}
export function runDef(data) {
  return http.post('/workflow/def/run', data)
}

// GET /workflow/instance  query {pageNum,pageSize,defId,status,beginTime,endTime}
export function listInstances(params) {
  return http.get('/workflow/instance', params)
}

// GET /workflow/instance/{instanceId}
export function getInstance(instanceId) {
  return http.get(`/workflow/instance/${instanceId}`)
}

// GET /workflow/instance/{instanceId}/detail  -> {instance, nodeRuns}
export function getInstanceDetail(instanceId) {
  return http.get(`/workflow/instance/${instanceId}/detail`)
}

// POST /workflow/instance/{instanceId}/retry
export function retryInstance(instanceId) {
  return http.post(`/workflow/instance/${instanceId}/retry`)
}

// PUT /workflow/instance/{instanceId}/stop
export function stopInstance(instanceId) {
  return http.put(`/workflow/instance/${instanceId}/stop`)
}

// DELETE /workflow/instance/{ids}
export function delInstance(ids) {
  return http.del(`/workflow/instance/${ids}`)
}
