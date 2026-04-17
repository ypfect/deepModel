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
        + "        \"code\": \"0100RE5320250900004\",\n"
        + "        \"rcfAmount\": -58301.89,\n"
        + "        \"billFullStatus\": \"BillStatus.effective\",\n"
        + "        \"revenueAmount\": 0,\n"
        + "        \"businessTypeId\": \"0H3L8K501JR0053\",\n"
        + "        \"originAmount\": -60000,\n"
        + "        \"businessDate\": \"2026-03-31 16:00:00\",\n"
        + "        \"accountingMethodId\": \"AccountingMethod.revenueConfirmationInvoice\",\n"
        + "        \"approvedUserId\": \"X3ECB060C200027\",\n"
        + "        \"createdTime\": \"2025-09-05 09:29:21.920706\",\n"
        + "        \"id\": \"RWCJHC642UX009T\",\n"
        + "        \"taxAmount\": -3396.23\n"
        + "      }\n"
        + "    ]\n"
        + "  },\n"
        + "  \"errors\": []\n"
        + "}";

    // ============================================================
    // ★ 配置区：按需修改
    // ============================================================
    private static final String URL = "http://baseapp.cn-apnorthbj-1.77hub.com/baseapp/flow/invoiceToRevenueConfirmation";
//    private static final String URL = "http://baseapp.cn-northwest-4.77hub.com/baseapp/flow/invoiceToRevenueConfirmation";
    private static final String TENANT_ID = "C2M3BN505E80001";

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
