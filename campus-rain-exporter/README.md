# CampusMind 雨课堂本地导出器

在 Chrome 或 Edge 的扩展管理页开启开发者模式，选择“加载已解压的扩展程序”，并指向本目录。

1. 用户自行登录雨课堂官方网页。
2. 点击“**一键自动同步**”。扩展会刷新当前页，在已登录会话下捕获课程接口，并以课程卡片作为兜底。
3. 点击“复制导入 JSON”。
4. 在 CampusMind 的“雨课堂 JSON 导入”页粘贴并提交。

扩展只在用户主动开启期间处理路径包含 `course`、`class`、`homework`、`assignment`、`notice` 或 `announcement` 的 JSON 响应。它不读取请求头、Cookie、Token、密码或页面存储，也不向 CampusMind 发送请求。作业和通知的全量自动遍历需要按本校实际接口补充白名单，不能猜测私有接口。

运行最小校验：`node test-normalizer.js`。
