package com.deepmodel.relation.service;

import com.deepmodel.relation.enums.ErrorCategory;
import com.deepmodel.relation.enums.ExpressionType;
import com.deepmodel.relation.enums.SeverityLevel;
import com.deepmodel.relation.model.ValidationReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class ExpressionValidatorServiceTest {

    @Mock
    private ImpactAnalyzerService impactAnalyzerService;

    @Mock
    private FormulaParserService formulaParserService;

    @InjectMocks
    private ExpressionValidatorService validatorService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testCheckSingleObject_Success() {
        when(impactAnalyzerService.getAllFields()).thenReturn(Collections.emptyList());
        
        ValidationReport report = validatorService.checkSingleObject("ArReceipt");
        
        assertEquals(0, report.getTotalErrors());
    }
}
