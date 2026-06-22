package com.aiflow.integration;

import com.aiflow.service.ApprovalVariableService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApprovalGatewayFlowableIntegrationTest {

    private static final String PROCESS_KEY = "approvalGatewayIntegration";

    private ProcessEngine processEngine;
    private RuntimeService runtimeService;
    private TaskService taskService;
    private ApprovalVariableService approvalVariableService;

    @BeforeAll
    void setUpEngine() {
        ProcessEngineConfiguration configuration = new StandaloneInMemProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:approval-gateway;DB_CLOSE_DELAY=-1")
                .setJdbcDriver("org.h2.Driver")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        processEngine = configuration.buildProcessEngine();
        runtimeService = processEngine.getRuntimeService();
        taskService = processEngine.getTaskService();
        approvalVariableService = new ApprovalVariableService(runtimeService);

        processEngine.getRepositoryService().createDeployment()
                .name("approval-gateway-integration")
                .addString("approval-gateway.bpmn20.xml", bpmnXml())
                .deploy();
    }

    @AfterAll
    void closeEngine() {
        if (processEngine != null) {
            processEngine.close();
        }
    }

    @Test
    void agreeResultSelectsApprovedBranchAndKeepsUnifiedVariables() {
        ProcessInstance instance = startInstance();
        Task approvalTask = currentTask(instance.getId());

        taskService.complete(approvalTask.getId(), approvalVariableService.build(
                instance.getId(), approvalTask.getTaskDefinitionKey(), "approve",
                "approved in integration test", false, null,
                LocalDateTime.of(2026, 6, 22, 9, 0)));

        Task routedTask = currentTask(instance.getId());
        assertThat(routedTask.getTaskDefinitionKey()).isEqualTo("Approved_Path");
        assertUnifiedVariables(instance.getId(), "agree", true, false);

        taskService.complete(routedTask.getId());
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(instance.getId()).singleResult()).isNull();
    }

    @Test
    void rejectResultSelectsRejectedBranchAndKeepsUnifiedVariables() {
        ProcessInstance instance = startInstance();
        Task approvalTask = currentTask(instance.getId());

        taskService.complete(approvalTask.getId(), approvalVariableService.build(
                instance.getId(), approvalTask.getTaskDefinitionKey(), "reject",
                "rejected in integration test", false, null,
                LocalDateTime.of(2026, 6, 22, 9, 5)));

        Task routedTask = currentTask(instance.getId());
        assertThat(routedTask.getTaskDefinitionKey()).isEqualTo("Rejected_Path");
        assertUnifiedVariables(instance.getId(), "reject", false, true);

        taskService.complete(routedTask.getId());
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(instance.getId()).singleResult()).isNull();
    }

    private ProcessInstance startInstance() {
        return runtimeService.startProcessInstanceByKey(PROCESS_KEY, Map.of("initiator", "100"));
    }

    private Task currentTask(String processInstanceId) {
        return taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
    }

    @SuppressWarnings("unchecked")
    private void assertUnifiedVariables(String processInstanceId, String result,
                                        boolean approved, boolean rejected) {
        Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
        assertThat(variables)
                .containsEntry("approvalResult", result)
                .containsEntry("approved", approved)
                .containsEntry("rejected", rejected)
                .containsEntry("Approve_1_result", result)
                .containsEntry("Approve_1_approved", approved)
                .containsEntry("lastApprovalNode", "Approve_1");

        Map<String, Object> approvalResults = (Map<String, Object>) variables.get("approvalResults");
        assertThat(approvalResults).containsKey("Approve_1");
        assertThat((Map<String, Object>) approvalResults.get("Approve_1"))
                .containsEntry("approvalResult", result)
                .containsEntry("approved", approved)
                .containsEntry("rejected", rejected);
    }

    private String bpmnXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                  targetNamespace="http://aiflow.com/integration">
                  <bpmn:process id="approvalGatewayIntegration" name="Approval gateway integration" isExecutable="true">
                    <bpmn:startEvent id="Start_1">
                      <bpmn:outgoing>Flow_Start_Approval</bpmn:outgoing>
                    </bpmn:startEvent>
                    <bpmn:userTask id="Approve_1" name="Approval">
                      <bpmn:incoming>Flow_Start_Approval</bpmn:incoming>
                      <bpmn:outgoing>Flow_Approval_Gateway</bpmn:outgoing>
                    </bpmn:userTask>
                    <bpmn:exclusiveGateway id="Gateway_Result">
                      <bpmn:incoming>Flow_Approval_Gateway</bpmn:incoming>
                      <bpmn:outgoing>Flow_Agree</bpmn:outgoing>
                      <bpmn:outgoing>Flow_Reject</bpmn:outgoing>
                    </bpmn:exclusiveGateway>
                    <bpmn:userTask id="Approved_Path" name="Approved path">
                      <bpmn:incoming>Flow_Agree</bpmn:incoming>
                      <bpmn:outgoing>Flow_Approved_End</bpmn:outgoing>
                    </bpmn:userTask>
                    <bpmn:userTask id="Rejected_Path" name="Rejected path">
                      <bpmn:incoming>Flow_Reject</bpmn:incoming>
                      <bpmn:outgoing>Flow_Rejected_End</bpmn:outgoing>
                    </bpmn:userTask>
                    <bpmn:endEvent id="End_Approved">
                      <bpmn:incoming>Flow_Approved_End</bpmn:incoming>
                    </bpmn:endEvent>
                    <bpmn:endEvent id="End_Rejected">
                      <bpmn:incoming>Flow_Rejected_End</bpmn:incoming>
                    </bpmn:endEvent>
                    <bpmn:sequenceFlow id="Flow_Start_Approval" sourceRef="Start_1" targetRef="Approve_1" />
                    <bpmn:sequenceFlow id="Flow_Approval_Gateway" sourceRef="Approve_1" targetRef="Gateway_Result" />
                    <bpmn:sequenceFlow id="Flow_Agree" sourceRef="Gateway_Result" targetRef="Approved_Path">
                      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression"><![CDATA[${approved}]]></bpmn:conditionExpression>
                    </bpmn:sequenceFlow>
                    <bpmn:sequenceFlow id="Flow_Reject" sourceRef="Gateway_Result" targetRef="Rejected_Path">
                      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression"><![CDATA[${rejected}]]></bpmn:conditionExpression>
                    </bpmn:sequenceFlow>
                    <bpmn:sequenceFlow id="Flow_Approved_End" sourceRef="Approved_Path" targetRef="End_Approved" />
                    <bpmn:sequenceFlow id="Flow_Rejected_End" sourceRef="Rejected_Path" targetRef="End_Rejected" />
                  </bpmn:process>
                </bpmn:definitions>
                """;
    }
}
