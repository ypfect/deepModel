package com.deepmodel.relation.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * description: 批量请求 invoiceToRevenueConfirmation 接口
 * 将查询结果 JSON 粘贴到 INPUT_JSON 变量中，直接运行即可。
 *
 * @author pengfyu
 * @date 2026/3/5
 */
public class BatchInvoiceToRevenueRequest {

    // ============================================================
    // ★ 将你的查询结果 JSON 粘贴到这里（data.Invoice 数组那一层）
    // ============================================================
    private static final String INPUT_JSON = "{\n"
        + "  \"data\": {\n"
        + "    \"Invoice\": [\n"
        + "      {\n"
        + "        \"accountingMethodId\": \"AccountingMethod.invoice\",\n"
        + "        \"approvedUserId\": \"ELQNUA60MRL00BX\",\n"
        + "        \"approvedTime\": \"2026-03-04 08:18:13\",\n"
        + "        \"createdTime\": \"2026-03-02 07:44:10.593869\",\n"
        + "        \"id\": \"H20TAE60M1T00AA\",\n"
        + "        \"revenueAmount\": 0,\n"
        + "        \"businessTypeId\": \"0H3L8K501JR0022\"\n"
        + "      },\n"
        + "      {\n"
        + "        \"accountingMethodId\": \"AccountingMethod.invoice\",\n"
        + "        \"approvedUserId\": \"0AX2KC60MJV00CB\",\n"
        + "        \"approvedTime\": \"2026-02-26 04:00:53\",\n"
        + "        \"createdTime\": \"2026-02-25 08:55:53.426778\",\n"
        + "        \"id\": \"FH089E60M1T005R\",\n"
        + "        \"revenueAmount\": 0,\n"
        + "        \"businessTypeId\": \"0H3L8K501JR0022\"\n"
        + "      },\n"
        + "      {\n"
        + "        \"accountingMethodId\": \"AccountingMethod.invoice\",\n"
        + "        \"approvedUserId\": \"0AX2KC60MJV00CB\",\n"
        + "        \"approvedTime\": \"2026-02-26 08:19:46\",\n"
        + "        \"createdTime\": \"2026-02-25 08:52:26.60652\",\n"
        + "        \"id\": \"7KW79E60M1T009T\",\n"
        + "        \"revenueAmount\": 0,\n"
        + "        \"businessTypeId\": \"0H3L8K501JR0022\"\n"
        + "      },\n"
        + "      {\n"
        + "        \"accountingMethodId\": \"AccountingMethod.invoice\",\n"
        + "        \"approvedUserId\": \"0AX2KC60MJV00CB\",\n"
        + "        \"approvedTime\": \"2026-02-26 03:52:29\",\n"
        + "        \"createdTime\": \"2026-02-25 09:10:03.617082\",\n"
        + "        \"id\": \"BD389E60MXP00CC\",\n"
        + "        \"revenueAmount\": 0,\n"
        + "        \"businessTypeId\": \"0H3L8K501JR0022\"\n"
        + "      },\n"
        + "      {\n"
        + "        \"accountingMethodId\": \"AccountingMethod.invoice\",\n"
        + "        \"approvedUserId\": \"0AX2KC60MJV00CB\",\n"
        + "        \"approvedTime\": \"2026-02-26 04:23:29\",\n"
        + "        \"createdTime\": \"2026-02-25 10:09:55.469074\",\n"
        + "        \"id\": \"Q2F89E60MXP00BL\",\n"
        + "        \"revenueAmount\": 0,\n"
        + "        \"businessTypeId\": \"0H3L8K501JR0022\"\n"
        + "      },\n"
        + "      {\n"
        + "        \"accountingMethodId\": \"AccountingMethod.invoice\",\n"
        + "        \"approvedUserId\": \"ELQNUA60MRL00BX\",\n"
        + "        \"approvedTime\": \"2026-03-04 08:42:04\",\n"
        + "        \"createdTime\": \"2026-03-02 07:45:59.093325\",\n"
        + "        \"id\": \"TF0TAE60M1T002X\",\n"
        + "        \"revenueAmount\": 0,\n"
        + "        \"businessTypeId\": \"0H3L8K501JR0022\"\n"
        + "      },\n"
        + "      {\n"
        + "        \"accountingMethodId\": \"AccountingMethod.invoice\",\n"
        + "        \"createdTime\": \"2026-03-02 07:32:40.163439\",\n"
        + "        \"id\": \"F8VSAE60M1T0079\",\n"
        + "        \"revenueAmount\": 0,\n"
        + "        \"businessTypeId\": \"0H3L8K501JR0022\"\n"
        + "      }\n"
        + "    ]\n"
        + "  },\n"
        + "  \"errors\": []\n"
        + "}";

    // ============================================================
    // ★ 配置区：按需修改
    // ============================================================
    private static final String URL = "http://baseapp.cn-northwest-4.77hub.com/baseapp/flow/invoiceToRevenueConfirmation";
    private static final String TENANT_ID = "WXDN4560GC1003A";

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(INPUT_JSON);
        JsonNode invoiceList = root.path("data").path("Invoice");

        int total = invoiceList.size();
        int success = 0;
        int failed = 0;
        StringBuilder failedIds = new StringBuilder();

        OkHttpClient client = new OkHttpClient();
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");

        int index = 0;
        for (JsonNode invoice : invoiceList) {
            index++;
            String objectId = invoice.path("id").asText();
            String businessTypeId = invoice.path("businessTypeId").asText();
            // lastUserId 来自 JSON 中的 approvedUserId，若不存在则为空字符串
            String lastUserId = invoice.path("approvedUserId").asText("");
            // requestId 每次唯一
            String requestId = "batch--" + objectId + "--" + System.currentTimeMillis();

            String jsonBody = "{"
                    + "\"objectName\":\"Invoice\","
                    + "\"objectId\":\"" + objectId + "\","
                    + "\"variables\":{"
                    + "\"objectId\":{\"value\":\"" + objectId + "\",\"type\":\"String\"},"
                    + "\"objectName\":{\"value\":\"Invoice\",\"type\":\"String\"},"
                    + "\"toObjectName\":{\"value\":\"RevenueConfirmation\",\"type\":\"String\"},"
                    + "\"businessTypeId\":{\"value\":\"" + businessTypeId + "\",\"type\":\"String\"},"
                    + "\"requestId\":{\"value\":\"" + requestId + "\",\"type\":\"String\"},"
                    + "\"lastUserId\":{\"value\":\"" + lastUserId + "\",\"type\":\"String\"}"
                    + "}"
                    + "}";

            RequestBody body = RequestBody.create(jsonBody, JSON);
            Request request = new Request.Builder()
                    .url(URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Tenant-Id", TENANT_ID)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                int code = response.code();
                double percent = (index * 100.0) / total;
                System.out.printf("(%d/%d %.2f%%) objectId=%s status=%d%n",
                        index, total, percent, objectId, code);

                if (code >= 200 && code < 300) {
                    success++;
                } else {
                    failed++;
                    if (failedIds.length() > 0)
                        failedIds.append(',');
                    failedIds.append(objectId);
                }

                if (response.body() != null) {
                    System.out.println(response.body().string());
                } else {
                    System.out.println("empty body");
                }
            } catch (Exception e) {
                failed++;
                if (failedIds.length() > 0)
                    failedIds.append(',');
                failedIds.append(objectId);
                System.out.println("request error for objectId=" + objectId + ": " + e.getMessage());
            }
        }

        System.out.println("====================================");
        System.out.println("总数: " + total);
        System.out.println("成功: " + success);
        System.out.println("失败: " + failed);
        if (failed > 0) {
            System.out.println("失败的 objectId 列表: " + failedIds);
        }
    }
}
