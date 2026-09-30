import { createPinia } from 'pinia'

const store = createPinia()

export { useUserStore } from './modules/user'
export { useAppStore } from './modules/app'
export { usePermissionStore } from './modules/permission'

export default store
