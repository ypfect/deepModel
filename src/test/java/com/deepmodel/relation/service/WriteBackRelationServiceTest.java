package com.deepmodel.relation.service;

import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.CascadeWriteBackInfo;
import com.deepmodel.relation.model.WriteBackRelationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class WriteBackRelationServiceTest {

    private WriteBackRelationService service;

    @BeforeEach
    void setUp() {
        service = new WriteBackRelationService();
    }

    private BaseappObjectField createField(String objectType, String name, String writeBackExpr) {
        BaseappObjectField f = new BaseappObjectField();
        f.setObjectType(objectType);
        f.setName(name);
        f.setWriteBackExpr(writeBackExpr);
        return f;
    }

    @Test
    void buildIndex_basicWriteBack_createsCorrectIndex() {
        String wbe = "{\"srcObjectType\":\"ArInvoiceItem\",\"expression\":\"sum(invoice_amount)\","
                + "\"idField\":\"contract_id\",\"condition\":\"bill_status_id = 'BillStatus.effective'\"}";
        List<BaseappObjectField> rows = Arrays.asList(
                createField("ArContract", "invoicedAmount", wbe));

        service.buildIndex(rows);

        Map<String, Set<WriteBackRelationInfo>> result = service.getWriteBackExprFields("ArInvoiceItem");
        assertFalse(result.isEmpty());
        assertTrue(result.containsKey("ArContract"));
        Set<WriteBackRelationInfo> infos = result.get("ArContract");
        assertEquals(1, infos.size());
        WriteBackRelationInfo info = infos.iterator().next();
        assertEquals("invoicedAmount", info.getTargetFieldName());
        assertEquals("sum(invoice_amount)", info.getExpression());
    }

    @Test
    void buildIndex_emptyData_returnsEmptyMaps() {
        service.buildIndex(Collections.emptyList());

        assertTrue(service.getWriteBackExprFields("Anything").isEmpty());
        assertTrue(service.getWriteBackFieldVars("Anything").isEmpty());
        assertTrue(service.getCascadeWriteBackInfo("Anything").isEmpty());
    }

    @Test
    void buildIndex_invalidJson_skipsAndContinues() {
        List<BaseappObjectField> rows = Arrays.asList(
                createField("ArContract", "bad", "{invalid json}"),
                createField("ArContract", "invoicedAmount",
                        "{\"srcObjectType\":\"ArInvoiceItem\",\"expression\":\"sum(amount)\",\"idField\":\"contractId\"}"));

        service.buildIndex(rows);

        // 第一条无效被跳过，第二条正常解析
        Map<String, Set<WriteBackRelationInfo>> result = service.getWriteBackExprFields("ArInvoiceItem");
        assertEquals(1, result.size());
    }

    @Test
    void getWriteBackFieldVars_extractsSourceVars() {
        String wbe = "{\"srcObjectType\":\"ArInvoiceItem\",\"expression\":\"sum(invoice_amount)\","
                + "\"idField\":\"contract_id\",\"condition\":\"bill_status_id = 'BillStatus.effective'\"}";
        service.buildIndex(Arrays.asList(createField("ArContract", "invoicedAmount", wbe)));

        Map<String, Set<String>> vars = service.getWriteBackFieldVars("ArContract");
        assertFalse(vars.isEmpty());
        assertTrue(vars.containsKey("invoicedAmount"));
        Set<String> fieldVars = vars.get("invoicedAmount");
        assertTrue(fieldVars.contains("invoiceAmount")); // snake→camel
        assertTrue(fieldVars.contains("contractId"));     // idField
        assertTrue(fieldVars.contains("billStatusId"));   // from condition
    }

    @Test
    void buildIndex_cascadeWriteBack_detected() {
        // A (ArInvoiceItem) 回写 B (ArContract.invoicedAmount)
        String wbeAB = "{\"srcObjectType\":\"ArInvoiceItem\",\"expression\":\"sum(amount)\",\"idField\":\"contractId\"}";
        // B (ArContract) 回写 C (ArFrameContract.totalInvoicedAmount)，条件中引用 invoicedAmount
        String wbeBC = "{\"srcObjectType\":\"ArContract\",\"expression\":\"sum(invoiced_amount)\",\"idField\":\"frameContractId\"}";

        service.buildIndex(Arrays.asList(
                createField("ArContract", "invoicedAmount", wbeAB),
                createField("ArFrameContract", "totalInvoicedAmount", wbeBC)));

        List<CascadeWriteBackInfo> cascades = service.getCascadeWriteBackInfo("ArInvoiceItem");
        assertFalse(cascades.isEmpty());
        CascadeWriteBackInfo c = cascades.get(0);
        assertEquals("ArInvoiceItem", c.getSrcObjectType());
        assertEquals("ArContract", c.getTargetObjectType());
        assertEquals("invoicedAmount", c.getTargetFieldName());
        assertEquals("ArFrameContract", c.getCascadeTargetObjectType());
    }

    @Test
    void buildIndex_nullWriteBackExpr_skipped() {
        List<BaseappObjectField> rows = Arrays.asList(
                createField("ArContract", "name", null),
                createField("ArContract", "title", ""));

        service.buildIndex(rows);
        assertTrue(service.getWriteBackExprFields("ArContract").isEmpty());
    }

    @Test
    void getWriteBackExprFields_unknownObject_returnsEmptyMap() {
        service.buildIndex(Collections.emptyList());
        Map<String, Set<WriteBackRelationInfo>> result = service.getWriteBackExprFields("NonExistent");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
