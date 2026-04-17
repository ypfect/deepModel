---
description: "Task list template for feature implementation"
---

# Tasks: Expression Checker UI

**Input**: Design documents from `/specs/002-expression-checker-ui/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Path Conventions

- Frontend paths: `frontend/src/views/ValidationCenter/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 [P] Create initial directory structure for `frontend/src/views/ValidationCenter/`
- [x] T002 [P] Create component sub-directory `frontend/src/views/ValidationCenter/components/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T003 Create `frontend/src/views/ValidationCenter/types.ts` defining `ValidationReportVO` and `ValidationItemVM` types.
- [x] T004 Create `frontend/src/views/ValidationCenter/api.ts` implementing the Axios calls for `/api/validation/report` and `/api/validation/check`.

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - 可视化按模块全量扫描结果 (Priority: P1) 🎯 MVP

**Goal**: 在顶层加载模块并展示统计与错误的骨架和表格组件。

**Independent Test**: 无需后台的 ControlPanel 输入也能在 `index.vue` 中预写一个死编码 `appName` 调用跑通骨架层。

### Implementation for User Story 1

- [x] T005 [P] [US1] Create UI Component `frontend/src/views/ValidationCenter/components/StatisticsPanel.vue` (Displays totals and colors).
- [x] T006 [P] [US1] Create UI Component `frontend/src/views/ValidationCenter/components/ErrorTable.vue` (Handles array data & badges for Severity).
- [x] T007 [US1] Create `frontend/src/views/ValidationCenter/index.vue` and orchestrate the layout bringing `StatisticsPanel` and `ErrorTable` together.
- [x] T008 [US1] Hook `api.ts` request logically in `index.vue` and properly manage `isLoading` ref variable.

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently (with hardcoded values).

---

## Phase 4: User Story 2 - 支持面向单一实体的配置排查 (Priority: P2)

**Goal**: 建立表单查询控制面板组，实现针对单一 Object 扫描的交互。

**Independent Test**: 切换表单的筛选条件，保证发起的网络请求和参数被正确路由给后端。

### Implementation for User Story 2

- [x] T009 [US2] Create `frontend/src/views/ValidationCenter/components/ControlPanel.vue` integrating inputs and scan buttons.
- [x] T010 [US2] Intercept emitted `@scan-app` and `@scan-object` events inside `index.vue` and trigger dynamic API calls instead of static ones.
- [x] T011 [US2] Add Form validation to ensure empty queries are not fired to Backend.

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [x] T012 Apply unified backdrop-filter and glass-morphism aesthetic CSS tweaks across all `.vue` view files.
- [x] T013 Update Vue-Router main tree per `quickstart.md` logic (Optional, since router file sits outside `ValidationCenter` path limits, it verifies if external routing fits).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) 
- **User Story 2 (P2)**: MUST start after User Story 1 is completed (needs the container created in US1).
