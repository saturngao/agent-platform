import { http } from '@/utils/request'

/**
 * 系统管理接口
 * 后端采用 REST 显式动作后缀：/list 列表、/add 新增、/edit 修改、/remove/{ids} 删除
 */

/* ---------------------------------- 用户 ---------------------------------- */

// GET /system/user/list?{pageNum,pageSize,username,phonenumber,status,deptId,beginTime,endTime}
export function listUser(params) {
  return http.get('/system/user/list', params)
}

// GET /system/user/detail/{userId}
export function getUserDetail(userId) {
  return http.get(`/system/user/detail/${userId}`)
}

// GET /system/user/profile
export function getUserProfile() {
  return http.get('/system/user/profile')
}

// POST /system/user/add
export function addUser(data) {
  return http.post('/system/user/add', data)
}

// PUT /system/user/edit
export function updateUser(data) {
  return http.put('/system/user/edit', data)
}

// DELETE /system/user/remove/{ids}   ids 逗号分隔
export function delUser(ids) {
  return http.del(`/system/user/remove/${ids}`)
}

// PUT /system/user/resetPwd  body {userId, password}
export function resetUserPwd(data) {
  return http.put('/system/user/resetPwd', data)
}

// PUT /system/user/updatePwd  body {oldPassword, newPassword}  当前登录用户自助修改密码
export function updatePwd(data) {
  return http.put('/system/user/updatePwd', data)
}

// PUT /system/user/status  body {userId, status}
export function changeUserStatus(data) {
  return http.put('/system/user/status', data)
}

/* ---------------------------------- 部门 ---------------------------------- */

export function listDept(params) {
  return http.get('/system/dept/list', params)
}

// GET /system/dept/tree
export function deptTree() {
  return http.get('/system/dept/tree')
}

// GET /system/dept/{id}
export function getDept(deptId) {
  return http.get(`/system/dept/${deptId}`)
}

export function addDept(data) {
  return http.post('/system/dept/add', data)
}

export function updateDept(data) {
  return http.put('/system/dept/edit', data)
}

export function delDept(id) {
  return http.del(`/system/dept/remove/${id}`)
}

/* ---------------------------------- 岗位 ---------------------------------- */

export function listPost(params) {
  return http.get('/system/post/list', params)
}

export function getPost(postId) {
  return http.get(`/system/post/${postId}`)
}

export function addPost(data) {
  return http.post('/system/post/add', data)
}

export function updatePost(data) {
  return http.put('/system/post/edit', data)
}

export function delPost(ids) {
  return http.del(`/system/post/remove/${ids}`)
}

/* ---------------------------------- 角色 ---------------------------------- */

export function listRole(params) {
  return http.get('/system/role/list', params)
}

export function getRole(roleId) {
  return http.get(`/system/role/${roleId}`)
}

export function addRole(data) {
  return http.post('/system/role/add', data)
}

export function updateRole(data) {
  return http.put('/system/role/edit', data)
}

export function delRole(ids) {
  return http.del(`/system/role/remove/${ids}`)
}

// PUT /system/role/status  body {roleId, status}
export function changeRoleStatus(data) {
  return http.put('/system/role/status', data)
}

// PUT /system/role/dataScope  body {roleId, dataScope, deptIds:[]}
export function updateRoleDataScope(data) {
  return http.put('/system/role/dataScope', data)
}

// GET /system/role/menuTree/{roleId} -> {menus:[], checkedKeys:[]}
export function roleMenuTree(roleId) {
  return http.get(`/system/role/menuTree/${roleId}`)
}

/* ---------------------------------- 菜单 ---------------------------------- */

export function listMenu(params) {
  return http.get('/system/menu/list', params)
}

// GET /system/menu/tree -> 树形数组（id/label/children）
export function menuTree() {
  return http.get('/system/menu/tree')
}

export function getMenu(menuId) {
  return http.get(`/system/menu/${menuId}`)
}

export function addMenu(data) {
  return http.post('/system/menu/add', data)
}

export function updateMenu(data) {
  return http.put('/system/menu/edit', data)
}

export function delMenu(id) {
  return http.del(`/system/menu/remove/${id}`)
}

/* -------------------------------- 字典类型 -------------------------------- */

export function listDictType(params) {
  return http.get('/system/dict/type/list', params)
}

// GET /system/dict/type/optionselect
export function dictTypeOptionselect() {
  return http.get('/system/dict/type/optionselect')
}

export function getDictType(dictId) {
  return http.get(`/system/dict/type/${dictId}`)
}

export function addDictType(data) {
  return http.post('/system/dict/type/add', data)
}

export function updateDictType(data) {
  return http.put('/system/dict/type/edit', data)
}

export function delDictType(ids) {
  return http.del(`/system/dict/type/remove/${ids}`)
}

/* -------------------------------- 字典数据 -------------------------------- */

// GET /system/dict/data/list?dictType=xxx
export function listDictData(params) {
  return http.get('/system/dict/data/list', params)
}

// GET /system/dict/data/type/{dictType}
export function getDictDataByType(dictType) {
  return http.get(`/system/dict/data/type/${dictType}`)
}

export function getDictData(dictCode) {
  return http.get(`/system/dict/data/${dictCode}`)
}

export function addDictData(data) {
  return http.post('/system/dict/data/add', data)
}

export function updateDictData(data) {
  return http.put('/system/dict/data/edit', data)
}

export function delDictData(ids) {
  return http.del(`/system/dict/data/remove/${ids}`)
}

/* -------------------------------- 参数配置 -------------------------------- */

export function listConfig(params) {
  return http.get('/system/config/list', params)
}

// GET /system/config/configKey/{key}
export function getConfigByKey(key) {
  return http.get(`/system/config/configKey/${key}`)
}

export function getConfig(configId) {
  return http.get(`/system/config/${configId}`)
}

export function addConfig(data) {
  return http.post('/system/config/add', data)
}

export function updateConfig(data) {
  return http.put('/system/config/edit', data)
}

export function delConfig(ids) {
  return http.del(`/system/config/remove/${ids}`)
}
