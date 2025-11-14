# Yazi 完整终端支持实现

## 📋 实现的功能

### 1. **Kitty Graphics Protocol 完整支持**

#### ✅ 已实现的命令

- **Transmit (a=t/T)**: 传输图片数据
  - 支持多 chunk 传输（`m=1` 表示更多数据）
  - 支持混合 ID 传输（第一个 chunk `imageId=1`，后续 `imageId=0`）
  - 支持 Unicode Placeholder (`U=1`)
  - **成功响应**: `ESC_Gi=<id>;OKESC\` (根据 `q` 参数)
  - 格式：PNG, JPEG, RGB, RGBA

- **Display (a=p)**: 显示已存储的图片
  - 支持指定位置 (`x`, `y`)
  - 支持指定尺寸 (`c`=columns, `r`=rows)
  - 支持 Z-index (`z`)

- **Delete (a=d)**: 删除图片
  - `d=A`: 删除所有图片
  - `d=I`: 删除指定 ID 的图片

- **Query (a=q)**: 查询终端支持
  - **响应**: `ESC_Ga=q,s=1,i=<id>;ESC\`
  - `s=1` 表示支持 Kitty Graphics Protocol

#### 🎯 关键实现细节

1. **Unicode Placeholder (U=1)**
   - 在第一个 chunk 接收时立即写入 `U+10EEEE` (低代理对 `\uDEEE`)
   - 占据图片区域，防止 Yazi 的文字被清除
   - 在渲染时作为空格处理，不影响显示

2. **多 Chunk 传输管理**
   - `ImageAccumulator` 累积数据和元数据（width, height, format, imageNumber, unicodePlaceholder）
   - 混合 ID 传输：通过 `currentTransmittingImageId` 跟踪当前传输
   - `imageNumberToIdMap` 映射 `imageNumber` 到 `actualImageId`

3. **图片渲染**
   - Z-index 分层：背景图片 (`z < 0`) → 文字 → 前景图片 (`z >= 0`)
   - RGB/BGR 颜色通道转换（Kitty 的 RGB → Skia 的 BGRA）
   - 自动计算尺寸（当 `columns=0` 或 `rows=0` 时）
   - `clipRect` 确保图片在边界内

4. **响应机制**
   - **Transmit OK 响应**: 传输完成时（`more=0`）发送 `ESC_Gi=<id>;OKESC\`
   - **Query 响应**: 声明支持 `s=1`
   - 根据 `q` 参数决定是否响应（`q=0` 总是响应，`q=1` 静默，`q=2` 仅失败时）

---

### 2. **终端查询响应支持**

#### ✅ 已实现的查询命令

| 命令 | 名称 | 格式 | 响应 |
|------|------|------|------|
| **DA1** | Primary Device Attributes | `CSI c` | `CSI ? 62 ; 1 ; 2 ; 6 ; 9 ; 15 ; 22 c` |
| **DA2** | Secondary Device Attributes | `CSI > c` | `CSI > 1 ; 10 ; 0 c` |
| **DA3** | Tertiary Device Attributes | `CSI = c` | `DCS ! \| 00000000 ST` |
| **DSR** | Device Status Report | `CSI 5 n` | `CSI 0 n` (Terminal OK) |
| **CPR** | Cursor Position Report | `CSI 6 n` | `CSI <row> ; <col> R` |
| **DECXCPR** | Extended CPR | `CSI ? 6 n` | `CSI ? <row> ; <col> ; 1 R` |
| **DECREQTPARM** | Request Terminal Parameters | `CSI <sol> x` | `CSI <sol> ; 1 ; 1 ; 120 ; 120 ; 1 ; 0 x` |
| **XTVERSION** | Terminal Version | `CSI > q` | `DCS > \| xterm-kitty(0.26.2) ST` |
| **DECRQM** | DEC Request Mode | `CSI ? <mode> $ p` | `CSI ? <mode> ; <value> $ y` |

#### 🎯 DECRQM 支持的模式

| Mode | 名称 | 返回值 | 说明 |
|------|------|--------|------|
| 1 | DECCKM - Cursor Keys | 2 | 未设置（普通模式） |
| 3 | DECCOLM - 80/132 Column | 2 | 未设置（80列） |
| 6 | DECOM - Origin Mode | 2 | 未设置（绝对定位） |
| 7 | DECAWM - Auto Wrap | 2 | 未设置 |
| 12 | Blinking Cursor | 2 | 未设置 |
| 25 | DECTCEM - Cursor Visible | 1 | **设置（光标可见）** |
| 1000-1007 | Mouse Tracking | 2 | 未设置 |
| 1047-1049 | Alternate Screen | 2 | 取决于状态 |
| 2004 | Bracketed Paste | 2 | 未设置 |
| 其他 | - | 0 | 不识别 |

**返回值说明**:
- `0` = 不识别该模式
- `1` = 模式已设置
- `2` = 模式未设置
- `3` = 永久设置
- `4` = 永久未设置

---

### 3. **Alternate Screen Buffer**

#### ✅ 功能

- **切换到 Alternate Screen**: `CSI ? 1049 h`
  - 保存主屏幕内容到 `mainScreenLines`
  - 保存光标位置到 `savedCursorForAltScreen`
  - 清空 `visibleLines` 作为 alternate screen

- **切换回 Main Screen**: `CSI ? 1049 l`
  - 从 `mainScreenLines` 恢复主屏幕内容
  - 从 `savedCursorForAltScreen` 恢复光标位置
  - 清空 `mainScreenLines`

- **分离的光标保存/恢复**
  - `savedCursor`: 普通模式使用
  - `savedCursorForAltScreen`: 切换 alternate screen 时使用

#### 🎯 UI 刷新机制

- `TerminalViewModel.refreshTrigger`: `mutableStateOf(0)`
- 每次处理数据后递增，触发 Compose UI 重新组合
- `TerminalBuffer.onScreenChanged` 回调：切换屏幕时强制刷新

---

### 4. **TERM 环境变量**

```kotlin
// composeApp/src/jvmMain/kotlin/cn/hjhw/ssh/connection/SshConnectionImpl.kt
channel.allocatePTY("xterm-kitty", ...)
```

- 设置为 `xterm-kitty` 而非 `xterm-256color`
- 告诉 Yazi 支持 Kitty Graphics Protocol
- 防止 Yazi 回退到 Sixel

---

## 🐛 修复的问题

### 问题 1: Yazi 不断重传图片
**根因**: 没有发送 Transmit 命令的 OK 响应
**修复**: 在最后一个 chunk (`more=0`) 时发送 `ESC_Gi=<id>;OKESC\`

### 问题 2: 图片颜色错误（橙色变蓝色）
**根因**: RGB/BGR 颜色通道顺序错误
**修复**: 在 `ImageDecoder.jvm.kt` 中交换 R 和 B 通道

### 问题 3: 文字被图片覆盖
**根因**: Unicode Placeholder 时机错误
**修复**: 在第一个 chunk 时立即写入 placeholder，而非传输完成后

### 问题 4: `Unknown CSI command 'p'`
**根因**: 未实现 DECRQM (DEC Request Mode)
**修复**: 实现 `handleDecRequestMode` 函数，支持常见模式查询

---

## 📊 测试状态

### ✅ 通过的测试

- Yazi 图片预览正常显示
- 图片颜色正确
- 文件列表文字不被覆盖
- 图片传输不再无限重复
- 切换文件时图片正常更新
- Alternate Screen 切换正常
- 无 `Unknown CSI command` 错误

### 🚀 性能优化

- 关闭了过多的调试日志
- 仅在关键节点记录日志（`⏰ FIRST CHUNK`, `✅ Sent OK response`）

---

## 📁 涉及文件

1. **AnsiParser.kt**: CSI 命令处理，终端查询响应，DECRQM
2. **TerminalBuffer.kt**: 图片管理，Unicode Placeholder，Alternate Screen
3. **TerminalImage.kt**: Kitty Protocol 解析，图片数据结构
4. **TerminalView.kt**: 图片渲染，Z-index，颜色转换
5. **TerminalViewModel.kt**: 响应发送，UI 刷新触发
6. **SshConnectionImpl.kt**: TERM 环境变量，UTF-8 解码
7. **ImageDecoder.jvm.kt**: RGB/BGRA 转换

---

## 🎉 总结

现在终端已经完整支持 Yazi 文件管理器，包括：
- ✅ Kitty Graphics Protocol 图片预览
- ✅ 所有终端查询响应
- ✅ Alternate Screen Buffer
- ✅ 正确的颜色渲染
- ✅ 文字与图片共存
- ✅ 无重复传输

**状态**: 所有已知问题已修复，Yazi 可完全正常工作！

