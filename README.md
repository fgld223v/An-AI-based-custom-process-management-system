# An AI-based Custom Process Management System

<div align="center">

**基于 AI 大模型的企业级自定义流程管理系统**

[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://adoptium.net/)
[![Spring Boot 3.2](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3.5-blue.svg)](https://vuejs.org/)
[![Flowable 7.0](https://img.shields.io/badge/Flowable-7.0-red.svg)](https://www.flowable.com/)
[![License MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## 目录

- [项目概述](#项目概述)
- [系统架构](#系统架构)
- [核心功能](#核心功能)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [API 接口](#api-接口)
- [测试](#测试)
- [权限体系](#权限体系)
- [默认数据](#默认数据)

---

## 项目概述

**An AI-based Custom Process Management System** 是一套将 AI 大模型（DeepSeek）与企业级流程引擎（Flowable）深度融合的业务流程管理系统。系统支持用户通过**自然语言描述**直接生成 BPMN 2.0 工作流和表单，并提供 AI 辅助审批、流程瓶颈分析等智能化功能。

### 核心亮点

| 能力 | 说明 |
|------|------|
| 🤖 **AI 流程生成** | 用自然语言描述业务需求，AI 自动生成标准 BPMN 2.0 XML 和节点配置 |
| 📝 **AI 表单生成** | 描述表单需求，AI 生成数据采集字段（自动过滤审批类字段） |
| 💡 **AI 审批建议** | 基于表单数据和流程上下文，AI 给出审批建议（通过/驳回/补充材料） |
| 📊 **AI 流程优化** | 分析历史流转数据，自动识别瓶颈、冗余节点、权限问题并给出改进建议 |
| 💬 **AI 对话助手** | 内置 BPM 专家对话机器人，支持 SSE 流式回复 |
| 🔧 **可视化流程设计器** | 基于 bpmn-js，支持拖拽式流程编辑、条件分支、多级审批 |
| 📋 **可视化表单设计器** | 支持多种字段类型，表单与流程节点灵活绑定 |
| ✅ **智能审批引擎** | 单人/会签/或签、直属主管/部门经理/角色/指定人员等多种审批模式 |
| 🔄 **驳回与重提** | 支持驳回后重新提交、驳回规则（结束流程/退回指定节点） |
| ⏱️ **超时与催办** | SLA 超时自动处理（提醒/自动通过/自动驳回）、催办通知 |
| 🔗 **字段联动** | 跨字段校验规则自动注入（如 endDate ≥ startDate） |
| 🛡️ **细粒度权限** | 系统角色 + 工作流角色 + 部门范围 + 数据权限四位一体 |
| 📡 **实时通知** | WebSocket 实时推送，通知去重机制 |
| 🏪 **模板市场** | 8 套种子模板（请假/出差/报销/采购等），支持上架/下架/复制 |

---

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend (Vue 3)                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────┐  │
│  │ bpmn-js  │ │FormDesign│ │   AI     │ │  SSE Chat     │  │
│  │ Designer │ │          │ │ Panels   │ │               │  │
│  └──────────┘ └──────────┘ └──────────┘ └───────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                     REST API / WebSocket                     │
├─────────────────────────────────────────────────────────────┤
│                   Backend (Spring Boot 3.2)                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────┐  │
│  │ 5 AI     │ │Flowable  │ │Security  │ │ Notification  │  │
│  │ Services │ │ Engine   │ │ + JWT    │ │ + WebSocket   │  │
│  └──────────┘ └──────────┘ └──────────┘ └───────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────┐  │
│  │ Process  │ │  Form    │ │ Approval │ │  Statistics   │  │
│  │ Template │ │ Designer │ │ Engine   │ │  & Monitor    │  │
│  └──────────┘ └──────────┘ └──────────┘ └───────────────┘  │
├─────────────────────────────────────────────────────────────┤
│              DeepSeek API  │  MySQL 8.0  │  H2 (Test)       │
└─────────────────────────────────────────────────────────────┘
```

### AI 服务调用链

```
用户输入（自然语言）
    │
    ▼
┌─────────────────┐     HTTP POST      ┌──────────────┐
│  AiProcessService │ ────────────────▶ │  DeepSeek    │
│  AiFormService    │                   │  Chat API    │
│  AiApprovalService│ ◀──────────────── │              │
│  AiOptimizationService│  JSON Response│              │
│  AiChatService   │                   └──────────────┘
└─────────────────┘
    │
    ▼
  后处理（四重保障机制）
  ├── 白名单校验 (8 种合法 businessType)
  ├── BPMN 元素类型交叉验证
  ├── 关键词匹配 (审批/填写/通知)
  └── 角色名推断
    │
    ▼
  持久化到 MySQL + 返回前端
```

---

## 核心功能

### 1. AI 流程构建引擎

| 功能 | 说明 |
|------|------|
| 🗣️ 自然语言输入 | 「员工提交请假申请，主管审批，超过3天需总监审批」→ 完整 BPMN |
| 🔍 四重保障修正 | 白名单 → 元素类型校验 → 关键词匹配 → 角色名推断 |
| 📐 标准 BPMN 2.0 | 生成含 `bpmn:definitions`、`bpmn:process`、DI 布局的完整 XML |
| 🏷️ 智能节点分类 | 自动区分 start/form_fill/approval/condition/parallel/notify/system_action/end |
| 🔧 自动修复 | isExecutable 缺失 → 自动注入；start/end 缺失 → 自动补充 |

### 2. 流程设计器与执行引擎

| 功能 | 说明 |
|------|------|
| 🎨 可视化编辑器 | 基于 bpmn-js，拖拽式流程设计，属性面板配置 |
| 📋 模板版本管理 | 草稿 → 发布 → 停用 → 删除，完整生命周期 |
| 🏪 模板市场 | 8套种子模板，支持上架/下架/复制为自有模板 |
| 🔀 BPMN XML 增强 | 自动注入审批监听器、通知监听器，清理旧委托 |
| 🔗 条件分支 | 支持排他网关（exclusiveGateway）和并行网关（parallelGateway） |
| 🚀 一键部署 | 流程发布自动部署到 Flowable 引擎 |

### 3. 智能审批引擎

| 审批模式 | 说明 |
|---------|------|
| **单人审批** (SINGLE) | 任一审批人处理即可 |
| **会签** (ALL) | 所有审批人都需通过 |
| **或签** (ANY) | 任一审批人通过即通过 |

| 分配策略 | 说明 |
|---------|------|
| DIRECT_SUPERVISOR | 申请人的直属主管 |
| DEPARTMENT_MANAGER | 申请人所在部门的负责人 |
| ROLE_IN_APPLICANT_DEPT | 申请人部门内的指定工作流角色 |
| ROLE_IN_SPECIFIED_DEPT | 指定部门内的指定工作流角色 |
| SPECIFIC_USERS | 指定具体人员 |

| 驳回规则 | 说明 |
|---------|------|
| END_PROCESS | 驳回后结束流程 |
| BACK_TO_START | 驳回后回到发起节点重新填写 |
| BACK_TO_PREVIOUS | 驳回后回到上一节点 |

### 4. 表单设计系统

| 功能 | 说明 |
|------|------|
| 🤖 AI 生成 | 描述需求 → AI 生成 fieldList + formSchema |
| 🧹 审批字段过滤 | 自动识别并移除「审批意见」「审核结果」等审批类字段 |
| 🔗 跨字段校验 | 自动为 endDate 注入 ≥ startDate 的校验规则 |
| 🎨 可视化设计 | 拖拽式表单设计器，支持 text/textarea/number/date/select/upload 等类型 |
| 📎 节点绑定 | 表单与流程节点灵活绑定（node_form 模式） |

### 5. 超时管理与催办

| 功能 | 说明 |
|------|------|
| ⏱️ SLA 超时提醒 | 节点配置 remindAfter，超时自动发送提醒通知 |
| 🤖 自动处理 | 超时自动通过（auto_approve）/ 自动驳回（auto_reject） |
| 📢 催办 | 申请人可催办当前节点，通知同时发送给所有审批人和候选人 |
| 📊 监控面板 | 业务管理员查看自有流程的运行状态和异常标记 |

### 6. 实时通知

| 功能 | 说明 |
|------|------|
| 🔔 任务分配通知 | 审批人被分配任务时立即推送 |
| 📋 审批结果通知 | 审批通过/驳回后通知申请人 |
| 📡 WebSocket 推送 | 点对点精准推送，只推送给通知的 receiver |
| 🚫 去重机制 | 同一事件不重复创建通知 |
| 📨 站内通知中心 | 已读/未读标记，通知列表查询 |

---

## 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 运行环境 |
| Spring Boot | 3.2.x | 应用框架 |
| Spring Security | 6.x | 认证与授权 |
| Spring Data JPA | 3.2.x | ORM / 数据访问 |
| Flowable | 7.0.0 | 流程引擎 |
| MySQL | 8.0 | 主数据库 |
| H2 | 2.x | 测试数据库 |
| JWT | 0.12.x | 无状态认证 |
| WebSocket | - | 实时推送 |
| WebClient | - | DeepSeek API 调用（SSE 流式） |
| Lombok | - | 代码简化 |
| Jackson | - | JSON 序列化 |
| JUnit 5 | 5.10.x | 测试框架 |
| Mockito | 5.x | 模拟框架 |
| AssertJ | 3.x | 断言库 |
| Hibernate | 6.x | ORM 实现 |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5.x | 前端框架 |
| TypeScript | 5.x | 类型安全 |
| Vite | 5.x | 构建工具 |
| Element Plus | 2.x | UI 组件库 |
| bpmn-js | - | 流程设计器 |
| Pinia | 2.x | 状态管理 |
| Vue Router | 4.x | 路由管理 |
| Axios | 1.x | HTTP 客户端 |
| EventSource | - | SSE 流式接收 |

### AI 模型

| 模型 | 用途 |
|------|------|
| DeepSeek Chat | 流程生成 / 表单生成 / 审批建议 / 优化分析 / 对话助手 |

---

## 项目结构

```
An-AI-based-custom-process-management-system/
│
├── backend/                                    # Spring Boot 后端 (239 Java 文件)
│   ├── src/main/java/com/aiflow/
│   │   ├── AiFlowApplication.java              # 应用入口
│   │   ├── config/                             # 配置类 (13个)
│   │   │   ├── AiConfig.java                   #   DeepSeek API 配置
│   │   │   ├── SecurityConfig.java             #   Spring Security 配置
│   │   │   ├── JwtConfig.java                  #   JWT 配置
│   │   │   ├── FlowableDelegateConfig.java     #   Flowable 委托 Bean 注册
│   │   │   ├── DemoOrganizationInitializer.java #   演示组织初始化
│   │   │   └── MarketTemplateInitializer.java  #   模板市场种子数据
│   │   ├── controller/                         # REST 控制器 (27个)
│   │   │   ├── AuthController.java             #   认证（登录/退出/注册）
│   │   │   ├── TaskController.java             #   任务（待办/已办/审批/驳回/催办）
│   │   │   ├── MyProcessController.java        #   我的流程（CRUD/发布/停用）
│   │   │   ├── ProcessInstanceController.java  #   流程实例（发起/详情/流程图）
│   │   │   ├── ProcessTemplateController.java  #   系统模板管理
│   │   │   ├── FormTemplateController.java     #   表单模板管理
│   │   │   ├── AiAssistantController.java      #   AI 对话助手（SSE）
│   │   │   ├── AiApprovalController.java       #   AI 审批建议
│   │   │   ├── AiProcessController.java        #   AI 流程生成
│   │   │   ├── AiFormController.java           #   AI 表单生成
│   │   │   ├── AiOptimizationController.java   #   AI 流程优化
│   │   │   ├── NotificationController.java     #   通知中心
│   │   │   ├── StatisticsController.java       #   统计分析
│   │   │   ├── TemplateMarketController.java   #   模板市场
│   │   │   └── WorkflowRoleController.java     #   工作流角色管理
│   │   ├── service/                            # 服务接口 (30个)
│   │   │   ├── AiProcessService.java           #   ★ AI 流程生成
│   │   │   ├── AiFormService.java              #   ★ AI 表单生成
│   │   │   ├── AiApprovalService.java          #   ★ AI 审批建议
│   │   │   ├── AiOptimizationService.java      #   ★ AI 流程优化
│   │   │   ├── AiChatService.java              #   ★ AI 对话助手
│   │   │   ├── impl/                           #   服务实现 (27个)
│   │   │   │   ├── ProcessTemplateServiceImpl.java     #   模板生命周期管理
│   │   │   │   ├── ProcessInstanceServiceImpl.java     #   实例管理
│   │   │   │   ├── ApproverResolverServiceImpl.java    #   审批人解析
│   │   │   │   ├── RuleEvaluatorServiceImpl.java       #   审批规则评估
│   │   │   │   ├── BpmnXmlEnhancer.java                #   BPMN XML 增强器
│   │   │   │   ├── TaskTimeoutNotificationScheduler.java # 超时扫描调度
│   │   │   │   ├── TaskUrgeScheduler.java              #   催办调度
│   │   │   │   ├── StatisticsServiceImpl.java          #   统计分析
│   │   │   │   └── BusinessMonitoringServiceImpl.java  #   业务监控
│   │   │   └── support/
│   │   │       └── OptionalTableQuerySupport.java      #   可选表查询降级
│   │   ├── repository/                         # JPA Repository (20个)
│   │   ├── model/                              # 实体类 (20个)
│   │   │   ├── ProcessTemplate.java            #   流程模板
│   │   │   ├── ProcessInstance.java            #   流程实例
│   │   │   ├── ApprovalRecord.java             #   审批记录
│   │   │   ├── FormDefinition.java             #   表单定义
│   │   │   ├── Notification.java               #   通知
│   │   │   ├── AiAdviceRecord.java             #   AI 建议记录
│   │   │   ├── AiChatSession.java              #   AI 对话会话
│   │   │   └── AiChatMessage.java              #   AI 对话消息
│   │   ├── dto/                                # 数据传输对象 (56个)
│   │   ├── enums/                              # 枚举类
│   │   ├── entity/                             # 用户实体（MyBatis-Plus）
│   │   ├── mapper/                             # MyBatis Mapper
│   │   ├── security/                           # 安全组件
│   │   │   ├── JwtTokenProvider.java           #   JWT Token 生成与验证
│   │   │   ├── JwtAuthenticationFilter.java    #   JWT 认证过滤器
│   │   │   ├── DataPermissionInterceptor.java  #   MyBatis 数据权限拦截器
│   │   │   └── SecurityUtils.java              #   安全上下文工具
│   │   ├── flowable/                           # Flowable 扩展
│   │   │   ├── SingleAssigneeTaskListenerBridge.java        # 单人审批监听器
│   │   │   ├── MultiInstanceAssigneeExecutionListenerBridge.java # 会签监听器
│   │   │   └── TaskCreatedNotificationListenerBridge.java   # 任务创建通知监听器
│   │   ├── websocket/                          # WebSocket
│   │   │   └── NotificationWebSocketHandler.java  # 通知推送处理器
│   │   ├── scheduler/                          # 定时任务
│   │   ├── aspect/                             # AOP 切面
│   │   ├── annotation/                         # 自定义注解
│   │   ├── common/                             # 公共类
│   │   └── util/                               # 工具类
│   ├── src/main/resources/
│   │   ├── application.yml                     # 应用配置
│   │   └── seed/templates/                     # 种子模板 (8个)
│   │       ├── leave-request/                  #   请假申请
│   │       ├── business-trip/                  #   出差申请
│   │       ├── reimbursement/                  #   报销申请
│   │       ├── purchase-request/               #   采购申请
│   │       ├── repair-request/                 #   维修申请
│   │       ├── inspection/                     #   巡检申请
│   │       ├── work-report/                    #   工作报告
│   │       └── general-approval/               #   通用审批
│   ├── src/test/java/com/aiflow/              # 测试 (43个测试类，160个用例)
│   │   ├── config/                             #   初始化配置测试 (4个)
│   │   ├── controller/                         #   控制器测试 (3个)
│   │   ├── integration/                        #   集成测试 (8个)
│   │   ├── security/                           #   安全测试 (1个)
│   │   ├── service/                            #   服务测试 (12个, 含新增5个AI测试)
│   │   ├── service/impl/                       #   实现类测试 (14个)
│   │   └── websocket/                          #   WebSocket测试 (1个)
│   └── pom.xml                                 # Maven 配置
│
├── frontend/                                   # Vue 3 前端
│   ├── src/
│   │   ├── api/                                # API 封装 (22个)
│   │   │   ├── auth.ts                         #   认证接口
│   │   │   ├── process.ts                      #   流程接口
│   │   │   ├── task.ts                         #   任务接口
│   │   │   ├── ai.ts                           #   AI 接口
│   │   │   └── notification.ts                 #   通知接口
│   │   ├── views/                              # 页面组件 (29个)
│   │   │   ├── Login.vue                       #   登录
│   │   │   ├── Dashboard.vue                   #   仪表盘
│   │   │   ├── process-designer/               #   流程设计器
│   │   │   ├── form-designer/                  #   表单设计器
│   │   │   ├── process/                        #   流程发起与详情
│   │   │   ├── task/                           #   任务处理
│   │   │   └── ai/                             #   AI 助手面板
│   │   ├── components/                         # 通用组件
│   │   ├── router/                             # 路由配置
│   │   ├── stores/                             # Pinia 状态管理
│   │   ├── types/                              # TypeScript 类型定义
│   │   ├── App.vue                             # 根组件
│   │   └── main.ts                             # 入口文件
│   ├── package.json                            # npm 配置
│   └── vite.config.ts                          # Vite 配置
│
└── README.md                                   # 项目说明
```

---

## 快速开始

### 环境要求

| 工具 | 最低版本 |
|------|---------|
| JDK | 17+ |
| Maven | 3.8+ |
| MySQL | 8.0+ |
| Node.js | 18+ |
| npm | 9+ |
| DeepSeek API Key | - |

### 1. 克隆项目

```bash
git clone <repository-url>
cd An-AI-based-custom-process-management-system
```

### 2. 数据库准备

```sql
CREATE DATABASE aiflow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. 配置后端

编辑 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/aiflow?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: your_password
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false

ai:
  deepseek:
    api-key: your_deepseek_api_key       # DeepSeek API 密钥（必填）
    base-url: https://api.deepseek.com/v1
    model: deepseek-chat
    timeout-seconds: 60
    max-context-messages: 20

app:
  jwt:
    secret: your-jwt-secret-key           # JWT 签名密钥（必填，至少256位）
    expiration: 86400000                  # Token 有效期（毫秒），默认24小时
```

### 4. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端启动后访问：`http://localhost:8080`

首次启动会自动：
- 创建数据库表（JPA ddl-auto: update）
- 初始化演示组织结构和用户
- 加载8套种子流程模板到模板市场

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端启动后访问：`http://localhost:5173`

### 6. 验证

使用默认管理员账号登录：
- 用户名：`admin`
- 密码：`admin123`

---

## API 接口

### 认证与用户

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/auth/login` | 否 | 用户登录，返回 JWT Token |
| POST | `/api/auth/logout` | 是 | 退出登录 |
| POST | `/api/auth/register` | 否 | 用户注册 |
| GET | `/api/auth/me` | 是 | 获取当前用户信息 |

### AI 智能服务

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/ai/process/generate` | 是 | **AI 生成流程**（自然语言 → BPMN XML + 节点配置） |
| POST | `/api/ai/form/generate` | 是 | **AI 生成表单**（自然语言 → 字段配置 + 布局） |
| POST | `/api/ai/approval/suggest` | 是 | **AI 审批建议**（基于表单数据给出建议） |
| GET | `/api/ai/optimization/template/{id}` | 是 | **AI 流程优化分析**（历史指标 → 改进建议） |
| POST | `/api/ai/optimization/{id}/adopt` | 是 | 采纳 AI 优化建议 |
| POST | `/api/ai/chat/stream` | 是 | **AI 对话**（SSE 流式回复） |
| GET | `/api/ai/chat/sessions` | 是 | 获取对话会话列表 |
| POST | `/api/ai/chat/sessions` | 是 | 创建对话会话 |
| DELETE | `/api/ai/chat/sessions/{id}` | 是 | 删除对话会话 |
| GET | `/api/ai/chat/sessions/{id}/messages` | 是 | 获取会话消息历史 |

### 流程模板管理

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/process-templates` | 是(管理员) | 系统模板列表 |
| POST | `/api/process-templates` | 是(管理员) | 创建模板 |
| GET | `/api/process-templates/{id}` | 是 | 模板详情 |
| PUT | `/api/process-templates/{id}` | 是 | 更新模板 |
| DELETE | `/api/process-templates/{id}` | 是(管理员) | 删除系统模板 |
| POST | `/api/process-templates/{id}/publish` | 是 | 发布模板 |
| POST | `/api/process-templates/{id}/unpublish` | 是 | 停用模板 |
| POST | `/api/process-templates/{id}/next-version` | 是 | 创建下一版本（草稿） |

### 我的流程（业务管理员）

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/my-processes` | 是(biz_admin) | 我的流程列表 |
| POST | `/api/my-processes` | 是(biz_admin) | 创建业务流程 |
| PUT | `/api/my-processes/{id}` | 是(biz_admin) | 更新业务流程 |
| DELETE | `/api/my-processes/{id}` | 是(biz_admin) | 删除业务流程 |
| GET | `/api/my-processes/{id}/route-preview` | 是 | 审批路由预览 |

### 流程实例

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/process-instances/start` | 是 | 发起流程实例 |
| GET | `/api/process-instances/{id}` | 是 | 实例详情 |
| GET | `/api/process-instances/{id}/diagram` | 是 | 流程图（含当前节点高亮） |
| GET | `/api/process-instances/{id}/timeline` | 是 | 审批时间线 |
| GET | `/api/process-catalog/available` | 是 | 可发起流程目录 |

### 任务管理

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/tasks/todo` | 是 | 待办任务列表 |
| GET | `/api/tasks/done` | 是 | 已办任务列表 |
| GET | `/api/tasks/{id}` | 是 | 任务详情 |
| POST | `/api/tasks/{id}/approve` | 是 | 审批通过 |
| POST | `/api/tasks/{id}/reject` | 是 | 审批驳回 |
| POST | `/api/tasks/{id}/urge` | 是 | 催办任务 |
| GET | `/api/tasks/{id}/ai-suggestion` | 是 | 获取 AI 审批建议 |

### 表单管理

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/forms` | 是 | 表单列表 |
| POST | `/api/forms` | 是 | 创建表单 |
| GET | `/api/forms/{id}` | 是 | 表单详情 |
| PUT | `/api/forms/{id}` | 是 | 更新表单 |
| DELETE | `/api/forms/{id}` | 是(管理员) | 删除表单 |

### 通知中心

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/notifications` | 是 | 通知列表 |
| GET | `/api/notifications/{id}` | 是 | 通知详情 |
| PUT | `/api/notifications/{id}/read` | 是 | 标记已读 |
| DELETE | `/api/notifications/{id}` | 是 | 删除通知 |
| GET | `/api/notifications/unread-count` | 是 | 未读通知数 |

### 模板市场

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/market/templates` | 是 | 市场模板列表 |
| POST | `/api/market/templates/{id}/copy` | 是 | 复制为我的流程 |

### 统计分析

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/statistics/overview` | 是 | 全局统计概览 |
| GET | `/api/monitoring/instances` | 是(biz_admin) | 业务流程监控 |
| GET | `/api/monitoring/instances/{id}` | 是(biz_admin) | 实例详情监控 |

### 工作流角色

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/workflow-roles` | 是(管理员) | 工作流角色列表 |
| POST | `/api/workflow-roles` | 是(管理员) | 创建角色 |
| POST | `/api/workflow-roles/{id}/assign` | 是(管理员) | 分配角色成员 |

---

## 测试

项目共有 **43个测试类，160个测试用例**，采用 JUnit 5 + Mockito + AssertJ + H2 内存数据库。

### 测试分层

```
测试体系
├── 单元测试 (35个类)
│   ├── AI 服务测试 (5个类, 77个用例) ★ 新增
│   │   ├── AiProcessServiceTest      ← AI 流程构建引擎 (18用例)
│   │   ├── AiApprovalServiceTest     ← AI 审批建议生成 (14用例)
│   │   ├── AiOptimizationServiceTest ← AI 流程优化分析 (13用例)
│   │   ├── AiFormServiceTest         ← AI 表单生成 (17用例)
│   │   └── AiChatServiceTest         ← AI 对话服务 (15用例)
│   ├── 配置初始化测试 (4个类)
│   ├── 控制器测试 (3个类)
│   ├── 安全层测试 (1个类)
│   ├── 服务层测试 (18个类)
│   │   ├── 权限测试 (ProcessAuthorization/TaskAuthorization/Notification)
│   │   ├── 审批测试 (ApprovalRecord/ApprovalVariable/ApproverResolver)
│   │   ├── 流程测试 (ProcessTemplate/ProcessInstance/RuleEvaluator)
│   │   ├── 超时催办测试 (TaskTimeoutNotification/TaskUrge)
│   │   └── 业务功能测试 (Statistics/BusinessMonitoring/WorkflowRole等)
│   └── WebSocket测试 (1个类)
└── 集成测试 (8个类)
    ├── 审批流转集成 (单部门/跨部门/角色审批/审批网关)
    ├── 种子模板集成 (8套模板部署验证)
    ├── 安全集成 (Task/Notification 接口权限)
    └── JPA集成 (ApprovalRecord 时间线查询)
```

### 运行测试

```bash
# 运行全部测试
cd backend
mvn test

# 运行 AI 服务测试
mvn test -Dtest="AiProcessServiceTest,AiApprovalServiceTest,AiOptimizationServiceTest,AiFormServiceTest,AiChatServiceTest"

# 运行指定测试类
mvn test -Dtest="AiProcessServiceTest"

# 运行指定测试方法
mvn test -Dtest="AiProcessServiceTest#generatesValidProcessFromNaturalLanguageDescription"

# 跳过集成测试（仅单元测试）
mvn test -Dtest="!*IntegrationTest"
```

### 测试覆盖重点

| 测试维度 | 覆盖内容 |
|---------|---------|
| AI 调用链路 | WebClient mock → API 响应解析 → 后处理修正 → 持久化 |
| 权限控制 | 流程/任务/通知/表单/监控 → 所有者/角色/部门多维校验 |
| 边界条件 | null值/空列表/超长输入/API失败/Markdown清洗/幂等性 |
| 业务流程 | 模板生命周期/实例提交/多实例会签/驳回规则/超时自动处理 |
| 降级容错 | bottleneck_prediction表缺失回退/通知推送失败不影响主流程 |

---

## 权限体系

系统采用**四位一体**的权限模型：

### 1. 系统角色 (System Role)

| 角色 | 枚举值 | 权限范围 |
|------|--------|---------|
| 超级管理员 | `super_admin` | 全局权限：所有模板/实例/用户/统计 |
| 业务管理员 | `biz_admin` | 管辖范围内的流程模板管理、实例监控 |
| 普通用户 | `normal_user` | 发起流程、处理待办、查看自己的实例 |

### 2. 工作流角色 (Workflow Role)

| 属性 | 说明 |
|------|------|
| 角色编码 | 全局唯一大写编码（如 FINANCE_APPROVER） |
| 作用域 | `global`（全局）/ `department`（部门级） |
| 成员分配 | 用户 + 部门绑定（部门级角色必须指定部门） |

### 3. 流程发起权限 (Start Permission)

| 权限类型 | 说明 |
|---------|------|
| `ALL` | 所有人可发起 |
| `ROLE` | 指定传统角色可发起 |
| `SYSTEM_ROLE` | 指定系统角色可发起 |
| `DEPARTMENT` | 指定部门成员可发起 |
| `CREATOR_DEPARTMENT` | 流程创建者所在部门成员可发起 |
| `WORKFLOW_ROLE` | 指定工作流角色成员可发起 |

### 4. 数据权限 (Data Permission)

| 层级 | 说明 |
|------|------|
| MyBatis 拦截器 | SQL 级别自动追加 `deleted=0` 条件 |
| 部门隔离 | 业务管理员只能查看管辖部门的数据 |
| 所有者隔离 | 表单/模板自动绑定创建者，非所有者不可操作 |

---

## 默认数据

系统首次启动时自动初始化：

### 管理员账号

| 用户名 | 密码 | 角色 | 说明 |
|--------|------|------|------|
| `admin` | `admin123` | super_admin | 超级管理员 |

### 演示组织架构

```
默认组织 (root)
└── 总部 (hq)
    ├── 技术部 (tech)
    ├── 财务部 (finance)
    ├── 人事行政部 (hr)
    ├── 市场部 (market)
    └── 采购部 (purchase)
```

### 种子流程模板 (8套)

| 模板编码 | 模板名称 | 业务类型 | 审批流程 |
|---------|---------|---------|---------|
| `SYS_LEAVE_REQUEST` | 请假申请 | 人事 | 填写 → 直属主管审批 |
| `SYS_BUSINESS_TRIP` | 出差申请 | 人事 | 填写 → 部门经理审批 |
| `SYS_EXPENSE_REIMBURSEMENT` | 报销申请 | 财务 | 填写 → 部门经理审批 → 财务审批 |
| `SYS_PURCHASE_REQUEST` | 采购申请 | 采购 | 填写 → 部门审核 → 采购审批 → 财务会签 |
| `SYS_REPAIR_REQUEST` | 维修申请 | 后勤 | 填写 → 部门审批 → 后勤处理 |
| `SYS_INSPECTION_REQUEST` | 巡检申请 | 后勤 | 填写 → 部门审批 → 巡检分配 |
| `SYS_WORK_REPORT` | 工作报告 | 通用 | 填写 → 直属主管审阅 |
| `SYS_GENERAL_APPROVAL` | 通用审批 | 通用 | 填写 → 直属主管审批 |

---

## 许可证

MIT License

---

<div align="center">

**Built with ❤️ using Spring Boot, Vue 3, Flowable & DeepSeek AI**

</div>
