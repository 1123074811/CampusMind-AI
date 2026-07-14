# CampusMind AI

基于 AI Agent 的校园事件自动感知与信息聚合系统。项目面向高校通知、课程、考试、作业、活动等信息分散且时效性强的问题，提供从多源采集、智能抽取、事件审核、个性化分发到语义检索的完整链路。

## 项目概览

CampusMind AI 采用前后端分离与微服务架构：

- 后端：Spring Boot 3 / Spring Cloud Gateway / Spring AI / MyBatis-Plus。
- 管理后台：Vue 3 + TypeScript + Vite。
- 用户端：Flutter。
- 数据基础设施：MySQL、Redis、MongoDB、PostgreSQL + PGVector。
- AI 能力：规则兜底 Agent、OpenAI 兼容 ChatModel 接入、向量文本与检索接口。

系统当前包含可运行的后端服务、管理后台页面、本地 Docker 基础设施、数据库初始化脚本和测试用例。

## 核心功能

- 多源事件感知：支持公开网页、用户文本、用户图片、雨课堂 JSON/Cookie 等导入路径。
- AI 认知抽取：从原始文本中抽取标题、类型、时间、地点、对象、标签、摘要等事件字段。
- 决策与检索规划：根据用户查询生成检索计划，支持后续语义检索和问答路由扩展。
- 事件聚合与审核：提供后台仪表盘、事件复核、确认发布、驳回、审计日志等能力。
- 个性化信息流：根据用户画像、年级、专业、订阅范围筛选校园事件。
- 网关统一入口：通过 Spring Cloud Gateway 聚合各服务 API，并提供 JWT 鉴权过滤。
- 本地一键开发：提供 `dev-start.ps1` / `dev-stop.ps1` 启停所有后端服务和管理后台。

## 目录结构

```text
CampusMind-AI
├── campus-gateway            # API 网关、路由、鉴权过滤
├── campus-auth-service       # 登录认证、JWT 签发、用户身份
├── campus-user-service       # 用户信息、画像、订阅偏好
├── campus-event-service      # 校园事件查询与详情
├── campus-feed-service       # 个性化信息流
├── campus-import-service     # 文本、图片、雨课堂数据导入
├── campus-crawler-service    # 公开网页采集与解析
├── campus-audit-service      # 管理后台审核、数据源、任务指标
├── campus-search-service     # 搜索、问答路由、决策 Agent 调用
├── campus-ai-service         # 认知 Agent、决策 Agent、向量服务
├── campus-common             # 通用响应、异常处理等公共能力
├── campus-admin-web          # Vue 管理后台
├── campus-flutter-app        # Flutter 用户端
├── infra                     # Docker Compose 与数据库初始化脚本
├── docs                      # 架构、路由、公共数据源文档
├── dev-start.ps1             # PowerShell 启动脚本
└── dev-stop.ps1              # PowerShell 停止脚本
```

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 网关 | Spring Cloud Gateway WebFlux |
| 后端服务 | Java 17、Spring Boot 3.5、Spring Cloud 2025、Maven |
| 持久层 | MyBatis-Plus、MySQL 8.4 |
| AI | Spring AI 1.1、OpenAI 兼容接口、规则 Agent、PGVector |
| 缓存/临时数据 | Redis 7.4 |
| 文档/原始数据 | MongoDB 8.0 |
| 向量库 | PostgreSQL 17 + pgvector |
| 管理后台 | Vue 3、TypeScript、Vite |
| 用户端 | Flutter 3.x |

## 服务与端口

| 服务 | 端口 | 说明 |
| --- | ---: | --- |
| `campus-gateway` | 8080 | 统一 API 入口 |
| `campus-auth-service` | 8081 | 认证服务 |
| `campus-user-service` | 8082 | 用户服务 |
| `campus-event-service` | 8083 | 事件服务 |
| `campus-feed-service` | 8084 | 信息流服务 |
| `campus-import-service` | 8085 | 导入服务 |
| `campus-crawler-service` | 8086 | 采集服务 |
| `campus-audit-service` | 8087 | 审核后台服务 |
| `campus-search-service` | 8088 | 搜索服务 |
| `campus-ai-service` | 8089 | AI 服务 |
| `campus-admin-web` | 5173 | 管理后台开发服务器 |

## 环境要求

- Windows PowerShell 5+ 或 PowerShell 7+
- JDK 17+，一键脚本默认使用 `C:\Program Files\Java\jdk-21`
- Maven 3.9+
- Node.js 20+ 与 npm
- Docker Desktop
- Flutter 3.4+（仅运行用户端时需要）

如本机 JDK 路径不同，请修改 `dev-start.ps1` 中的 `$JavaHome`。

## 配置说明

复制环境变量模板：

```powershell
Copy-Item .env.example .env
```

主要配置项：

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `MYSQL_HOST` / `MYSQL_PORT` | MySQL 地址 | `localhost:3306` |
| `MYSQL_DATABASE` | 业务数据库 | `campusmind` |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | 业务库账号 | `campusmind` / `<your-password>` |
| `REDIS_HOST` / `REDIS_PORT` | Redis 地址 | `localhost:6379` |
| `MONGODB_URI` | MongoDB 连接串 | `mongodb://localhost:27017/campusmind` |
| `PGVECTOR_HOST` / `PGVECTOR_PORT` | PGVector 地址 | `localhost:5432` |
| `OPENAI_API_KEY` | OpenAI 兼容模型 API Key | 空 |
| `OPENAI_BASE_URL` | OpenAI 兼容模型 Base URL | 空 |

AI 服务支持 `llm,local` Profile。开发时可复制本地模板：

```powershell
Copy-Item campus-ai-service\src\main\resources\application-local.yml.example campus-ai-service\src\main\resources\application-local.yml
```

然后在 `application-local.yml` 中填入真实 API Key。该文件已被 `.gitignore` 忽略。

## 快速启动

1. 确认本地基础服务可用：

开发阶段默认连接本机 MySQL、Redis、MongoDB、PostgreSQL/PGVector。请先确认这些服务已在本地启动，并已执行 `infra/mysql/init` 下的初始化脚本创建业务表和演示数据。

2. 一键启动 Docker 开发依赖、后端服务、管理后台和 Windows App：

在 PowerShell 执行：

```powershell
.\dev-start.ps1
```

脚本会：

- 清理 8080-8089、5173 端口上的旧进程。
- 检查后端 jar，缺失时自动执行 Maven 打包。
- 启动全部后端微服务。
- 启动 `campus-admin-web` Vite 开发服务器。
- 构建并打开 Windows Flutter App。
- 等待 `/actuator/health` 与前端服务可访问。
- 将日志写入 `logs/`。

3. 访问系统：

- 管理后台：http://localhost:5173
- API 网关：http://localhost:8080
- 健康检查：http://localhost:8080/actuator/health

4. 停止服务：

在 PowerShell 执行：

```powershell
.\dev-stop.ps1
```

## 手动启动

构建全部后端模块：

```powershell
mvn clean package
```

单独运行某个服务：

```powershell
mvn -pl campus-auth-service spring-boot:run
```

运行 AI 服务并启用大模型配置：

```powershell
mvn -pl campus-ai-service spring-boot:run -Dspring-boot.run.profiles=llm,local
```

启动管理后台：

```powershell
cd campus-admin-web
npm install
npm run dev
```

启动 Flutter 用户端：

```powershell
cd campus-flutter-app
flutter pub get
flutter analyze
flutter test
```

## API 路由

所有业务 API 推荐通过网关 `http://localhost:8080` 访问。

| 路径 | 目标服务 |
| --- | --- |
| `/api/v1/auth/**` | `campus-auth-service` |
| `/api/v1/users/**` | `campus-user-service` |
| `/api/v1/events/**` | `campus-event-service` |
| `/api/v1/feed/**` | `campus-feed-service` |
| `/api/v1/import/**` | `campus-import-service` |
| `/api/admin/crawler/**` | `campus-crawler-service` |
| `/api/admin/**` | `campus-audit-service` |
| `/api/v1/search/**` | `campus-search-service` |
| `/api/v1/ai/**` | `campus-ai-service` |

常用接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v1/auth/login` | 登录并返回访问令牌与轮换刷新令牌 |
| `POST` | `/api/v1/auth/refresh` | 使用单次刷新令牌续期会话 |
| `POST` | `/api/v1/auth/logout` | 注销并撤销当前会话 |
| `GET` | `/api/v1/users/me` | 获取当前用户信息 |
| `GET` | `/api/v1/users/me/export` | 导出当前用户数据 |
| `DELETE` | `/api/v1/users/me` | 校验密码后匿名化注销账号 |
| `PUT` | `/api/v1/users/me/profile` | 更新用户画像 |
| `GET` | `/api/v1/events/search` | 查询事件列表 |
| `GET` | `/api/v1/events/{id}` | 查询事件详情 |
| `GET` | `/api/v1/feed` | 获取个性化信息流 |
| `POST` | `/api/v1/import/text` | 导入文本 |
| `POST` | `/api/v1/import/image` | 导入图片信息 |
| `POST` | `/api/v1/import/rain/json` | 导入雨课堂 JSON |
| `DELETE` | `/api/v1/import/tasks/{taskId}/raw` | 删除本人导入任务的原始数据，保留私有事件 |
| `POST` | `/api/v1/import/rain/cookie` | 一次性 Cookie 导入（默认关闭，须取得书面授权后启用） |
| `GET` | `/api/v1/search` | 事件搜索 |
| `GET` | `/api/admin/dashboard` | 管理后台仪表盘 |
| `PUT` | `/api/admin/events/{id}/review` | 审核事件 |
| `POST` | `/api/admin/crawler/sources/{sourceId}/crawl` | 触发数据源采集 |
| `POST/PUT` | `/api/admin/sources` | 新增或维护数据源（管理员） |
| `POST` | `/api/v1/ai/cognition/extract` | AI 认知抽取 |
| `POST` | `/api/v1/ai/decision/plan` | AI 决策规划 |
| `POST` | `/api/v1/ai/vector/text` | 生成事件向量文本 |
| `POST` | `/api/v1/ai/vector/store` | 写入向量文本 |
| `POST` | `/api/v1/ai/vector/search` | 向量检索 |
| `POST` | `/api/v1/ai/chat` | ChatModel 对话 |

## 生产容器部署

生产编排只向宿主机暴露管理端 `80` 和网关 `8080`，其余微服务仅在 Docker 网络内通信。先在 `.env` 配置数据库、JWT、真实模型和 PGVector 凭据，再执行：

```powershell
docker compose -f infra/docker-compose.yml -f infra/docker-compose.app.yml up -d --build
```

Android release 构建必须通过 `CAMPUSMIND_KEYSTORE_PATH`、`CAMPUSMIND_KEYSTORE_PASSWORD`、`CAMPUSMIND_KEY_ALIAS`、`CAMPUSMIND_KEY_PASSWORD` 注入签名；缺少任一项时 release 构建会明确失败，debug 构建不受影响。

校方单点登录采用标准 OIDC。为认证服务启用 `sso` profile，并配置 `CAMPUS_OIDC_ISSUER_URI`、`CAMPUS_OIDC_CLIENT_ID`、`CAMPUS_OIDC_CLIENT_SECRET` 与 `CAMPUS_OIDC_APP_CALLBACK_URL`。登录入口为 `/oauth2/authorization/campus`；回调只携带 60 秒一次性 code，客户端再调用 `/api/v1/auth/sso/exchange` 换取会话，访问令牌不会出现在 URL 中。SSO 用户须先在用户目录中开通。

## 示例请求

登录：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/v1/auth/login `
  -ContentType application/json `
  -Body '{"username":"admin_seed","password":"admin123"}'
```

查看管理后台数据：

```powershell
Invoke-RestMethod http://localhost:8080/api/admin/dashboard
```

AI 文本抽取：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/v1/ai/cognition/extract `
  -ContentType application/json `
  -Body '{"text":"7月8日19:00，软件学院将在图书馆报告厅举办人工智能主题讲座，面向软件学院本科生。"}'
```

## 数据库

本地 Docker Compose 会启动：

- MySQL：业务主库，初始化脚本位于 `infra/mysql/init`。
- Redis：缓存、限流、临时 Cookie。
- MongoDB：原始文档、导入材料等非结构化数据。
- PostgreSQL + PGVector：向量检索实验环境。

MySQL 初始化脚本说明：

- `001_schema.sql`：业务表结构。
- `002_admin_seed.sql`：后台演示账号、事件、数据源、任务、审计日志。
- `003_public_sources.sql`：新疆大学与软件学院公开网页数据源。
- `004_web_crawl_item.sql` / `005_web_crawl_item_detail.sql`：网页采集结果表。

如果需要重新执行初始化脚本，需要删除对应 Docker volume 后重新启动容器。

## 测试与质量检查

运行后端测试：

```powershell
mvn test
```

跳过测试打包：

```powershell
mvn clean package -DskipTests
```

检查管理后台构建：

```powershell
cd campus-admin-web
npm run build
```

检查 Flutter 用户端：

```powershell
cd campus-flutter-app
flutter analyze
flutter test
```

## 开发约定

- 后端包名统一为 `cn.campusmind.<service>`。
- 服务内部按 `config`、`controller`、`application`、`domain`、`infrastructure` 分层。
- 持久层统一使用 MyBatis-Plus，Mapper 放在 `infrastructure.mapper`。
- 跨服务公共能力放入 `campus-common`，避免引入不稳定业务逻辑。
- API 响应使用 `campus-common` 中的统一响应结构。
- 本地敏感配置写入 `application-local.yml` 或环境变量，不提交真实密钥。

## 常见问题

### Docker 镜像拉取慢

`.env.example` 中提供了 `MYSQL_IMAGE=docker.1ms.run/mysql:8.4` 示例，可按需修改为可用镜像源，或在 Docker Desktop 中配置 registry mirror。

### 一键脚本找不到 Java

修改 `dev-start.ps1`：

```powershell
$JavaHome = "你的 JDK 路径"
```

### 服务健康检查未全部通过

查看 `logs/` 下对应服务日志，例如：

```powershell
Get-Content logs\campus-ai-service-err.log -Tail 80
```

常见原因包括数据库未启动、端口被占用、AI 服务缺少 API Key、JDK 路径不正确。

### 管理后台显示演示数据

`campus-admin-web` 会请求 `/api/admin/dashboard`。当后端不可用或接口返回异常时，页面会自动回退到内置演示数据。确认基础设施和后端服务启动后，刷新页面即可连接真实接口。

## 相关文档

- `docs/architecture.md`：架构与模块职责。
- `docs/api-routing.md`：网关路由说明。
- `docs/public-web-sources.md`：公开网页数据源。
- `docs/roadmap.md`：后续功能预留与产品路线图。
- `校园事件AI感知聚合系统_Design_Document.md`：项目设计文档。
