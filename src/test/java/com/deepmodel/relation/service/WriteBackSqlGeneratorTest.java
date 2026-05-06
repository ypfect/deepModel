package com.deepmodel.relation.service;

import com.deepmodel.relation.dao.BaseappObjectFieldMapper;
import com.deepmodel.relation.model.WriteBackExpr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * WriteBackSqlGenerator 单元测试。
 * 使用 Mockito mock mapper 和 impactAnalyzerService，仅验证 SQL 模板组装逻辑。
 */
class WriteBackSqlGeneratorTest {

    private WriteBackSqlGenerator generator;
    private BaseappObjectFieldMapper mockMapper;
    private ImpactAnalyzerService mockImpact;

    @BeforeEach
    void setUp() {
        mockMapper = Mockito.mock(BaseappObjectFieldMapper.class);
        mockImpact = Mockito.mock(ImpactAnalyzerService.class);

        // 默认 appName 返回空（无前缀）
        when(mockMapper.selectAppNameByObjectType(anyString())).thenReturn("");
        // 默认 fieldInfo 返回 null（使用 camelToSnake 转换）
        when(mockImpact.getFieldInfo(anyString(), anyString())).thenReturn(null);

        generator = new WriteBackSqlGenerator(mockImpact, mockMapper);
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
        assertTrue(sql.contains("AND bill_status='APPROVED'"));
    }

    @Test
    void generateSql_multiFieldAggregation_expressionPreserved() {
        WriteBackExpr wb = new WriteBackExpr();
        wb.setSrcObjectType("OrderItem");
        wb.setIdField("contractId");
        wb.setExpression("sum(quantity * unitPrice)");

        String sql = generator.generateSql("ArContract", "totalAmount", wb);

        assertNotNull(sql);
        // expression 中的字段应被转为 snake_case
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
        // expression 为空
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
        // 级联路径只取第一段 contractId → contract_id
        assertTrue(sql.contains("contract_id = m.id"));
    }

    @Test
    void generateSql_noIdField_defaultInferred() {
        WriteBackExpr wb = new WriteBackExpr();
        wb.setSrcObjectType("InvoiceItem");
        // idField 为空，应从目标对象名推导：ArContract → arContractId → ar_contract_id
        wb.setExpression("sum(qty)");

        String sql = generator.generateSql("ArContract", "totalQty", wb);

        assertNotNull(sql);
        assertTrue(sql.contains("ar_contract_id = m.id"));
    }
}
