# Kitty Graphics Protocol - Bug修复记录

## 🐛 发现的严重Bug

### Bug #1: APC状态机错误

**问题位置**: `AnsiParser.kt` 第191行

**错误代码**:
```kotlin
if (state == ParseState.ESCAPE && apcData.isNotEmpty()) {
    // 从APC_KITTY状态来的，处理Kitty Graphics
    handleKittyGraphics(apcData.toString(), cursor, buffer)
}
```

**问题分析**:
- 在处理 `ESC \` (String Terminator) 时，代码检查 `state == ParseState.ESCAPE`
- 但此时已经在ESCAPE状态中，这个检查**总是true**
- 无法区分是从哪个状态（DCS/PM/APC/APC_KITTY）进入ESCAPE的
- 导致Kitty Graphics数据无法正确处理

**影响**:
- ❌ Kitty Graphics Protocol完全无法工作
- ❌ APC序列被错误地忽略
- ❌ `handleKittyGraphics`函数从未被正确调用

---

## ✅ 修复方案

### 添加状态记忆

**修改**: 添加 `previousState` 变量

```kotlin
private var state = ParseState.NORMAL
private var previousState = ParseState.NORMAL  // 保存前一个状态
```

### 状态切换时保存

当从任何状态进入ESCAPE时，保存当前状态：

```kotlin
'\u001b' -> {
    // 保存当前状态
    previousState = state
    state = ParseState.ESCAPE
}
```

### 检查前一个状态

在处理 `ESC \` 时，检查 `previousState`：

```kotlin
'\\' -> { // ST - String Terminator (ESC \)
    when (previousState) {
        ParseState.APC_KITTY -> {
            // Kitty Graphics命令结束
            println("      [AnsiParser] Kitty Graphics sequence ended (ESC \\), data length: ${apcData.length}")
            handleKittyGraphics(apcData.toString(), cursor, buffer)
            apcData.clear()
        }
        ParseState.DCS, ParseState.PM, ParseState.APC -> {
            println("      [AnsiParser] Graphics sequence ended (ESC \\)")
        }
        else -> {
            println("      [AnsiParser] String Terminator (ESC \\) from state: $previousState")
        }
    }
    state = ParseState.NORMAL
    previousState = ParseState.NORMAL
}
```

---

## 🔍 修复验证

### 修复前的行为

1. Yazi发送: `ESC _ G a=t,f=100;base64data ESC \`
2. Parser检测到 `ESC _` → 进入APC状态
3. Parser检测到 `G` → 进入APC_KITTY状态
4. 收集数据: `a=t,f=100;base64data`
5. Parser检测到 `ESC` → 进入ESCAPE状态
6. Parser检测到 `\` → 检查 `state == ESCAPE` (true)
7. **但无法知道来自APC_KITTY** → 数据被忽略 ❌

### 修复后的行为

1. Yazi发送: `ESC _ G a=t,f=100;base64data ESC \`
2. Parser检测到 `ESC _` → 进入APC状态
3. Parser检测到 `G` → 进入APC_KITTY状态
4. 收集数据: `a=t,f=100;base64data`
5. Parser检测到 `ESC` → **保存previousState=APC_KITTY**，进入ESCAPE状态
6. Parser检测到 `\` → 检查 `previousState == APC_KITTY` (true)
7. **调用handleKittyGraphics()** → 图片正确处理 ✅

---

## 🧪 测试建议

### 1. 检查日志输出

运行yazi后，应该看到：

```
[AnsiParser] APC (Kitty Graphics) sequence started
[AnsiParser] Kitty Graphics Protocol detected
[AnsiParser] Kitty Graphics sequence ended (ESC \), data length: XXXX
[KittyGraphics] Parsing command, data length: XXXX
[KittyGraphics] Transmit command: imageId=1, size=100x100
[TerminalBuffer] Image stored: id=1, size=100x100
```

### 2. 检查图标显示

**之前的问题**:
- 文件夹icon显示异常
- 文件icon显示异常

**可能原因**:
1. UTF-8 emoji没有正确显示
2. Nerd Font图标字符没有渲染
3. 字体不支持这些Unicode字符

**检查方法**:
```bash
# 在SSH会话中测试
echo "📁 📂 📄 🖼️"  # 测试emoji
echo "        "  # 测试Nerd Font icons（如果使用）
```

### 3. 验证Kitty协议

**简单测试**:
```bash
# 1x1红色像素PNG（base64）
printf '\033_Ga=t,f=100,s=1,v=1;iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==\033\\'
```

---

## 📊 相关问题

### 图标显示问题

**可能的原因**:

1. **字体问题**
   - 终端使用的字体不支持emoji/Nerd Font
   - 解决: 安装支持的字体（如FiraCode Nerd Font）

2. **字体回退**
   - Compose的字体渲染可能没有正确回退到emoji字体
   - 需要检查 `TextRenderer.kt`

3. **字符宽度计算**
   - Emoji可能被识别为1个字符宽度，但实际需要2个
   - 需要检查 `wcwidth` 计算

### 图片显示问题

**当前状态**:
- ✅ 协议解析 - 修复后应该工作
- ❌ Base64解码 - 还未实现
- ❌ PNG解码 - 还未实现
- ❌ 图片渲染 - 还未实现

---

## 📝 后续任务

1. **验证修复** ⏳
   - 运行yazi
   - 检查日志
   - 确认协议被正确识别

2. **图标问题诊断** ⏳
   - 检查使用的字体
   - 测试emoji渲染
   - 验证字符宽度

3. **完成图片功能** ⏳
   - 实现ImageDecoder
   - 添加TerminalView渲染
   - 测试实际图片显示

---

**创建日期**: 2025-11-14  
**修复状态**: ✅ 编译通过，待测试验证

