package com.aiflow.integration;

import com.aiflow.entity.UserEntity;
import com.aiflow.mapper.SysUserMapper;
import com.aiflow.model.Department;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.ApprovalVariableService;
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
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SingleDepartmentApprovalFlowableIntegrationTest {

    private static final Long BUSINESS_INSTANCE_ID = 100L;
    private static final Long TEMPLATE_ID = 200L;
    private static final Long APPLICANT_ID = 10L;
    private static final Long MANAGER_ID = 20L;
    private static final Long DEPARTMENT_ID = 6L;

    private ProcessEngine processEngine;
    private RuntimeService runtimeService;
    private TaskService taskService;
    private WorkflowNotificationService workflowNotificationService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessInstanceRepository processInstanceRepository = mock(ProcessInstanceRepository.class);
        ProcessTemplateRepository processTemplateRepository = mock(ProcessTemplateRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        WorkflowRoleService workflowRoleService = mock(WorkflowRoleService.class);
        workflowNotificationService = mock(WorkflowNotificationService.class);

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
        Department department = Department.builder()
                .id(DEPARTMENT_ID)
                .deptCode("PRODUCT")
                .deptName("Product Department")
                .leaderUserId(MANAGER_ID)
                .status(1)
                .deleted(0)
                .build();
        UserEntity applicant = activeUser(APPLICANT_ID, DEPARTMENT_ID, "applicant");
        UserEntity manager = activeUser(MANAGER_ID, DEPARTMENT_ID, "manager");

        when(processInstanceRepository.findByIdAndDeleted(BUSINESS_INSTANCE_ID, 0))
                .thenReturn(Optional.of(businessInstance));
        when(processTemplateRepository.findByIdAndDeleted(TEMPLATE_ID, 0))
                .thenReturn(Optional.of(template));
        when(departmentRepository.findByIdAndDeleted(DEPARTMENT_ID, 0))
                .thenReturn(Optional.of(department));
        when(sysUserMapper.selectById(APPLICANT_ID)).thenReturn(applicant);
        when(sysUserMapper.selectById(MANAGER_ID)).thenReturn(manager);

        ApproverResolverServiceImpl resolver = new ApproverResolverServiceImpl(
                processInstanceRepository, departmentRepository, sysUserMapper,
                objectMapper, workflowRoleService);
        SingleAssigneeListener assigneeListener = new SingleAssigneeListener(
                resolver, processTemplateRepository, objectMapper);
        TaskCreatedNotificationListener notificationListener =
                new TaskCreatedNotificationListener(workflowNotificationService);

        StandaloneInMemProcessEngineConfiguration configuration =
                new StandaloneInMemProcessEngineConfiguration();
        configuration.setJdbcUrl("jdbc:h2:mem:single-department-approval;DB_CLOSE_DELAY=-1");
        configuration.setJdbcDriver("org.h2.Driver");
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setBeans(Map.of(
                "singleAssigneeListener", assigneeListener,
                "taskCreatedNotificationListener", notificationListener));
        processEngine = configuration.buildProcessEngine();
        runtimeService = processEngine.getRuntimeService();
        taskService = processEngine.getTaskService();

        String deployableBpmn = new BpmnXmlEnhancer(objectMapper).enhance(rawBpmnXml(), nodeConfig());
        processEngine.getRepositoryService().createDeployment()
                .name("single-department-approval")
                .addString("single-department-approval.bpmn20.xml", deployableBpmn)
                .deploy();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (processEngine != null) {
            processEngine.close();
        }
    }

    @Test
    void departmentManagerReceivesAndCompletesSingleApprovalTask() {
        ProcessInstance flowableInstance = runtimeService.startProcessInstanceByKey(
                "singleDepartmentApproval",
                Map.of("businessInstanceId", BUSINESS_INSTANCE_ID,
                        "templateId", TEMPLATE_ID,
                        "initiator", String.valueOf(APPLICANT_ID)));

        Task approvalTask = taskService.createTaskQuery()
                .processInstanceId(flowableInstance.getId())
                .singleResult();
        assertThat(approvalTask).isNotNull();
        assertThat(approvalTask.getTaskDefinitionKey()).isEqualTo("Approve_Manager");
        assertThat(approvalTask.getAssignee()).isEqualTo(String.valueOf(MANAGER_ID));
        verify(workflowNotificationService).notifyTaskCreated(
                approvalTask.getId(), BUSINESS_INSTANCE_ID,
                String.valueOf(MANAGER_ID), "Department manager approval");

        TaskAuthorizationService authorizationService = new TaskAuthorizationService(taskService);
        authenticate(activeUser(30L, DEPARTMENT_ID, "other-user"));
        assertThatThrownBy(() -> authorizationService.requireOperableTask(approvalTask))
                .isInstanceOf(AccessDeniedException.class);

        authenticate(activeUser(MANAGER_ID, DEPARTMENT_ID, "manager"));
        assertThat(authorizationService.requireOperableTask(approvalTask).getAssignee())
                .isEqualTo(String.valueOf(MANAGER_ID));
        ApprovalVariableService variableService = new ApprovalVariableService(runtimeService);
        taskService.complete(approvalTask.getId(), variableService.build(
                flowableInstance.getId(), approvalTask.getTaskDefinitionKey(),
                "approve", "single department approved", false, null,
                LocalDateTime.of(2026, 6, 22, 14, 0)));

        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(flowableInstance.getId()).singleResult()).isNull();
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
                  "Approve_Manager": {
                    "nodeId": "Approve_Manager",
                    "nodeKey": "Approve_Manager",
                    "businessType": "approval",
                    "approvalMode": "SINGLE",
                    "assignStrategy": "DEPARTMENT_MANAGER"
                  }
                }
                """;
    }

    private String rawBpmnXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                  targetNamespace="http://aiflow.com/integration">
                  <bpmn:process id="singleDepartmentApproval" name="Single department approval" isExecutable="true">
                    <bpmn:startEvent id="Start_1">
                      <bpmn:outgoing>Flow_Start_Approval</bpmn:outgoing>
                    </bpmn:startEvent>
                    <bpmn:userTask id="Approve_Manager" name="Department manager approval">
                      <bpmn:incoming>Flow_Start_Approval</bpmn:incoming>
                      <bpmn:outgoing>Flow_Approval_Gateway</bpmn:outgoing>
                    </bpmn:userTask>
                    <bpmn:exclusiveGateway id="Gateway_Result">
                      <bpmn:incoming>Flow_Approval_Gateway</bpmn:incoming>
                      <bpmn:outgoing>Flow_Agree</bpmn:outgoing>
                      <bpmn:outgoing>Flow_Reject</bpmn:outgoing>
                    </bpmn:exclusiveGateway>
                    <bpmn:endEvent id="End_Approved"><bpmn:incoming>Flow_Agree</bpmn:incoming></bpmn:endEvent>
                    <bpmn:endEvent id="End_Rejected"><bpmn:incoming>Flow_Reject</bpmn:incoming></bpmn:endEvent>
                    <bpmn:sequenceFlow id="Flow_Start_Approval" sourceRef="Start_1" targetRef="Approve_Manager" />
                    <bpmn:sequenceFlow id="Flow_Approval_Gateway" sourceRef="Approve_Manager" targetRef="Gateway_Result" />
                    <bpmn:sequenceFlow id="Flow_Agree" sourceRef="Gateway_Result" targetRef="End_Approved">
                      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression"><![CDATA[${approved}]]></bpmn:conditionExpression>
                    </bpmn:sequenceFlow>
                    <bpmn:sequenceFlow id="Flow_Reject" sourceRef="Gateway_Result" targetRef="End_Rejected">
                      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression"><![CDATA[${rejected}]]></bpmn:conditionExpression>
                    </bpmn:sequenceFlow>
                  </bpmn:process>
                </bpmn:definitions>
                """;
    }
}
