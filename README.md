# AI 流程管理系统

基于 AI 的自定义流程管理系统，支持自然语言生成流程、可视化流程编辑、智能审批等功能。

## 技术栈

### 后端
- Spring Boot 3.2.x
- Java 17
- Maven
- Spring Security + JWT 无状态认证
- Spring Data JPA + Hibernate
- MySQL 8.0
- Redis 7.x（Token 黑名单）
- Flowable 7.0.0（流程引擎）
- Lombok

### 前端
- Vue 3 + Vite
- TypeScript
- Element Plus
- Vue Router
- Pinia
- Axios

## 项目结构

```
An-AI-based-custom-process-management-system/
├── ai-flow-platform/           # Maven 后端模块
│   ├── backend/
│   │   ├── src/main/java/com/aiflow/
│   │   │   ├── config/          # 配置类（Security、JWT、Redis、DataInitializer）
│   │   │   ├── controller/      # REST API 控制器
│   │   │   ├── service/         # 业务服务层
│   │   │   ├── repository/      # JPA Repository（20个）
│   │   │   ├── model/           # 实体类（20个）
│   │   │   ├── enums/           # 枚举类（18个）
│   │   │   └── util/            # 工具类（JwtUtil）
│   │   ├── src/main/resources/
│   │   │   └── application.yml  # 应用配置
│   │   └── pom.xml              # Maven 依赖配置
│   └── target/                  # 编译输出
├── frontend/                    # Vue 前端模块
│   ├── src/
│   │   ├── api/                 # API 请求封装
│   │   ├── router/              # 路由配置
│   │   ├── stores/              # Pinia 状态管理
│   │   ├── views/               # 页面组件（Login、Dashboard）
│   │   ├── App.vue              # 根组件
│   │   └── main.ts              # 入口文件
│   ├── index.html               # HTML 入口
│   ├── package.json             # npm 依赖配置
│   ├── vite.config.ts           # Vite 配置
│   └── tsconfig.json            # TypeScript 配置
├── README.md                    # 项目说明
├── STARTUP.md                   # 启动指南
└── 枢 Pivot (standalone).html   # 前端原型设计
```

## 数据库实体清单

| 模块 | 文件数 | 说明 |
|------|--------|------|
| 实体类 | 20 | Department, User, BizTypeDict, ProcessTemplate 等 |
| 枚举类 | 18 | SystemRole, UserStatus, TemplateStatus 等 |
| Repository | 20 | 各实体对应的 JPA Repository |

## 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 7.x+
- Node.js 18+
- npm 9+

## 启动步骤

### 1. 数据库准备

创建 MySQL 数据库：
```sql
CREATE DATABASE aiflow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 配置后端

修改 `ai-flow-platform/backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/aiflow?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: your_password

  data:
    redis:
      host: localhost
      port: 6379
```

### 3. 启动后端

```bash
cd ai-flow-platform/backend
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

| 接口 | 方法 | 认证 | 说明 |
|------|------|------|------|
| `/api/hello` | GET | 否 | 健康检查 |
| `/api/auth/login` | POST | 否 | 用户登录 |
| `/api/hello-auth` | GET | 是 | 认证测试 |

## 默认数据

系统启动时自动初始化：

**业务类型**（biz_type_dict 表）：
- 一级分类：人事行政类、财务类、后勤类、管理类
- 二级分类：请假、加班、考勤、报销、报修

**默认管理员**：
- 用户名：`admin`
- 密码：`admin123`
- 角色：超级管理员

## 许可证

MIT License
