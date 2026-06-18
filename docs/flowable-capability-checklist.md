# Flowable 流程引擎能力清单

> 生成日期：2026-06-16 | 版本：v1.0.0
>
> 本文档记录 AI Flow 平台当前已集成的 Flowable 流程引擎能力及对应的业务功能。

---

## 一、BPMN 2.0 元素支持

| BPMN 元素 | 业务语义 | 支持状态 | 说明 |
|-----------|----------|:---:|------|
| `StartEvent` | 流程开始 | ✅ | 支持手动/表单触发/定时三种发起方式 |
| `EndEvent` | 流程结束 | ✅ | 支持完成/驳回/取消/异常四种结束状态 |
| `UserTask` | 审批/填写/任务 | ✅ | 核心任务节点，支持多实例（会签/或签） |
| `ExclusiveGateway` | 条件分支 | ✅ | 支持表达式条件 `${leaveDays > 3}`，默认分支兜底 |
| `ParallelGateway` | 并行分支 | ✅ | 支持全部完成/任一完成等待策略 |
| `SendTask` | 抄送通知 | ✅ | 自动创建通知并完成，非阻塞 |
| `ServiceTask` | 系统动作 | ✅ | 支持 HTTP/DB/编号生成/Webhook/工单创建 |
| `SequenceFlow` | 流转连线 | ✅ | 条件表达式、默认流 |
| `multiInstanceLoopCharacteristics` | 多实例(会签/或签) | ✅ | 并行多实例，动态审批人解析 |

## 二、Flowable 引擎集成

| 能力 | 状态 | 实现类 |
|------|:---:|------|
| **流程定义部署** | ✅ | `FlowableDeploymentServiceImpl` — deployProcessTemplate() |
| **BPMN XML 校验** | ✅ | 部署前校验 definitions/process 标签完整性 |
| **isExecutable 自动修复** | ✅ | 自动注入 `isExecutable="true"` |
| **BPMN 增强注入** | ✅ | `BpmnXmlEnhancer` — 部署前注入多实例/抄送扩展 |
| **流程实例启动** | ✅ | `FlowableRuntimeServiceImpl` — startProcess() |
| **流程变量构建** | ✅ | allFormData + startFormData + 顶层变量 |
| **任务查询（待办）** | ✅ | `TaskQueryService` — ACT_RU_TASK |
| **任务查询（已办）** | ✅ | `TaskQueryService` — ACT_HI_TASKINST |
| **任务完成** | ✅ | `TaskRuntimeServiceImpl` — completeTask() |
| **任务驳回** | ✅ | `TaskRuntimeServiceImpl` — rejectTask() |
| **审批人自动分配** | ✅ | `ApproverResolverService` + `MultiInstanceAssigneeListener` |
| **运行时状态查询** | ✅ | GET `/api/process-instances/{id}/runtime-state` |
| **多实例进度查询** | ✅ | `TaskQueryServiceImpl.enrichMultiInstanceInfo()` |
| **超时自动处理** | ✅ | `TaskTimeoutNotificationScheduler` — 定时扫描+自动通过 |
| **催办定时调度** | ✅ | `TaskUrgeScheduler` — 定时扫描+提醒通知 |

## 三、审批角色策略

| 策略 | 常量 | 说明 |
|------|------|------|
| 部门负责人 | `DEPARTMENT_MANAGER` | 查发起人所属部门 leaderUserId |
| 直属上级 | `DIRECT_SUPERVISOR` | 当前回退到部门经理（待实现真正的职级上级查询） |
| 指定用户 | `SPECIFIC_USERS` | JSON 数组 `[1,2,3]` 指定具体用户 ID |
| 指定角色 | `ROLE` | 查所有 role=XXX 的用户 |

## 四、自动化规则

| 规则 | 触发条件 | 动作 | 实现 |
|------|----------|------|------|
| **条件自动审批** | approvalRule 字段+运算符+阈值命中 | 自动通过，记录 `autoApproved=true` | `RuleEvaluatorService` |
| **超时自动通过** | 任务创建超过 N 小时 | 自动通过，创建 `timeout_warning` 通知 | `TaskTimeoutNotificationScheduler` |
| **自动催办** | 任务创建超过 N 小时 | 创建 `task_remind` 通知 | `TaskUrgeScheduler` |
| **手动催办** | 用户点击"催办" | 立即创建提醒通知 | `TaskUrgeService` |

## 五、流程生命周期

```
草稿(draft) → 提交(submitted) → 运行(running) → 完成(completed)
                                ↓              ↓
                            驳回(rejected)   异常(error)
```

| 操作 | 前置状态 | 后置状态 |
|------|----------|----------|
| 保存草稿 (save) | — | `draft` |
| 提交 (submit) | `draft` | `submitted` → `running`（Flowable 启动后） |
| 审批通过 (approve) | `running` | `running`（流转到下一节点）或 `completed`（最后一个节点） |
| 驳回 (reject) | `running` | `rejected` |
| 超时自动通过 | `running` | 同上（审批通过逻辑） |
| 发布模板 (publish) | `draft`/`reviewing` | `published` |
| 撤回模板 (unpublish) | `published` | `draft` |

## 六、通知类型

| 类型 | 触发场景 | 渠道 |
|------|----------|------|
| `task_remind` | 催办（手动/自动） | 站内信 + WebSocket |
| `timeout_warning` | 任务超时 | 站内信 + WebSocket |
| `approval_result` | 审批结果（预留） | 站内信 + WebSocket |
| `system_notice` | 系统通知/抄送 | 站内信 + WebSocket |

## 七、边界条件与兜底

| 场景 | 处理策略 |
|------|----------|
| 多实例审批人列表为空 | 兜底为系统管理员 (userId=1) |
| 任务不存在 | 抛出 IllegalArgumentException |
| 非 running 状态操作 | 抛出 IllegalStateException |
| BPMN XML 不完整 | 拒绝部署 |
| nodeConfig JSON 解析失败 | 静默降级，不影响流程 |
| 自动审批链过长 | 最多 10 跳后停止并告警 |
| DeepSeek API 调用失败 | 抛出 BusinessException，前端展示错误 |
| AI 生成 XML 缺少 isExecutable | 正则自动修复 |

---

## 八、API 端点总览

### 认证
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | JWT 登录 |

### AI
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ai/generate-process` | 自然语言生成流程 |

### 表单
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/forms` | 表单列表 |
| GET | `/api/forms/published` | 已发布表单 |
| POST | `/api/forms` | 创建表单 |
| PUT | `/api/forms/{id}` | 更新表单 |
| GET | `/api/forms/{id}` | 表单详情 |
| POST | `/api/forms/{id}/publish` | 发布表单 |

### 流程模板
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/process-templates` | 模板列表 |
| GET | `/api/process-templates/{id}` | 模板详情（含 BPMN XML） |
| POST | `/api/process-templates` | 创建模板 |
| PUT | `/api/process-templates/{id}` | 更新模板 |
| POST | `/api/process-templates/{id}/publish` | 发布（→Flowable 部署） |
| POST | `/api/process-templates/{id}/unpublish` | 撤回 |
| GET | `/api/process-templates/{id}/form` | 模板绑定表单 |

### 流程实例
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/process-instances` | 实例列表 |
| GET | `/api/process-instances/{id}` | 实例详情 |
| POST | `/api/process-instances/draft` | 保存草稿 |
| POST | `/api/process-instances/{id}/node-form` | 保存节点表单 |
| POST | `/api/process-instances/{id}/submit` | 提交启动 |
| GET | `/api/process-instances/{id}/submissions` | 表单提交历史 |
| GET | `/api/process-instances/{id}/runtime-state` | Flowable 运行时状态 |
| POST | `/api/process-instances/{id}/urge` | 手动催办 |

### 任务
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/tasks/my` | 待办列表 |
| GET | `/api/tasks/done` | 已办列表 |
| GET | `/api/tasks/{taskId}` | 任务详情 |
| POST | `/api/tasks/{taskId}/complete` | 审批通过 |
| POST | `/api/tasks/{taskId}/reject` | 审批驳回 |

### 统计
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/statistics/overview` | 概览统计 |
| GET | `/api/statistics/trend` | 趋势统计 |

### 通知
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/notifications` | 通知列表 |
| GET | `/api/notifications/unread-count` | 未读数 |
| GET | `/api/notifications/{id}` | 通知详情 |
| POST | `/api/notifications` | 创建通知 |
| PUT | `/api/notifications/{id}` | 更新通知 |
| PUT | `/api/notifications/{id}/read` | 标为已读 |
| PUT | `/api/notifications/{id}/unread` | 标为未读 |
| DELETE | `/api/notifications/{id}` | 删除（软删除） |

### 业务类型
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/biz-types` | 业务类型列表 |

### 模板市场
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/template-market` | 市场列表 |
| POST | `/api/template-market/publish-template` | 上架 |
| POST | `/api/template-market/{id}/copy` | 复制使用 |
| DELETE | `/api/template-market/{id}` | 下架 |

---

## 九、Swagger UI

启动后端后访问：**http://localhost:8080/swagger-ui/index.html**

API 规范 JSON：**http://localhost:8080/v3/api-docs**
