import { http } from '@/utils/request'

/**
 * 工作管理
 *   任务    /work/task          GET 列表 / POST 新增 / PUT 修改 / DELETE /work/task/{ids}
 *   日志    /work/log           GET 列表 / POST 新增 / PUT 修改 / DELETE /work/log/{ids}
 *   总结    /work/summary       GET 列表 / POST 生成 / GET /work/summary/{id}
 *   邮件    /work/summary/mail  POST body {summaryId, receivers:[]}
 */

/* ---------------------------------- 任务 ---------------------------------- */

// GET /work/task  query {pageNum,pageSize,taskName,status,assignee,priority,beginTime,endTime}
export function listTasks(params) {
  return http.get('/work/task', params)
}

export function getTask(taskId) {
  return http.get(`/work/task/${taskId}`)
}

// POST /work/task  body {taskName, content, assignee, priority, status, startTime, endTime, deptId, progress, remark}
export function addTask(data) {
  return http.post('/work/task', data)
}

// PUT /work/task  body {taskId, ...}
export function editTask(data) {
  return http.put('/work/task', data)
}

// DELETE /work/task/{ids}
export function delTask(ids) {
  return http.del(`/work/task/${ids}`)
}

// PUT /work/task/status  body {taskId, status}
export function changeTaskStatus(data) {
  return http.put('/work/task/status', data)
}

// PUT /work/task/progress  body {taskId, progress}
export function changeTaskProgress(data) {
  return http.put('/work/task/progress', data)
}

// GET /work/task/stat -> {total, TODO, DOING, REVIEW, DONE, CANCELLED}
export function taskStat(params) {
  return http.get('/work/task/stat', params)
}

/* ---------------------------------- 日志 ---------------------------------- */

// GET /work/log  query {pageNum,pageSize,taskId,userId,workDate,keyword,beginTime,endTime}
export function listLogs(params) {
  return http.get('/work/log', params)
}

// GET /work/log/my
export function listMyLogs(params) {
  return http.get('/work/log/my', params)
}

export function getLog(logId) {
  return http.get(`/work/log/${logId}`)
}

// POST /work/log  body {taskId, workDate, hours, content, progress, logTitle, logType, result, blockers, planNext}
export function addLog(data) {
  return http.post('/work/log', data)
}

// PUT /work/log  body {logId, ...}
export function editLog(data) {
  return http.put('/work/log', data)
}

// DELETE /work/log/{ids}
export function delLog(ids) {
  return http.del(`/work/log/${ids}`)
}

// GET /work/log/stat -> {days:[{workDate, cnt}], total}
export function logStat(params) {
  return http.get('/work/log/stat', params)
}

/* --------------------------------- AI 总结 -------------------------------- */

// POST /work/summary  body {summaryType, beginDate, endDate, memberIds:[], deptId, modelId, title}
export function generateSummary(data) {
  return http.post('/work/summary', data)
}

// GET /work/summary  query {pageNum,pageSize,summaryType}
export function listSummaries(params) {
  return http.get('/work/summary', params)
}

// GET /work/summary/{summaryId}
export function getSummary(summaryId) {
  return http.get(`/work/summary/${summaryId}`)
}

// POST /work/summary/regenerate/{summaryId}
export function regenerateSummary(summaryId) {
  return http.post(`/work/summary/regenerate/${summaryId}`)
}

// DELETE /work/summary/{ids}
export function delSummary(ids) {
  return http.del(`/work/summary/${ids}`)
}

// POST /work/summary/mail  body {summaryId, receivers:[], cc:[], subject}
export function sendSummaryMail(data) {
  return http.post('/work/summary/mail', data)
}

// GET /work/summary/mail/status -> {configured: true|false}
export function mailStatus() {
  return http.get('/work/summary/mail/status')
}

// GET /work/summary/members -> [{userId, username, nickname, email, deptId}]
export function summaryMembers() {
  return http.get('/work/summary/members')
}
