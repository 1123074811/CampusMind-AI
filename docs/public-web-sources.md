# 新疆大学公开网页采集来源清单

更新时间：2026-07-07

## 合规边界

- 仅采集新疆大学与新疆大学软件学院官网可直接访问的公开 HTML 页面。
- 不采集登录后页面、个人隐私页、验证码保护页，不使用 Cookie，不绕过访问控制。
- `https://www.xju.edu.cn/robots.txt` 与 `https://ss.xju.edu.cn/robots.txt` 当前返回 404，未发现显式 robots 策略；系统仍按低频率、清晰 User-Agent 采集。
- 同一来源采集间隔配置为 10 秒，大于项目要求的 2 秒下限。
- User-Agent：`CampusEventBot/1.0 (+contact: admin@example.edu)`。

## 第一批官方来源

| 来源名称 | 列表 URL | 官网主体 | 用途 | 分页规律 | 详情链接规律 |
|---|---|---|---|---|---|
| 新疆大学通知公告 | `https://www.xju.edu.cn/xwzx/tzgg.htm` | 新疆大学 | 学校通知、公示、公告 | `xwzx/tzgg/{page}.htm` | `https://www.xju.edu.cn/info/{catalog}/{id}.htm` |
| 新疆大学校园经纬 | `https://www.xju.edu.cn/xwzx/xyjw.htm` | 新疆大学 | 校园新闻、活动动态 | `xwzx/xyjw/{page}.htm` | `https://www.xju.edu.cn/info/{catalog}/{id}.htm` |
| 新疆大学新大头条 | `https://www.xju.edu.cn/xwzx/xdtt.htm` | 新疆大学 | 学校重要新闻 | `xwzx/xdtt/{page}.htm` | `https://www.xju.edu.cn/info/{catalog}/{id}.htm` |
| 软件学院学生工作通知公告 | `https://ss.xju.edu.cn/xsgz/tzgg.htm` | 新疆大学软件学院 | 学生工作、培养手册、学院通知 | `xsgz/tzgg/{page}.htm` | `https://ss.xju.edu.cn/info/{catalog}/{id}.htm` |
| 软件学院招生就业 | `https://ss.xju.edu.cn/zsjy.htm` | 新疆大学软件学院 | 招生、复试、录取、就业实习 | `zsjy/{page}.htm` | `https://ss.xju.edu.cn/info/{catalog}/{id}.htm` |
| 软件学院创新创业通知公告 | `https://ss.xju.edu.cn/cxcy/tzgg.htm` | 新疆大学软件学院 | 创新创业通知、竞赛、成果 | `cxcy/tzgg/{page}.htm` | `https://ss.xju.edu.cn/info/{catalog}/{id}.htm` |
| 软件学院教务教学 | `https://ss.xju.edu.cn/jwjx.htm` | 新疆大学软件学院 | 教务教学、课程培养相关信息 | `jwjx/{page}.htm` | `https://ss.xju.edu.cn/info/{catalog}/{id}.htm` |

## 页面结构

### 新疆大学主站列表页

- 列表项：`.list1 li`
- 链接：`a[href*="info/"]`
- 标题：`a[title]` 或 `h4`
- 摘要：`.txt p`
- 日期：`.time`
- 示例详情：`https://www.xju.edu.cn/info/1030/28464.htm`

### 新疆大学主站详情页

- 标题：`.arc-tit h1`
- 元信息：`.arc-info`
- 正文：`#vsb_content .v_news_content`
- 发布时间文本示例：`信息日期：2026-07-07 15:57:55`

### 软件学院列表页

- 列表链接：`a.c130885[href*="info/"]`
- 标题：`a.c130885[title]`
- 日期：`.timestyle130885`
- 示例详情：`https://ss.xju.edu.cn/info/1018/3453.htm`

### 软件学院详情页

- 标题：`td.titlestyle130886`
- 正文：`#vsb_content .v_news_content`
- 发布时间：详情页模板不统一，优先使用列表页日期；若详情页出现 `YYYY-MM-DD HH:mm` 再补充为发布时间。

## 审计结论

- 上述来源均为官网公开栏目，HTTP 访问返回 200。
- 两个域名 robots.txt 均未提供有效规则，采集策略仍按保守频率执行。
- 页面均为静态 HTML，第一阶段使用静态解析即可，暂不需要 Playwright。
- 软件学院部分栏目内容存在交叉复用，爬虫必须按来源 URL 与列表选择器绑定，不扩大抓取范围。
