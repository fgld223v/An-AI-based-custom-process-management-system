# 基于AI的自定义流程管理系统

基于 AI 的自定义流程管理系统，支持自然语言生成流程、可视化流程编辑、智能审批等功能。

## 技术栈

### 后端
- Spring Boot 3.2.x
- Java 17
- Maven
- Spring Security + JWT
- Spring Data JPA
- MySQL 8.0
- Redis 7.x
- Flowable 7.0.0
- Lombok

### 前端
- Vue 3
- Vite
- TypeScript
- Element Plus
- Vue Router
- Pinia
- Axios
- bpmn-js

## 项目结构

```
ai-flow-platform/
├── backend/    # Spring Boot 后端
│   ├── src/main/java/com/aiflow/
│   │   ├── config/       # 配置类
│   │   ├── controller/   # 控制器
│   │   ├── model/        # 实体和 DTO
│   │   ├── repository/   # JPA Repository
│   │   ├── service/      # 服务层
│   │   └── util/         # 工具类
│   └── src/main/resources/
└── frontend/   # Vue 3 前端
    ├── src/
    │   ├── api/          # API 请求
    │   ├── router/       # 路由
    │   ├── stores/       # Pinia 状态管理
    │   └── views/        # 页面组件
    └── ...
```

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

修改 `backend/src/main/resources/application.yml` 中的数据库和 Redis 连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/aiflow?...
    username: root
    password: your_password

  data:
    redis:
      host: localhost
      port: 6379
```

### 3. 启动后端

```bash
cd backend
mvn clean install
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
   - 密码：`123456`
3. 登录成功后会跳转到仪表盘，显示欢迎信息

## 默认接口

- `GET /api/hello` - 测试接口（无需认证）
- `POST /api/auth/login` - 登录接口
- `GET /api/hello-auth` - 认证测试接口（需认证）

## 测试用户

系统启动时会自动创建测试用户：
- 用户名：`admin`
- 密码：`123456`
