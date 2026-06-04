# AI Workflow MVP Backend

Spring Boot 3 + Java 17 + MyBatis-Plus + MySQL + Spring Security + JWT.

## 本地启动

1. 创建数据库并执行 `src/main/resources/sql/init.sql`
2. 修改 `src/main/resources/application.yml` 中的 MySQL 用户名和密码
3. 启动应用

```bash
mvn spring-boot:run
```

首次启动会自动创建默认管理员：

```text
username: admin
password: admin123
role: ADMIN
```

## 主要接口

```text
POST   /api/auth/login
GET    /api/user/me
GET    /api/templates
POST   /api/templates
GET    /api/templates/{id}
PUT    /api/templates/{id}
DELETE /api/templates/{id}
POST   /api/ai/generate-form
POST   /api/ai/generate-process
```
