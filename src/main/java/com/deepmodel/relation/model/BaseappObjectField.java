package com.deepmodel.relation.model;

public class BaseappObjectField {
    private String id;
    private String objectType;
    private String name; // 物理/原始名
    private String apiName; // 对外规范名
    private String title;
    private String type;
    private String bizType;
    private String expression;
    private String triggerExpr;
    private String virtualExpr;
    private String writeBackExpr;
    private String referInfo;

    // Source info for LIST type fields (detail entity identification)
    private String sourceInfo; // jsonb as text

    // Reference relation
    private String refObjectType;

    // Extension
    private String appName; // jsonb as text

    // Resolve 增强字段
    private String description;
    private String enumType;
    private Boolean isDisabled;
    private Boolean isMasterField;
    private Boolean isCustomizedField;

    /** 实体元数据 JSON（baseapp_system_metadata.content.fields[]）中该字段的原始定义片段 */
    private String metadataJson;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getTriggerExpr() {
        return triggerExpr;
    }

    public void setTriggerExpr(String triggerExpr) {
        this.triggerExpr = triggerExpr;
    }

    public String getVirtualExpr() {
        return virtualExpr;
    }

    public void setVirtualExpr(String virtualExpr) {
        this.virtualExpr = virtualExpr;
    }

    public String getWriteBackExpr() {
        return writeBackExpr;
    }

    public void setWriteBackExpr(String writeBackExpr) {
        this.writeBackExpr = writeBackExpr;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getRefObjectType() {
        return refObjectType;
    }

    public void setRefObjectType(String refObjectType) {
        this.refObjectType = refObjectType;
    }

    public String getReferInfo() {
        return referInfo;
    }

    public void setReferInfo(String referInfo) {
        this.referInfo = referInfo;
    }

    public String getSourceInfo() {
        return sourceInfo;
    }

    public void setSourceInfo(String sourceInfo) {
        this.sourceInfo = sourceInfo;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEnumType() {
        return enumType;
    }

    public void setEnumType(String enumType) {
        this.enumType = enumType;
    }

    public Boolean getIsDisabled() {
        return isDisabled;
    }

    public void setIsDisabled(Boolean isDisabled) {
        this.isDisabled = isDisabled;
    }

    public Boolean getIsMasterField() {
        return isMasterField;
    }

    public void setIsMasterField(Boolean isMasterField) {
        this.isMasterField = isMasterField;
    }

    public Boolean getIsCustomizedField() {
        return isCustomizedField;
    }

    public void setIsCustomizedField(Boolean isCustomizedField) {
        this.isCustomizedField = isCustomizedField;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }
}
