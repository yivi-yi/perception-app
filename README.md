# Perception

深色粉调日历 APP，前后端一体化。内置 MCP 服务器，可在其他客户端填地址调用工具。

## 页面
- 日历：纪念日 + 日历 + 行程 / 闹钟信息条 + 添加弹窗
- 设置：启动/停止 MCP 服务器，显示调用地址

## 云端打包
push 到 main 分支自动触发 GitHub Actions 构建 Debug APK，在 Actions 页面下载 `perception-debug-apk`。

## MCP 调用地址
启动后：`http://<手机IP>:9001/mcp`
