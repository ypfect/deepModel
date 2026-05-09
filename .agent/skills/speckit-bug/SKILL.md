---
name: "speckit-bug"
description: "从 bug 描述创建 bug 报告规格文档，使用 bug 专用模板。"
compatibility: "Requires spec-kit project structure with .specify/ directory"
metadata:
  author: "speckit-extension"
  source: "templates/commands/bug.md"
---


## User Input

```text
$ARGUMENTS
```

You **MUST** consider the user input before proceeding (if not empty).

## Pre-Execution Checks

**Check for extension hooks (before bug)**:
- Check if `.specify/extensions.yml` exists in the project root.
- If it exists, read it and look for entries under the `hooks.before_bug` key
- If the YAML cannot be parsed or is invalid, skip hook checking silently and continue normally
- Filter out hooks where `enabled` is explicitly `false`. Treat hooks without an `enabled` field as enabled by default.
- For each remaining hook, do **not** attempt to interpret or evaluate hook `condition` expressions:
  - If the hook has no `condition` field, or it is null/empty, treat the hook as executable
  - If the hook defines a non-empty `condition`, skip the hook and leave condition evaluation to the HookExecutor implementation
- For each executable hook, output the following based on its `optional` flag:
  - **Optional hook** (`optional: true`):
    ```
    ## Extension Hooks

    **Optional Pre-Hook**: {extension}
    Command: `/{command}`
    Description: {description}

    Prompt: {prompt}
    To execute: `/{command}`
    ```
  - **Mandatory hook** (`optional: false`):
    ```
    ## Extension Hooks

    **Automatic Pre-Hook**: {extension}
    Executing: `/{command}`
    EXECUTE_COMMAND: {command}

    Wait for the result of the hook command before proceeding to the Outline.
    ```
- If no hooks are registered or `.specify/extensions.yml` does not exist, skip silently

## Outline

The text the user typed after `/speckit.bug` in the triggering message **is** the bug description. Assume you always have it available in this conversation even if `$ARGUMENTS` appears literally below. Do not ask the user to repeat it unless they provided an empty command.

Given that bug description, do this:

1. **Generate a concise short name** (2-4 words) for the bug fix:
   - Analyze the bug description and extract the most meaningful keywords
   - Create a 2-4 word short name that captures the essence of the bug
   - **MUST prefix with `fix-`** (e.g., "fix-null-pointer-crash", "fix-writeBack-missing")
   - Preserve technical terms and error keywords
   - Examples:
     - "保存合同时报空指针异常" → "fix-contract-save-npe"
     - "回写金额不对" → "fix-writeback-amount"
     - "审批后状态没更新" → "fix-approval-status"

2. **Branch creation** (optional, via hook):

   If a `before_bug` hook ran successfully in the Pre-Execution Checks above, it will have created/switched to a git branch and output JSON containing `BRANCH_NAME` and `FEATURE_NUM`. Note these values for reference, but the branch name does **not** dictate the spec directory name.

3. **Determine the parent iteration directory**:

   Bug specs are nested under their parent iteration's `bugs/` subdirectory:
   ```
   specs/
     002-metadata-services/       ← 迭代目录
       spec.md                    ← 迭代 spec
       bugs/
         001-fix-parse-error/     ← bug 目录
           spec.md                ← bug spec
   ```

   **Resolution order for parent iteration**:
   1. If the user explicitly specified an iteration (e.g., `002-metadata-services` or just `002`):
      - Find the matching directory under `specs/` by prefix
   2. Otherwise, read `.specify/feature.json` → `feature_directory`:
      - If it points to a valid iteration directory (e.g., `specs/002-metadata-services`), use it
      - If it points to a bug directory (e.g., `specs/002-metadata-services/bugs/001-fix-xxx`), extract and use the iteration root
   3. If neither available: **ask the user** which iteration this bug belongs to, listing existing directories under `specs/`

   Set `PARENT_ITERATION_DIR` to the resolved iteration directory (absolute path).

4. **Create the bug directory under `bugs/`**:

   - Scan `PARENT_ITERATION_DIR/bugs/` for existing bug directories
   - Determine next sequential number (3-digit, starting from `001`)
   - Construct bug directory name: `<NNN>-<short-name>` (e.g., `001-fix-contract-save-npe`)
   - Set `BUG_DIR` to `PARENT_ITERATION_DIR/bugs/<bug-directory-name>`

   **Create the directory and spec file**:
   - `mkdir -p BUG_DIR`
   - Copy `.specify/templates/bug-spec-template.md` to `BUG_DIR/spec.md` as the starting point
   - Set `SPEC_FILE` to `BUG_DIR/spec.md`
   - Persist the resolved path to `.specify/feature.json`:
     ```json
     {
       "feature_directory": "<resolved bug dir>",
       "parent_iteration": "<parent iteration dir>"
     }
     ```
     Example:
     ```json
     {
       "feature_directory": "specs/002-metadata-services/bugs/001-fix-contract-save-npe",
       "parent_iteration": "specs/002-metadata-services"
     }
     ```
     `parent_iteration` enables downstream commands (`/speckit.plan`, `/speckit.tasks`, etc.) to access the iteration's spec.md, plan.md, data-model.md as background context for the bug fix.

   **IMPORTANT**:
   - You must only create one bug report per `/speckit.bug` invocation
   - The spec directory and file are always created by this command, never by the hook
   - Bug numbering is scoped to the parent iteration's `bugs/` directory (independent of the iteration's own numbering)

5. **Load iteration context** (background for bug analysis):

   Read the following files from `PARENT_ITERATION_DIR` if they exist:
   - `spec.md` — understand the iteration's feature scope and user stories
   - `plan.md` — understand the tech stack, architecture, and file structure
   - `data-model.md` — understand entities and relationships
   
   This context helps the AI:
   - Write more accurate root cause hypotheses
   - Identify which components/files are likely affected
   - Generate better acceptance criteria that consider the iteration's design

   **Do NOT copy or duplicate this context into the bug spec.** Just use it as background knowledge.

6. Load `.specify/templates/bug-spec-template.md` to understand required sections.

7. Follow this execution flow:
    1. Parse bug description from arguments
       If empty: ERROR "No bug description provided"
    2. Extract key information from description
       Identify: symptoms, error messages, affected components, trigger conditions
    3. For unclear aspects:
       - Make informed guesses based on context
       - Only mark with [NEEDS CLARIFICATION: specific question] if:
         - The bug cannot be reasonably analyzed without this info
         - Multiple completely different bugs could match the description
       - **LIMIT: Maximum 2 [NEEDS CLARIFICATION] markers total**
       - Prioritize: reproducibility > affected scope > environment details
    4. Fill 复现步骤 section
       If no clear reproduction steps: infer from bug description, mark uncertain steps with [推断]
    5. Fill 期望行为 vs 实际行为
       Each must be concrete and testable
    6. Assess 严重级别
       - P0-阻塞: 系统不可用 / 数据损坏
       - P1-严重: 核心功能不可用 / 阻塞业务流程
       - P2-一般: 功能异常但有绕过方案
       - P3-轻微: UI 问题 / 非核心功能 / 边界场景
    7. Fill 影响范围
    8. Fill 根因假设 (if enough clues in description)
    9. Generate 验收标准
       Must include: 原始 bug 修复验证 + 回归验证
   10. Return: SUCCESS (bug spec ready)

8. Write the bug specification to SPEC_FILE using the bug template structure, replacing placeholders with concrete details derived from the bug description while preserving section order and headings.

9. **Bug Spec Quality Validation**: After writing the initial spec, validate against quality criteria:

   a. **Create Bug Spec Quality Checklist**: Generate a checklist file at `BUG_DIR/checklists/requirements.md`:

      ```markdown
      # Bug 修复质量检查清单: [BUG TITLE]

      **Purpose**: 验证 bug 报告完整性和修复就绪度
      **Created**: [DATE]
      **Bug Report**: [Link to spec.md]

      ## 报告完整性

      - [ ] 复现步骤可执行（非模糊描述）
      - [ ] 期望行为和实际行为都有具体描述
      - [ ] 严重级别评估合理
      - [ ] 影响范围已评估

      ## 修复就绪度

      - [ ] 验收标准可测试
      - [ ] 验收标准包含回归验证
      - [ ] 无未解决的 [NEEDS CLARIFICATION] 标记
      - [ ] 环境信息足够定位问题

      ## Notes

      - Items marked incomplete require spec updates before `/speckit.plan`
      ```

   b. **Run Validation Check**: Review the spec against each checklist item

   c. **Handle Validation Results**:
      - **If all items pass**: Mark checklist complete and proceed
      - **If items fail**: Fix and re-validate (max 3 iterations)
      - **If [NEEDS CLARIFICATION] markers remain** (max 2): Present questions with options table, wait for user response, then update spec

   d. **Update Checklist**: After each validation iteration, update the checklist file

10. **Report completion** to the user with:
   - `BUG_DIR` — the feature directory path
   - `SPEC_FILE` — the spec file path
   - 严重级别 summary
   - Checklist results summary
   - Next steps: `/speckit.plan` (for complex bugs needing design) or direct fix (for simple bugs)

11. **Check for extension hooks**: After reporting completion, check if `.specify/extensions.yml` exists in the project root.
   - If it exists, read it and look for entries under the `hooks.after_bug` key
   - Follow the same hook execution rules as Pre-Execution Checks
   - If no hooks are registered or `.specify/extensions.yml` does not exist, skip silently

**NOTE:** Branch creation is handled by the `before_bug` hook (git extension). Spec directory and file creation are always handled by this core command.

## Quick Guidelines

- Focus on **WHAT went wrong** and **HOW to verify the fix**.
- Avoid premature fix implementation details in the spec.
- Root cause analysis belongs in the `plan` phase, not here (but initial hypotheses are welcome).
- The spec is for documenting the bug, not for solving it.

### Section Requirements

- **Mandatory sections**: 环境信息, 复现步骤, 期望行为 vs 实际行为, 影响范围, 验收标准
- **Optional sections**: 根因假设, 修复约束, 关联信息
- When a section doesn't apply, remove it entirely (don't leave as "N/A")

### For AI Generation

When creating this bug spec from a user prompt:

1. **Extract symptoms**: Error messages, stack traces, unexpected behavior descriptions
2. **Infer reproduction**: Build minimal reproduction steps from the description
3. **Assess severity**: Default to P2 if unclear, adjust based on keywords (crash → P1, data loss → P0)
4. **Generate hypotheses**: If the description mentions code locations or error types, form root cause hypotheses
5. **Limit clarifications**: Maximum 2 [NEEDS CLARIFICATION] markers — most bugs can be spec'd with reasonable assumptions
6. **Think like a QA**: Every vague symptom should be converted to a testable acceptance criterion

### Downstream Workflow

After `/speckit.bug` completes, the standard SpecKit workflow continues:

- **Simple bugs** (clear root cause, 1-2 file changes): Skip `plan`, go directly to `tasks` → `implement`
- **Complex bugs** (unclear root cause, multi-component): Use `plan` for root cause analysis → `tasks` → `implement`
- **`clarify`**: Can be used to ask follow-up questions about reproduction or environment details
