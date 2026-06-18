# AI 模块接口文档

> **模块**：AI 交互界面（成员 D）  
> **LLM**：DeepSeek Chat API  
> **版本**：v1.0  
> **更新时间**：2026-06-14

---

## 接口概览

| 接口 | 方法 | 用途 | 状态 |
|------|------|------|------|
| `/api/ai/generate-process` | POST | AI 生成流程（BPMN XML + 节点配置） | ✅ 已实现 |
| `/api/ai/generate-form` | POST | AI 生成表单（fieldList + formSchema） | ⏳ D5 |
| `/api/ai/suggest-approval` | POST | AI 审批建议（通过/驳回 + 理由） | ⏳ D7 |

---

## 1. AI 生成流程

### 基本信息

```
POST /api/ai/generate-process
Content-Type: application/json
```

### 请求参数

```json
{
  "description": "创建一个请假审批流程，3天内自动通过，超过3天需要经理审批，超过7天需要总经理审批"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `description` | String | ✅ | 自然语言流程描述，最大 2000 字符 |

### 响应

**成功 (200)：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "bpmnXml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>...",
    "nodeConfig": [
      { "nodeKey": "StartEvent_1", "nodeName": "开始", "businessType": "start" },
      { "nodeKey": "UserTask_1", "nodeName": "提交请假申请", "businessType": "approval" },
      { "nodeKey": "ExclusiveGateway_1", "nodeName": "天数判断", "businessType": "condition" },
      { "nodeKey": "UserTask_2", "nodeName": "自动通过", "businessType": "approval" },
      { "nodeKey": "UserTask_3", "nodeName": "经理审批", "businessType": "approval" },
      { "nodeKey": "EndEvent_1", "nodeName": "结束", "businessType": "end" }
    ],
    "summary": "请假审批流程：员工提交后，系统判断天数..."
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `bpmnXml` | String | 标准 BPMN 2.0 XML，可直接导入流程设计器 |
| `nodeConfig` | Array | 节点配置列表 |
| `nodeConfig[].nodeKey` | String | 节点唯一标识，如 `StartEvent_1` |
| `nodeConfig[].nodeName` | String | 节点显示名称 |
| `nodeConfig[].businessType` | String | 业务类型：`start` / `approval` / `condition` / `end` |
| `summary` | String | 人类可读的流程摘要 |

**失败 (500)：**

```json
{
  "code": 500,
  "message": "AI 服务调用失败：超时",
  "data": null
}
```

### 调用示例

```javascript
// 前端调用
const { generateProcess } = await import('@/api/ai')
const result = await generateProcess('请假流程3天内自动通过')
console.log(result.bpmnXml)       // BPMN XML 字符串
console.log(result.nodeConfig)    // 节点配置数组
console.log(result.summary)       // 流程摘要
```

```bash
# curl 调用
curl -X POST http://localhost:8080/api/ai/generate-process \
  -H "Content-Type: application/json" \
  -d '{"description":"请假流程3天内自动通过，超过3天需经理审批"}'
```

### 说明

- 生成耗时约 3~10 秒（取决于 DeepSeek API 响应速度）
- 超时设置：60 秒
- 返回的 BPMN XML 已经过合法性校验（含 `<definitions>`、`<process>`、`isExecutable="true"`）
- 可直接将 `bpmnXml` 传入 bpmn-js 的 `importXML()` 渲染流程图
- 可通过 `POST /api/process-templates` 将结果创建为正式模板

---

## 2. AI 生成表单（待实现 D5）

```
POST /api/ai/generate-form
Content-Type: application/json

请求：
{
  "description": "请假表单：类型（事假/病假）、开始时间、结束时间、请假原因"
}

响应 (200)：
{
  "code": 200,
  "data": {
    "fieldList": "[{\"fieldName\":\"leaveType\",\"fieldLabel\":\"请假类型\",\"fieldType\":\"select\",...}]",
    "formSchema": "{\"layout\":\"vertical\",\"sections\":[...]}"
  }
}
```

---

## 3. AI 审批建议（待实现 D7）

```
POST /api/ai/suggest-approval
Content-Type: application/json

请求：
{
  "instanceId": 42,
  "nodeKey": "UserTask_ManagerApprove"
}

响应 (200)：
{
  "code": 200,
  "data": {
    "suggestion": "approve",
    "reason": "该申请金额在授权范围内，申请人历史信用良好",
    "confidence": 0.88,
    "riskPoints": ["建议关注：供应商合作年限不足1年"]
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `suggestion` | String | `approve` 建议通过 / `reject` 建议驳回 / `supplement` 建议补充材料 |
| `reason` | String | 建议理由，可直接作为审批意见使用 |
| `confidence` | Double | 置信度 0~1，越高越确定 |
| `riskPoints` | Array | 风险提示列表，为空表示无风险点 |
