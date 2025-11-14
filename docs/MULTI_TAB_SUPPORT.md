# 多标签页支持

## 📋 概述

HJSSH 支持完整的多标签页功能，允许用户同时管理多个 SSH 连接，在不同的终端会话之间快速切换。**后台标签页的终端连接会持续保持活跃状态**，不会因为切换标签而断开。

---

## ✨ 核心特性

### 1. **多会话管理**
- ✅ 同时打开多个 SSH 连接
- ✅ 每个标签页对应一个独立的终端会话
- ✅ 标签页状态指示器（连接中/已连接/失败/断开）
- ✅ 标签页名称显示（主机名）

### 2. **后台会话保持**
- ✅ **切换标签页时，后台终端继续运行**
- ✅ **SSH 连接保持活跃**
- ✅ **终端状态完整保留**（缓冲区、光标位置、历史记录）
- ✅ 只有关闭标签页才会断开连接

### 3. **会话持久化**
- ✅ 自动保存会话配置到本地文件
- ✅ 重启应用后自动恢复上次的会话列表
- ✅ 不自动连接，用户手动选择连接

---

## 🎯 使用方法

### 创建新会话

#### 方式 1: 从 SSH 配置中选择
1. 点击左上角的 `▶` 按钮展开侧边栏
2. 从 `~/.ssh/config` 中选择一个已配置的主机
3. 自动创建新标签页并连接

#### 方式 2: 手动新建
1. 点击标签栏右侧的 `+ New Session` 按钮
2. 创建一个新的 Mock 会话（用于测试）

### 切换会话
- **鼠标点击**：直接点击标签页切换
- **快捷键**（计划中）：
  - `Ctrl+Tab`: 下一个标签页
  - `Ctrl+Shift+Tab`: 上一个标签页
  - `Ctrl+1~9`: 切换到第 1~9 个标签页

### 关闭会话
- 点击标签页上的 `×` 按钮
- 快捷键（计划中）：`Ctrl+W`

---

## 🏗️ 架构设计

### 核心组件

#### 1. **SessionManager**
**职责**: 管理所有会话的生命周期

```kotlin
class SessionManager {
    // 存储所有会话（包括活动和后台会话）
    private val sessions: MutableMap<String, Session>
    
    // 当前活动会话 ID
    private var activeSessionId: String?
    
    // 创建新会话
    fun createSession(config: SessionConfig): Session
    
    // 切换活动会话（不销毁其他会话）
    fun setActiveSession(id: String?)
    
    // 删除会话（断开连接并释放资源）
    fun deleteSession(id: String)
}
```

**关键点**:
- `sessions` Map 存储所有会话，无论是否为活动会话
- `setActiveSession()` 只是切换 `activeSessionId`，**不会销毁其他会话**
- 只有调用 `deleteSession()` 或 `dispose()` 才会断开连接

#### 2. **Session**
**职责**: 管理单个 SSH 连接和终端状态

```kotlin
class Session {
    val config: SessionConfig
    private var connection: SshConnection?
    
    // 连接状态流（CONNECTING, CONNECTED, FAILED, DISCONNECTING, DISCONNECTED）
    val state: StateFlow<SessionState>
    
    // 连接到 SSH 服务器
    fun connect()
    
    // 断开连接并释放资源
    fun dispose()
}
```

#### 3. **TerminalViewModel**
**职责**: 管理终端的 UI 状态和用户交互

```kotlin
class TerminalViewModel(connection: SshConnection) {
    val terminalBuffer: TerminalBuffer
    val cursorState: CursorState
    
    // 处理 SSH 输出
    private fun observeOutput()
    
    // 发送用户输入
    fun sendInput(text: String)
}
```

**重要**: 每个 `Session` 对应一个 `TerminalViewModel`，使用 `remember(session.config.id)` 确保标签页切换时 ViewModel 不会被销毁。

---

## 🔄 数据流

### 创建会话流程
```
用户操作 → SessionManager.createSession()
         → 创建 Session 实例
         → 添加到 sessions Map
         → 保存到本地存储
         → 设置为活动会话
         → Session.connect()
         → 建立 SSH 连接
         → 创建 TerminalViewModel
         → 开始接收输出
```

### 切换标签页流程
```
用户点击标签页 → SessionManager.setActiveSession(id)
               → 更新 activeSessionId
               → 更新 _activeSession StateFlow
               → UI 重新渲染（显示新的 TerminalView）
               → **后台会话继续运行**
```

### 关闭标签页流程
```
用户点击 × → SessionManager.deleteSession(id)
           → 从 sessions Map 中移除
           → Session.dispose()
           → 断开 SSH 连接
           → 释放 TerminalViewModel 资源
           → 保存配置到本地存储
           → 如果是活动会话，清空 activeSessionId
```

---

## 💾 会话持久化

### 存储位置
```
~/.hjssh/sessions.json
```

### 存储内容
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "myserver",
    "host": "example.com",
    "port": 22,
    "user": "user",
    "authMethod": {
      "type": "KeyFile",
      "keyPath": "~/.ssh/id_rsa",
      "passphrase": null
    }
  }
]
```

**注意**: 
- 密码和密钥密码不会被存储（出于安全考虑）
- 重启应用后，会话列表会恢复，但不会自动连接
- 用户需要手动点击标签页或 "Connect" 按钮来建立连接

---

## 🔧 UI 改进

### 已实现
✅ **侧边栏优化**
   - 展开/收起按钮移到顶部，始终可见
   - 收起时不占用终端显示区域
   - 平滑的展开/收起动画

✅ **空状态优化**
   - 启动时不自动创建 mock 会话
   - 空状态时显示友好提示
   - 引导用户创建第一个会话

✅ **标签栏优化**
   - 状态指示器（绿色=已连接，橙色=连接中，红色=失败，灰色=断开）
   - "+ New Session" 按钮更明显
   - 标签页关闭按钮

### 计划中
⏳ **快捷键支持**
   - `Ctrl+T`: 新建标签页
   - `Ctrl+W`: 关闭当前标签页
   - `Ctrl+Tab` / `Ctrl+Shift+Tab`: 切换标签页
   - `Ctrl+1~9`: 快速切换到指定标签页

⏳ **标签页增强**
   - 右键菜单（重命名、复制、移动）
   - 拖拽排序
   - 活动指示器（后台有输出时闪烁）

⏳ **会话管理增强**
   - 会话分组（按项目/环境）
   - 会话搜索和筛选
   - 批量操作（关闭所有、关闭其他）

---

## ⚡ 性能考虑

### 内存管理
- **后台会话占用**: 每个会话独立占用内存（缓冲区、历史记录、图像）
- **默认配置**: 
  - 终端缓冲区: 80 列 × 24 行
  - 滚动历史: 1000 行
  - 估算单个会话: ~5-10 MB（不含图像）
- **建议**: 不超过 20 个同时打开的会话

### 网络连接
- **KeepAlive**: 所有会话默认启用 SSH KeepAlive，防止超时断开
- **带宽**: 后台会话继续接收数据，即使不可见
- **优化**: 考虑为后台会话降低刷新频率（计划中）

### UI 渲染
- **活动会话**: 全速渲染（60 FPS）
- **后台会话**: 不渲染 UI，但继续接收和解析数据
- **切换延迟**: < 100ms（依赖于终端缓冲区大小）

---

## 🐛 常见问题

### Q1: 切换标签页后，后台会话还在运行吗？
**A**: 是的！后台会话的 SSH 连接、终端缓冲区、光标状态等都会完整保留。只有关闭标签页（点击 `×`）才会断开连接。

### Q2: 后台会话会自动重连吗？
**A**: 不会。如果后台会话因网络问题断开，需要用户手动切换到该标签页并点击 "Connect" 重新连接。

### Q3: 如何查看后台会话的状态？
**A**: 查看标签页左侧的状态指示器：
- 🟢 绿色: 已连接
- 🟠 橙色: 连接中
- 🔴 红色: 连接失败
- ⚪ 灰色: 已断开

### Q4: 会话配置保存在哪里？
**A**: `~/.hjssh/sessions.json`，包含会话名称、主机、端口、用户名和认证方式（不含密码）。

### Q5: 如何清理所有会话？
**A**: 当前需要手动逐个关闭标签页。未来计划添加 "关闭所有" 功能。

### Q6: 后台会话的输出会丢失吗？
**A**: 不会。后台会话继续接收和解析数据，存储在终端缓冲区中。切换回该标签页时，可以看到最新的终端状态（包括滚动历史）。

### Q7: 可以同时连接到同一个服务器的多个会话吗？
**A**: 可以！每个标签页对应一个独立的 SSH 连接，可以打开多个连接到同一服务器的会话。

---

## 📝 代码示例

### 创建和切换会话
```kotlin
// 创建新会话
val config = SessionConfig(
    id = UUID.randomUUID().toString(),
    name = "Production Server",
    host = "prod.example.com",
    port = 22,
    user = "admin",
    authMethod = SessionConfig.SerializedAuthMethod.KeyFile(
        keyPath = "~/.ssh/id_rsa",
        passphrase = null
    )
)
val session = sessionManager.createSession(config)
sessionManager.setActiveSession(session.config.id)
session.connect()

// 切换到另一个会话
val anotherId = "existing-session-id"
sessionManager.setActiveSession(anotherId)

// 关闭会话
sessionManager.deleteSession(session.config.id)
```

### 监听会话状态
```kotlin
// 在 Composable 中观察会话列表
val sessions by sessionManager.sessionsList.collectAsState()

// 观察活动会话
val activeSession by sessionManager.activeSession.collectAsState()

// 观察单个会话的连接状态
val state by session.state.collectAsState()
when (state) {
    SessionState.CONNECTING -> println("Connecting...")
    SessionState.CONNECTED -> println("Connected!")
    SessionState.FAILED -> println("Connection failed")
    SessionState.DISCONNECTED -> println("Disconnected")
}
```

---

## 🎨 UI 布局

```
┌─────────────────────────────────────────────────────────────────┐
│ [▶] SSH Hosts           [Tab 1] [Tab 2] [Tab 3]  [+ New Session]│
├─────────────┬───────────────────────────────────────────────────┤
│             │                                                     │
│  • myserver     │  Terminal Content (Active Tab)                     │
│  • web1     │  $ ls -la                                          │
│  • web2     │  total 48                                          │
│  • db1      │  drwxr-xr-x  5 user user 4096 Nov 14 10:30 .       │
│             │  drwxr-xr-x 10 user user 4096 Nov 14 09:00 ..      │
│             │  -rw-r--r--  1 user user  220 Nov 14 09:00 .bash...│
│             │  ...                                                │
│             │                                                     │
│             │  $ _                                                │
│             │                                                     │
└─────────────┴───────────────────────────────────────────────────┘
```

**收起侧边栏后**:
```
┌─────────────────────────────────────────────────────────────────┐
│ [▶]                     [Tab 1] [Tab 2] [Tab 3]  [+ New Session]│
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  Terminal Content (Active Tab) - 全屏显示                        │
│  $ ls -la                                                         │
│  total 48                                                         │
│  drwxr-xr-x  5 user user 4096 Nov 14 10:30 .                     │
│  drwxr-xr-x 10 user user 4096 Nov 14 09:00 ..                    │
│  -rw-r--r--  1 user user  220 Nov 14 09:00 .bash_profile         │
│  ...                                                              │
│                                                                   │
│  $ _                                                              │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
```

---

## 📚 相关文档

- [SESSION_MANAGEMENT.md](SESSION_MANAGEMENT.md) - 会话管理详细文档
- [SSH_CONFIG_PARSER.md](SSH_CONFIG_PARSER.md) - SSH 配置文件解析
- [TERMINAL_ARCHITECTURE.md](TERMINAL_ARCHITECTURE.md) - 终端架构文档

---

**最后更新**: 2025-11-14  
**功能状态**: ✅ 已实现并稳定运行

