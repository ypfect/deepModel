---
description: "Task list template for feature implementation"
---

# Tasks: Expression Consistency Checker

**Input**: Design documents from `/specs/001-expression-consistency-checker/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Test tasks are included as required by the testability principle in `constitution.md`.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Java source code: `src/main/java/com/deepmodel/relation/...`
- Test source code: `src/test/java/com/deepmodel/relation/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 [P] Create `SeverityLevel.java` Enum
- [x] T002 [P] Create `ErrorCategory.java` Enum
- [x] T003 [P] Create `ExpressionType.java` Enum

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T004 [P] Create `ValidationErrorItem.java` model
- [x] T005 [P] Create `ValidationReport.java` model

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - 验证全量/单个对象的表达式一致性 (Priority: P1) 🎯 MVP

**Goal**: 实现校验核心引擎，支撑单对象与多对象的配置有效性扫描，捕获异常并分级处理。

**Independent Test**: 可以直接在单元测试中实例化 `ExpressionValidatorService` 并提供假元数据环境，验证其捕获脏数据的能力。

### Tests for User Story 1 (OPTIONAL - only if tests requested) ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [x] T006 [P] [US1] Unit test skeleton for `ExpressionValidatorServiceTest`

### Implementation for User Story 1

- [x] T007 [US1] Create core `ExpressionValidatorService.java`
- [x] T008 [US1] Implement `JSqlParser` integration logic
- [x] T009 [US1] Implement metadata matching and validation logic for `writeBackExpr`, `triggerExpr`
- [x] T010 [US1] Implement severity routing logic
- [x] T011 [US1] Implement orchestrator public methods

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - 输出结构化的异常修复报告 (Priority: P2)

**Goal**: 通过独立的 API 端点对外发布一致性校验的能力，呈现结构化 JSON 报告，供前端/CLI快速消费。

**Independent Test**: 在本地启动服务，并通过 REST 客户端请求 `/api/validation/check` 端点，验证 JSON 返回的层次和字段。

### Tests for User Story 2 (OPTIONAL - only if tests requested) ⚠️

- [x] T012 [P] [US2] Integration test for `ValidationController` checking JSON structure

### Implementation for User Story 2

- [x] T013 [US2] Create new `ValidationController.java`
- [x] T014 [US2] Implement `GET /api/validation/check?objectType=...` endpoint
- [x] T015 [US2] Implement `GET /api/validation/report?appName=...` endpoint

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [x] T016 Setup Javadoc documentation strings for public APIs inside `ValidationController`.
- [x] T017 Review and verify OpenAPI/Swagger definition auto-generation works correctly for the newly created endpoints (if Swagger is installed).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2)
- **User Story 2 (P2)**: MUST start after User Story 1 is completed (Needs the core service methods).

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- Models before services
- Services before endpoints
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup enum generation (T001, T002, T003) can be created simultaneously.
- Core models (T004, T005) can be created simultaneously after Setup Phase.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently logic via `ExpressionValidatorServiceTest`
5. Consider the feature mathematically proven at this stage.

### Incremental Delivery

1. Complete MVP (engine level).
2. Complete User Story 2 (controller interface level) → Test over HTTP independently → Expose/Demo feature to consumers.
