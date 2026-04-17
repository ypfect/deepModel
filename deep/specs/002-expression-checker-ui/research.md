# UI Design & Components Design Decisions

### 1. State Management
- **Decision**: 采用组件内聚状态（Composition API / Hooks 级别传递），放弃全局 Vuex/Redux 状态机。
- **Rationale**: 此校验结果的数据是一次性的读取结果展示，属于非常典型的页面级短暂态，不会跨菜单、跨页面流转，强行放进全局 Store 反而会造成额外的 boilerplate。

### 2. Theming & Coloring (符合审美要求)
- **Decision**: 必须定义三原色对应 `FATAL`(赤红色 `#FF4D4F`), `ERROR`(橙红色 `#FA8C16`), `WARNING`(金黄色 `#FAAD14`)。利用毛玻璃卡片（`backdrop-filter: blur(10px)`）承载顶部的数字指标概览，使其具备现代的高级感。
- **Rationale**: 基于 `web_application_development` 高要求指导原则，需要给企业端沉闷的 ERP 界面添加生动活泼而又不失严谨的微交互。

### 3. Loading Experience
- **Decision**: 在调用 `/api/validation/report` 时由于耗时达数十秒，界面应当使用 Skeleton 流光骨架屏而非粗鲁的全屏蒙层转圈。
- **Rationale**: 流畅的微动画能大幅减少等待焦虑。
