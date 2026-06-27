package com.aiflow.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContextHolder.applicationContext = applicationContext;
    }

    public static <T> T getBean(String beanName, Class<T> requiredType) {
        if (applicationContext == null) {
            throw new IllegalStateException("Spring application context is not ready");
        }
        return applicationContext.getBean(beanName, requiredType);
    }
}
