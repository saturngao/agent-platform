import Cookies from 'js-cookie'

const TokenKey = 'Admin-Token'
const UserKey = 'Admin-User'
const RememberKey = 'Admin-Remember'

/**
 * 读取 token：优先 Cookie，其次 localStorage（兼容后端从 Cookie 取 Admin-Token 的场景）
 */
export function getToken() {
  const token = Cookies.get(TokenKey)
  if (token) return token
  return localStorage.getItem(TokenKey) || ''
}

export function setToken(token) {
  // 同时写入 Cookie 与 localStorage，后端若从 Cookie 读取 Admin-Token 也能命中
  Cookies.set(TokenKey, token, { expires: 7, path: '/' })
  localStorage.setItem(TokenKey, token)
  return token
}

export function removeToken() {
  Cookies.remove(TokenKey, { path: '/' })
  localStorage.removeItem(TokenKey)
}

/** 记住我：保存/读取用户名 */
export function getRemember() {
  try {
    const raw = localStorage.getItem(RememberKey)
    return raw ? JSON.parse(raw) : {}
  } catch (e) {
    return {}
  }
}

export function setRemember(data) {
  localStorage.setItem(RememberKey, JSON.stringify(data || {}))
}

export function removeRemember() {
  localStorage.removeItem(RememberKey)
}

/** 本地缓存的用户信息（可选） */
export function getLocalUser() {
  try {
    const raw = localStorage.getItem(UserKey)
    return raw ? JSON.parse(raw) : null
  } catch (e) {
    return null
  }
}

export function setLocalUser(user) {
  if (user) localStorage.setItem(UserKey, JSON.stringify(user))
  else localStorage.removeItem(UserKey)
}
