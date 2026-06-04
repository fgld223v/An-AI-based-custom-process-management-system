# 基于AI的自定义流程管理系统

基于 AI 的自定义流程管理系统，支持可视化流程编辑、智能审批、表单设计等功能。

## 技术栈

### 后端
- Spring Boot 3.2.x
- Java 17
- Maven
- Spring Security + JWT 无状态认证
- Spring Data JPA + Hibernate
- MySQL 8.0
- Flowable 7.0.0（流程引擎）
- Lombok

### 前端
- Vue 3 + Vite
- TypeScript
- Element Plus
- Vue Router
- Pinia
- Axios
- bpmn-js（流程设计器）

## 项目结构

```
An-AI-based-custom-process-management-system/
├── backend/                      # Spring Boot 后端
│   ├── src/main/java/com/aiflow/
│   │   ├── config/               # 配置类（Security、JWT、DataInitializer）
│   │   ├── controller/           # REST API 控制器
│   │   │   ├── process/          # 流程相关接口
│   │   │   │   ├── ProcessDefinitionController.java
│   │   │   │   ├── ProcessInstanceController.java
│   │   │   │   └── TaskController.java
│   │   │   ├── AuthController.java
│   │   │   ├── FormTemplateController.java
│   │   │   └── ...
│   │   ├── service/              # 业务服务层
│   │   │   ├── process/          # 流程服务
│   │   │   │   ├── ProcessService.java
│   │   │   │   └── ProcessServiceImpl.java
│   │   │   ├── AuthService.java
│   │   │   └── ...
│   │   ├── repository/           # JPA Repository
│   │   ├── model/                # 实体类
│   │   ├── enums/                # 枚举类
│   │   └── util/                 # 工具类（JwtUtil）
│   ├── src/main/resources/
│   │   └── application.yml       # 应用配置
│   └── pom.xml                   # Maven 依赖配置
├── frontend/                     # Vue 前端
│   ├── src/
│   │   ├── api/                  # API 请求封装
│   │   ├── router/               # 路由配置
│   │   ├── stores/               # Pinia 状态管理
│   │   ├── views/                # 页面组件
│   │   │   ├── Login.vue         # 登录页面
│   │   │   ├── Dashboard.vue     # 仪表盘
│   │   │   ├── FormDesigner.vue  # 表单设计器
│   │   │   ├── ProcessDesigner.vue # 流程设计器
│   │   │   ├── StartProcess.vue  # 发起流程
│   │   │   ├── TaskList.vue      # 我的待办
│   │   │   └── TaskDetail.vue    # 任务详情
│   │   ├── App.vue               # 根组件
│   │   └── main.ts               # 入口文件
│   ├── index.html                # HTML 入口
│   ├── package.json              # npm 依赖配置
│   ├── vite.config.ts            # Vite 配置
│   └── tsconfig.json             # TypeScript 配置
└── README.md                     # 项目说明
```

## 功能特性

### 流程管理
- ✅ 流程定义部署与查询
- ✅ 流程实例启动与状态查询
- ✅ 待办任务列表
- ✅ 任务审批（通过/驳回）
- ✅ 审批记录保存

### 表单设计器
- ✅ 可视化表单设计
- ✅ 支持多种字段类型（文本、选择、日期等）
- ✅ 表单模板管理

### 流程设计器
- ✅ 基于 bpmn-js 的可视化设计
- ✅ 流程节点编辑
- ✅ 属性面板配置
- ✅ BPMN XML 导入/导出

### 权限管理
- ✅ 角色权限控制（SUPER_ADMIN、BIZ_ADMIN、NORMAL_USER）
- ✅ 方法级安全（@PreAuthorize）
- ✅ 前端路由守卫

## 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Node.js 18+
- npm 9+

## 启动步骤

### 1. 数据库准备

创建 MySQL 数据库：
```sql
CREATE DATABASE aiflow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 配置后端

修改 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/aiflow?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: your_password
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端服务将在 `http://localhost:8080` 启动。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端服务将在 `http://localhost:5173` 启动。

## 验证

1. 打开浏览器访问 `http://localhost:5173`
2. 使用测试账号登录：
   - 用户名：`admin`
   - 密码：`admin123`
3. 登录成功后会跳转到仪表盘

## API 接口

### 认证接口
| 接口 | 方法 | 认证 | 说明 |
|------|------|------|------|
| `/api/auth/login` | POST | 否 | 用户登录 |
| `/api/auth/logout` | POST | 是 | 用户退出 |

### 流程定义接口
| 接口 | 方法 | 认证 | 说明 |
|------|------|------|------|
| `/api/process-definitions/deploy` | POST | 是 | 部署流程定义 |
| `/api/process-definitions` | GET | 是 | 获取流程定义列表 |
| `/api/process-definitions/{id}/xml` | GET | 是 | 获取流程定义XML |

### 流程实例接口
| 接口 | 方法 | 认证 | 说明 |
|------|------|------|------|
| `/api/process-instances/start` | POST | 是 | 启动流程实例 |
| `/api/process-instances/{id}/status` | GET | 是 | 获取流程状态 |

### 任务接口
| 接口 | 方法 | 认证 | 说明 |
|------|------|------|------|
| `/api/tasks` | GET | 是 | 获取待办任务列表 |
| `/api/tasks/{id}/complete` | POST | 是 | 完成任务（审批） |

### 表单模板接口
| 接口 | 方法 | 认证 | 说明 |
|------|------|------|------|
| `/api/form-templates` | GET | 是 | 获取表单模板列表 |
| `/api/form-templates` | POST | 是(管理员) | 创建表单模板 |
| `/api/form-templates/{id}` | PUT | 是(管理员) | 更新表单模板 |
| `/api/form-templates/{id}` | DELETE | 是(管理员) | 删除表单模板 |

## 角色权限

| 角色 | 值 | 说明 |
|------|-----|------|
| SUPER_ADMIN | `super_admin` | 超级管理员，拥有所有权限 |
| BIZ_ADMIN | `biz_admin` | 业务管理员，可管理表单和流程 |
| NORMAL_USER | `normal_user` | 普通用户，可发起流程和处理待办 |

## 默认数据

系统启动时自动初始化：

**默认管理员**：
- 用户名：`admin`
- 密码：`admin123`
- 角色：超级管理员

## 许可证

MIT License
