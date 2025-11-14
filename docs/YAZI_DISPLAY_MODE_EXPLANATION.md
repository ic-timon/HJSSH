# Yazi 图片显示模式说明

## 📊 当前状态

✅ **TrueColor 颜色已正确支持**  
✅ **Yazi 正在使用Unicode块字符模式显示图片**

---

## 🖼️ Yazi的三种图片预览模式

### 1. Unicode 半块字符 + TrueColor（当前使用✅）

**原理**：
- 使用Unicode块字符（▀上半块, ▄下半块, █全块等）
- 每个字符的前景色和背景色都是图片的RGB颜色
- 每个字符代表 **2个垂直像素**

**特点**：
- ✅ 最兼容（所有支持TrueColor的终端都能用）
- ✅ 不需要特殊协议支持
- ❌ 分辨率较低（每个字符约9x18像素，但只显示2个像素）
- ❌ 看起来"一格一格"

**示例**：
```
ESC[38;2;255;0;0;48;2;0;0;255m▀  ← 前景红色，背景蓝色
```
显示效果：上半格红色，下半格蓝色

---

### 2. Sixel图形协议（未检测到）

**原理**：
- 专门的位图图形协议
- 序列：`ESC P q ... ESC \`
- 可以显示完整的像素级图像

**支持的终端**：
- xterm (with sixel)
- mlterm
- mintty
- wezterm

**检测**：
- 终端需要报告支持Sixel
- 环境变量 `$TERM` 或响应 `DA1/DA2` 查询

---

### 3. Kitty Graphics Protocol（未检测到）

**原理**：
- 最现代的图形协议
- 序列：`ESC _ G ... ESC \`
- 支持PNG/JPEG直接传输

**支持的终端**：
- Kitty
- WezTerm
- foot

---

### 4. iTerm2 Inline Images（未检测到）

**原理**：
- iTerm2专有协议
- 序列：`ESC ] 1337 ; File=... BEL`
- Base64编码的图片数据

**支持的终端**：
- iTerm2（macOS）
- WezTerm（部分支持）

---

## 🔧 当前实现状态

| 协议 | 解析支持 | 显示支持 | 状态 |
|------|---------|---------|------|
| Unicode半块 + TrueColor | ✅ | ✅ | 正在使用 |
| Sixel (ESC P) | ✅ | ❌ | 仅忽略数据 |
| Kitty (ESC _) | ✅ | ❌ | 仅忽略数据 |
| iTerm2 (OSC 1337) | ✅ | ❌ | 仅忽略数据 |

---

## 🎯 为什么看起来"一格一格"？

这是**正常现象**！

当Yazi检测到终端不支持高级图形协议时，会自动降级到Unicode块字符模式。

### 示例对比

**高分辨率图形协议（Sixel/Kitty）**：
```
每个像素都是独立的
图片清晰，像真正的图片
```

**Unicode块字符模式（当前）**：
```
█████░░░░░
███████░░░
█████████░
每个"块"是一个终端字符
看起来"一格一格"
```

---

## 🚀 如何启用高质量图片预览？

### 方案1：切换到支持Sixel的终端

**Windows推荐**：
- **WezTerm** - 支持Sixel和Kitty
  - 下载：https://wezfurlong.org/wezterm/
- **Windows Terminal** + VT100 - 部分支持

**配置**：
```toml
# ~/.config/yazi/yazi.toml
[preview]
image_filter = "sixel"  # 优先使用Sixel
```

---

### 方案2：支持Kitty Graphics Protocol

**推荐终端**：
- **Kitty**（Linux/macOS）
- **WezTerm**（全平台）
- **foot**（Wayland）

**配置**：
```toml
# ~/.config/yazi/yazi.toml
[preview]
image_filter = "kitty"
```

---

### 方案3：实现图形协议显示（需要开发）

如果要在当前终端中支持高质量预览，需要实现：

**Sixel渲染器**：
1. 解析Sixel数据（已完成✅）
2. 解码图像数据
3. 渲染到TerminalBuffer
4. 显示位图而不是字符

**难度**: ⭐⭐⭐⭐ (需要图像处理库)

---

## 📝 当前终端的优势

虽然不支持高级图形协议，但我们的终端有这些优势：

✅ **完整的TrueColor支持** - 16,777,216种颜色  
✅ **Unicode字符支持** - 所有块字符正确显示  
✅ **Nano/Vi完美支持** - 文本编辑器完全正常  
✅ **良好的性能** - 不需要处理大量图像数据  

---

## 🎨 优化建议

### 提高Unicode预览质量

1. **减小终端字体大小** - 更多字符 = 更高"分辨率"
2. **使用等宽字体** - 确保字符对齐
3. **增加终端尺寸** - 更多空间显示图片

### Yazi配置优化

```toml
# ~/.config/yazi/yazi.toml
[preview]
max_width = 80
max_height = 40
# 如果图片太大，会自动调整
```

---

## 🧪 验证当前显示

运行以下测试：

```bash
# 测试TrueColor支持
printf '\e[38;2;255;0;0m●\e[0m'  # 红色圆点
printf '\e[48;2;0;255;0m \e[0m'  # 绿色背景
printf '\e[48;2;0;0;255m \e[0m'  # 蓝色背景
echo

# 测试块字符
printf '▀▄█▌▐░▒▓'
echo

# 测试组合
printf '\e[38;2;255;0;0;48;2;0;0;255m▀\e[0m'  # 上红下蓝
```

如果这些都正常显示彩色，说明当前的实现是**完全正确**的！

---

## 📊 总结

| 问题 | 状态 | 说明 |
|------|------|------|
| 颜色不对 | ✅ 已修复 | TrueColor正常工作 |
| 乱码 | ✅ 已修复 | UTF-8解码正确 |
| "一格一格" | ⚠️ 正常现象 | Unicode块字符的特性 |

**结论**：当前实现是**正确的**！Yazi正在使用最兼容的显示模式。如果需要更高质量的预览，建议切换到支持Sixel/Kitty的终端。

---

## 📚 相关文档

- [YAZI_GRAPHICS_FIX.md](./YAZI_GRAPHICS_FIX.md) - 乱码和颜色修复
- [iTerm2 Images Protocol](https://iterm2.com/documentation-images.html)
- [Sixel Graphics](https://en.wikipedia.org/wiki/Sixel)
- [Kitty Graphics Protocol](https://sw.kovidgoyal.net/kitty/graphics-protocol/)

---

**创建日期**: 2025-11-14  
**最后更新**: 2025-11-14

