# Quickstart: Injecting into Admin Shell

这通常是加到任意以 Vue Router 为主的后台管理系统的方式：

1. **迁移页面文件**:
   将 `ValidationCenter/` 整个目录移动到你们的前端代码仓库 `src/views/` 目录下。

2. **配装路由**:
   找到仓库中的 `src/router/index.ts`（或者对应的路由表），加粗暴增加这一段：
   ```typescript
   {
       path: '/devops/validation-center',
       name: 'ValidationCenter',
       component: () => import('@/views/ValidationCenter/index.vue'),
       meta: {
           title: '健康检查器',
           icon: 'el-icon-monitor' // 或者对应基建体系的图标规范
       }
   }
   ```

3. **进入面板把玩**:
   重新启动热更新前端服务（如 `npm run dev`），点击左侧侧边栏新出来的图标，畅想极其炫酷的测试界面。
