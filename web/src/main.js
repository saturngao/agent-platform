import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIcons from '@element-plus/icons-vue'

import 'element-plus/dist/index.css'
import '@/styles/index.scss'

import App from './App.vue'
import store from './stores'
import router from './router'
import './permission'

const app = createApp(App)

// 全局注册 Element Plus
app.use(ElementPlus, { locale: zhCn, size: 'default' })

// 全局注册 Element Plus 图标（模板中可直接使用 <User /> / <Setting />）
Object.entries(ElementPlusIcons).forEach(([key, component]) => {
  app.component(key, component)
})

app.use(store)
app.use(router)

router.isReady().then(() => {
  app.mount('#app')
})

export default app
