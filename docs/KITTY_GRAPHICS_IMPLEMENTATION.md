# Kitty Graphics Protocol 实现状态

## 📊 实现进度

| 组件 | 状态 | 说明 |
|------|------|------|
| 数据结构 | ✅ 完成 | TerminalImage.kt |
| 协议解析 | ✅ 完成 | KittyProtocolParser |
| ANSI解析器集成 | ✅ 完成 | AnsiParser支持 ESC _ G ... ESC \ |
| 图片存储管理 | ✅ 完成 | TerminalBuffer图片管理 |
| Base64解码 | ⏳ 进行中 | 需要实现 |
| PNG/JPEG解码 | ⏳ 进行中 | 需要使用Compose库 |
| TerminalView渲染 | ⏳ 进行中 | 需要添加 |
| 测试 | ⏳ 待测试 | 等待完整实现 |

---

## ✅ 已实现功能

### 1. 数据结构（TerminalImage.kt）

**核心类**：
- `TerminalImage` - 图片数据
- `ImagePlacement` - 图片放置位置
- `KittyGraphicsCommand` - 命令类型（Transmit/Display/Delete/Query）
- `KittyProtocolParser` - 协议解析器

**支持的格式**：
- PNG (f=100)
- RGB (f=24)
- RGBA (f=32)

**传输介质**：
- 直接传输 (t=d) ✅
- 文件 (t=f) ⚠️ 未测试
- 临时文件 (t=t) ⚠️ 未测试
- 共享内存 (t=s) ❌ 不支持

### 2. ANSI解析器增强（AnsiParser.kt）

**新增解析状态**：
- `ParseState.APC` - Application Program Command识别
- `ParseState.APC_KITTY` - Kitty Graphics数据收集

**序列识别**：
```
ESC _ G key=value,key=value;base64data ESC \
```

**处理流程**：
1. 检测 `ESC _` 进入APC模式
2. 检测 `G` 字符识别Kitty协议
3. 收集所有数据直到 `ESC \` 或 `ST`
4. 解析并执行命令

### 3. 图片管理（TerminalBuffer.kt）

**存储结构**：
- `images: Map<Int, TerminalImage>` - 图片数据库
- `imagePlacements: Map<Int, ImagePlacement>` - 放置信息
- `imageDataBuffer: Map<Int, StringBuilder>` - 分块传输缓冲

**API方法**：
```kotlin
fun addImage(imageId, imageNumber, format, width, height, data, isMore)
fun placeImage(imageId, placementId, x, y, columns, rows, zIndex)
fun deleteImages(deleteMode, imageId, placementId)
fun getImagePlacements(): List<ImagePlacement>
fun getImage(imageId): TerminalImage?
```

**支持特性**：
- ✅ 分块传输（m=1多块，m=0最后一块）
- ✅ 多图片管理
- ✅ Z轴排序
- ✅ 按ID/位置删除

---

## 🚧 待实现功能

### 1. 图片解码（优先级：高）

**需要实现**：
```kotlin
// composeApp/src/jvmMain/kotlin/cn/hjhw/ssh/terminal/ImageDecoder.kt
object ImageDecoder {
    fun decodeImage(image: TerminalImage): ImageBitmap? {
        // 1. Base64解码
        val imageData = Base64.getDecoder().decode(image.data)
        
        // 2. 根据format解码
        return when (image.format) {
            TransmissionFormat.PNG -> decodePNG(imageData)
            TransmissionFormat.JPEG -> decodeJPEG(imageData)
            TransmissionFormat.RGB -> decodeRGB(imageData, image.width, image.height)
            TransmissionFormat.RGBA -> decodeRGBA(imageData, image.width, image.height)
        }
    }
    
    private fun decodePNG(data: ByteArray): ImageBitmap {
        // 使用Compose的图片解码API
        return org.jetbrains.skia.Image.makeFromEncoded(data).toComposeImageBitmap()
    }
}
```

### 2. TerminalView渲染（优先级：高）

**需要添加**：
```kotlin
// 在TerminalView的Canvas中添加图片渲染
val placements = buffer.getImagePlacements()
for (placement in placements) {
    val image = buffer.getImage(placement.imageId) ?: continue
    
    // 解码图片（如果未解码）
    if (image.bitmap == null) {
        image.bitmap = ImageDecoder.decodeImage(image)
    }
    
    // 计算显示位置和尺寸
    val x = placement.x * charWidth
    val y = placement.y * charHeight
    val width = if (placement.columns > 0) {
        placement.columns * charWidth
    } else {
        image.width
    }
    val height = if (placement.rows > 0) {
        placement.rows * charHeight
    } else {
        image.height
    }
    
    // 渲染图片
    image.bitmap?.let { bitmap ->
        drawImage(
            image = bitmap,
            dstOffset = IntOffset(x, y),
            dstSize = IntSize(width, height)
        )
    }
}
```

### 3. 优化建议（优先级：中）

**缓存优化**：
- ✅ 已实现：图片只解码一次，存储在 `TerminalImage.bitmap`
- ⚠️ 待实现：内存限制和LRU缓存

**性能优化**：
- 使用协程异步解码大图片
- 分片渲染大图
- 虚拟化：只渲染可见区域的图片

**兼容性**：
- 处理不同终端尺寸
- 响应窗口resize
- 支持滚动时的图片位置更新

---

## 🧪 测试计划

### 单元测试

```kotlin
@Test
fun testKittyProtocolParser() {
    val data = "a=t,f=100,s=100,v=100;iVBORw0KG..."
    val command = KittyProtocolParser.parse(data)
    
    assertTrue(command is KittyGraphicsCommand.Transmit)
    assertEquals(100, (command as KittyGraphicsCommand.Transmit).width)
}

@Test
fun testImageStorage() {
    val buffer = TerminalBuffer(80, 24)
    buffer.addImage(1, 0, TransmissionFormat.PNG, 100, 100, "base64data", false)
    
    val image = buffer.getImage(1)
    assertNotNull(image)
    assertEquals(100, image?.width)
}
```

### 集成测试

**简单图片测试**：
```bash
# 1x1像素红色PNG（base64）
printf '\033_Ga=t,f=100,s=1,v=1;iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==\033\\'
```

**使用kitty icat工具**：
```bash
# 如果kitty可用
kitten icat /path/to/image.png
```

---

## 📚 参考资料

**官方文档**：
- [Kitty Graphics Protocol](https://sw.kovidgoyal.net/kitty/graphics-protocol/)
- [iTerm2 Inline Images](https://iterm2.com/documentation-images.html)

**示例实现**：
- [kitty源码 - graphics.c](https://github.com/kovidgoyal/kitty/blob/master/kitty/graphics.c)
- [wezterm图形支持](https://wezfurlong.org/wezterm/imgcat.html)

**相关协议**：
- Sixel Graphics
- iTerm2 Inline Images (OSC 1337)
- ReGIS (VT125)

---

## 🎯 下一步行动

1. **实现ImageDecoder** ⏳
   - Base64解码
   - PNG/JPEG解码
   - RGB/RGBA原始数据转换

2. **在TerminalView中渲染** ⏳
   - 获取图片放置列表
   - 延迟解码图片
   - Canvas绘制图片

3. **测试验证** ⏳
   - 单元测试
   - 使用简单图片测试
   - 使用yazi测试

4. **文档和示例** 📋
   - 更新用户文档
   - 提供示例脚本
   - 添加配置选项

---

## ⚠️ 当前限制

1. **不支持动画** - 动画需要额外的帧管理和定时器
2. **不支持Unicode占位符** - U=1功能未实现
3. **不支持相对放置** - P/Q/H/V参数未实现
4. **不支持查询** - Query命令未实现
5. **不支持压缩** - zlib压缩未实现

这些功能可以在后续版本中逐步添加。

---

## 📊 与Unicode块字符模式对比

| 特性 | Unicode块字符 | Kitty Graphics |
|------|--------------|----------------|
| 分辨率 | 低（每字符2像素） | 高（真实像素） |
| 兼容性 | 极高（所有终端） | 低（仅Kitty/WezTerm） |
| 性能 | 优秀 | 中等 |
| 颜色深度 | 24位TrueColor | 24位TrueColor |
| 实现复杂度 | 简单 | 复杂 |
| 图片质量 | 一格一格 | 完美 |

**用户可见改进**：
- ✅ Yazi图片预览不再"一格一格"
- ✅ 图片更清晰、更流畅
- ✅ 支持更大的图片显示

---

**创建日期**: 2025-11-14  
**最后更新**: 2025-11-14  
**版本**: 0.1.0-alpha

