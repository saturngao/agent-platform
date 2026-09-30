import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi, getInfo as getInfoApi } from '@/api/auth'
import { getToken, setToken, removeToken, setLocalUser } from '@/utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    userId: '',
    username: '',
    nickname: '',
    avatar: '',
    deptId: '',
    userInfo: {},
    roles: [],
    permissions: []
  }),
  getters: {
    isLogin: (state) => !!state.token
  },
  actions: {
    /** 登录：返回 data {token, expiresIn, ...} */
    async login({ username, password }) {
      const data = await loginApi({ username, password })
      this.token = data?.token || ''
      setToken(this.token)
      if (data) {
        this.userId = data.userId
        this.username = data.username
        this.nickname = data.nickname || data.username
        this.avatar = data.avatar
        this.deptId = data.deptId
      }
      return data
    },

    /** 获取用户信息 + 角色权限 */
    async getInfo() {
      const data = await getInfoApi()
      const user = data?.user || {}
      this.userInfo = user
      this.userId = user.userId
      this.username = user.username
      this.nickname = user.nickname || user.username
      this.avatar = user.avatar
      this.deptId = user.deptId
      this.roles = data?.roles || []
      this.permissions = data?.permissions || []
      setLocalUser(user)
      return data
    },

    /** 退出登录 */
    async logout() {
      try {
        await logoutApi()
      } catch (e) {
        /* 忽略后端异常，前端仍需清理 */
      }
      this.resetState()
    },

    resetState() {
      this.token = ''
      this.userId = ''
      this.username = ''
      this.nickname = ''
      this.avatar = ''
      this.deptId = ''
      this.userInfo = {}
      this.roles = []
      this.permissions = []
      removeToken()
    }
  }
})

export default useUserStore
