package com.aiflow.security;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.PreparedStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class DataPermissionInterceptorTest {

    @Test
    void acceptsStatementHandlerAlreadyWrappedByAnotherMybatisPlugin() throws Throwable {
        Configuration configuration = new Configuration();
        String sql = "select * from sys_user where id = ?";
        SqlSource sqlSource = parameter -> new BoundSql(configuration, sql, null, parameter);
        MappedStatement mappedStatement = new MappedStatement.Builder(
                configuration, "com.aiflow.mapper.SysUserMapper.selectById",
                sqlSource, SqlCommandType.SELECT).build();
        BoundSql boundSql = sqlSource.getBoundSql(20L);
        StatementHandler target = new PreparedStatementHandler(
                mock(Executor.class), mappedStatement, 20L,
                RowBounds.DEFAULT, null, boundSql);
        StatementHandler proxied = (StatementHandler) Plugin.wrap(target, new ExistingPlugin());
        Method prepare = StatementHandler.class.getMethod("prepare", Connection.class, Integer.class);
        Invocation invocation = new Invocation(proxied, prepare, new Object[]{mock(Connection.class), null});

        assertThatCode(() -> new DataPermissionInterceptor().intercept(invocation))
                .doesNotThrowAnyException();
    }

    @Intercepts(@Signature(
            type = StatementHandler.class,
            method = "prepare",
            args = {Connection.class, Integer.class}
    ))
    private static class ExistingPlugin implements Interceptor {
        @Override
        public Object intercept(Invocation invocation) {
            return null;
        }
    }
}
