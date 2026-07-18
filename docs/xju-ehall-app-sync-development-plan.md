# 新疆大学一站式服务大厅 App 内教务数据同步方案

> 文档状态：待实施  
> 版本：v1.0  
> 编制日期：2026-07-18  
> 适用仓库：CampusMind-AI  
> 目标读者：负责实现、评审、测试和部署该功能的 AI 编码智能体与开发人员

### 实施进度（2026-07-18）

- [x] 阶段 0：后端功能开关和非秘密配置 API。
- [x] 阶段 1：用户导入私有化、owner 隔离去重、真正 upsert、来源引用去重、管理员默认不可读私有正文、敏感 JSON 失败关闭、专项授权与 scope。
- [ ] 阶段 2：真实学生受控登录勘察与脱敏课表/考试/作业 fixture。当前阻塞：尚无真实数据主机、响应 schema、分页和稳定 ID。
- [x] 阶段 3：后端规范化导入、确定性周次展开、配置/上传/删除 API；解析输入为 App 统一模型，不猜学校私有字段。
- [ ] 阶段 4：Windows WebView 与学校专用本地提取器。必须等待阶段 2，不能用猜测选择器或假接口代替。
- [ ] 阶段 5～6：移动端适配、真实验收和灰度上线。

## 1. 结论

一期采用以下闭环：

1. 用户在 CampusMind App 的现有“导入信息”页面点击“同步教务信息”。
2. App 展示数据范围和隐私说明，由用户主动选择要同步的课表、考试、作业；默认不勾选。
3. App 在受限的内嵌 WebView 中打开新疆大学一站式服务大厅。
4. 用户本人完成统一身份认证、验证码和多因素认证；CampusMind 不读取、记录或代填密码。
5. 登录成功后，App 只在经过确认的教务数据域名中读取结构化数据，在设备本地转换成统一模型。
6. App 展示同步预览，用户确认后才把规范化结果发送到 CampusMind 后端。
7. 后端再次校验授权、来源、大小和字段，将数据作为该用户的私有事件写入现有事件体系。
8. 无论成功、取消或失败，App 都销毁 WebView 并清理学校站点的 Cookie、缓存和本地存储。

该方案不需要浏览器插件，也不要求学校把登录凭据交给 CampusMind。由于目前没有发现新疆大学公开提供给第三方的教务 OAuth/OIDC 或数据 API，该方案必须以“用户主动登录、前台一次性同步”为边界，不能实现后台无人值守定时同步。

如果学校正式提供 OAuth/OIDC、CAS 服务票据校验接口或教务开放 API，应停止扩展页面适配逻辑，迁移到官方接口。仓库已有 `application-sso.yml` 中的 OIDC 能力，可作为后续迁移基础，但它不是本方案一期登录链路的一部分。

## 2. 建设目标

### 2.1 一期必须交付

- Windows App 端可完成一次完整同步：授权、学校登录、数据发现、预览、上传、结果展示、会话清理。
- 同步范围仅限：个人课表、个人考试、个人作业；确有必要时可包含与课程直接相关的调停课通知。
- 数据默认且强制为私有，只能被所属用户在 App、搜索、智能体和提醒中访问。
- 结构化课表、考试、作业采用确定性解析，不调用大模型猜测日期、课程或地点。
- 同一条学校数据可重复同步；已变化的数据被更新，未变化的数据不会重复创建。
- 用户可以撤回后续同步授权，并可一键删除已同步的新疆大学教务数据。
- 学校密码、验证码、MFA 信息、Cookie、Ticket、Token 和完整原始响应不进入 CampusMind 后端、数据库、日志、崩溃报告或埋点。
- 使用功能开关控制上线；没有完成真实环境勘察、安全评审和学校授权前，生产环境默认关闭。

### 2.2 一期非目标

- 不导入成绩、排名、绩点、同学名单、身份证号、手机号、银行卡等高敏感或非必要数据。
- 不实现后台定时同步、服务器代登录、长期保存登录会话或跨设备复用学校会话。
- 不破解验证码、短信验证、滑块、设备指纹、加密参数或私有签名算法。
- 不伪造 User-Agent 绕过限制，不注入学校未授权的网络请求，不绕过学校访问控制。
- 不建设通用“高校连接器平台”、插件系统或独立连接器微服务。
- 不支持 Flutter Web 端同步。浏览器同源隔离、Cookie 和 CORS 使该路径无法满足目标。
- 不把成绩、课表等个人数据混入公开采集事件或公共推荐池。
- 不处理雨课堂导入，本功能与现有雨课堂链路保持隔离。

## 3. 前置事实与关键假设

### 3.1 已确认事实

- 新疆大学存在统一身份认证入口 `authserver.xju.edu.cn`，一站式大厅位于 `ehall.xju.edu.cn`。
- 校外或不受信任设备可能要求多因素认证，必须由学生本人完成。
- 学校公开资料说明一站式大厅已集成大量校内系统，但未发现面向第三方公开的教务 OAuth/OIDC 或数据 API 文档。
- 当前 Flutter 项目已有文本、文件和雨课堂导入页面及 `/api/v1/import/**` 后端路由，但尚未引入 WebView。
- 当前 App 外部打开 URL 的方式无法读取外部浏览器登录后的 Cookie 或页面数据，因此无法实现“登录后自动回到 App 并导入”。
- 当前仓库已有用户同意记录、私有事件、导入任务、删除用户数据等基础能力，应优先复用。

### 3.2 必须验证后才能编码解析器的假设

- 一站式大厅登录后是否能在标准 WebView2/Android WebView/WKWebView 中正常工作。
- 教务应用实际使用的主机名、成功登录 URL、跨域跳转和 iframe 结构。
- 课表、考试、作业是否有稳定的 JSON/XHR 数据源，以及字段、分页、学期、周次和节次语义。
- 返回数据是否为完整快照，是否提供稳定业务 ID，是否包含取消、调课和删除状态。
- 学校相关使用协议是否允许学生把自己的数据导入个人信息管理工具。

编码智能体不得根据网上其他学校的示例猜测新疆大学端点、参数、选择器或字段，也不得把测试账号、Cookie 或抓包原文提交到仓库。

## 4. 目标架构

```text
学生
  │ 选择范围、本人登录、确认预览
  ▼
CampusMind Flutter App
  ├─ 受限 WebView：只访问学校认证/教务白名单域名
  ├─ 本地提取器：读取已确认的数据响应或 DOM
  ├─ 规范化器：转换为 XJU_EHALL v1 请求
  └─ 清理器：销毁 WebView 并清除站点数据
  │ JWT + 规范化数据；不含密码/Cookie/Token/原始响应
  ▼
API Gateway
  ▼
campus-import-service
  ├─ 校验登录用户、授权记录、来源、大小和字段
  ├─ 确定性展开课表周次/节次
  ├─ 生成稳定去重键
  └─ 调用现有事件服务写入私有事件
  ▼
campus-event-service
  ├─ 私有事件 upsert
  ├─ 来源引用与所属用户隔离
  └─ 供首页、搜索、提醒和智能体按 owner 读取
```

设计原则：

- 学校认证会话只存在于用户设备中的临时 WebView。
- App 只上传业务所需的规范化字段，后端永远不接收可复用的学校会话。
- 后端以 JWT 中的用户 ID 为唯一所属人依据，不接受客户端提交 `ownerUserId`。
- 前端授权只是交互门禁，后端还必须查询最新同意记录，不能信任客户端复选框。
- 一期复用现有导入和事件服务，不新增微服务、不引入连接器抽象层。

## 5. 用户流程与状态机

### 5.1 页面流程

1. 在 `campus-flutter-app/lib/import_page.dart` 的现有入口区增加一个主操作卡片“同步教务信息”。不要再增加一个顶级 Tab。
2. 点击后先请求同步配置。如果功能关闭或平台不支持，展示明确说明，不打开学校页面。
3. 展示授权页：
   - 说明数据用途、存储范围、保留方式和删除方式；
   - 分别列出“课表”“考试”“作业”；
   - 所有范围默认不选；
   - 至少选择一项后才能继续；
   - 明确提示“请只在新疆大学官方页面输入密码，CampusMind 不会读取密码”。
4. 用户确认后，先向现有用户服务提交 `ACADEMIC_DATA_IMPORT` 同意记录，再进入 WebView。
5. WebView 顶部固定显示当前主机名和安全状态；用户完成登录和 MFA。
6. App 检测到已确认的登录成功页面后进入数据发现/提取阶段。
7. 如果教务应用位于跨域 iframe，App 导航到经过勘察确认的实际教务数据页面，再执行提取；不得绕过跨域限制。
8. 提取完成后关闭或隐藏 WebView，展示预览：每类数量、学期、时间范围、异常项和将被忽略的字段。
9. 用户确认后上传；取消则立即清理学校站点数据且不上传。
10. 结果页显示成功数、跳过数、失败数和最多 20 条脱敏失败原因，并提供“查看已同步信息”。
11. 在隐私/数据管理页增加“撤回教务同步授权”和“删除已同步教务数据”。撤回不等于删除，二者必须分开说明。

### 5.2 状态机

```text
IDLE
  -> LOADING_CONFIG
  -> CONSENT
  -> AUTHENTICATING
  -> DISCOVERING
  -> EXTRACTING
  -> PREVIEW
  -> UPLOADING
  -> SUCCESS

任意中间状态 -> FAILED -> CLEANUP
任意中间状态 -> CANCELLED -> CLEANUP
SUCCESS -> CLEANUP -> RESULT
```

实现要求：

- 一个状态只允许一个异步动作，避免重复注入和重复上传。
- 页面返回、窗口关闭、网络错误、超时、异常导航和解析异常都必须进入 `CLEANUP`。
- 上传按钮在请求进行中禁用，并使用本地 request nonce 防止双击重复提交。
- App 不承诺后台继续执行；进入后台超过设定时间后中止同步并清理。

## 6. WebView 实施方案

### 6.1 技术选型

使用 `flutter_inappwebview`，原因是它同时提供页面导航控制、Cookie/缓存清理、脚本执行和 Windows WebView2 支持，能覆盖当前项目的 Windows、Android 和 iOS 目标。

实施前先执行兼容性门禁：

- 当前项目 Dart 约束是 `>=3.4.0 <4.0.0`。
- 当前插件版本要求以实施时官方包页面为准；若要求 Dart `>=3.5.0` / Flutter `>=3.24.0`，先确认本机、CI 和发布环境，再统一抬升最低版本。
- 不得为了兼容旧 SDK 使用多年未维护的 WebView 版本。
- Windows 构建环境需要 WebView2 Runtime 和插件要求的 NuGet CLI；在开发文档和 CI 中显式检查。

### 6.2 域名与导航策略

域名分为两类：

- 认证白名单：初始仅包含 `authserver.xju.edu.cn`、`ehall.xju.edu.cn`。
- 数据白名单：必须在真实环境勘察后逐项加入，初始为空。

规则：

- 只允许 HTTPS，精确匹配主机名，不使用 `*.xju.edu.cn` 通配符。
- 证书错误直接失败，不允许“继续访问”。
- 认证阶段可以浏览认证白名单，但不得注入提取脚本。
- 只有当前顶级页面主机位于数据白名单时才允许提取。
- 第三方说明页可调用系统浏览器打开，但不得把它加入提取白名单。
- 非白名单重定向必须阻止，并向用户显示目标主机和原因。
- 最终上传的 `originHosts` 必须是配置返回的主机子集。

### 6.3 脚本桥安全

- 不启用通用对象桥，不暴露文件系统、剪贴板、系统命令或任意 HTTP 能力。
- 每次同步生成至少 128 位随机 session nonce。
- App 注入的提取器把 nonce、schemaVersion 和 payload 一并返回。
- App 接收消息前再次检查当前顶级页面 URL、精确主机、HTTPS、状态机阶段、nonce 和 payload 大小。
- 提取器只读取允许的业务字段，绝不查询密码输入框、Cookie、localStorage 中的认证令牌或 Authorization 头。
- 不在 `authserver.xju.edu.cn` 注入任何脚本。
- 脚本应以 App 资源随版本发布；后端配置接口不得下发可执行 JavaScript。
- Release 构建关闭 DevTools、WebView 调试和详细网络日志。

### 6.4 数据提取优先级

按以下优先级选择，前一项可行时不要实现后一项：

1. 学校正式开放 API / OAuth：改用官方协议，本方案 WebView 仅负责授权。
2. 页面自身已加载的稳定 JSON/XHR 响应：本地监听并按已确认 schema 读取业务响应。
3. 页面提供的导出文件：由用户主动点击导出，App 本地解析。
4. 稳定 DOM：只在没有结构化数据时使用精确选择器提取，并为页面版本设置适配器版本。

遇到以下情况应停止，而不是规避：

- 只有逆向私有签名、解密或设备指纹后才能调用接口；
- 页面明确阻止嵌入式浏览器且学校不允许此用法；
- 数据位于无法合法进入的跨域 iframe；
- 需要保存或转发 Cookie、Ticket、Token 才能完成后续步骤；
- 学校条款或校方反馈不允许第三方导入。

此时保留现有文本/文件手动导入作为降级路径。

### 6.5 会话清理

- 同步使用独立的临时 WebView 数据目录，不能复用 App 其他网页组件的持久会话。
- 成功、取消和失败均清除学校域名的 Cookie、缓存、IndexedDB、Local Storage 和 Service Worker 数据。
- Windows 使用自定义 WebView2 user data folder；先停止并销毁 WebView，再调用清理 API。不得在 WebView 进程占用文件时直接递归删除目录。
- App 启动时可清理上次崩溃遗留且不再被占用的临时目录。
- 清理失败时结果页必须提示用户并记录不含路径中个人数据的错误码；不得静默当作成功。

## 7. 后端 API 契约

所有端点沿用 API Gateway 已有 `/api/v1/import/**` 路由，均要求 CampusMind JWT。

### 7.1 获取同步配置

`GET /api/v1/import/xju/ehall/config`

响应示例：

```json
{
  "enabled": false,
  "loginUrl": "https://ehall.xju.edu.cn/new/index.html",
  "allowedAuthHosts": [
    "authserver.xju.edu.cn",
    "ehall.xju.edu.cn"
  ],
  "allowedDataHosts": [],
  "supportedScopes": ["TIMETABLE", "EXAM", "HOMEWORK"],
  "schemaVersion": 1,
  "policyVersion": "2026-07-18-v1",
  "maxPayloadBytes": 2097152,
  "maxSourceItems": 500
}
```

要求：

- 只返回非秘密配置，不返回 Cookie、Token、脚本或内部服务地址。
- `enabled=false` 时 App 隐藏或禁用入口，并展示维护说明。
- 配置中的 URL 必须在服务启动时校验为 HTTPS 且主机在认证白名单中。
- 数据白名单未配置时即使误开功能也不得提取。

### 7.2 上传规范化数据

`POST /api/v1/import/xju/ehall`

请求示例：

```json
{
  "schemaVersion": 1,
  "provider": "XJU_EHALL",
  "consentVersion": "2026-07-18-v1",
  "collectedAt": "2026-07-18T10:30:00+08:00",
  "originHosts": ["confirmed-teaching-host.xju.edu.cn"],
  "scopes": ["TIMETABLE", "EXAM", "HOMEWORK"],
  "semester": {
    "code": "2026-2027-1",
    "startDate": "2026-09-07",
    "endDate": "2027-01-10",
    "timezone": "Asia/Shanghai"
  },
  "items": [
    {
      "providerItemId": "school-stable-id-or-null",
      "type": "COURSE",
      "title": "软件工程",
      "courseCode": "SE1001",
      "courseName": "软件工程",
      "teacherName": "教师姓名",
      "startTime": null,
      "endTime": null,
      "deadline": null,
      "location": "博达校区 A101",
      "description": null,
      "schedule": {
        "weekNumbers": [1, 2, 3, 4],
        "weekday": 1,
        "startSection": 1,
        "endSection": 2,
        "startClock": "10:00",
        "endClock": "11:40"
      }
    }
  ]
}
```

响应沿用当前导入任务语义，避免为一期重构全部导入接口：

```json
{
  "taskId": 12345,
  "status": "SUCCESS",
  "message": "同步完成",
  "summary": {
    "total": 120,
    "success": 118,
    "skipped": 1,
    "failed": 1,
    "byType": {
      "COURSE": 100,
      "EXAM": 8,
      "HOMEWORK": 10
    },
    "failureReasons": [
      {
        "index": 18,
        "code": "INVALID_TIME_RANGE",
        "message": "结束时间不得早于开始时间"
      }
    ]
  }
}
```

约束：

- 请求体上限 2 MiB；源条目上限 500；课表展开后的事件总数上限 800。超限整单拒绝，不截断后静默成功。
- `provider` 只能为 `XJU_EHALL`；`schemaVersion` 必须精确支持。
- `originHosts` 必须非空且全部属于服务端数据白名单。
- `scopes` 必须是用户已同意范围的子集。
- `collectedAt` 与服务器时间偏差超过 15 分钟时拒绝，降低重放风险。
- 同一用户一分钟最多发起 2 次该同步；复用现有每用户限流能力。
- 失败原因最多返回 20 条，不回显完整原始条目。
- 响应不承诺区分 created/updated，以避免一期改造所有内部事件接口；只提供 success/skipped/failed。若产品确实需要精确操作类型，再单独扩展事件服务返回值。

### 7.3 删除已同步数据

`DELETE /api/v1/import/xju/ehall/data`

行为：

- 从 JWT 获取用户 ID。
- 只删除或按项目现有策略软删除该用户 `source_type = XJU_EHALL` 的数据。
- 同步清理 `event_source_ref`、提醒、行动项以及已生成的向量文档。
- 幂等执行；重复调用返回删除数量 0，不报错。
- 写入脱敏审计记录：用户 ID、操作时间、来源类型、删除数量、结果；不记录事件正文。

建议响应：

```json
{
  "deletedEvents": 120,
  "deletedReminders": 4,
  "deletedActions": 2,
  "deletedVectors": 120
}
```

## 8. 统一数据模型和映射规则

### 8.1 支持类型

| 学校数据 | `sourceType` | `eventType` | 主时间字段 | 默认可见性 |
|---|---|---|---|---|
| 课程上课实例 | `XJU_EHALL` | `COURSE` | `startTime/endTime` | `PRIVATE` |
| 作业 | `XJU_EHALL` | `HOMEWORK` | `deadline`，映射为事件开始时间 | `PRIVATE` |
| 考试 | `XJU_EHALL` | `EXAM` | `startTime/endTime` | `PRIVATE` |
| 调停课通知 | `XJU_EHALL` | `COURSE_CHANGE` | 通知内明确时间 | `PRIVATE` |

不把普通校级通知混入个人教务同步；公共通知继续走现有官网采集链路。

### 8.2 时间规则

- 时区固定为 `Asia/Shanghai`。
- 若学校返回明确 ISO 时间，直接校验并规范化。
- 若只返回学期起止、周次、星期和节次，由后端按已确认的校历和节次时刻表确定性展开。
- 节次时刻不能靠大模型推测；必须来自真实接口字段或经学校确认的配置。
- 双周/单周必须转换成明确 `weekNumbers`。
- 无法确定日期或节次时整条失败，不能默认到当天、零点或下一周。
- `endTime < startTime`、超出学期日期、星期不匹配均为校验错误。

### 8.3 稳定标识与去重

优先去重键：

```text
SHA-256(ownerUserId | XJU_EHALL | providerItemId | occurrenceDate)
```

没有稳定 `providerItemId` 时才使用降级键：

```text
SHA-256(
  ownerUserId | XJU_EHALL | type | normalizedCourseCode |
  normalizedTitle | startTimeOrDeadline | normalizedLocation
)
```

要求：

- 私有事件的内容哈希和去重查询必须包含 `ownerUserId`；不同用户内容相同也绝不能融合为同一事件。
- 对已存在项执行真正 upsert：地点、教师、标题、时间、描述发生变化时更新原事件。
- `event_source_ref` 对同一事件和来源引用保持唯一，重复同步不能无限插入引用。
- 一期只 upsert 本次看见的数据，不根据“本次没出现”自动删除旧事件。只有真实接口被证明返回完整快照且提供取消/删除语义后，才能增加对账删除，避免因分页或接口失败误删用户数据。

### 8.4 原始数据保留

生产环境不保存新疆大学完整页面、HTML、XHR 原文或原始 JSON。

`import_task.result_summary` 仅保留：

- scopes；
- consentVersion；
- collectedAt；
- collectionMode=`APP_WEBVIEW`；
- extractorVersion；
- 学期编号；
- 各类型成功/跳过/失败数量；
- 规范化内容哈希；
- 脱敏错误码。

现有 `RawDocument` 不应用于 XJU_EHALL。若未来确需故障样本，只允许用户在本地显式导出已脱敏诊断包，由人工单次授权上传到受控环境，不能自动上报。

## 9. 隐私、授权与访问控制门禁

### 9.1 用户授权

复用现有 `user_consent_record`，扩展同意类型：

```text
ACADEMIC_DATA_IMPORT
```

授权记录至少包含：同意/撤回、政策版本、时间、客户端版本和选择的数据范围。若现有表无法保存 scope，可将范围作为结构化扩展字段；不要为一期新建一套重复的同意表。

服务端执行顺序：

1. 校验 CampusMind JWT。
2. 通过 user-service 内部接口读取该用户最新 `ACADEMIC_DATA_IMPORT` 记录。
3. 确认状态为已同意，版本与请求一致，scope 覆盖请求范围。
4. 再处理导入。

撤回授权只阻止后续同步；历史数据保留到用户主动删除或触发既有数据生命周期规则。

### 9.2 现有代码必须先修复的 P0 隐私问题

编码智能体必须先完成并验证以下门禁，再接入学校数据：

- `USER_TEXT` 和 `USER_FILE` 当前写入路径需要统一改为私有；所有用户主动提交或认证来源不得默认为公共事件。
- `EventCommandService` 中私有内容哈希融合必须按 owner 隔离，防止两个用户相同内容被合并。
- `EventCommandService` 的现有“upsert”命中重复项后必须更新可变字段，而不是只复用 ID。
- `XJU_EHALL` 事件在事件服务端被强制设为 `PRIVATE`，不能只依赖导入服务传参。
- 管理后台默认查询不得展示个人私有正文。若确需合规排障，应另设显式高权限、审计和脱敏流程，不得使用普通 ADMIN 列表旁路。
- `RawDocumentService` 对结构化敏感 JSON 的清洗必须 fail closed；JSON 解析失败不能原样保存。XJU_EHALL 本身不得进入 RawDocument。
- feed、搜索、智能体、向量检索、提醒和导出都必须验证 `ownerUserId`，匿名用户和其他用户不能得到存在性、标题、计数或摘要。

### 9.3 最小化和日志

- 不收集密码、验证码、MFA 答案、身份证号、学号以外的账号凭据、Cookie、Ticket、Token。
- 如果业务不需要学号，连学号也不上传；事件所属关系由 CampusMind JWT 决定。
- URL 入库前删除 query、fragment 和可能含会话信息的 path 参数，仅保留经允许的站点基础地址。
- 请求日志不得打印 body；异常日志不得打印 WebView 消息、页面 HTML、响应 JSON 或认证 URL 查询参数。
- 崩溃报告和产品埋点只记录状态码、阶段、提取器版本、计数和耗时。
- 不复用网络搜索工具的 API Key，也不在 App 中放置任何服务端密钥。

## 10. 配置设计

在 `campus-import-service/src/main/resources/application.yml` 增加：

```yaml
campus:
  import:
    xju-ehall:
      enabled: ${XJU_EHALL_ENABLED:false}
      login-url: ${XJU_EHALL_LOGIN_URL:https://ehall.xju.edu.cn/new/index.html}
      auth-hosts:
        - authserver.xju.edu.cn
        - ehall.xju.edu.cn
      data-hosts: []
      max-payload-bytes: 2097152
      max-source-items: 500
      max-expanded-events: 800
      policy-version: 2026-07-18-v1
```

实现一个具体的 `XjuEhallProperties` 配置类即可，不创建接口、工厂或通用 Provider 层。

部署要求：

- 开发、测试、预发、生产分别配置功能开关和数据域名。
- 生产默认 `false`；数据域名为空时启动可成功，但功能必须判定不可用。
- 只有完成真实环境验证、安全评审和必要的校方合规确认后才能打开。
- 配置变更进入版本控制或受控配置中心，保留审批和回滚记录。

## 11. 代码改动清单

以下路径是执行基线；编码前应再次使用 `rg` 追踪调用者，避免覆盖期间发生的用户改动。

### 11.1 Flutter App

修改：

- `campus-flutter-app/pubspec.yaml`
  - 增加 `flutter_inappwebview`；
  - 完成 Flutter/Dart 兼容性门禁后再调整 SDK 下限。
- `campus-flutter-app/lib/import_page.dart`
  - 在现有导入入口增加“同步教务信息”；
  - 不新增顶级 Tab，不影响文本、文件和雨课堂既有行为。
- `campus-flutter-app/lib/information_api_stub.dart`
  - 增加配置、同步、删除所需模型和方法签名。
- `campus-flutter-app/lib/information_api_io.dart`
  - 调用三个新 API；
  - 使用现有鉴权和错误映射；
  - 不记录请求体。
- 隐私/数据管理对应页面
  - 展示授权状态、撤回授权、删除数据；优先复用现有个人设置页面，不新建重复导航层。

新增：

- `campus-flutter-app/lib/xju_ehall_sync_page.dart`
  - 状态机、授权交互、WebView、域名校验、提取、预览、上传和清理。
- `campus-flutter-app/lib/xju_ehall_models.dart`
  - 只放本功能配置、规范化 payload 和预览模型。若实现后不足约 150 行，可合并到同步页，避免碎片化。

平台文件：

- Android：确认 INTERNET 权限；不添加明文 HTTP 例外。
- iOS：不设置任意 ATS 放行；学校 URL 必须 HTTPS。
- Windows：配置插件生成项、WebView2 Runtime 和 NuGet 前置条件；使用临时 user data folder。

### 11.2 campus-import-service

修改：

- `controller/ImportController.java`
  - 增加 config、import、delete 三个端点。
- `config/ImportProperties.java` 或新增具体 `XjuEhallProperties.java`
  - 因配置字段较多，优先新增独立具体配置类，不建立抽象层。
- `application/ImportService.java`
  - 修复已有用户文本/文件私有性问题；XJU 逻辑不要继续塞进已较大的通用服务。
- `feign/EventFeignClient.java` / `application/EventServiceClient.java`
  - 复用现有事件创建链路，确保传递 `XJU_EHALL`、owner 和 PRIVATE。
- `application/RawDocumentService.java`
  - 修复敏感结构化 JSON 解析失败时原样保存的问题。

新增：

- `controller/XjuEhallConfigResponse.java`
- `controller/XjuEhallImportRequest.java`
- `controller/XjuEhallImportResponse.java`
- `application/XjuEhallImportService.java`
  - 一个具体服务，负责校验、规范化、展开、去重键和事件写入；不创建通用连接器接口。
- `config/XjuEhallProperties.java`
- user-service 内部授权查询 Feign client（若当前没有可复用客户端）。

DTO 应使用 Jakarta Validation 加手工跨字段校验。所有字符串设置明确长度，集合设置上限，枚举拒绝未知值。

### 11.3 campus-event-service

修改：

- `application/EventCommandService.java`
  - 私有去重按 owner 隔离；
  - 命中已有事件时更新可变字段；
  - 来源引用去重；
  - `XJU_EHALL` 和所有认证/用户导入来源强制 PRIVATE。
- `application/EventQueryService.java`
  - 管理端普通列表不能默认旁路个人数据权限。
- `controller/EventController.java`
  - 若删除接口需要内部批量删除，增加只允许服务间调用且按 owner/source 限定的端点；不要接受任意 SQL 条件。
- Mapper
  - 增加按 `owner_user_id + source_type` 查询/删除和按 owner 去重的方法。

数据库约束建议：

- 为私有去重键增加包含 owner 的唯一约束或等效安全约束。
- 为 `event_source_ref` 增加能阻止重复来源引用的唯一约束。
- 迁移前先扫描重复数据，生成备份和回滚 SQL；不能直接加唯一索引导致生产迁移失败。

### 11.4 campus-user-service

修改：

- `controller/ConsentRequest.java` 及同意类型校验
  - 增加 `ACADEMIC_DATA_IMPORT`。
- `application/UserService.java`
  - 支持记录/撤回该授权。
- `controller/UserController.java`
  - 复用已有 `/me/privacy` 能力；增加受服务认证保护的内部最新授权查询端点，或扩展已有内部接口。

禁止导入服务通过客户端传来的字段判断授权。

### 11.5 其他消费者

逐一验证而非默认正确：

- 首页/feed：私有事件只返回 owner。
- 搜索：查询必须携带并验证 owner。
- 智能体/向量检索：XJU 私有向量必须带 owner；删除时清理 vector doc ID。
- 提醒/行动项：只为 owner 创建；删除源事件时同步清理或失效。
- 管理后台：不显示个人教务正文和数量明细。
- 用户数据导出：若现有个人数据导出已覆盖私有事件，应包含；否则明确加入。

## 12. 真实环境勘察阶段

此阶段由开发人员和一名自愿参与的真实学生共同完成，AI 智能体只提供检查清单和本地调试工具，绝不能索要或操作学生凭据。

### 12.1 操作规则

- 使用专用测试构建、隔离开发设备和测试 CampusMind 账号。
- 学生本人在学校官方页面输入账号、密码并完成 MFA。
- 调试日志禁用请求头、请求体和 Cookie 输出。
- 只记录：主机名、页面标识、URL 路径模式、业务响应 schema、字段含义、分页方式、稳定 ID、学期/周次规则。
- 不记录：密码、Cookie、Ticket、Token、完整姓名、学号、真实课程正文或个人标识。
- 用人工替换后的虚构值生成脱敏 fixture，真实抓包不入库。

### 12.2 必须产出的勘察结果

- 登录入口、成功落地页和退出行为。
- 认证主机和数据主机精确清单。
- 是否存在跨域 iframe、弹窗或新窗口。
- 课表、考试、作业各自的数据获取方式和分页规则。
- 一份脱敏的课表 fixture、一份考试 fixture、一份作业 fixture。
- 字段映射表：原字段、含义、是否必填、示例、统一字段。
- 稳定业务 ID 是否存在。
- 数据是完整快照还是增量列表。
- 取消考试、调课、撤回作业的状态表示。
- 页面/API 变更时可识别的版本信号。
- 学校条款/审批结论和允许的上线范围。

### 12.3 勘察退出条件

只有同时满足以下条件才能进入解析器编码：

- WebView 可正常完成本人登录和 MFA；
- 至少一种目标数据存在可稳定、合规提取的来源；
- 数据主机和 schema 已确认；
- fixture 完成彻底脱敏；
- 没有发现必须转移学校会话到后端的要求；
- 合规负责人确认可进行小范围测试。

若只确认其中一种数据，例如课表，一期先只开放课表 scope，不要用猜测补齐考试和作业。

## 13. 分阶段执行计划

### 阶段 0：冻结范围与建立安全开关

任务：

- 建立本文档对应的实现分支。
- 增加 `XJU_EHALL_ENABLED=false` 配置和 config API 骨架。
- 确认生产、预发均默认关闭。
- 在 issue/任务中记录非目标、学校许可状态和测试负责人。

验证：

- 未配置或数据主机为空时，App 不能进入 WebView。
- 配置接口不包含秘密和可执行脚本。

完成条件：功能在任何误部署场景下都不会被意外打开。

### 阶段 1：修复 P0 隐私与事件正确性

任务：

- 用户文本/文件默认私有。
- 私有事件按 owner 去重，禁止跨用户内容融合。
- 修复现有 upsert 不更新字段。
- 来源引用去重。
- 管理端默认不旁路私有正文。
- RawDocument 敏感 JSON 失败关闭。
- 扩展 `ACADEMIC_DATA_IMPORT` 并提供内部授权查询。

验证：

- 用户 A、B 导入完全相同内容，生成彼此独立且不可互见的私有事件。
- A 再次导入改变地点/时间的同一事件，原事件被更新而非新增。
- 普通管理员列表、匿名请求、B 的搜索和智能体均不能获取 A 的内容或存在性。
- 非法 JSON 不被 RawDocument 原样保存。

完成条件：P0 测试全部通过后才允许继续。

### 阶段 2：真实环境勘察与脱敏 fixture

任务：执行第 12 章；只为已确认的数据类型产出 fixture 和字段映射。

验证：

- fixture 中搜索不到真实姓名、学号、Cookie、Token、Ticket、手机号。
- 解析所需字段和分页规则有书面定义。

完成条件：勘察退出条件全部满足。

### 阶段 3：后端确定性导入垂直切片

任务：

- 实现配置、上传、删除 API。
- 实现 `XjuEhallImportService` 和 fixture 解析测试。
- 实现时间展开、校验、去重和 XJU 私有事件写入。
- 保存最小任务摘要，不保存原始 payload。

验证：

- 使用脱敏 fixture 导入课表、考试、作业。
- 重复同步不增加重复事件；改变时间/地点后更新。
- 超限、非法 host、过期 collectedAt、无授权、错误 scope、异常周次均明确失败。
- 删除接口只删除当前用户 XJU 数据，并清理关联数据。

完成条件：后端不依赖真实学校在线环境即可通过全部 fixture 测试。

### 阶段 4：Windows App WebView 垂直切片

任务：

- 完成插件兼容性门禁和 Windows 构建前置条件。
- 实现同步页状态机、域名栏、导航限制、nonce 桥、预览、上传和清理。
- 用本地 HTTPS 模拟页覆盖登录跳转、数据页、异常跳转和脚本消息。
- 再由学生本人完成一次真实手工验收。

验证：

- 非白名单 URL 被拦截。
- 认证页面不执行提取脚本。
- 错误 nonce、错误 origin、超大消息被拒绝。
- 取消、失败、成功和窗口关闭均清理站点数据。
- 重启 App 后学校会话不可复用。
- App 日志、后端日志、崩溃报告不含凭据或业务原文。

完成条件：Windows 完成一次端到端同步且隐私检查通过。

### 阶段 5：Android/iOS 适配

任务：

- 复用同一状态机和 payload，不复制业务逻辑。
- 验证系统 WebView/WKWebView、键盘、MFA 跳转、返回键和生命周期。
- iOS 不增加任意 ATS 例外，Android 不允许明文网络。

验证：

- 至少一台受支持的 Android 真机完成全流程。
- 如计划发布 iOS，至少一台真机完成全流程。
- App 切后台、系统回收、旋转和返回均不泄露会话。

完成条件：各目标平台单独通过安全与功能验收后才打开该平台入口。

### 阶段 6：小范围试点与上线

任务：

- 先只对白名单测试用户开放。
- 监控阶段耗时、成功率、解析版本、失败错误码和清理结果，不采集正文。
- 建立学校页面变化后的自动关闭条件。
- 完成隐私说明、用户支持文档、删除流程和回滚演练。

建议熔断：

- 同一提取器版本 30 分钟内连续 5 次 schema mismatch；
- 清理失败率超过 1%；
- 出现任何凭据/会话泄露迹象；
- 校方要求停止；
- 数据错配或跨用户可见事件 1 次。

触发后立即把远端 `enabled` 设为 `false`，不等待发版。

## 14. 测试与验收矩阵

### 14.1 后端单元测试

- 每类脱敏 fixture 正常解析。
- 具体时间课程和周次课程分别映射正确。
- 单双周、跨月、学期边界、节假日规则按已确认配置处理。
- 无稳定 ID 时降级去重键一致。
- 标题空、超长字段、非法枚举、错误时区、时间倒置、周次越界失败。
- 未知字段忽略但不进入摘要；未知必需语义应失败，不猜测。

### 14.2 服务/API 测试

- 未登录：401。
- 无同意或已撤回：403。
- scope 不覆盖：403。
- 功能关闭：明确业务错误或 404，App 可识别。
- 非白名单 origin：400/403。
- body 超限、条目超限、展开超限：413 或明确业务错误。
- 重复请求、重复条目、变更条目：无重复且更新正确。
- A/B 用户隔离：事件、搜索、智能体、提醒、管理后台全部覆盖。
- 删除：幂等、限定 owner/source、关联项和向量清理。
- 部分失败：成功项可追踪，错误原因脱敏且最多 20 条。

### 14.3 Flutter Widget/状态测试

- scope 默认未选择；未选时继续按钮禁用。
- 配置加载、功能关闭、网络失败、空数据、解析失败、上传失败有独立状态。
- 地址栏始终展示当前主机，不被页面遮挡。
- 非白名单导航有明确警告。
- 预览正确显示数量、学期、时间范围和异常。
- 上传中不可重复提交。
- 返回键、取消、窗口关闭均触发 cleanup。
- 按钮、复选框、错误提示具备 accessible label，文本缩放后无溢出。

### 14.4 WebView 集成测试

使用本地 HTTPS fixture 站点模拟：

- eHall -> authserver -> eHall -> data host 重定向；
- 登录页与数据页不同 origin；
- 错误证书、HTTP、外部主机、弹窗和下载；
- 正确/错误 nonce；
- 正确/超大/畸形 payload；
- 网络中断、超时、页面刷新和重复消息；
- 成功/失败/崩溃后的站点数据清理。

真实学校环境只做人工验收，不能把生产账号交给自动化测试或 CI。

### 14.5 性能基线

- config API p95 小于 300 ms（不含公网学校页面）。
- 500 个源条目、最多 800 个展开事件在测试环境 30 秒内完成导入。
- App 上传超时建议 60 秒，并给出可重试提示。
- WebView 单次提取消息不超过 2 MiB。
- 若实际数据超过限制或性能不达标，先基于测量结果增加事件批量写入；不要在一期预先建立通用消息队列作业系统。

### 14.6 各阶段建议验证命令

以下命令由编码智能体在对应代码完成后执行。先运行聚焦测试，确认通过后再扩大范围；命令失败必须修复并重跑，不能只记录失败后继续交付。

P0 隐私、用户授权和事件 upsert：

```powershell
mvn -pl campus-user-service,campus-event-service,campus-import-service -am test
```

若新增了聚焦测试类，可在开发迭代中先运行：

```powershell
mvn -pl campus-event-service -Dtest=EventFusionTest test
mvn -pl campus-import-service -Dtest=ImportControllerTest,RawDocumentSanitizeTest test
mvn -pl campus-user-service -Dtest=UserControllerTest test
```

Flutter 依赖、静态分析和测试：

```powershell
Set-Location campus-flutter-app
flutter pub get
flutter analyze
flutter test
flutter build windows
```

管理端私有数据不可见性若涉及前端逻辑：

```powershell
Set-Location campus-admin-web
npm ci
npm test
npm run build
```

提交前仓库检查：

```powershell
git status --short
git diff --check
git diff --stat
git diff
rg -n "Cookie|Authorization|Bearer|Ticket|Token|password|学号|手机号" docs campus-* -g "!target/**" -g "!build/**"
```

最后一条是人工复核线索，不是看到单词就机械判定泄露；重点检查新增 fixture、日志、配置、截图和测试输出中是否出现真实值。不得把真实学生凭据作为自动化测试参数或 CI Secret。

## 15. 监控、审计与告警

允许采集：

- 平台、App 版本、提取器版本；
- 状态机阶段和稳定错误码；
- 总条数和各类型计数；
- 各阶段耗时；
- 清理成功/失败；
- feature flag 状态。

禁止采集：

- 页面 URL 查询参数；
- 密码、验证码、Cookie、Ticket、Token；
- 课程名、教师名、地点、作业正文、考试内容；
- 完整请求/响应体、页面 HTML 和截图。

关键告警：

- schema mismatch 激增；
- 非白名单导航激增；
- cleanup 失败；
- 无授权导入尝试；
- 删除链路部分失败；
- 任何跨用户访问测试失败或生产疑似事件。

安全事件优先级高于可用性；发生疑似泄露时先关闭功能，再调查。

## 16. 发布、回滚与兼容性

发布顺序：

1. 先发布 P0 隐私修复。
2. 发布后端 API，功能开关保持关闭。
3. 发布带入口但受 config 控制的 App。
4. 预发真实用户验收。
5. Windows 小范围白名单开放。
6. 观察稳定后再扩大范围；Android/iOS 分平台独立开放。

回滚：

- 第一动作：`XJU_EHALL_ENABLED=false`，App 立即隐藏/禁用入口。
- 禁用同步不自动删除已存在的用户私有事件；用户删除接口继续可用。
- App 解析器按 `schemaVersion` 和 `extractorVersion` 版本化；后端至少兼容当前发布版本，不能无提示改变字段语义。
- 数据库迁移提供回滚 SQL；回滚唯一约束前保留数据备份。
- 若插件升级导致平台构建问题，回滚 App 版本，不降级到已知不安全的 WebView 插件。

## 17. 编码智能体执行规则

编码智能体在每个阶段必须遵守：

1. 先读本文档、仓库 README、`docs/authenticated-data-source-development-plan.md`、相关配置和当前 `git status`。
2. 使用 `rg` 追踪调用链，确认共享 DTO、事件查询和删除消费者后再改代码。
3. 每次只实施一个阶段；P0 门禁未通过不得继续 WebView 接入。
4. 真实端点/schema 缺失时停止并报告具体缺口，不写假实现、示例接口或猜测选择器。
5. 不索要、保存或打印学生凭据；真实登录只允许学生本人在官方页面完成。
6. 复用现有导入、用户同意、JWT、事件、提醒和删除链路。
7. 不引入通用连接器平台、后台浏览器、代理池、验证码服务、自动登录脚本或新微服务。
8. 所有跨层名字、枚举、默认值、验证和错误码同步修改。
9. 先运行聚焦测试，再运行相关服务测试和 Flutter 静态检查/构建；真实环境仅做人工验收。
10. 检查 diff 中是否包含密钥、Cookie、Token、个人数据、抓包、截图、缓存和生成文件。
11. 不处理与本功能无关的雨课堂、UI 重构或代码清理。
12. 未经用户明确要求不提交、不推送；交付时说明实际运行过的命令和未验证项。

建议每阶段交付报告格式：

```text
阶段：
完成项：
修改文件：
数据/配置迁移：
安全检查：
执行的测试与结果：
未执行的验证：
已知阻塞：
下一阶段进入条件：
```

## 18. Definition of Done

只有以下条件全部满足，功能才算完成：

- [ ] 已确认学校使用条款/授权边界，必要审批有记录。
- [ ] 数据主机、schema、分页和稳定 ID 经真实环境确认，无猜测端点。
- [ ] Windows App 中学生本人可完成登录和 MFA。
- [ ] App 不读取或上传密码、Cookie、Ticket、Token。
- [ ] scope 默认不选，授权可撤回，历史数据可单独删除。
- [ ] 课表、考试、作业只同步用户选择的范围。
- [ ] 结构化数据全部确定性解析，异常时间不由 AI 猜测。
- [ ] 所有 XJU 数据强制 PRIVATE，A/B 用户隔离测试通过。
- [ ] 重复同步不重复创建，变更内容可以更新。
- [ ] 未出现于本次列表的数据不会被误删。
- [ ] 生产不保存完整学校原始响应。
- [ ] 成功、失败、取消、关闭和崩溃恢复路径均清理 WebView 会话。
- [ ] 非白名单、HTTP、证书错误、错误 origin/nonce 和超限 payload 均被拒绝。
- [ ] feed、搜索、智能体、向量、提醒、后台和导出权限逐一验证。
- [ ] 删除接口清理事件、引用、提醒/行动项和向量，且幂等。
- [ ] 后端、Flutter、WebView 模拟站点和真实人工验收完成。
- [ ] 日志、埋点、崩溃报告和 Git diff 无凭据或个人原文。
- [ ] 功能开关、告警、熔断和回滚演练完成。
- [ ] Android/iOS 仅在各自通过真机验收后开放。

## 19. 风险登记

| 风险 | 影响 | 控制措施 | 上线阻断 |
|---|---|---|---|
| 学校不允许嵌入 WebView 或第三方导入 | 功能不可上线 | 先确认条款/校方意见；保留文件导入 | 是 |
| MFA/验证码不兼容 WebView | 无法登录 | Windows 真机勘察；不绕过；降级手动导入 | 是 |
| 页面或接口 schema 变化 | 同步失败或错配 | 版本化提取器、严格校验、熔断开关 | 错配时是 |
| 跨域 iframe 无法读取 | 不能提取 | 导航到已授权实际数据页；否则停止 | 目标数据不可得时是 |
| 会话未清理 | 学校账号泄露 | 临时 UDF、全路径 cleanup、清理告警 | 是 |
| 私有事件跨用户融合 | 严重隐私事件 | owner 进入去重键和唯一约束，A/B 测试 | 是 |
| 列表并非完整快照 | 误删旧数据 | 一期禁止缺失删除 | 否 |
| 500 次逐项事件写入性能不足 | 同步超时 | 先测量；必要时增加窄范围批量写入 | 达不到基线时是 |
| 管理员旁路私有数据 | 内部越权 | 默认不展示正文，显式审计支持流程 | 是 |
| 原始响应进入日志/数据库 | 数据泄露 | 不持久化原文，请求体不日志化，扫描验证 | 是 |

## 20. 官方参考资料

- 新疆大学网络与信息技术中心：一站式服务大厅说明  
  <https://net.xju.edu.cn/index/fwzn/yzsyyxt.htm>
- 新疆大学网络与信息技术中心：统一身份认证多因素认证说明  
  <https://net.xju.edu.cn/info/1129/1828.htm>
- 新疆大学统一身份认证入口  
  <https://authserver.xju.edu.cn/authserver/login?service=https%3A%2F%2Fehall.xju.edu.cn%3A443%2Fnew%2Findex.html>
- flutter_inappwebview 包说明  
  <https://pub.dev/packages/flutter_inappwebview>
- flutter_inappwebview InAppWebView 文档  
  <https://inappwebview.dev/docs/webview/in-app-webview/>
- Microsoft WebView2 安全指南  
  <https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/security>
- Microsoft WebView2 用户数据目录  
  <https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/user-data-folder>
- Microsoft WebView2 清除浏览数据  
  <https://learn.microsoft.com/en-in/microsoft-edge/webview2/concepts/clear-browsing-data>
- 《中华人民共和国个人信息保护法》  
  <https://www.npc.gov.cn/npc/c2/c30834/202108/t20210820_313088.html>

---

本方案有意把一期范围收敛为一个可审计的垂直闭环：不建立通用连接器、不保存学校会话、不做后台代登录。真正的不确定性集中在“学校实际页面和数据 schema”上，因此真实环境勘察是实现依赖，不是可以由编码智能体跳过的准备工作。
