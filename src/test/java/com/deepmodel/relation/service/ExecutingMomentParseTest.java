package com.deepmodel.relation.service;

import com.deepmodel.relation.model.WriteBackExpr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * executingMoment 解析单元测试。
 * 验证 parseWriteBack 能正确提取各种格式的 executingMoment。
 */
class ExecutingMomentParseTest {

    private ImpactAnalyzerService service;

    @BeforeEach
    void setUp() {
        service = new ImpactAnalyzerService(null, null, null, null, null);
    }

    @Test
    void parseWriteBack_alwaysMoment_parsed() {
        String json = "{\"srcObjectType\":\"InvoiceItem\","
                + "\"expression\":\"sum(amount)\","
                + "\"executingMoment\":\"ALWAYS\"}";

        WriteBackExpr wb = service.parseWriteBack(json);

        assertNotNull(wb);
        assertEquals("ALWAYS", wb.getExecutingMoment());
    }

    @Test
    void parseWriteBack_billStatusMoment_parsed() {
        String json = "{\"srcObjectType\":\"InvoiceItem\","
                + "\"expression\":\"sum(amount)\","
                + "\"executingMoment\":\"billStatus='APPROVED'\"}";

        WriteBackExpr wb = service.parseWriteBack(json);

        assertNotNull(wb);
        assertEquals("billStatus='APPROVED'", wb.getExecutingMoment());
    }

    @Test
    void parseWriteBack_nullMoment_defaultNull() {
        String json = "{\"srcObjectType\":\"InvoiceItem\","
                + "\"expression\":\"sum(amount)\"}";

        WriteBackExpr wb = service.parseWriteBack(json);

        assertNotNull(wb);
        assertNull(wb.getExecutingMoment());
    }

    @Test
    void parseWriteBack_jsonMoment_parsed() {
        // JSON 格式的 executingMoment（如状态条件对象）
        String json = "{\"srcObjectType\":\"InvoiceItem\","
                + "\"expression\":\"sum(amount)\","
                + "\"executingMoment\":\"{\\\"billStatus\\\":[\\\"APPROVED\\\",\\\"COMPLETED\\\"]}\"}";

        WriteBackExpr wb = service.parseWriteBack(json);

        assertNotNull(wb);
        assertNotNull(wb.getExecutingMoment());
        assertTrue(wb.getExecutingMoment().contains("APPROVED"));
        assertTrue(wb.getExecutingMoment().contains("COMPLETED"));
    }

    @Test
    void parseWriteBack_emptyMoment_treated() {
        String json = "{\"srcObjectType\":\"InvoiceItem\","
                + "\"expression\":\"sum(amount)\","
                + "\"executingMoment\":\"\"}";

        WriteBackExpr wb = service.parseWriteBack(json);

        assertNotNull(wb);
        // 空字符串应被保留（调用方判断是否等价于 ALWAYS）
        assertEquals("", wb.getExecutingMoment());
    }
}
