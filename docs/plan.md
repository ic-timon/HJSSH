黑解SSH 终端 

一、项目与基础设施
1. 项目结构与基础配置

任务

初始化 Kotlin Multiplatform 项目（targets: jvm/desktop）

集成 Compose Multiplatform

配置 Gradle 构建、依赖管理（kotlinx.coroutines、sshj、kotlinx.serialization 等）

测试逻辑

构建测试：确保三个 OS 下都能成功构建。

启动测试：执行空 Compose App 启动测试。

CI 校验：Lint、Static Analysis（detekt、ktlint）

二、核心网络层（SSH）
1. 读取并解析标准 OpenSSH 配置文件；
任务
支持常见指令：

Host, HostName, User, Port, IdentityFile, ProxyCommand, ForwardAgent, StrictHostKeyChecking 等；

支持多条 Host 条目（通配符如 Host *, Host *.example.com）；

提供 API：按 Host 名称查找解析结果；

单元测试：

单元测试：解析器正确性

使用临时文件写入测试内容。

输入样例：
```
Host test
  HostName 198.51.100.10
  User testuser
  Port 2222
  IdentityFile ~/.ssh/id_rsa
```
断言：

解析结果包含 1 个 host

host == "test"

hostName == "198.51.100.10"

user == "root"

port == 2222

identityFile 展开为绝对路径


单元测试：多条配置与通配符

输入样例：
```
Host *
  User common
Host server1
  HostName example.com
  Port 22
```
断言：

findHost("server1") → user == "common"（继承默认）

findHost("unknown") → 使用通配符 * 条目

大小写不敏感（HostName, User）

单元测试：容错

输入样例：
```
Host test
  HostName
  Port abc
  UnknownKey something

```
断言：

不抛异常；

hostName == null；

port == null；

忽略 UnknownKey。

集成测试：真实 config 文件

测试读取当前环境的 ~/.ssh/config（若存在）

调用 SshConfigParser.parse(defaultPath)

拿到第一个 host (hosts.firstOrNull())

使用该 host 建立 SSH 连接

```
val host = config.hosts.first()
val conn = SshConnectionImpl(host.hostName!!, host.user!!, host.port ?: 22, host.identityFile)
conn.connect()
conn.exec("echo OK")
```

断言输出包含 "OK"



2. SSH 接口定义

任务

定义跨模块接口 SshConnection（open、close、write、resize、observeOutput、isConnected）

定义连接参数数据类（host、port、user、authMethod、keepAliveInterval）

测试逻辑

单元测试：接口 contract 测试（mock 实现，验证状态流）

3. SSH 实现（sshj）

任务

实现基于 sshj 的连接管理：

连接与认证（密码、密钥）

PTY 创建（设置终端类型、宽高、字符集）

流式输入输出（ChannelInputStream → Flow<String>）

Resize 支持（发送 window-change 请求）

KeepAlive 心跳

异常恢复（连接断开、超时、认证失败）

测试逻辑

集成测试（CI 中启动 docker openssh-server）：

成功连接测试（echo 命令返回 OK）

错误凭证认证失败测试

断开重连测试（docker 暂停 → 恢复）

Resize 测试（验证 window-change 请求发出）

三、终端引擎（ANSI/VT100 解析）
4. 数据模型与行缓冲

任务

定义行数据结构 TerminalLine

实现滚动缓冲区（ring buffer 或动态 list）

实现光标状态（x, y, 样式）

测试逻辑

单元测试：行追加、截断、滚动边界条件

内存压力测试：持续追加 10^5 行后无泄漏

5. ANSI/VT100 解析器

任务

实现对控制序列（CSI、OSC、SGR、cursor movement）的解析

支持 256 色 / TrueColor、光标移动、清屏、文本样式

异常输入容忍（乱序、截断）

测试逻辑

单元测试：

输入 "\u001b[31mred\u001b[0m" → 输出样式红色

光标移动命令测试（上下左右）

多字节 UTF-8 输入测试（中日韩字符）

随机序列 Fuzz 测试（防崩溃）

6. 输入映射（键盘事件 → 控制序列）

任务

建立键盘映射表（例如 Ctrl+C → 0x03、Enter → \r）

支持常见快捷键组合（Ctrl、Alt、Fn）

支持终端模式（normal/app key mode）

测试逻辑

单元测试：键事件映射正确性（例如 “Up arrow” → “ESC [ A”）

模拟交互测试：输入命令 “ls\r” → 发送字节流与期望一致

四、UI 层（Compose 终端）
7. 终端文本渲染

任务

在 Compose Canvas 上绘制字符矩阵

应用样式（颜色、粗体、下划线）

光标闪烁动画

滚动实现（虚拟化，仅渲染可见区域）

测试逻辑

UI 快照测试：渲染已知行 → 比对 bitmap 输出（golden test）

性能测试：10k 行渲染 < 50ms

光标闪烁测试（时间稳定）

8. 输入与焦点处理

任务

处理键盘输入、粘贴（clipboard）、复制选中区域

支持多行选择、拖拽选择

支持右键菜单（复制/粘贴/清屏）

测试逻辑

UI 自动化测试：

模拟键盘输入并验证发送内容

模拟粘贴（Clipboard.set → 验证 write 调用）

选择文本 → 复制 → 验证剪贴板内容一致

9. 滚动与回放

任务

支持 scrollback（上下滚动查看历史）

支持 session 录制与回放（时间戳流）

测试逻辑

单元测试：scrollback 缓冲正确（上下滚动时行索引正确）

回放测试：录制 session → 回放 → 验证输出一致性

性能测试：长时间录制内存无增长

五、应用功能层
10. 会话管理

任务

会话配置（保存 host、user、port、auth）

多会话标签页（Tab / Split）

会话重连、关闭逻辑

测试逻辑

单元测试：会话序列化、加载

UI 测试：开多个 Tab 并行连接（模拟 echo）

异常测试：关闭 SSH 时释放资源（线程、流）

11. 设置与主题

任务

实现 UI 主题配置（暗色、亮色、自定义配色）

字体与字号配置

保存到本地配置文件（JSON）

测试逻辑

单元测试：配置序列化/反序列化一致性

UI 测试：切换主题后 UI 颜色变化正确

快照测试：暗色/亮色主题对比输出一致

12. 日志与安全

任务

记录 session 输出到文件（可选启用）

日志加密（防止敏感数据明文存储）

Key 文件管理（加载、验证、加密存储）

测试逻辑

单元测试：日志文件完整性、内容一致性

安全测试：加载加密私钥正确性；解密失败时拒绝连接

模拟崩溃恢复：日志写入无数据丢失

六、测试与发布支持
13. 自动化测试框架集成

任务

引入 JUnit5 + kotest

UI Snapshot 测试集成（Compose Test Rule）

性能与内存监控脚本（JMH 或自定义）

测试逻辑

分析和优化代码质量和优化代码复杂度

CI 流程中运行全套单元+集成+UI 测试

SSH 到 .ssh/config 中第一个Host 自动起停

结果报告（HTML 报告、code coverage）

14. 打包与多平台分发

任务

使用 Compose Desktop 的 native 分发（.exe、.dmg、.deb/.AppImage）

先只做

打包 icon、签名、版本号

生成更新检查逻辑（可选）

测试逻辑

构建验证：三平台都能打出包

启动验证：执行包后能启动、显示窗口

兼容性测试：Windows/macOS/Linux 字体与输入法兼容

七、非功能目标验证（持续进行）
15. 性能与稳定性验证

任务

大数据流压力测试（例如持续 cat 大文件）

快速重连与网络抖动恢复测试

多会话并发压力测试

测试逻辑

脚本化负载测试：模拟 10MB 连续输出

性能计时：帧率、内存曲线

稳定性测试：长连接 24h 不崩溃

16. 用户体验与健壮性

任务

处理窗口 resize、最小化、系统休眠恢复

错误提示（连接失败、超时、权限）

健壮性：防止死锁、协程取消安全

测试逻辑

自动化 UI 测试：resize 窗口时渲染不混乱

模拟网络断线恢复

单元测试：协程取消后所有 job 被释放

总结

这个任务规划对应三层验证逻辑：

单元层（解析、缓冲、映射）

集成层（SSH、I/O、回放）

UI 层（Compose 渲染与交互）

在执行时，每个模块都可以独立实现、独立测试。
所有测试都能在 CI 上自动运行，不依赖人工交互。