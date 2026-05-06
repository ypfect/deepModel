package com.deepmodel.relation.model;

/**
 * 级联回写信息——当回写目标字段本身也被其他对象回写时，描述级联链路。
 */
public class CascadeWriteBackInfo {
    private String srcObjectType;
    private String targetObjectType;
    private String targetFieldName;
    private String cascadeTargetObjectType;
    private String cascadeTargetFieldName;

    public CascadeWriteBackInfo() {}

    public CascadeWriteBackInfo(String srcObjectType, String targetObjectType, String targetFieldName,
                                String cascadeTargetObjectType, String cascadeTargetFieldName) {
        this.srcObjectType = srcObjectType;
        this.targetObjectType = targetObjectType;
        this.targetFieldName = targetFieldName;
        this.cascadeTargetObjectType = cascadeTargetObjectType;
        this.cascadeTargetFieldName = cascadeTargetFieldName;
    }

    public String getSrcObjectType() { return srcObjectType; }
    public void setSrcObjectType(String srcObjectType) { this.srcObjectType = srcObjectType; }

    public String getTargetObjectType() { return targetObjectType; }
    public void setTargetObjectType(String targetObjectType) { this.targetObjectType = targetObjectType; }

    public String getTargetFieldName() { return targetFieldName; }
    public void setTargetFieldName(String targetFieldName) { this.targetFieldName = targetFieldName; }

    public String getCascadeTargetObjectType() { return cascadeTargetObjectType; }
    public void setCascadeTargetObjectType(String cascadeTargetObjectType) { this.cascadeTargetObjectType = cascadeTargetObjectType; }

    public String getCascadeTargetFieldName() { return cascadeTargetFieldName; }
    public void setCascadeTargetFieldName(String cascadeTargetFieldName) { this.cascadeTargetFieldName = cascadeTargetFieldName; }
}
