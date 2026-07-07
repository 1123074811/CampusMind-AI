# CampusMind AI

基于 AI Agent 的校园事件自动感知与信息聚合系统。

当前阶段只搭建项目架构，不包含业务代码。工程结构按设计文档拆分为：

- `campus-gateway`：Spring Cloud Gateway 网关层。
- `campus-auth-service`：认证与权限服务骨架。
- `campus-user-service`：用户与画像服务骨架。
- `campus-event-service`：校园事件聚合服务骨架。
- `campus-feed-service`：个性化信息流服务骨架。
- `campus-import-service`：文本、图片、雨课堂导入服务骨架。
- `campus-crawler-service`：公开网页采集任务服务骨架。
- `campus-audit-service`：后台审核纠错服务骨架。
- `campus-search-service`：搜索与问答路由服务骨架。
- `campus-ai-service`：Spring AI Agent 与向量检索服务骨架。
- `campus-common`：跨服务共享的基础包。
- `campus-admin-web`：Vue 3 + TypeScript 管理后台骨架。
- `campus-flutter-app`：Flutter 用户端骨架。
- `infra`：本地依赖服务与数据库初始化脚本。
- `docs`：架构说明与设计文档归档。

## 后端构建

```powershell
mvn clean package
```

## 本地基础设施

```powershell
docker compose -f infra/docker-compose.yml up -d
```

## 管理后台

```powershell
cd campus-admin-web
npm install
npm run dev
```

## 用户端 APP

```powershell
cd campus-flutter-app
flutter pub get
flutter analyze
flutter test
```
