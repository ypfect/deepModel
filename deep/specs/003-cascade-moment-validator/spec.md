# Feature Specification: onlyCascade 时机有效性校验

**Feature Branch**: `003-cascade-moment-validator`  
**Created**: 2026-04-16  
**Status**: Draft  
**Input**: "在增加一个检查 如果回写配置时机写的是onlyCascade，那么要看这个回写来源对象的这个字段是否是回写字段。不然是无效的"

---

## 背景知识

`executingMoment: "onlyCascade"` 的语义：

> 该回写仅在上游字段发生**级联回写**时才会被触发。

**触发链**：
```
上游对象的某字段（必须有 writeBackExpr）发生回写
    → 回写引擎扫描所有依赖它的 onlyCascade 配置
    → 检查 srcObjectType.idField 是否关联到了那条回写记录
    → 若匹配，则触发本 onlyCascade 回写
```

**因此**：如果 `srcObjectType` 中 `idField` 关联的字段本身**没有 `writeBackExpr`**，级联链条不存在上游驱动者，这条 `onlyCascade` 配置**永远不会被执行**，属于**无效配置（Dead Config）**。

---

## User Scenarios & Testing

### User Story 1 - 检测无效的 onlyCascade 配置 (Priority: P1)

**As a** 配置工程师，  
**I want to** 在运行配置体检时，自动检测所有 executingMoment=onlyCascade 中无效的回写配置，  
**So that** 我能及时发现那些看起来配置正确、实际上永远不会执行的「僵尸回写」，避免线上数据不更新的问题被漏掉。

**Why this priority**: onlyCascade 配置错误属于运行时静默失败——不报错、不更新、无日志，排查成本极高。

**Independent Test**: 准备一个 writeBackExpr 带有 executingMoment=onlyCascade 且其 srcObjectType 对应字段无 writeBackExpr 的 JSON 配置，触发扫描后应看到 WARNING 级别的诊断项。

**Acceptance Scenarios**:

1. **Given** 字段 A.fieldX 配置了 writeBackExpr 且 executingMoment=onlyCascade，srcObjectType=B，idField=linkToA，
   **When** 扫描该对象，
   **Then** 检查 expression 中引用的列（如 negativeAdjustmentQuantity）在 srcObjectType 中是否有 writeBackExpr；若不存在则报告 ERROR 级别错误，说明该 onlyCascade 配置永远不会触发。

2. **Given** 同样配置，但 B 中 idField 对应字段确实有 writeBackExpr，
   **When** 扫描，
   **Then** 不产生此类告警。

3. **Given** executingMoment 不是 onlyCascade（例如 onlyAfterSubmit），
   **When** 扫描，
   **Then** 不触发此检查逻辑。

4. **Given** srcObjectType 在元数据中不存在（已由 Rule 3 报过 OBJECT_NOT_FOUND），
   **When** 扫描，
   **Then** 跳过此项检查（避免重复报错）。

---

### Edge Cases

- idField 可能包含点号（如 parentObject.id），需正确解析字段名部分（取点号前的部分）。
- 若 idField 字段本身在 Rule 4 中已被报告为 FIELD_NOT_FOUND，跳过 Rule 8，避免重复噪音。
- srcObjectType 对象存在但字段数量为零，跳过检查。

---

## Requirements

### Functional Requirements

- **FR-001**: 当 writeBackExpr.executingMoment == "onlyCascade" 时，系统 MUST 在现有校验链（Rule 1-7）之后新增 Rule 8 进行级联有效性检查。
- **FR-002**: Rule 8 MUST 查找 srcObjectType 中名称与 idField（去除点号前缀后）匹配的字段，检查该字段是否拥有非空的 writeBackExpr。
- **FR-003**: 若该字段不存在 writeBackExpr，MUST 报告一条 SeverityLevel.ERROR 级别的 ValidationErrorItem，errorCategory = INVALID_CASCADE_TARGET（新增枚举值）。
- **FR-004**: 若 srcObjectType 无法在 groupedFields 中找到，MUST 跳过 Rule 8，不重复报错。
- **FR-005**: ErrorCategory 枚举 MUST 新增 INVALID_CASCADE_TARGET 值，UI 对应显示文本：「级联目标无效」。

### Key Entities

- **WriteBackExpr**: 已有模型，本次不需要新增字段。
- **ErrorCategory**: 枚举新增 INVALID_CASCADE_TARGET。
- **ExpressionValidatorService.validateWriteBackExpr()**: 新增 Rule 8 逻辑分支。
- **checker.html**: categoryLabel 映射中新增 INVALID_CASCADE_TARGET → "级联目标无效"。

---

## Success Criteria

- **SC-001**: 对于 onlyCascade 且源字段无 writeBackExpr 的实体，体检结果中必须出现对应 WARNING 诊断项。
- **SC-002**: 对于 onlyCascade 且源字段有 writeBackExpr 的实体，体检结果中不得出现此类告警。
- **SC-003**: 非 onlyCascade 的配置不受影响，已有校验规则不回归。
- **SC-004**: UI 上 INVALID_CASCADE_TARGET 显示为「级联目标无效」标签。

---

## Assumptions

- idField 的字段匹配复用现有 normalize 策略（去下划线、忽略大小写），与 Rule 4 保持一致。
- 有效性定义：srcObjectType 中 idField 对应字段的 writeBackExpr 字段非空即为有效（不深入校验该 writeBackExpr 内容）。
- 严重级别定为 WARNING（非 ERROR），因为配置人员需有人工确认的空间。

---

## Implementation Plan（实现要点）

### Step 1: 新增 ErrorCategory 枚举值

在 `ErrorCategory.java` 新增：
```java
INVALID_CASCADE_TARGET
```

### Step 2: Rule 8 实现（ExpressionValidatorService）

在 `validateWriteBackExpr()` 末尾 Rule 7 之后追加：

```java
// --- Rule 8: onlyCascade 时机有效性 —— idField 对应源字段必须是回写字段 ---
if ("onlyCascade".equals(wb.getExecutingMoment())
        && wb.getSrcObjectType() != null
        && groupedFields.containsKey(wb.getSrcObjectType())
        && wb.getIdField() != null && !wb.getIdField().isEmpty()) {

    String rawIdField = wb.getIdField().contains(".")
            ? wb.getIdField().split("\\.")[0]
            : wb.getIdField();
    String normalizedId = rawIdField.replace("_", "").toLowerCase();

    List<BaseappObjectField> srcFields = groupedFields.get(wb.getSrcObjectType());
    Optional<BaseappObjectField> srcFieldOpt = srcFields.stream()
            .filter(f -> {
                String n = f.getName() != null ? f.getName().replace("_","").toLowerCase() : "";
                String a = f.getApiName() != null ? f.getApiName().replace("_","").toLowerCase() : "";
                return normalizedId.equals(n) || normalizedId.equals(a);
            })
            .findFirst();

    if (srcFieldOpt.isPresent()) {
        String srcWb = srcFieldOpt.get().getWriteBackExpr();
        if (srcWb == null || srcWb.trim().isEmpty()) {
            report.addItem(new ValidationErrorItem(
                    field.getObjectType(), field.getName(),
                    ExpressionType.WRITE_BACK, ErrorCategory.INVALID_CASCADE_TARGET, SeverityLevel.ERROR,
                    "executingMoment 为 `onlyCascade`，但源对象 `" + wb.getSrcObjectType()
                    + "` 中的字段 `" + rawIdField + "` 没有配置 writeBackExpr，"
                    + "该级联链条不存在上游驱动者，onlyCascade 回写永远不会触发（Dead Config）。"));
        }
    }
}
```

### Step 3: checker.html UI 映射

在 `categoryLabel` 函数中新增：
```js
'INVALID_CASCADE_TARGET': '级联目标无效',
```
