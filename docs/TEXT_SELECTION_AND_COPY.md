# 文本选择和复制功能

## 📅 实现日期
2025-11-14

## ✅ 功能说明

HJSSH 终端模拟器现在支持完整的文本选择和复制功能，类似于其他终端应用（如 iTerm2、Windows Terminal）。

---

## 🖱️ 使用方法

### 1. 选择文本
**操作**: 按住鼠标左键并拖拽

**效果**:
- 拖拽时实时显示选择区域（半透明蓝色高亮）
- 支持单行选择和多行选择
- 支持反向选择（从右下到左上）

**示例**:
```
拖拽前:
> ls -la
total 48
drwxr-xr-x  6 user  staff   192 Nov 14 10:30 .

拖拽中（高亮显示）:
> ls -la
total 48            ← 选中部分显示蓝色高亮
drwxr-xr-x  6 user
```

### 2. 复制文本
**操作**: 在选中文本后，点击鼠标右键

**效果**:
- 文本自动复制到系统剪贴板
- 选择区域自动清除
- 控制台输出确认信息

**日志输出**:
```
[TextSelection] ✅ Copied to clipboard: total 48...
[TextSelection] Selection cleared after copy
```

### 3. 清除选择
**操作**: 
- 左键单击（不拖拽）
- 或在复制后自动清除

**效果**:
- 选择区域消失
- 终端保持焦点

### 4. 粘贴文本
**操作**: `Ctrl+V` 或 `Cmd+V`

**效果**:
- 剪贴板内容粘贴到终端
- 支持多行粘贴

---

## 🎨 视觉反馈

### 选择区域高亮
- **颜色**: 半透明蓝色 (`#4080FF` with 38% opacity)
- **样式**: 覆盖整个字符单元格
- **动态**: 拖拽时实时更新

### 单行选择
```
Before: Hello World
After:  Hello World  ← "World" 部分高亮
        ^^^^^ (蓝色背景)
```

### 多行选择
```
Line 1: First line of text
        ^^^^^^^^^^^^^^^^^^ (高亮到行尾)
Line 2: Second line text
        ^^^^^^^^^^^^^^^^ (整行高亮)
Line 3: Third line
        ^^^^^ (高亮到选择终点)
```

---

## 🏗️ 技术实现

### 架构设计

```
┌─────────────────────────────────────────────────┐
│              TerminalView (UI Layer)            │
│  ┌───────────────────────────────────────────┐  │
│  │  detectDragGestures                       │  │
│  │  - onDragStart  → startSelection()        │  │
│  │  - onDrag       → updateSelection()       │  │
│  │  - onDragEnd    → endSelection()          │  │
│  └───────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────┐  │
│  │  awaitPointerEventScope                   │  │
│  │  - isSecondaryPressed → 复制并清除        │  │
│  │  - isPrimaryPressed   → 请求焦点          │  │
│  └───────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────┐  │
│  │  drawSelection()                          │  │
│  │  - 绘制半透明蓝色高亮                      │  │
│  │  - 支持单行/多行渲染                       │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
                     ↕ (State & Events)
┌─────────────────────────────────────────────────┐
│         TerminalViewModel (Logic Layer)         │
│  ┌───────────────────────────────────────────┐  │
│  │  TextSelection State                      │  │
│  │  - startX, startY, endX, endY             │  │
│  └───────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────┐  │
│  │  Selection Methods                        │  │
│  │  - startSelection(x, y)                   │  │
│  │  - updateSelection(x, y)                  │  │
│  │  - endSelection() → String                │  │
│  │  - clearSelection()                       │  │
│  └───────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────┐  │
│  │  Text Extraction                          │  │
│  │  - normalizeSelection()                   │  │
│  │  - extractSelectedText()                  │  │
│  │  - extractLineText()                      │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
                     ↕ (Data Access)
┌─────────────────────────────────────────────────┐
│         TerminalBuffer (Data Layer)             │
│  ┌───────────────────────────────────────────┐  │
│  │  - getLine(y) → TerminalLine              │  │
│  │  - getCells() → List<TerminalCell>        │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

### 核心数据结构

#### TextSelection
```kotlin
data class TextSelection(
    val startX: Int,    // 起始列（0-based）
    val startY: Int,    // 起始行（0-based）
    val endX: Int,      // 结束列
    val endY: Int       // 结束行
)
```

#### 选择状态
```kotlin
var textSelection: TextSelection? by mutableStateOf(null)  // 当前选择范围
var selectedText: String? by mutableStateOf(null)          // 选中的文本内容
```

---

## 🔄 工作流程

### 1. 拖拽选择流程
```
用户按下鼠标左键
    ↓
onDragStart(offset)
    ↓
计算字符位置: x = offset.x / charWidth
              y = offset.y / charHeight
    ↓
viewModel.startSelection(x, y)
    ↓
textSelection = TextSelection(x, y, x, y)
    ↓
用户拖拽鼠标
    ↓
onDrag(change, _)
    ↓
计算新位置并更新选择
    ↓
viewModel.updateSelection(x, y)
    ↓
textSelection = textSelection.copy(endX=x, endY=y)
    ↓ (实时触发重绘)
drawSelection() → 绘制蓝色高亮
    ↓
用户释放鼠标
    ↓
onDragEnd()
    ↓
viewModel.endSelection()
    ↓
extractSelectedText(startX, startY, endX, endY)
    ↓
从 TerminalBuffer 提取文本
    ↓
selectedText = "提取的文本内容"
```

### 2. 右键复制流程
```
用户右键点击
    ↓
awaitPointerEventScope { awaitPointerEvent() }
    ↓
检测: event.buttons.isSecondaryPressed == true
    ↓
获取: viewModel.selectedText
    ↓
clipboardManager.setText(AnnotatedString(selectedText))
    ↓
viewModel.clearSelection()
    ↓
textSelection = null
selectedText = null
    ↓
✅ 复制完成，选择清除
```

### 3. 左键点击清除流程
```
用户左键点击（不拖拽）
    ↓
onDragStart() → isSelecting = true, isDragging = false
    ↓
onDragEnd() → 检测 isDragging == false
    ↓
viewModel.clearSelection()
    ↓
✅ 选择已清除
```

---

## 🎯 关键特性

### 1. 智能文本提取
- **空字符过滤**: 跳过 `\u0000` 字符
- **行尾修剪**: 自动 `trimEnd()` 每行
- **多行连接**: 使用 `\n` 连接多行
- **边界检查**: `coerceAtMost(cells.size - 1)`

```kotlin
private fun extractLineText(cells: List<TerminalCell>, startX: Int, endX: Int): String {
    val sb = StringBuilder()
    for (x in startX..endX.coerceAtMost(cells.size - 1)) {
        val cell = cells.getOrNull(x)
        if (cell != null && cell.char != '\u0000') {
            sb.append(cell.char)
        }
    }
    return sb.toString().trimEnd()
}
```

### 2. 反向选择支持
- 自动规范化起点和终点
- 确保 `(startX, startY)` 始终在 `(endX, endY)` 之前

```kotlin
private fun normalizeSelection(selection: TextSelection): Pair<Pair<Int, Int>, Pair<Int, Int>> {
    val (sx, sy, ex, ey) = selection.run {
        listOf(startX, startY, endX, endY)
    }
    
    return if (sy < ey || (sy == ey && sx <= ex)) {
        // 正向选择
        Pair(Pair(sx, sy), Pair(ex, ey))
    } else {
        // 反向选择：交换起点和终点
        Pair(Pair(ex, ey), Pair(sx, sy))
    }
}
```

### 3. 精确的像素到字符转换
```kotlin
val charWidthPx = charWidth.toPx()
val charHeightPx = charHeight.toPx()
val x = (offset.x / charWidthPx).toInt().coerceIn(0, terminalWidth - 1)
val y = (offset.y / charHeightPx).toInt().coerceIn(0, terminalHeight - 1)
```

### 4. 鼠标按钮区分
- **左键**: 选择文本、清除旧选择
- **右键**: 复制到剪贴板并清除选择
- 使用底层 `awaitPointerEventScope` API 确保准确检测

---

## 🐛 问题修复历史

### Issue 1: 右键复制不工作
**问题**: `detectTapGestures` 无法准确区分鼠标按钮

**解决方案**: 使用 `awaitPointerEventScope` + `event.buttons.isSecondaryPressed`

```kotlin
// ❌ 旧方法（不可靠）
detectTapGestures(
    onPress = { /* 无法区分左右键 */ }
)

// ✅ 新方法（可靠）
awaitPointerEventScope {
    val event = awaitPointerEvent()
    if (event.buttons.isSecondaryPressed) {
        // 右键处理
    }
}
```

### Issue 2: 复制后选择未清除
**问题**: 复制到剪贴板后，选择区域仍然高亮显示

**解决方案**: 在复制后立即调用 `viewModel.clearSelection()`

```kotlin
clipboardManager.setText(AnnotatedString(selectedText))
println("[TextSelection] ✅ Copied to clipboard: ${selectedText.take(50)}...")

// ✅ 复制后清除选择
viewModel.clearSelection()
println("[TextSelection] Selection cleared after copy")
```

### Issue 3: 左键点击未清除旧选择
**问题**: 点击（不拖拽）时，旧的选择没有被清除

**解决方案**: 区分"拖拽"和"点击"，点击时清除选择

```kotlin
var isDragging = false

onDrag = {
    isDragging = true  // 标记为真正的拖拽
}

onDragEnd = {
    if (isDragging) {
        // 拖拽：保存选择
    } else {
        // 点击：清除旧选择
        viewModel.clearSelection()
    }
}
```

---

## 📊 性能优化

### 1. 状态管理
- 使用 `mutableStateOf` 实现响应式更新
- 仅在选择变化时触发重绘
- 避免不必要的文本提取

### 2. 渲染优化
- 选择区域使用简单的 `drawRect`
- 半透明背景避免完全遮挡文本
- 与文本渲染分离，不影响性能

### 3. 内存优化
- 提取的文本仅在需要时生成
- 清除选择时释放文本引用
- 使用 `StringBuilder` 构建多行文本

---

## 🧪 测试场景

### 基本功能测试
- [x] 单行选择
- [x] 多行选择
- [x] 反向选择
- [x] 右键复制
- [x] 复制后自动清除
- [x] 左键点击清除
- [x] Ctrl+V 粘贴

### 边界情况测试
- [x] 选择空行
- [x] 选择包含特殊字符的文本
- [x] 选择包含 CJK 字符的文本
- [x] 选择包含 Emoji 的文本
- [x] 超出屏幕范围的选择

### 交互测试
- [x] 选择时滚动
- [x] 在不同行之间切换选择
- [x] 快速点击不触发选择
- [x] 拖拽后立即复制

---

## 💡 使用技巧

### 1. 快速复制整行
拖拽选择整行文本，然后右键复制

### 2. 复制多行命令
从第一行开始拖拽到最后一行，右键复制整个代码块

### 3. 清除不需要的选择
左键单击任意位置即可清除

### 4. 精确选择
拖拽时观察蓝色高亮，确保选中了正确的内容

---

## 🔮 未来改进

### 短期
- [ ] 添加双击选择单词
- [ ] 添加三击选择整行
- [ ] 支持 Shift+箭头键扩展选择

### 中期
- [ ] 添加复制时的视觉反馈（闪烁效果）
- [ ] 支持选择样式自定义（颜色、透明度）
- [ ] 添加右键菜单（复制、粘贴、清除）

### 长期
- [ ] 支持矩形选择（Alt+拖拽）
- [ ] 支持正则表达式搜索和选择
- [ ] 支持选择历史记录

---

## 📚 相关文档

- [PROJECT_STRUCTURE_AND_CODE_STYLE.md](PROJECT_STRUCTURE_AND_CODE_STYLE.md) - 代码规范
- [README.md](../README.md) - 项目总览

---

**实现完成日期**: 2025-11-14  
**状态**: ✅ 已完成并测试  
**功能**: 文本选择、右键复制、自动清除

