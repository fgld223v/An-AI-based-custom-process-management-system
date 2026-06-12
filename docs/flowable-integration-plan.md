# Flowable Integration Plan

本文档记录当前系统接入 Flowable 前的审查结论、已完成的小范围适配，以及下一阶段正式接入建议。当前阶段不引入 Flowable 依赖，不调用 RuntimeService、RepositoryService、TaskService，不部署 BPMN，不创建正式流程实例或审批任务。

## 1. 当前流程数据模型

当前系统已形成轻量级运行链路：

```text
ProcessTemplate
  - bpmnXml: BPMN XML
  - nodeConfig: 节点业务配置
  - formId: 模板默认表单/启动表单
  - flowableDeploymentId: 模板级 Flowable 部署 ID 预留
  - flowableProcessDefinitionId: 模板级 Flowable 流程定义 ID 预留

ProcessInstance
  - templateId: 关联流程模板
  - status: 当前阶段使用 draft/submitted
  - currentNodeKey/currentNodeName/currentBusinessType: 当前预览节点
  - flowableProcessInstanceId: 未来 Flowable 实例 ID 预留
  - flowableDefinitionId: 未来 Flowable 定义 ID 预留
  - flowableDeploymentId: 未来 Flowable 部署 ID 预留

FormSubmission
  - processInstanceId: 关联轻量实例
  - nodeKey: BPMN 节点 ID
  - formId: 节点实际使用表单
  - formDataJson: 节点表单数据
```

当前运行时仍以 `ProcessInstance + FormSubmission` 保存草稿和提交数据，不代表正式流程流转。

## 2. ProcessTemplate / nodeConfig / ProcessInstance / FormSubmission 关系

```text
ProcessTemplate.bpmnXml
  保存 BPMN 图本体，未来用于部署到 Flowable。

ProcessTemplate.nodeConfig
  保存 BPMN 节点对应的业务配置。key 应等于 BPMN 节点 ID，value.nodeId 也应等于 BPMN 节点 ID。

ProcessInstance
  保存当前系统自己的业务实例。未来接入 Flowable 后，仍作为业务主表，通过 flowableProcessInstanceId 关联 Flowable 实例。

FormSubmission
  保存每个节点的表单提交数据。未来进入某个 Flowable 用户任务时，可根据 taskDefinitionKey/nodeKey 找到节点配置和表单数据。
```

## 3. BPMN XML 与 nodeConfig 对应关系

保存模板时需要保证：

```text
nodeConfigMap key == BPMN element id
nodeConfigMap[key].nodeId == BPMN element id
nodeConfigMap[key].bpmnType == BPMN element type
```

当前前端流程编辑器已增加保存前校验：

1. BPMN XML 不能为空。
2. BPMN XML 必须包含 `bpmn:definitions` 与 `bpmn:process`。
3. nodeConfig key 必须与 `nodeId` 一致。
4. nodeConfig 中的节点必须仍存在于 BPMN 图中。
5. nodeConfig 中记录的 BPMN 类型必须与当前 BPMN 元素类型一致。
6. 选择 `node_form` 的节点必须选择具体已发布表单。

当前阶段不做 BPMN 自动转换，也不调用 Flowable RepositoryService。

## 4. 业务节点到 BPMN / Flowable 节点映射

建议映射如下：

| businessType | BPMN / Flowable 节点 |
| --- | --- |
| start | bpmn:StartEvent |
| form_fill | bpmn:UserTask |
| approval | bpmn:UserTask |
| generic_task | bpmn:UserTask |
| condition | bpmn:ExclusiveGateway |
| parallel | bpmn:ParallelGateway |
| notify | bpmn:SendTask 或 bpmn:ServiceTask |
| system_action | bpmn:ServiceTask |
| end | bpmn:EndEvent |

当前代码中已经将常用业务节点创建为对应 BPMN 类型，并在前端保留了该映射常量，用于后续校验和接入说明。

## 5. 表单数据到流程变量的设计

未来启动 Flowable 流程实例时，可从当前轻量实例和提交记录组装变量：

```java
Map<String, Object> variables = new HashMap<>();
variables.put("businessInstanceId", processInstance.getId());
variables.put("templateId", processInstance.getTemplateId());
variables.put("formData", startFormDataMap);
variables.put("allFormData", allNodeFormDataMap);
variables.put("applicantId", processInstance.getApplicantId());
```

建议变量含义：

| 变量 | 含义 |
| --- | --- |
| businessInstanceId | 当前系统自己的 process_instance.id |
| templateId | 流程模板 ID |
| formData | 发起节点或当前节点表单数据 |
| allFormData | 按 nodeKey 聚合的所有节点表单数据 |
| applicantId | 发起人 ID，后续权限模块接入后使用 |

FormSubmission 的 `nodeKey` 应与 Flowable 的 `taskDefinitionKey` 对齐，便于进入任务时查找节点表单。

## 6. ProcessInstance 状态演进

当前阶段只使用：

```text
draft
submitted
```

未来接入 Flowable 后再扩展：

```text
running
completed
cancelled
terminated
```

当前阶段不把这些状态写入业务逻辑，避免提前模拟正式流转。

## 7. 本阶段已做的小范围适配

1. 为 `process_instance` 预留 Flowable 关联字段：
   - `flowable_process_instance_id`
   - `flowable_definition_id`
   - `flowable_deployment_id`
2. 为 `ProcessInstance` 实体、DTO、前端类型补齐上述字段。
3. 为流程编辑器增加 BPMN XML 保存校验。
4. 为流程编辑器增加 nodeConfig 与 BPMN 节点一致性校验。
5. 形成本接入计划文档。

如果本地数据库已存在旧表，需要执行：

```sql
ALTER TABLE process_instance
  ADD COLUMN flowable_definition_id VARCHAR(128) NULL COMMENT 'Flowable流程定义ID' AFTER flowable_process_instance_id,
  ADD COLUMN flowable_deployment_id VARCHAR(128) NULL COMMENT 'Flowable部署ID' AFTER flowable_definition_id,
  ADD KEY idx_process_instance_flowable_definition_id (flowable_definition_id),
  ADD KEY idx_process_instance_flowable_deployment_id (flowable_deployment_id);
```

如字段或索引已存在，请跳过对应语句。

## 8. 下一阶段正式接入步骤

建议按以下顺序推进：

1. 增加 Flowable 依赖和基础配置，但先不替换现有轻量实例逻辑。
2. 增加 BPMN 部署服务，只处理 `ProcessTemplate.bpmnXml` 到 Flowable definition 的部署。
3. 发布流程模板时部署 BPMN，回写 `ProcessTemplate.flowableDeploymentId` 和 `flowableProcessDefinitionId`。
4. 提交流程实例时，根据模板定义启动 Flowable 实例，回写 `ProcessInstance.flowableProcessInstanceId`、`flowableDefinitionId`、`flowableDeploymentId`。
5. 将 `FormSubmission` 聚合为流程变量传入 RuntimeService。
6. 根据 Flowable 当前任务的 `taskDefinitionKey` 读取 nodeConfig，动态解析节点表单。
7. 再开发任务列表、审批处理、审批记录和通知能力。

## 9. 当前阶段未实现内容

当前阶段明确没有实现：

1. 没有引入 Flowable 依赖。
2. 没有调用 RuntimeService。
3. 没有调用 RepositoryService。
4. 没有调用 TaskService。
5. 没有部署 BPMN。
6. 没有启动 Flowable 流程实例。
7. 没有生成审批任务。
8. 没有开发我的待办。
9. 没有开发审批记录。
10. 没有改变现有轻量草稿/提交逻辑。

## 10. 风险点和注意事项

1. BPMN XML 必须完整，否则后续部署会失败。
2. nodeConfig 的 key、nodeId 必须与 BPMN 节点 ID 稳定一致，否则节点表单无法按 taskDefinitionKey 找回。
3. 用户手动修改 BPMN XML 后，可能导致 nodeConfig 残留旧节点，需要保存前校验阻止。
4. 当前 `approval`、`form_fill`、`generic_task` 都映射为 UserTask，后续需要用 nodeConfig 区分业务语义。
5. `notify` 可根据实际实现选择 SendTask 或 ServiceTask，不建议本阶段强制转换。
6. Flowable 表和业务表应保持解耦，业务实例仍以 `process_instance.id` 为主键。
7. 接入时应保留现有 StartPreview 轻量预览能力，作为不启动流程引擎的调试入口。