package com.aiflow.integration;

import com.aiflow.service.impl.BpmnXmlEnhancer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.flowable.task.service.delegate.TaskListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SeedMarketTemplateFlowableIntegrationTest {

    private ProcessEngine processEngine;
    private RuntimeService runtimeService;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        TaskListener assignReviewer = task -> task.setAssignee("seed-reviewer");
        TaskListener ignoreNotification = task -> { };

        StandaloneInMemProcessEngineConfiguration configuration =
                new StandaloneInMemProcessEngineConfiguration();
        configuration.setJdbcUrl("jdbc:h2:mem:seed-market-templates;DB_CLOSE_DELAY=-1");
        configuration.setJdbcDriver("org.h2.Driver");
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        configuration.setBeans(Map.of(
                "singleAssigneeListener", assignReviewer,
                "taskCreatedNotificationListener", ignoreNotification));
        processEngine = configuration.buildProcessEngine();
        runtimeService = processEngine.getRuntimeService();
        taskService = processEngine.getTaskService();
    }

    @AfterEach
    void tearDown() {
        if (processEngine != null) processEngine.close();
    }

    @Test
    void allSeedTemplatesStopAtEveryConfiguredApprovalTask() throws Exception {
        deploy("leave-request", "leave-seed.bpmn20.xml");
        deploy("reimbursement", "reimbursement-seed.bpmn20.xml");
        deploy("purchase-request", "purchase-seed.bpmn20.xml");

        assertApprovalRoute("Process_Leave_Request",
                List.of("Approve_Supervisor", "Approve_Hr"));
        assertApprovalRoute("Process_Expense_Reimbursement",
                List.of("Approve_Department", "Approve_Finance_Reviewer", "Approve_Finance_Manager"));
        assertApprovalRoute("Process_Purchase_Request",
                List.of("Approve_Department", "Approve_Purchase"));
    }

    @Test
    void leaveGatewayRoutesSupervisorRejectionToRejectedEnd() throws Exception {
        deploy("leave-request", "leave-rejection-seed.bpmn20.xml");
        org.flowable.engine.runtime.ProcessInstance instance =
                runtimeService.startProcessInstanceByKey("Process_Leave_Request");

        Task supervisorTask = taskService.createTaskQuery()
                .processInstanceId(instance.getId()).singleResult();
        assertThat(supervisorTask.getTaskDefinitionKey()).isEqualTo("Approve_Supervisor");
        taskService.complete(supervisorTask.getId(), Map.of(
                "approved", false,
                "Approve_Supervisor_approved", false));

        assertThat(taskService.createTaskQuery().processInstanceId(instance.getId()).count()).isZero();
        assertThat(processEngine.getHistoryService().createHistoricActivityInstanceQuery()
                .processInstanceId(instance.getId())
                .activityId("End_Rejected")
                .count()).isEqualTo(1);
        assertThat(processEngine.getHistoryService().createHistoricActivityInstanceQuery()
                .processInstanceId(instance.getId())
                .activityId("Approve_Hr")
                .count()).isZero();
    }

    private void deploy(String folder, String resourceName) throws Exception {
        String bpmn = read("seed/templates/" + folder + "/process.bpmn20.xml");
        String nodeConfig = read("seed/templates/" + folder + "/node-config.json")
                .replace("${dept.hr}", "30")
                .replace("${dept.finance}", "20")
                .replace("${dept.purchase}", "40");
        String enhanced = new BpmnXmlEnhancer(new ObjectMapper()).enhance(bpmn, nodeConfig);
        processEngine.getRepositoryService().createDeployment()
                .name("seed-" + folder)
                .addString(resourceName, enhanced)
                .deploy();
    }

    private void assertApprovalRoute(String processKey, List<String> expectedTaskKeys) {
        org.flowable.engine.runtime.ProcessInstance instance =
                runtimeService.startProcessInstanceByKey(processKey);

        for (String expectedTaskKey : expectedTaskKeys) {
            Task task = taskService.createTaskQuery()
                    .processInstanceId(instance.getId())
                    .singleResult();
            assertThat(task).as("process must wait at %s", expectedTaskKey).isNotNull();
            assertThat(task.getTaskDefinitionKey()).isEqualTo(expectedTaskKey);
            assertThat(task.getAssignee()).isEqualTo("seed-reviewer");
            taskService.complete(task.getId(), Map.of(
                    "approved", true,
                    expectedTaskKey + "_approved", true));
        }

        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(instance.getId()).singleResult()).isNull();
    }

    private String read(String path) throws IOException {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
