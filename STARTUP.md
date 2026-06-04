# 基于AI的自定义流程管理系统 - 启动指南

## 已修复的问题

1. ✅ 修复了前端循环依赖问题
2. ✅ 优化了 Element Plus 配置
3. ✅ 简化了后端控制器代码
4. ✅ 更新了依赖版本兼容性

## 重新安装前端依赖

由于 package.json 已更新，请重新安装依赖：

```bash
cd frontend
rm -rf node_modules package-lock.json
npm install
```

## 常见错误及解决方案

### 1. 循环依赖错误
**问题**: `api/index.ts` 和 `stores/user.ts` 相互导入
**解决**: ✅ 已修复，api 模块不再导入 store

### 2. Element Plus 图标导入错误
**问题**: 图标组件全局注册导致类型错误
**解决**: ✅ 已修复，移除了全局图标注册

### 3. TypeScript 类型错误
**问题**: 类型声明缺失
**解决**: ✅ 已创建 `vite-env.d.ts` 类型声明文件

## 启动顺序

### 后端启动
```bash
cd backend
mvn clean install
mvn spring-boot:run
```

### 前端启动
```bash
cd frontend
npm install
npm run dev
```

## 验证

访问 `http://localhost:5173`，使用 `admin` / `123456` 登录。

如果还有报错，请告诉我具体的错误信息。
