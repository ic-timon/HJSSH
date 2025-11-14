# Nano 编辑器完整修复记录

## 概述

本文档记录了 nano 编辑器在终端模拟器中的所有问题及其修复过程。

## 问题列表与修复

### 1. 显示错位和光标移动异常

**问题描述：**
- nano 启动后，光标位置显示不正确
- 文字内容显示错位
- 光标移动与实际位置不符

**根本原因：**
缺少两个关键的 ANSI 光标定位命令：
- `ESC[nG` (CHA - Cursor Horizontal Absolute) - 只改变光标的 X 坐标
- `ESC[nd` (VPA - Vertical Position Absolute) - 只改变光标的 Y 坐标

nano 大量使用这两个命令进行精确的光标定位，但终端模拟器未实现它们，导致所有依赖这些命令的布局都错位。

**修复方案：**
在 `AnsiParser.kt` 中添加了这两个命令的支持：

```kotlin
'G' -> { // CHA - Cursor Horizontal Absolute
    val col = params.getOrElse(0) { 1 }
    val newX = (col - 1).coerceAtLeast(0)
    cursor.moveTo(newX, cursor.y)
}
'd' -> { // VPA - Vertical Position Absolute
    val row = params.getOrElse(0) { 1 }
    val newY = (row - 1).coerceAtLeast(0)
    cursor.moveTo(cursor.x, newY)
}
```

**文件修改：**
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/AnsiParser.kt`

---

### 2. 标题栏宽度截断

**问题描述：**
- nano 的顶部标题栏在约 80 列处被截断
- 文件名只显示前几个字符（如 "quick_start.sh" 显示为 "qui"）

**根本原因：**
当终端缓冲区从 80x24 调整到 163x73 时，现有的 `TerminalLine` 对象保留了旧的 `maxWidth=80`。由于 `TerminalLine` 的 `maxWidth` 是在构造时设置的不可变属性，导致即使终端宽度已经扩展，行仍然无法存储超过 80 个字符。

**修复方案：**
在 `TerminalBuffer.resize()` 中，当宽度改变时重新创建所有 `TerminalLine` 对象：

```kotlin
// 重新创建所有可见行
val newVisibleLines = mutableListOf<TerminalLine>()
for (oldLine in visibleLines) {
    val newLine = TerminalLine(newWidth)
    // 复制现有内容
    val cells = oldLine.getCells()
    for (i in cells.indices) {
        if (i < newWidth) {
            newLine.setCell(i, cells[i])
        }
    }
    newVisibleLines.add(newLine)
}
visibleLines.clear()
visibleLines.addAll(newVisibleLines)
```

**文件修改：**
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/TerminalBuffer.kt`

---

### 3. 退出后屏幕内容残留

**问题描述：**
- 使用 nano 编辑文件后，按 Ctrl+X 退出
- 终端屏幕仍然显示 nano 的界面内容
- 无法看到之前的命令历史和提示符

**根本原因：**
SSH 连接时使用的终端类型不正确。使用 `allocateDefaultPTY()` 时，终端类型默认可能不支持备用屏幕缓冲区（Alternate Screen Buffer）功能，导致 nano 不发送切换备用屏幕的 ANSI 序列。

**修复方案：**
在 SSH 连接建立时，显式指定终端类型为 `xterm-256color`：

```kotlin
// 分配 PTY，使用 xterm-256color 支持完整的终端功能
newSession.allocatePTY(
    "xterm-256color",  // 终端类型
    80,                 // 列数（初始值）
    24,                 // 行数（初始值）
    640,                // 宽度（像素）
    384,                // 高度（像素）
    emptyMap()          // 终端模式
)
```

`xterm-256color` 终端类型支持：
- 备用屏幕缓冲区 (`ESC[?1049h/l`)
- 256 色彩模式
- 所有标准 VT100/ANSI 功能

**文件修改：**
- `composeApp/src/jvmMain/kotlin/cn/hjhw/ssh/connection/SshConnectionImpl.kt`

---

### 4. 字符集序列显示为 'B'

**问题描述：**
- nano 界面中出现大量的字母 'B'
- 这些 'B' 不应该显示

**根本原因：**
`ESC(B` 是字符集选择序列（Select Character Set），用于切换字符集。nano 使用这些序列在 ASCII 和 DEC 特殊图形字符集之间切换。解析器未正确处理这些序列，导致 'B' 被当作普通字符显示。

**修复方案：**
在 `AnsiParser.kt` 中添加字符集选择状态和处理：

```kotlin
private enum class ParseState {
    NORMAL,
    ESCAPE,
    CSI,
    OSC,
    PARAMETER,
    CHARSET,  // 新增：字符集选择状态
}

// 在 ESCAPE 状态下识别字符集序列
'(', ')' -> {
    // ESC ( B = 选择 G0 字符集为 ASCII
    // ESC ) B = 选择 G1 字符集为 ASCII
    // ESC ( 0 = 选择 G0 字符集为 DEC 特殊图形字符集
    state = ParseState.CHARSET
}

// 新增 CHARSET 状态处理
ParseState.CHARSET -> {
    // 字符集选择序列：ESC ( X 或 ESC ) X
    // 我们使用 UTF-8，所以简单地忽略字符集标识符
    state = ParseState.NORMAL
}
```

同时添加了 SI/SO 控制字符的支持：
- `0x0E` (Shift Out) - 切换到 G1 字符集
- `0x0F` (Shift In) - 切换回 G0 字符集

**文件修改：**
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/AnsiParser.kt`

---

## 新增 ANSI 命令支持

为了提高终端兼容性，新增了以下常用 ANSI/VT100 命令：

### 光标定位命令
- `ESC[nG` - CHA (Cursor Horizontal Absolute) - 水平绝对定位
- `ESC[nd` - VPA (Vertical Position Absolute) - 垂直绝对定位

### 编辑命令
- `ESC[nX` - ECH (Erase Character) - 擦除字符
- `ESC[nS` - SU (Scroll Up) - 向上滚动
- `ESC[nT` - SD (Scroll Down) - 向下滚动

### 光标保存/恢复
- `ESC[s` - SCP (Save Cursor Position) - 保存光标位置
- `ESC[u` - RCP (Restore Cursor Position) - 恢复光标位置

### 制表位
- `ESC[nI` - CHT (Cursor Horizontal Forward Tabulation) - 向前跳转制表位
- `ESC[nZ` - CBT (Cursor Backward Tabulation) - 向后跳转制表位

---

## 代码优化

### 1. 调试日志清理

**优化前：**
- 大量详细的调试日志输出
- 每个字符写入都打印日志
- 光标移动、ANSI 序列解析都有详细输出

**优化后：**
- 移除了详细的字符写入日志
- 移除了常规光标移动日志
- 移除了模式设置/重置日志
- 仅保留关键错误日志（未知的 ANSI 命令）

**好处：**
- 大幅提升性能
- 减少控制台输出噪音
- 便于识别真正的问题

### 2. 代码注释优化

- 添加了详细的命令说明和标准缩写（如 CHA, VPA, CUU等）
- 简化了冗长的注释
- 保留了关键的实现说明

---

## 测试验证

### nano 编辑器测试
✅ **通过** - 启动正常，无显示错位
✅ **通过** - 光标位置准确
✅ **通过** - 标题栏完整显示文件名
✅ **通过** - 底部菜单完整显示
✅ **通过** - 文字颜色正确渲染（反色、高亮等）
✅ **通过** - 退出后正确恢复命令行界面

### 其他全屏应用测试
✅ **通过** - `btop` 显示正常
✅ **通过** - `vi/vim` 编辑器工作正常
✅ **通过** - 常规命令行操作无影响

---

## 相关文档

- `ANSI_PARSER_ENHANCEMENTS.md` - ANSI 解析器增强记录
- `NANO_WIDTH_FIX.md` - Nano 标题栏宽度截断修复
- `ALTERNATE_SCREEN_FIX.md` - 备用屏幕缓冲区修复

---

## 技术总结

### 关键经验

1. **终端类型的重要性**
   - 终端类型决定了服务器发送哪些控制序列
   - `xterm-256color` 是现代终端的标准选择
   - 不正确的终端类型会导致功能缺失

2. **ANSI 命令的完整性**
   - 即使是"不常见"的命令（如 'G'、'd'）对某些应用也至关重要
   - 需要实现完整的 ANSI/VT100 命令集
   - 字符集选择序列虽然在 UTF-8 环境下不常用，但必须正确处理

3. **状态管理的挑战**
   - 不可变属性（如 `maxWidth`）在状态变化时需要特别注意
   - 对象重建有时是必要的解决方案
   - 状态持久化和恢复需要完整的实现

4. **调试策略**
   - 详细日志在调试阶段非常有价值
   - 生产环境需要清理日志以保证性能
   - 保留关键错误日志有助于后续问题诊断

### 性能优化

- 移除了每字符日志，减少 I/O 开销
- 简化了光标移动逻辑，减少条件判断
- 优化了字符集序列处理，避免不必要的状态切换

---

## 未来改进方向

1. **完善 DEC 特殊图形字符集**
   - 当前简单忽略了字符集切换
   - 可以实现线条字符的 UTF-8 映射

2. **增加更多 ANSI 命令**
   - `ESC[nE` - CNL (Cursor Next Line)
   - `ESC[nF` - CPL (Cursor Previous Line)
   - 更完整的 SGR 参数支持

3. **性能监控**
   - 添加性能指标收集
   - 监控渲染帧率
   - 优化大量文本输出的处理

---

**修复完成日期：** 2025-11-14
**修复版本：** 1.0
**状态：** ✅ 完全修复

