import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { rawAxios } from '@/utils/request'

/**
 * 时间格式化
 * @param {Date|string|number} time
 * @param {string} pattern 默认 YYYY-MM-DD HH:mm:ss
 */
export function parseTime(time, pattern = 'YYYY-MM-DD HH:mm:ss') {
  if (time === null || time === undefined || time === '') return ''
  if (time === 0 && time !== '0') return ''
  const d = dayjs(time)
  return d.isValid() ? d.format(pattern) : ''
}

/** formatDate 是 parseTime 的语义化别名 */
export function formatDate(time, pattern = 'YYYY-MM-DD HH:mm:ss') {
  return parseTime(time, pattern)
}

/** 日期范围 -> 起止时间字符串（默认 beginTime / endTime） */
export function addDateRange(params, dateRange, beginKey = 'beginTime', endKey = 'endTime') {
  const search = params ? JSON.parse(JSON.stringify(params)) : {}
  if (Array.isArray(dateRange) && dateRange.length === 2 && dateRange[0] && dateRange[1]) {
    search[beginKey] = parseTime(dateRange[0], 'YYYY-MM-DD')
    search[endKey] = parseTime(dateRange[1], 'YYYY-MM-DD')
  } else {
    search[beginKey] = undefined
    search[endKey] = undefined
  }
  return search
}

/** 深拷贝 */
export function deepClone(obj) {
  if (obj === null || typeof obj !== 'object') return obj
  if (obj instanceof Date) return new Date(obj.getTime())
  if (Array.isArray(obj)) return obj.map((i) => deepClone(i))
  const clone = {}
  Object.keys(obj).forEach((key) => {
    clone[key] = deepClone(obj[key])
  })
  return clone
}

/**
 * 扁平列表转树
 * @param {Array} list
 * @param {{idKey?:string, parentKey?:string, childrenKey?:string}} options
 */
export function buildTree(list, options = {}) {
  const { idKey = 'id', parentKey = 'parentId', childrenKey = 'children' } = options
  const src = Array.isArray(list) ? deepClone(list) : []
  const map = new Map()
  const roots = []

  src.forEach((item) => {
    map.set(String(item[idKey]), item)
    item[childrenKey] = []
  })

  src.forEach((item) => {
    const pid = item[parentKey]
    const parent = pid === undefined || pid === null ? null : map.get(String(pid))
    if (parent && String(parent[idKey]) !== String(item[idKey])) {
      parent[childrenKey].push(item)
    } else {
      roots.push(item)
    }
  })

  const clean = (nodes) => {
    nodes.forEach((n) => {
      if (n[childrenKey] && n[childrenKey].length === 0) delete n[childrenKey]
      else clean(n[childrenKey])
    })
  }
  clean(roots)
  return roots
}

/** handleTree：buildTree 的位置参数写法（兼容常见用法） */
export function handleTree(data, idKey = 'id', parentKey = 'parentId', childrenKey = 'children') {
  return buildTree(data, { idKey, parentKey, childrenKey })
}

/** 字典回显 */
export function selectDictLabel(datas, value) {
  if (!Array.isArray(datas)) return ''
  const hit = datas.find((item) => String(item.value) === String(value))
  return hit ? hit.label : ''
}

/** 文件下载（走原始 axios，返回 blob） */
export function download(url, params, filename, method = 'get') {
  return rawAxios({
    url,
    method,
    params: method === 'get' ? params : undefined,
    data: method !== 'get' ? params : undefined,
    responseType: 'blob'
  })
    .then(async (res) => {
      const isJson = res.data?.type && res.data.type.indexOf('application/json') !== -1
      if (isJson) {
        const text = await res.data.text()
        try {
          const json = JSON.parse(text)
          ElMessage.error(json.msg || '下载失败')
        } catch (e) {
          ElMessage.error('下载失败')
        }
        return
      }
      const blob = new Blob([res.data])
      const downloadUrl = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = downloadUrl
      link.download = filename || 'download'
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(downloadUrl)
    })
    .catch(() => ElMessage.error('下载失败'))
}

/** 通用重置对象字段（用于搜索表单） */
export function resetObject(target, source = {}) {
  Object.keys(target).forEach((key) => {
    target[key] = source[key]
  })
}

/** 生成 UUID（前端临时主键 / 工作流节点 id） */
export function uuid() {
  const s = []
  const hex = '0123456789abcdef'
  for (let i = 0; i < 36; i++) s[i] = hex[Math.floor(Math.random() * 16)]
  s[14] = '4'
  s[19] = hex[(s[19] & 0x3) | 0x8]
  s[8] = s[13] = s[18] = s[23] = '-'
  return s.join('')
}
