package com.deepmodel.relation.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ValidationReport {
    private String reportTime;
    private int scannedObjectCount;
    /** 当前环境下全部对象数（未按 appName 过滤） */
    private int totalObjectCountInEnv;
    /** 实际生效的 appName 过滤，空表示全库 */
    private String filterAppName;
    /** 检出存在问题的对象数（items 中去重 objectType） */
    private int issueObjectCount;
    private int totalErrors;
    private int totalWarnings;
    private List<ValidationErrorItem> items;

    public ValidationReport() {
        this.reportTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.items = new ArrayList<>();
    }

    public void addItem(ValidationErrorItem item) {
        this.items.add(item);
        if (item.getSeverity() == com.deepmodel.relation.enums.SeverityLevel.FATAL || item.getSeverity() == com.deepmodel.relation.enums.SeverityLevel.ERROR) {
            this.totalErrors++;
        } else if (item.getSeverity() == com.deepmodel.relation.enums.SeverityLevel.WARNING) {
            this.totalWarnings++;
        }
    }

    public String getReportTime() { return reportTime; }
    public void setReportTime(String reportTime) { this.reportTime = reportTime; }

    public int getScannedObjectCount() { return scannedObjectCount; }
    public void setScannedObjectCount(int scannedObjectCount) { this.scannedObjectCount = scannedObjectCount; }

    public int getTotalObjectCountInEnv() { return totalObjectCountInEnv; }
    public void setTotalObjectCountInEnv(int totalObjectCountInEnv) { this.totalObjectCountInEnv = totalObjectCountInEnv; }

    public String getFilterAppName() { return filterAppName; }
    public void setFilterAppName(String filterAppName) { this.filterAppName = filterAppName; }

    public int getIssueObjectCount() { return issueObjectCount; }
    public void setIssueObjectCount(int issueObjectCount) { this.issueObjectCount = issueObjectCount; }

    public int getTotalErrors() { return totalErrors; }
    public void setTotalErrors(int totalErrors) { this.totalErrors = totalErrors; }

    public int getTotalWarnings() { return totalWarnings; }
    public void setTotalWarnings(int totalWarnings) { this.totalWarnings = totalWarnings; }

    public List<ValidationErrorItem> getItems() { return items; }
    public void setItems(List<ValidationErrorItem> items) { this.items = items; }
}
