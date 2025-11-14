# Kitty Graphics Protocol - 颜色映射修复

## 🐛 问题诊断

### 问题：颜色通道反转（橙色→蓝色）

**用户报告**：
```
图片的颜色不对，橙色的部分变蓝了
```

**根本原因**：RGB vs BGRA 颜色通道顺序不匹配

---

## 🔍 技术分析

### Kitty Graphics Protocol 规范
- RGB 格式：每像素3字节，顺序为 `R, G, B`
- RGBA 格式：每像素4字节，顺序为 `R, G, B, A`

### Skia ImageInfo.makeS32() 行为
- `makeS32()` 在大多数平台上使用 **BGRA** 格式（不是RGBA！）
- 字节顺序：`B, G, R, A`

### 问题表现
```
输入（Kitty RGB）: [255, 128, 0]  ← 橙色 (R=255, G=128, B=0)
我们的代码:      [255, 128, 0, 255]  ← 直接添加Alpha
Skia解释为BGRA:  B=255, G=128, R=0  ← 蓝色！
结果:             橙色变成了蓝色
```

---

## ✅ 修复方案

### RGB 格式处理

**修复前**：
```kotlin
val rgbaBytes = ByteArray(width * height * 4)
for (i in 0 until (width * height)) {
    rgbaBytes[i * 4] = pixelBytes[i * 3]         // R
    rgbaBytes[i * 4 + 1] = pixelBytes[i * 3 + 1] // G
    rgbaBytes[i * 4 + 2] = pixelBytes[i * 3 + 2] // B
    rgbaBytes[i * 4 + 3] = 0xFF.toByte()          // A
}
val imageInfo = ImageInfo.makeS32(width, height, ColorAlphaType.PREMUL)
SkiaImage.makeRaster(imageInfo, rgbaBytes, width * 4)
```

**修复后**：
```kotlin
val bgraBytes = ByteArray(width * height * 4)
for (i in 0 until (width * height)) {
    bgraBytes[i * 4] = pixelBytes[i * 3 + 2]     // B ← 交换！
    bgraBytes[i * 4 + 1] = pixelBytes[i * 3 + 1] // G (不变)
    bgraBytes[i * 4 + 2] = pixelBytes[i * 3]     // R ← 交换！
    bgraBytes[i * 4 + 3] = 0xFF.toByte()          // A
}
val imageInfo = ImageInfo.makeS32(width, height, ColorAlphaType.PREMUL)
SkiaImage.makeRaster(imageInfo, bgraBytes, width * 4)
```

### RGBA 格式处理

**修复前**：
```kotlin
// 直接使用RGBA数据（错误！）
val imageInfo = ImageInfo.makeS32(width, height, ColorAlphaType.PREMUL)
SkiaImage.makeRaster(imageInfo, pixelBytes, width * 4)
```

**修复后**：
```kotlin
// 转换RGBA到BGRA
val bgraBytes = ByteArray(width * height * 4)
for (i in 0 until (width * height)) {
    bgraBytes[i * 4] = pixelBytes[i * 4 + 2]     // B ← 交换！
    bgraBytes[i * 4 + 1] = pixelBytes[i * 4 + 1] // G (不变)
    bgraBytes[i * 4 + 2] = pixelBytes[i * 4]     // R ← 交换！
    bgraBytes[i * 4 + 3] = pixelBytes[i * 4 + 3] // A
}
val imageInfo = ImageInfo.makeS32(width, height, ColorAlphaType.PREMUL)
SkiaImage.makeRaster(imageInfo, bgraBytes, width * 4)
```

---

## 🎨 颜色转换示例

| 原始颜色 | Kitty RGB | 修复前（错误） | 修复后（正确） |
|---------|----------|--------------|--------------|
| 橙色     | `255,128,0` | `0,128,255` (蓝色) | `255,128,0` (橙色) ✓ |
| 红色     | `255,0,0`   | `0,0,255` (蓝色)   | `255,0,0` (红色) ✓ |
| 蓝色     | `0,0,255`   | `255,0,0` (红色)   | `0,0,255` (蓝色) ✓ |
| 绿色     | `0,255,0`   | `0,255,0` (绿色)   | `0,255,0` (绿色) ✓ |

---

## 📊 数据流（修复后）

```
Yazi发送RGB数据:
  [R, G, B] = [255, 128, 0]  ← 橙色
    ↓
ImageDecoder.jvm.kt 转换:
  [B, G, R, A] = [0, 128, 255, 255]  ← 交换R和B通道
    ↓
Skia ImageInfo.makeS32():
  解释为BGRA格式
  B=0, G=128, R=255, A=255
    ↓
最终渲染:
  RGB(255, 128, 0) = 橙色 ✓
```

---

## ✅ 修复文件

**文件**: `composeApp/src/jvmMain/kotlin/cn/hjhw/ssh/terminal/ImageDecoder.jvm.kt`

**修改内容**：
1. RGB格式：添加R↔B通道交换
2. RGBA格式：添加R↔B通道交换
3. 变量重命名：`rgbaBytes` → `bgraBytes` (更准确的命名)

---

## 🧪 测试验证

1. 启动应用并连接SSH
2. 运行 `yazi /root`
3. 导航到有彩色图片的目录
4. **验证**：
   - ✓ 橙色显示为橙色（不是蓝色）
   - ✓ 红色显示为红色（不是蓝色）
   - ✓ 蓝色显示为蓝色（不是红色）
   - ✓ 绿色保持绿色
   - ✓ 所有颜色准确还原

---

## 📝 技术备注

### 为什么不使用其他ColorType？

Skia提供了多种ColorType选项：
- `ColorType.RGBA_8888`: 期望RGBA顺序
- `ColorType.BGRA_8888`: 期望BGRA顺序（如果有）

但`makeS32()`是标准的32位颜色格式，在Windows/Linux上通常是BGRA。为了兼容性和性能，我们选择手动转换通道顺序，而不是尝试不同的ColorType。

### 性能考虑

通道交换的性能开销：
- RGB: `O(width * height)` - 每像素3次读取 + 4次写入
- RGBA: `O(width * height)` - 每像素4次读取 + 4次写入

对于288x288的图片：
- 像素数：82,944
- 操作数：~580K
- 耗时：<1ms（现代CPU）

**结论**：性能开销可忽略不计，正确性更重要。

---

## 🚀 相关修复

与此修复一起完成的还有：
1. **混合ID传输修复** (`KITTY_GRAPHICS_IMAGE_SIZE_FIX.md`)
2. **图片尺寸计算优化** (columns/rows=0时的处理)

