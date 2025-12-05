package com.deepmodel.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MappingProcessor {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Set<String> EXCLUDED_KEYS = new LinkedHashSet<>(Arrays.asList(
            "currencyId","settleCurrencyId","originCurrencyId",
            "originExchangeRate","settleExchangeRate","exchangeRate",
            "verExchangeRate","settleVerExchangeRate",
            "originDebitDeclaredAmount","originCreditDeclaredAmount",
            "billDebitDeclaredAmount","billCreditDeclaredAmount",
            "debitDeclaredAmount","creditDeclaredAmount",
            "debitDeclaredAmountSelf","creditDeclaredAmountSelf",
            "midDebitDeclaredAmount","midCreditDeclaredAmount"
    ));

    private MappingProcessor() {}

    /**
     * 输入为一个以逗号分隔的字符串：'DebitCreditDirection.debit', '{...json...}'
     */
    public static String process(String input) throws IOException {
        if (input == null || !input.contains(",")) {
            throw new IllegalArgumentException("输入格式不正确，应为 'DebitCreditDirection.xxx', '{...}'");
        }
        int firstComma = input.indexOf(',');
        String directionPart = input.substring(0, firstComma).trim();
        String jsonPart = input.substring(firstComma + 1).trim();

        // 去除外围引号
        directionPart = trimQuotes(directionPart);
        jsonPart = trimQuotes(jsonPart);

        return processByDirection(directionPart, jsonPart);
    }

    /**
     * 直接传入方向（包含 'debit' 或 'credit'）与 JSON mapping 字符串
     */
    public static String process(String direction, String mappingJson, boolean pretty) throws IOException {
        String result = processByDirection(direction, mappingJson);
        if (!pretty) return result;
        // 重新格式化输出
        @SuppressWarnings("unchecked") Map<String, Object> map = OBJECT_MAPPER.readValue(result, Map.class);
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(map);
    }

    public static String process(String direction, String mappingJson) throws IOException {
        return process(direction, mappingJson, true);
    }

    private static String processByDirection(String direction, String mappingJson) throws IOException {
        boolean isCredit = direction != null && direction.toLowerCase().contains("credit");
        boolean isDebit = direction != null && direction.toLowerCase().contains("debit");
        if (!isCredit && !isDebit) {
            throw new IllegalArgumentException("方向必须包含 'credit' 或 'debit'");
        }

        // 读取为有序 Map，保持原字段相对顺序
        LinkedHashMap<String, Object> mapping = OBJECT_MAPPER.readValue(
                mappingJson,
                new TypeReference<LinkedHashMap<String, Object>>() {}
        );

        // 1) 删除指定字段
        mapping.entrySet().removeIf(e -> EXCLUDED_KEYS.contains(e.getKey()));

        // 3) 在非排除字段中替换 value 里的 ${billDeclaredAmount}
        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            String key = entry.getKey();
            if (EXCLUDED_KEYS.contains(key)) continue; // 已经删过，这里只是保护
            Object value = entry.getValue();
            if (value instanceof String) {
                String strVal = (String) value;
                String replaced = strVal.replace("${billDeclaredAmount}", "${ret}.originDeclaredAmount");
                entry.setValue(replaced);
            }
        }

        // 2) 根据借贷方向追加字段
        LinkedHashMap<String, Object> toAppend = new LinkedHashMap<>();
        if (isCredit) {
            toAppend.put("currencyId", "${ret}.midCurrencyId");
            toAppend.put("settleCurrencyId", "${ret}.settleCurrencyId");
            toAppend.put("originCurrencyId", "${ret}.transCurrencyId");
            toAppend.put("originExchangeRate", "${ret}.transExchangeRate");
            toAppend.put("settleExchangeRate", "${ret}.settleExchangeRate");
            toAppend.put("exchangeRate", "${ret}.midExchangeRate");
            toAppend.put("verExchangeRate", "${ret}.trans2MidExchangeRate");
            toAppend.put("settleVerExchangeRate", "${ret}.settle2MidExchangeRate");
            toAppend.put("originDebitDeclaredAmount", "null");
            toAppend.put("originCreditDeclaredAmount", "${ret}.originDeclaredAmount");
            toAppend.put("billDebitDeclaredAmount", "${ret}.billDeclaredAmount");
            toAppend.put("billCreditDeclaredAmount", "null");
            toAppend.put("debitDeclaredAmount", "null");
            toAppend.put("creditDeclaredAmount", "${ret}.declaredAmount");
            toAppend.put("debitDeclaredAmountSelf", "null");
            toAppend.put("creditDeclaredAmountSelf", "${ret}.declaredAmountSelf");
            toAppend.put("midDebitDeclaredAmount", "null");
            toAppend.put("midCreditDeclaredAmount", "${ret}.midDeclaredAmount");
        } else { // debit
            toAppend.put("currencyId", "${ret}.midCurrencyId");
            toAppend.put("settleCurrencyId", "${ret}.settleCurrencyId");
            toAppend.put("originCurrencyId", "${ret}.transCurrencyId");
            toAppend.put("originExchangeRate", "${ret}.transExchangeRate");
            toAppend.put("settleExchangeRate", "${ret}.settleExchangeRate");
            toAppend.put("exchangeRate", "${ret}.midExchangeRate");
            toAppend.put("verExchangeRate", "${ret}.trans2MidExchangeRate");
            toAppend.put("settleVerExchangeRate", "${ret}.settle2MidExchangeRate");
            toAppend.put("originDebitDeclaredAmount", "${ret}.originDeclaredAmount");
            toAppend.put("originCreditDeclaredAmount", "null");
            toAppend.put("billDebitDeclaredAmount", "${ret}.billDeclaredAmount");
            toAppend.put("billCreditDeclaredAmount", "$null");
            toAppend.put("debitDeclaredAmount", "${ret}.declaredAmount");
            toAppend.put("creditDeclaredAmount", "null");
            toAppend.put("debitDeclaredAmountSelf", "${ret}.declaredAmountSelf");
            toAppend.put("creditDeclaredAmountSelf", "null");
            toAppend.put("midDebitDeclaredAmount", "${ret}.midDeclaredAmount");
            toAppend.put("midCreditDeclaredAmount", "null");
        }

        // 追加到结果末尾
        for (Map.Entry<String, Object> e : toAppend.entrySet()) {
            mapping.put(e.getKey(), e.getValue());
        }

        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(mapping);
    }

    private static String trimQuotes(String s) {
        if (s == null || s.length() < 2) return s;
        char first = s.charAt(0);
        char last = s.charAt(s.length() - 1);
        if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /**
     * 本地快速验证：直接运行该 main 即可看到格式化后的结果。
     */
    public static void main(String[] args) throws IOException {
        String direction = "DebitCreditDirection.debit";
//        String direction = "DebitCreditDirection.credit";
        String mappingJson = "{\"auxQty\": \"${isLast}?doDeclaredAuxQty:precise(auxQty==null?0:(auxQty * (${billDeclaredAmount}/originAmount)), getUnitScale($data, auxUnitId))\", \"unitId\": \"unitId\", \"baseQty\": \"${isLast} ? doDeclaredBaseQty :precise(baseQty==null? 0 : (baseQty * (${billDeclaredAmount}/originAmount)), getUnitScale($data, baseUnitId))\", \"objectId\": \"revenueConfirmation.settleCustomerId\", \"quantity\": \"${isLast}?doDeclaredQuantity:precise(quantity==null?0:(quantity * (${billDeclaredAmount}/originAmount)), getUnitScale($data, unitId))\", \"auxUnitId\": \"auxUnitId\", \"productId\": \"productId\", \"projectId\": \"projectId\", \"revenueId\": \"revenueId\", \"unitPrice\": \"price\", \"baseUnitId\": \"baseUnitId\", \"contractId\": \"contractId\", \"currencyId\": \"${verifyCurrencyId}\", \"objectType\": \"'Customer'\", \"salesOrgId\": \"revenueConfirmation.salesOrgId\", \"ownerUserId\": \"revenueConfirmation.ownerUserId\", \"srcObjectId\": \"revenueConfirmationId\", \"transAuxQty\": \"${isLast}?doDeclaredTransAuxQty:precise(transAuxQty==null?0:(transAuxQty * (${billDeclaredAmount}/originAmount)), getUnitScale($data, transAuxUnitId))\", \"businessDate\": \"revenueConfirmation.businessDate\", \"departmentId\": \"revenueConfirmation.departmentId\", \"exchangeRate\": \"${exchangeRate}\", \"projectOrgId\": \"projectOrgId\", \"salesIssueId\": \"${party}.salesIssueId != null ? ${party}.salesIssueId  : salesIssueId\", \"salesOrderId\": \"salesOrderId\", \"srcObjectType\": \"'RevenueConfirmation'\", \"transObjectId\": \"revenueConfirmation.customerId\", \"financialOrgId\": \"revenueConfirmation.createdOrgId\", \"projectStageId\": \"projectStageId\", \"transAuxUnitId\": \"transAuxUnitId\", \"srcItemObjectId\": \"id\", \"verExchangeRate\": \"${verExchangeRate}\", \"arFundArapTypeId\": \"null\", \"originCurrencyId\": \"revenueConfirmation.currencyId\", \"partySrcObjectId\": \"${party}.partyId\", \"salesIssueItemId\": \"${party}.salesIssueItemId != null ? ${party}.salesIssueItemId  : salesIssueItemId\", \"salesOrderItemId\": \"salesOrderItemId\", \"srcItemObjectType\": \"'RevenueConfirmationItem'\", \"contractObjectType\": \"contractObjectType\", \"originExchangeRate\": \"revenueConfirmation.exchangeRate\", \"partySrcObjectType\": \"${party}.entityName\", \"debitDeclaredAmount\": \"${declaredAmount}\", \"srcBusinessObjectId\": \"revenueConfirmationId\", \"unitPriceWithoutTax\": \"priceWithoutTax\", \"creditDeclaredAmount\": \"null\", \"partySrcItemObjectId\": \"${party}.id\", \"salesTraceContractId\": \"salesTraceContractId\", \"srcPartySalesIssueId\": \"salesIssueId\", \"srcBusinessObjectType\": \"'RevenueConfirmation'\", \"partySrcItemObjectType\": \"${party}.entityItemName\", \"billDebitDeclaredAmount\": \"${billDeclaredAmount}\", \"billCreditDeclaredAmount\": \"null\", \"srcPartySalesIssueItemId\": \"salesIssueItemId\", \"originDebitDeclaredAmount\": \"${originDeclaredAmount}\", \"originCreditDeclaredAmount\": \"null\", \"contractSubjectMatterItemId\": \"contractSubjectMatterItemId\", \"salesTraceContractObjectType\": \"contractObjectType\", \"contractSubjectMatterItemObjectType\": \"contractSubjectMatterItemObjectType\", \"salesTraceContractSubjectMatterItemId\": \"salesTraceContractSubjectMatterItemId\", \"salesTraceContractSubjectMatterItemObjectType\": \"contractSubjectMatterItemObjectType\"}";

        String result = process(direction, mappingJson, true);
        System.out.println(result);
    }
}
