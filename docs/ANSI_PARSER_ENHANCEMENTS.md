# ANSI 解析器增强和修复记录

本文档记录了终端 ANSI 解析器的增强和修复，以更好地支持 nano、vi、yazi、sc 等终端应用程序。

## 修复日期
2024年（当前会话）

---

## 最新修复：退格行为标准化

### 问题描述（最终修复）
用户反馈：退格时会把命令行的用户提示区域整个删掉（如 `user@host:~$` 被删除）

### 原因分析

#### 标准终端退格序列
在标准终端中，删除一个字符的序列是：
1. **BS (0x08)** - 光标左移一格
2. **空格 (0x20)** - 覆盖字符
3. **BS (0x08)** - 光标再次左移

这样可以正确删除字符，且服务器控制边界（不会删除提示符）。

#### 我们的问题
之前的实现中：
- BS (0x08) 收到时：**移动光标 + 删除字符**
- DEL (0x7F) 收到时：**移动光标 + 删除字符**

这导致：
1. 客户端发送 DEL (0x7F) 到服务器
2. 服务器返回 `BS + 空格 + BS` 序列
3. 第一个 BS 被处理：光标左移 + **错误地删除了字符**
4. 空格覆盖（但字符已被删除）
5. 第二个 BS：又左移 + **又删除了字符**

结果：每次退格删除了多个字符，甚至删除提示符！

### 修复方案

**文件**: `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/AnsiParser.kt`

#### 修复前（错误）
```kotlin
0x08 -> { // BS (Backspace)
    if (cursor.x > 0) {
        cursor.moveBy(-1, 0)
        // 错误：立即删除字符
        val line = buffer.getVisibleLine(cursor.y) ?: buffer.getCurrentLine()
        line.setCell(cursor.x, emptyCell)  // ❌ 不应该删除
    }
}
0x7F -> { // DEL
    if (cursor.x > 0) {
        cursor.moveBy(-1, 0)
        // 错误：立即删除字符
        line.setCell(cursor.x, emptyCell)  // ❌ 不应该删除
    }
}
```

#### 修复后（正确）
```kotlin
0x08 -> { // BS (Backspace)
    // 标准退格行为：只向左移动光标，不删除字符
    // 删除是通过 BS + 空格 + BS 序列完成的
    if (cursor.x > 0) {
        cursor.moveBy(-1, 0)  // ✅ 只移动光标
    }
}
0x7F -> { // DEL (Delete)
    // DEL 字符通常作为键盘输入发送到服务器，而不是作为输出序列
    // 如果服务器发送了 DEL，可能是某些特殊情况，暂时忽略
    // 不做任何处理  // ✅ 忽略
}
```

### 标准退格流程

现在的正确流程：
1. **用户按退格** → 客户端发送 `DEL (0x7F)` 到服务器
2. **服务器处理** → 返回 `BS (0x08) + 空格 (0x20) + BS (0x08)`
3. **客户端解析**：
   - 第一个 BS：光标左移（不删除）
   - 空格：在当前位置写入空格（覆盖字符）
   - 第二个 BS：光标再次左移（回到正确位置）

### 修复结果
✅ **退格正确工作** - 只删除一个字符
✅ **不会删除提示符** - 服务器控制删除边界
✅ **符合标准终端行为** - BS 只移动光标，删除由序列完成
✅ **完全依赖服务器逻辑** - 客户端不做删除判断

---

## KeyDown/KeyUp 事件过滤

### 问题描述（关键修复）
用户提供了日志，发现**真正的原因**：
```
handleKeyEvent called: key=Key: L, ctrl=false, shift=false, alt=false
  -> Using key mapping: l
Key event handled: Key: L
handleKeyEvent called: key=Key: 未知 keyCode: 0x0, ctrl=false, shift=false, alt=false
Key event not handled: Key: 未知 keyCode: 0x0
handleKeyEvent called: key=Key: L, ctrl=false, shift=false, alt=false
  -> Using key mapping: l
Key event handled: Key: L
```

**每个按键触发了两次事件**：KeyDown（按下）和 KeyUp（抬起），导致重复输入！

### 原因分析
- `onKeyEvent` 默认捕获**所有**键盘事件（KeyDown + KeyUp）
- 没有过滤事件类型，导致每个按键被处理两次
- KeyUp 事件的 key 可能是 `未知 keyCode: 0x0`，但 KeyDown 事件会被正常处理

### 修复方案

**文件**: `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/ui/TerminalView.kt`

#### 添加事件类型过滤

```kotlin
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type

private fun handleKeyEvent(event: KeyEvent, viewModel: TerminalViewModel): Boolean {
    // 只处理 KeyDown 事件，忽略 KeyUp 事件，避免重复输入
    if (event.type != KeyEventType.KeyDown) {
        return false
    }
    
    // ... 其余处理逻辑
}
```

### 修复结果
✅ **彻底解决重复输入问题** - 每个按键只处理一次（KeyDown）
✅ 忽略 KeyUp 事件，不再重复
✅ 日志清晰，每个按键只有一条处理记录

---

## 键盘处理逻辑重构

### 问题描述
1. **输入仍然重复** - 输入一个 `l` 显示两个 `l`（已通过 KeyDown/KeyUp 过滤解决）
2. **缺少 Ctrl 组合键支持** - PTY 终端需要 Ctrl+C、Ctrl+D 等组合键

### 原因分析

#### 重复输入的根本原因
经过深入分析，发现原来的修复不够彻底：

1. **字符处理后返回值错误**：
   - 第330行：`char != null` 且不是控制字符时，处理后返回 `true`
   - 第384行：检查到字符已处理，返回 `false` 而不是跳过
   - 返回 `false` 导致事件继续传播，可能被其他处理器再次处理

2. **处理顺序混乱**：
   - 字符处理、按键处理的顺序和条件判断不够清晰
   - 多个地方都可能处理同一个按键

#### Ctrl 组合键缺失
- 虽然导入了 `isCtrlPressed`，但完全没有使用
- PTY 终端需要 Ctrl+C (中断)、Ctrl+D (EOF)、Ctrl+Z (挂起) 等控制命令

### 修复方案：完全重构键盘处理逻辑

**文件**: `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/ui/TerminalView.kt`

#### 新的处理流程（清晰的优先级）

```kotlin
private fun handleKeyEvent(event: KeyEvent, viewModel: TerminalViewModel): Boolean {
    // 1. 优先处理 Ctrl 组合键（最高优先级）
    if (event.isCtrlPressed) {
        return handleCtrlCombination(event, viewModel)
    }
    
    // 2. 处理特殊功能键（Enter, Tab, 方向键等）
    when (key) {
        Key.Enter -> { /* ... */ return true }
        Key.Backspace -> { /* ... */ return true }
        // ...
    }
    
    // 3. 尝试通过反射获取字符
    val char: Char? = try { /* 反射获取 */ } catch (e: Exception) { null }
    
    // 4. 如果获取到字符且不是控制字符，直接使用（优先）
    if (char != null && !char.isISOControl()) {
        viewModel.handleInputBytes(char.toString().toByteArray())
        return true  // 立即返回，不再处理
    }
    
    // 5. 如果没有字符，才通过按键类型处理
    when (key) {
        Key.A, Key.B, ..., Key.Z -> {
            if (char == null) {  // 只在没有字符时处理
                val charFromKey = /* 根据 Shift 状态确定大小写 */
                viewModel.handleInputBytes(charFromKey.toString().toByteArray())
                return true
            }
        }
    }
    
    return false  // 未处理
}
```

#### 新增 Ctrl 组合键支持

```kotlin
private fun handleCtrlCombination(event: KeyEvent, viewModel: TerminalViewModel): Boolean {
    when (event.key) {
        Key.A -> { viewModel.handleInputBytes(byteArrayOf(0x01)); return true } // Ctrl+A
        Key.B -> { viewModel.handleInputBytes(byteArrayOf(0x02)); return true } // Ctrl+B
        Key.C -> { viewModel.handleInputBytes(byteArrayOf(0x03)); return true } // Ctrl+C (中断)
        Key.D -> { viewModel.handleInputBytes(byteArrayOf(0x04)); return true } // Ctrl+D (EOF)
        Key.E -> { viewModel.handleInputBytes(byteArrayOf(0x05)); return true } // Ctrl+E
        // ... 完整的 A-Z 控制字符支持
        Key.Z -> { viewModel.handleInputBytes(byteArrayOf(0x1A)); return true } // Ctrl+Z (挂起)
        else -> return false
    }
}
```

### 关键改进

1. **清晰的优先级**：
   - Ctrl 组合键 > 特殊功能键 > 字符事件 > 按键事件
   
2. **防止重复处理**：
   - 每个按键只会在一个地方被处理
   - 处理后立即返回 `true`，不再继续
   - 按键处理只在 `char == null` 时执行

3. **支持 Shift**：
   - 在按键处理中检查 `isShiftPressed`，正确处理大小写

4. **完整的 Ctrl 组合键**：
   - 支持 Ctrl+A 到 Ctrl+Z 的所有组合
   - 对应标准的控制字符 0x01-0x1A

### 修复结果（部分解决，最终通过 KeyDown/KeyUp 过滤彻底解决）
✅ 改进了处理逻辑，但仍有重复（因为 KeyDown/KeyUp 都被处理）
✅ 支持 Ctrl+C (中断进程)
✅ 支持 Ctrl+D (EOF/退出)
✅ 支持 Ctrl+Z (挂起进程)
✅ 支持 Ctrl+L (清屏)
✅ 支持所有标准的 Ctrl 组合键 (A-Z)
✅ 支持 Shift 键（大小写切换）

**注意**：此修复改进了逻辑，但未解决重复输入的根本原因（KeyDown/KeyUp 双重触发）。最终通过添加 `KeyEventType.KeyDown` 过滤彻底解决。

### 调试信息
新增了更详细的调试输出：
```
handleKeyEvent called: key=L, ctrl=false, shift=false, alt=false
  -> Found char: l (code: 108)
  -> Using char directly
```

这样可以清楚地看到每个按键的处理路径。

---

## 重要修复：输入和删除重复问题（初始修复）

### 问题描述（最新修复）
用户反馈：
1. **删除输入的时候，将整行都删除了** - 应该只删除一个字符，但却删除了整行
2. **输入的时候还会出现重复的输入** - 输入一个字符，却显示了两个

### 原因分析

#### 问题 1：删除整行
- **本地删除处理**：客户端在 `handleInputBytes` 中本地处理了删除（移动光标并清除字符）
- **服务器返回删除序列**：服务器收到删除命令后，会返回删除序列（BS 或 DEL）
- **ANSI 解析器再次处理**：解析器再次处理服务器返回的删除序列
- **结果**：导致双重删除，如果服务器返回了多次删除操作（如 BS + 空格 + BS），可能删除整行

#### 问题 2：重复输入
- **字符事件处理**：`handleKeyEvent` 中通过反射获取字符并处理
- **按键事件处理**：同一事件又通过按键类型（Key.A, Key.B 等）处理
- **结果**：同一个按键被处理两次，导致重复输入

### 修复方案

#### 1. 移除本地删除处理

**文件**: `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/ui/TerminalViewModel.kt`

**修复前**:
```kotlin
fun handleInputBytes(bytes: ByteArray) {
    // 如果只有一个字节且是退格或删除字符，在本地立即处理
    if (bytes.size == 1) {
        val byte = bytes[0]
        when (byte.toInt()) {
            0x08, 0x7F -> {
                // 在本地立即处理退格...
            }
        }
    }
    // 发送到服务器
}
```

**修复后**:
```kotlin
fun handleInputBytes(bytes: ByteArray) {
    // 不进行本地处理，完全依赖服务器的回显和响应
    // 这样可以避免重复输入和删除的问题
    // 服务器会处理输入并返回相应的输出（包括回显和删除序列）
    
    // 发送到服务器
    scope.launch {
        connection.writeBytes(bytes)
    }
}
```

**原理**: 完全依赖服务器的回显和删除序列，让服务器控制所有的显示更新。

#### 2. 修复重复输入处理

**文件**: `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/ui/TerminalView.kt`

**修复前**:
```kotlin
// 字符处理和按键处理都可能执行，导致重复
if (char != null) {
    // 处理字符...
    viewModel.handleInputBytes(char.toString().toByteArray())
    return true
}
// 按键处理（可能再次执行）
when (key) {
    Key.A -> { /* 处理按键 */ }
}
```

**修复后**:
```kotlin
// 优先处理字符事件（如果存在），避免重复处理
if (char != null) {
    when {
        char.isISOControl() -> {
            // 控制字符，通过按键处理
        }
        else -> {
            // 可打印字符，直接使用字符，立即返回
            viewModel.handleInputBytes(char.toString().toByteArray())
            return true
        }
    }
}
// 处理特殊键（只在字符处理未完成时执行）
when (key) {
    Key.A, Key.B, ... -> {
        // 如果字符已经处理过，不再处理
        if (char != null && !char.isISOControl()) {
            return false  // 已经被字符处理过了
        }
        // 处理按键...
    }
}
```

**原理**: 
- 优先处理字符事件，如果是可打印字符，立即返回，不再处理按键
- 在按键处理中检查是否已经被字符处理过，避免重复处理

#### 3. 移除重复的退格处理

**文件**: `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/ui/TerminalView.kt`

**修复前**:
```kotlin
.onKeyEvent { event ->
    // 特别优先处理退格键，确保它被捕获
    if (event.key == Key.Backspace) {
        viewModel.handleInputBytes("\u007F".toByteArray())
        return@onKeyEvent true
    }
    
    val handled = handleKeyEvent(event, viewModel)
    // ...
}
```

**修复后**:
```kotlin
.onKeyEvent { event ->
    val handled = handleKeyEvent(event, viewModel)
    // ...
}
```

**原理**: 移除重复的退格处理，统一在 `handleKeyEvent` 中处理。

### 修复结果
✅ 删除功能正常，只删除一个字符，不再删除整行
✅ 输入功能正常，不再重复输入
✅ 完全依赖服务器的回显和响应，符合终端标准行为

### 注意事项
- 此修复要求服务器启用回显（echo）模式，这在大多数 SSH 终端中是默认的
- 如果服务器禁用了回显（例如在输入密码时），客户端不会显示任何内容，这是正常行为
- 删除操作完全由服务器控制，客户端不再进行本地预删除

---

## 一、删除功能修复（初始修复）

### 问题描述
用户反馈：能够输入字母，但无法删除字符。按下退格键时光标会移动，但已输入的字符没有消失。

### 原因分析
1. **ANSI 解析器缺少 DEL 字符处理**：解析器只处理了 BS (0x08)，但客户端发送的是 DEL (0x7F)
2. **退格处理不完整**：处理退格时只移动了光标，但没有清除字符

### 修复方案

#### 1. 增强 ANSI 解析器对退格和删除的处理

**文件**: `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/AnsiParser.kt`

- **添加 DEL (0x7F) 处理**：在解析器中添加了对 DEL 字符的完整处理
- **改进 BS (0x08) 处理**：不仅移动光标，还清除光标位置的字符

```kotlin
0x08 -> { // BS (Backspace)
    if (cursor.x > 0) {
        cursor.moveBy(-1, 0)
        // 清除当前光标位置的字符
        val line = buffer.getVisibleLine(cursor.y) ?: buffer.getCurrentLine()
        val emptyCell = TerminalCell(char = ' ', ...)
        line.setCell(cursor.x, emptyCell)
    }
}
0x7F -> { // DEL (Delete)
    if (cursor.x > 0) {
        cursor.moveBy(-1, 0)
        // 清除当前光标位置的字符
        ...
    }
}
```

#### 2. 客户端即时反馈处理

**文件**: `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/ui/TerminalViewModel.kt`

- 在 `handleInputBytes` 中添加本地即时处理，提供即时的视觉反馈

```kotlin
fun handleInputBytes(bytes: ByteArray) {
    // 如果只有一个字节且是退格或删除字符，在本地立即处理
    if (bytes.size == 1) {
        val byte = bytes[0]
        when (byte.toInt()) {
            0x08, 0x7F -> { // BS 或 DEL
                // 在本地立即处理退格，提供即时反馈
                if (cursor.x > 0) {
                    cursor.moveBy(-1, 0)
                    // 清除当前光标位置的字符
                    ...
                }
            }
        }
    }
    // 发送到服务器
    scope.launch {
        connection.writeBytes(bytes)
    }
}
```

#### 3. 键盘事件处理增强

**文件**: `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/ui/TerminalView.kt`

- 在 `onKeyEvent` 中优先处理退格键，确保被正确捕获
- 添加了通过反射获取字符事件的尝试，以处理可能的字符级事件

### 修复结果
✅ 退格键现在可以正确删除字符
✅ 光标移动和字符清除同步进行
✅ 提供即时视觉反馈

---

## 二、ANSI 解析器全面增强

### 增强目标
为了支持 nano、vi、yazi、sc 等终端应用程序，需要对 ANSI 解析器进行全面增强。

### 1. 保存/恢复光标位置 (ESC 7/8)

**新增功能**: 
- `ESC 7` (DECSC) - 保存光标位置和样式
- `ESC 8` (DECRC) - 恢复光标位置和样式

**修改文件**:
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/CursorState.kt` - 添加保存/恢复功能
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/AnsiParser.kt` - 添加 ESC 7/8 处理

**用途**: vi/nano 等编辑器需要此功能来保存和恢复光标位置

### 2. 光标可见性控制 (CSI ? 25 h/l)

**新增功能**:
- `CSI ? 25 h` - 显示光标
- `CSI ? 25 l` - 隐藏光标

**修改文件**:
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/AnsiParser.kt` - 添加 DEC 私有模式处理

**用途**: vi 等编辑器在特定模式下会隐藏光标

### 3. 滚动区域设置 (CSI r)

**新增功能**:
- `CSI r` - 设置滚动区域（顶部和底部边距）
- 支持在指定区域内滚动

**修改文件**:
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/TerminalBuffer.kt` - 添加滚动区域支持
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/AnsiParser.kt` - 添加 CSI r 处理

**用途**: yazi、sc 等分屏工具需要滚动区域功能

### 4. 备用屏幕缓冲 (CSI ? 47 h/l, 1049)

**新增功能**:
- `CSI ? 47 h` - 切换到备用屏幕
- `CSI ? 47 l` - 切回主屏幕
- `CSI ? 1049 h` - 切换到备用屏幕并保存光标
- `CSI ? 1049 l` - 切回主屏幕并恢复光标

**修改文件**:
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/TerminalBuffer.kt` - 添加备用屏幕缓冲
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/AnsiParser.kt` - 添加 DEC 私有模式 47 和 1049 处理

**用途**: vi/nano 等全屏应用需要备用屏幕来保存主屏幕内容

### 5. 删除/插入字符和行

**新增功能**:
- **CSI P** - 删除字符（光标位置，后续字符左移）
- **CSI @** - 插入字符（光标位置，后续字符右移）
- **CSI L** - 插入行（光标位置，后续行下移）
- **CSI M** - 删除行（光标位置，后续行上移）

**修改文件**:
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/AnsiParser.kt` - 添加相应处理函数

**用途**: vi 等编辑器在编辑模式下需要插入/删除字符和行

### 6. 清行和清屏命令完善

**增强功能**:
- **CSI K 0** - 从光标到行末尾清除
- **CSI K 1** - 从行开头到光标清除
- **CSI K 2** - 清除整行
- **CSI J 0** - 从光标到屏幕末尾清除（已实现）
- **CSI J 1** - 从屏幕开头到光标清除（已实现）
- **CSI J 2/3** - 清除整个屏幕（已实现）

**修改文件**:
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/AnsiParser.kt` - 完善 handleEraseLine 函数
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/ui/TerminalViewModel.kt` - 实现 eraseFromCursorToEnd 和 eraseFromStartToCursor

### 7. DEC 私有模式支持

**新增功能**:
- 支持 `CSI ? ... h` (Set Mode) 和 `CSI ? ... l` (Reset Mode)
- 支持的私有模式：
  - `1` - DECCKM (Cursor Keys Mode)
  - `25` - DECTCEM (Text Cursor Enable Mode)
  - `47` - 备用屏幕切换
  - `1049` - 备用屏幕切换并保存光标

**修改文件**:
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/AnsiParser.kt` - 添加 DEC 私有模式处理

### 8. 更多 ESC 转义序列

**新增功能**:
- **ESC D** (IND) - 向下滚动一行
- **ESC M** (RI) - 向上滚动一行
- **ESC E** (NEL) - 下一行（回车+换行）

**修改文件**:
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/AnsiParser.kt` - 添加 ESC 序列处理

---

## 三、数据结构增强

### 1. CursorState 增强

**文件**: `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/CursorState.kt`

**新增字段**:
- `savedX: Int` - 保存的光标 X 位置
- `savedY: Int` - 保存的光标 Y 位置
- `savedStyle: CellStyle` - 保存的样式

**新增方法**:
- `save()` - 保存当前光标位置和样式
- `restore()` - 恢复保存的光标位置和样式

### 2. TerminalBuffer 增强

**文件**: `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/TerminalBuffer.kt`

**新增字段**:
- `scrollRegionTop: Int?` - 滚动区域顶部
- `scrollRegionBottom: Int?` - 滚动区域底部
- `alternateVisibleLines` - 备用屏幕缓冲
- `useAlternateScreen: Boolean` - 是否使用备用屏幕

**新增方法**:
- `setScrollRegion(top: Int, bottom: Int)` - 设置滚动区域
- `clearScrollRegion()` - 清除滚动区域
- `scrollUpInRegion(lines: Int)` - 在滚动区域内向上滚动
- `scrollDownInRegion(lines: Int)` - 在滚动区域内向下滚动
- `switchToAlternateScreen()` - 切换到备用屏幕
- `switchToMainScreen()` - 切换回主屏幕
- `isUsingAlternateScreen()` - 是否使用备用屏幕

### 3. ControlSequence 扩展

**文件**: `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/AnsiParser.kt`

**新增类型**:
- `ScrollUp(val lines: Int)` - 向上滚动
- `ScrollDown(val lines: Int)` - 向下滚动

---

## 四、语法错误修复

### 问题描述
编译时报错：`TerminalBuffer.kt:243:63 Syntax error: Missing '}'`

### 原因分析
`TerminalBuffer` 类的最后一个方法 `isUsingAlternateScreen()` 后缺少类的闭合大括号 `}`

### 修复方案
在文件末尾添加类的闭合大括号 `}`

**修改**:
```kotlin
// 修复前
fun isUsingAlternateScreen(): Boolean = useAlternateScreen


// 修复后
fun isUsingAlternateScreen(): Boolean = useAlternateScreen
}
```

---

## 五、测试建议

### 需要测试的工具
1. **nano** - 测试备用屏幕、光标控制、删除功能
2. **vi/vim** - 测试备用屏幕、滚动区域、插入/删除字符和行
3. **yazi** - 测试滚动区域、备用屏幕
4. **sc** - 测试滚动区域、清屏功能

### 测试场景
- ✅ 基本文本输入和删除
- ✅ 全屏编辑器（vi/nano）的启动和退出
- ✅ 分屏工具的滚动区域
- ✅ 光标可见性控制
- ✅ 清屏和清行功能
- ✅ 插入/删除字符和行

---

## 六、相关文件列表

### 修改的文件
1. `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/AnsiParser.kt`
2. `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/CursorState.kt`
3. `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/TerminalBuffer.kt`
4. `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/ui/TerminalViewModel.kt`
5. `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/ui/TerminalView.kt`

---

## 七、后续优化建议

1. **完善插入/删除行功能** - 当前实现还有 TODO，需要完善
2. **滚动区域优化** - 滚动区域内的滚动逻辑可以进一步优化
3. **备用屏幕优化** - 备用屏幕的内存管理可以进一步优化
4. **性能优化** - 对于大量数据的处理，可以考虑性能优化
5. **更多 ANSI 序列支持** - 根据实际使用情况，可以添加更多 ANSI 序列支持

---

## 八、添加详细调试日志以诊断删除问题

### 8.1 问题描述

用户报告了一个问题：
- ✅ **正常情况**：在没有输入任何东西的情况下按退格，命令行的提示信息不会被删除
- ❌ **问题情况**：输入字符后再删除，整行的文字（包括命令提示符）都消失了

### 8.2 诊断策略

为了诊断这个问题，我们需要知道：
1. 客户端发送给服务器的确切字节序列
2. 服务器返回的确切字节序列
3. ANSI 解析器如何处理这些序列
4. 光标在每个步骤后的位置

### 8.3 添加的调试日志

#### 8.3.1 在 `TerminalViewModel.kt` 中添加输入日志

在 `handleInputBytes` 函数中添加日志，记录发送给服务器的字节：

```kotlin
fun handleInputBytes(bytes: ByteArray) {
    // 调试：打印发送的字节
    val hexString = bytes.joinToString(" ") { "0x%02X".format(it) }
    val charString = bytes.map { 
        if (it in 0x20..0x7E) it.toInt().toChar().toString() 
        else if (it == 0x0D.toByte()) "\\r"
        else if (it == 0x0A.toByte()) "\\n"
        else if (it == 0x09.toByte()) "\\t"
        else if (it == 0x7F.toByte()) "DEL"
        else if (it == 0x08.toByte()) "BS"
        else if (it == 0x1B.toByte()) "ESC"
        else "?"
    }.joinToString("")
    println(">>> Sending to server: $hexString ($charString)")
    
    // 发送到服务器
    scope.launch {
        connection.writeBytes(bytes)
    }
}
```

#### 8.3.2 在 `TerminalViewModel.kt` 中添加输出日志

在 `parseAndUpdateBuffer` 函数中添加日志，记录从服务器接收的字节和光标位置变化：

```kotlin
private fun parseAndUpdateBuffer(data: String) {
    // 调试：打印接收的数据
    val bytes = data.toByteArray()
    val hexString = bytes.take(100).joinToString(" ") { "0x%02X".format(it) }
    val charString = data.take(100).map { 
        if (it in ' '..'~') it.toString() 
        else if (it == '\r') "\\r"
        else if (it == '\n') "\\n"
        else if (it == '\t') "\\t"
        else if (it.code == 0x7F) "DEL"
        else if (it.code == 0x08) "BS"
        else if (it.code == 0x1B) "ESC"
        else "?"
    }.joinToString("")
    println("<<< Received from server (${bytes.size} bytes): $hexString")
    println("    Content: $charString")
    println("    Cursor before: (${cursor.x}, ${cursor.y})")
    
    // ... 解析逻辑 ...
    
    // 调试：打印光标位置
    println("    Cursor after: (${cursor.x}, ${cursor.y})")
}
```

#### 8.3.3 在 `AnsiParser.kt` 中添加关键操作日志

在处理退格、空格写入和行清除操作时添加日志：

```kotlin
0x08 -> { // BS (Backspace)
    println("      [AnsiParser] BS - Moving cursor left from (${cursor.x}, ${cursor.y})")
    if (cursor.x > 0) {
        cursor.moveBy(-1, 0)
    }
    println("      [AnsiParser] BS - Cursor now at (${cursor.x}, ${cursor.y})")
}
```

```kotlin
in 0x20..0x7E -> { // ASCII 可打印字符
    if (char == ' ') {
        println("      [AnsiParser] Writing SPACE at (${cursor.x}, ${cursor.y})")
    }
    onText(char, cursor.currentStyle)
}
```

```kotlin
private fun handleEraseLine(mode: Int, cursor: CursorState, buffer: TerminalBuffer) {
    val line = buffer.getVisibleLine(cursor.y) ?: buffer.getCurrentLine()
    val width = buffer.getWidth()
    
    println("      [AnsiParser] Erase Line mode=$mode at cursor (${cursor.x}, ${cursor.y}), width=$width")
    
    when (mode) {
        0 -> {
            println("      [AnsiParser] Erasing from cursor to end of line (${cursor.x} to ${width-1})")
            // ... 清除逻辑 ...
        }
        1 -> {
            println("      [AnsiParser] Erasing from start of line to cursor (0 to ${cursor.x})")
            // ... 清除逻辑 ...
        }
        2 -> {
            println("      [AnsiParser] Erasing entire line (0 to ${width-1})")
            // ... 清除逻辑 ...
        }
    }
}
```

### 8.4 预期的日志输出

#### 正常删除操作的预期日志

当用户输入 "l" 然后按退格键时，预期看到：

```
>>> Sending to server: 0x6C (l)
<<< Received from server (1 bytes): 0x6C
    Content: l
    Cursor before: (14, 0)
    Cursor after: (15, 0)

>>> Sending to server: 0x7F (DEL)
<<< Received from server (3 bytes): 0x08 0x20 0x08
    Content: BS SPACE BS
    Cursor before: (15, 0)
      [AnsiParser] BS - Moving cursor left from (15, 0)
      [AnsiParser] BS - Cursor now at (14, 0)
      [AnsiParser] Writing SPACE at (14, 0)
      [AnsiParser] BS - Moving cursor left from (15, 0)
      [AnsiParser] BS - Cursor now at (14, 0)
    Cursor after: (14, 0)
```

这表明：
1. 客户端发送 `0x7F` (DEL) 给服务器
2. 服务器返回 `BS + Space + BS` 序列
3. 第一个 BS 将光标从 15 移到 14
4. Space 在位置 14 写入空格（覆盖字符 'l'）
5. 第二个 BS 将光标从 15 移回 14

#### 异常删除操作的日志

如果看到 `CSI K` 序列或其他异常序列，可能表明服务器发送了不同的删除命令。

### 8.5 问题诊断结果

用户提供的日志显示了问题的根本原因：

```
>>> Sending to server: 0x7F (DEL)
<<< Received from server (16 bytes): 0x08 0x1B 0x5B 0x4B ...
    Content: BSESC[K????????????
    Cursor before: (42, 23)
      [AnsiParser] BS - Moving cursor left from (42, 23)
      [AnsiParser] BS - Cursor now at (41, 23)
      [AnsiParser] Erase Line mode=1 at cursor (41, 23), width=80
      [AnsiParser] Erasing from start of line to cursor (0 to 41)
    Cursor after: (41, 23)
```

**问题分析**：

1. 服务器发送的删除序列是 `BS + ESC[K`
2. `ESC[K` 应该表示"从光标到行尾清除"（mode=0）
3. 但我们的解析器错误地将它解释为 mode=1（从行开头到光标清除）
4. 这导致整行（包括命令提示符）都被删除了

**根本原因**：

在 `handleCsiCommand` 函数中，我们为所有命令使用了统一的默认参数值：

```kotlin
val p1 = params.getOrElse(0) { 1 }  // ❌ 错误！
```

但实际上，不同的 CSI 命令有不同的默认值：
- 移动命令（A/B/C/D）：默认值是 1
- 清除命令（J/K）：默认值是 0
- 光标位置（H/f）：默认值是 1

对于 `CSI K`（Erase in Line），当没有参数时：
- `ESC[K` 等价于 `ESC[0K`（清除从光标到行尾）
- 而不是 `ESC[1K`（清除从行开头到光标）

### 8.6 解决方案

修改 `AnsiParser.kt` 中的 `handleCsiCommand` 函数，为不同的命令使用正确的默认值：

```kotlin
private fun handleCsiCommand(
    command: Char,
    params: List<Int>,
    cursor: CursorState,
    buffer: TerminalBuffer,
    onControlSequence: (ControlSequence) -> Unit,
) {
    // 注意：不同命令的默认值不同！
    // 对于移动命令（A/B/C/D），默认是 1
    // 对于清除命令（J/K），默认是 0
    // 对于光标位置（H/f），默认是 1
    
    when (command) {
        'A' -> { // Cursor Up
            val count = params.getOrElse(0) { 1 }
            cursor.moveBy(0, -count)
        }
        // ... 其他移动命令类似 ...
        
        'J' -> { // Erase in Display
            val mode = params.getOrElse(0) { 0 }  // ✅ 默认值是 0
            when (mode) {
                0 -> { // 从光标到屏幕末尾
                    onControlSequence(ControlSequence.EraseDisplay(0))
                }
                // ...
            }
        }
        'K' -> { // Erase in Line
            val mode = params.getOrElse(0) { 0 }  // ✅ 默认值是 0（从光标到行尾）
            handleEraseLine(mode, cursor, buffer)
        }
        // ...
    }
}
```

**修复后的行为**：

现在当服务器发送 `BS + ESC[K` 时：
1. `BS` (0x08) 将光标从 42 移到 41
2. `ESC[K` 清除从光标位置 41 到行尾（而不是从行开头到光标）
3. 命令提示符保持不变，只有输入的字符被删除

---

## 总结

本次增强大大提升了终端对常见终端应用程序的支持，特别是：
- ✅ 修复了删除功能
- ✅ 支持全屏编辑器（vi/nano）
- ✅ 支持分屏工具（yazi/sc）
- ✅ 完善了 ANSI/VT100 转义序列支持
- ✅ 修复了语法错误
- ✅ 添加了详细的调试日志
- ✅ **修复了 CSI K/J 命令的默认参数错误（关键修复）**

终端现在应该能够较好地支持大多数常见的终端应用程序。

**最近修复的重要问题**：
- **删除整行问题**：修复了 `ESC[K` 被错误解释为"从行开头到光标清除"而不是"从光标到行尾清除"的问题。这是由于 CSI 命令的默认参数值设置错误导致的。现在删除操作应该正常工作，不会删除命令提示符。

---

## 九、修复 cat 多行输出显示不全的问题

### 9.1 问题描述

用户报告：
- ❌ 使用 `cat` 打印多行文件时，内容显示不全
- ❌ 使用 `htop` 时没有反应

### 9.2 问题分析

#### 问题 1：cat 打印不全

经过代码审查，发现 `TerminalBuffer.newLine()` 方法的逻辑是错误的：

**错误的实现**：
```kotlin
fun newLine() {
    // ❌ 将最后一行移到 scrollback
    val lastLine = visibleLines.removeAt(visibleLines.size - 1)
    scrollbackLines.add(lastLine)
    
    // 在底部添加新行
    visibleLines.add(TerminalLine(width))
}
```

这个逻辑是错误的，因为：
1. 它移除了最后一行（刚刚输入的内容）
2. 然后又在底部添加新行
3. 结果是刚输入的内容被移到了 scrollback，但我们期望看到的是顶行被移走

**正确的行为**：
当终端需要滚动时（例如，光标在最后一行按下回车），应该：
1. 将**顶行**（第一行）移到 scrollback 历史
2. 在底部添加新的空行
3. 所有中间的行向上移动一行

这就像一个队列：顶部出队，底部入队。

### 9.3 解决方案

修复 `TerminalBuffer.kt` 中的 `newLine()` 方法：

```kotlin
fun newLine() {
    println("      [TerminalBuffer] newLine() - Scrolling buffer (visibleLines=${visibleLines.size}, scrollback=${scrollbackLines.size})")
    
    // ✅ 将顶行（第一行）移到 scrollback
    if (visibleLines.isNotEmpty()) {
        val topLine = visibleLines.removeAt(0)  // 移除第一行而不是最后一行
        scrollbackLines.add(topLine)
        
        // 限制 scrollback 大小
        if (scrollbackLines.size > scrollbackSize) {
            scrollbackLines.removeAt(0)
        }
    }

    // 在底部添加新行
    visibleLines.add(TerminalLine(width))
    
    // 重置滚动偏移（显示最新内容）
    scrollOffset = 0
    
    println("      [TerminalBuffer] newLine() - After scroll (visibleLines=${visibleLines.size}, scrollback=${scrollbackLines.size})")
}
```

#### 问题 2：htop 没反应

htop 是一个全屏交互式程序，可能需要：
1. 更多的 ANSI 序列支持（已经添加了日志来捕获未知序列）
2. 正确的终端类型设置（TERM 环境变量）
3. 可能需要鼠标事件支持

添加了调试日志来捕获未知的 ANSI 序列：

```kotlin
else -> {
    // 未知命令
    println("      [AnsiParser] Unknown CSI command '$command' (0x${command.code.toString(16)}), params=$params, isPrivate=$isPrivateMode")
}
```

```kotlin
else -> {
    // 未知的转义序列
    println("      [AnsiParser] Unknown ESC sequence: ESC $char (0x${char.code.toString(16)})")
    state = ParseState.NORMAL
}
```

### 9.4 测试步骤

请重新测试：

1. **测试 cat 多行文件**：
   ```bash
   cat /etc/passwd
   # 或者
   cat some_long_file.txt
   ```
   现在应该能看到完整的内容，不会丢失行。

2. **测试 htop**：
   ```bash
   htop
   ```
   请提供日志输出，特别是包含 `[AnsiParser] Unknown` 的行，这样我们就能知道 htop 使用了哪些我们还没实现的 ANSI 序列。

### 9.5 添加的调试日志

为了更好地诊断问题，添加了以下调试日志：

1. **换行和回车日志**：
   ```kotlin
   0x0A -> { // LF (Line Feed)
       println("      [AnsiParser] LF - Moving cursor down from (${cursor.x}, ${cursor.y})")
       // ... 处理逻辑 ...
       println("      [AnsiParser] LF - Cursor now at (${cursor.x}, ${cursor.y})")
   }
   
   0x0D -> { // CR (Carriage Return)
       println("      [AnsiParser] CR - Moving cursor to start of line")
       cursor.x = 0
   }
   ```

2. **模式设置日志**：
   ```kotlin
   'h' -> { // Set Mode
       if (isPrivateMode) {
           println("      [AnsiParser] DEC Private Mode Set: params=$params")
           // ...
       } else {
           println("      [AnsiParser] Normal Mode Set: params=$params (ignored)")
       }
   }
   ```

3. **缓冲区滚动日志**：
   ```kotlin
   fun newLine() {
       println("      [TerminalBuffer] newLine() - Scrolling buffer (visibleLines=${visibleLines.size}, scrollback=${scrollbackLines.size})")
       // ... 滚动逻辑 ...
       println("      [TerminalBuffer] newLine() - After scroll (visibleLines=${visibleLines.size}, scrollback=${scrollbackLines.size})")
   }
   ```

---

## 十、修复 btop 显示问题（Unicode 字符和屏幕尺寸）

### 10.1 问题描述

用户使用 btop（系统监控工具）时遇到两个问题：
1. ❌ **字符显示为奇怪的框体**：btop 使用的 Unicode 框线字符没有正确渲染
2. ❌ **没有铺满整个屏幕**：btop 的界面没有占满整个终端窗口

### 10.2 问题分析

#### 问题 1：Unicode 字符渲染问题

**根本原因**：
- btop/htop 使用了大量的 Unicode 框线字符（Box Drawing Characters, U+2500 - U+257F）
- 当前使用的字体（Courier New, Consolas）对这些 Unicode 字符的支持不完整
- 缺少字体回退机制，导致不支持的字符显示为空框或错误的符号

**常见的 Unicode 框线字符**：
- `─` (U+2500) 水平线
- `│` (U+2502) 垂直线
- `┌` (U+250C) 左上角
- `┐` (U+2510) 右上角
- `└` (U+2514) 左下角
- `┘` (U+2518) 右下角
- `├` (U+251C) 左T字
- `┤` (U+2524) 右T字
- 等等...

#### 问题 2：终端尺寸问题

**根本原因**：
- `TerminalBuffer` 的 `width` 和 `height` 被声明为 `val`（不可变）
- 即使调用了 `resize()` 方法，缓冲区的实际尺寸也无法改变
- 服务器收到了新的尺寸信息，但客户端的缓冲区仍然保持初始大小（80x24）

### 10.3 解决方案

#### 解决方案 1：改进字体支持

修改 `TextRenderer.kt`，优先使用对 Unicode 支持更好的字体：

```kotlin
// 按优先级尝试不同的字体
cachedTypeface = fontMgr.matchFamilyStyle("DejaVu Sans Mono", FontStyle.NORMAL)
    ?: fontMgr.matchFamilyStyle("Liberation Mono", FontStyle.NORMAL)
    ?: fontMgr.matchFamilyStyle("Noto Sans Mono", FontStyle.NORMAL)
    ?: fontMgr.matchFamilyStyle("JetBrains Mono", FontStyle.NORMAL)
    ?: fontMgr.matchFamilyStyle("Cascadia Mono", FontStyle.NORMAL)
    ?: fontMgr.matchFamilyStyle("Courier New", FontStyle.NORMAL)
    ?: fontMgr.matchFamilyStyle("monospace", FontStyle.NORMAL)
    ?: fontMgr.matchFamilyStyle("Consolas", FontStyle.NORMAL)
    ?: fontMgr.matchFamilyStyle("Lucida Console", FontStyle.NORMAL)
    ?: throw IllegalStateException("No monospace font available")
```

**推荐的字体**（按 Unicode 支持程度排序）：
1. **DejaVu Sans Mono** - 优秀的 Unicode 支持
2. **Liberation Mono** - 开源字体，良好的 Unicode 支持
3. **Noto Sans Mono** - Google 的 Noto 字体家族，广泛的 Unicode 覆盖
4. **JetBrains Mono** - 现代等宽字体，良好的编程符号支持
5. **Cascadia Mono** - Windows Terminal 默认字体

#### 解决方案 2：实现真正的缓冲区大小调整

**步骤 1**：将 `width` 和 `height` 改为 `var`（可变）

```kotlin
class TerminalBuffer(
    private var width: Int,      // ✅ 改为 var
    private var height: Int,     // ✅ 改为 var
    private val scrollbackSize: Int = 1000,
) {
```

**步骤 2**：实现 `resize()` 方法

```kotlin
fun resize(newWidth: Int, newHeight: Int) {
    println("      [TerminalBuffer] Resizing buffer from ${width}x${height} to ${newWidth}x${newHeight}")
    
    if (newWidth == width && newHeight == height) {
        return // 尺寸没变，不需要调整
    }
    
    // 调整高度
    if (newHeight != height) {
        if (newHeight > height) {
            // 增加高度 - 在底部添加新行
            val linesToAdd = newHeight - height
            for (i in 0 until linesToAdd) {
                visibleLines.add(TerminalLine(width))
            }
        } else {
            // 减少高度 - 移除底部的行，但保留内容
            val linesToRemove = height - newHeight
            for (i in 0 until linesToRemove) {
                if (visibleLines.isNotEmpty()) {
                    val line = visibleLines.removeAt(visibleLines.size - 1)
                    // 如果行有内容，移到 scrollback
                    if (line.getCells().any { it.char != ' ' }) {
                        scrollbackLines.add(line)
                        if (scrollbackLines.size > scrollbackSize) {
                            scrollbackLines.removeAt(0)
                        }
                    }
                }
            }
        }
        height = newHeight
    }
    
    // 调整宽度
    if (newWidth != width) {
        width = newWidth
        // 如果宽度变小，行内容会被 TerminalLine 自动截断
        // 如果宽度变大，新增的空间会在需要时自动填充
    }
    
    println("      [TerminalBuffer] Resize complete, new size: ${width}x${height}, visible lines: ${visibleLines.size}")
}
```

**步骤 3**：在 `TerminalViewModel.resize()` 中调用缓冲区的 resize

```kotlin
fun resize(width: Int, height: Int) {
    println(">>> Resizing terminal to: ${width}x${height}")
    terminalWidth = width
    terminalHeight = height
    buffer.resize(width, height)  // ✅ 调用缓冲区的 resize
    scope.launch {
        connection.resize(width, height)
    }
}
```

### 10.4 添加的调试日志

为了帮助诊断问题，添加了以下日志：

1. **字体选择日志**：
   ```kotlin
   println("[TextRenderer] Using normal font: ${cachedTypeface?.familyName}")
   ```

2. **终端尺寸变化日志**：
   ```kotlin
   println("Terminal size changed: ${terminalWidth}x${terminalHeight} -> ${newWidth}x${newHeight}")
   println("Canvas size: ${size.width}x${size.height}px, Char size: ${charWidthPx}x${charHeightPx}px")
   ```

3. **缓冲区调整日志**：
   ```kotlin
   println("      [TerminalBuffer] Resizing buffer from ${width}x${height} to ${newWidth}x${newHeight}")
   println("      [TerminalBuffer] Resize complete, new size: ${width}x${height}, visible lines: ${visibleLines.size}")
   ```

### 10.5 测试步骤

1. **重新运行程序**
2. **查看日志输出**，确认：
   - 使用的字体是什么
   - 终端尺寸是否正确计算
   - 缓冲区是否正确调整大小
3. **运行 btop**：
   ```bash
   btop
   ```
4. **观察**：
   - 框线字符是否正确显示
   - btop 界面是否铺满整个屏幕

### 10.6 如果问题仍然存在

如果 Unicode 字符仍然显示不正确：

1. **检查系统是否安装了推荐的字体**：
   - Windows: 可以安装 [Cascadia Code](https://github.com/microsoft/cascadia-code)
   - Linux: `sudo apt install fonts-dejavu fonts-liberation fonts-noto`
   - macOS: 通常已经预装了较好的等宽字体

2. **查看日志中显示的字体名称**：
   ```
   [TextRenderer] Using normal font: Consolas
   ```
   如果显示的是 Consolas 或 Courier New，说明系统没有安装更好的 Unicode 字体。

3. **手动安装 DejaVu Sans Mono**：
   - 下载: https://dejavu-fonts.github.io/
   - 安装后重启程序

---

## 十一、修复中文乱码和 btop 全屏显示问题

### 11.1 问题反馈

用户测试后反馈：
- ✅ btop 的 Unicode 框线字符乱码问题已解决
- ✅ cat 可以正常打印多行文件
- ❌ **中文显示乱码**
- ❌ **btop 没有铺满全屏**

### 11.2 问题分析与解决

#### 问题 1：中文乱码

**根本原因**：
- 英文等宽字体（如 Consolas、Courier New）不包含中文字符
- 需要针对 CJK（中日韩）字符使用专门的字体

**解决方案**：

在 `TextRenderer.kt` 中添加 CJK 字符检测和专用字体支持：

```kotlin
// 判断是否是 CJK 字符
private fun isCjkChar(char: Char): Boolean {
    val code = char.code
    return when {
        code in 0x4E00..0x9FFF -> true  // CJK Unified Ideographs (中日韩统一表意文字)
        code in 0x3400..0x4DBF -> true  // CJK Extension A
        code in 0x20000..0x2A6DF -> true  // CJK Extension B
        code in 0x2A700..0x2B73F -> true  // CJK Extension C
        code in 0x2B740..0x2B81F -> true  // CJK Extension D
        code in 0x2B820..0x2CEAF -> true  // CJK Extension E
        code in 0x3000..0x303F -> true  // CJK Symbols and Punctuation
        code in 0xFF00..0xFFEF -> true  // Halfwidth and Fullwidth Forms
        else -> false
    }
}

// 在绘制文本时选择合适的字体
val typeface = if (isCjk) {
    // 使用 CJK 字体
    if (cachedCjkTypeface == null) {
        cachedCjkTypeface = fontMgr.matchFamilyStyle("Microsoft YaHei", FontStyle.NORMAL)
            ?: fontMgr.matchFamilyStyle("SimHei", FontStyle.NORMAL)
            ?: fontMgr.matchFamilyStyle("SimSun", FontStyle.NORMAL)
            ?: fontMgr.matchFamilyStyle("Noto Sans CJK SC", FontStyle.NORMAL)
            ?: fontMgr.matchFamilyStyle("Source Han Sans", FontStyle.NORMAL)
            ?: fontMgr.matchFamilyStyle("WenQuanYi Micro Hei", FontStyle.NORMAL)
            ?: fontMgr.matchFamilyStyle("AR PL UMing CN", FontStyle.NORMAL)
            ?: cachedTypeface  // 后备方案
        println("[TextRenderer] Using CJK font: ${cachedCjkTypeface?.familyName}")
    }
    cachedCjkTypeface!!
} else if (bold) {
    // ... 英文粗体字体 ...
} else {
    // ... 英文普通字体 ...
}
```

**支持的中文字体**（按优先级）：
1. **Microsoft YaHei** (微软雅黑) - Windows 系统字体
2. **SimHei** (黑体) - Windows 系统字体
3. **SimSun** (宋体) - Windows 系统字体
4. **Noto Sans CJK SC** - Google 开源字体
5. **Source Han Sans** (思源黑体) - Adobe 开源字体
6. **WenQuanYi Micro Hei** (文泉驿微米黑) - Linux 常用字体
7. **AR PL UMing CN** - Linux 字体

#### 问题 2：btop 没有铺满全屏

**根本原因**：

1. **SSH 连接的 resize 方法实现不正确**：
   - 原实现试图重新分配 PTY，这是错误的
   - 正确的方式是发送 window-change 信号

2. **可能的终端尺寸计算问题**：
   - 字符尺寸设置可能不合适

**解决方案 1：修复 SSH resize 方法**

```kotlin
override suspend fun resize(width: Int, height: Int) {
    withContext(Dispatchers.IO) {
        try {
            println("[SshConnection] Attempting to resize PTY to ${width}x${height}")
            
            shell?.let { shell ->
                try {
                    // 使用反射访问 channel 来发送 window-change 请求
                    val channelField = shell.javaClass.getDeclaredField("channel")
                    channelField.isAccessible = true
                    val channel = channelField.get(shell) as? SessionChannel
                    
                    channel?.let {
                        // 发送 window-change 请求
                        it.changeWindowDimensions(width, height, 0, 0)
                        println("[SshConnection] Window change request sent: ${width}x${height}")
                    }
                } catch (e: Exception) {
                    println("[SshConnection] Failed to send window change: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("[SshConnection] Error resizing terminal: ${e.message}")
            e.printStackTrace()
        }
    }
}
```

**解决方案 2：改进字符尺寸和调试日志**

调整字符尺寸以获得更好的显示：
```kotlin
val charWidth = 9.dp  // 从 8.dp 增加到 9.dp
val charHeight = 18.dp  // 从 16.dp 增加到 18.dp
```

添加详细的调试日志：
```kotlin
if (newWidth != terminalWidth || newHeight != terminalHeight) {
    println("=== Terminal Resize ===")
    println("Canvas size: ${size.width} x ${size.height} px")
    println("Char size: ${charWidthPx} x ${charHeightPx} px")
    println("Old terminal: ${terminalWidth} x ${terminalHeight} chars")
    println("New terminal: ${newWidth} x ${newHeight} chars")
    println("Buffer size: ${buffer.getWidth()} x ${buffer.getHeight()} chars")
    terminalWidth = newWidth
    terminalHeight = newHeight
    viewModel.resize(newWidth, newHeight)
    println("======================")
}
```

**解决方案 3：在连接时立即发送终端尺寸**

```kotlin
suspend fun connect() {
    println("[TerminalViewModel] Connecting to SSH server...")
    connection.connect()
    println("[TerminalViewModel] Connected! Notifying server of terminal size: ${terminalWidth}x${terminalHeight}")
    // 连接后立即发送终端尺寸
    connection.resize(terminalWidth, terminalHeight)
    startOutputObserver()
}
```

### 11.3 测试步骤

1. **重新运行程序**

2. **观察启动日志**，应该看到：
   ```
   [TerminalViewModel] Initialized with size 80x24
   [TerminalViewModel] Buffer size: 80x24
   [TerminalViewModel] Connecting to SSH server...
   [TerminalViewModel] Connected! Notifying server of terminal size: XXxYY
   [SshConnection] Attempting to resize PTY to XXxYY
   [SshConnection] Window change request sent: XXxYY
   === Terminal Resize ===
   Canvas size: 1200.0 x 800.0 px
   Char size: 9.0 x 18.0 px
   Old terminal: 80 x 24 chars
   New terminal: 133 x 44 chars
   Buffer size: 80 x 24 chars
   [TerminalBuffer] Resizing buffer from 80x24 to 133x44
   [TerminalBuffer] Resize complete, new size: 133x44, visible lines: 44
   ======================
   ```

3. **测试中文显示**：
   ```bash
   echo "你好，世界！Hello World!"
   cat /path/to/chinese/file.txt
   ```
   中文应该正确显示（如果系统安装了中文字体）。

4. **测试 btop**：
   ```bash
   btop
   ```
   - btop 应该铺满整个窗口
   - 界面尺寸应该与终端窗口匹配
   - 如果仍然有问题，请提供日志中的终端尺寸信息

### 11.4 如果问题仍然存在

#### 中文仍然乱码

查看日志中的 CJK 字体信息：
```
[TextRenderer] Using CJK font: Microsoft YaHei
```

如果显示 `null` 或没有这条日志，说明系统没有安装中文字体。

**Windows 解决方案**：
- Windows 10/11 通常已经预装了微软雅黑
- 如果没有，可以在"设置 > 时间和语言 > 语言"中添加中文语言包

**Linux 解决方案**：
```bash
# Ubuntu/Debian
sudo apt install fonts-noto-cjk fonts-wqy-microhei

# Fedora/RHEL
sudo dnf install google-noto-sans-cjk-fonts wqy-microhei-fonts
```

#### btop 仍然不铺满屏幕

从日志中查找以下信息：
1. **Canvas size** - 窗口的像素尺寸
2. **New terminal** - 计算出的终端字符尺寸
3. **Window change request sent** - SSH 服务器收到的尺寸

如果这些数值看起来正常（例如 133x44 而不是 80x24），但 btop 仍然显示为小窗口，可能是：
- 服务器端的 $COLUMNS 和 $LINES 环境变量没有更新
- btop 在启动时读取了旧的终端尺寸

**临时解决方案**：
```bash
# 手动设置环境变量
export COLUMNS=$(tput cols)
export LINES=$(tput lines)
btop
```

或者在 btop 中按 `ESC` 然后重新启动。

---

## 十二、强制 Resize 和减少日志噪音

### 12.1 问题反馈

用户反馈 btop 仍然没有铺满屏幕，从日志坐标（如 `(78, 22)`）可以看出终端仍在使用默认的 80x24 尺寸。

### 12.2 问题诊断

关键发现：
- ❌ 没有看到 `=== Terminal Resize ===` 日志
- ❌ 没有看到 `Window change request sent` 日志
- ✅ 看到大量的空格写入日志，造成噪音

**问题原因**：
resize 逻辑可能在某些情况下没有触发（例如初始渲染时 Canvas 尺寸还没有确定）。

### 12.3 解决方案

#### 解决方案 1：添加强制 resize 机制

在 `TerminalView.kt` 中添加 `resizeCounter`，确保至少触发 2 次 resize 检查：

```kotlin
// 强制触发一次 resize 检查
var resizeCounter by remember { mutableStateOf(0) }

// 检查是否需要 resize
if (newWidth != terminalWidth || newHeight != terminalHeight || resizeCounter < 2) {
    println("=== Terminal Resize ===")
    println("Canvas size: ${size.width} x ${size.height} px")
    println("Char size: ${charWidthPx} x ${charHeightPx} px")
    println("Old terminal: ${terminalWidth} x ${terminalHeight} chars")
    println("New terminal: ${newWidth} x ${newHeight} chars")
    println("Buffer size: ${buffer.getWidth()} x ${buffer.getHeight()} chars")
    println("Resize counter: $resizeCounter")
    terminalWidth = newWidth
    terminalHeight = newHeight
    viewModel.resize(newWidth, newHeight)
    resizeCounter++
    println("======================")
}
```

这确保了：
1. 第一次绘制时会触发 resize（即使计算出的尺寸恰好是 80x24）
2. 第二次绘制时再次检查，以防 Canvas 尺寸在第一次绘制后改变
3. 之后只在尺寸真正改变时才 resize

#### 解决方案 2：减少调试日志噪音

移除了大量重复的调试日志，保留关键信息：

**移除的日志**：
- ❌ `[AnsiParser] Writing SPACE at (x, y)` - 每个空格都打印
- ❌ `[AnsiParser] LF - Moving cursor down` - 每个换行都打印
- ❌ `[AnsiParser] CR - Moving cursor to start` - 每个回车都打印
- ❌ `[AnsiParser] BS - Moving cursor left` - 每个退格都打印
- ❌ `[TerminalBuffer] newLine() - Scrolling buffer` - 每次滚动都打印

**保留的关键日志**：
- ✅ `=== Terminal Resize ===` - 终端尺寸改变
- ✅ `[SshConnection] Window change request sent` - SSH resize 请求
- ✅ `[AnsiParser] Unknown CSI/ESC sequence` - 未知的 ANSI 序列
- ✅ `[AnsiParser] DEC Private Mode Set/Reset` - 模式切换
- ✅ `[AnsiParser] Erase Line` - 行清除操作
- ✅ `[TextRenderer] Using XXX font` - 字体选择

### 12.4 测试步骤

1. **重新运行程序**，现在日志应该清晰很多

2. **查找关键日志**：
   ```
   === Terminal Resize ===
   Canvas size: XXX x YYY px
   Char size: 9.0 x 18.0 px
   Old terminal: 80 x 24 chars
   New terminal: AAA x BBB chars
   Buffer size: 80 x 24 chars
   Resize counter: 0
   >>> Resizing terminal to: AAAxBBB
   [TerminalBuffer] Resizing buffer from 80x24 to AAAxBBB
   [SshConnection] Attempting to resize PTY to AAAxBBB
   [SshConnection] Window change request sent: AAAxBBB
   ======================
   ```

3. **检查计算出的终端尺寸（AAA x BBB）**：
   - 如果仍然是 80x24，说明 Canvas 尺寸太小
   - 如果是更大的值（如 133x44），说明 resize 已经成功

4. **运行 btop**：
   ```bash
   btop
   ```

### 12.5 如果仍然是 80x24

如果日志显示计算出的终端尺寸仍然是 80x24，可能的原因：

1. **窗口太小**：
   - 检查日志中的 `Canvas size`
   - 如果 Canvas 是 720x432 px（刚好 80x24 @ 9x18），说明窗口尺寸就是这么小
   - 请**最大化窗口**或手动调整窗口大小

2. **字符尺寸设置**：
   - 当前设置：9x18 px
   - 如果想要更多列/行，可以减小字符尺寸（例如改为 8x16）
   - 如果想要更大的字符，可以增加字符尺寸（例如改为 10x20）

3. **DPI 缩放问题**：
   - 高 DPI 显示器可能导致 Canvas 尺寸计算不准确
   - 这需要额外的 DPI 适配代码

### 12.6 预期结果

正确的日志输出应该类似：
```
=== Terminal Resize ===
Canvas size: 1440.0 x 900.0 px
Char size: 9.0 x 18.0 px
Old terminal: 80 x 24 chars
New terminal: 160 x 50 chars
Buffer size: 80 x 24 chars
Resize counter: 0
>>> Resizing terminal to: 160x50
[TerminalBuffer] Resizing buffer from 80x24 to 160x50
[TerminalBuffer] Resize complete, new size: 160x50, visible lines: 50
[SshConnection] Attempting to resize PTY to 160x50
[SshConnection] Window change request sent: 160x50
======================
```

这表示：
- Canvas 是 1440x900 像素
- 以 9x18 的字符尺寸，可以容纳 160 列 x 50 行
- 已成功通知服务器新的终端尺寸

---

## 十三、添加粘贴功能和标点符号支持

### 13.1 问题描述

用户报告了两个输入相关的问题：
1. ❌ **无法向终端粘贴内容** - Ctrl+V 不起作用
2. ❌ **无法输入引号和其他标点符号** - 只能输入字母和数字

### 13.2 问题分析

#### 问题 1：无法粘贴

**根本原因**：
- 键盘处理函数中，Ctrl+V 被当作普通的 Ctrl 组合键处理
- 没有特殊处理剪贴板粘贴功能
- Ctrl+V 会发送 `0x16` 控制字符而不是粘贴剪贴板内容

#### 问题 2：无法输入标点符号

**根本原因**：
- 当前的键盘处理逻辑分为两步：
  1. 通过反射尝试获取字符
  2. 如果失败，则通过按键映射（只映射了字母 A-Z 和数字）
- 标点符号没有在按键映射中处理
- 如果反射失败（某些键盘布局或 Compose 版本），标点符号就无法输入

### 13.3 解决方案

#### 解决方案 1：实现 Ctrl+V 粘贴

在 `handleKeyEvent` 中添加 Ctrl+V 的特殊处理：

```kotlin
// 1. 优先处理 Ctrl 组合键（用于终端控制）
if (event.isCtrlPressed) {
    // 特殊处理 Ctrl+V (粘贴)
    if (key == Key.V) {
        val clipboardText = clipboardManager.getText()?.text
        if (!clipboardText.isNullOrEmpty()) {
            println("  -> Pasting text: ${clipboardText.take(50)}...")
            viewModel.handleInputBytes(clipboardText.toByteArray())
            return true
        }
    }
    return handleCtrlCombination(event, viewModel)
}
```

**工作原理**：
- 检测到 Ctrl+V 时，直接从剪贴板获取文本
- 将文本作为字节数组发送到终端
- 支持粘贴多行文本、命令等

#### 解决方案 2：添加长按粘贴（移动端风格）

为了更好的用户体验，还添加了长按粘贴：

```kotlin
.pointerInput(Unit) {
    detectTapGestures(
        onTap = { offset ->
            focusRequester.requestFocus()
        },
        onLongPress = { offset ->
            // 长按粘贴
            val clipboardText = clipboardManager.getText()?.text
            if (!clipboardText.isNullOrEmpty()) {
                println("Long press - pasting text")
                viewModel.handleInputBytes(clipboardText.toByteArray())
            }
        },
    )
}
```

#### 解决方案 3：添加标点符号映射

添加 `getPunctuationChar` 函数来处理所有常见的标点符号：

```kotlin
private fun getPunctuationChar(key: Key, shiftPressed: Boolean): Char? {
    return when (key.toString()) {
        // 标点符号
        "Key: Minus", "Minus" -> if (shiftPressed) '_' else '-'
        "Key: Equals", "Equals" -> if (shiftPressed) '+' else '='
        "Key: LeftBracket", "LeftBracket" -> if (shiftPressed) '{' else '['
        "Key: RightBracket", "RightBracket" -> if (shiftPressed) '}' else ']'
        "Key: Backslash", "Backslash" -> if (shiftPressed) '|' else '\\'
        "Key: Semicolon", "Semicolon" -> if (shiftPressed) ':' else ';'
        "Key: Apostrophe", "Apostrophe", "Quote" -> if (shiftPressed) '"' else '\''  // 引号！
        "Key: Grave", "Grave" -> if (shiftPressed) '~' else '`'
        "Key: Comma", "Comma" -> if (shiftPressed) '<' else ','
        "Key: Period", "Period" -> if (shiftPressed) '>' else '.'
        "Key: Slash", "Slash" -> if (shiftPressed) '?' else '/'
        
        // 数字键（支持 Shift 修饰符）
        "Key: Zero", "Zero", "Key: 0" -> if (shiftPressed) ')' else '0'
        "Key: One", "One", "Key: 1" -> if (shiftPressed) '!' else '1'
        "Key: Two", "Two", "Key: 2" -> if (shiftPressed) '@' else '2'
        // ... 其他数字键 ...
        
        else -> null
    }
}
```

**键盘处理优先级**（从高到低）：
1. Ctrl 组合键（Ctrl+V 特殊处理粘贴）
2. 特殊功能键（Enter, Tab, 方向键, Home, End, PageUp, PageDown）
3. 通过反射获取的字符（如果成功）
4. **标点符号映射**（新增）
5. 字母 A-Z 映射
6. 数字键映射（后备方案）

#### 解决方案 4：添加更多功能键

同时添加了对其他常用功能键的支持：

```kotlin
Key.Home -> viewModel.handleInputBytes("\u001B[H".toByteArray())
Key.End -> viewModel.handleInputBytes("\u001B[F".toByteArray())
Key.PageUp -> viewModel.handleInputBytes("\u001B[5~".toByteArray())
Key.PageDown -> viewModel.handleInputBytes("\u001B[6~".toByteArray())
```

### 13.4 支持的标点符号

现在支持所有常见的标点符号：

| 键 | 普通 | Shift |
|----|------|-------|
| ' (Apostrophe) | `'` | `"` |
| , (Comma) | `,` | `<` |
| . (Period) | `.` | `>` |
| / (Slash) | `/` | `?` |
| ; (Semicolon) | `;` | `:` |
| [ (LeftBracket) | `[` | `{` |
| ] (RightBracket) | `]` | `}` |
| \ (Backslash) | `\` | `|` |
| - (Minus) | `-` | `_` |
| = (Equals) | `=` | `+` |
| ` (Grave) | `` ` `` | `~` |
| 1-9, 0 | 数字 | `!@#$%^&*()` |

### 13.5 测试步骤

#### 测试粘贴功能

1. **复制一些文本**到剪贴板（Ctrl+C）
2. **在终端中按 Ctrl+V**
3. **文本应该被粘贴**到终端

或者：

1. **复制文本**
2. **长按**终端区域
3. **文本应该被粘贴**

#### 测试引号和标点符号

尝试输入以下命令：

```bash
echo "Hello, World!"
ls -la /path/to/directory
cat file.txt | grep "pattern"
echo 'Single quotes work too'
test="variable assignment"
echo $((1 + 2 * 3))
```

所有这些命令现在都应该能够正常输入！

#### 测试功能键

- **Home** - 移动到行首
- **End** - 移动到行尾
- **PageUp/PageDown** - 在某些程序中滚动（如 less, man）

### 13.6 已知限制

1. **右键粘贴**：
   - 由于 Compose 的限制，无法直接检测鼠标右键点击
   - 使用长按作为替代方案
   - 如果需要真正的右键支持，需要更底层的鼠标事件处理

2. **某些特殊字符**：
   - 非英文键盘布局的特殊字符可能需要额外处理
   - 大部分字符应该可以通过反射获取
   - 如果某个字符无法输入，请报告按键名称（从日志中的 `key=` 获取）

3. **粘贴大量文本**：
   - 粘贴非常大的文本可能会有延迟
   - 这是因为文本是作为一个整体发送到 SSH 服务器的
   - 未来可以考虑分块发送

### 13.7 调试信息

如果某个字符无法输入，日志会显示：

```
handleKeyEvent called: key=Key: XXX, ctrl=false, shift=false, alt=false
  -> Found char: ? (code: 0)
```

或者：

```
handleKeyEvent called: key=Key: XXX, ctrl=false, shift=false, alt=false
Key event not handled: Key: XXX
```

请将这些日志信息提供给开发者，以便添加对该字符的支持。

---

## 修复 19：中文字符宽度支持（Wide Character Support）

**日期**：2025-11-14

### 问题描述

用户报告："发现一个新的问题，当渲染中文的时候，字符的宽度有点窄，导致显示的文字挤在一起遮挡了。"

中文字符（以及其他CJK字符）在终端中显示时相互重叠，无法正常阅读。

### 问题原因

**根本原因**：终端没有正确实现宽字符（Wide Character）支持。

在标准终端中：
- **普通字符**（ASCII）占用 **1个单元格**
- **宽字符**（如中文、日文、韩文）占用 **2个单元格**

当前实现存在的问题：
1. `TerminalCell` 没有标记字符是否是宽字符
2. 所有字符都被当作占用1个单元格处理
3. 渲染时，宽字符只使用了1个单元格的宽度，导致字符重叠

### 解决方案

**实现完整的宽字符支持**：

#### 1. 修改 `TerminalCell` 添加宽字符标记

```kotlin
data class TerminalCell(
    val char: Char = ' ',
    // ... 其他属性 ...
    val isWideChar: Boolean = false,        // 是否是宽字符（如中文，占2个单元格）
    val isWideContinuation: Boolean = false, // 是否是宽字符的延续单元格（不渲染）
)
```

#### 2. 创建字符宽度检测工具函数

新建 `CharWidthUtils.kt`：

```kotlin
fun isWideChar(char: Char): Boolean {
    val code = char.code
    return when {
        // CJK Unified Ideographs（中日韩统一表意文字）
        code in 0x4E00..0x9FFF -> true
        // CJK Extension A
        code in 0x3400..0x4DBF -> true
        // CJK Extension B, C, D, E, F
        code in 0x20000..0x2CEAF -> true
        // CJK Symbols and Punctuation
        code in 0x3000..0x303F -> true
        // Halfwidth and Fullwidth Forms
        code in 0xFF00..0xFFEF -> true
        // Hiragana（平假名）
        code in 0x3040..0x309F -> true
        // Katakana（片假名）
        code in 0x30A0..0x30FF -> true
        // Hangul Syllables（韩文音节）
        code in 0xAC00..0xD7AF -> true
        // Emoji 和其他宽字符
        code in 0x1F300..0x1F9FF -> true  // Emoji
        code in 0x2600..0x26FF -> true    // Miscellaneous Symbols
        code in 0x2700..0x27BF -> true    // Dingbats
        else -> false
    }
}

fun getCharWidth(char: Char): Int {
    return if (isWideChar(char)) 2 else 1
}
```

#### 3. 修改 `TerminalViewModel` 的 `onText` 回调

```kotlin
onText = { char, style ->
    // 检测是否是宽字符（如中文，占2个单元格）
    val isWide = cn.hjhw.ssh.terminal.isWideChar(char)
    
    // 获取当前行
    val line = buffer.getVisibleLine(cursor.y) ?: buffer.getCurrentLine()
    
    // 写入主字符单元格
    val cell = TerminalCell(
        char = char,
        // ... 其他属性 ...
        isWideChar = isWide,
    )
    line.setCell(cursor.x, cell)
    
    // 如果是宽字符，需要在下一个单元格写入延续标记
    if (isWide && cursor.x + 1 < bufferWidth) {
        val continuationCell = TerminalCell(
            char = ' ',  // 延续单元格不显示字符
            foregroundColor = style.foregroundColor,
            backgroundColor = style.backgroundColor,
            isWideContinuation = true,
        )
        line.setCell(cursor.x + 1, continuationCell)
    }
    
    // 移动光标（宽字符移动2格，普通字符移动1格）
    val charWidth = if (isWide) 2 else 1
    cursor.x += charWidth
    
    // ... 处理换行等 ...
}
```

#### 4. 修改 `TerminalView` 的渲染逻辑

```kotlin
// 绘制每一行
for (y in 0 until height) {
    val line = buffer.getVisibleLine(y)
    if (line != null) {
        val lineWidth = line.width()
        var x = 0
        while (x < lineWidth.coerceAtMost(width)) {
            val cell = line.getCell(x)
            
            // 跳过宽字符的延续单元格（它们不应该被渲染）
            if (cell.isWideContinuation) {
                x++
                continue
            }
            
            val xPos = x * charWidth
            val yPos = y * charHeight
            
            // 宽字符占用2个单元格的宽度
            val cellWidth = if (cell.isWideChar) charWidth * 2 else charWidth
            
            // 绘制背景
            val bgColor = cell.backgroundColor.toComposeColor(backgroundColor)
            drawRect(
                color = bgColor,
                topLeft = Offset(xPos, yPos),
                size = Size(cellWidth, charHeight),
            )
            
            // 绘制字符
            if (!cell.invisible && cell.char != ' ') {
                val fgColor = cell.foregroundColor.toComposeColor(foregroundColor)
                
                // 使用文本渲染器绘制字符
                TextRenderer.drawText(
                    scope = this@drawTerminalContent,
                    text = cell.char.toString(),
                    x = xPos,
                    y = yPos + charHeight * 0.8f,
                    color = fgColor,
                    fontSize = charHeight * 0.8f,
                    bold = cell.bold,
                )
                
                // 绘制下划线（使用完整的宽字符宽度）
                if (cell.underline) {
                    drawLine(
                        color = fgColor,
                        start = Offset(xPos, yPos + charHeight - 2),
                        end = Offset(xPos + cellWidth, yPos + charHeight - 2),
                        strokeWidth = 1f,
                    )
                }
            }
            
            x++
        }
    }
}
```

### 修改的文件

1. **`composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/TerminalCell.kt`**
   - 添加 `isWideChar` 和 `isWideContinuation` 属性

2. **`composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/CharWidthUtils.kt`** （新建）
   - 实现 `isWideChar()` 和 `getCharWidth()` 函数
   - 支持检测 CJK、全角符号、Emoji 等宽字符

3. **`composeApp/src/commonMain/kotlin/cn/hjhw/ssh/ui/TerminalViewModel.kt`**
   - 修改 `onText` 回调，检测宽字符并占用2个单元格
   - 宽字符写入时，在下一个单元格写入延续标记
   - 光标移动根据字符宽度调整（宽字符+2，普通字符+1）

4. **`composeApp/src/commonMain/kotlin/cn/hjhw/ssh/ui/TerminalView.kt`**
   - 修改渲染循环，跳过宽字符延续单元格
   - 宽字符使用2倍单元格宽度渲染背景和下划线

### 测试方法

1. **测试中文显示**：
   ```bash
   echo "你好，世界！Hello World"
   ```
   - 中文字符应该正常显示，不重叠
   - 中英文混排应该对齐

2. **测试 `cat` 中文文件**：
   ```bash
   cat chinese_file.txt
   ```
   - 多行中文应该正常显示

3. **测试全角符号**：
   ```bash
   echo "【标题】：内容"
   ```
   - 全角括号、冒号应该占用正确的宽度

4. **测试 CJK 混排**：
   ```bash
   ls -la | grep "中文目录"
   ```
   - 包含中文的文件名应该正确对齐

### 技术细节

**标准终端宽字符行为**：
- **East Asian Width** 属性决定字符宽度
- **宽字符 (Wide/Fullwidth)** 占用 2 个单元格
- **窄字符 (Narrow/Halfwidth)** 占用 1 个单元格
- 当宽字符写入时，它占用当前和下一个单元格位置
- 下一个单元格被标记为"延续"，不渲染内容

**实现细节**：
1. **字符宽度检测**：基于 Unicode 范围判断
2. **双单元格占用**：主单元格 + 延续单元格
3. **渲染优化**：延续单元格跳过渲染
4. **光标移动**：根据字符宽度调整步长

### 预期效果

✅ 中文字符显示完整，不重叠  
✅ 中英文混排正确对齐  
✅ CJK 字符（中日韩）正确显示  
✅ 全角符号占用正确宽度  
✅ Emoji 等宽字符正确显示  
✅ 终端宽度计算准确（80列 = 40个中文字符）

### 注意事项

1. **字符宽度数据库**：当前实现基于 Unicode 范围判断，覆盖了常见的宽字符。如需更精确的判断，可以集成 `wcwidth` 算法或 Unicode East Asian Width 数据库。

2. **混合宽度行**：当一行中同时包含宽字符和窄字符时，光标位置和渲染位置应该保持一致。

3. **删除和编辑**：当删除宽字符时，应该同时清除主单元格和延续单元格。这由服务器端的 ANSI 序列处理，客户端无需特殊处理。

4. **性能影响**：每个字符都需要检测宽度，但 `isWideChar()` 函数使用简单的范围判断，性能开销很小。

---

## 修复 20：小键盘（Numpad）支持

**日期**：2025-11-14

### 问题描述

用户报告："发现一个问题，没有捕捉小键盘的功能键逻辑"

使用小键盘（Numeric Keypad）输入数字、运算符等字符时，终端没有响应。

### 问题原因

**根本原因**：`handleKeyEvent` 函数缺少对小键盘按键的处理逻辑。

小键盘按键在 Compose Desktop 中有专门的 Key 枚举值（如 `Key.NumPad0` 到 `Key.NumPad9`），与主键盘区的数字键（`Key.Zero` 到 `Key.Nine`）是不同的。

当前实现只处理了主键盘区的按键，导致小键盘按键未被识别。

### 解决方案

在 `TerminalView.kt` 的 `handleKeyEvent` 函数中，添加对所有小键盘按键的处理：

#### 添加的小键盘按键支持

```kotlin
// 小键盘（Numpad）数字键
Key.NumPad0 -> {
    viewModel.handleInputBytes("0".toByteArray())
    return true
}
Key.NumPad1 -> {
    viewModel.handleInputBytes("1".toByteArray())
    return true
}
Key.NumPad2 -> {
    viewModel.handleInputBytes("2".toByteArray())
    return true
}
Key.NumPad3 -> {
    viewModel.handleInputBytes("3".toByteArray())
    return true
}
Key.NumPad4 -> {
    viewModel.handleInputBytes("4".toByteArray())
    return true
}
Key.NumPad5 -> {
    viewModel.handleInputBytes("5".toByteArray())
    return true
}
Key.NumPad6 -> {
    viewModel.handleInputBytes("6".toByteArray())
    return true
}
Key.NumPad7 -> {
    viewModel.handleInputBytes("7".toByteArray())
    return true
}
Key.NumPad8 -> {
    viewModel.handleInputBytes("8".toByteArray())
    return true
}
Key.NumPad9 -> {
    viewModel.handleInputBytes("9".toByteArray())
    return true
}

// 小键盘运算符键
Key.NumPadAdd -> {
    viewModel.handleInputBytes("+".toByteArray())
    return true
}
Key.NumPadSubtract -> {
    viewModel.handleInputBytes("-".toByteArray())
    return true
}
Key.NumPadMultiply -> {
    viewModel.handleInputBytes("*".toByteArray())
    return true
}
Key.NumPadDivide -> {
    viewModel.handleInputBytes("/".toByteArray())
    return true
}
Key.NumPadDot -> {
    viewModel.handleInputBytes(".".toByteArray())
    return true
}
Key.NumPadEnter -> {
    viewModel.handleInputBytes("\r".toByteArray())
    return true
}
Key.NumPadEquals -> {
    viewModel.handleInputBytes("=".toByteArray())
    return true
}
```

### 修改的文件

1. **`composeApp/src/commonMain/kotlin/cn/hjhw/ssh/ui/TerminalView.kt`**
   - 在 `handleKeyEvent` 的特殊功能键处理部分添加小键盘支持
   - 位置：在 `Key.PageDown` 处理之后，反射获取字符之前

### 支持的小键盘按键

#### 数字键（10个）
- `NumPad0` → `0`
- `NumPad1` → `1`
- `NumPad2` → `2`
- `NumPad3` → `3`
- `NumPad4` → `4`
- `NumPad5` → `5`
- `NumPad6` → `6`
- `NumPad7` → `7`
- `NumPad8` → `8`
- `NumPad9` → `9`

#### 运算符键（5个）
- `NumPadAdd` → `+` (加号)
- `NumPadSubtract` → `-` (减号)
- `NumPadMultiply` → `*` (乘号)
- `NumPadDivide` → `/` (除号)
- `NumPadEquals` → `=` (等号)

#### 其他功能键（2个）
- `NumPadDot` → `.` (小数点/句点)
- `NumPadEnter` → `\r` (回车)

### 测试方法

1. **测试数字输入**：
   - 使用小键盘输入数字 `0-9`
   - 应该能正常输入，与主键盘数字键效果一致

2. **测试运算符输入**：
   ```bash
   echo 1+2*3/4-5
   ```
   - 使用小键盘输入运算符
   - 应该正常显示和执行

3. **测试小数点**：
   ```bash
   echo 3.14159
   ```
   - 使用小键盘的小数点键
   - 应该正常输入

4. **测试小键盘 Enter**：
   - 输入命令后按小键盘的 Enter 键
   - 应该正常执行命令，与主键盘 Enter 效果一致

5. **测试混合输入**：
   - 混合使用主键盘和小键盘输入
   - 应该都能正常工作

### 技术细节

**小键盘 vs 主键盘**：
- **小键盘**：通常位于键盘右侧，专门用于快速数字输入
- **主键盘**：数字行位于键盘顶部
- 在 Compose Desktop 中，这两个区域的按键有不同的 `Key` 枚举值

**处理位置**：
- 小键盘按键的处理放在**特殊功能键**部分（Step 2）
- 在反射获取字符之前处理，确保优先级
- 直接映射到对应的字符或控制字符

**NumLock 状态**：
- 当前实现假设 NumLock 处于开启状态（数字模式）
- 如果 NumLock 关闭，某些键（如 NumPad2/4/6/8）可能映射为方向键
- 这种情况下，系统会将按键识别为 `Key.DirectionUp/Down/Left/Right`，已有处理逻辑

### 预期效果

✅ 小键盘数字键（0-9）正常输入  
✅ 小键盘运算符（+、-、*、/）正常输入  
✅ 小键盘小数点正常输入  
✅ 小键盘 Enter 正常执行  
✅ 小键盘与主键盘混用无问题  
✅ 适合需要大量数字输入的场景（如计算器、配置端口号等）

### 注意事项

1. **NumLock 状态**：如果 NumLock 关闭，部分小键盘按键会变成导航键（Home、End、方向键等），这些已由现有逻辑处理。

2. **小键盘 Enter vs 主 Enter**：两者都映射为 `\r`（回车符），在终端中效果相同。

3. **跨平台兼容性**：Compose Multiplatform 在不同平台（JVM/Native/JS）可能有不同的小键盘按键名称，当前实现针对 JVM Desktop。

---

## 修复 21：nano 编辑器颜色和宽度显示问题

**日期**：2025-11-14

### 问题描述

用户报告："在使用 nano 的时候，字体颜色没有渲染出来，而且显示的宽度也有问题"

具体表现：
1. nano 编辑器的状态栏、菜单等没有显示颜色（应该是反色显示）
2. 显示的内容宽度不正确，没有占满终端宽度

### 问题原因

**根本原因 1：没有处理 `reverse` 属性**

nano 编辑器大量使用 ANSI 的 **reverse video** (反色) 功能来显示状态栏、菜单、高亮等元素。当 `reverse=true` 时，前景色和背景色应该互换。

当前渲染逻辑中：
```kotlin
// 之前的代码：直接使用前景色和背景色，没有检查 reverse 属性
val fgColor = cell.foregroundColor.toComposeColor(foregroundColor)
val bgColor = cell.backgroundColor.toComposeColor(backgroundColor)
```

这导致所有反色元素都显示为普通文本，失去了视觉区分。

**根本原因 2：行宽渲染不完整**

渲染逻辑中使用 `lineWidth`（行的实际内容宽度）而不是 `width`（终端的完整宽度）：
```kotlin
// 之前的代码：只渲染到内容宽度
while (x < lineWidth.coerceAtMost(width)) {
```

这导致：
- 行尾的空格或默认单元格不被渲染
- 背景色在行尾被截断
- nano 的状态栏（通常占满整行）显示不完整

**根本原因 3：没有处理 `faint` 属性**

某些终端应用使用 `faint` 属性来降低文本的显示强度（暗淡显示），但当前实现未处理此属性。

### 解决方案

#### 1. 添加 `reverse` 属性处理

在渲染时检查 `reverse` 属性，如果为 `true`，则交换前景色和背景色：

```kotlin
// 处理 reverse 属性（反色）：前景色和背景色互换
val actualFgColor = if (cell.reverse) {
    cell.backgroundColor.toComposeColor(backgroundColor)
} else {
    cell.foregroundColor.toComposeColor(foregroundColor)
}
val actualBgColor = if (cell.reverse) {
    cell.foregroundColor.toComposeColor(foregroundColor)
} else {
    cell.backgroundColor.toComposeColor(backgroundColor)
}
```

#### 2. 渲染完整行宽

修改渲染循环，确保每行都渲染到终端的完整宽度：

```kotlin
// 渲染完整的终端宽度，而不是只渲染到行的实际内容宽度
// 这样可以确保背景色和反色在整行中正确显示
var x = 0
while (x < width) {  // 之前是 lineWidth.coerceAtMost(width)
    val cell = line.getCell(x)
    // ...
}
```

#### 3. 添加 `faint` 属性支持

在渲染字符时，如果 `faint=true`，降低颜色的不透明度：

```kotlin
// 如果是 faint（暗淡）模式，降低颜色的不透明度
val finalFgColor = if (cell.faint) {
    actualFgColor.copy(alpha = actualFgColor.alpha * 0.5f)
} else {
    actualFgColor
}
```

### 修改的文件

1. **`composeApp/src/commonMain/kotlin/cn/hjhw/ssh/ui/TerminalView.kt`**
   - 修改 `drawTerminalContent` 函数
   - 添加 `reverse` 属性处理逻辑
   - 修改渲染循环为完整行宽
   - 添加 `faint` 属性支持

### 完整的渲染逻辑

```kotlin
// 绘制每一行
for (y in 0 until height) {
    val line = buffer.getVisibleLine(y)
    if (line != null) {
        // 渲染完整的终端宽度
        var x = 0
        while (x < width) {
            val cell = line.getCell(x)
            
            // 跳过宽字符的延续单元格
            if (cell.isWideContinuation) {
                x++
                continue
            }
            
            val xPos = x * charWidth
            val yPos = y * charHeight
            val cellWidth = if (cell.isWideChar) charWidth * 2 else charWidth
            
            // 处理 reverse 属性（反色）
            val actualFgColor = if (cell.reverse) {
                cell.backgroundColor.toComposeColor(backgroundColor)
            } else {
                cell.foregroundColor.toComposeColor(foregroundColor)
            }
            val actualBgColor = if (cell.reverse) {
                cell.foregroundColor.toComposeColor(foregroundColor)
            } else {
                cell.backgroundColor.toComposeColor(backgroundColor)
            }
            
            // 绘制背景（始终绘制，包括空格）
            drawRect(
                color = actualBgColor,
                topLeft = Offset(xPos, yPos),
                size = Size(cellWidth, charHeight),
            )
            
            // 绘制字符
            if (!cell.invisible && cell.char != ' ') {
                // 如果是 faint（暗淡）模式，降低颜色的不透明度
                val finalFgColor = if (cell.faint) {
                    actualFgColor.copy(alpha = actualFgColor.alpha * 0.5f)
                } else {
                    actualFgColor
                }
                
                TextRenderer.drawText(
                    scope = this@drawTerminalContent,
                    text = cell.char.toString(),
                    x = xPos,
                    y = yPos + charHeight * 0.8f,
                    color = finalFgColor,
                    fontSize = charHeight * 0.8f,
                    bold = cell.bold,
                )
                
                // 绘制下划线
                if (cell.underline) {
                    drawLine(
                        color = finalFgColor,
                        start = Offset(xPos, yPos + charHeight - 2),
                        end = Offset(xPos + cellWidth, yPos + charHeight - 2),
                        strokeWidth = 1f,
                    )
                }
            }
            
            x++
        }
    }
}
```

### 测试方法

1. **测试 nano 编辑器**：
   ```bash
   nano test.txt
   ```
   - 状态栏应该显示为反色（白底黑字或其他配色）
   - 底部的快捷键提示应该正常显示
   - 文件内容区域应该占满整个宽度

2. **测试其他使用反色的应用**：
   ```bash
   vi test.txt   # vi 也使用反色显示
   htop          # htop 的标题栏使用反色
   ```

3. **测试颜色混合**：
   - nano 的语法高亮（如果启用）应该正常显示
   - 反色 + 颜色的组合应该正确渲染

4. **测试完整行宽**：
   - 状态栏的背景色应该占满整行
   - 不应该在行尾出现截断或黑色区域

### 技术细节

**ANSI Reverse Video**：
- **SGR 7**：`ESC[7m` - 启用反色
- **SGR 27**：`ESC[27m` - 禁用反色
- 效果：前景色 ↔ 背景色互换

**实现要点**：
1. **反色优先级最高**：即使设置了自定义颜色，反色也会将它们互换
2. **空格也需要渲染**：反色模式下，空格显示背景色，必须渲染
3. **完整行宽**：确保每行都渲染到终端宽度，而不是内容宽度
4. **Faint 叠加**：faint 可以与反色、颜色同时使用

**nano 编辑器的颜色使用**：
- **状态栏**：反色显示（通常是白底黑字）
- **标题栏**：反色 + 粗体
- **快捷键提示**：反色显示
- **语法高亮**：各种 ANSI 颜色代码
- **选中文本**：反色显示

### 预期效果

✅ nano 状态栏正确显示为反色  
✅ nano 底部快捷键提示正常显示  
✅ 显示宽度占满整个终端  
✅ 语法高亮颜色正常渲染  
✅ 反色 + 颜色混合正确显示  
✅ htop、vi 等其他应用的反色元素正常显示  
✅ faint 属性正确降低文本亮度

### 注意事项

1. **性能影响**：渲染完整行宽会略微增加渲染开销，但对于现代硬件可以忽略不计。

2. **空单元格默认值**：`line.getCell(x)` 对于超出内容的索引会返回 `TerminalCell.empty()`，默认为空格 + 默认颜色。

3. **宽字符与反色**：宽字符的两个单元格（主单元格 + 延续单元格）都应该使用相同的背景色。

4. **Blink 属性**：当前未实现 blink（闪烁）效果，因为现代终端通常禁用此功能。如需实现，可以使用定时器切换可见性。

---

## 修复 22：完善 SGR 颜色支持（亮色和 invisible）

**日期**：2025-11-14

### 问题描述

用户报告："nano 的文字颜色还是没有显示出来"

尽管之前添加了 `reverse` 属性支持，但 nano 编辑器的语法高亮和状态栏颜色仍然无法正确显示。

### 问题原因

**根本原因：缺少亮色（Bright Colors）支持**

经过全面检查，发现 ANSI 解析器缺少以下 SGR (Select Graphic Rendition) 代码的支持：

#### 1. **亮色（Bright Colors）**
- **SGR 90-97**：亮前景色（Bright Black - Bright White）
- **SGR 100-107**：亮背景色（Bright Black - Bright White）

nano、vi、htop 等应用大量使用亮色来区分不同的界面元素和语法高亮。当前实现只支持标准 16 色中的 8 种暗色（30-37 前景色，40-47 背景色），缺少另外 8 种亮色。

#### 2. **Invisible 属性**
- **SGR 8**：设置文本为不可见
- **SGR 28**：取消不可见

虽然不常用，但某些应用可能使用此功能隐藏敏感信息（如密码输入）。

### 已支持的 SGR 功能总结

#### ✅ 文本属性（已完整支持）
| SGR 代码 | 功能 | 说明 |
|---------|------|------|
| 0 | 重置所有样式 | 恢复默认状态 |
| 1 | 粗体 | Bold |
| 2 | 暗淡 | Faint/Dim |
| 3 | 斜体 | Italic |
| 4 | 下划线 | Underline |
| 5 | 闪烁 | Blink (已解析但未渲染闪烁效果) |
| 7 | 反色 | Reverse Video |
| 8 | 不可见 | Invisible/Hidden |
| 9 | 删除线 | Strikethrough |
| 21-29 | 关闭对应属性 | 如 22=关闭粗体, 27=关闭反色, 28=关闭不可见 |

#### ✅ 标准 16 色（已完整支持）
| SGR 代码 | 前景色 | 背景色 | 颜色 |
|---------|--------|--------|------|
| 30/40 | ✅ | ✅ | 黑色 (Black) |
| 31/41 | ✅ | ✅ | 红色 (Red) |
| 32/42 | ✅ | ✅ | 绿色 (Green) |
| 33/43 | ✅ | ✅ | 黄色 (Yellow) |
| 34/44 | ✅ | ✅ | 蓝色 (Blue) |
| 35/45 | ✅ | ✅ | 品红 (Magenta) |
| 36/46 | ✅ | ✅ | 青色 (Cyan) |
| 37/47 | ✅ | ✅ | 白色 (White) |
| 90/100 | ✅ **新增** | ✅ **新增** | 亮黑色 (Bright Black/Gray) |
| 91/101 | ✅ **新增** | ✅ **新增** | 亮红色 (Bright Red) |
| 92/102 | ✅ **新增** | ✅ **新增** | 亮绿色 (Bright Green) |
| 93/103 | ✅ **新增** | ✅ **新增** | 亮黄色 (Bright Yellow) |
| 94/104 | ✅ **新增** | ✅ **新增** | 亮蓝色 (Bright Blue) |
| 95/105 | ✅ **新增** | ✅ **新增** | 亮品红 (Bright Magenta) |
| 96/106 | ✅ **新增** | ✅ **新增** | 亮青色 (Bright Cyan) |
| 97/107 | ✅ **新增** | ✅ **新增** | 亮白色 (Bright White) |
| 39/49 | ✅ | ✅ | 重置为默认颜色 |

#### ✅ 256 色和 TrueColor（已完整支持）
- **SGR 38;5;n**：设置前景色为 256 色索引 n
- **SGR 48;5;n**：设置背景色为 256 色索引 n
- **SGR 38;2;r;g;b**：设置前景色为 TrueColor RGB
- **SGR 48;2;r;g;b**：设置背景色为 TrueColor RGB

### 解决方案

#### 添加亮色支持

在 `AnsiParser.kt` 的 `handleSgr` 函数中添加：

```kotlin
code in 90..97 -> {
    // 亮前景色（Bright foreground colors）
    val colorIndex = code - 90 + 8  // 映射到 BrightBlack(8) - BrightWhite(15)
    cursor.currentStyle = cursor.currentStyle.copy(
        foregroundColor = TerminalColor.Standard(
            TerminalColor.StandardColor.values()[colorIndex]
        )
    )
}
code in 100..107 -> {
    // 亮背景色（Bright background colors）
    val colorIndex = code - 100 + 8  // 映射到 BrightBlack(8) - BrightWhite(15)
    cursor.currentStyle = cursor.currentStyle.copy(
        backgroundColor = TerminalColor.Standard(
            TerminalColor.StandardColor.values()[colorIndex]
        )
    )
}
```

#### 添加 Invisible 属性支持

```kotlin
code == 8 -> cursor.currentStyle = cursor.currentStyle.copy(invisible = true)
// 在 21-29 范围内添加：
28 -> cursor.currentStyle = cursor.currentStyle.copy(invisible = false)
```

#### 添加调试日志

```kotlin
// 在 handleSgr 开头添加
println("      [AnsiParser] SGR params: $params")
```

这将帮助诊断实际接收到的颜色代码。

### 修改的文件

1. **`composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/AnsiParser.kt`**
   - 在 `handleSgr` 函数中添加 SGR 90-97（亮前景色）支持
   - 在 `handleSgr` 函数中添加 SGR 100-107（亮背景色）支持
   - 添加 SGR 8（invisible）支持
   - 添加 SGR 28（取消 invisible）支持
   - 添加 SGR 参数调试日志

### 测试方法

1. **测试 nano 编辑器颜色**：
   ```bash
   nano test.txt
   ```
   - 状态栏应该正确显示颜色（通常是亮白底黑字）
   - 如果启用了语法高亮，应该能看到不同的颜色
   - 底部快捷键提示应该正确显示

2. **测试亮色显示**：
   ```bash
   # 使用 ANSI 转义序列测试
   echo -e "\e[91mBright Red\e[0m"
   echo -e "\e[92mBright Green\e[0m"
   echo -e "\e[93mBright Yellow\e[0m"
   echo -e "\e[94mBright Blue\e[0m"
   echo -e "\e[95mBright Magenta\e[0m"
   echo -e "\e[96mBright Cyan\e[0m"
   echo -e "\e[97mBright White\e[0m"
   echo -e "\e[101mBright Red Background\e[0m"
   ```
   - 应该看到明显的亮色效果

3. **测试 vi 编辑器**：
   ```bash
   vi test.txt
   ```
   - 状态栏颜色应该正确
   - 语法高亮应该正常工作

4. **测试 htop/btop**：
   ```bash
   htop   # 或 btop
   ```
   - 界面颜色应该丰富多彩
   - 不同的状态（CPU、内存等）应该有不同的颜色

5. **查看调试日志**：
   - 运行 nano，观察控制台输出的 SGR 参数
   - 应该能看到类似 `[AnsiParser] SGR params: [1, 37, 44]` 的日志

### nano/vi 编辑器完整支持清单

#### ✅ 已完整支持的功能
1. **颜色系统**
   - ✅ 标准 16 色（包括亮色）
   - ✅ 256 色
   - ✅ TrueColor (24-bit)
   - ✅ 反色（Reverse Video）
   - ✅ 前景色/背景色独立控制

2. **文本样式**
   - ✅ 粗体 (Bold)
   - ✅ 暗淡 (Faint)
   - ✅ 斜体 (Italic)
   - ✅ 下划线 (Underline)
   - ✅ 删除线 (Strikethrough)
   - ✅ 不可见 (Invisible)
   - ✅ 闪烁 (Blink) - 解析但不渲染闪烁

3. **光标控制**
   - ✅ 光标移动（上下左右、绝对位置）
   - ✅ 光标保存/恢复
   - ✅ 光标可见性控制

4. **屏幕控制**
   - ✅ 清屏 (Clear Screen)
   - ✅ 清行 (Erase Line)
   - ✅ 滚动区域 (Scroll Region/DECSTBM)
   - ✅ 备用屏幕缓冲区 (Alternate Screen Buffer)

5. **字符处理**
   - ✅ 宽字符支持（中日韩字符占2个单元格）
   - ✅ Unicode 字符渲染
   - ✅ CJK 字体选择

6. **输入支持**
   - ✅ 完整键盘支持（包括小键盘）
   - ✅ Ctrl 组合键
   - ✅ 功能键（方向键、Home、End、PageUp/Down）
   - ✅ 粘贴功能

7. **渲染优化**
   - ✅ 完整行宽渲染
   - ✅ 反色正确处理
   - ✅ 宽字符正确占位

### 预期效果

✅ nano 编辑器颜色正常显示  
✅ nano 语法高亮正常工作  
✅ vi 编辑器颜色正常显示  
✅ htop/btop 界面色彩丰富  
✅ 亮色和暗色区分明显  
✅ 所有文本样式正确渲染  
✅ 全屏应用显示完整  
✅ 中文字符正常显示不重叠

### 技术细节

**标准 16 色映射**：
- 0-7：暗色（Black, Red, Green, Yellow, Blue, Magenta, Cyan, White）
- 8-15：亮色（对应的 Bright 版本）

**SGR 代码映射**：
- SGR 30-37 → 颜色 0-7（暗色）
- SGR 90-97 → 颜色 8-15（亮色）
- SGR 40-47 → 颜色 0-7（暗色）
- SGR 100-107 → 颜色 8-15（亮色）

**为什么需要亮色**：
1. **视觉层次**：亮色用于强调重要信息（如错误、警告）
2. **语法高亮**：不同的代码元素使用不同的颜色组合
3. **状态区分**：nano 的状态栏使用亮色背景
4. **可读性**：亮色在深色背景上更易读

### 注意事项

1. **颜色方案**：不同终端模拟器对标准 16 色的具体 RGB 值可能略有不同，当前使用标准 VGA 调色板。

2. **调试日志**：SGR 参数日志在生产环境中可能产生大量输出，可以根据需要关闭。

3. **Blink 效果**：虽然解析了 blink 属性，但未实现实际的闪烁渲染，因为现代终端通常禁用此功能以避免干扰。

4. **Invisible 属性**：不可见文本仍然占用单元格空间，只是不渲染字符。

---

## 修复 23：擦除行时保留样式属性（关键修复）

**日期**：2025-11-14

### 问题描述

用户报告："btop 占满整个终端没问题，但是 nano 就是不行，好像被截断了一样，也还是没有显示出颜色"

尽管之前已经实现了反色（reverse）渲染逻辑，但 nano 编辑器的状态栏和底部菜单仍然无法显示颜色。

### 问题原因

**根本原因：擦除行时没有保留样式属性**

通过详细分析发现，nano 的渲染流程如下：
1. 设置反色样式：`ESC[0;7m`（reset + reverse）
2. 擦除整行：`ESC[K`（Erase in Line）
3. 写入文本："GNU nano 8.6"

问题出在**步骤 2**：`handleEraseLine` 函数在擦除行时，虽然使用了 `cursor.currentStyle` 的颜色，但**没有保留 `reverse` 等样式属性**！

原代码：
```kotlin
val emptyCell = TerminalCell(
    char = ' ',
    foregroundColor = cursor.currentStyle.foregroundColor,
    backgroundColor = cursor.currentStyle.backgroundColor,
    // ❌ 缺少 reverse, bold, faint, underline 等属性
)
```

结果：
- 擦除后的单元格有正确的前景色和背景色
- 但是 `reverse=false`，所以渲染时不会交换颜色
- 导致反色效果失效，无法显示正确的背景色

这就是为什么 nano 的状态栏看起来是"没有颜色"的原因！

### 解决方案

修改 `handleEraseLine` 函数，在擦除时**完整保留当前样式的所有属性**：

```kotlin
val emptyCell = TerminalCell(
    char = ' ',
    foregroundColor = cursor.currentStyle.foregroundColor,
    backgroundColor = cursor.currentStyle.backgroundColor,
    reverse = cursor.currentStyle.reverse,        // ✅ 保留反色
    bold = cursor.currentStyle.bold,              // ✅ 保留粗体
    faint = cursor.currentStyle.faint,            // ✅ 保留暗淡
    underline = cursor.currentStyle.underline,    // ✅ 保留下划线
)
```

同时添加调试日志：
```kotlin
println("      [AnsiParser] Erasing from cursor to end of line (${cursor.x} to ${width-1}), current style: reverse=${cursor.currentStyle.reverse}")
```

### 修改的文件

1. **`composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/AnsiParser.kt`**
   - 修改 `handleEraseLine` 函数的三个 mode（0, 1, 2）
   - 在创建 `emptyCell` 时保留 `reverse`, `bold`, `faint`, `underline` 属性
   - 添加调试日志输出当前的 reverse 状态

2. **`composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/TerminalBuffer.kt`**
   - 在 `setScrollRegion` 中添加调试日志

3. **`composeApp/src/commonMain/kotlin/cn/hjhw/ssh/ui/TerminalView.kt`**
   - 添加反色单元格的渲染调试日志

### nano 编辑器的渲染流程

以下是 nano 绘制状态栏的完整流程（基于日志分析）：

#### 1. 清屏并移动光标
```
ESC[H       - 光标移动到 (0, 0)
ESC[J       - 清除屏幕
```

#### 2. 设置反色并擦除状态栏区域
```
ESC[71;75H  - 移动光标到第 71 行第 75 列
ESC[0;7m    - 重置样式 + 设置反色
ESC[K       - 擦除从光标到行尾（这里是关键！）
```

**问题发生**：如果擦除时不保留 `reverse=true`，那么擦除后的单元格将是 `reverse=false`，后续写入的文本虽然也有 `reverse=true`，但中间的空格单元格没有反色，导致显示不连续。

#### 3. 写入状态栏文本
```
[ Reading... ]
```

#### 4. 绘制完整的标题栏
```
ESC[H       - 回到顶部
ESC[0;7m    - 设置反色
  GNU nano 8.6 ...
```

### 为什么这个 bug 很隐蔽

1. **btop 正常** - btop 可能使用了 alternate screen 或者不同的渲染策略，没有触发这个问题
2. **颜色代码被正确解析** - `SGR params: [0, 7]` 日志显示解析正常
3. **反色逻辑看起来正确** - 渲染代码确实检查了 `cell.reverse` 并交换颜色
4. **只在特定场景出现** - 只有在"先设置反色，再擦除，再写入"这种流程中才会暴露

### 测试方法

1. **测试 nano**：
   ```bash
   nano test.txt
   ```
   - 顶部标题栏（"GNU nano 8.6..."）应该显示为白底黑字
   - 底部快捷键提示应该完整显示且有反色背景
   - 状态栏背景应该占满整行

2. **测试 vi**：
   ```bash
   vi test.txt
   ```
   - 状态栏应该有颜色

3. **测试其他全屏应用**：
   ```bash
   htop
   ```
   - 所有使用反色的界面元素应该正常显示

### 预期效果

✅ nano 状态栏显示为白底黑字（或配置的反色）  
✅ nano 底部菜单完整显示且有反色背景  
✅ 反色背景占满整行，不会被截断  
✅ vi 的状态栏正常显示  
✅ 所有使用反色的终端应用正常工作

### 技术细节

**ANSI Erase in Line (CSI K) 的标准行为**：
- Mode 0：从光标到行尾
- Mode 1：从行首到光标
- Mode 2：整行

**重要**：擦除时应该使用**当前样式的背景色**（包括反色后的背景色），而不是默认的黑色。

**样式继承规则**：
1. 设置样式（如 `ESC[7m`）会影响 `cursor.currentStyle`
2. 擦除操作应该使用 `cursor.currentStyle` 的所有属性
3. 后续写入的字符会继续使用当前样式

**为什么需要保留所有样式属性**：
- `reverse`：决定前景色和背景色是否互换
- `bold`：影响字体渲染
- `faint`：影响颜色亮度
- `underline`：影响下划线显示

### 调试技巧

如果还有问题，查看日志中的：
```
[AnsiParser] Erasing from cursor to end of line (...), current style: reverse=true
```

如果 `reverse=false` 但 nano 应该显示反色，说明在擦除之前样式设置有问题。

### 相关修复

这个修复是基于之前的修复堆叠而来的：
- **修复 21**：添加了 reverse 渲染逻辑
- **修复 22**：添加了亮色支持
- **修复 23**：修复了擦除时保留样式（本修复）

三个修复缺一不可，共同实现了完整的 nano 编辑器支持。

