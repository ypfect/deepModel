package com.deepmodel.relation.model;

/**
 * 枚举值元信息，嵌套在 EnumTypeMeta 中。
 */
public class EnumValueMeta {
    private String value;
    private String title;
    private Integer ordinal;
    private Boolean isDisabled;

    public EnumValueMeta() {}

    public EnumValueMeta(String value, String title, Integer ordinal, Boolean isDisabled) {
        this.value = value;
        this.title = title;
        this.ordinal = ordinal;
        this.isDisabled = isDisabled;
    }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getOrdinal() { return ordinal; }
    public void setOrdinal(Integer ordinal) { this.ordinal = ordinal; }

    public Boolean getIsDisabled() { return isDisabled; }
    public void setIsDisabled(Boolean isDisabled) { this.isDisabled = isDisabled; }
}
