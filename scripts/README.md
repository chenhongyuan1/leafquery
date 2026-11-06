# 本地联调脚本

`weather-gateway.mjs` 为 Dify 天气回调提供固定路径、GET 与参数校验、并发限制和等待超时。按用户要求无需自定义请求头。使用根目录的 [start-weather-tunnel.cmd](../start-weather-tunnel.cmd) 管理网关与临时隧道；它们使用 Node.js 和 Windows OpenSSH，不需要额外 npm 依赖。

公网入口由第三方 Pinggy 提供，用户已授权它转发天气参数和响应。和风天气私钥不会交给网关。运行信息和专用 SSH 身份位于被 Git 忽略的 `.leafquery-runtime/weather-tunnel/`；当前不生成或读取网关访问密钥。

修改网关后可执行 `start-weather-tunnel.cmd restart-gateway`，仅重启网关并保留 SSH 隧道和现有临时 URL。到期后的新隧道仍会分配新 URL。

模拟后端验证：

```powershell
node scripts/weather-gateway.test.mjs
```

功能、当前状态和验证限制见 [Dify 天气联调](../docs/biz-modules/04-dify-weather-tunnel/README.md)。
