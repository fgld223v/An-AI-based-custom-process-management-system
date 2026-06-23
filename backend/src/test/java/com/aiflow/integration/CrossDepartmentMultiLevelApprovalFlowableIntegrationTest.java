package com.aiflow.integration;

import com.aiflow.entity.UserEntity;
import com.aiflow.mapper.SysUserMapper;
import com.aiflow.model.Department;
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

class CrossDepartmentMultiLevelApprovalFlowableIntegrationTest {

    private static final Long BUSINESS_INSTANCE_ID = 500L;
    private static final Long TEMPLATE_ID = 600L;
    private static final Long APPLICANT_ID = 10L;
    private static final Long APPLICANT_MANAGER_ID = 20L;
    private static final Long FINANCE_REVIEWER_ID = 30L;
    private static final Long SECOND_FINANCE_REVIEWER_ID = 31L;
    private static final Long FINANCE_MANAGER_ID = 40L;
    private static final Long APPLICANT_DEPARTMENT_ID = 6L;
    private static final Long FINANCE_DEPARTMENT_ID = 8L;

    private ProcessEngine processEngine;
    private RuntimeService runtimeService;
    private TaskService taskService;
    private WorkflowNotificationService notificationService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessInstanceRepository instanceRepository = mock(ProcessInstanceRepository.class);
        ProcessTemplateRepository templateRepository = mock(ProcessTemplateRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
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
        Department applicantDepartment = Department.builder()
                .id(APPLICANT_DEPARTMENT_ID)
                .leaderUserId(APPLICANT_MANAGER_ID)
                .status(1).deleted(0).build();
        Department financeDepartment = Department.builder()
                .id(FINANCE_DEPARTMENT_ID)
                .leaderUserId(FINANCE_MANAGER_ID)
                .status(1).deleted(0).build();

        when(instanceRepository.findByIdAndDeleted(BUSINESS_INSTANCE_ID, 0))
                .thenReturn(Optional.of(businessInstance));
        when(templateRepository.findByIdAndDeleted(TEMPLATE_ID, 0))
                .thenReturn(Optional.of(template));
        when(departmentRepository.findByIdAndDeleted(APPLICANT_DEPARTMENT_ID, 0))
                .thenReturn(Optional.of(applicantDepartment));
        when(departmentRepository.findByIdAndDeleted(FINANCE_DEPARTMENT_ID, 0))
                .thenReturn(Optional.of(financeDepartment));
        when(userMapper.selectById(APPLICANT_ID))
                .thenReturn(activeUser(APPLICANT_ID, APPLICANT_DEPARTMENT_ID, "applicant"));
        when(userMapper.selectById(APPLICANT_MANAGER_ID))
                .thenReturn(activeUser(APPLICANT_MANAGER_ID, APPLICANT_DEPARTMENT_ID, "applicant-manager"));
        when(userMapper.selectById(FINANCE_MANAGER_ID))
                .thenReturn(activeUser(FINANCE_MANAGER_ID, FINANCE_DEPARTMENT_ID, "finance-manager"));
        when(workflowRoleService.resolveActiveUserIds("FINANCE_REVIEWER", FINANCE_DEPARTMENT_ID))
                .thenReturn(List.of(FINANCE_REVIEWER_ID, SECOND_FINANCE_REVIEWER_ID));

        ApproverResolverServiceImpl resolver = new ApproverResolverServiceImpl(
                instanceRepository, departmentRepository, userMapper, objectMapper, workflowRoleService);
        SingleAssigneeListener assigneeListener = new SingleAssigneeListener(
                resolver, templateRepository, objectMapper);

        StandaloneInMemProcessEngineConfiguration configuration =
                new StandaloneInMemProcessEngineConfiguration();
        configuration.setJdbcUrl("jdbc:h2:mem:cross-department-approval;DB_CLOSE_DELAY=-1");
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
                .name("cross-department-multi-level-approval")
                .addString("cross-department-approval.bpmn20.xml", enhanced)
                .deploy();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (processEngine != null) processEngine.close();
    }

    @Test
    void flowsAcrossApplicantManagerFinanceRoleAndFinanceManager() {
        org.flowable.engine.runtime.ProcessInstance flowableInstance =
                runtimeService.startProcessInstanceByKey("crossDepartmentApproval", Map.of(
                        "businessInstanceId", BUSINESS_INSTANCE_ID,
                        "templateId", TEMPLATE_ID,
                        "initiator", String.valueOf(APPLICANT_ID)));

        Task applicantManagerTask = currentTask(flowableInstance.getId());
        assertThat(applicantManagerTask.getTaskDefinitionKey()).isEqualTo("Approve_Applicant_Manager");
        assertThat(applicantManagerTask.getAssignee()).isEqualTo(String.valueOf(APPLICANT_MANAGER_ID));
        verify(notificationService).notifyTaskCreated(applicantManagerTask.getId(), BUSINESS_INSTANCE_ID,
                String.valueOf(APPLICANT_MANAGER_ID), "Applicant department manager approval");
        taskService.complete(applicantManagerTask.getId());

        Task financeRoleTask = currentTask(flowableInstance.getId());
        assertThat(financeRoleTask.getTaskDefinitionKey()).isEqualTo("Approve_Finance_Role");
        assertThat(financeRoleTask.getAssignee()).isNull();
        assertThat(taskService.createTaskQuery().taskId(financeRoleTask.getId())
                .taskCandidateUser(String.valueOf(FINANCE_REVIEWER_ID)).count()).isEqualTo(1);
        assertThat(taskService.createTaskQuery().taskId(financeRoleTask.getId())
                .taskCandidateUser(String.valueOf(SECOND_FINANCE_REVIEWER_ID)).count()).isEqualTo(1);
        verify(notificationService).notifyTaskCreated(financeRoleTask.getId(), BUSINESS_INSTANCE_ID,
                String.valueOf(FINANCE_REVIEWER_ID), "Finance role approval");
        verify(notificationService).notifyTaskCreated(financeRoleTask.getId(), BUSINESS_INSTANCE_ID,
                String.valueOf(SECOND_FINANCE_REVIEWER_ID), "Finance role approval");

        TaskAuthorizationService authorizationService = new TaskAuthorizationService(taskService);
        authenticate(activeUser(FINANCE_REVIEWER_ID, FINANCE_DEPARTMENT_ID, "finance-reviewer"));
        Task claimed = authorizationService.requireOperableTask(financeRoleTask);
        authenticate(activeUser(SECOND_FINANCE_REVIEWER_ID, FINANCE_DEPARTMENT_ID, "finance-reviewer-two"));
        assertThatThrownBy(() -> authorizationService.requireOperableTask(claimed))
                .isInstanceOf(AccessDeniedException.class);
        authenticate(activeUser(FINANCE_REVIEWER_ID, FINANCE_DEPARTMENT_ID, "finance-reviewer"));
        taskService.complete(claimed.getId());

        Task financeManagerTask = currentTask(flowableInstance.getId());
        assertThat(financeManagerTask.getTaskDefinitionKey()).isEqualTo("Approve_Finance_Manager");
        assertThat(financeManagerTask.getAssignee()).isEqualTo(String.valueOf(FINANCE_MANAGER_ID));
        verify(notificationService).notifyTaskCreated(financeManagerTask.getId(), BUSINESS_INSTANCE_ID,
                String.valueOf(FINANCE_MANAGER_ID), "Finance department manager approval");
        taskService.complete(financeManagerTask.getId());

        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(flowableInstance.getId()).singleResult()).isNull();
    }

    private Task currentTask(String processInstanceId) {
        return taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
    }

    private void authenticate(UserEntity user) {
        com.aiflow.security.CurrentUser currentUser = new com.aiflow.security.CurrentUser(
                user, List.of(new SimpleGrantedAuthority("ROLE_NORMAL_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, null, currentUser.getAuthorities()));
    }

    private UserEntity activeUser(Long id, Long departmentId, String username) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("-");
        user.setRole("USER");
        user.setSystemRole("normal_user");
        user.setDepartmentId(departmentId);
        user.setEnabled(1);
        user.setDeleted(0);
        return user;
    }

    private String nodeConfig() {
        return """
                {
                  "Approve_Applicant_Manager": {
                    "nodeId": "Approve_Applicant_Manager", "nodeKey": "Approve_Applicant_Manager",
                    "businessType": "approval", "approvalMode": "SINGLE",
                    "assignStrategy": "DEPARTMENT_MANAGER"
                  },
                  "Approve_Finance_Role": {
                    "nodeId": "Approve_Finance_Role", "nodeKey": "Approve_Finance_Role",
                    "businessType": "approval", "approvalMode": "SINGLE",
                    "assignStrategy": "ROLE_IN_SPECIFIED_DEPT",
                    "assignValue": "{\\\"departmentId\\\":8,\\\"roleCode\\\":\\\"FINANCE_REVIEWER\\\"}"
                  },
                  "Approve_Finance_Manager": {
                    "nodeId": "Approve_Finance_Manager", "nodeKey": "Approve_Finance_Manager",
                    "businessType": "approval", "approvalMode": "SINGLE",
                    "assignStrategy": "SPECIFIED_DEPARTMENT_MANAGER",
                    "assignValue": "{\\\"departmentId\\\":8}"
                  }
                }
                """;
    }

    private String bpmnXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  targetNamespace="http://aiflow.com/integration">
                  <bpmn:process id="crossDepartmentApproval" isExecutable="true">
                    <bpmn:startEvent id="Start_1"><bpmn:outgoing>Flow_1</bpmn:outgoing></bpmn:startEvent>
                    <bpmn:userTask id="Approve_Applicant_Manager" name="Applicant department manager approval">
                      <bpmn:incoming>Flow_1</bpmn:incoming><bpmn:outgoing>Flow_2</bpmn:outgoing>
                    </bpmn:userTask>
                    <bpmn:userTask id="Approve_Finance_Role" name="Finance role approval">
                      <bpmn:incoming>Flow_2</bpmn:incoming><bpmn:outgoing>Flow_3</bpmn:outgoing>
                    </bpmn:userTask>
                    <bpmn:userTask id="Approve_Finance_Manager" name="Finance department manager approval">
                      <bpmn:incoming>Flow_3</bpmn:incoming><bpmn:outgoing>Flow_4</bpmn:outgoing>
                    </bpmn:userTask>
                    <bpmn:endEvent id="End_1"><bpmn:incoming>Flow_4</bpmn:incoming></bpmn:endEvent>
                    <bpmn:sequenceFlow id="Flow_1" sourceRef="Start_1" targetRef="Approve_Applicant_Manager" />
                    <bpmn:sequenceFlow id="Flow_2" sourceRef="Approve_Applicant_Manager" targetRef="Approve_Finance_Role" />
                    <bpmn:sequenceFlow id="Flow_3" sourceRef="Approve_Finance_Role" targetRef="Approve_Finance_Manager" />
                    <bpmn:sequenceFlow id="Flow_4" sourceRef="Approve_Finance_Manager" targetRef="End_1" />
                  </bpmn:process>
                </bpmn:definitions>
                """;
    }
}
