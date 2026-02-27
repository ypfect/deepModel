package com.deepmodel.relation.util;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * description:
 * @author pengfyu
 * @date 2025/3/7 10:23
 */
public class BatchRequest {

  public static void main(String[] args) throws Exception {
    String ids = "XVGHMD6480800ED,NNHHMD6480800BN,02FH0E648AK00US,04VC9A648KM001S,050HWD648080083,059HND64808005V,06F0UD64808004E,0FE4PD6480800D7,0FJGXD648080068,0QRNRD64808004N,0WN5PD64808004A,12MGXD6480800C4,1KHHMD64808007W,1KHHMD6480800F8,22WMTD64808008V,2BLXSD6480800DT,35C0UD64808009N,36R9LD648080083,3LG4PD6480800DX,3P22TD64808009D,3UXGWD64808006A,3XAKMD6480800A9,421HWD64808008R,43MJVD64808004K,44E0UD6480800E8,4JLGXD64808003N,4RMULD6480800BH,4RP4CB648KM0048,61MJVD6480800EW,6507AB648KM001D,682HWD64808007Q,682HWD6480800BR,6AH4PD648080023,6RM7LD6480800BW,7DF0UD64808003V,7SKXSD648080078,80G4PD648080052,852LLD648080068,87VMTD6480800AB,87VMTD6480800CT,8CLGXD6480800EW,8DXMTD6480800GQ,8ED4PD6480800F4,8FE4PD6480800EE,9042TD64808002V,91F0UD6480800A5,9HK7LD64808001K,9MB4PD648080001,9NHQQD64808003C,9R12TD64808004K,9SXLHB648KM00EP,ADXGWD64808002K,AQLTCB648KM001C,ATHHMD648080057,AUBHMD6480800A7,AXR9LD64808006T,B1E4PD6480800FL,B6R9LD6480800C5,B8LTCB648KM0096,BJVHXD64808001R,C040SD64808002D,C0K4PD64808000K,CGNP1E648AK00AC,CND0UD648080002,CND0UD648080053,CPJ2HD64808003B,CV6WLD64808000V,DNJSQD6480800BJ,E5D4PD6480800E2,EATATD64808000M,EATATD648080042,ECLHMD64808000L,EE20JB648KM009N,F0HHMD64808008G,FB73A361RSQ0067,FG22TD648080062,FWFQGD64808002A,G1WTV9648KM00C2,G5CHMD648080089,GC7RND64808007J,GCDSQD64808000R,GEKHMD64808002V,GQ0HWD648LJ0069,HCHHMD6480800M4,J484PD6480800CW,J6KTCB648KM00D1,JAEH0E648AK00DJ,JBBHMD6480800F3,JXLJXD6480800F5,K4D4PD648080063,KHWGWD64808001P,KLQBUD648080086,KLWGWD648LJ00CU,KMLHMD648080015,L0NCQD6480800CJ,LTJ4PD648080035,M7E0UD6480800BW,MDJ40E6480800E7,MLBKMD64808000R,MMD0UD6480800C6,N7F70E6480800WP,NCGERD6480800C9,NH052B648KM00C4,NM8HND64808001A,NP6HND64808008U,P2HERD64808000Q,P3CGMD6480800F8,P6JQQD64808000C,PAS04C648KM0003,PBD4PD64808003P,PTR6PD6480800D5,PUE0UD6480800EJ,PVE4PD64808006L,Q19HND64808000V,R0SVLD6480800E2,R1WEQD648080054,R2D70E6480800BQ,R33HWD648080058,R3KXSD648080057,R3KXSD648080075,R639UD64808007L,RDRS0E648AK00AF,RFUEQD64808009X,RKTATD64808006H,RSQNRD6480800FB,RTGHMD64808001N,S161RD6480800B4,S2J4PD6480800D0,SE29UD6480800D1,ST5WLD6480800BN,SVG4PD6480800HQ,SVG4PD6480800NH,SXR2RD648080041,T4LXSD6480800AK,TDR7LD648080097,TFHHMD64808006F,TN29LD6480800C9,TQHJND64808001N,TURNRD64808005E,TURNRD6480800BQ,U4CGMD648080097,UG6RND6480800FK,UWCN69648KM001R,V0S6PD6480800EH,W5E0UD64808000U,W5E0UD6480800EQ,WDU04C648KM008S,WE6BUD64808006E,WH4M2C648KM008J,XEL5PD6480800E4,XJ3A3C648KM00D9,XV2HWD64808006L,XVGHMD64808001U";
//    String ids="DNJSQD6480800BJ";
    String[] split = ids.split(",");
    List<String> idsP = Arrays.asList(split);
    int total = idsP.size();
    int success = 0;
    int failed = 0;
    StringBuilder failedIds = new StringBuilder();

    OkHttpClient client = new OkHttpClient();
    MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    int index = 0;
    for (String s : idsP) {
      index++;
      String url = "http://arap.cn-apnorthbj-4.77hub.com/arap/flow/verifyBillWithSourceInfo";
//      String url = "http://arap.test-tx-19.e7link.com/arap/flow/verifyBillWithSourceInfo";

      Map<String, String> headers = new HashMap<String, String>();
      headers.put("Tenant-Id", "JCEJ8260G2700DD");

      // 按服务端要求改为 JSON 请求体
      String jsonBody = "{\"objectName\":\"Receipt\",\"objectId\":\"" + s + "\"}";
      RequestBody body = RequestBody.create(jsonBody, JSON);

      Request.Builder builder = new Request.Builder()
          .url(url)
          .post(body)
          .addHeader("Content-Type", "application/json");

      for (Map.Entry<String, String> entry : headers.entrySet()) {
        builder.addHeader(entry.getKey(), entry.getValue());
      }

      Request request = builder.build();

      try (Response response = client.newCall(request).execute()) {
        int code = response.code();
        double percent = (index * 100.0) / total;
        System.out.printf("(%d/%d %.2f%%) objectId=%s status=%d%n", index, total, percent, s, code);

        boolean ok = (code >= 200 && code < 300);
        if (ok) {
          success++;
        } else {
          failed++;
          if (failedIds.length() > 0) {
            failedIds.append(',');
          }
          failedIds.append(s);
        }

        if (response.body() != null) {
          System.out.println(response.body().string());
        } else {
          System.out.println("empty body");
        }
      } catch (Exception e) {
        failed++;
        if (failedIds.length() > 0) {
          failedIds.append(',');
        }
        failedIds.append(s);
        System.out.println("request error for objectId=" + s + ": " + e.getMessage());
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
