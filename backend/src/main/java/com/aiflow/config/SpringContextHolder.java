package com.aiflow.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 容器上下文持有者。
 * <p>
 * 通过实现 {@link ApplicationContextAware} 接口，在 Spring 初始化完成后
 * 将 {@link ApplicationContext} 静态缓存，使得非 Spring 管理的类（如工具类）
 * 也能通过静态方法获取容器中的 Bean。
 * </p>
 * <p>
 * 典型用法：{@code SpringContextHolder.getBean("someBean", SomeClass.class)}
 * </p>
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    /** 静态持有 Spring 应用上下文，容器启动后赋值 */
    private static ApplicationContext applicationContext;

    /**
     * Spring 容器回调：在 Bean 属性设置完成后注入 ApplicationContext。
     *
     * @param applicationContext Spring 应用上下文
     * @throws BeansException 注入异常时抛出
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContextHolder.applicationContext = applicationContext;
    }

    /**
     * 从 Spring 容器中按名称和类型获取 Bean。
     *
     * @param beanName     Bean 的名称
     * @param requiredType Bean 的类型
     * @param <T>          Bean 类型泛型
     * @return 容器中匹配的 Bean 实例
     * @throws IllegalStateException 当容器尚未就绪时抛出
     */
    public static <T> T getBean(String beanName, Class<T> requiredType) {
        if (applicationContext == null) {
            throw new IllegalStateException("Spring application context is not ready");
        }
        return applicationContext.getBean(beanName, requiredType);
    }
}
