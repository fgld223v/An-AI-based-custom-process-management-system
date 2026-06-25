package com.aiflow.service.impl;

import com.aiflow.entity.UserEntity;
import com.aiflow.model.FormSubmission;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.FormSubmissionRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.security.CurrentUser;
import com.aiflow.service.FlowableRuntimeService;
import com.aiflow.service.ProcessAuthorizationService;
import com.aiflow.service.ProcessTimelineService;
import com.aiflow.service.RuleEvaluatorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessInstanceServiceImplTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void applicantCanReadDiagramForOwnProcessInstance() {
        authenticate(10L);
        ProcessInstanceRepository instanceRepository = mock(ProcessInstanceRepository.class);
        FormSubmissionRepository submissionRepository = mock(FormSubmissionRepository.class);
        ProcessTemplateRepository templateRepository = mock(ProcessTemplateRepository.class);
        ProcessAuthorizationService authorizationService = mock(ProcessAuthorizationService.class);
        FlowableRuntimeService flowableRuntimeService = mock(FlowableRuntimeService.class);
        RuleEvaluatorService ruleEvaluatorService = mock(RuleEvaluatorService.class);
        TaskService taskService = mock(TaskService.class);
        FormBindConfigParser formBindConfigParser = mock(FormBindConfigParser.class);
        ProcessTimelineService timelineService = mock(ProcessTimelineService.class);

        ProcessInstance instance = ProcessInstance.builder()
                .id(100L)
                .templateId(200L)
                .applicantId(10L)
                .deleted(0)
                .build();
        ProcessTemplate template = ProcessTemplate.builder()
                .id(200L)
                .templateName("Leave request")
                .bpmnXml("<definitions><process id=\"leave\" /></definitions>")
                .deleted(0)
                .build();

        when(instanceRepository.findByIdAndDeleted(100L, 0)).thenReturn(Optional.of(instance));
        when(templateRepository.findByIdAndDeleted(200L, 0)).thenReturn(Optional.of(template));

        ProcessInstanceServiceImpl service = new ProcessInstanceServiceImpl(
                instanceRepository, submissionRepository, templateRepository,
                authorizationService, flowableRuntimeService, ruleEvaluatorService,
                taskService, new ObjectMapper(), formBindConfigParser, timelineService);

        var result = service.getDiagram(100L);

        assertThat(result.getTemplateId()).isEqualTo(200L);
        assertThat(result.getTemplateName()).isEqualTo("Leave request");
        assertThat(result.getBpmnXml()).contains("process id=\"leave\"");
        verify(templateRepository).findByIdAndDeleted(200L, 0);
    }

    @Test
    void submittingMultiInstanceProcessAcceptsMultipleActiveTasks() {
        authenticate(10L);
        ProcessInstanceRepository instanceRepository = mock(ProcessInstanceRepository.class);
        FormSubmissionRepository submissionRepository = mock(FormSubmissionRepository.class);
        ProcessTemplateRepository templateRepository = mock(ProcessTemplateRepository.class);
        ProcessAuthorizationService authorizationService = mock(ProcessAuthorizationService.class);
        FlowableRuntimeService flowableRuntimeService = mock(FlowableRuntimeService.class);
        RuleEvaluatorService ruleEvaluatorService = mock(RuleEvaluatorService.class);
        TaskService taskService = mock(TaskService.class);
        TaskQuery taskQuery = mock(TaskQuery.class, RETURNS_SELF);
        FormBindConfigParser formBindConfigParser = mock(FormBindConfigParser.class);
        ProcessTimelineService timelineService = mock(ProcessTimelineService.class);

        ProcessInstance instance = ProcessInstance.builder()
                .id(100L)
                .templateId(200L)
                .applicantId(10L)
                .instanceCode("PI-100")
                .title("Department role approval")
                .status("draft")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deleted(0)
                .build();
        FormSubmission submission = FormSubmission.builder()
                .id(1L)
                .processInstanceId(100L)
                .status("draft")
                .deleted(0)
                .build();
        Task firstTask = mock(Task.class);
        Task secondTask = mock(Task.class);

        when(instanceRepository.findByIdAndDeleted(100L, 0)).thenReturn(Optional.of(instance));
        when(submissionRepository.findByProcessInstanceIdAndDeletedOrderByUpdatedAtDescCreatedAtDesc(100L, 0))
                .thenReturn(List.of(submission));
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(firstTask, secondTask));
        doAnswer(invocation -> {
            instance.setStatus("running");
            instance.setFlowableProcessInstanceId("flowable-100");
            return null;
        }).when(flowableRuntimeService).startProcess(100L);

        ProcessInstanceServiceImpl service = new ProcessInstanceServiceImpl(
                instanceRepository, submissionRepository, templateRepository,
                authorizationService, flowableRuntimeService, ruleEvaluatorService,
                taskService, new ObjectMapper(), formBindConfigParser, timelineService);

        var result = service.submitInstance(100L);

        assertThat(result.getStatus()).isEqualTo("running");
        verify(ruleEvaluatorService).evaluateAndAutoComplete(instance);
        verify(taskQuery, never()).singleResult();
        verify(taskService, never()).setAssignee(anyString(), anyString());
    }

    private void authenticate(Long userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("applicant");
        user.setEnabled(1);
        user.setDeleted(0);
        CurrentUser currentUser = new CurrentUser(
                user, List.of(new SimpleGrantedAuthority("ROLE_NORMAL_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, null, currentUser.getAuthorities()));
    }
}
