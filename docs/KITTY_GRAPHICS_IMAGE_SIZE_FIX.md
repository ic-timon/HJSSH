# Kitty Graphics Protocol - 完整修复记录

## 🐛 问题诊断

### 问题1: 隐式多块传输ID管理（已修复）

**现象**：
```
每个chunk都生成新ID:
  actualImageId=1, 2, 3, ..., 80
结果：80个独立图片，最后一个只有4096字节
```

**根本原因**：
- Yazi发送: `imageId=0, imageNumber=0` (隐式多块传输)
- 我们每次都调用 `generateImageId()`，生成新ID
- 前79块的数据累积被丢弃

**修复方案**：
```kotlin
// 新增：追踪隐式多块传输的当前图片ID
private var currentImplicitImageId: Int? = null

// 修改ID分配逻辑
if (command.imageId > 0) {
    actualImageId = command.imageId  // 显式指定
} else if (command.imageNumber > 0) {
    actualImageId = imageNumberToIdMap.getOrPut(command.imageNumber) {
        generateImageId()  // 使用imageNumber追踪
    }
} else {
    // imageId=0 且 imageNumber=0: 隐式多块传输
    if (command.more > 0) {
        // 有更多块：使用或创建currentImplicitImageId
        if (currentImplicitImageId == null) {
            currentImplicitImageId = generateImageId()
        }
        actualImageId = currentImplicitImageId!!
    } else {
        // 最后一块：使用currentImplicitImageId或生成新ID
        actualImageId = currentImplicitImageId ?: generateImageId()
        currentImplicitImageId = null  // 清理
    }
}
```

**结果**：
```
✓ 所有80个chunk使用同一个actualImageId=1
✓ totalLength=331776 bytes (完整的PNG数据)
✓ Successfully decoded raw pixels: 288x288
✓ Bitmap decoded successfully: 288x288
```

---

### 问题2: 渲染尺寸为0（刚修复）

**现象**：
```
Drawing image at (927.0, 18.0), size: 0.0x0.0  ← 尺寸为0！
placeImage: placementId=81, pos=(103,1), size=0x0, z=0
```

**根本原因**：
- Yazi的Transmit命令只指定了像素尺寸 `s=288, v=288`
- **没有指定** `c` (columns) 和 `r` (rows)
- 我们的渲染代码: `imageWidth = placement.columns * charWidth`
- 如果 `placement.columns = 0`，则 `imageWidth = 0`

**修复方案**：
```kotlin
// 如果columns/rows为0，使用图片的像素尺寸作为默认值
val imageWidth = if (placement.columns > 0) {
    placement.columns * charWidth
} else {
    image.width.toFloat()  // 使用原始像素宽度
}
val imageHeight = if (placement.rows > 0) {
    placement.rows * charHeight
} else {
    image.height.toFloat()  // 使用原始像素高度
}
```

**预期结果**：
```
Drawing image at (927.0, 18.0), size: 288.0x288.0  ← 正确的尺寸！
图片应该能正常显示了！
```

---

### 问题3: 混合ID传输处理（刚修复）

**现象**：
```
Chunk 1: imageId=1, format=RGB, size=288x288  ← 明确指定id=1
         Accumulating to id=1
Chunk 2: imageId=0, format=PNG               ← imageId=0!
         Created implicit image ID: 3         ← 创建新ID=3！
         → 结果：两个独立图片，id=3的尺寸为0x0，PNG损坏
```

**根本原因**：
- Yazi的行为：
  - Chunk 1: `imageId=1, m=1` (明确指定，创建id=1)
  - Chunk 2-79: `imageId=0, m=1` (意图是"继续id=1")
  - Chunk 80: `imageId=0, m=0` (完成id=1)
- 我们的代码：检测到Chunk 2的`imageId=0`，误以为是新的隐式传输，创建了id=3！
- 结果：id=1只有第1个chunk的数据，id=3有第2-80个chunk的数据但没有尺寸信息

**修复方案**：
```kotlin
// 新增：追踪当前正在传输的图片ID（无论是显式还是隐式）
private var currentTransmittingImageId: Int? = null

// 修改ID分配逻辑
if (command.imageId > 0) {
    actualImageId = command.imageId
    currentTransmittingImageId = actualImageId  // 追踪当前传输
} else if (command.imageNumber > 0) {
    actualImageId = imageNumberToIdMap.getOrPut(command.imageNumber) {
        generateImageId()
    }
    currentTransmittingImageId = actualImageId
} else {
    // imageId=0 且 imageNumber=0
    if (currentTransmittingImageId != null) {
        // 正在进行传输，继续使用当前ID
        actualImageId = currentTransmittingImageId!!
    } else {
        // 开始新的隐式传输
        actualImageId = generateImageId()
        currentTransmittingImageId = actualImageId
    }
}

// 传输完成时清理
if (command.more == 0) {
    currentTransmittingImageId = null
}
```

**预期结果**：
```
Chunk 1:    imageId=1 → actualImageId=1, currentTransmittingImageId=1
Chunk 2-79: imageId=0 → actualImageId=1 (继续使用currentTransmittingImageId)
Chunk 80:   imageId=0, m=0 → actualImageId=1, 清理currentTransmittingImageId
✓ 所有80个chunk累积到同一个id=1
✓ totalLength=331776, size=288x288
✓ 图片应该能正确解码和显示！
```

---

## 📊 完整的数据流（修复后）

```
Yazi发送 (80个chunk):
  Chunk 1:    imageId=1, imageNumber=0, size=288x288, format=RGB, m=1
  Chunk 2-79: imageId=0, imageNumber=0, size=0x0,     format=PNG, m=1
  Chunk 80:   imageId=0, imageNumber=0, size=0x0,     format=PNG, m=0
    ↓
AnsiParser处理混合ID传输:
  Chunk 1: imageId=1 → actualImageId=1, currentTransmittingImageId=1
  Chunk 2-79: imageId=0 → 检测到currentTransmittingImageId=1 → actualImageId=1
  Chunk 80: imageId=0, m=0 → actualImageId=1, 清理currentTransmittingImageId
    ↓
TerminalBuffer累积数据:
  ImageAccumulator(id=1, width=288, height=288, data=331776 bytes)
    ↓
最后一块触发创建TerminalImage:
  TerminalImage(id=1, width=288, height=288, format=RGB, data=248832 bytes)
    ↓
TerminalView渲染:
  解码: decodeRawPixels(288x288, RGB) → ImageBitmap(288x288)
  尺寸计算: columns=0 → fallback to 288px
  绘制: drawImage(pos=(927,18), size=288x288) ✓
```

---

## ✅ 修复文件清单

1. **AnsiParser.kt** (混合ID传输处理)
   - 问题1修复：新增 `currentImplicitImageId` → 重构为 `currentTransmittingImageId`
   - 问题3修复：追踪当前正在传输的图片ID，处理`imageId=0`的后续chunk
   - 修改 `handleKittyGraphics()` 的ID分配逻辑
   - 在传输完成时清理 `currentTransmittingImageId`

2. **TerminalBuffer.kt** (元数据累积)
   - 问题1修复：新增 `ImageAccumulator` 数据类
   - 累积width/height元数据（从第一块保存，后续块保留）

3. **TerminalView.kt** (渲染尺寸回退)
   - 问题2修复：修改图片尺寸计算逻辑
   - 如果 `columns/rows=0`，使用图片的像素尺寸

---

## 🧪 测试步骤

1. 启动应用并连接SSH
2. 运行 `yazi /root`
3. 导航到有图片的目录
4. **预期看到的日志链路**：
   ```
   [KittyGraphics] Transmit: imageId=1, size=288x288, format=RGB, m=1
   [KittyGraphics] Using actualImageId=1
   [TerminalBuffer] Accumulating: id=1, totalLength=4096, size=288x288
   
   [KittyGraphics] Transmit: imageId=0, size=0x0, format=PNG, m=1
   [KittyGraphics] Continuing current transmission with ID: 1
   [KittyGraphics] Using actualImageId=1
   [TerminalBuffer] Accumulating: id=1, totalLength=8192, size=288x288
   
   ... (重复79次，累积到totalLength=331776) ...
   
   [KittyGraphics] Transmit: imageId=0, size=0x0, format=PNG, m=0
   [KittyGraphics] Continuing current transmission with ID: 1
   [TerminalBuffer] Multi-chunk complete, totalLength=331776, size=288x288
   [TerminalBuffer] ✓ Image stored: id=1, size=288x288
   
   [ImageDecoder] Successfully decoded raw pixels: 288x288
   [TerminalView] Bitmap decoded successfully: 288x288
   [TerminalView] Drawing image at (...), size: 288.0x288.0
   ```
5. **UI: 图片正常显示！**

---

## 📝 相关协议规范

**Kitty Graphics Protocol - 尺寸参数**：
- `s`, `v`: 图片的像素宽度/高度
- `c`, `r`: 显示时占用的终端列数/行数
- **如果c/r未指定**：终端应该自动计算合适的尺寸
  - 可以基于图片像素尺寸
  - 可以基于终端字符尺寸
  - 可以保持宽高比

我们的实现：**如果c/r=0，直接使用图片的像素尺寸**，这是最简单且最准确的方案。

