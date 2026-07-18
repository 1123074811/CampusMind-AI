# CampusMind AI 配置说明

本文档对应当前仓库的开发脚本、Spring 配置、Docker Compose、Vue 管理端和 Flutter 用户端。示例均以 Windows PowerShell 和项目根目录为工作目录。

## 1. 配置来源与优先级

配置优先级从高到低为：

1. Java 启动参数，例如 `--spring.profiles.active=llm,pg`。
2. 当前进程环境变量。
3. 项目根目录 `.env`。
4. `application-{profile}.yml`。
5. `application.yml` 中的默认值。

`dev-start.ps1` 会读取根目录 `.env`，但不会覆盖已经存在的进程环境变量。因此，临时测试某个配置时可以先在当前 PowerShell 会话中赋值：

```powershell
$env:CAMPUS_AI_MODE = "llm"
.\dev-start.ps1
```

Docker Compose 使用根目录 `.env` 进行变量替换。Spring 服务不会直接读取 `.env`；宿主机开发模式由 `dev-start.ps1` 将其中的值转换为服务环境变量。

## 2. 推荐开发拓扑

当前推荐方式是“基础设施运行在 Docker，应用运行在宿主机”：

| 组件 | 宿主机地址 | 容器内地址 |
| --- | --- | --- |
| MySQL | `localhost:13306` | `mysql:3306` |
| Redis | `localhost:16379` | `redis:6379` |
| MongoDB | `localhost:27018` | `mongo:27017` |
| PostgreSQL/PGVector | `localhost:15432` | `postgres:5432` |
| Nacos | `localhost:8848` | `nacos:8848` |
| API 网关 | `localhost:8080` | `campus-gateway:8080` |
| 管理端 | `localhost:5173` | 生产编排为宿主机 `80` |

`dev-start.ps1` 会强制后端使用上述 Docker 映射端口，而不是 `.env` 中的本机默认端口。只有手动启动服务时，`MYSQL_PORT=3306`、`REDIS_PORT=6379` 等本机端口才直接生效。

## 3. 首次配置

复制模板：

```powershell
Copy-Item .env.example .env
```

至少替换以下占位值：

```dotenv
MYSQL_ROOT_PASSWORD=<strong-root-password>
MYSQL_PASSWORD=<strong-app-password>
PGVECTOR_PASSWORD=<strong-pg-password>
AUTH_JWT_SECRET=<at-least-32-random-bytes>
```

可使用 PowerShell 生成随机 JWT 密钥：

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
```

不要提交 `.env`、证书、keystore、SMTP 密码或模型 API Key。

## 4. 基础设施配置

| 变量 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `MYSQL_HOST` | 否 | `localhost` | 手动启动服务时的 MySQL 主机 |
| `MYSQL_PORT` | 否 | `3306` | 本机 MySQL 端口；一键脚本会改用 Docker 映射端口 |
| `MYSQL_DOCKER_PORT` | 否 | `13306` | Docker MySQL 暴露到宿主机的端口 |
| `MYSQL_DATABASE` | 否 | `campusmind` | 业务数据库名 |
| `MYSQL_USERNAME` | 否 | `campusmind` | 应用数据库账号 |
| `MYSQL_PASSWORD` | 是 | 无 | 应用数据库密码 |
| `MYSQL_ROOT_PASSWORD` | 是 | 无 | 首次创建数据库和应用账号时使用 |
| `MYSQL_IMAGE` | 否 | `mysql:8.4` | MySQL 镜像，可替换为内部镜像源 |
| `REDIS_HOST` | 否 | `localhost` | 手动启动时的 Redis 主机 |
| `REDIS_PORT` | 否 | `6379` | 手动启动时的 Redis 端口 |
| `REDIS_DOCKER_PORT` | 否 | `16379` | Docker Redis 宿主机端口 |
| `REDIS_PASSWORD` | 否 | 空 | Redis 密码；当前 Compose 默认未启用密码 |
| `MONGODB_URI` | 否 | `mongodb://localhost:27017/campusmind` | 手动启动导入服务时使用 |
| `MONGO_DOCKER_PORT` | 否 | `27018` | Docker MongoDB 宿主机端口 |
| `IMPORT_MONGO_URI` | 是 | 无 | 导入服务实际使用的 MongoDB URI；一键脚本自动生成 |
| `PGVECTOR_HOST` | 否 | `localhost` | PGVector 主机 |
| `PGVECTOR_PORT` | 否 | `5432` | 手动启动端口 |
| `PGVECTOR_DOCKER_PORT` | 否 | `15432` | Docker PGVector 宿主机端口 |
| `PGVECTOR_DATABASE` | 否 | `campusmind_vector` | 向量数据库名 |
| `PGVECTOR_USERNAME` | 否 | `campusmind` | 向量数据库账号 |
| `PGVECTOR_PASSWORD` | LLM 模式必填 | 无 | 向量数据库密码 |
| `NACOS_SERVER_ADDR` | 否 | `127.0.0.1:8848` | 服务发现地址 |
| `NACOS_NAMESPACE` | 否 | `public` | Nacos 命名空间 |
| `NACOS_GROUP` | 否 | `DEFAULT_GROUP` | Nacos 服务分组 |
| `NACOS_IMAGE` | 否 | `nacos/nacos-server:v2.4.3` | Nacos 镜像 |

### 服务级数据库覆盖

手动启动或自定义部署时，每个访问 MySQL 的服务都需要自己的连接变量：

```text
AUTH_DB_URL / AUTH_DB_USERNAME / AUTH_DB_PASSWORD
USER_DB_URL / USER_DB_USERNAME / USER_DB_PASSWORD
EVENT_DB_URL / EVENT_DB_USERNAME / EVENT_DB_PASSWORD
FEED_DB_URL / FEED_DB_USERNAME / FEED_DB_PASSWORD
IMPORT_DB_URL / IMPORT_DB_USERNAME / IMPORT_DB_PASSWORD
CRAWLER_DB_URL / CRAWLER_DB_USERNAME / CRAWLER_DB_PASSWORD
AUDIT_DB_URL / AUDIT_DB_USERNAME / AUDIT_DB_PASSWORD
SEARCH_DB_URL / SEARCH_DB_USERNAME / SEARCH_DB_PASSWORD
```

`dev-start.ps1` 和 `infra/docker-compose.app.yml` 会根据通用的 `MYSQL_*` 变量自动生成这些值，一般不需要在 `.env` 重复填写。

Redis 同理支持 `AUTH_`、`USER_`、`IMPORT_`、`CRAWLER_`、`GATEWAY_`、`AI_` 前缀的 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD` 和 `REDIS_DATABASE`。AI 短期会话默认保留 24 小时，可用 `CAMPUS_AI_SHORT_TERM_MEMORY_TTL`（ISO-8601 Duration）调整；导入服务默认使用 Redis DB 8，其余服务默认 DB 0。

## 5. 认证、会话与密码找回

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AUTH_JWT_ISSUER` | `campusmind-auth` | JWT 签发者，网关和所有业务服务必须一致 |
| `AUTH_JWT_SECRET` | 无 | JWT HMAC 密钥，至少 32 字节 |
| `AUTH_JWT_ACCESS_TTL_MINUTES` | `15` | Access Token 有效期 |
| `AUTH_JWT_REFRESH_TTL_DAYS` | `30` | Refresh Token 有效期 |
| `AUTH_COOKIE_SECURE` | `false` | HTTPS 生产环境必须设为 `true` |
| `AUTH_RESET_TOKEN_TTL_MINUTES` | `30` | 密码重置令牌有效期 |
| `AUTH_RESET_URL` | `http://localhost:5173/reset-password` | 邮件中的重置入口；生产必须指向实际重置页面或 App 深链 |
| `AUTH_DEV_EXPOSE_RESET_TOKEN` | `false` | 仅本地无 SMTP 调试可设为 `true`；生产必须为 `false` |

移动端使用 Bearer Token 和轮换 Refresh Token。管理端使用 `HttpOnly` Cookie，不把访问令牌存入 `localStorage`。注销、改密、禁用账号和删除账号都会撤销已有会话。

当前仓库提供找回与重置 API，以及 Flutter 内的开发期令牌输入流程，但没有独立的 Web `/reset-password` 页面。启用生产邮件前，部署方需要提供重置落地页或 App 深链，并把 `AUTH_RESET_URL` 指向该入口。

### SMTP

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AUTH_MAIL_ENABLED` | `false` | 是否发送密码找回邮件，同时控制邮件健康检查 |
| `AUTH_MAIL_HOST` | `localhost` | SMTP 主机 |
| `AUTH_MAIL_PORT` | `25` | SMTP 端口 |
| `AUTH_MAIL_USERNAME` | 空 | SMTP 用户名 |
| `AUTH_MAIL_PASSWORD` | 空 | SMTP 密码 |
| `AUTH_MAIL_FROM` | `no-reply@campusmind.local` | 发件人 |

生产配置示例：

```dotenv
AUTH_MAIL_ENABLED=true
AUTH_MAIL_HOST=smtp.example.edu.cn
AUTH_MAIL_PORT=587
AUTH_MAIL_USERNAME=campusmind@example.edu.cn
AUTH_MAIL_PASSWORD=<secret>
AUTH_MAIL_FROM=campusmind@example.edu.cn
AUTH_RESET_URL=https://campusmind.example.edu/reset-password
AUTH_COOKIE_SECURE=true
AUTH_DEV_EXPOSE_RESET_TOKEN=false
```

## 6. 校园统一身份认证（OIDC）

启用认证服务的 `sso` profile，并配置：

| 变量 | 说明 |
| --- | --- |
| `CAMPUS_OIDC_ISSUER_URI` | 校方 OIDC Issuer 地址 |
| `CAMPUS_OIDC_CLIENT_ID` | 客户端 ID |
| `CAMPUS_OIDC_CLIENT_SECRET` | 客户端密钥 |
| `CAMPUS_OIDC_APP_CALLBACK_URL` | SSO 成功后返回管理端或 App 的地址 |

宿主机单独启动示例：

```powershell
java -jar campus-auth-service\target\campus-auth-service-0.1.0-SNAPSHOT.jar `
  --spring.profiles.active=sso
```

容器部署时，只给 `campus-auth-service` 增加：

```yaml
environment:
  SPRING_PROFILES_ACTIVE: sso
```

登录入口为 `/oauth2/authorization/campus`。回调使用短期一次性 code，再通过 `/api/v1/auth/sso/exchange` 换取会话。

## 7. 隐私与数据生命周期

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `PRIVACY_POLICY_VERSION` | `2026-07-01` | 当前隐私政策版本；政策变更时必须更新 |
| `PRIVACY_RETENTION_DAYS` | `365` | 通知投递和信息变更操作记录保留天数，最小 30 天 |
| `PRIVACY_CLEANUP_CRON` | `0 20 3 * * *` | 每日清理任务 Cron，默认 03:20 |

用户端支持隐私授权、个性化授权与通知授权的记录和撤回，并提供个人数据导出和账号匿名化删除。政策文本变化时应同时更新 `PRIVACY_POLICY_VERSION`，不能只修改页面文案。

## 8. 通知配置

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `FEED_REMINDER_CHECK_INTERVAL_MS` | `60000` | 到期提醒扫描间隔 |
| `FEED_SOURCE_SUBSCRIPTION_WEIGHT` | `100` | 已订阅数据源的排序权重 |
| `FEED_PUSH_WEBHOOK_URL` | 空 | 推送供应商适配器地址；为空时仍保留站内通知 |

Webhook 接收 JSON：

```json
{
  "token": "device-push-token",
  "title": "CampusMind 待办提醒",
  "body": "提醒标题",
  "url": "原始信息地址",
  "reminderId": 123
}
```

适配器应返回 2xx；可用响应头 `X-Message-Id` 返回供应商消息 ID。失败会进入投递账本并按指数退避重试，最多 5 次。正式移动推送还需要 App 侧接入 FCM、APNs 或厂商 SDK，将真实 `pushToken` 注册到设备接口。

## 9. AI 模式

### 规则模式（默认）

```dotenv
CAMPUS_AI_MODE=rule
SPRING_AI_CHAT_MODEL=none
SPRING_AI_EMBEDDING_MODEL=none
```

规则模式不需要模型 API、PGVector 或模型下载，适合本地页面和业务流程开发。搜索接口会明确返回降级元数据，前端会标记关键字降级结果。

采集服务默认 `CAMPUS_AI_REQUIRE_LLM=true`，因此规则抽取不会被伪装成完整 LLM 结果。没有配置真实 LLM 时，相关信息会保留待人工处理状态。

### LLM + PGVector 模式

```dotenv
CAMPUS_AI_MODE=llm
OPENAI_API_KEY=<secret>
OPENAI_BASE_URL=https://api.deepseek.com
OPENAI_CHAT_MODEL=deepseek-chat
PGVECTOR_DATABASE=campusmind_vector
PGVECTOR_USERNAME=campusmind
PGVECTOR_PASSWORD=<secret>
TAVILY_API_KEY=<secret>
```

`dev-start.ps1` 检测到 `CAMPUS_AI_MODE=llm` 后会：

- 启动 PostgreSQL/PGVector 容器。
- 使用 `llm,pg` profiles 启动 AI 服务。
- 使用真实 ChatModel 和 384 维多语言 Transformer Embedding。

相关可选变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `OPENAI_TEMPERATURE` | `0.2` | 模型温度 |
| `CAMPUS_AI_MODEL_VERSION` | 当前模型名或 `rule-v1` | 记录模型版本 |
| `CAMPUS_AI_PROMPT_VERSION` | 规则模式 `rule-v1`、LLM 模式 `llm-v1` | 记录提示词版本 |
| `CAMPUS_AI_REQUIRE_LLM` | `true` | 采集结果是否必须来自 LLM |
| `TAVILY_API_KEY` | 空 | Tavily 联网搜索密钥；为空时禁用联网工具 |
| `TAVILY_BASE_URL` | `https://api.tavily.com` | Tavily 兼容 API 地址 |
| `CAMPUS_AI_WEB_SEARCH_ENABLED` | `true` | 是否允许智能体联网搜索 |
| `CAMPUS_AI_WEB_SEARCH_MAX_RESULTS` | `5` | 单次最多返回网页结果，限制为 1–10 |
| `CAMPUS_AI_BACKFILL_INITIAL_DELAY_MS` | `10000` | AI 回填首次延迟 |
| `CAMPUS_AI_BACKFILL_DELAY_MS` | `60000` | AI 回填扫描间隔 |
| `TRANSFORMER_MODEL_URI` | 固定版本 Hugging Face URI | ONNX 模型地址，可改为 `file:` URI |
| `TRANSFORMER_TOKENIZER_URI` | 固定版本 Hugging Face URI | Tokenizer 地址 |
| `TRANSFORMER_CACHE_DIR` | `${user.home}/.campusmind/models` | 模型缓存目录 |

首次启用 `llm,pg` 可能需要下载模型。离线环境应预先下载模型，并通过 `file:` URI 配置。

## 10. 导入、OCR 与采集

### 导入服务

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `IMPORT_RAIN_COOKIE_ENABLED` | `false` | 是否允许一次性雨课堂 Cookie 导入；没有书面授权不要开启 |
| `XJU_EHALL_ENABLED` | `false` | 是否开放新疆大学一站式大厅同步；生产默认关闭 |
| `XJU_EHALL_LOGIN_URL` | `https://ehall.xju.edu.cn/new/index.html` | 学校官方登录入口，只允许 HTTPS |
| `XJU_EHALL_DATA_HOSTS` | 空 | 真实环境确认的数据域名，逗号分隔；为空时即使误开开关也不可用 |
| `XJU_EHALL_POLICY_VERSION` | `2026-07-18-v1` | 教务数据同步专项授权版本 |
| `TESSDATA_PATH` | `C:/Program Files/Tesseract-OCR/tessdata` | Tesseract 语言数据目录 |
| `OCR_LANGUAGE` | `chi_sim+eng` | OCR 语言 |
| `SENTINEL_DASHBOARD` | `localhost:8858` | 可选 Sentinel 控制台地址 |

内置限制：单图 5 MiB、单文件 10 MiB、请求 15 MiB、文本 20,000 字符、雨课堂 JSON 2 MiB、每用户每分钟 10 次导入。原始导入文档默认保留 7 天。

### 采集服务

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `CRAWLER_HOURLY_CRON` | `0 0 * * * *` | 每小时采集任务 |
| `CRAWLER_STARTUP_DELAY_MS` | `15000` | 启动后首次采集延迟 |
| `CRAWLER_STARTUP_REPEAT_DELAY_MS` | `315360000000` | 启动采集重复延迟，默认近似禁用重复 |
| `CAMPUS_AI_EXTRACT_URL` | `http://localhost:8089/api/v1/ai/cognition/extract` | AI 抽取地址；容器编排会改为服务名 |

数据源自身的采集间隔、解析器、选择器配置和启停状态由管理端维护，并写入版本历史。`012_enterprise_closure.sql` 会为已有数据源补齐 `BASELINE` 版本。

## 11. 网关与管理端

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `GATEWAY_RATE_LIMIT_ENABLED` | `true` | Redis 令牌桶限流开关 |

默认按客户端 IP 限流：容量 50，每秒补充 10 个令牌。网关允许最大 15 MiB 请求体。

管理端开发服务器通过 Vite 把 `/api` 代理到 `http://localhost:8080`，无需额外 API Base 变量。生产镜像通过同源反向代理访问网关。

管理端认证使用 HttpOnly Cookie。跨域部署时需要同时处理 HTTPS、Cookie Secure、SameSite 和反向代理域名；推荐让管理端与网关保持同源。

## 12. Flutter 配置与 Android 签名

Flutter API 地址通过编译期变量设置：

```powershell
flutter run --dart-define=CAMPUSMIND_API_BASE=http://localhost:8080
```

常见地址：

| 运行位置 | API 地址 |
| --- | --- |
| Windows 桌面端 | `http://localhost:8080` |
| Android 模拟器 | `http://10.0.2.2:8080` |
| 实体手机 | `http://<开发机局域网IP>:8080` |

Windows App 推荐使用：

```powershell
.\start-user-app.ps1
```

Android Release 必须配置：

```dotenv
CAMPUSMIND_KEYSTORE_PATH=C:/secure/campusmind-release.jks
CAMPUSMIND_KEYSTORE_PASSWORD=<secret>
CAMPUSMIND_KEY_ALIAS=campusmind
CAMPUSMIND_KEY_PASSWORD=<secret>
```

缺少任一签名变量时 Release 构建会主动失败；Debug 构建不受影响。

## 13. 数据库初始化与迁移

MySQL 初始化目录为 `infra/mysql/init`。Docker 仅在空数据卷第一次创建时自动执行其中的脚本；已有数据卷不会自动重放入口脚本。

`dev-start.ps1` 会在每次启动时检查并执行当前需要的幂等结构脚本，包括：

- 主业务表、采集表、AI 字段、订阅、用户行动项和变更日志。
- `user_profile.sensitivity`。
- `012_enterprise_closure.sql`：邮箱、数据源版本、隐私授权、用户设备和通知投递。

只手动更新某个数据库时可执行：

```powershell
$env:MYSQL_PWD = "<app-password>"
mysql --host=127.0.0.1 --port=13306 --user=campusmind --database=campusmind `
  --execute="SOURCE E:/code/CampusMind-AI/infra/mysql/init/012_enterprise_closure.sql"
Remove-Item Env:MYSQL_PWD
```

本机 MySQL 使用端口 `3306`，Docker MySQL 使用端口 `13306`。迁移前应先备份：

```powershell
$env:MYSQL_PWD = "<app-password>"
mysqldump --host=127.0.0.1 --port=3306 --user=campusmind `
  --single-transaction --routines --triggers `
  --result-file=campusmind-backup.sql campusmind
Remove-Item Env:MYSQL_PWD
```

不要为了重放初始化脚本直接删除有业务数据的 Docker volume。

## 14. 启动方式

### 一键开发启动

```powershell
.\dev-start.ps1
```

脚本会：

1. 启动 MySQL、Redis、MongoDB、Nacos；LLM 模式额外启动 PGVector。
2. 检查数据库账号、结构和迁移。
3. 清理 8080–8089 和 5173 上的旧项目进程。
4. 仅在 JAR 缺失或源码更新时重新打包后端。
5. 启动十个后端服务和 Vue 管理端。
6. 检查所有健康端点。
7. 构建并启动 Windows Flutter App。

停止宿主机应用：

```powershell
.\dev-stop.ps1
```

停止 Docker 基础设施：

```powershell
docker compose --env-file .env -f infra/docker-compose.yml down
```

### 全容器部署

当前应用编排固定使用真实 `llm,pg` 模式，因此必须配置模型和 PGVector 凭据：

```powershell
docker compose --env-file .env `
  -f infra/docker-compose.yml `
  -f infra/docker-compose.app.yml `
  up -d --build
```

生产编排只暴露管理端 `80` 和网关 `8080`；内部微服务不直接暴露到宿主机。

## 15. 验证命令

后端全量测试：

```powershell
mvn test
```

管理端：

```powershell
Set-Location campus-admin-web
npm test
npm run build
npm audit --omit=dev --registry=https://registry.npmjs.org
```

Flutter：

```powershell
Set-Location campus-flutter-app
flutter analyze
flutter test
flutter build windows
flutter build apk --debug
```

跨服务 API E2E：

```powershell
.\e2e\core_api_e2e.ps1
```

健康检查：

```powershell
8080..8089 | ForEach-Object {
  $health = Invoke-RestMethod "http://localhost:$($_)/actuator/health"
  "$_ $($health.status)"
}
```

## 16. 生产安全检查清单

- `AUTH_JWT_SECRET` 使用独立随机密钥，不与测试环境共用。
- `AUTH_COOKIE_SECURE=true`，管理端和网关使用 HTTPS 同源部署。
- `AUTH_DEV_EXPOSE_RESET_TOKEN=false`。
- 替换或禁用所有开发固定密码与种子账号。
- SMTP、OIDC、模型、数据库和 keystore 密钥只由密钥管理系统注入。
- Redis、MySQL、MongoDB、PGVector 和 Nacos 不直接暴露到公网。
- `IMPORT_RAIN_COOKIE_ENABLED` 默认保持关闭，开启前取得明确授权。
- 配置真实推送供应商前，确认供应商侧去重、撤回和失败告警策略。
- 修改隐私政策后同步更新 `PRIVACY_POLICY_VERSION`。
- 上线前运行 Maven、管理端、Flutter 和跨服务 E2E 全套检查。

## 17. 常见问题

### 认证服务健康检查返回 503

如果没有 SMTP，确认 `AUTH_MAIL_ENABLED=false`。启用邮件后，SMTP 不可达会使认证服务健康检查失败，这是预期的生产保护。

### 管理端显示“部分接口失败”

管理端不会把接口异常伪装成空数据或演示数据。查看 `logs/campus-*-service.log` 和浏览器网络请求，修复具体失败服务后再刷新。

### AI 结果显示降级

确认 `CAMPUS_AI_MODE=llm`、`OPENAI_*`、`PGVECTOR_*` 和模型下载均可用。规则模式和关键字检索会在接口与页面中明确标记降级状态。

### Flutter 在手机上无法连接 `localhost`

手机的 `localhost` 指手机自身。使用开发机局域网 IP 重新通过 `CAMPUSMIND_API_BASE` 构建，并确认 Windows 防火墙允许访问 8080。

### 数据库字段缺失

重新运行 `dev-start.ps1`，或手动执行对应迁移。不要只更新本机 MySQL 而遗漏 Docker 13306，反之亦然。
