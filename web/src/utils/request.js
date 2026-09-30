import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getToken, removeToken } from '@/utils/auth'

const baseURL = import.meta.env.VITE_APP_BASE_API || '/api'

/** 带拦截器的业务请求实例（baseURL = /api） */
export const request = axios.create({
  baseURL,
  timeout: 60000,
  headers: { 'Content-Type': 'application/json;charset=utf-8' }
})

/** 不做任何拦截的原始实例：用于下载等需要拿完整响应的场景 */
export const rawAxios = axios.create({ baseURL, timeout: 120000 })

/** 是否为二进制响应 */
function isBlob(res) {
  return (
    res.config?.responseType === 'blob' ||
    res.config?.responseType === 'arraybuffer' ||
    res.data instanceof Blob ||
    res.data instanceof ArrayBuffer
  )
}

/** 登录失效统一处理 */
function handleUnauthorized(msg) {
  removeToken()
  ElMessageBox.confirm(msg || '登录状态已过期，您可以继续留在该页面，或者重新登录', '系统提示', {
    confirmButtonText: '重新登录',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => {
      // 动态引入，避免与 router 产生循环依赖
      import('@/router').then((m) => {
        m.default.push('/login')
      })
    })
    .catch(() => {
      /* 用户选择留在当前页面 */
    })
}

request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    return config
  },
  (error) => Promise.reject(error)
)

request.interceptors.response.use(
  (res) => {
    // 二进制流直接返回完整 response（导出/下载）
    if (isBlob(res)) {
      return res
    }

    const body = res.data
    // 非标准 JSON（例如第三方接口）直接透传
    if (!body || typeof body !== 'object' || body.code === undefined) {
      return body
    }

    const code = Number(body.code)
    const msg = body.msg || '请求失败'

    if (code === 401) {
      handleUnauthorized(msg)
      return Promise.reject(new Error(msg))
    }

    if (code !== 200) {
      ElMessage.error(msg)
      return Promise.reject(new Error(msg))
    }

    // 业务成功：直接返回 data，页面里可直接 res.rows / res.total
    return body.data
  },
  (error) => {
    const status = error?.response?.status
    const msg = error?.response?.data?.msg || error.message || '网络异常，请稍后再试'

    if (status === 401) {
      handleUnauthorized(msg)
      return Promise.reject(new Error(msg))
    }
    if (status === 403) {
      ElMessage.error('没有权限访问该资源')
    } else {
      ElMessage.error(msg)
    }
    return Promise.reject(new Error(msg))
  }
)

/** 统一的 http 方法对象，请求成功时 resolve 的是响应体中的 data */
export const http = {
  get(url, params, config = {}) {
    return request.get(url, { params, ...config })
  },
  post(url, data, config = {}) {
    return request.post(url, data, config)
  },
  put(url, data, config = {}) {
    return request.put(url, data, config)
  },
  del(url, params, config = {}) {
    return request.delete(url, { params, ...config })
  },
  /** 删除（ids 逗号分隔走 path） */
  delete(url, data, config = {}) {
    return request.delete(url, { data, ...config })
  }
}

export default request
