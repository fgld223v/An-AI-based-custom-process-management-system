package com.aiflow.config;

import com.aiflow.security.DataPermissionInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * MyBatis-Plus 配置。
 * DataPermissionInterceptor 在 ApplicationReadyEvent 后注册，使用 ObjectProvider 延迟获取 SqlSessionFactory。
 */
@Configuration
public class MybatisPlusConfig {

    private final ObjectProvider<SqlSessionFactory> sqlSessionFactoryProvider;
    private final DataPermissionInterceptor dataPermissionInterceptor;

    public MybatisPlusConfig(ObjectProvider<SqlSessionFactory> sqlSessionFactoryProvider,
                              DataPermissionInterceptor dataPermissionInterceptor) {
        this.sqlSessionFactoryProvider = sqlSessionFactoryProvider;
        this.dataPermissionInterceptor = dataPermissionInterceptor;
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 在应用完全启动后注册数据权限拦截器。
     * ObjectProvider 延迟获取 SqlSessionFactory，打破循环依赖。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerDataPermissionInterceptor() {
        sqlSessionFactoryProvider.forEach(factory ->
            factory.getConfiguration().addInterceptor(dataPermissionInterceptor)
        );
    }
}
