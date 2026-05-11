package com.deepmodel.relation.model;

/**
 * 对象类型元信息，用于 resolve 匹配和前端展示。
 * 数据来源：baseapp_object_type 表。
 */
public class ObjectTypeMeta {
    private String name;
    private String title;
    private String description;
    private String type; // bill / document / setting
    private Boolean isDisabled;
    // 对象特性字段（从 content JSON 提取）
    private Boolean isTree;
    private Boolean isDetail;
    private Boolean isSupportChangeLog;
    private Boolean isCustomizedEntity;
    private Boolean isMultiDataVersion;
    private String businessModuleId;
    private String appName;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getIsDisabled() {
        return isDisabled;
    }

    public void setIsDisabled(Boolean isDisabled) {
        this.isDisabled = isDisabled;
    }

    public Boolean getIsTree() { return isTree; }
    public void setIsTree(Boolean isTree) { this.isTree = isTree; }

    public Boolean getIsDetail() { return isDetail; }
    public void setIsDetail(Boolean isDetail) { this.isDetail = isDetail; }

    public Boolean getIsSupportChangeLog() { return isSupportChangeLog; }
    public void setIsSupportChangeLog(Boolean isSupportChangeLog) { this.isSupportChangeLog = isSupportChangeLog; }

    public Boolean getIsCustomizedEntity() { return isCustomizedEntity; }
    public void setIsCustomizedEntity(Boolean isCustomizedEntity) { this.isCustomizedEntity = isCustomizedEntity; }

    public Boolean getIsMultiDataVersion() { return isMultiDataVersion; }
    public void setIsMultiDataVersion(Boolean isMultiDataVersion) { this.isMultiDataVersion = isMultiDataVersion; }

    public String getBusinessModuleId() { return businessModuleId; }
    public void setBusinessModuleId(String businessModuleId) { this.businessModuleId = businessModuleId; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
}
