# Feature Specification: Expression Checker UI

**Status**: Draft
**Input**: 给刚刚做的这个（表达式一致性校验引擎）功能增加前端界面展示

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 可视化按模块全量扫描结果 (Priority: P1)
**As a** 系统管理员或开发验证人员，
**I want to** 在后台管理界面上通过点击按钮触发某个应用模块（如 arap）的表达式一致性全量校验，
**So that** 我能看清当前系统配置中所有的语法错误和依赖断裂问题，而不需要使用命令行或 Postman。

**Acceptance Criteria**:
1. **Given** 用户进入“配置校验中心”界面，**When** 在顶部的应用模块下拉框中选择某个模块并点击“开始扫描”，**Then** 界面显示加载动画并在完成后展示分析概要（总扫描数、Error 数、Warning 数）。
2. **Given** 校验报告返回了多条异常项，**When** 用户查看明细区域，**Then** 可以看到以表格或卡片列表形式展示的具体异常记录。
3. **Given** 存在不同的严重级别，**When** 查看列表时，**Then** FATAL、ERROR 和 WARNING 类别会有各自明显的颜色标识（如红色、橙色、黄色）。

### User Story 2 - 支持面向单一实体的配置排查 (Priority: P2)
**As a** 配置实施顾问，
**I want to** 在界面上指定一个特定的业务对象（如 ArReceipt）进行定向扫描，
**So that** 在刚修改完某个实体的配置后可以立即验证，排除干扰项。

**Acceptance Criteria**:
1. **Given** 校验表单区域，**When** 用户切换至“单对象扫描”模式，输入具体的对象标识符并执行，**Then** 只展示出该对象自身内部包含的表达式健康状况分析报告。

## Requirements *(mandatory)*

### Functional Requirements
- **FR-001**: System MUST 提供一个交互式界面，包含用于输入或选择“应用模块名”和“单对象名”的检索条件区。
- **FR-002**: System MUST 对接后端 API `/api/validation/report` 和 `/api/validation/check` 获取 `ValidationReport` 结构的 JSON 数据。
- **FR-003**: System MUST 提供一个概览统计面板，明确直观地聚合展示 `scannedObjectCount`, `totalErrors` 和 `totalWarnings`。
- **FR-004**: System MUST 提供可分页或带内滚动的数据表格展示 `items`。各列应包含：被扫描的对象 (`objectType`)、异常出处 (`expressionType`)、关联字段 (`fieldName`)、具体的异常种类 (`errorCategory`) 和详细文本提示 (`message`)。
- **FR-005**: System MUST 为不同 `severity` 提供视觉样式区分。

### Key Entities
本需求纯属消费后端 API 数据的前端应用展示，不需要构建新的后端结构或落库，核心消费现有的 `ValidationReport` 和 `ValidationErrorItem` 数据结构即可。

## Success Criteria *(mandatory)*

- **Visibility**: 任何系统开发人员可以通过前端快速启动针对核心模块的健康检查，不再查验 JSON 裸串。
- **Usability**: 点击发起校验后至结果渲染的响应过程中，需展现友好的加载指示以避免用户中途关闭页面；错误文案直观地暴露给用户。

## Assumptions & Boundaries *(optional)*

- 前端将挂载在目前项目的管理界面菜单体系内，复用已有的基建体系（如 Vue/React UI Component库）。
- 由于扫描可能是慢速操作（单个模块~5秒），界面需要处理可能的读取等待甚至超长 Timeout，但初期假设后端能在合理时间内响应。

- 前端界面的开发将采取深度集成至现有的前端工程 (如 Vue / React) 的策略，以保证视觉规范一致。
- 采用内网同源发起 API 调用请求，无需额外进行跨域（CORS）配置和复杂的 Token 代理注入。
