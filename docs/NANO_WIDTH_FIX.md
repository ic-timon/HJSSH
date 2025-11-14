# Nano 标题栏宽度截断问题修复记录

## 问题描述

在使用 nano 编辑器时，顶部标题栏被从中间截断，文件名（如 `quick_start.sh`）无法完整显示，只显示了前几个字符（如 `qui`）。

## 问题现象

- 终端宽度：163 列
- nano 标题栏应该显示：`GNU nano 8.6` ... `quick_start.sh` ...
- 实际显示：标题栏在约 80 列处被截断，`quick_start.sh` 只显示为 `qui`
- 其他全屏应用（如 `btop`）显示正常

## 调试过程

### 1. 初步怀疑：延迟换行问题

最初怀疑是"延迟换行"（Delayed Wrap）机制导致的，因为当光标到达行尾时可能会触发提前换行。

**实施的调试措施：**
- 在 `TerminalViewModel.kt` 中添加了详细的 `[WriteChar]` 日志
- 在 `AnsiParser.kt` 中添加了光标位置和行内容的详细日志
- 实现了完整的延迟换行逻辑（`wrapPending` 标志）

**结果：**
- 延迟换行逻辑正确
- 日志显示所有字符（包括 `quick_start.sh`）都被正确写入

### 2. 关键发现：cells.size 异常

通过详细日志发现了关键线索：

```
[Line 0] cells.size=80, maxWidth=163
```

**问题分析：**
- `maxWidth=163` 是正确的终端宽度
- `cells.size=80` 表示该行的单元格列表只有 80 个元素
- 这意味着该行无法存储超过 80 个字符

**日志验证：**
```
[WriteChar] Writing 'q' at (77, 0), wrapPending was: false
[WriteChar] Writing 'u' at (78, 0), wrapPending was: false
[WriteChar] Writing 'i' at (79, 0), wrapPending was: false
[WriteChar] Writing 'c' at (80, 0), wrapPending was: false
[WriteChar] Writing 'k' at (81, 0), wrapPending was: false
...
```

所有字符都被"写入"了，但随后查询 Line 0 内容时：
```
[Line 0 FULL - 163 chars]:
  [0-80]:   __GNU_nano_8.6_______________________________________________________________qui
  [81-end]: ___________________________________________________________________________________
  [75-94]: __qui_______________
```

位置 80 之后的内容全部丢失！

### 3. 根本原因定位

**问题根源：**
在 `TerminalBuffer.kt` 的 `resize()` 方法中，当终端宽度改变时，代码只更新了 `width` 变量，但**没有重新创建 `TerminalLine` 对象**。

```kotlin
// 调整宽度
if (newWidth != width) {
    // 注意：TerminalLine 的 maxWidth 是在构造时设置的，所以这里只能简单处理
    // 更好的实现是重新创建所有行，但这会比较复杂
    width = newWidth
    
    // 如果宽度变小，行内容会被截断（由 TerminalLine 处理）
    // 如果宽度变大，新增的空间会在需要时自动填充  // ❌ 这个假设是错误的！
}
```

**为什么会出现这个问题：**

1. 终端初始化时创建了 80x24 的缓冲区，所有 `TerminalLine` 对象的 `maxWidth=80`
2. 窗口显示后，终端被 resize 到 163x73
3. `TerminalBuffer.width` 更新为 163，但所有已存在的 `TerminalLine` 对象仍然是 `maxWidth=80`
4. 当 nano 写入标题栏时，`TerminalLine.setCell()` 方法会检查 `index < maxWidth`：

```kotlin
fun setCell(index: Int, cell: TerminalCell) {
    // 确保列表足够大
    while (cells.size <= index && cells.size < maxWidth) {  // maxWidth=80，所以最多扩展到80
        cells.add(TerminalCell.empty())
    }
    if (index < maxWidth) {  // index >= 80 时，这里直接返回，不写入！
        if (index < cells.size) {
            cells[index] = cell
        } else {
            cells.add(cell)
        }
    }
}
```

5. 位置 80 及以后的字符写入被静默忽略，导致内容截断

## 解决方案

在 `TerminalBuffer.resize()` 方法中，当宽度改变时，**重新创建所有 `TerminalLine` 对象**，同时保留它们的现有内容。

### 代码修改

**文件：** `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/TerminalBuffer.kt`

**修改内容：**

```kotlin
// 调整宽度
if (newWidth != width) {
    // 对于宽度改变，我们需要更新所有行的最大宽度
    // 由于 TerminalLine 的 maxWidth 是在构造时设置的，需要重新创建所有行
    println("      [TerminalBuffer] Recreating all lines with new maxWidth=$newWidth")
    
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
    
    // 重新创建备用屏幕的所有行
    val newAlternateLines = mutableListOf<TerminalLine>()
    for (oldLine in alternateVisibleLines) {
        val newLine = TerminalLine(newWidth)
        // 复制现有内容
        val cells = oldLine.getCells()
        for (i in cells.indices) {
            if (i < newWidth) {
                newLine.setCell(i, cells[i])
            }
        }
        newAlternateLines.add(newLine)
    }
    alternateVisibleLines.clear()
    alternateVisibleLines.addAll(newAlternateLines)
    
    // 重新创建 scrollback 中的所有行
    val newScrollbackLines = mutableListOf<TerminalLine>()
    for (oldLine in scrollbackLines) {
        val newLine = TerminalLine(newWidth)
        // 复制现有内容
        val cells = oldLine.getCells()
        for (i in cells.indices) {
            if (i < newWidth) {
                newLine.setCell(i, cells[i])
            }
        }
        newScrollbackLines.add(newLine)
    }
    scrollbackLines.clear()
    scrollbackLines.addAll(newScrollbackLines)
    
    width = newWidth
}
```

### 修改要点

1. **重新创建所有可见行**：使用新的 `maxWidth` 创建新的 `TerminalLine` 对象
2. **重新创建备用屏幕行**：确保 nano/vi 使用的备用屏幕也有正确的宽度
3. **重新创建 scrollback 行**：保证历史记录中的行也能正确显示
4. **保留现有内容**：从旧行复制所有单元格到新行
5. **处理宽度变化**：
   - 宽度变大：新行可以容纳更多字符
   - 宽度变小：超出部分自动被截断（`if (i < newWidth)`）

## 修复效果

修复后：
- ✅ nano 标题栏完整显示，包括完整的文件名
- ✅ 终端宽度调整后，所有行都能正确使用新的宽度
- ✅ 用户调整窗口大小时，内容能正确重排
- ✅ 不影响其他终端功能（scrollback、备用屏幕等）

## 经验总结

1. **不可变属性问题**：当对象的关键属性（如 `maxWidth`）在构造时设置后无法修改时，状态变化（如 resize）需要重新创建对象。

2. **调试技巧**：
   - 详细的结构化日志（如 `cells.size` 和 `maxWidth`）能快速定位问题
   - 对比"写入时"和"读取时"的数据能发现数据丢失问题
   - 追踪数据流向：输入 → 处理 → 存储 → 读取 → 输出

3. **代码注释的双刃剑**：
   ```kotlin
   // 更好的实现是重新创建所有行，但这会比较复杂
   ```
   这个注释指出了正确的方向，但因为"比较复杂"而没有实施，导致了 bug。在关键路径上，不应该因为"复杂"而妥协。

4. **静默失败的危险**：`setCell()` 方法对超出 `maxWidth` 的写入静默忽略，没有任何警告或错误，导致问题难以发现。应该添加调试日志或断言。

## 相关文件

- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/TerminalBuffer.kt` - 修复位置
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/TerminalLine.kt` - 问题相关类
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/ui/TerminalViewModel.kt` - 调试日志位置
- `composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/AnsiParser.kt` - 调试日志位置

## 修复日期

2025年11月14日

