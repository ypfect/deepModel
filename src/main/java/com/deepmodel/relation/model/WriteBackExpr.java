package com.deepmodel.relation.model;

public class WriteBackExpr {
    private String srcObjectType;
    private String idField;
    private String expression;
    private String condition;

    public String getSrcObjectType() { return srcObjectType; }
    public void setSrcObjectType(String srcObjectType) { this.srcObjectType = srcObjectType; }
    public String getIdField() { return idField; }
    public void setIdField(String idField) { this.idField = idField; }
    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
}
