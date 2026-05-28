package com.deepmodel.relation.dao;

import com.deepmodel.relation.model.BaseappObjectField;
import com.deepmodel.relation.model.ObjectTypeMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开发态本地 PostgreSQL 元数据读取（配置体检中心等场景，未部署环境时用）。
 */
@Repository
public class LocalPostgresMetadataRepository {

    private static final Logger log = LoggerFactory.getLogger(LocalPostgresMetadataRepository.class);

    @Value("${metadata-local.host:localhost}")
    private String host;

    @Value("${metadata-local.port:5432}")
    private int port;

    @Value("${metadata-local.database:testapp}")
    private String database;

    @Value("${metadata-local.username:postgres}")
    private String username;

    @Value("${metadata-local.password:123}")
    private String password;

    public Map<String, Object> connectionInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("host", host);
        info.put("port", port);
        info.put("database", database);
        info.put("username", username);
        info.put("jdbcUrl", jdbcUrl());
        return info;
    }

    public String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    public List<String> selectDistinctAppNames() {
        String sql = """
                select distinct app_name
                from baseapp_object_type
                where app_name is not null and trim(app_name) != ''
                order by app_name
                """;
        return queryStrings(sql);
    }

    public List<BaseappObjectField> selectAll() {
        String sql = """
                select f.id,
                       f.object_type,
                       f.name,
                       f.api_name,
                       f.title,
                       f.type,
                       f.biz_type,
                       f.expression,
                       f.trigger_expr,
                       f.virtual_expr,
                       f.write_back_expr::text as write_back_expr,
                       f.refer_info::text as refer_info,
                       f.source_info::text as source_info,
                       f.enum_type,
                       f.is_disabled,
                       f.is_customized_field,
                       t.app_name
                from baseapp_object_field f
                left join baseapp_object_type t on f.object_type = t.name
                """;
        return queryFields(sql);
    }

    public List<ObjectTypeMeta> selectObjectTitles() {
        String sql = """
                select name, title, type, description, is_disabled, app_name,
                       is_customized_entity, is_detail, is_tree, is_multi_data_version,
                       is_support_change_bill
                from baseapp_object_type
                """;
        List<ObjectTypeMeta> result = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ObjectTypeMeta meta = new ObjectTypeMeta();
                meta.setName(rs.getString("name"));
                meta.setTitle(rs.getString("title"));
                meta.setType(rs.getString("type"));
                meta.setDescription(rs.getString("description"));
                meta.setIsDisabled(rs.getObject("is_disabled") == null ? null : rs.getBoolean("is_disabled"));
                meta.setAppName(rs.getString("app_name"));
                meta.setIsCustomizedEntity(rs.getObject("is_customized_entity") == null ? null : rs.getBoolean("is_customized_entity"));
                meta.setIsDetail(rs.getObject("is_detail") == null ? null : rs.getBoolean("is_detail"));
                meta.setIsTree(rs.getObject("is_tree") == null ? null : rs.getBoolean("is_tree"));
                meta.setIsMultiDataVersion(rs.getObject("is_multi_data_version") == null ? null : rs.getBoolean("is_multi_data_version"));
                meta.setIsSupportChangeLog(rs.getObject("is_support_change_bill") == null ? null : rs.getBoolean("is_support_change_bill"));
                result.add(meta);
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
        return result;
    }

    public List<String> selectEnumDefinitions() {
        String sql = """
                select m.content::text as content
                from baseapp_system_metadata m
                where m.is_deleted = false
                  and lower(m.type_id) like '%enum%'
                  and m.content is not null
                """;
        return queryStrings(sql, "content");
    }

    public List<String> selectViewDefinitions() {
        String sql = """
                select m.content::text as content
                from baseapp_system_metadata m
                join baseapp_object_type t on m.name = t.name
                where m.name like '%View%'
                  and m.is_deleted = false
                  and m.type_id = 'MetaType.entity'
                  and coalesce(t.type, '') = 'bill'
                  and lower(coalesce(t.app_name, '')) in ('arap','purchase', 'sales', 'contract')
                """;
        return queryStrings(sql, "content");
    }

    public List<String> selectChangeBillSupportedEntities() {
        String sql = """
                select m.name
                from baseapp_system_metadata m
                where m.is_deleted = false
                  and m.type_id = 'MetaType.entity'
                  and m.content::jsonb->>'isSupportChangeBill' = 'true'
                """;
        return queryStrings(sql, "name");
    }

    public List<Map<String, Object>> selectEntityMetadataContents() {
        String sql = """
                select m.name, m.content::text as content
                from baseapp_system_metadata m
                where m.is_deleted = false
                  and m.type_id = 'MetaType.entity'
                  and m.content is not null
                """;
        return queryNameContent(sql);
    }

    public List<Map<String, Object>> selectCustomizedMetadataContents() {
        String sql = """
                select m.name, m.content::text as content
                from baseapp_customized_metadata m
                where m.is_deleted = false
                  and m.type_id = 'MetaType.entity'
                  and m.content is not null
                """;
        return queryNameContent(sql);
    }

    /** 探测连接是否可用（用于友好错误提示）。 */
    public void ping() {
        try (Connection conn = connect()) {
            try (PreparedStatement ps = conn.prepareStatement("select 1");
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("ping 无结果");
                }
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl(), username, password);
    }

    private List<String> queryStrings(String sql) {
        return queryStrings(sql, null);
    }

    private List<String> queryStrings(String sql, String column) {
        String col = column != null ? column : findSingleStringColumn(sql);
        List<String> result = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String v = rs.getString(col);
                if (v != null && !v.isBlank()) {
                    result.add(v);
                }
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
        return result;
    }

    private static String findSingleStringColumn(String sql) {
        if (sql.contains("app_name")) {
            return "app_name";
        }
        if (sql.contains(" as content") || sql.contains("::text as content")) {
            return "content";
        }
        return "name";
    }

    private List<Map<String, Object>> queryNameContent(String sql) {
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", rs.getString("name"));
                row.put("content", rs.getString("content"));
                result.add(row);
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
        return result;
    }

    private List<BaseappObjectField> queryFields(String sql) {
        List<BaseappObjectField> result = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                BaseappObjectField f = new BaseappObjectField();
                f.setId(rs.getString("id"));
                f.setObjectType(rs.getString("object_type"));
                f.setName(rs.getString("name"));
                f.setApiName(rs.getString("api_name"));
                f.setTitle(rs.getString("title"));
                f.setType(rs.getString("type"));
                f.setBizType(rs.getString("biz_type"));
                f.setExpression(rs.getString("expression"));
                f.setTriggerExpr(rs.getString("trigger_expr"));
                f.setVirtualExpr(rs.getString("virtual_expr"));
                f.setWriteBackExpr(rs.getString("write_back_expr"));
                f.setReferInfo(rs.getString("refer_info"));
                f.setSourceInfo(rs.getString("source_info"));
                f.setEnumType(rs.getString("enum_type"));
                f.setIsDisabled(rs.getObject("is_disabled") == null ? null : rs.getBoolean("is_disabled"));
                f.setIsCustomizedField(rs.getObject("is_customized_field") == null ? null : rs.getBoolean("is_customized_field"));
                f.setAppName(rs.getString("app_name"));
                result.add(f);
            }
        } catch (SQLException e) {
            throw wrap(e);
        }
        log.info("[LocalPG] loaded {} object fields from {}", result.size(), jdbcUrl());
        return result;
    }

    private RuntimeException wrap(SQLException e) {
        String msg = "连接本地 PostgreSQL 失败 (" + jdbcUrl() + ", user=" + username + "): " + e.getMessage();
        log.warn(msg);
        return new RuntimeException(msg, e);
    }
}
