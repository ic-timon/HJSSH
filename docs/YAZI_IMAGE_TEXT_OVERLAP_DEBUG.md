# Yazi 图片文字覆盖问题调试

## 🐛 问题描述

用户报告：在 Yazi 中预览图片时，左侧文件列表的文字被覆盖/消失。

## 📊 当前状态（从日志分析）

### 数据层
```
[TerminalBuffer] Multi-chunk complete: size=288x288
[TerminalBuffer] Image placed: id=1, placement=81, pos=(103,1), size=0x0
```
- ✅ 图片数据完整（288x288px）
- ✅ 图片位置：第 103 列，第 1 行
- ⚠️ `size=0x0` 意味着 Yazi 没有指定列/行数（我们需要根据像素计算）

### 渲染层
```
[RenderCheck] Image covers 512 cells (32x16)
[RenderCheck] Drew 347 chars in left-side (skipped image-covered cells)
[ImageRender] Foreground image: id=81, pos=(103,1), size=288x288
```
- ✅ 图片占用计算正确：512 单元格 = 32列 x 16行
- ✅ 左侧文字已绘制：347 个字符
- ✅ 图片绘制位置：(103, 1)

### 坐标计算
- 终端宽度：163 列
- 字符宽度：9px
- 图片起始位置：103 列 = 103 * 9 = **927px**
- 图片大小：288px = 32 列
- 图片范围：**927px - 1215px**（列 103-135）
- 左侧文字区域：**0px - 918px**（列 0-102）

**理论上，图片和文字不应该重叠！**

## 🔍 可能的原因

### 1. Canvas BlendMode 问题
- 当前使用 `BlendMode.SrcOver`
- 可能导致图片绘制时清除了整个Canvas区域

### 2. 图片实际绘制位置错误
- 需要验证 `dstOffset` 是否真的是 (927, 18)
- 需要验证 `clipRect` 是否正确工作

### 3. Yazi 自身行为
- Yazi 可能在切换到图片预览时，故意清除了部分文字
- Yazi 可能发送了清屏命令（ED/EL）

### 4. Canvas 重绘机制
- Compose Canvas 每次重绘会清空整个区域
- 即使我们正确绘制了文字和图片，可能存在时序问题

## 📝 调试计划

### 步骤1：验证图片绘制坐标（进行中）
添加详细日志，输出：
- 图片的像素坐标范围
- Canvas 总大小
- 字符单元格大小

### 步骤2：检查 Yazi 是否发送清屏命令
监控：
- `CSI J` (Erase in Display)
- `CSI K` (Erase in Line)
- 图片放置前后的光标位置

### 步骤3：尝试不同的 BlendMode
测试：
- `BlendMode.SrcOver` (当前)
- `BlendMode.Plus`
- `BlendMode.Screen`

### 步骤4：尝试不使用 clipRect
直接绘制图片，看是否 `clipRect` 导致问题

## 💡 潜在解决方案

### 方案A：分层Canvas
- 使用多个 Canvas 分层
- 文字Canvas + 图片Canvas
- 确保文字层始终可见

### 方案B：智能区域刷新
- 只在必要时重绘特定区域
- 避免全局刷新

### 方案C：等待 Yazi 完成布局
- Yazi 可能需要时间重新布局文字
- 添加延迟或监听 Yazi 的布局完成信号

## 📌 待验证的假设

1. **假设**：图片绘制位置错误（从0开始而不是103）
   - **验证方法**：查看详细像素坐标日志
   
2. **假设**：Yazi 发送了清屏命令
   - **验证方法**：监控所有 ED/EL 命令

3. **假设**：Canvas BlendMode 导致覆盖
   - **验证方法**：尝试不同的 BlendMode

4. **假设**：Compose UI 重组时机问题
   - **验证方法**：添加 recomposition 计数器

---

**下一步**：等待详细坐标日志，确定图片的实际绘制位置。

