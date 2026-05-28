package com.deepmodel.relation.service;

import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.ValidationReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

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
        BaseappObjectField field = new BaseappObjectField();
        field.setObjectType("ArReceipt");
        field.setName("amount");
        when(impactAnalyzerService.getAllFields()).thenReturn(List.of(field));

        ValidationReport report = validatorService.checkSingleObject("ArReceipt");

        assertEquals(0, report.getTotalErrors());
    }
}
