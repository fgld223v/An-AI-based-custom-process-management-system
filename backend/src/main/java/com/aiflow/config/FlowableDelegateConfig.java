package com.aiflow.config;

import com.aiflow.repository.ProcessTemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.task.service.delegate.TaskListener;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.ProcessEngineConfigurationConfigurer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 三重保障：
 * <ol>
 *   <li>将 Spring 容器中所有 JavaDelegate / TaskListener / ExecutionListener Bean
 *       自动注册到 Flowable beans map，确保各类 delegateExpression 均可解析</li>
 *   <li>启动时修复已部署模板中的 ${systemActionDelegate} → ${true}，
 *       <b>并同步更新 ProcessTemplate 记录</b>，确保新实例使用修正后的定义</li>
 *   <li>@Lazy 打破与 RepositoryService 之间的循环依赖</li>
 * </ol>
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

    /**
     * 自动发现并注册所有委托 Bean 到 Flowable beans map。
     */
    @Override
    public void configure(SpringProcessEngineConfiguration config) {
        if (config.getBeans() == null) {
            config.setBeans(new java.util.HashMap<>());
        }

        Map<String, JavaDelegate> javaDelegates = applicationContext.getBeansOfType(JavaDelegate.class);
        for (Map.Entry<String, JavaDelegate> entry : javaDelegates.entrySet()) {
            config.getBeans().put(entry.getKey(), entry.getValue());
            log.info("已注册 JavaDelegate Bean [{}] → Flowable beans map", entry.getKey());
        }

        Map<String, TaskListener> taskListeners = applicationContext.getBeansOfType(TaskListener.class);
        for (Map.Entry<String, TaskListener> entry : taskListeners.entrySet()) {
            config.getBeans().put(entry.getKey(), entry.getValue());
            log.info("已注册 TaskListener Bean [{}] → Flowable beans map", entry.getKey());
        }

        Map<String, ExecutionListener> executionListeners = applicationContext.getBeansOfType(ExecutionListener.class);
        for (Map.Entry<String, ExecutionListener> entry : executionListeners.entrySet()) {
            config.getBeans().put(entry.getKey(), entry.getValue());
            log.info("已注册 ExecutionListener Bean [{}] → Flowable beans map", entry.getKey());
        }
    }

    /**
     * 启动时遍历所有已部署的流程定义，将 BPMN XML 中的
     * delegateExpression="${systemActionDelegate}" 替换为
     * flowable:expression="${true}"，
     * <b>并同步更新 ProcessTemplate 记录指向新部署</b>。
     */
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

                String fixedXml = bpmnXml.replace(
                        "flowable:delegateExpression=\"${systemActionDelegate}\"",
                        "flowable:expression=\"${true}\"");

                if (!fixedXml.equals(bpmnXml)) {
                    String oldDefinitionId = pd.getId();

                    // 重新部署修正后的 BPMN
                    Deployment deployment = repositoryService.createDeployment()
                            .name(pd.getName() + "-fixed")
                            .key(pd.getKey())
                            .addString(pd.getResourceName(), fixedXml)
                            .deploy();

                    // 获取新部署的 ProcessDefinition
                    ProcessDefinition newPd = repositoryService.createProcessDefinitionQuery()
                            .deploymentId(deployment.getId())
                            .singleResult();

                    // 更新 ProcessTemplate 记录，使其指向修正后的 Flowable 定义
                    if (newPd != null) {
                        processTemplateRepository.findByFlowableProcessDefinitionId(oldDefinitionId)
                                .ifPresent(template -> {
                                    template.setFlowableProcessDefinitionId(newPd.getId());
                                    template.setFlowableDeploymentId(deployment.getId());
                                    template.setBpmnXml(fixedXml);
                                    processTemplateRepository.save(template);
                                    templateUpdated.incrementAndGet();
                                    log.info("已更新模板 [{}] 的 Flowable 定义 ID: {} → {}",
                                            template.getTemplateCode(), oldDefinitionId, newPd.getId());
                                });
                    }

                    fixed++;
                    log.info("已修复流程定义 [{}] 中的 systemActionDelegate 引用", pd.getKey());
                }
            } catch (Exception e) {
                log.warn("修复流程定义 [{}] 失败: {}", pd.getKey(), e.getMessage());
            }
        }
        if (fixed > 0) {
            log.info("共修复 {} 个包含 systemActionDelegate 的流程定义，更新 {} 个模板记录", fixed, templateUpdated.get());
        }
    }
}
