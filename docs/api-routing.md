# API Routing

网关路由按设计文档中的 API 边界预留。

| 路径 | 目标服务 |
|---|---|
| `/api/v1/auth/**` | `campus-auth-service` |
| `/api/v1/users/**` | `campus-user-service` |
| `/api/v1/events/**` | `campus-event-service` |
| `/api/v1/feed/**` | `campus-feed-service` |
| `/api/v1/import/**` | `campus-import-service` |
| `/api/admin/**` | `campus-audit-service` |
| `/api/v1/search/**` | `campus-search-service` |
| `/api/v1/ai/**` | `campus-ai-service` |

