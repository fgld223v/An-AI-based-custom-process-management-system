package com.aiflow.config;

import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.ApproverResolverService;
import com.aiflow.service.WorkflowNotificationService;
import com.aiflow.service.impl.CcNotificationDelegate;
import com.aiflow.service.impl.MultiInstanceAssigneeListener;
import com.aiflow.service.impl.SingleAssigneeListener;
import com.aiflow.service.impl.SystemActionDelegate;
import com.aiflow.service.impl.TaskCreatedNotificationListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.service.delegate.TaskListener;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FlowableDelegateConfigTest {

    @Test
    void configureRegistersRequiredDelegateBeansByName() {
        GenericApplicationContext context = new GenericApplicationContext();
        ProcessTemplateRepository processTemplateRepository = mock(ProcessTemplateRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();

        context.getBeanFactory().registerSingleton("systemActionDelegate", new SystemActionDelegate());
        context.getBeanFactory().registerSingleton("singleAssigneeListener",
                new SingleAssigneeListener(mock(ApproverResolverService.class), processTemplateRepository, objectMapper));
        context.getBeanFactory().registerSingleton("multiInstanceAssigneeListener", new MultiInstanceAssigneeListener());
        context.getBeanFactory().registerSingleton("taskCreatedNotificationListener",
                new TaskCreatedNotificationListener(mock(WorkflowNotificationService.class)));
        context.getBeanFactory().registerSingleton("ccNotificationDelegate", new CcNotificationDelegate());
        context.refresh();

        try {
            FlowableDelegateConfig configurer = new FlowableDelegateConfig(
                    mock(RepositoryService.class), context, processTemplateRepository);
            SpringProcessEngineConfiguration configuration = new SpringProcessEngineConfiguration();

            configurer.configure(configuration);

            assertThat(configuration.getBeans()).isNotNull();
            assertThat(configuration.getBeans().get("systemActionDelegate")).isInstanceOf(JavaDelegate.class);
            assertThat(configuration.getBeans().get("singleAssigneeListener")).isInstanceOf(TaskListener.class);
            assertThat(configuration.getBeans().get("multiInstanceAssigneeListener")).isInstanceOf(ExecutionListener.class);
            assertThat(configuration.getBeans().get("taskCreatedNotificationListener")).isInstanceOf(TaskListener.class);
            assertThat(configuration.getBeans().get("ccNotificationDelegate")).isInstanceOf(JavaDelegate.class);
        } finally {
            context.close();
        }
    }
}
