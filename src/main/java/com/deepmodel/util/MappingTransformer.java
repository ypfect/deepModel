package com.deepmodel.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * 核销关系映射转换器
 * 查询数据库，处理映射配置，控制台输出结果
 */
@Service
public class MappingTransformer {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    // 内存对象支持的字段集合
    private static final Set<String> SUPPORTED_FIELDS = new LinkedHashSet<>(Arrays.asList(
        "currencyId", "businessDate", "createdOrgId", "exchangeRate", 
        "isSameCurrency", "verifyRelationId", "verificationTypeId", "exchangeId",
        "debitDeclaredAmount", "creditDeclaredAmount", 
        "originDebitDeclaredAmount", "originCreditDeclaredAmount"
    ));
    
    public String run() throws Exception {
        StringBuilder result = new StringBuilder();
        result.append("=== 核销关系映射转换器 ===\n\n");
        
        // 查询数据库 - mappings是jsonb字段，需要转换为text
        String sql = "SELECT id, mappings::text as mappings FROM baseapp_verify_relation " +
                    "WHERE verify_system_id IN ('FT58KL5015F0001', 'FT58KL5015F0002', 'FT58KL5015F0003') " +
                    "ORDER BY verify_system_id";
        
        result.append("执行SQL: ").append(sql).append("\n");
        result.append("----------------------------------------\n\n");
        
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            
            result.append("查询到 ").append(results.size()).append(" 条记录\n\n");
            
            int successCount = 0;
            int failCount = 0;
            
            for (Map<String, Object> row : results) {
                String id = (String) row.get("id");
                String mappings = (String) row.get("mappings");
                
                try {
                    String transformedMappings = transformMapping(mappings);
                    
                    result.append("ID: ").append(id).append("\n");
                    result.append("结果: ").append(transformedMappings).append("\n");
                    result.append("----------------------------------------\n\n");
                    
                    successCount++;
                    
                } catch (Exception e) {
                    result.append("转换失败 ID: ").append(id).append(", 错误: ").append(e.getMessage()).append("\n");
                    result.append("----------------------------------------\n\n");
                    failCount++;
                }
            }
            
            result.append("=== 处理完成 ===\n");
            result.append("总记录数: ").append(results.size()).append("\n");
            result.append("成功: ").append(successCount).append("\n");
            result.append("失败: ").append(failCount);
            
        } catch (Exception e) {
            result.append("数据库查询失败: ").append(e.getMessage()).append("\n");
            e.printStackTrace();
        }
        
        return result.toString();
    }
    
    /**
     * 转换单个映射配置
     * @param mappingJson 原始的映射JSON字符串
     * @return 转换后的映射JSON字符串
     */
    private String transformMapping(String mappingJson) throws IOException {
        if (mappingJson == null || mappingJson.trim().isEmpty()) {
            return mappingJson;
        }
        
        // 解析JSON为Map
        LinkedHashMap<String, Object> originalMapping;
        try {
            originalMapping = OBJECT_MAPPER.readValue(mappingJson, 
                new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new IOException("JSON解析失败: " + e.getMessage());
        }
        
        // 转换后的新映射
        LinkedHashMap<String, Object> newMapping = new LinkedHashMap<>();
        
        // 遍历原映射，处理所有字段
        for (Map.Entry<String, Object> entry : originalMapping.entrySet()) {
            String fieldName = entry.getKey();
            
            // 检查是否为支持的字段
            if (SUPPORTED_FIELDS.contains(fieldName)) {
                // 支持的字段转换为${head}.fieldName格式
                newMapping.put(fieldName, "${head}." + fieldName);
            } else {
                // 不支持的字段保持原值不变
                newMapping.put(fieldName, entry.getValue());
            }
        }
        
        // 确保添加 exchangeId 字段（如果还没有的话）
        if (!newMapping.containsKey("exchangeId")) {
            newMapping.put("exchangeId", "${head}.exchangeId");
        }
        
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(newMapping);
    }
    
    /**
     * 用于测试的main方法
     */
    public static void main(String[] args) {
        System.out.println("请使用 Spring Boot 应用启动:");
        System.out.println("mvn spring-boot:run");
        System.out.println("或者在IDE中运行 RelationApplication 主类");
    }
}
