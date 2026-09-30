import { http } from '@/utils/request'

/** 登录：{username,password} -> {token, expiresIn, userId, username, nickname, avatar, deptId} */
export function login(data) {
  return http.post('/auth/login', data)
}

/** 退出登录 */
export function logout() {
  return http.post('/auth/logout')
}

/** 获取当前用户信息 -> {user:{...}, roles:[], permissions:[]} */
export function getInfo() {
  return http.get('/auth/info')
}

/** 获取动态路由 -> RouterVO[] */
export function getRouters() {
  return http.get('/auth/routers')
}

/** 验证码配置 -> {captchaEnabled:false, ...} */
export function getCaptcha() {
  return http.get('/auth/captcha')
}
