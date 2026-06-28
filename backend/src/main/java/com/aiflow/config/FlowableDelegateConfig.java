package com.aiflow.config;

import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.impl.CcNotificationDelegate;
import com.aiflow.service.impl.MultiInstanceAssigneeListener;
import com.aiflow.service.impl.SingleAssigneeListener;
import com.aiflow.service.impl.SystemActionDelegate;
import com.aiflow.service.impl.TaskCreatedNotificationListener;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.ProcessEngineConfigurationConfigurer;
import org.flowable.task.service.delegate.TaskListener;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.BeansException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flowable 委托 Bean 自动注册配置。
 *
 * <p>在引擎配置阶段自动扫描并注册所有 JavaDelegate、TaskListener、ExecutionListener
 * 的实现 Bean 到 Flowable beans map，确保 delegateExpression 能正确解析。</p>
 *
 * <p>启动时同时执行兼容性修复：将旧版 systemActionDelegate 引用替换为表达式。</p>
 */
@Slf4j
@Component
public class FlowableDelegateConfig implements ProcessEngineConfigurationConfigurer, CommandLineRunner {

    private final RepositoryService repositoryService;
    private final ApplicationContext applicationContext;
    private final ProcessTemplateRepository processTemplateRepository;

    public FlowableDelegateConfig(@Lazy RepositoryService repositoryService,
                                  ApplicationContext applicationContext,
                                  @Lazy ProcessTemplateRepository processTemplateRepository) {
        this.repositoryService = repositoryService;
        this.applicationContext = applicationContext;
        this.processTemplateRepository = processTemplateRepository;
    }

    @Override
    public void configure(SpringProcessEngineConfiguration config) {
        Map<Object, Object> existingBeans = config.getBeans() == null
                ? Map.of()
                : new HashMap<>(config.getBeans());
        Map<Object, Object> flowableBeans = new HashMap<>(existingBeans);
        config.setBeans(flowableBeans);

        Map<String, JavaDelegate> javaDelegates = applicationContext.getBeansOfType(JavaDelegate.class);
        for (Map.Entry<String, JavaDelegate> entry : javaDelegates.entrySet()) {
            Object candidate = unwrapProxyIfNeeded(entry.getValue(), JavaDelegate.class);
            config.getBeans().put(entry.getKey(), candidate);
            log.info("Registered JavaDelegate bean [{}] into Flowable beans map", entry.getKey());
        }

        Map<String, TaskListener> taskListeners = applicationContext.getBeansOfType(TaskListener.class);
        for (Map.Entry<String, TaskListener> entry : taskListeners.entrySet()) {
            Object candidate = unwrapProxyIfNeeded(entry.getValue(), TaskListener.class);
            config.getBeans().put(entry.getKey(), candidate);
            log.info("Registered TaskListener bean [{}] into Flowable beans map", entry.getKey());
        }

        Map<String, ExecutionListener> executionListeners = applicationContext.getBeansOfType(ExecutionListener.class);
        for (Map.Entry<String, ExecutionListener> entry : executionListeners.entrySet()) {
            Object candidate = unwrapProxyIfNeeded(entry.getValue(), ExecutionListener.class);
            config.getBeans().put(entry.getKey(), candidate);
            log.info("Registered ExecutionListener bean [{}] into Flowable beans map", entry.getKey());
        }

        registerRequiredFlowableBean(config, "systemActionDelegate", SystemActionDelegate.class, JavaDelegate.class);
        registerRequiredFlowableBean(config, "singleAssigneeListener", SingleAssigneeListener.class, TaskListener.class);
        registerRequiredFlowableBean(config, "multiInstanceAssigneeListener", MultiInstanceAssigneeListener.class, ExecutionListener.class);
        registerRequiredFlowableBean(config, "taskCreatedNotificationListener", TaskCreatedNotificationListener.class, TaskListener.class);
        registerRequiredFlowableBean(config, "ccNotificationDelegate", CcNotificationDelegate.class, JavaDelegate.class);
    }

    private void registerRequiredFlowableBean(SpringProcessEngineConfiguration config,
                                              String beanName,
                                              Class<?> expectedClass,
                                              Class<?> requiredInterface) {
        try {
            Object bean = applicationContext.getBean(beanName);
            Object candidate = unwrapProxyIfNeeded(bean, requiredInterface);
            if (!expectedClass.isInstance(candidate) && !requiredInterface.isInstance(candidate)) {
                log.warn("Flowable delegate bean [{}] type mismatch: actual={}, required={}",
                        beanName, candidate.getClass().getName(), requiredInterface.getName());
            }
            config.getBeans().put(beanName, candidate);
            log.info("Registered required Flowable bean [{}] ({})", beanName, candidate.getClass().getName());
        } catch (BeansException ex) {
            log.warn("Required Flowable bean [{}] was not found: {}", beanName, ex.getMessage());
        }
    }

    private Object unwrapProxyIfNeeded(Object bean, Class<?> requiredInterface) {
        if (requiredInterface.isInstance(bean)) {
            return bean;
        }
        if (bean instanceof Advised advised) {
            try {
                Object target = advised.getTargetSource().getTarget();
                if (target != null && requiredInterface.isInstance(target)) {
                    return target;
                }
            } catch (Exception ex) {
                log.warn("Failed to unwrap Flowable delegate proxy {}: {}", bean.getClass().getName(), ex.getMessage());
            }
        }
        return bean;
    }

    @Override
    public void run(String... args) {
        List<ProcessDefinition> definitions = repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .list();

        int fixed = 0;
        java.util.concurrent.atomic.AtomicInteger templateUpdated = new java.util.concurrent.atomic.AtomicInteger(0);
        for (ProcessDefinition pd : definitions) {
            try {
                byte[] modelBytes = repositoryService.getProcessModel(pd.getId()).readAllBytes();
                String bpmnXml = new String(modelBytes, StandardCharsets.UTF_8);
                if (!bpmnXml.contains("systemActionDelegate")) continue;

                String fixedXml = replaceSystemActionDelegate(bpmnXml);
                if (!fixedXml.equals(bpmnXml)) {
                    String oldDefinitionId = pd.getId();

                    Deployment deployment = repositoryService.createDeployment()
                            .name(pd.getName() + "-fixed")
                            .key(pd.getKey())
                            .addString(pd.getResourceName(), fixedXml)
                            .deploy();

                    ProcessDefinition newPd = repositoryService.createProcessDefinitionQuery()
                            .deploymentId(deployment.getId())
                            .singleResult();

                    if (newPd != null) {
                        processTemplateRepository.findByFlowableProcessDefinitionId(oldDefinitionId)
                                .ifPresent(template -> {
                                    template.setFlowableProcessDefinitionId(newPd.getId());
                                    template.setFlowableDeploymentId(deployment.getId());
                                    template.setBpmnXml(fixedXml);
                                    processTemplateRepository.save(template);
                                    templateUpdated.incrementAndGet();
                                    log.info("Updated template [{}] Flowable definition id: {} -> {}",
                                            template.getTemplateCode(), oldDefinitionId, newPd.getId());
                                });
                    }

                    fixed++;
                    log.info("Fixed systemActionDelegate reference in process definition [{}]", pd.getKey());
                }
            } catch (Exception e) {
                log.warn("Failed to fix process definition [{}]: {}", pd.getKey(), e.getMessage());
            }
        }
        if (fixed > 0) {
            log.info("Fixed {} process definitions containing systemActionDelegate and updated {} templates",
                    fixed, templateUpdated.get());
        }
    }

    private String replaceSystemActionDelegate(String bpmnXml) {
        return bpmnXml
                .replace("flowable:delegateExpression=\"${systemActionDelegate}\"", "flowable:expression=\"${true}\"")
                .replace("flowable:delegateExpression='${systemActionDelegate}'", "flowable:expression=\"${true}\"")
                .replace("activiti:delegateExpression=\"${systemActionDelegate}\"", "flowable:expression=\"${true}\"")
                .replace("activiti:delegateExpression='${systemActionDelegate}'", "flowable:expression=\"${true}\"")
                .replace("delegateExpression=\"${systemActionDelegate}\"", "flowable:expression=\"${true}\"")
                .replace("delegateExpression='${systemActionDelegate}'", "flowable:expression=\"${true}\"");
    }
}
