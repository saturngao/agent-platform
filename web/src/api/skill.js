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
