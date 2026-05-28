package com.deepmodel.relation.model;

import com.deepmodel.relation.enums.ErrorCategory;
import com.deepmodel.relation.enums.ExpressionType;
import com.deepmodel.relation.enums.SeverityLevel;

public class ValidationErrorItem {
    private String objectType;
    private String fieldName;
    private ExpressionType expressionType;
    private ErrorCategory errorCategory;
    private SeverityLevel severity;
    private String message;
    /** 字段在实体元数据 JSON 中的定义；无 MDD 片段时为由库表字段拼成的 JSON */
    private String fieldDefinitionJson;

    public ValidationErrorItem(String objectType, String fieldName, ExpressionType expressionType, ErrorCategory errorCategory, SeverityLevel severity, String message) {
        this.objectType = objectType;
        this.fieldName = fieldName;
        this.expressionType = expressionType;
        this.errorCategory = errorCategory;
        this.severity = severity;
        this.message = message;
    }

    // Getters and setters
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public ExpressionType getExpressionType() { return expressionType; }
    public void setExpressionType(ExpressionType expressionType) { this.expressionType = expressionType; }

    public ErrorCategory getErrorCategory() { return errorCategory; }
    public void setErrorCategory(ErrorCategory errorCategory) { this.errorCategory = errorCategory; }

    public SeverityLevel getSeverity() { return severity; }
    public void setSeverity(SeverityLevel severity) { this.severity = severity; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getFieldDefinitionJson() { return fieldDefinitionJson; }
    public void setFieldDefinitionJson(String fieldDefinitionJson) { this.fieldDefinitionJson = fieldDefinitionJson; }
}
