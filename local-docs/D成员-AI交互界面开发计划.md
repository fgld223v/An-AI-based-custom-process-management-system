# 成员 D：AI 交互界面 — 完整开发计划（代码审查修正版）

> **角色**：全栈（前端 + 后端）  
> **周期**：10 天（跨第 1、2 周）  
> **LLM**：DeepSeek Chat API（OpenAI 兼容格式）  
> **修正日期**：2026-06-17  
> **修正说明**：V3 — 新增节点-表单绑定完整流程设计；D5 后端已实现；表单字段名标准化

---

## 一、项目现状关键发现（修正依据）

### ⚠️ pom.xml 缺失的依赖

| 依赖 | 用途 | 是否需要加 |
|------|------|-----------|
| `spring-boot-starter-webflux` | WebClient 调 DeepSeek API | **必须加** |
| `spring-boot-starter-aop` | @AspectJ 审计日志切面 | **必须加**（项目零 AOP） |

项目中目前**没有任何 HTTP 客户端**（无 WebClient、无 RestTemplate、无 OkHttp）。

### ⚠️ 数据库表状态

| 表 | 状态 | 需要做什么 |
|----|------|-----------|
| `ai_advice_record` | DDL 已有，无 Java 实体 | D7 新建 JPA Entity |
| `ai_correction_log` | DDL 已有，无 Java 实体 | 本次不创建（后续迭代） |
| `ai_model_metric` | DDL 已有，无 Java 实体 | 本次不创建（后续迭代） |
| `ai_service_account` | **DDL 不存在** | D8 新建完整 DDL + Entity |
| `operation_log` | DDL 已有，无 Java 实体 | D9 新建 Entity + **ALTER ENUM** 加 AI 操作类型 |

### ⚠️ operation_log 表 ENUM 冲突

现有 `operation_type` ENUM 只允许：`login, logout, create, update, delete, approve, reject, publish, config_change`。  
AI 操作（`GENERATE_PROCESS`、`GENERATE_FORM` 等）不在列表中。  
**修正方案**：D9 执行 `ALTER TABLE operation_log MODIFY COLUMN operation_type ENUM('login','logout','create','update','delete','approve','reject','publish','config_change','ai_generate','ai_suggest')`。

### ⚠️ AOP 基础设施为零

项目零 `@Aspect`、零自定义注解、零切面。D9 需要从零建立 AOP 模式。

### ✅ 可直接复用的前端资产

| 资产 | 路径 | 复用场景 |
|------|------|---------|
| `DynamicFormRenderer.vue` | `src/components/form/` | D4/D5 表单 Schema 预览渲染 |
| `BpmnModeler` | `ProcessDesigner.vue` 的用法可参考 | D3 BPMN 查看器（需改用 BpmnViewer） |
| `SidebarNav.vue` menuGroups | `src/components/layout/` | D1 加 AI 菜单项 |
| `BasicLayout.vue` | `src/components/layout/` | 所有新页面共用布局 |

---

## 二、技术架构总览（修正版）

```
┌─────────────────────────────────────────────────────────┐
│                      Frontend (Vue 3)                     │
│  /ai/generate-process   /ai/generate-form                 │
│  TaskDetail 内嵌 AiSuggestionPanel                         │
├─────────────────────────────────────────────────────────┤
│  POST /api/ai/generate-process                            │
│  POST /api/ai/generate-form                               │
│  POST /api/ai/suggest-approval                            │
│       ↑ 受 AiServiceAuthFilter 保护                        │
├─────────────────────────────────────────────────────────┤
│  AiProcessService          AiFormService                   │
│  AiApprovalService                                        │
│       │                                                   │
│       ├── WebClient ──→ https://api.deepseek.com/v1       │
│       │                  /chat/completions                 │
│       │                  model: deepseek-chat              │
│       │                                                   │
│       ├── AiServiceAccountRepository (API Key 校验)        │
│       └── @AuditLog AOP ──→ operation_log 表               │
└─────────────────────────────────────────────────────────┘
```

### 新增/修改文件清单（修正版）

| 层 | 文件 | 操作 | 说明 |
|----|------|------|------|
| **pom.xml** | `backend/pom.xml` | 修改 | +webflux +aop 两个依赖 |
| **yml** | `application.yml` | 修改 | +deepseek 配置 |
| **schema.sql** | `db/schema.sql` | 修改 | +ai_service_account DDL + ALTER operation_log |
| 后端-Config | `config/AiConfig.java` | 新建 | DeepSeek 配置属性 |
| 后端-Config | `config/WebClientConfig.java` | 新建 | WebClient Bean |
| 后端-Controller | `controller/AiController.java` | 新建 | 3 个端点，替代占位 |
| 后端-Service | `service/AiProcessService.java` | 新建 | 流程生成 |
| 后端-Service | `service/AiFormService.java` | 新建 | 表单生成 |
| 后端-Service | `service/AiApprovalService.java` | 新建 | 审批建议 |
| 后端-Service | `service/AiApprovalService.java` 内 | 内嵌 | 直接调用 A 的 ApproverResolverService 和 B 的 NotificationService |
| 后端-DTO | `dto/AiGenerateRequest.java` | 新建 | 通用请求 DTO |
| 后端-DTO | `dto/AiGenerateProcessResponse.java` | 新建 | 流程生成响应 |
| 后端-DTO | `dto/AiGenerateFormResponse.java` | 新建 | 表单生成响应 |
| 后端-DTO | `dto/AiApprovalRequest.java` | 新建 | 审批建议请求 |
| 后端-DTO | `dto/AiApprovalResponse.java` | 新建 | 审批建议响应 |
| 后端-Model | `model/AiAdviceRecord.java` | 新建 | JPA Entity（表已存在） |
| 后端-Model | `model/AiServiceAccount.java` | 新建 | JPA Entity（表需新建） |
| 后端-Model | `model/OperationLog.java` | 新建 | JPA Entity（表已存在） |
| 后端-Repo | `repository/AiAdviceRecordRepository.java` | 新建 | JPA Repository |
| 后端-Repo | `repository/AiServiceAccountRepository.java` | 新建 | JPA Repository |
| 后端-Repo | `repository/OperationLogRepository.java` | 新建 | JPA Repository |
| 后端-Filter | `filter/AiServiceAuthFilter.java` | 新建 | API Key 校验（仿 JwtAuthFilter） |
| 后端-Annotation | `annotation/AuditLog.java` | 新建 | 审计注解 |
| 后端-Aspect | `aspect/AuditLogAspect.java` | 新建 | 审计切面（项目首个 AOP） |
| 前端-Page | `views/ai/AiGenerateProcess.vue` | 新建 | AI 流程生成 |
| 前端-Page | `views/ai/AiGenerateForm.vue` | 新建 | AI 表单生成 |
| 前端-Component | `components/ai/AiSuggestionPanel.vue` | 新建 | AI 审批建议卡片 |
| 前端-Component | `components/ai/BpmnViewerPanel.vue` | 新建 | BPMN 只读查看器（BpmnViewer） |
| 前端-Designer | `views/process-designer/ProcessDesigner.vue` | 修改 | D3：接收 AI 生成的 XML，自动导入 |
| 前端-API | `api/ai.ts` | 新建 | AI 接口封装 |
| 前端-Router | `router/index.ts` | 修改 | +2 条路由 |
| 前端-Sidebar | `components/layout/SidebarNav.vue` | 修改 | 启用 AI 菜单项 |
| 前端-TaskDetail | `views/task/TaskDetail.vue` | 修改 | 嵌入 AI 建议面板 |

---

## 三、日计划详细拆解（修正版）

---

### D1：AI 流程生成页面（前端）

**目标**：`/ai/generate-process` 页面框架就绪

**实际代码对齐**：

1. **路由注册**（`frontend/src/router/index.ts`）：
   在 `children` 数组中新增：
   ```typescript
   {
     path: '/ai/generate-process',
     name: 'AiGenerateProcess',
     component: () => import('@/views/ai/AiGenerateProcess.vue'),
     meta: { title: 'AI 智能生成流程', group: '流程' }
   }
   ```

2. **菜单启用**（`frontend/src/components/layout/SidebarNav.vue`）：
   找到 `menuGroups` 中「AI生成流程」项，将 `available` 改为 `true`，`path` 改为 `/ai/generate-process`：
   ```typescript
   { label: 'AI生成流程', path: '/ai/generate-process', icon: MagicStick, available: true }
   ```
   同时可以删除 TopBar 的 "AI助手" coming-soon 按钮（或保留不管）。

3. **页面组件**（`frontend/src/views/ai/AiGenerateProcess.vue`）：
   - 使用 `<script setup lang="ts">`
   - 输入区：`<el-input type="textarea" :rows="6">` + placeholder 示例
   - 操作区：「生成」按钮（primary + loading 状态）+ 「清空」按钮
   - 加载态：`<el-skeleton :rows="8" animated />` + 提示文字
   - 响应为空时留空骨架区域，等 D3 填充

4. **API 空壳**（`frontend/src/api/ai.ts`）：
   ```typescript
   import request from './request'
   
   export async function generateProcess(description: string) {
     return await request.post('/api/ai/generate-process', { description })
   }
   ```

**验收标准**：
- 侧边栏「AI生成流程」可点击，跳转到新页面
- 输入文字点生成 → console 看到 POST 请求（后端先返回 501，报错是预期的）
- 加载骨架屏正常展示

**依赖**：无

---

### D2：AI 流程生成后端（后端核心）

**目标**：`POST /api/ai/generate-process` 接入 DeepSeek，替代 501

**修正重点**：
- pom.xml 加 `spring-boot-starter-webflux`
- 创建 WebClient Bean（非 RestTemplate）
- Prompt 中强制 JSON 输出 + 校验 BPMN XML 合法性

**具体步骤**：

1. **加依赖**（`backend/pom.xml`）：
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-webflux</artifactId>
   </dependency>
   ```

2. **加配置**（`backend/src/main/resources/application.yml`）：
   ```yaml
   ai:
     deepseek:
       api-key: ${DEEPSEEK_API_KEY:sk-your-api-key-here}
       base-url: https://api.deepseek.com/v1
       model: deepseek-chat
       timeout-seconds: 60
   ```

3. **创建配置类**（`config/AiConfig.java`）：
   ```java
   @ConfigurationProperties(prefix = "ai.deepseek")
   public record AiConfig(String apiKey, String baseUrl, String model, int timeoutSeconds) {}
   ```
   注：项目使用 Java 17，可用 record。如要保持风格统一也可用 `@Data`。

4. **创建 WebClient Bean**（`config/WebClientConfig.java`）：
   ```java
   @Configuration
   public class WebClientConfig {
       @Bean
       public WebClient deepseekWebClient(AiConfig aiConfig) {
           return WebClient.builder()
               .baseUrl(aiConfig.baseUrl())
               .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + aiConfig.apiKey())
               .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
               .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
               .build();
       }
   }
   ```

5. **创建 AiProcessService**（`service/AiProcessService.java`）：
   - 注入 `WebClient` + `AiConfig` + `ObjectMapper`
   - **System Prompt 核心设计**：
     ```
     你是一个 BPMN 2.0 工作流生成专家。输出必须是合法的 JSON，格式固定为：
     {"bpmnXml":"...","nodeConfig":[...]}
     不要输出 Markdown 代码块。直接输出 JSON。
     BPMN XML 必须包含 isExecutable="true"。
     ```
   - 调 DeepSeek → 解析 JSON → 校验 bpmnXml 含 `<definitions>` + `<process>` + `isExecutable="true"`
   - 失败时抛出 `BusinessException`（走全局异常处理）

6. **创建请求/响应 DTO**：
   - `dto/AiGenerateRequest.java`：`@Data` + `@NotBlank String description`
   - `dto/AiGenerateProcessResponse.java`：`@Data` + `String bpmnXml` + `List<NodeConfigItem> nodeConfig`
   - `dto/NodeConfigItem.java`：`String nodeKey, nodeName, businessType`

7. **创建 AiController**（`controller/AiController.java`）：
   仿 `ProcessTemplateController` 风格：
   ```java
   @RestController
   @RequiredArgsConstructor
   @RequestMapping("/api/ai")
   public class AiController {
       private final AiProcessService aiProcessService;
       
       @PostMapping("/generate-process")
       public ApiResponse<AiGenerateProcessResponse> generateProcess(
               @Valid @RequestBody AiGenerateRequest request) {
           return ApiResponse.success(aiProcessService.generateProcess(request.getDescription()));
       }
   }
   ```

8. **删除 AiPlaceholderController**（或先保留，确认无引用后删除）

**验收标准**：
- `POST /api/ai/generate-process` 返回 `code: 200`
- 请求 `{"description": "请假流程3天内自动通过"}`
- 返回的 bpmnXml 可用 bpmn-js 正常渲染
- nodeConfig 含完整节点列表

**依赖**：无（DeepSeek API Key 已有）

> 🔧 **2026-06-16 更新**：System Prompt 已升级，AI 生成的 nodeConfig 现在包含 `approvalMode`、`assignStrategy`、`notifyTarget` 等字段，与组长的 `BpmnXmlEnhancer` 兼容。生成的 BPMN XML 可直接部署到 Flowable 并支持会签/或签/抄送。

---

### D3：AI 流程生成前端 — 预览 + 确认创建（前端）

**目标**：生成结果可视化展示 + 一键创建模板

**修正重点**：
- **BPMN 查看用 BpmnViewer，不是 BpmnModeler**  
  `import BpmnViewer from 'bpmn-js/lib/Viewer'`  
  查看器比建模器轻量，适合只读预览
- 复用项目现有的 `POST /api/process-templates` 创建模板

**具体步骤**：

1. **创建 BpmnViewerPanel.vue**（`components/ai/BpmnViewerPanel.vue`）：
   ```typescript
   import BpmnViewer from 'bpmn-js/lib/Viewer'
   import 'bpmn-js/dist/assets/diagram-js.css'
   import 'bpmn-js/dist/assets/bpmn-js.css'
   import 'bpmn-font/css/bpmn.css'
   ```
   Props：`bpmnXml: string`  
   在 `watch` 中调用 `viewer.importXML(props.bpmnXml)` → `canvas.zoom('fit-viewport')`

2. **完善 AiGenerateProcess.vue**（分三栏布局）：
   - 左栏（60%）：BPMN 图预览（BpmnViewerPanel）
   - 右栏（40%）：节点列表卡片 + 流程摘要
   - 底部操作栏：「在流程编辑器中打开」+ 「确认创建模板」+ 「重新生成」

3. **「在流程编辑器中打开」功能**（AI 生成→设计器微调的核心通路）：
   - 把生成的 BPMN XML 写入 `sessionStorage.setItem('ai-generated-bpmn', xml)`
   - 跳转 `/process-designer?from=ai`
   - 在 `ProcessDesigner.vue` 的 `onMounted` 中检查 `from=ai` 参数 + sessionStorage 是否有 XML：
     ```typescript
     const fromAi = route.query.from === 'ai'
     const aiXml = sessionStorage.getItem('ai-generated-bpmn')
     if (fromAi && aiXml) {
       await modeler.value.importXML(aiXml)
       sessionStorage.removeItem('ai-generated-bpmn')  // 用完清掉
     }
     ```
   - `ProcessDesigner.vue` 改动极小（约 8 行），只在 onMounted 末尾加一个判断

4. **创建模板弹窗**：
   - 模板名称（从 AI 输出预填）
   - bizType 下拉（调 `GET /api/biz-types`）
   - 确认 → `POST /api/process-templates` 创建
   - 成功后跳转 `/templates/:id`

5. **对接 api/ai.ts**：处理 loading / 成功 / 失败三种状态

**验收标准**：
- 输入「请假流程」→ 生成 → 看到 BPMN 图 + 节点列表
- 确认创建 → 模板列表能看到新模板
- 生成失败时展示错误 + 重试按钮

**依赖**：D2 完成

---

### D4：AI 表单生成页面（前端）

**目标**：`/ai/generate-form` 页面，预览 + 创建

**修正重点**：
- 复用 `DynamicFormRenderer.vue` 渲染表单预览（已支持 fieldList/JSON string 输入）
- 不需要自己写表单渲染逻辑

**具体步骤**：

1. **路由 + 菜单**：
   - 路由 `/ai/generate-form` → `AiGenerateForm.vue`
   - SidebarNav 加「AI表单生成」菜单项

2. **页面组件**（`views/ai/AiGenerateForm.vue`）：
   - 输入区：自然语言描述框
   - 结果区三栏：
     - 左：生成的 fieldList 表格展示
     - 中：`<DynamicFormRenderer :fieldList="..." :readonly="true" />` 实时预览
     - 右：formSchema JSON 折叠面板
   - 底部：「确认创建表单」

3. **API**：`api/ai.ts` 加 `generateForm(description)`

**验收标准**：
- 页面完整，表单预览用 DynamicFormRenderer 正常渲染
- 确认创建 → 表单列表能看到新表单

**依赖**：无（先 mock 数据开发 UI）

---

### D5：AI 表单生成后端（后端）

**目标**：`POST /api/ai/generate-form` 调 DeepSeek，返回 fieldList + formSchema

**具体步骤**：

1. **创建 AiFormService**（`service/AiFormService.java`）：
   - 注入 WebClient + AiConfig + ObjectMapper
   - **System Prompt** 返回格式要求与 `FormCreateRequest` 结构对齐：
     ```
     输出 JSON：
     {
       "fieldList": [
         {"fieldName":"...", "fieldLabel":"...", "fieldType":"text|textarea|number|select|date|datetime|radio|checkbox", "required":true, "options":[...]}
       ],
       "formSchema": { "layout":"vertical", "sections":[...] }
     }
     fieldType 必须与 DynamicFormRenderer 支持的类型一致
     ```

2. **创建响应 DTO**（`dto/AiGenerateFormResponse.java`）：
   - `String fieldList`（JSON 字符串，与 FormDefinition 表字段一致）
   - `String formSchema`（JSON 字符串）

3. **AiController 加端点**：
   ```java
   @PostMapping("/generate-form")
   public ApiResponse<AiGenerateFormResponse> generateForm(
           @Valid @RequestBody AiGenerateRequest request) {
       return ApiResponse.success(aiFormService.generateForm(request.getDescription()));
   }
   ```

**验收标准**：
- `POST /api/ai/generate-form` 返回 fieldList + formSchema
- 返回的 fieldList JSON 与 FormDefinition.fieldList 格式兼容，可直接创建表单

**依赖**：无

---

### D6：AI 审批建议组件（前端）

**目标**：TaskDetail 审批区嵌入 AI 建议卡片

**修正重点**：
- 精确插入位置：`info-panel` 之后、`form-panel` 之前
- 复用 Element Plus `<el-card>` 样式

**具体步骤**：

1. **创建 AiSuggestionPanel.vue**（`components/ai/AiSuggestionPanel.vue`）：
   - Props：`instanceId: number`、`nodeKey: string`
   - 三种状态：
     - `loading`：`<el-skeleton :rows="3" />` + 「AI 正在分析...」
     - `error`：`<el-alert type="warning">` + 重试按钮
     - `loaded`：卡片内容
       - 建议标签：`<el-tag type="success">建议通过</el-tag>` / `danger` 驳回 / `warning` 需补充
       - 理由文本
       - 置信度：`<el-progress :percentage="85">`
       - 风险点列表：`<el-tag size="small" type="warning">` 每条
       - 「采纳建议」按钮 → 填入审批意见 → 触发 emit

2. **嵌入 TaskDetail.vue**：
   在 `info-panel`（第 18-57 行区域）和 `form-panel`（第 60-88 行区域）之间插入：
   ```html
   <AiSuggestionPanel
     v-if="task.status === 'active'"
     :instance-id="task.businessInstanceId"
     :node-key="task.taskDefinitionKey"
     @adopt="onAdoptSuggestion"
   />
   ```

3. **API**：`api/ai.ts` 加 `getApprovalSuggestion(instanceId, nodeKey)`

**验收标准**：
- 打开待审批任务 → 看到 AI 建议卡片
- 三种状态（加载/成功/失败）都正常
- 采纳建议正确填入审批表单

**依赖**：先 mock 数据开发 D6，D7 接口好了再对接

---

### D7：AI 审批建议后端（后端）

**目标**：`POST /api/ai/suggest-approval` 调 DeepSeek → 返回建议 + 存表

**修正重点**（2026-06-16 更新）：
- ✅ A 的 `ApproverResolverService` **已由组长 merge 实现**（5 种策略），直接注入调用
- ✅ B 的 `NotificationService` **已由组长 merge 实现**（含 WebSocket 推送），直接注入调用
- ✅ `NotificationCreateRequest` DTO 已存在（`receiverId, type, title, content, targetType, targetId, targetUrl`）
- ✅ `ai_advice_record` 表已存在 → 创建 JPA Entity 映射
- **不再需要 mock，直接对接真实接口**

**具体步骤**：

1. **创建 JPA Entity**：
   - `model/AiAdviceRecord.java`（映射已有 `ai_advice_record` 表）
   - `repository/AiAdviceRecordRepository.java`

2. **创建 AiApprovalService**（`service/AiApprovalService.java`）：
   - 输入：instanceId + nodeKey
   - 查询流程上下文：ProcessInstance（获取 templateId） + FormSubmission（当前节点表单数据） + FormDefinition（表单结构）
   - 构建 Prompt（含表单数据 + 流程上下文 + 字段说明）
   - 调 DeepSeek API → 解析建议 JSON → `AiApprovalResponse`
   - 保存到 `ai_advice_record` 表
   - **调用 A 的 ApproverResolverService**：验证审批人身份
     ```java
     List<Long> approvers = approverResolverService.resolveApprovers(
         instanceId, nodeKey, assignStrategy, assignValue);
     ```
   - **调用 B 的 NotificationService**：推送 AI 建议通知给审批人
     ```java
     notificationService.createNotification(NotificationCreateRequest.builder()
         .receiverId(approverId)
         .type("ai_suggestion")
         .title("AI 已生成审批建议")
         .content(suggestion.getReason())
         .targetType("task")
         .targetId(taskId)
         .targetUrl("/tasks/" + taskId)
         .build());
     ```

3. **创建请求/响应 DTO**：
   - `dto/AiApprovalRequest.java`：`Long instanceId` + `String nodeKey`
   - `dto/AiApprovalResponse.java`：`String suggestion (approve/reject/supplement)` + `String reason` + `Double confidence` + `List<String> riskPoints`

4. **AiController 加端点**：
   ```java
   @PostMapping("/suggest-approval")
   public ApiResponse<AiApprovalResponse> suggestApproval(
           @Valid @RequestBody AiApprovalRequest request) {
       return ApiResponse.success(aiApprovalService.suggest(request));
   }
   ```

**验收标准**：
- 对待审批任务调 API → 返回合理建议（agree/reject + 理由）
- `ai_advice_record` 表有记录写入
- 通知推送到审批人的通知中心

**依赖**：A 和 B 接口已就绪，无阻塞

---

### D8：AI 服务账号 + API Key 保护（后端）

**目标**：AI 接口调用受 API Key 保护

**修正重点**：
- `ai_service_account` 表**不存在** → 需要完整 DDL
- Filter 仿 `JwtAuthenticationFilter` 的 `OncePerRequestFilter` 模式
- SecurityConfig 中 AI 端点目前是 `permitAll`，Filter 独立校验

**具体步骤**：

1. **建表 DDL**（追加到 `db/schema.sql`）：
   ```sql
   CREATE TABLE IF NOT EXISTS ai_service_account (
     id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
     account_name VARCHAR(64) NOT NULL COMMENT '账号名称',
     api_key VARCHAR(128) NOT NULL COMMENT 'API密钥',
     status ENUM('active','disabled') NOT NULL DEFAULT 'active',
     granted_endpoints JSON NULL COMMENT '授权端点列表',
     rate_limit INT UNSIGNED DEFAULT 100 COMMENT '每分钟限次',
     created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
     updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
     deleted TINYINT NOT NULL DEFAULT 0,
     PRIMARY KEY (id),
     UNIQUE KEY uk_ai_service_account_api_key (api_key)
   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI服务账号表';
   ```
   以及插入一条默认测试账号的 INSERT 语句。

2. **创建 JPA Entity + Repository**：
   - `model/AiServiceAccount.java`
   - `repository/AiServiceAccountRepository.java`

3. **创建 AiServiceAuthFilter**（`filter/AiServiceAuthFilter.java`）：
   仿 `JwtAuthenticationFilter`：
   ```java
   @Component
   public class AiServiceAuthFilter extends OncePerRequestFilter {
       // 只拦截 /api/ai/**
       @Override
       protected boolean shouldNotFilter(HttpServletRequest request) {
           return !request.getRequestURI().startsWith("/api/ai/");
       }
       
       @Override
       protected void doFilterInternal(...) {
           String apiKey = request.getHeader("X-API-Key");
           if (!StringUtils.hasText(apiKey)) {
               sendError(response, 401, "缺少 API Key");
               return;  // ← 注意：这里直接返回，不放行
           }
           AiServiceAccount account = repo.findByApiKey(apiKey).orElse(null);
           if (account == null || !"active".equals(account.getStatus())) {
               sendError(response, 403, "API Key 无效或已禁用");
               return;
           }
           // 可选：校验速率限制
           filterChain.doFilter(request, response);
       }
   }
   ```

4. **注册 Filter**（`SecurityConfig.java`）：
   ```java
   .addFilterBefore(aiServiceAuthFilter, JwtAuthenticationFilter.class)
   ```
   AI AuthFilter 在 JWT Filter 之前执行 —— AI 调用者不需要 JWT，只需要 API Key。

5. **数据脱敏切面**（`aspect/DataMaskAspect.java`）：对敏感字段做脱敏（手机号/身份证号）

**验收标准**：
- 无 `X-API-Key` Header → 401
- 错误 Key → 403
- 正确 Key → 正常返回

**依赖**：无

---

### D9：审计日志 AOP（后端）

**目标**：AI 操作自动记录到 operation_log 表

**修正重点**：
- pom.xml 加 `spring-boot-starter-aop`（项目无 AOP 基础设施）
- operation_log 表需要 **ALTER ENUM** 加 AI 操作类型
- 这是项目首个自定义注解 + 切面

**具体步骤**：

1. **加依赖**（`pom.xml`）：
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-aop</artifactId>
   </dependency>
   ```

2. **ALTER operation_log 表**（`db/schema.sql` 追加）：
   ```sql
   ALTER TABLE operation_log 
   MODIFY COLUMN operation_type ENUM(
     'login','logout','create','update','delete','approve','reject','publish','config_change',
     'ai_generate','ai_suggest'
   ) NOT NULL COMMENT '操作类型';
   ```

3. **创建 JPA Entity**（`model/OperationLog.java`）：
   映射已有 `operation_log` 表

4. **创建 @AuditLog 注解**（`annotation/AuditLog.java`）：
   ```java
   @Target(ElementType.METHOD)
   @Retention(RetentionPolicy.RUNTIME)
   public @interface AuditLog {
       String value();  // 操作名称，如 "AI_GENERATE_PROCESS"
   }
   ```

5. **创建切面**（`aspect/AuditLogAspect.java`）：
   ```java
   @Aspect
   @Component
   @RequiredArgsConstructor
   public class AuditLogAspect {
       private final OperationLogRepository operationLogRepository;
       
       @Around("@annotation(auditLog)")
       public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
           // 记录开始时间、入参
           Object result;
           try {
               result = pjp.proceed();
               // 记录成功
           } catch (Exception e) {
               // 记录失败
               throw e;
           }
           return result;
       }
   }
   ```

6. **在 AiController 方法上加注解**：
   ```java
   @AuditLog("AI_GENERATE_PROCESS")
   @PostMapping("/generate-process")
   ...
   ```

**验收标准**：
- 调用 AI 接口后 operation_log 表有记录
- 操作人（尝试从 JWT 获取，无则记 "api_service"）

**依赖**：E 的 `SecurityUtils.getCurrentUser()` — D9 时 E 的 D2 已完成

---

### D10：联调 + AI 端点集成测试

**目标**：全链路跑通，周五演示

**任务清单**：

1. 流程生成全链路：输入文字 → 生成 BPMN → 预览 → 创建模板 → 模板可发布
2. 表单生成全链路：输入文字 → 生成字段 → 预览表单 → 创建表单 → 表单可发布
3. 审批建议全链路：创建实例 → 提交审批 → TaskDetail AI 建议展示 → 采纳
4. 对接 A：ApproverResolverService 真实调用（替换 mock）
5. 对接 B：NotificationService 推送通知（替换 mock）
6. 对接 E：SecurityUtils.getCurrentUser() 集成
7. API Key 保护验证：无 Key / 错 Key / 正确 Key
8. DeepSeek 超时/异常降级测试

**验收标准**：
- 周五 Demo 三条全链路可走通
- 无 500 错误
- API Key 保护有效
- 审计日志有记录

---

## 四、修正总结

| 原计划问题 | 修正 |
|-----------|------|
| 没提 pom.xml 需要加 webflux | D2 第一步加依赖 |
| 没提 pom.xml 需要加 aop | D9 第一步加依赖 |
| ai_service_account 表假设存在 | D8 新建完整 DDL |
| operation_log ENUM 不包含 AI 操作 | D9 ALTER TABLE 扩展 ENUM |
| 计划说 AOP 但项目零 AOP 基础 | 明确标注"项目首个切面" |
| D3 用 bpmn-js 查看器没区分 Modeler/Viewer | 改为 BpmnViewer（只读） |
| D4/D5 没提复用 DynamicFormRenderer | 明确复用现有组件 |
| D7 假设 A/B 接口存在 | ✅ A/B 接口已由组长实现，直接注入调用，无需 mock |
| sidebarnav 用 placeholder 路由 | 改为真实路由 `/ai/generate-process` |
| AiPlaceholderController 怎么处理 | 新建 AiController，确认无引用后删除占位 |
| 部分文件名与项目风格不符 | 全部对齐 `@Data` + `@RequiredArgsConstructor` + `ApiResponse<T>` 模式 |

---

## 五、节点-表单绑定完整流程（2026-06-17 新增）

### 用户期望的流程

```
流程设计器点节点
  → [AI 生成此节点表单] 弹窗
    → 用户点 [跳转到 AI 表单生成]（携带节点名+提示词）
      → /ai/generate-form（提示词自动填入）
        → 用户生成表单
          → 确认创建 → 跳转 /form-designer（编辑查看）
            → 回到流程设计器 → 表单自动绑定到节点 ✅
              → 保存模板 → 绑定关系持久化 ✅
```

### 技术实现

**跨页面状态传递（sessionStorage）：**

```
对话框点击"跳转AI表单生成"
  → sessionStorage: { pendingBindNodeKey, pendingBindPrompt }
  
AiGenerateForm 创建表单后
  → sessionStorage: { pendingBindNodeKey, pendingBindFormId, pendingBindFormName }
  → 跳转 /form-designer?id=xxx
  
ProcessDesigner onMounted
  → 检测 sessionStorage 有 pendingBindFormId
    → 自动在 nodeConfigMap 中设置对应节点的 formId + formBindingMode
    → syncNodeConfig()
    → 提示"表单已绑定到节点 XX，请保存模板"
    → 清除 sessionStorage 挂起状态
```

### 改动清单

| 文件 | 改动 |
|------|------|
| ProcessDesigner.vue - jumpToAiGenerateForm() | 保存 nodeKey 到 sessionStorage |
| AiGenerateForm.vue - submitCreateForm() | 创建后保存 formId 到 sessionStorage，跳转 /form-designer |
| ProcessDesigner.vue - onMounted() | 检测挂起的绑定，自动绑定到节点 |
