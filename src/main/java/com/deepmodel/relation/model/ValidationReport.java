package com.deepmodel.relation.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ValidationReport {
    private String reportTime;
    private int scannedObjectCount;
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

    public int getTotalErrors() { return totalErrors; }
    public void setTotalErrors(int totalErrors) { this.totalErrors = totalErrors; }

    public int getTotalWarnings() { return totalWarnings; }
    public void setTotalWarnings(int totalWarnings) { this.totalWarnings = totalWarnings; }

    public List<ValidationErrorItem> getItems() { return items; }
    public void setItems(List<ValidationErrorItem> items) { this.items = items; }
}
