# Kitty Graphics Protocol - 诊断指南

## 🔍 当前问题分析

根据最新日志：
```
[AnsiParser] Kitty Graphics Protocol detected          ✓
[KittyGraphics] Query command (not implemented yet)    ⚠️
[AnsiParser] DCS (Sixel) sequence started              ⚠️
```

**诊断结果**：
- ✓ Kitty协议被正确识别
- ✗ **只收到Query命令，没有Transmit命令**
- ✗ **Yazi切换到Sixel协议了**

---

## 🎯 原因分析

### 为什么没有收到图片数据？

1. **Query命令的作用**
   - Yazi启动时发送Query命令查询终端能力
   - 如果终端响应支持Kitty Graphics，Yazi会使用Kitty协议发送图片
   - 如果没有响应，Yazi会回退到其他协议（Sixel、iTerm2或Unicode blocks）

2. **当前情况**
   - Yazi发送了Query (`a=q`)
   - 我们的终端没有响应
   - Yazi认为不支持Kitty Graphics
   - Yazi切换到Sixel协议

3. **为什么看到Sixel？**
   - 日志显示：`[AnsiParser] DCS (Sixel) sequence started`
   - Sixel是另一种图形协议，我们目前忽略了它
   - 这就是为什么图标和图片显示不正常的原因

---

## 📋 完整日志追踪链路

我已经添加了详细日志，现在运行Yazi时应该看到：

### 1. 协议检测阶段
```
[AnsiParser] Kitty Graphics Protocol detected
[AnsiParser] Kitty Graphics sequence ended (ESC \), data length: XXX
[KittyGraphics] Parsing command, data length: XXX
```

### 2. 命令处理阶段
```
[KittyGraphics] Query command: mode=X        # 查询命令
[KittyGraphics] Transmit command: ...        # 传输命令（目前没有）
[KittyGraphics] Display command: ...         # 显示命令（目前没有）
```

### 3. 图片存储阶段
```
[TerminalBuffer] addImage: id=X, format=PNG, size=100x100, dataLen=1234
[TerminalBuffer] Image stored: id=X
```

### 4. 图片放置阶段
```
[TerminalBuffer] placeImage: imageId=X, placementId=Y, pos=(10,5), size=5x3
[TerminalBuffer] ImagePlacement created: ...
```

### 5. UI渲染阶段
```
[TerminalView] Rendering N image placements
[TerminalView] Processing placement: id=Y, imageId=X
[TerminalView] Image found: 100x100, format=PNG
[TerminalView] Bitmap decoded successfully: 100x100
[TerminalView] Drawing image at (90.0, 90.0), size: 45.0x54.0
```

---

## 🛠️ 解决方案

### 方案A: 配置Yazi强制使用Kitty协议

编辑Yazi配置文件 `~/.config/yazi/yazi.toml`:

```toml
[preview]
# 强制使用Kitty Graphics Protocol
image_filter = "kitty"

# 或者设置优先级
# image_filter = "auto"  # 自动选择
```

然后重启Yazi测试。

---

### 方案B: 实现Query响应（推荐）

当前我们的代码可以识别Query，但没有发送响应。需要：

1. **在AnsiParser中保存SSH连接引用**
2. **收到Query时发送响应**：
   ```kotlin
   // 响应格式: ESC _ G a=q,s=1; ESC \
   // s=1 表示支持
   sshConnection.write("\u001b_Ga=q,s=1;\u001b\\")
   ```

但这需要重构代码架构，将SSH连接传递到Parser。

---

### 方案C: 同时支持Sixel协议

如果Yazi使用Sixel，我们可以：

1. **解析Sixel命令** (`ESC P ... ESC \`)
2. **解码Sixel图形数据**
3. **转换为ImageBitmap渲染**

但Sixel实现比Kitty协议复杂得多。

---

## 🧪 测试步骤

### 步骤1: 运行Yazi并查看日志

```bash
ssh myserver
yazi /root
```

### 步骤2: 检查日志输出

**场景A: 如果看到Transmit命令**
```
[KittyGraphics] Transmit command: imageId=1, size=100x100
[TerminalBuffer] addImage: id=1, ...
[TerminalBuffer] placeImage: ...
[TerminalView] Drawing image at ...
```
→ **成功！图片应该显示**

**场景B: 如果只看到Query命令**
```
[KittyGraphics] Query command (not implemented yet)
[AnsiParser] DCS (Sixel) sequence started
```
→ **Yazi使用Sixel协议** → 需要方案A或B

**场景C: 如果什么都没看到**
```
（无Kitty相关日志）
```
→ **Yazi使用Unicode blocks** → 查看Yazi配置

---

## 📊 当前支持矩阵

| 协议 | 识别 | 解析 | 存储 | 渲染 | 状态 |
|------|------|------|------|------|------|
| Kitty Graphics | ✅ | ✅ | ✅ | ✅ | ⏳ 等待Transmit命令 |
| Sixel | ✅ | ❌ | ❌ | ❌ | ⏳ 被忽略 |
| iTerm2 Inline | ✅ | ❌ | ❌ | ❌ | ⏳ 被忽略 |
| Unicode Blocks | N/A | N/A | N/A | ✅ | ✅ 文本渲染 |

---

## 💡 快速诊断命令

### 检查Yazi配置
```bash
cat ~/.config/yazi/yazi.toml | grep image
```

### 手动测试Kitty协议
在终端中发送测试图片：
```bash
# 1x1红色像素PNG
printf '\033_Ga=T,f=100,s=1,v=1,c=5,r=3;iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==\033\\'
```

**预期日志**:
```
[KittyGraphics] Transmit command: imageId=0, size=1x1
[TerminalBuffer] addImage: id=0, format=PNG, size=1x1
[TerminalBuffer] placeImage: imageId=0, pos=(0,0), size=5x3
[TerminalView] Rendering 1 image placements
[TerminalView] Drawing image at (0.0, 0.0)
```

如果看到这些日志，说明**我们的实现完全正常**，问题在于Yazi没有发送Transmit命令。

---

## 🔍 下一步行动

### 如果看到完整的日志链路（包括Transmit）
→ 检查图片是否显示
→ 如果不显示，问题在UI渲染层

### 如果只看到Query
→ **优先方案**: 配置Yazi强制使用Kitty协议（方案A）
→ **备选方案**: 实现Query响应（方案B，需要重构）
→ **长期方案**: 支持Sixel协议（方案C，工作量大）

---

## 📝 请测试并反馈

请运行Yazi，然后告诉我：

1. **是否看到`[KittyGraphics] Transmit command`？**
2. **是否看到`[TerminalBuffer] addImage`？**
3. **是否看到`[TerminalView] Rendering`？**
4. **图片/图标是否显示？**

根据你的反馈，我会采取相应的修复措施！

---

**更新日期**: 2025-11-14  
**应用状态**: 🚀 已启动，等待测试

