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
