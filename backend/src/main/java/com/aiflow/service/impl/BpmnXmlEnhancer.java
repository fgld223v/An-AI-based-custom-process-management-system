package com.aiflow.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BPMN XML 增强器 — 在部署前对 BPMN XML 进行变换，注入 Flowable 扩展元素。
 *
 * <p>处理的业务节点类型：</p>
 * <ul>
 *   <li><b>表单填写 (form_fill)</b> — 注入 {@code flowable:assignee="${initiator}"}，
 *       任务自动分配给流程发起人。</li>
 *   <li><b>单人审批 (approvalMode=SINGLE)</b> — 注入 {@code flowable:taskListener}（event=create），
 *       在任务创建时动态解析审批人并分配。</li>
 *   <li><b>会签 (approvalMode=ALL)</b> — 注入 {@code multiInstanceLoopCharacteristics}（并行多实例），
 *       所有审批人完成后流程继续。</li>
 *   <li><b>或签 (approvalMode=ANY)</b> — 注入 {@code multiInstanceLoopCharacteristics} +
 *       {@code completionCondition="${nrOfCompletedInstances >= 1}"}，
 *       任一审批人完成后流程继续。</li>
 *   <li><b>抄送 (notify)</b> — 注入 {@code flowable:delegateExpression}，
 *       流程到达时自动创建通知并立即完成（非阻塞）。</li>
 * </ul>
 */
@Slf4j
@Component
public class BpmnXmlEnhancer {

    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";
    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String FLOWABLE_PREFIX = "flowable";

    private final ObjectMapper objectMapper;

    public BpmnXmlEnhancer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 增强 BPMN XML：根据 nodeConfig 注入多实例和抄送扩展元素。
     *
     * @param bpmnXml    原始 BPMN XML
     * @param nodeConfigJson 节点配置 JSON（前端 persist 格式：{nodeId: config}）
     * @return 增强后的 BPMN XML
     */
    public String enhance(String bpmnXml, String nodeConfigJson) {
        if (nodeConfigJson == null || nodeConfigJson.isBlank()) {
            return bpmnXml;
        }

        Map<String, Map<String, Object>> nodeConfigMap = parseNodeConfig(nodeConfigJson);
        if (nodeConfigMap.isEmpty()) {
            return bpmnXml;
        }

        try {
            Document doc = parseXml(bpmnXml);

            // 确保根元素有 flowable 命名空间声明
            Element root = doc.getDocumentElement();
            boolean hasFlowableNs = false;
            for (int i = 0; i < root.getAttributes().getLength(); i++) {
                if (FLOWABLE_NS.equals(root.getAttributes().item(i).getNodeValue())) {
                    hasFlowableNs = true;
                    break;
                }
            }
            if (!hasFlowableNs) {
                root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:flowable", FLOWABLE_NS);
            }

            for (Map.Entry<String, Map<String, Object>> entry : nodeConfigMap.entrySet()) {
                String nodeId = entry.getKey();
                Map<String, Object> config = entry.getValue();
                String businessType = stringValue(config.get("businessType"));
                String approvalMode = stringValue(config.get("approvalMode"));

                if ("form_fill".equals(businessType)) {
                    // 表单填写节点 → 分配给流程发起人
                    injectInitiatorAssignee(doc, nodeId);
                } else if ("approval".equals(businessType) && ("ALL".equals(approvalMode) || "ANY".equals(approvalMode))) {
                    // 会签/或签 → 注入多实例特性
                    injectMultiInstance(doc, nodeId, "ALL".equals(approvalMode), config);
                } else if ("approval".equals(businessType)) {
                    // SINGLE 审批 → 不设 assignee，任务无候选人暂不可见。
                    // 上一节点完成时由 TaskRuntimeServiceImpl.completeTask() 动态分配审批人。
                    log.info("SINGLE 审批节点 {} 不注入 assignee，将在上一节点完成时动态分配", nodeId);
                } else if ("notify".equals(businessType)) {
                    injectCcDelegate(doc, nodeId, config);
                }
            }

            return serializeXml(doc);
        } catch (Exception e) {
            log.error("BPMN XML 增强失败，将使用原始 XML。nodeConfig={}", nodeConfigJson, e);
            return bpmnXml;
        }
    }

    /**
     * 注入 multiInstanceLoopCharacteristics。
     *
     * <p>会签 (allMustComplete=true)：所有实例完成 → 流程继续</p>
     * <p>或签 (allMustComplete=false)：任一实例完成 → 流程继续，其余取消</p>
     */
    private void injectMultiInstance(Document doc, String nodeId, boolean allMustComplete,
                                     Map<String, Object> config) {
        Element userTask = findElementById(doc, "userTask", nodeId);
        if (userTask == null) {
            log.warn("未找到 userTask 节点 {}，跳过多实例注入", nodeId);
            return;
        }

        // 设置 flowable:assignee="${assignee}" — 多实例中每个实例从集合变量取值
        userTask.setAttributeNS(FLOWABLE_NS, FLOWABLE_PREFIX + ":assignee", "${assignee}");

        // 构建 multiInstanceLoopCharacteristics
        Element multiInstance = doc.createElementNS(BPMN_NS, "bpmn:multiInstanceLoopCharacteristics");
        multiInstance.setAttribute("isSequential", "false");
        multiInstance.setAttributeNS(FLOWABLE_NS, FLOWABLE_PREFIX + ":collection",
                "assigneeList_" + nodeId);
        multiInstance.setAttributeNS(FLOWABLE_NS, FLOWABLE_PREFIX + ":elementVariable",
                "assignee");

        if (!allMustComplete) {
            // 或签：任一完成即继续
            Element completionCondition = doc.createElementNS(BPMN_NS,
                    "bpmn:completionCondition");
            completionCondition.setTextContent("${nrOfCompletedInstances >= 1}");
            // Flowable 要求 completionCondition 有 xsi:type
            completionCondition.setAttributeNS(
                    "http://www.w3.org/2001/XMLSchema-instance", "xsi:type",
                    "bpmn:tFormalExpression");
            multiInstance.appendChild(completionCondition);
        }

        // 注入 executionListener — 在 multiInstance 启动时解析审批人列表
        Element executionListener = doc.createElementNS(FLOWABLE_NS,
                FLOWABLE_PREFIX + ":executionListener");
        executionListener.setAttribute("event", "start");
        executionListener.setAttributeNS(FLOWABLE_NS, FLOWABLE_PREFIX + ":delegateExpression",
                "${multiInstanceAssigneeListener}");
        multiInstance.appendChild(executionListener);

        userTask.appendChild(multiInstance);
        log.info("已为节点 {} 注入多实例特性，approvalMode={}，allMustComplete={}",
                nodeId, config.get("approvalMode"), allMustComplete);
    }

    /**
     * 为抄送节点 (notify/SendTask) 注入 delegateExpression，
     * 使其在流程到达时自动创建通知并完成。
     */
    private void injectCcDelegate(Document doc, String nodeId, Map<String, Object> config) {
        // 查找 SendTask 或 ServiceTask
        Element task = findElementById(doc, "sendTask", nodeId);
        if (task == null) {
            task = findElementById(doc, "serviceTask", nodeId);
        }
        if (task == null) {
            log.warn("未找到抄送任务节点 {}，跳过 CC 注入", nodeId);
            return;
        }

        // 设置 JavaDelegate 实现类
        task.setAttributeNS(FLOWABLE_NS, FLOWABLE_PREFIX + ":delegateExpression",
                "${ccNotificationDelegate}");

        log.info("已为抄送节点 {} 注入 CC 通知委托", nodeId);
    }

    /**
     * 为 form_fill 节点注入 {@code flowable:assignee="${initiator}"}，
     * 使任务自动分配给流程发起人（即申请人）。
     */
    private void injectInitiatorAssignee(Document doc, String nodeId) {
        Element userTask = findElementById(doc, "userTask", nodeId);
        if (userTask == null) {
            log.warn("未找到 form_fill 节点 {}，跳过 initiator assignee 注入", nodeId);
            return;
        }
        userTask.setAttributeNS(FLOWABLE_NS, FLOWABLE_PREFIX + ":assignee", "${initiator}");
        log.info("已为 form_fill 节点 {} 注入 initiator assignee", nodeId);
    }

    /**
     * 为 SINGLE 审批节点注入 TaskListener（event=create），
     * 在任务创建时通过 {@code singleAssigneeListener} 动态解析审批人并分配。
     *
     * <p>TaskListener 必须包裹在 {@code bpmn:extensionElements} 中，
     * 否则 Flowable 部署时的 XSD 校验会失败。</p>
     */
    private void injectSingleApprovalListener(Document doc, String nodeId,
                                               Map<String, Object> config) {
        Element userTask = findElementById(doc, "userTask", nodeId);
        if (userTask == null) {
            log.warn("未找到审批节点 {}，跳过 TaskListener 注入", nodeId);
            return;
        }

        // 先设置一个占位 assignee，确保 Flowable 能创建任务
        userTask.setAttributeNS(FLOWABLE_NS, FLOWABLE_PREFIX + ":assignee", "${initiator}");

        // 查找或创建 bpmn:extensionElements 容器
        Element extElements = findExtensionElements(doc, userTask);
        if (extElements == null) {
            extElements = doc.createElementNS(BPMN_NS, "bpmn:extensionElements");
            // extensionElements 必须是 userTask 的第一个子元素（在 outgoing 等之前）
            org.w3c.dom.Node firstChild = userTask.getFirstChild();
            if (firstChild != null) {
                userTask.insertBefore(extElements, firstChild);
            } else {
                userTask.appendChild(extElements);
            }
        }

        // 注入 TaskListener — create 事件触发时由 singleAssigneeListener 重新分配
        Element taskListener = doc.createElementNS(FLOWABLE_NS,
                FLOWABLE_PREFIX + ":taskListener");
        taskListener.setAttribute("event", "create");
        taskListener.setAttributeNS(FLOWABLE_NS, FLOWABLE_PREFIX + ":delegateExpression",
                "${singleAssigneeListener}");
        extElements.appendChild(taskListener);

        log.info("已为 SINGLE 审批节点 {} 注入 TaskListener（assignStrategy={}）",
                nodeId, stringValue(config.get("assignStrategy")));
    }

    /**
     * 查找或创建 userTask 下的 bpmn:extensionElements 子元素。
     */
    private Element findExtensionElements(Document doc, Element userTask) {
        org.w3c.dom.NodeList children = userTask.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child instanceof Element elem
                    && "extensionElements".equals(elem.getLocalName())) {
                return elem;
            }
        }
        return null;
    }

    // ================================================================
    // XML 工具方法
    // ================================================================

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String serializeXml(Document doc) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    /**
     * 按 ID 属性查找 BPMN 元素。
     * 需要处理命名空间 — BPMN 元素可能在默认命名空间或 bpmn: 前缀下。
     */
    private Element findElementById(Document doc, String localName, String id) {
        // 尝试通过 getElementById（需要 DTD/Schema 声明 id 属性）
        Element byId = doc.getElementById(id);
        if (byId != null) return byId;

        // 遍历所有元素查找匹配的 localName + id
        return findElementByLocalNameAndId(doc.getDocumentElement(), localName, id);
    }

    private Element findElementByLocalNameAndId(Element parent, String localName, String targetId) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element child)) continue;

            String childLocalName = child.getLocalName();
            if (childLocalName == null) {
                childLocalName = child.getTagName();
                // 去掉命名空间前缀
                int colonIdx = childLocalName.indexOf(':');
                if (colonIdx >= 0) {
                    childLocalName = childLocalName.substring(colonIdx + 1);
                }
            }

            String childId = child.getAttribute("id");
            if (localName.equals(childLocalName) && targetId.equals(childId)) {
                return child;
            }

            Element found = findElementByLocalNameAndId(child, localName, targetId);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * 解析 nodeConfig JSON。
     * 前端格式：{ "NodeId1": { "nodeId": "NodeId1", ... }, "NodeId2": { ... } }
     */
    private Map<String, Map<String, Object>> parseNodeConfig(String json) {
        try {
            return objectMapper.readValue(json,
                    new TypeReference<Map<String, Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("nodeConfig JSON 解析失败（非标准格式），尝试数组格式", e);
            // 尝试数组格式兼容
            try {
                List<Map<String, Object>> list = objectMapper.readValue(json,
                        new TypeReference<List<Map<String, Object>>>() {});
                Map<String, Map<String, Object>> map = new HashMap<>();
                for (Map<String, Object> item : list) {
                    Object nodeId = item.get("nodeId");
                    if (nodeId == null) nodeId = item.get("nodeKey");
                    if (nodeId != null) {
                        map.put(nodeId.toString(), item);
                    }
                }
                return map;
            } catch (Exception ex2) {
                log.error("nodeConfig JSON 解析完全失败", ex2);
                return Map.of();
            }
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString().trim();
    }
}
