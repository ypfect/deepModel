package com.deepmodel.relation.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 枚举类型元信息，从 baseapp_system_metadata 中 type_id LIKE '%enum%' 的 content JSON 解析。
 */
public class EnumTypeMeta {
    private String name;
    private String title;
    private String description;
    private List<EnumValueMeta> values = new ArrayList<>();

    public EnumTypeMeta() {}

    public EnumTypeMeta(String name, String title, String description) {
        this.name = name;
        this.title = title;
        this.description = description;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<EnumValueMeta> getValues() { return values; }
    public void setValues(List<EnumValueMeta> values) { this.values = values; }
}
