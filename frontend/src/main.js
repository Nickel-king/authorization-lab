import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './assets/main.css'
import App from './App.vue'
import router from './router'

// 创建应用实例
const app = createApp(App)

// 挂载 Element Plus 组件库（中文语言包）
app.use(ElementPlus, { locale: zhCn })

// 挂载前端路由
app.use(router)

// 挂载到 #app
app.mount('#app')