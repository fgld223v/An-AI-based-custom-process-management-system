package com.aiflow.integration;

import com.aiflow.entity.UserEntity;
import com.aiflow.mapper.SysUserMapper;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.TaskAuthorizationService;
import com.aiflow.service.WorkflowNotificationService;
import com.aiflow.service.WorkflowRoleService;
import com.aiflow.service.impl.ApproverResolverServiceImpl;
import com.aiflow.service.impl.BpmnXmlEnhancer;
import com.aiflow.service.impl.SingleAssigneeListener;
import com.aiflow.service.impl.TaskCreatedNotificationListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SameDepartmentRoleApprovalFlowableIntegrationTest {

    private static final Long BUSINESS_INSTANCE_ID = 300L;
    private static final Long TEMPLATE_ID = 400L;
    private static final Long APPLICANT_ID = 10L;
    private static final Long FIRST_APPROVER_ID = 20L;
    private static final Long SECOND_APPROVER_ID = 21L;
    private static final Long DEPARTMENT_ID = 6L;

    private ProcessEngine processEngine;
    private RuntimeService runtimeService;
    private TaskService taskService;
    private WorkflowNotificationService notificationService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ProcessTemplateRepository processTemplateRepository = mock(ProcessTemplateRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        WorkflowRoleService workflowRoleService = mock(WorkflowRoleService.class);
        notificationService = mock(WorkflowNotificationService.class);

        com.aiflow.model.ProcessInstance businessInstance = com.aiflow.model.ProcessInstance.builder()
                .id(BUSINESS_INSTANCE_ID)
                .templateId(TEMPLATE_ID)
                .applicantId(APPLICANT_ID)
                .status("running")
                .deleted(0)
                .build();
        ProcessTemplate template = ProcessTemplate.builder()
                .id(TEMPLATE_ID)
                .nodeConfig(nodeConfig())
                .deleted(0)
                .build();
        UserEntity applicant = activeUser(APPLICANT_ID, "applicant");

        when(processInstanceRepository.findByIdAndDeleted(BUSINESS_INSTANCE_ID, 0))
                .thenReturn(Optional.of(businessInstance));
        when(processTemplateRepository.findByIdAndDeleted(TEMPLATE_ID, 0))
                .thenReturn(Optional.of(template));
        when(sysUserMapper.selectById(APPLICANT_ID)).thenReturn(applicant);
        when(workflowRoleService.resolveActiveUserIds("DEPT_REVIEWER", DEPARTMENT_ID))
                .thenReturn(List.of(FIRST_APPROVER_ID, SECOND_APPROVER_ID));

        ApproverResolverServiceImpl resolver = new ApproverResolverServiceImpl(
                processInstanceRepository, departmentRepository, sysUserMapper,
                objectMapper, workflowRoleService);
        SingleAssigneeListener assigneeListener = new SingleAssigneeListener(
                resolver, processTemplateRepository, objectMapper);

        StandaloneInMemProcessEngineConfiguration configuration =
                new StandaloneInMemProcessEngineConfiguration();
        configuration.setJdbcUrl("jdbc:h2:mem:same-department-role-approval;DB_CLOSE_DELAY=-1");
        configuration.setJdbcDriver("org.h2.Driver");
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setBeans(Map.of(
                "singleAssigneeListener", assigneeListener,
                "taskCreatedNotificationListener", new TaskCreatedNotificationListener(notificationService)));
        processEngine = configuration.buildProcessEngine();
        runtimeService = processEngine.getRuntimeService();
        taskService = processEngine.getTaskService();

        String enhanced = new BpmnXmlEnhancer(objectMapper).enhance(bpmnXml(), nodeConfig());
        processEngine.getRepositoryService().createDeployment()
                .name("same-department-role-approval")
                .addString("same-department-role-approval.bpmn20.xml", enhanced)
                .deploy();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (processEngine != null) processEngine.close();
    }

    @Test
    void roleMembersReceiveCandidateTaskAndFirstClaimantCompletesIt() {
        org.flowable.engine.runtime.ProcessInstance flowableInstance =
                runtimeService.startProcessInstanceByKey("sameDepartmentRoleApproval", Map.of(
                        "businessInstanceId", BUSINESS_INSTANCE_ID,
                        "templateId", TEMPLATE_ID,
                        "initiator", String.valueOf(APPLICANT_ID)));

        Task candidateTask = taskService.createTaskQuery()
                .processInstanceId(flowableInstance.getId()).singleResult();
        assertThat(candidateTask).isNotNull();
        assertThat(candidateTask.getAssignee()).isNull();
        assertThat(taskService.createTaskQuery().taskId(candidateTask.getId())
                .taskCandidateUser(String.valueOf(FIRST_APPROVER_ID)).count()).isEqualTo(1);
        assertThat(taskService.createTaskQuery().taskId(candidateTask.getId())
                .taskCandidateUser(String.valueOf(SECOND_APPROVER_ID)).count()).isEqualTo(1);
        verify(notificationService).notifyTaskCreated(candidateTask.getId(), BUSINESS_INSTANCE_ID,
                String.valueOf(FIRST_APPROVER_ID), "Department reviewer approval");
        verify(notificationService).notifyTaskCreated(candidateTask.getId(), BUSINESS_INSTANCE_ID,
                String.valueOf(SECOND_APPROVER_ID), "Department reviewer approval");

        TaskAuthorizationService authorizationService = new TaskAuthorizationService(taskService);
        authenticate(activeUser(30L, "outsider"));
        assertThatThrownBy(() -> authorizationService.requireOperableTask(candidateTask))
                .isInstanceOf(AccessDeniedException.class);

        authenticate(activeUser(FIRST_APPROVER_ID, "reviewer-one"));
        Task claimed = authorizationService.requireOperableTask(candidateTask);
        assertThat(claimed.getAssignee()).isEqualTo(String.valueOf(FIRST_APPROVER_ID));

        authenticate(activeUser(SECOND_APPROVER_ID, "reviewer-two"));
        assertThatThrownBy(() -> authorizationService.requireOperableTask(claimed))
                .isInstanceOf(AccessDeniedException.class);

        authenticate(activeUser(FIRST_APPROVER_ID, "reviewer-one"));
        taskService.complete(claimed.getId());
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(flowableInstance.getId()).singleResult()).isNull();
    }

    private void authenticate(UserEntity user) {
        com.aiflow.security.CurrentUser currentUser = new com.aiflow.security.CurrentUser(
                user, List.of(new SimpleGrantedAuthority("ROLE_NORMAL_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, null, currentUser.getAuthorities()));
    }

    private UserEntity activeUser(Long id, String username) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("-");
        user.setRole("USER");
        user.setSystemRole("normal_user");
        user.setDepartmentId(DEPARTMENT_ID);
        user.setEnabled(1);
        user.setDeleted(0);
        return user;
    }

    private String nodeConfig() {
        return """
                {
                  "Approve_Role": {
                    "nodeId": "Approve_Role",
                    "nodeKey": "Approve_Role",
                    "businessType": "approval",
                    "approvalMode": "SINGLE",
                    "assignStrategy": "ROLE_IN_APPLICANT_DEPT",
                    "assignValue": "DEPT_REVIEWER"
                  }
                }
                """;
    }

    private String bpmnXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  targetNamespace="http://aiflow.com/integration">
                  <bpmn:process id="sameDepartmentRoleApproval" isExecutable="true">
                    <bpmn:startEvent id="Start_1"><bpmn:outgoing>Flow_1</bpmn:outgoing></bpmn:startEvent>
                    <bpmn:userTask id="Approve_Role" name="Department reviewer approval">
                      <bpmn:incoming>Flow_1</bpmn:incoming><bpmn:outgoing>Flow_2</bpmn:outgoing>
                    </bpmn:userTask>
                    <bpmn:endEvent id="End_1"><bpmn:incoming>Flow_2</bpmn:incoming></bpmn:endEvent>
                    <bpmn:sequenceFlow id="Flow_1" sourceRef="Start_1" targetRef="Approve_Role" />
                    <bpmn:sequenceFlow id="Flow_2" sourceRef="Approve_Role" targetRef="End_1" />
                  </bpmn:process>
                </bpmn:definitions>
                """;
    }
}
