# Architecture

本文件记录当前工程骨架与设计文档的映射关系。

## 服务拆分

| 工程模块 | 设计文档职责 |
|---|---|
| `campus-gateway` | 网关层：鉴权、限流、路由、审计入口 |
| `campus-auth-service` | 认证、JWT、后台 RBAC 的承载模块 |
| `campus-user-service` | 用户、画像、订阅信息 |
| `campus-event-service` | 校园事件主数据、来源引用、发布状态 |
| `campus-feed-service` | 个性化信息流、缓存编排、推荐结果承载 |
| `campus-import-service` | 文本、图片、雨课堂 JSON/Cookie 导入 |
| `campus-crawler-service` | 公开网页数据源、robots、限速、采集任务 |
| `campus-audit-service` | 事件审核、纠错、合并、下架与审计日志 |
| `campus-search-service` | 条件检索、语义检索、问答路由入口 |
| `campus-ai-service` | 感知、认知、决策 Agent 与向量服务 |
| `campus-common` | 跨服务共享基础能力，后续只放稳定契约 |

## 包结构约定

每个服务后续按以下方向扩展：

```text
cn.campusmind.<service>
├── config
├── controller
├── application
├── domain
├── infrastructure
└── interfaces
```

当前只创建启动类和少量占位目录，避免在架构阶段提前沉淀业务代码。

## 数据层

- MySQL：`infra/mysql/init/001_schema.sql`，所有业务服务统一使用 MyBatis-Plus 访问关系型数据库。
- MongoDB：由服务连接后创建集合和索引
- Redis：缓存、限流、锁、临时 Cookie
- PGVector：`infra/postgres/init/001_pgvector.sql`

## 持久层约定

- Spring Boot 3 服务统一引入 `mybatis-plus-spring-boot3-starter`，禁止再新增 JPA Repository。
- Mapper 接口放在 `cn.campusmind.<service>.infrastructure.mapper`，继承 MyBatis-Plus `BaseMapper<T>`。
- 实体对象按数据库表结构使用 MyBatis-Plus 注解声明表名、主键和下划线字段映射。
- 简单 CRUD 优先使用 `BaseMapper` 和 `LambdaQueryWrapper`，跨表或复杂查询再编写 XML/注解 SQL。

## 端口规划

| 服务 | 端口 |
|---|---:|
| Gateway | 8080 |
| Auth | 8081 |
| User | 8082 |
| Event | 8083 |
| Feed | 8084 |
| Import | 8085 |
| Crawler | 8086 |
| Audit | 8087 |
| Search | 8088 |
| AI | 8089 |
