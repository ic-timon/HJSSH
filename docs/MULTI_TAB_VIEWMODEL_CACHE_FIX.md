# Multi-Tab ViewModel Cache Fix

## 问题描述

创建第二个标签页后，只显示一个闪烁的光标，没有任何终端输出（欢迎信息等）。第一个标签页显示正常。

## 问题分析

通过日志分析，发现了两个关键问题：

### 1. ViewModel 在标签页切换时被 dispose

在原来的实现中：
- 每个标签页在激活时使用 `remember(session.config.id)` 创建 `TerminalViewModel`
- 当切换到其他标签页时，`DisposableEffect` 会在 `onDispose` 中销毁 `TerminalViewModel`
- 这导致标签页的终端状态丢失，SSH 连接也被断开

**日志证据：**
```
Line 164: [MainView] 🖥️ Disposing ViewModel for session: 863c365d-eb6f-4c1b-8e6b-6e1cf04c9f50
```

### 2. Observer 依赖 UI resize 事件

`TerminalViewModel` 的 `startOutputObserver()` 依赖以下条件：
1. `connect()` 完成（`isConnected == true`）
2. `resize()` 完成（`initialResizeDone == true`）

对于**后台标签页**（非激活状态创建的标签页）：
- `TerminalView` 不会被渲染
- 不会触发 `onSizeChanged` 回调
- 不会调用 `TerminalViewModel.resize()`
- `initialResizeDone` 始终为 `false`
- **Observer 永远不会启动**

**日志证据：**
```
Line 176: Initial resize done: false, Observer started: false
Line 187-188: Waiting for initial resize before starting output observer...
Line 191: Disposing ViewModel for session: eecc4bfd-... (还没 resize 就被切换了)
```

## 解决方案

### 1. 全局缓存所有 ViewModel

**文件：**`composeApp/src/jvmMain/kotlin/cn/hjhw/ssh/App.kt`

**修改内容：**
```kotlin
@Composable
private fun MainView(
    sessionManager: SessionManager,
    modifier: Modifier = Modifier,
) {
    // ✅ 修复：为所有 sessions 创建并缓存 ViewModel，避免切换时 dispose
    val viewModels = remember { mutableMapOf<String, TerminalViewModel>() }
    
    // 为所有 sessions 创建 ViewModel（如果还没有）
    LaunchedEffect(sessions) {
        sessions.forEach { session ->
            val connection = session.getConnection()
            if (connection != null && !viewModels.containsKey(session.config.id)) {
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
                val viewModel = TerminalViewModel(connection, scope)
                viewModels[session.config.id] = viewModel
                
                // 立即连接
                scope.launch {
                    viewModel.connect()
                }
            }
        }
        
        // 清理已删除的 sessions
        val sessionIds = sessions.map { it.config.id }.toSet()
        val toRemove = viewModels.keys.filter { it !in sessionIds }
        toRemove.forEach { id ->
            viewModels[id]?.dispose()
            viewModels.remove(id)
        }
    }
    
    // 渲染时从缓存中获取 ViewModel
    activeSession?.let { session ->
        val viewModel = viewModels[session.config.id]
        if (viewModel != null) {
            TerminalView(viewModel = viewModel, ...)
        }
    }
}
```

**关键改进：**
- 所有 `ViewModel` 在创建后立即缓存到 `viewModels` 中
- 切换标签页不会销毁 `ViewModel`
- 只有当 `Session` 真正被删除时，才会 dispose 对应的 `ViewModel`

### 2. 无条件启动 Observer

**文件：**`composeApp/src/commonMain/kotlin/cn/hjhw/ssh/ui/TerminalViewModel.kt`

**修改内容：**
```kotlin
suspend fun connect() {
    connection.connect()
    isConnected = true
    
    // 等待一小段时间，确保 shell 已经创建
    kotlinx.coroutines.delay(200)
    
    // 通知服务器当前终端尺寸
    connection.resize(terminalWidth, terminalHeight)
    
    // ✅ 修复：无论 initialResizeDone 状态如何，都启动 observer
    // 原因：后台标签页可能不会触发 UI resize 事件
    if (!observerStarted) {
        observerStarted = true
        
        // 如果还没有 resize，使用默认尺寸
        if (!initialResizeDone) {
            initialResizeDone = true  // 标记为已完成，避免后续重复处理
        }
        
        startOutputObserver()
    }
}
```

**关键改进：**
- `connect()` 完成后立即启动 `Observer`
- 不再等待 `resize()` 事件
- 如果没有 UI resize，使用默认的 `80x24` 尺寸
- 当 UI 真正渲染后，`resize()` 会更新终端尺寸

### 3. 简化 resize() 方法

**文件：**`composeApp/src/commonMain/kotlin/cn/hjhw/ssh/ui/TerminalViewModel.kt`

**修改内容：**
```kotlin
fun resize(width: Int, height: Int) {
    terminalWidth = width
    terminalHeight = height
    buffer.resize(width, height)
    
    // 标记第一次 resize 已完成
    if (!initialResizeDone) {
        initialResizeDone = true
    }
    
    // 通知 SSH 服务器终端尺寸变化
    scope.launch {
        try {
            connection.resize(width, height)
        } catch (e: Exception) {
            println(">>> [TerminalViewModel] SSH resize failed: ${e.message}")
            e.printStackTrace()
        }
    }
}
```

**关键改进：**
- 移除了启动 `Observer` 的逻辑（现在由 `connect()` 负责）
- 只负责调整终端尺寸

## 修复效果

### 修复前：
- 第一个标签页：正常显示
- 第二个标签页：只有光标闪烁，无任何输出
- 切换标签页：之前的标签页内容丢失

### 修复后：
- 第一个标签页：正常显示
- 第二个标签页：正常显示完整输出
- 切换标签页：所有标签页状态保持，内容不丢失
- 多个标签页可以同时运行，互不干扰

## 技术要点

### 1. Compose 状态管理
```kotlin
val viewModels = remember { mutableMapOf<String, TerminalViewModel>() }
```
- 使用 `remember` 确保 `viewModels` 在 recomposition 之间保持
- 使用 `mutableMapOf` 允许动态添加/删除 `ViewModel`

### 2. LaunchedEffect 依赖
```kotlin
LaunchedEffect(sessions) {
    // 每次 sessions 列表变化时执行
    sessions.forEach { session ->
        // 为新 session 创建 ViewModel
    }
}
```
- 依赖 `sessions`，当列表变化时自动更新
- 创建新 `ViewModel` 并立即连接
- 清理已删除 `Session` 的 `ViewModel`

### 3. Observer 启动时机
- **之前：**`connect()` 完成 + `resize()` 完成
- **现在：**`connect()` 完成立即启动
- **好处：**不依赖 UI 渲染，后台标签页也能正常工作

## 相关文件

- `composeApp/src/jvmMain/kotlin/cn/hjhw/ssh/App.kt`
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/ui/TerminalViewModel.kt`
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/session/Session.kt`
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/session/SessionManager.kt`
- `composeApp/src/jvmMain/kotlin/cn/hjhw/ssh/connection/SshConnectionImpl.kt`

## 日期

2025-11-14

