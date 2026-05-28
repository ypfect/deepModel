package com.deepmodel.relation.service;

import com.deepmodel.relation.dao.MetadataRepository;
import com.deepmodel.relation.model.WriteBackExpr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * WriteBackSqlGenerator 单元测试。
 * 使用 Mockito mock repository 和 impactAnalyzerService，仅验证 SQL 模板组装逻辑。
 */
class WriteBackSqlGeneratorTest {

    private WriteBackSqlGenerator generator;
    private MetadataRepository mockRepository;
    private ImpactAnalyzerService mockImpact;

    @BeforeEach
    void setUp() {
        mockRepository = Mockito.mock(MetadataRepository.class);
        mockImpact = Mockito.mock(ImpactAnalyzerService.class);

        when(mockRepository.selectAppNameByObjectType(anyString())).thenReturn("");
        when(mockImpact.getFieldInfo(anyString(), anyString())).thenReturn(null);

        generator = new WriteBackSqlGenerator(mockImpact, mockRepository);
    }

    @Test
    void generateSql_basicSum_correctTemplate() {
        WriteBackExpr wb = new WriteBackExpr();
        wb.setSrcObjectType("ArInvoiceItem");
        wb.setIdField("contractId");
        wb.setExpression("sum(amount)");

        String sql = generator.generateSql("ArContract", "invoicedAmount", wb);

        assertNotNull(sql);
        assertTrue(sql.contains("UPDATE ar_contract m"));
        assertTrue(sql.contains("SET invoiced_amount ="));
        assertTrue(sql.contains("FROM ar_invoice_item"));
        assertTrue(sql.contains("contract_id = m.id"));
        assertTrue(sql.contains("is_deleted = false"));
    }

    @Test
    void generateSql_withCondition_conditionIncluded() {
        WriteBackExpr wb = new WriteBackExpr();
        wb.setSrcObjectType("PaymentItem");
        wb.setIdField("orderId");
        wb.setExpression("sum(paidAmount)");
        wb.setCondition("billStatus='APPROVED'");

        String sql = generator.generateSql("SalesOrder", "totalPaid", wb);

        assertNotNull(sql);
        assertTrue(sql.contains("bill_status='APPROVED'"));
    }

    @Test
    void generateSql_multiFieldAggregation_expressionPreserved() {
        WriteBackExpr wb = new WriteBackExpr();
        wb.setSrcObjectType("OrderItem");
        wb.setIdField("contractId");
        wb.setExpression("sum(quantity * unitPrice)");

        String sql = generator.generateSql("ArContract", "totalAmount", wb);

        assertNotNull(sql);
        assertTrue(sql.contains("sum(quantity * unit_price)") || sql.contains("sum(quantity*unit_price)"));
    }

    @Test
    void generateSql_nullWriteBackExpr_returnsNull() {
        String sql = generator.generateSql("ArContract", "amount", null);
        assertNull(sql);
    }

    @Test
    void generateSql_incompleteWriteBackExpr_returnsNull() {
        WriteBackExpr wb = new WriteBackExpr();
        wb.setSrcObjectType("SomeObject");
        String sql = generator.generateSql("ArContract", "amount", wb);
        assertNull(sql);
    }

    @Test
    void generateSql_cascadeIdField_usesFirstSegment() {
        WriteBackExpr wb = new WriteBackExpr();
        wb.setSrcObjectType("InvoiceItem");
        wb.setIdField("contractId.id");
        wb.setExpression("sum(amount)");

        String sql = generator.generateSql("ArContract", "invoicedAmount", wb);

        assertNotNull(sql);
        assertTrue(sql.contains("contract_id = m.id"));
    }

    @Test
    void generateSql_noIdField_defaultInferred() {
        WriteBackExpr wb = new WriteBackExpr();
        wb.setSrcObjectType("InvoiceItem");
        wb.setExpression("sum(qty)");

        String sql = generator.generateSql("ArContract", "totalQty", wb);

        assertNotNull(sql);
        assertTrue(sql.contains("ar_contract_id = m.id"));
    }
}
