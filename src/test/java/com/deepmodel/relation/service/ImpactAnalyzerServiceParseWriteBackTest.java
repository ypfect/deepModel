package com.deepmodel.relation.service;

import com.deepmodel.relation.model.WriteBackExpr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * parseWriteBack() 增强解析单元测试。
 * 命名规则: 方法名_场景_期望结果
 */
class ImpactAnalyzerServiceParseWriteBackTest {

    private ImpactAnalyzerService service;

    @BeforeEach
    void setUp() {
        // parseWriteBack 不依赖 mapper/formulaParserService，传 null 即可
        service = new ImpactAnalyzerService(null, null, null, null, null);
    }

    @Test
    void parseWriteBack_fullJson_allFieldsParsed() {
        String json = "{\"srcObjectType\":\"ArInvoiceItem\","
                + "\"idField\":\"contractId\","
                + "\"expression\":\"sum(amount)\","
                + "\"condition\":\"isDeleted=false\","
                + "\"executingMoment\":\"ALWAYS\","
                + "\"validateExpr\":\"invoicedAmount<=contractAmount\","
                + "\"validateMessage\":\"开票金额超出合同金额\"}";

        WriteBackExpr wb = service.parseWriteBack(json);

        assertNotNull(wb);
        assertEquals("ArInvoiceItem", wb.getSrcObjectType());
        assertEquals("contractId", wb.getIdField());
        assertEquals("sum(amount)", wb.getExpression());
        assertEquals("isDeleted=false", wb.getCondition());
        assertEquals("ALWAYS", wb.getExecutingMoment());
        assertEquals("invoicedAmount<=contractAmount", wb.getValidateExpr());
        assertEquals("开票金额超出合同金额", wb.getValidateMessage());
    }

    @Test
    void parseWriteBack_minimalJson_optionalFieldsNull() {
        String json = "{\"srcObjectType\":\"PaymentItem\",\"expression\":\"sum(amount)\"}";

        WriteBackExpr wb = service.parseWriteBack(json);

        assertNotNull(wb);
        assertEquals("PaymentItem", wb.getSrcObjectType());
        assertEquals("sum(amount)", wb.getExpression());
        assertNull(wb.getIdField());
        assertNull(wb.getCondition());
        assertNull(wb.getExecutingMoment());
        assertNull(wb.getValidateExpr());
    }

    @Test
    void parseWriteBack_arrayFormat_firstElementParsed() {
        String json = "[{\"srcObjectType\":\"InvoiceItem\",\"idField\":\"orderId\","
                + "\"expression\":\"sum(qty)\",\"executingMoment\":\"APPROVED\"}]";

        WriteBackExpr wb = service.parseWriteBack(json);

        assertNotNull(wb);
        assertEquals("InvoiceItem", wb.getSrcObjectType());
        assertEquals("orderId", wb.getIdField());
        assertEquals("sum(qty)", wb.getExpression());
        assertEquals("APPROVED", wb.getExecutingMoment());
    }

    @Test
    void parseWriteBack_singleQuoteJson_fallbackParsed() {
        String json = "{'srcObjectType':'RevenueItem','expression':'sum(amount)',"
                + "'idField':'contractId','executingMoment':'ALWAYS'}";

        WriteBackExpr wb = service.parseWriteBack(json);

        assertNotNull(wb);
        assertEquals("RevenueItem", wb.getSrcObjectType());
        assertEquals("contractId", wb.getIdField());
        assertEquals("ALWAYS", wb.getExecutingMoment());
    }

    @Test
    void parseWriteBack_nullOrEmpty_returnsNull() {
        assertNull(service.parseWriteBack(null));
        assertNull(service.parseWriteBack(""));
        assertNull(service.parseWriteBack("   "));
    }

    @Test
    void parseWriteBack_invalidJson_returnsNull() {
        assertNull(service.parseWriteBack("not a json"));
        assertNull(service.parseWriteBack("{broken"));
    }
}
