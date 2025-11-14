# Kitty Graphics Protocol - 最终修复

## 🔍 问题根源发现

### 日志分析
```
✓ [KittyGraphics] Query command: action=q, imageId=31
✓ [KittyGraphics] Sending Query response: OK (s=1)
✓ [TerminalViewModel] Sent response to SSH: ...
✗ [AnsiParser] DCS (Sixel) sequence started    ← Yazi还是用Sixel
```

### 真正的问题

**不是我们的实现有问题，而是TERM环境变量设置不对！**

```kotlin
// ❌ 错误的TERM设置
newSession.allocatePTY("xterm-256color", ...)
```

**Yazi的协议选择逻辑**：
1. 检查`TERM`环境变量
2. 如果TERM是`xterm-kitty`、`kitty`等 → 优先使用Kitty Graphics
3. 如果TERM是`xterm-256color` → 不使用Kitty Graphics，即使响应了Query也不信任
4. 回退到Sixel或Unicode blocks

---

## ✅ 解决方案

### 修改TERM环境变量

```kotlin
// ✅ 正确的TERM设置
newSession.allocatePTY(
    "xterm-kitty",  // 声明支持Kitty Graphics Protocol
    80, 24, 640, 384, emptyMap()
)
```

### 为什么这样有效？

1. **协议声明**：`xterm-kitty`是Kitty终端的官方TERM类型
2. **兼容性**：向下兼容xterm的所有功能
3. **自动识别**：Yazi等应用会自动启用Kitty Graphics Protocol
4. **无需Query响应**：Yazi直接使用Kitty协议，不需要等待Query响应

---

## 📊 完整的协议选择流程

### 修复前（TERM=xterm-256color）
```
Yazi启动
 ↓
检查TERM=xterm-256color
 ↓
发送Query（探测是否支持）
 ↓
收到我们的响应（s=1）
 ↓
但TERM不匹配，不信任响应
 ↓
回退到Sixel协议
 ↓
我们忽略Sixel数据
 ↓
❌ 图片不显示
```

### 修复后（TERM=xterm-kitty）
```
Yazi启动
 ↓
检查TERM=xterm-kitty
 ↓
✓ 直接使用Kitty Graphics Protocol
 ↓
发送Transmit命令（图片数据）
 ↓
我们解析、解码、存储
 ↓
UI渲染
 ↓
✅ 图片显示！
```

---

## 🧪 预期结果

运行Yazi后，应该看到：

```
[AnsiParser] Kitty Graphics Protocol detected
[KittyGraphics] Transmit command: imageId=1, size=100x100, format=PNG
[TerminalBuffer] addImage: id=1, format=PNG, size=100x100, dataLen=12345
[TerminalBuffer] Image stored: id=1
[TerminalBuffer] placeImage: imageId=1, placementId=1, pos=(10,5), size=5x3
[TerminalBuffer] ImagePlacement created: id=1
[TerminalView] Rendering 1 image placements
[TerminalView] Processing placement: id=1, imageId=1
[TerminalView] Image found: 100x100, format=PNG
[TerminalView] Bitmap decoded successfully: 100x100
[TerminalView] Drawing image at (90.0, 90.0), size: 45.0x54.0
```

**关键变化**：
- ✅ 不再有`DCS (Sixel) sequence started`
- ✅ 有大量`Transmit command`日志
- ✅ 有`addImage`和`placeImage`日志
- ✅ 有`Rendering`和`Drawing`日志

---

## 📝 完整实现回顾

### 1. 数据结构 ✅
- `TerminalImage` - 图片存储
- `ImagePlacement` - 图片放置
- `KittyGraphicsCommand` - 协议命令

### 2. 协议解析 ✅
- APC状态机
- Kitty协议解析器
- Query响应机制

### 3. 图片管理 ✅
- `TerminalBuffer.addImage()`
- `TerminalBuffer.placeImage()`
- 多块传输支持

### 4. 图片解码 ✅
- Base64解码
- PNG/JPEG解码
- RGB/RGBA原始像素

### 5. UI渲染 ✅
- `TerminalView`中绘制图片
- 正确的位置和大小计算
- 懒加载解码

### 6. **TERM设置** ✅ ← **关键修复**
- 从`xterm-256color`改为`xterm-kitty`
- 声明支持Kitty Graphics Protocol

---

## 🎯 测试步骤

1. **启动应用**（正在后台运行）
2. **连接SSH**: `ssh myserver`
3. **运行Yazi**: `yazi /root`
4. **观察**:
   - 文件夹和文件图标应该正常显示
   - 图片预览应该正确渲染
   - 日志中应该有完整的图片处理链路

---

## 🔧 备用方案（如果还不行）

### 方案A: 强制Yazi使用Kitty协议

编辑`~/.config/yazi/yazi.toml`:
```toml
[preview]
image_filter = "kitty"
```

### 方案B: 检查terminfo

某些系统可能没有`xterm-kitty`的terminfo定义：
```bash
# 检查是否存在
infocmp xterm-kitty

# 如果不存在，回退到xterm-256color并配置Yazi
```

### 方案C: 添加环境变量

在SSH会话中：
```bash
export TERM=xterm-kitty
yazi /root
```

---

## 📈 性能考虑

- ✅ 懒加载解码（仅在需要时解码）
- ✅ 日志优化（关闭verbose日志）
- ✅ Base64解码缓存
- ⏳ 大图片可能需要优化（待测试）

---

## 🎉 总结

**核心修复**：将TERM从`xterm-256color`改为`xterm-kitty`

**为什么之前不工作**：
- 我们的实现是正确的
- Query响应也是正确的
- 但Yazi不信任TERM=xterm-256color的终端
- 所以回退到Sixel协议

**现在应该工作了**：
- TERM=xterm-kitty声明了协议支持
- Yazi会直接使用Kitty Graphics
- 图片应该能正确显示！

---

**更新日期**: 2025-11-14  
**状态**: 🚀 关键修复已应用，等待测试验证

