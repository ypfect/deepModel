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
}
