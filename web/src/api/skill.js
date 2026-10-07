import { http } from '@/utils/request'

// GET /skill  分页列表，入参 {pageNum,pageSize,skillName,category,source,status}
export function listSkills(params) {
  return http.get('/skill', params)
}

// GET /skill/{skillId}
export function getSkill(skillId) {
  return http.get('/skill/' + skillId)
}

// POST /skill  新增
export function addSkill(data) {
  return http.post('/skill', data)
}

// PUT /skill  修改
export function editSkill(data) {
  return http.put('/skill', data)
}

// DELETE /skill/{ids}  ids 逗号分隔
export function delSkill(ids) {
  return http.del('/skill/' + ids)
}

// PUT /skill/status  body {skillId, status}
export function changeSkillStatus(data) {
  return http.put('/skill/status', data)
}

// GET /skill/optionselect  下拉选项
export function skillOptions() {
  return http.get('/skill/optionselect')
}

// GET /skill/categories  分类选项
export function skillCategories() {
  return http.get('/skill/categories')
}

// POST /skill/import  导入技能（粘贴 SKILL.md 内容）
// body { content, skillName, skillCode, summary, category, icon, source, author, version }
export function importSkill(data) {
  return http.post('/skill/import', data)
}

// ============ 技能目录（文件化：SKILL.md + scripts/*.py） ============

// GET /skill/root  技能文件根目录
export function skillRoot() {
  return http.get('/skill/root')
}

// GET /skill/{skillId}/files  技能目录下的文件树
export function skillFiles(skillId) {
  return http.get('/skill/' + skillId + '/files')
}

// GET /skill/{skillId}/file?path=xxx  读取文件内容
export function readSkillFile(skillId, path) {
  return http.get('/skill/' + skillId + '/file', { path })
}

// PUT /skill/{skillId}/file  body {path, content}  保存文件
export function writeSkillFile(skillId, data) {
  return http.put('/skill/' + skillId + '/file', data)
}

// DELETE /skill/{skillId}/file?path=xxx  删除文件
export function deleteSkillFile(skillId, path) {
  return http.del('/skill/' + skillId + '/file', { path })
}

// POST /skill/{skillId}/upload  往技能目录追加文件（支持文件夹）
// formData: files[] + paths(JSON 字符串)
export function uploadSkillFiles(skillId, formData) {
  return http.post('/skill/' + skillId + '/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

// POST /skill/{skillId}/reload  重新扫描技能目录（刷新 SKILL.md 与脚本）
export function reloadSkill(skillId) {
  return http.post('/skill/' + skillId + '/reload')
}

// GET /skill/{skillId}/export  导出技能目录为 ZIP
export function exportSkill(skillId) {
  return http.get('/skill/' + skillId + '/export', {}, { responseType: 'blob' })
}

// POST /skill/import/folder  上传整个技能文件夹
// formData: files[] + paths(JSON 字符串) + skillCode + overwrite
export function importSkillFolder(formData) {
  return http.post('/skill/import/folder', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

// POST /skill/import/zip  上传 ZIP 技能包
export function importSkillZip(formData) {
  return http.post('/skill/import/zip', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

// POST /skill/import/preview  ZIP 导入预览
export function previewSkillZip(formData) {
  return http.post('/skill/import/preview', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

// GET /skill/binding?bizType=AGENT|WORKFLOW&bizId=1  查询已挂载的技能ID
export function skillBinding(params) {
  return http.get('/skill/binding', params)
}

// POST /skill/binding  重建绑定 body {bizType, bizId, skillIds:[]}
export function bindSkills(data) {
  return http.post('/skill/binding', data)
}

// ============ 技能脚本（Python 可执行脚本） ============
// 接口挂在 /skill-script 下，避免与 /skill/{skillId} 路径参数冲突

// GET /skill-script?skillId=1  某技能下的脚本列表
export function listSkillScripts(skillId) {
  return http.get('/skill-script', { skillId })
}

// GET /skill-script/{scriptId}
export function getSkillScript(scriptId) {
  return http.get('/skill-script/' + scriptId)
}

// POST /skill-script  新增脚本
export function addSkillScript(data) {
  return http.post('/skill-script', data)
}

// PUT /skill-script  修改脚本
export function editSkillScript(data) {
  return http.put('/skill-script', data)
}

// DELETE /skill-script/{ids}
export function delSkillScript(ids) {
  return http.del('/skill-script/' + ids)
}

// POST /skill-script/run  body {scriptId, params}
export function runSkillScript(data) {
  return http.post('/skill-script/run', data)
}

// POST /skill-script/run-inline  body {code, params, timeoutSec, skillId}  不落库直接执行
export function runInlineScript(data) {
  return http.post('/skill-script/run-inline', data)
}

// GET /skill-script/log?scriptId=&limit=
export function skillScriptLogs(params) {
  return http.get('/skill-script/log', params)
}

// GET /skill-script/{scriptId}/content  读取脚本文件内容
export function readScriptContent(scriptId) {
  return http.get('/skill-script/' + scriptId + '/content')
}

// PUT /skill-script/{scriptId}/content  body {content}  保存脚本源码到技能目录文件
export function saveScriptContent(scriptId, data) {
  return http.put('/skill-script/' + scriptId + '/content', data)
}

// POST /skill-script/sync  body {skillId}  重新扫描技能目录，注册新增脚本
export function syncSkillScripts(skillId) {
  return http.post('/skill-script/sync', { skillId })
}
