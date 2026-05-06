package com.deepmodel.relation.model;

import java.util.Map;
import java.util.Set;

/**
 * 表达式字段依赖视图——单个对象内所有表达式字段的计算依赖和层级排序。
 */
public class ExpressionFieldInfo {
    private String objectType;
    /** 表达式字段 → 变量字段集合 */
    private Map<String, Set<String>> exprFieldToVars;
    /** 无变量的表达式字段 */
    private Set<String> noVarExprFields;
    /** 变量字段 → 引用该变量的表达式字段 */
    private Map<String, Set<String>> fieldToExprFields;
    /** 层级 → 字段集合（-1=变量, 0=最先计算, N=依赖 N-1） */
    private Map<Integer, Set<String>> levelToFields;

    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }

    public Map<String, Set<String>> getExprFieldToVars() { return exprFieldToVars; }
    public void setExprFieldToVars(Map<String, Set<String>> exprFieldToVars) { this.exprFieldToVars = exprFieldToVars; }

    public Set<String> getNoVarExprFields() { return noVarExprFields; }
    public void setNoVarExprFields(Set<String> noVarExprFields) { this.noVarExprFields = noVarExprFields; }

    public Map<String, Set<String>> getFieldToExprFields() { return fieldToExprFields; }
    public void setFieldToExprFields(Map<String, Set<String>> fieldToExprFields) { this.fieldToExprFields = fieldToExprFields; }

    public Map<Integer, Set<String>> getLevelToFields() { return levelToFields; }
    public void setLevelToFields(Map<Integer, Set<String>> levelToFields) { this.levelToFields = levelToFields; }
}
