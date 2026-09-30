import { ref } from 'vue'
import { getDictDataByType } from '@/api/system'

/**
 * 全局字典缓存：key 为 dictType，value 为 [{label, value, listClass}]
 */
const dictCache = new Map()
const pending = new Map()

/** 预加载 / 获取字典数据 */
export function loadDict(type) {
  if (!type) return Promise.resolve([])
  if (dictCache.has(type)) return Promise.resolve(dictCache.get(type))
  if (pending.has(type)) return pending.get(type)

  const p = getDictDataByType(type)
    .then((data) => {
      const list = (data || []).map((item) => ({
        label: item.dictLabel,
        value: item.dictValue,
        listClass: item.listClass,
        elTagType: item.listClass,
        dictCode: item.dictCode,
        isDefault: item.isDefault
      }))
      dictCache.set(type, list)
      pending.delete(type)
      return list
    })
    .catch(() => {
      pending.delete(type)
      return []
    })

  pending.set(type, p)
  return p
}

/**
 * 字典 hook
 * const { sys_user_sex } = useDict('sys_user_sex')
 * 模板中直接用 sys_user_sex（Ref 会自动解包）
 */
export function useDict(...types) {
  const result = {}
  const flat = types.flat ? types.flat() : types
  flat.forEach((type) => {
    const item = ref(dictCache.get(type) || [])
    result[type] = item
    loadDict(type).then((list) => {
      item.value = list
    })
  })
  return result
}

/** 清空字典缓存（字典维护后调用） */
export function clearDictCache(type) {
  if (type) dictCache.delete(type)
  else dictCache.clear()
}

export default useDict
