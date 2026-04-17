# Implementation Plan: Expression Checker UI

**Branch**: `002-expression-checker-ui` | **Date**: 2026-04-14 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-expression-checker-ui/spec.md`

## Summary

构建一个“配置校验中心”可视化前端看板，与昨天建立的后端 `/api/validation` 端点无缝对接集成。目标是通过结构化表格展示、并借助明显的严重度颜色标识，让管理员和实施人员摆脱日志文件和 Postman，直观发现由于模型更改导致的依赖性断链与语法级错误。代码直接深度集成进当前的 Vue/React 主线基座前端仓内，复用基建路由。

## Technical Context

**Language/Version**: JavaScript / TypeScript
**Primary Dependencies**: Vue.js / React (假设以 Vue + Element Plus / Ant Design 为主，具体视仓库基础定), Axios
**Storage**: N/A (仅充当展现层)
**Testing**: Jest (Unit Test) / Cypress (E2E) - OPTIONAL
**Target Platform**: Web Browser (Chrome/Edge为主)
**Project Type**: Dashboard UI Component (前端页面集成)
**Performance Goals**: 页面渲染响应与原生组件齐平，主要瓶颈交由后端的 5秒 轮询处理进度。长耗时需挂载 Loading 态。
**Constraints**: 必须复用现有的请求封装，必须同源请求不做额外的跨域配置。
**Scale/Scope**: 单机版管理界面入口扩展。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Data Integrity**: 界面为纯只读展示，不具备改写配置的交互，遵守无损原则。
- **Core Principles**: “Aesthetics”（审美）至关重要。符合 UI/UX “玻璃态”、“流畅动画与渐变色”、良好层级排版的现代化界面需要深度体现。
**状态**: 校验通过 ✅。

## Project Structure

### Documentation (this feature)

```text
specs/002-expression-checker-ui/
├── plan.md              # This file
├── research.md          # UI 交互与设计系统敲脚
├── data-model.md        # 前端 ViewModel
├── quickstart.md        # 如何插入本地路由测验
├── contracts/           
│   └── components.md    # 组件拆分的 Props 契约约束
└── tasks.md             
```

### Source Code (repository root)

```text
frontend/src/
├── views/
│   └── ValidationCenter/
│       ├── index.vue
│       ├── components/
│       │   ├── StatisticsPanel.vue
│       │   ├── ControlPanel.vue
│       │   └── ErrorTable.vue
│       └── api.ts
└── router/
    └── index.ts        // 增加路由挂载点
```

**Structure Decision**: 采用现代前端高内聚的按领域组织路由视图结构。将数据态与接口 API 内聚放置于 `ValidationCenter/` 子目录，并抽出表单、图表与数据表格的木偶形组件使其职责单一。
