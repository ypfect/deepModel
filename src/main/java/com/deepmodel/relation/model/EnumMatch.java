package com.deepmodel.relation.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 枚举匹配结果，作为 ResolveResult.enumMatches 列表的元素。
 */
public class EnumMatch {
    private String enumType;
    private String title;
    private String description;
    private double score;
    private ResolveModels.MatchSource matchSource;
    private List<EnumValueMeta> values = new ArrayList<>();
    private List<String> usedByFields = new ArrayList<>();

    public String getEnumType() { return enumType; }
    public void setEnumType(String enumType) { this.enumType = enumType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public ResolveModels.MatchSource getMatchSource() { return matchSource; }
    public void setMatchSource(ResolveModels.MatchSource matchSource) { this.matchSource = matchSource; }

    public List<EnumValueMeta> getValues() { return values; }
    public void setValues(List<EnumValueMeta> values) { this.values = values; }

    public List<String> getUsedByFields() { return usedByFields; }
    public void setUsedByFields(List<String> usedByFields) { this.usedByFields = usedByFields; }
}
