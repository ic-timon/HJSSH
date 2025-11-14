# Yazi 图形显示修复文档

## 问题描述

在运行 `yazi` 命令时发现两个主要问题：

1. **乱码问题**：终端显示大量 "锟斤拷" 等乱码字符
2. **图片显示问题**：图片预览是"一格一格"的，24位真彩色（TrueColor）没有正确显示

## 根本原因分析

### 1. UTF-8 解码问题

**位置**：`SshConnectionImpl.kt` 第 299 行

```kotlin
val data = String(buffer, 0, bytesRead)  // ❌ 使用平台默认编码
```

**问题**：
- Yazi 在发送图片预览数据时，可能包含图形协议的二进制数据（Sixel、Kitty Graphics Protocol等）
- 使用平台默认编码构造字符串，导致二进制数据被错误解析为文本
- 产生 "锟斤拷" 等典型的 UTF-8 解码错误字符

### 2. 图形协议未处理

**位置**：`AnsiParser.kt` 缺少对图形协议的支持

**问题**：
- `ESC P ... ESC \` (DCS - Device Control String, 用于 Sixel)
- `ESC _ ... ESC \` (APC - Application Program Command, 用于 Kitty Graphics)
- `ESC ^ ... ESC \` (PM - Privacy Message)

这些序列中的图形数据被当作普通文本处理，导致乱码。

### 3. TrueColor 渲染错误

**位置**：`TerminalView.kt` 第 358-361 行

```kotlin
is TerminalColor.TrueColor -> {
    val rValue = (this.r / 255f).coerceIn(0f, 1f)  // ❌ 整数除法错误
    val gValue = (this.g / 255f).coerceIn(0f, 1f)
    val bValue = (this.b / 255f).coerceIn(0f, 1f)
    Color(red = rValue, green = gValue, blue = bValue)
}
```

**问题**：
- `this.r` 是 `Int` 类型
- `this.r / 255f` 会先执行整数除法（结果几乎总是 0），然后再转 float
- 导致所有 TrueColor 都变成黑色或接近黑色
- Yazi 的图片预览使用每个字符的背景色（24位RGB）来模拟像素，颜色错误导致图片无法正确显示

## 修复方案

### 修复 1：显式使用 UTF-8 解码，并优雅处理无效字节

**文件**：`composeApp/src/jvmMain/kotlin/cn/hjhw/ssh/connection/SshConnectionImpl.kt`

```kotlin
override fun observeOutput(): Flow<String> = flow {
    val inputStream = shell?.inputStream ?: return@flow
    val buffer = ByteArray(8192)
    val charset = Charsets.UTF_8
    val decoder = charset.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE)

    while (isConnected) {
        val bytesRead = withContext(Dispatchers.IO) {
            inputStream.read(buffer)
        }

        if (bytesRead > 0) {
            // 使用UTF-8显式解码，并替换无效字节
            val byteBuffer = java.nio.ByteBuffer.wrap(buffer, 0, bytesRead)
            val charBuffer = decoder.decode(byteBuffer)
            val data = charBuffer.toString()
            emit(data)
        } else if (bytesRead == -1) {
            // 流已关闭
            break
        }
    }
}.flowOn(Dispatchers.IO)
```

**改进**：
- 显式使用 `Charsets.UTF_8`
- 配置 `CharsetDecoder` 遇到无效字节时用替换字符代替（而不是抛出异常）
- 这样即使遇到二进制数据，也不会产生乱码

### 修复 2：支持图形协议序列

**文件**：`composeApp/src/commonMain/kotlin/cn/hjhw/ssh/terminal/AnsiParser.kt`

#### 2.1 添加新的解析状态

```kotlin
private enum class ParseState {
    NORMAL,
    ESCAPE,
    CSI, // Control Sequence Introducer: ESC [
    OSC, // Operating System Command: ESC ]
    PARAMETER,
    CHARSET, // Character Set Selection: ESC ( or ESC )
    DCS, // Device Control String: ESC P (用于Sixel等)
    PM, // Privacy Message: ESC ^
    APC, // Application Program Command: ESC _
}
```

#### 2.2 处理图形协议开始序列

在 `ParseState.ESCAPE` 分支中添加：

```kotlin
'P' -> { // DCS - Device Control String (Sixel等图形协议)
    println("      [AnsiParser] DCS (Sixel) sequence started, ignoring graphics data")
    state = ParseState.DCS
}
'^' -> { // PM - Privacy Message
    state = ParseState.PM
}
'_' -> { // APC - Application Program Command (Kitty图形协议等)
    println("      [AnsiParser] APC (Kitty Graphics) sequence started, ignoring graphics data")
    state = ParseState.APC
}
```

#### 2.3 处理图形协议结束序列

添加 `ESC \` (String Terminator) 处理：

```kotlin
'\\' -> { // ST - String Terminator (ESC \)
    println("      [AnsiParser] Graphics sequence ended (ESC \\)")
    state = ParseState.NORMAL
}
```

#### 2.4 忽略图形数据

在主解析循环中添加：

```kotlin
ParseState.DCS, ParseState.PM, ParseState.APC -> {
    // DCS/PM/APC 模式：忽略所有内容直到遇到 ESC \ (String Terminator)
    when (char) {
        '\u001b' -> {
            // 遇到ESC，切换到ESCAPE状态以检测是否是 ESC \
            state = ParseState.ESCAPE
        }
        '\u009c' -> {
            // ST (String Terminator) 的单字节形式（C1控制码）
            println("      [AnsiParser] Graphics sequence ended (ST)")
            state = ParseState.NORMAL
        }
        // 否则忽略所有字符（图形数据/二进制数据）
    }
}
```

### 修复 3：正确渲染 TrueColor

**文件**：`composeApp/src/commonMain/kotlin/cn/hjhw/ssh/ui/TerminalView.kt`

```kotlin
is TerminalColor.TrueColor -> {
    // 直接使用RGB整数值（0-255）创建颜色
    // 确保值在有效范围内
    val rValue = this.r.coerceIn(0, 255)
    val gValue = this.g.coerceIn(0, 255)
    val bValue = this.b.coerceIn(0, 255)
    Color(red = rValue, green = gValue, blue = bValue, alpha = 255)
}
```

**改进**：
- 直接使用整数值（0-255）调用 `Color()` 构造函数
- 不再进行错误的除法操作
- `coerceIn(0, 255)` 确保值在有效范围内

## 技术细节

### Yazi 的图片预览原理

Yazi 使用两种方式显示图片：

1. **Unicode 块字符 + TrueColor**
   - 使用 Unicode 块字符（▀▄█等）
   - 每个字符的**前景色**和**背景色**都设置为图片的 RGB 值
   - 通过 `ESC[38;2;R;G;Bm` (前景) 和 `ESC[48;2;R;G;Bm` (背景) 设置
   - 每个字符代表 1-2 个像素

2. **图形协议**（可选）
   - **Sixel**: `ESC P ... ESC \`
   - **Kitty Graphics Protocol**: `ESC _ ... ESC \`
   - **iTerm2 Inline Images**: `ESC ] 1337 ; File=... BEL`

### 24位真彩色 (TrueColor) ANSI 序列

格式：
- 前景色：`ESC[38;2;<R>;<G>;<B>m`
- 背景色：`ESC[48;2;<R>;<G>;<B>m`

其中 R, G, B 的值范围是 0-255。

例如：
```
ESC[38;2;255;128;0m  → 设置前景色为橙色 (255, 128, 0)
ESC[48;2;0;128;255m  → 设置背景色为天蓝色 (0, 128, 255)
```

## 测试验证

### 手动测试步骤

1. 连接到服务器：`ssh myserver`
2. 准备测试图片：确保 `/home/testuser/pic.jpg` 存在
3. 运行 yazi：`yazi /root`
4. 导航到 `pic.jpg`
5. 观察预览面板：
   - ✅ 应该显示彩色图片
   - ✅ 没有乱码字符
   - ✅ 颜色渐变平滑

### 验证 TrueColor 支持

在终端中执行：

```bash
printf '\e[38;2;255;0;0mRed\e[0m '
printf '\e[38;2;0;255;0mGreen\e[0m '
printf '\e[38;2;0;0;255mBlue\e[0m\n'
```

应该看到红、绿、蓝三个颜色的文字。

## 相关文档

- [NANO_COMPLETE_FIX.md](./NANO_COMPLETE_FIX.md) - nano 编辑器修复
- [TERMINAL_COMPATIBILITY_TEST_PLAN.md](./TERMINAL_COMPATIBILITY_TEST_PLAN.md) - 兼容性测试计划
- [YAZI_IMAGE_PREVIEW_TEST.md](./YAZI_IMAGE_PREVIEW_TEST.md) - Yazi 图片预览测试

## 修复日期

2025-11-14

## 状态

✅ **已修复** - 编译通过，待实际测试验证

