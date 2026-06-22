package com.aiflow.security;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.Properties;

/**
 * MyBatis 数据权限拦截器 — 业务管理员自动注入 biz_type_id IN (...) 过滤条件。
 *
 * <p>拦截所有查询 process_instance 表的 SQL，
 * 当当前用户为 biz_admin 且有 managedBizTypeIds 时，
 * 自动追加 {@code AND biz_type_id IN (1,2,3)} 条件。</p>
 *
 * <p>注意：不注入 SysUserMapper（避免与 MyBatis 初始化产生循环依赖），
 * managedBizTypeIds 直接从 SecurityContext 中的 CurrentUser 读取。</p>
 */
@Slf4j
@Component
@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class DataPermissionInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = PluginUtils.realTarget(invocation.getTarget());
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
        MappedStatement mappedStatement = getMappedStatement(metaObject);

        // 只拦截查询语句
        if (mappedStatement.getSqlCommandType() != org.apache.ibatis.mapping.SqlCommandType.SELECT) {
            return invocation.proceed();
        }

        BoundSql boundSql = statementHandler.getBoundSql();
        String sql = boundSql.getSql().toLowerCase();

        // 只拦截 process_instance 表查询
        if (!sql.contains("from process_instance") && !sql.contains("from `process_instance`")) {
            return invocation.proceed();
        }

        // 获取当前用户
        String managedBizTypeIds = getCurrentUserManagedBizTypeIds();
        if (managedBizTypeIds == null || managedBizTypeIds.isBlank() || "[]".equals(managedBizTypeIds.trim())) {
            return invocation.proceed();
        }

        // 解析业务类型ID列表
        String inClause = parseBizTypeIds(managedBizTypeIds);
        if (inClause == null) return invocation.proceed();

        // 追加 WHERE 条件
        String newSql = appendBizTypeFilter(sql, inClause);
        PluginUtils.mpBoundSql(boundSql).sql(newSql);
        log.debug("DataPermissionInterceptor: 已追加 biz_type_id IN ({})", inClause);

        return invocation.proceed();
    }

    private MappedStatement getMappedStatement(MetaObject metaObject) {
        if (metaObject.hasGetter("delegate.mappedStatement")) {
            return (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        }
        if (metaObject.hasGetter("mappedStatement")) {
            return (MappedStatement) metaObject.getValue("mappedStatement");
        }
        throw new IllegalStateException("unsupported MyBatis StatementHandler: "
                + metaObject.getOriginalObject().getClass().getName());
    }

    private String appendBizTypeFilter(String sql, String inClause) {
        String condition = "biz_type_id IN (" + inClause + ")";
        if (sql.contains("where")) {
            return sql + " AND " + condition;
        } else {
            // 在 ORDER BY / GROUP BY / LIMIT 之前插入 WHERE
            int orderIdx = sql.indexOf("order by");
            int groupIdx = sql.indexOf("group by");
            int limitIdx = sql.indexOf("limit");
            int insertIdx = sql.length();
            if (orderIdx > 0) insertIdx = Math.min(insertIdx, orderIdx);
            if (groupIdx > 0) insertIdx = Math.min(insertIdx, groupIdx);
            if (limitIdx > 0) insertIdx = Math.min(insertIdx, limitIdx);
            return sql.substring(0, insertIdx) + " WHERE " + condition + " " + sql.substring(insertIdx);
        }
    }

    private String getCurrentUserManagedBizTypeIds() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return null;
            Object principal = auth.getPrincipal();
            if (!(principal instanceof CurrentUser currentUser)) return null;

            String systemRole = currentUser.getSystemRole();
            if (!"biz_admin".equals(systemRole)) return null;

            // 直接从 CurrentUser（UserEntity）读取，无需查库，避免循环依赖
            return currentUser.getManagedBizTypeIds();
        } catch (Exception e) {
            log.warn("DataPermissionInterceptor: 获取用户信息失败", e);
            return null;
        }
    }

    private String parseBizTypeIds(String managedBizTypeIds) {
        try {
            // 格式: "[1, 2, 3]" 或 "[1]"
            String cleaned = managedBizTypeIds.trim()
                    .replace("[", "")
                    .replace("]", "")
                    .replace("\"", "");
            if (cleaned.isBlank()) return null;
            String[] ids = cleaned.split(",");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ids.length; i++) {
                String id = ids[i].trim();
                if (id.matches("\\d+")) {
                    if (i > 0) sb.append(",");
                    sb.append(id);
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        } catch (Exception e) {
            log.warn("解析 managedBizTypeIds 失败: {}", managedBizTypeIds, e);
            return null;
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
