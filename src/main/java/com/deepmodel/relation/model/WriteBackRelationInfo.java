package com.deepmodel.relation.model;

import java.util.Set;

/**
 * 回写触发关系索引项。
 * <p>
 * 描述源对象通过 writeBackExpr 回写到目标对象某字段的完整信息。
 */
public class WriteBackRelationInfo {
    private String srcObjectType;
    private String targetObjectType;
    private String targetFieldName;
    private String expression;
    private String idField;
    private String condition;
    private Set<String> sourceVars;

    public String getSrcObjectType() { return srcObjectType; }
    public void setSrcObjectType(String srcObjectType) { this.srcObjectType = srcObjectType; }

    public String getTargetObjectType() { return targetObjectType; }
    public void setTargetObjectType(String targetObjectType) { this.targetObjectType = targetObjectType; }

    public String getTargetFieldName() { return targetFieldName; }
    public void setTargetFieldName(String targetFieldName) { this.targetFieldName = targetFieldName; }

    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }

    public String getIdField() { return idField; }
    public void setIdField(String idField) { this.idField = idField; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public Set<String> getSourceVars() { return sourceVars; }
    public void setSourceVars(Set<String> sourceVars) { this.sourceVars = sourceVars; }
}
