# Nano 退出后屏幕内容残留问题修复记录

## 问题描述

用户报告：使用 nano 编辑器时，退出 nano 后屏幕上的内容没有被正常清理，nano 显示的文本内容还残留在屏幕上，无法回到执行 nano 命令之前的终端状态。

## 问题现象

- 在终端中执行 `nano quick_start.sh`
- 进入 nano 编辑器，可以正常编辑
- 按 `Ctrl+X` 退出 nano
- **问题**：退出后，终端屏幕上仍然显示 nano 的界面内容（标题栏、文件内容、底部菜单），无法看到之前的命令历史和提示符

## 预期行为

标准终端中使用 nano 时：
1. 启动 nano 前，终端显示命令提示符和命令历史
2. 启动 nano 后，终端切换到**备用屏幕缓冲区**（Alternate Screen Buffer），显示 nano 界面
3. 退出 nano 后，终端自动切换回**主屏幕缓冲区**，恢复之前的命令历史和提示符

## 调试过程

### 1. 添加备用屏幕切换日志

在 `TerminalBuffer.kt` 和 `AnsiParser.kt` 中添加了详细的调试日志，用于追踪备用屏幕的切换过程：

```kotlin
// TerminalBuffer.kt
fun switchToAlternateScreen() {
    if (!useAlternateScreen) {
        println("      [TerminalBuffer] === Switching TO Alternate Screen ===")
        println("      [TerminalBuffer] Saving main screen (${visibleLines.size} lines) to alternate buffer")
        // ... 保存主屏幕内容
    }
}

fun switchToMainScreen() {
    if (useAlternateScreen) {
        println("      [TerminalBuffer] === Switching BACK TO Main Screen ===")
        println("      [TerminalBuffer] Restoring main screen from alternate buffer (${alternateVisibleLines.size} lines)")
        // ... 恢复主屏幕内容
    }
}

// AnsiParser.kt
47 -> {
    println("      [AnsiParser] ⚡ DEC Mode Set 47: Switching to alternate screen")
    buffer.switchToAlternateScreen()
}
1049 -> {
    println("      [AnsiParser] ⚡ DEC Mode Set 1049: Save cursor & switch to alternate screen")
    cursor.save()
    buffer.switchToAlternateScreen()
}
```

### 2. 分析 nano 启动和退出日志

**启动 nano 时接收的序列：**
```
ESC[?2004h          # 启用 bracketed paste mode
ESC(B               # 选择 G0 字符集
ESC)0               # 选择 G1 字符集
ESC[1;73r           # 设置滚动区域 (top=1, bottom=73)
ESC[m               # 重置 SGR 属性
0x0F                # Shift In (SI)
ESC[?7h             # 启用自动换行
ESC[?1h             # 启用应用光标键模式
ESC=                # 启用应用数字键盘模式
```

**退出 nano 时接收的序列：**
```
ESC[69B             # 光标下移 69 行
ESC[J               # 清除从光标到屏幕末尾
ESC[2B              # 光标下移 2 行
ESC[73;1H           # 光标移动到第 73 行第 1 列
\r                  # 回车
ESC[?1l             # 禁用应用光标键模式
ESC>                # 禁用应用数字键盘模式
ESC[?2004l          # 禁用 bracketed paste mode
```

**关键发现：**
- ❌ **完全没有看到 `⚡` 或 `===` 标记的备用屏幕切换日志！**
- ❌ **nano 没有发送 `ESC[?1049h` 或 `ESC[?47h` (切换到备用屏幕)**
- ❌ **nano 没有发送 `ESC[?1049l` 或 `ESC[?47l` (恢复主屏幕)**

### 3. 根本原因定位

**问题根源：SSH 连接时使用的终端类型不支持备用屏幕缓冲区。**

在 `SshConnectionImpl.kt` 中，使用了 `allocateDefaultPTY()`：

```kotlin
// 创建交互式会话
val newSession = client.startSession()
session = newSession

// 分配 PTY
newSession.allocateDefaultPTY()  // ❌ 使用默认 PTY，终端类型可能是 vt100 或类似的基本终端

// 启动 shell
shell = newSession.startShell()
```

`allocateDefaultPTY()` 通常会分配一个基本的终端类型（如 `vt100`），这种终端类型**不支持备用屏幕缓冲区**等高级特性。

当 nano 检测到终端类型不支持备用屏幕时，它会：
1. 直接在主屏幕缓冲区运行
2. 退出时只清除部分内容（使用 `ESC[J`），但不会恢复之前的屏幕内容
3. 导致 nano 的内容残留在屏幕上

## 解决方案

将 SSH 连接时分配的 PTY 终端类型改为 **`xterm-256color`**，这种终端类型支持：
- ✅ 备用屏幕缓冲区（Alternate Screen Buffer）
- ✅ 256 色显示
- ✅ 各种高级终端控制序列（光标控制、滚动区域等）

### 代码修改

**文件：** `composeApp/src/jvmMain/kotlin/cn/hjhw/ssh/connection/SshConnectionImpl.kt`

```kotlin
// 创建交互式会话
val newSession = client.startSession()
session = newSession

// 分配 PTY，使用 xterm-256color 支持完整的终端功能（包括备用屏幕缓冲区）
// 终端类型: xterm-256color
// 列数: 80, 行数: 24 (初始值，后续会通过 resize 调整)
// 宽度: 80*8 像素, 高度: 24*16 像素 (估计值)
// 终端模式: 空map (使用默认模式)
newSession.allocatePTY(
    "xterm-256color",  // 终端类型
    80,                 // 列数
    24,                 // 行数
    640,                // 宽度（像素）
    384,                // 高度（像素）
    emptyMap()          // 终端模式
)

// 启动 shell
shell = newSession.startShell()
```

### 修改说明

1. **终端类型**：`xterm-256color`
   - 兼容 xterm 的所有功能
   - 支持 256 色显示
   - 支持备用屏幕缓冲区
   - 支持所有现代终端特性

2. **初始尺寸**：80 列 × 24 行
   - 这只是初始值，连接后会立即通过 `resize()` 方法调整为实际窗口大小

3. **像素尺寸**：640×384
   - 估计值，基于常见的字符尺寸（8×16 像素）
   - 实际使用中由字符尺寸（列×行）决定

4. **终端模式**：使用空 Map（默认模式）
   - 采用终端默认的标准行为
   - 包括 ECHO、ICANON、ISIG 等标准模式

## 修复效果

修复后，nano 的行为应该符合标准终端：

1. **启动 nano 时**：
   - nano 检测到终端支持备用屏幕
   - 发送 `ESC[?1049h` 切换到备用屏幕并保存光标
   - 在备用屏幕中显示 nano 界面

2. **退出 nano 时**：
   - 发送 `ESC[?1049l` 切换回主屏幕并恢复光标
   - 主屏幕自动恢复，显示之前的命令历史和提示符
   - ✅ **屏幕内容正确清理，无残留！**

## 相关 ANSI 序列说明

### 备用屏幕缓冲区控制序列

| 序列 | 功能 | 说明 |
|------|------|------|
| `ESC[?47h` | 切换到备用屏幕 | 基本的备用屏幕切换 |
| `ESC[?47l` | 切换回主屏幕 | 恢复主屏幕显示 |
| `ESC[?1049h` | 切换到备用屏幕并保存光标 | 完整的状态保存（推荐） |
| `ESC[?1049l` | 恢复主屏幕和光标 | 完整的状态恢复（推荐） |

### 终端类型对比

| 终端类型 | 支持备用屏幕 | 颜色支持 | 使用场景 |
|----------|-------------|----------|----------|
| `vt100` | ❌ | 单色 | 基本终端，兼容性最好 |
| `xterm` | ✅ | 8 色 | 标准 X 终端 |
| `xterm-256color` | ✅ | 256 色 | 现代终端（推荐） |
| `xterm-truecolor` | ✅ | 1670万色 | 最高级终端 |

## 测试验证

### 测试步骤

1. 启动应用，连接到 SSH 服务器
2. 执行一些命令，观察命令历史
3. 执行 `nano quick_start.sh`
4. 在 nano 中随便编辑
5. 按 `Ctrl+X` 退出 nano
6. **验证**：屏幕应该恢复到执行 nano 之前的状态，显示命令历史

### 预期日志输出

启动 nano 时应该看到：
```
[AnsiParser] ⚡ DEC Mode Set 1049: Save cursor & switch to alternate screen
[TerminalBuffer] === Switching TO Alternate Screen ===
[TerminalBuffer] Saving main screen (73 lines) to alternate buffer
[TerminalBuffer]   Main screen line 0: root@server:~# nano quick_start.sh
[TerminalBuffer] Created blank alternate screen (73 lines)
```

退出 nano 时应该看到：
```
[AnsiParser] ⚡ DEC Mode Reset 1049: Switch to main screen & restore cursor
[TerminalBuffer] === Switching BACK TO Main Screen ===
[TerminalBuffer] Restoring main screen from alternate buffer (73 lines)
[TerminalBuffer]   Restoring line 0: root@server:~# nano quick_start.sh
[TerminalBuffer] Main screen restored (73 lines), useAlternateScreen=false
```

## 其他受益的应用

使用 `xterm-256color` 终端类型后，以下应用也将正常工作：

- ✅ **vi/vim**：完整的全屏编辑体验
- ✅ **less/more**：查看文件后正确恢复屏幕
- ✅ **htop/top**：系统监控工具的全屏显示
- ✅ **man**：帮助文档查看器
- ✅ **tmux/screen**：终端复用器

## 相关文件

- `composeApp/src/jvmMain/kotlin/cn/hjhw/ssh/connection/SshConnectionImpl.kt` - SSH 连接实现（修改位置）
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/TerminalBuffer.kt` - 备用屏幕缓冲区管理
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/AnsiParser.kt` - ANSI 序列解析（备用屏幕切换）

## 修复日期

2025年11月14日

