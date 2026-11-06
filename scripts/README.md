# 本地联调脚本

`weather-gateway.mjs` 为 Dify 天气回调提供固定路径、GET 与参数校验、并发限制和等待超时。按用户要求无需自定义请求头。使用根目录的 [ngrok-weather.cmd](../ngrok-weather.cmd) 管理网关与 ngrok 固定域名入口，需要 Node.js 和 ngrok，不需要额外 npm 依赖。

首次双击 [setup-ngrok.cmd](../setup-ngrok.cmd)，本人登录 ngrok 并在本机隐藏输入 Authtoken。项目本地配置、工具、进程状态及首次实际域名保存在被 Git 忽略的 `.leafquery-runtime/weather-tunnel/`，后续启动显式复用这个域名。ngrok 只连接天气网关，和风天气私钥保留在 Java 后端。

`ngrok-weather-support.ps1` 提供配置编码及管理 API 地址识别。仅接受指向 `http://127.0.0.1:18787` 的 HTTPS 端点；已有固定域名与 API 返回值不一致时不更新 Dify 地址。管理 API 绑定回环地址 18788，客户端日志与流量检查关闭。

修改网关后可执行 `ngrok-weather.cmd restart-gateway`，仅重启网关并保留 ngrok 连接。缺少账号认证时提示 setup，保留已有服务；迁移成功后才停止旧受管 Pinggy 进程。

模拟后端验证：

```powershell
node scripts/weather-gateway.test.mjs
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ngrok-weather-support.test.ps1
```

功能、当前状态和验证限制见 [Dify 天气联调](../docs/biz-modules/04-dify-weather-tunnel/README.md)。
