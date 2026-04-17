package com.deepmodel.relation.enums;

public enum ErrorCategory {
    FIELD_NOT_FOUND,
    OBJECT_NOT_FOUND,
    TYPE_MISMATCH,
    FATAL_PARSE_ERROR,

    // ---- WriteBack expression specific ----
    /** validateExpr 或 expression 中存在聚合函数嵌套 (e.g. string_agg(CASE WHEN sum(...) ...) ) */
    NESTED_AGGREGATE,
    /** validateExpr 中引用了自身字段（该字段是"被回写的目标字段"），在 validateExpr 上下文中不合法 */
    SELF_REFERENCE_IN_VALIDATE,
    /** writeBackExpr 缺少必填属性 (srcObjectType / idField / expression) */
    MISSING_REQUIRED_FIELD,
    /** executingMoment 值不在合法枚举范围内 */
    INVALID_EXECUTING_MOMENT,
    /** validateExpr 中使用了聚合函数，回写后校验的上下文是单行（已回写后的目标行），不应出现 sum/count 等聚合 */
    AGGREGATE_IN_VALIDATE_EXPR,
    /**
     * executingMoment=onlyCascade 时，writeBackExpr.expression 中引用的源对象字段不是回写字段（没有 writeBackExpr），
     * 导致级联链条不存在上游驱动者，onlyCascade 回写永远不会触发（Dead Config）。
     */
    INVALID_CASCADE_TARGET
}
