package com.aiflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BpmnXmlEnhancerTest {

    private final BpmnXmlEnhancer enhancer = new BpmnXmlEnhancer(new ObjectMapper());

    @Test
    void injectsSingleApprovalListenerOnlyOnce() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  id="Definitions_1" targetNamespace="http://example.com">
                  <bpmn:process id="Process_1" isExecutable="true">
                    <bpmn:startEvent id="Start_1" />
                    <bpmn:userTask id="Approve_1" name="审批" />
                    <bpmn:endEvent id="End_1" />
                  </bpmn:process>
                </bpmn:definitions>
                """;
        String config = """
                {"Approve_1":{"nodeId":"Approve_1","businessType":"approval",
                  "approvalMode":"SINGLE","assignStrategy":"DIRECT_SUPERVISOR"}}
                """;

        String enhanced = enhancer.enhance(xml, config);
        String enhancedAgain = enhancer.enhance(enhanced, config);

        assertThat(enhanced).contains("singleAssigneeListener");
        assertThat(enhanced).contains("taskCreatedNotificationListener");
        assertThat(enhanced).doesNotContain("assignee=\"${initiator}\"");
        assertThat(count(enhancedAgain, "singleAssigneeListener")).isEqualTo(1);
    }

    @Test
    void multiInstanceApprovalStopsOnRejectAndExposesResultToGateway() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  id="Definitions_1" targetNamespace="http://example.com">
                  <bpmn:process id="Process_1" isExecutable="true">
                    <bpmn:startEvent id="Start_1"><bpmn:outgoing>Flow_1</bpmn:outgoing></bpmn:startEvent>
                    <bpmn:userTask id="Approve_1" name="Approval">
                      <bpmn:incoming>Flow_1</bpmn:incoming><bpmn:outgoing>Flow_2</bpmn:outgoing>
                    </bpmn:userTask>
                    <bpmn:endEvent id="End_1"><bpmn:incoming>Flow_2</bpmn:incoming></bpmn:endEvent>
                    <bpmn:sequenceFlow id="Flow_1" sourceRef="Start_1" targetRef="Approve_1" />
                    <bpmn:sequenceFlow id="Flow_2" sourceRef="Approve_1" targetRef="End_1" />
                  </bpmn:process>
                </bpmn:definitions>
                """;
        String config = """
                {"Approve_1":{"nodeId":"Approve_1","businessType":"approval",
                  "approvalMode":"ALL","assignStrategy":"SPECIFIC_USERS","assignValue":"[1,2]"}}
                """;

        String enhanced = enhancer.enhance(xml, config);

        assertThat(enhanced)
                .contains("rejected || nrOfCompletedInstances == nrOfInstances")
                .contains("multiInstanceAssigneeListener")
                .contains("taskCreatedNotificationListener")
                .contains("<bpmn:extensionElements>");

        StandaloneInMemProcessEngineConfiguration configuration =
                new StandaloneInMemProcessEngineConfiguration();
        configuration.setJdbcUrl("jdbc:h2:mem:multi-instance-enhancer-test;DB_CLOSE_DELAY=-1");
        configuration.setJdbcDriver("org.h2.Driver");
        configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        ProcessEngine processEngine = configuration.buildProcessEngine();
        try {
            var deployment = processEngine.getRepositoryService().createDeployment()
                    .addString("multi-instance.bpmn20.xml", enhanced)
                    .deploy();
            assertThat(deployment.getId()).isNotBlank();
        } finally {
            processEngine.close();
        }
    }

    private int count(String value, String token) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}
