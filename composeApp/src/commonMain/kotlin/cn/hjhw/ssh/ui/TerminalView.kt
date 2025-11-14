package cn.hjhw.ssh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.graphics.drawscope.clipRect
import cn.hjhw.ssh.terminal.ImagePlacement
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
// 暂时移除 Skia 导入，使用更简单的方法
import cn.hjhw.ssh.terminal.TerminalCell
import cn.hjhw.ssh.terminal.TerminalColor
import kotlinx.coroutines.delay

/**
 * 终端视图
 */
@Suppress("DEPRECATION") // LocalClipboardManager 在新版本中已弃用，但仍正常工作
@Composable
fun TerminalView(
    viewModel: TerminalViewModel,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Black,
    foregroundColor: Color = Color.White,
) {
    val density = LocalDensity.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    
    // 字符尺寸（像素）- 这些值决定了终端的列数和行数
    val charWidth = 9.dp  // 增加字符宽度以适应更多字体
    val charHeight = 18.dp  // 增加字符高度以获得更好的显示
    
    // 请求焦点，确保能接收键盘输入
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100) // 延迟一下，确保 UI 已经渲染
        focusRequester.requestFocus()
        println("Focus requested for TerminalView")
    }
    
    // 终端尺寸（字符数）
    var terminalWidth by remember { mutableStateOf(80) }
    var terminalHeight by remember { mutableStateOf(24) }
    
    // 强制触发一次 resize 检查
    var resizeCounter by remember { mutableStateOf(0) }
    var resizeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    // 光标闪烁状态
    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            cursorVisible = !cursorVisible
        }
    }
    
    var selectionStart by remember { mutableStateOf<Offset?>(null) }
    var selectionEnd by remember { mutableStateOf<Offset?>(null) }
    
    // ⚠️ 注意：TextMeasurer 在 DrawScope 中调用会导致重绘循环，已移除
    // val textMeasurer = rememberTextMeasurer()
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                val handled = handleKeyEvent(event, viewModel, clipboardManager, coroutineScope)
                // 键盘日志已关闭
                // if (handled) {
                //     println("Key event handled: ${event.key}")
                // } else {
                //     println("Key event not handled: ${event.key}")
                // }
                handled
            }
            .pointerInput(Unit) {
                // 用于存储选择的起始位置
                var selectionStartX = 0
                var selectionStartY = 0
                var isSelecting = false
                var isDragging = false  // 标记是否真的在拖拽
                
                detectDragGestures(
                    onDragStart = { offset ->
                        // 请求焦点
                        focusRequester.requestFocus()
                        
                        // 计算字符位置
                        val charWidthPx = charWidth.toPx()
                        val charHeightPx = charHeight.toPx()
                        val x = (offset.x / charWidthPx).toInt().coerceIn(0, terminalWidth - 1)
                        val y = (offset.y / charHeightPx).toInt().coerceIn(0, terminalHeight - 1)
                        
                        // 开始选择
                        selectionStartX = x
                        selectionStartY = y
                        isSelecting = true
                        isDragging = false
                        viewModel.startSelection(x, y)
                        
                        println("[TextSelection] Start at ($x, $y)")
                    },
                    onDrag = { change, _ ->
                        if (isSelecting) {
                            isDragging = true  // 标记为真正的拖拽
                            val charWidthPx = charWidth.toPx()
                            val charHeightPx = charHeight.toPx()
                            val x = (change.position.x / charWidthPx).toInt().coerceIn(0, terminalWidth - 1)
                            val y = (change.position.y / charHeightPx).toInt().coerceIn(0, terminalHeight - 1)
                            
                            // 更新选择范围
                            viewModel.updateSelection(x, y)
                        }
                    },
                    onDragEnd = {
                        if (isSelecting) {
                            if (isDragging) {
                                // 真正的拖拽：结束选择并提取文本
                                val selectedText = viewModel.endSelection()
                                if (!selectedText.isNullOrEmpty()) {
                                    println("[TextSelection] ✅ Selected: ${selectedText.take(50)}...")
                                }
                            } else {
                                // 只是点击（没有拖拽）：清除旧的选择
                                viewModel.clearSelection()
                                println("[TextSelection] Click detected, selection cleared")
                            }
                            isSelecting = false
                            isDragging = false
                        }
                    },
                    onDragCancel = {
                        isSelecting = false
                        isDragging = false
                        viewModel.clearSelection()
                    }
                )
            }
            .pointerInput(Unit) {
                // 处理鼠标点击事件（右键复制）
                // 使用底层 API 来准确检测鼠标按钮
                awaitEachGesture {
                    val down = awaitFirstDown()
                    
                    // 等待释放
                    val up = waitForUpOrCancellation()
                    
                    if (up != null) {
                        // 检查是否是右键（通过检测 button 属性）
                        // 注意：这里需要在按下时就检测，因为 up 时可能丢失 button 信息
                        
                        // 简单的启发式方法：如果有选中文本，就复制
                        // 由于 Compose 的限制，我们使用 Ctrl+C 或中键来触发复制
                        // 这里我们检测任何点击后是否有选中文本
                        
                        // 暂时使用简化方案：任何点击都尝试复制（如果有选中文本）
                        val selectedText = viewModel.selectedText
                        if (!selectedText.isNullOrEmpty()) {
                            // 有选中文本时，点击即复制
                            coroutineScope.launch {
                                clipboardManager.setText(AnnotatedString(selectedText))
                                println("[TextSelection] ✅ Copied to clipboard: ${selectedText.take(50)}...")
                                
                                // 复制后清除选择
                                kotlinx.coroutines.delay(100) // 稍微延迟，确保复制完成
                                viewModel.clearSelection()
                                println("[TextSelection] Selection cleared after copy")
                            }
                        }
                    }
                }
            },
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            // 监听刷新触发器（即使不使用它的值，也会触发重绘）
            val refreshTrigger = viewModel.refreshTrigger
            
            val buffer = viewModel.getBuffer()
            val cursor = viewModel.getCursor()
            
            // 更新终端尺寸
            val charWidthPx = with(density) { charWidth.toPx() }
            val charHeightPx = with(density) { charHeight.toPx() }
            val newWidth = (size.width / charWidthPx).toInt().coerceAtLeast(1)
            val newHeight = (size.height / charHeightPx).toInt().coerceAtLeast(1)
            
            // 检查是否需要 resize（使用防抖避免频繁调整）
            if (newWidth != terminalWidth || newHeight != terminalHeight || resizeCounter < 2) {
                println("=== Terminal Resize ===")
                println("Canvas size: ${size.width} x ${size.height} px")
                println("Char size: ${charWidthPx} x ${charHeightPx} px")
                println("Old terminal: ${terminalWidth} x ${terminalHeight} chars")
                println("New terminal: ${newWidth} x ${newHeight} chars")
                println("Resize counter: $resizeCounter")
                
                // 更新本地尺寸变量（立即生效，用于UI渲染）
                terminalWidth = newWidth
                terminalHeight = newHeight
                
                // 初始的 2 次 resize 立即执行（不防抖），后续才使用防抖
                if (resizeCounter < 2) {
                    println(">>> [Initial] Executing immediate resize to ${newWidth}x${newHeight}")
                    viewModel.resize(newWidth, newHeight)
                    resizeCounter++
                    println(">>> [Initial] Resize completed")
                } else {
                    // 取消之前的防抖任务
                    resizeJob?.cancel()
                    
                    // 延迟执行真正的 resize（防抖）- 仅用于拖动窗口时
                    resizeJob = coroutineScope.launch {
                        kotlinx.coroutines.delay(150) // 等待 150ms，避免拖动时频繁 resize
                        println(">>> [Debounce] Executing delayed resize to ${newWidth}x${newHeight}")
                        viewModel.resize(newWidth, newHeight)
                        println(">>> [Debounce] Resize completed")
                    }
                }
                
                println("======================")
            }
            
            // 绘制终端内容
            drawTerminalContent(
                buffer = buffer,
                cursor = cursor,
                charWidth = charWidthPx,
                charHeight = charHeightPx,
                cursorVisible = cursorVisible,
                backgroundColor = backgroundColor,
                foregroundColor = foregroundColor,
            )
            
            // 绘制选择区域
            viewModel.textSelection?.let { selection ->
                drawSelection(
                    selection = selection,
                    charWidth = charWidthPx,
                    charHeight = charHeightPx,
                )
            }
        }
    }
}

/**
 * 绘制终端内容
 */
private var drawCallCounter = 0  // 全局计数器，用于诊断重绘次数

private fun DrawScope.drawTerminalContent(
    buffer: cn.hjhw.ssh.terminal.TerminalBuffer,
    cursor: cn.hjhw.ssh.terminal.CursorState,
    charWidth: Float,
    charHeight: Float,
    cursorVisible: Boolean,
    backgroundColor: Color,
    foregroundColor: Color,
) {
    drawCallCounter++
    val currentDrawCall = drawCallCounter
    
    val width = buffer.getWidth()
    val height = buffer.getHeight()
    
    // ========== 步骤0：获取图片放置信息并诊断 ==========
    val imagePlacements = buffer.getImagePlacements()
    
    // 精简日志（仅打印关键信息）
    // if (imagePlacements.isNotEmpty()) {
    //     println("      [DrawCall #$currentDrawCall] 🎨 drawTerminalContent called, images=${imagePlacements.size}")
    // }
    
    // ========== 步骤1：绘制背景图片 (z < 0) ==========
    imagePlacements.filter { it.zIndex < 0 }.forEach { placement ->
        val image = buffer.getImage(placement.imageId)
        if (image != null) {
            val bitmap = image.decodeBitmap()
            if (bitmap != null) {
                val imageX = (placement.x * charWidth)
                val imageY = (placement.y * charHeight)
                val imageWidth = if (placement.columns > 0) {
                    placement.columns * charWidth
                } else {
                    val cols = kotlin.math.ceil(image.width / charWidth).toInt()
                    cols * charWidth
                }
                val imageHeight = if (placement.rows > 0) {
                    placement.rows * charHeight
                } else {
                    val rows = kotlin.math.ceil(image.height / charHeight).toInt()
                    rows * charHeight
                }
                
                clipRect(imageX, imageY, imageX + imageWidth, imageY + imageHeight) {
                    drawImage(
                        image = bitmap,
                        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                        srcSize = androidx.compose.ui.unit.IntSize(bitmap.width, bitmap.height),
                        dstOffset = androidx.compose.ui.unit.IntOffset(imageX.toInt(), imageY.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(imageWidth.toInt(), imageHeight.toInt()),
                        blendMode = androidx.compose.ui.graphics.BlendMode.SrcOver
                    )
                }
            }
        }
    }
    
    // ========== 步骤2：绘制所有文字 ==========
    // 计算图片占用的区域（用于跳过这些区域的文字绘制）
    val imageCoveredCells = mutableSetOf<Pair<Int, Int>>()
    imagePlacements.filter { it.zIndex >= 0 }.forEach { placement ->
        val image = buffer.getImage(placement.imageId)
        if (image != null) {
            val startX = placement.x
            val startY = placement.y
            val cols = if (placement.columns > 0) {
                placement.columns
            } else {
                kotlin.math.ceil(image.width / charWidth).toInt()
            }
            val rows = if (placement.rows > 0) {
                placement.rows
            } else {
                kotlin.math.ceil(image.height / charHeight).toInt()
            }
            
            // 标记图片覆盖的单元格
            for (py in startY until minOf(startY + rows, height)) {
                for (px in startX until minOf(startX + cols, width)) {
                    imageCoveredCells.add(Pair(px, py))
                }
            }
        }
    }
    
    // 精简日志（已去除详细诊断）
    
    // 绘制每一行
    
    for (y in 0 until height) {
        val line = buffer.getVisibleLine(y)
        if (line != null) {
            // 渲染完整的终端宽度，而不是只渲染到行的实际内容宽度
            // 这样可以确保背景色和反色在整行中正确显示
            var x = 0
            while (x < width) {
                // 跳过被前景图片覆盖的单元格
                if (imageCoveredCells.contains(Pair(x, y))) {
                    x++
                    continue
                }
                
                val cell = line.getCell(x)
                
                // Kitty Graphics Protocol 的 Unicode Placeholder 处理
                // U+10EEEE (low surrogate: 0xDEEE) - 完全跳过，不渲染
                // 这些 placeholder 是不可见的标记，不应该占用任何显示空间
                val isPlaceholder = cell.char.code in 0xDEEE..0xDEEF
                if (isPlaceholder) {
                    x++
                    continue
                }
                
                // 跳过宽字符的延续单元格（它们不应该被渲染）
                if (cell.isWideContinuation) {
                    x++
                    continue
                }
                
                val xPos = x * charWidth
                val yPos = y * charHeight
                
                // 宽字符占用2个单元格的宽度
                val cellWidth = if (cell.isWideChar) charWidth * 2 else charWidth
                
                // 处理 reverse 属性（反色）：前景色和背景色互换
                val actualFgColor = if (cell.reverse) {
                    cell.backgroundColor.toComposeColor(backgroundColor)
                } else {
                    cell.foregroundColor.toComposeColor(foregroundColor)
                }
                val actualBgColor = if (cell.reverse) {
                    cell.foregroundColor.toComposeColor(foregroundColor)
                } else {
                    cell.backgroundColor.toComposeColor(backgroundColor)
                }
                
                // 绘制背景（始终绘制，包括空格）
                drawRect(
                    color = actualBgColor,
                    topLeft = Offset(xPos, yPos),
                    size = Size(cellWidth, charHeight),
                )
                
                // 绘制字符（包括空格，因为在反色模式下空格也需要显示背景色）
                // Placeholder 字符渲染为空格（避免显示乱码），但保留背景色
                val charToRender = if (isPlaceholder) ' ' else cell.char
                
                if (!cell.invisible && charToRender != ' ') {
                    // 如果是 faint（暗淡）模式，降低颜色的不透明度
                    val finalFgColor = if (cell.faint) {
                        actualFgColor.copy(alpha = actualFgColor.alpha * 0.5f)
                    } else {
                        actualFgColor
                    }
                    
                    // ⚠️ CRITICAL FIX: textMeasurer.measure() 在 DrawScope 中调用会导致副作用和重绘循环！
                    // 暂时回退到 TextRenderer（使用 drawIntoCanvas 的版本）
                    TextRenderer.drawText(
                        scope = this@drawTerminalContent,
                        text = charToRender.toString(),
                        x = xPos,
                        y = yPos + charHeight * 0.8f,
                        color = finalFgColor,
                        fontSize = charHeight * 0.8f,
                        bold = cell.bold,
                    )
                    
                    // 绘制下划线
                    if (cell.underline) {
                        drawLine(
                            color = finalFgColor,
                            start = Offset(xPos, yPos + charHeight - 2),
                            end = Offset(xPos + cellWidth, yPos + charHeight - 2),
                            strokeWidth = 1f,
                        )
                    }
                }
                
                x++
            }
        }
    }
    
    // ========== 步骤3：绘制前景图片 (z >= 0，填充之前跳过的区域) ==========
    val foregroundImages = imagePlacements.filter { it.zIndex >= 0 }
    foregroundImages.forEach { placement ->
        val image = buffer.getImage(placement.imageId)
        if (image != null) {
            val bitmap = image.decodeBitmap()
            if (bitmap != null) {
                val imageX = (placement.x * charWidth)
                val imageY = (placement.y * charHeight)
                val imageWidth = if (placement.columns > 0) {
                    placement.columns * charWidth
                } else {
                    val cols = kotlin.math.ceil(image.width / charWidth).toInt()
                    cols * charWidth
                }
                val imageHeight = if (placement.rows > 0) {
                    placement.rows * charHeight
                } else {
                    val rows = kotlin.math.ceil(image.height / charHeight).toInt()
                    rows * charHeight
                }
                
                clipRect(imageX, imageY, imageX + imageWidth, imageY + imageHeight) {
                    drawImage(
                        image = bitmap,
                        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                        srcSize = androidx.compose.ui.unit.IntSize(bitmap.width, bitmap.height),
                        dstOffset = androidx.compose.ui.unit.IntOffset(imageX.toInt(), imageY.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(imageWidth.toInt(), imageHeight.toInt()),
                        blendMode = androidx.compose.ui.graphics.BlendMode.SrcOver
                    )
                }
            }
        }
    }
    
    // ========== 步骤4：绘制光标（在最上层）==========
    if (cursorVisible && cursor.y < height && cursor.x < width) {
        val xPos = cursor.x * charWidth
        val yPos = cursor.y * charHeight
        
        drawRect(
            color = foregroundColor,
            topLeft = Offset(xPos, yPos),
            size = Size(charWidth, charHeight),
            alpha = 0.5f,
        )
    }
}

/**
 * 将终端颜色转换为 Compose 颜色
 */
private fun TerminalColor.toComposeColor(defaultColor: Color): Color {
    return when (this) {
        is TerminalColor.Default -> defaultColor
        is TerminalColor.Standard -> {
            when (color) {
                TerminalColor.StandardColor.Black -> Color(0xFF000000)
                TerminalColor.StandardColor.Red -> Color(0xFF800000)
                TerminalColor.StandardColor.Green -> Color(0xFF008000)
                TerminalColor.StandardColor.Yellow -> Color(0xFF808000)
                TerminalColor.StandardColor.Blue -> Color(0xFF000080)
                TerminalColor.StandardColor.Magenta -> Color(0xFF800080)
                TerminalColor.StandardColor.Cyan -> Color(0xFF008080)
                TerminalColor.StandardColor.White -> Color(0xFFC0C0C0)
                TerminalColor.StandardColor.BrightBlack -> Color(0xFF808080)
                TerminalColor.StandardColor.BrightRed -> Color(0xFFFF0000)
                TerminalColor.StandardColor.BrightGreen -> Color(0xFF00FF00)
                TerminalColor.StandardColor.BrightYellow -> Color(0xFFFFFF00)
                TerminalColor.StandardColor.BrightBlue -> Color(0xFF0000FF)
                TerminalColor.StandardColor.BrightMagenta -> Color(0xFFFF00FF)
                TerminalColor.StandardColor.BrightCyan -> Color(0xFF00FFFF)
                TerminalColor.StandardColor.BrightWhite -> Color(0xFFFFFFFF)
            }
        }
        is TerminalColor.Indexed256 -> {
            // 简化处理：将 256 色索引映射到标准 16 色
            when {
                index < 16 -> {
                    // 标准 16 色
                    TerminalColor.Standard(TerminalColor.StandardColor.values()[index]).toComposeColor(defaultColor)
                }
                index < 232 -> {
                    // 216 色立方体
                    val r = (index - 16) / 36
                    val g = ((index - 16) % 36) / 6
                    val b = (index - 16) % 6
                    val rValue = if (r == 0) 0 else (r * 40 + 55)
                    val gValue = if (g == 0) 0 else (g * 40 + 55)
                    val bValue = if (b == 0) 0 else (b * 40 + 55)
                    // 使用十六进制构造（需要Long类型）
                    Color(0xFF000000L or (rValue.toLong() shl 16) or (gValue.toLong() shl 8) or bValue.toLong())
                }
                else -> {
                    // 灰度色
                    val gray = (index - 232) * 10 + 8
                    Color(0xFF000000L or (gray.toLong() shl 16) or (gray.toLong() shl 8) or gray.toLong())
                }
            }
        }
        is TerminalColor.TrueColor -> {
            // 使用十六进制ARGB格式创建颜色
            // 确保值在有效范围内
            val rValue = this.r.coerceIn(0, 255)
            val gValue = this.g.coerceIn(0, 255)
            val bValue = this.b.coerceIn(0, 255)
            // 格式: 0xAARRGGBB (AA=alpha, RR=red, GG=green, BB=blue)
            Color(0xFF000000L or (rValue.toLong() shl 16) or (gValue.toLong() shl 8) or bValue.toLong())
        }
    }
}

/**
 * 绘制文本选择区域
 */
private fun DrawScope.drawSelection(
    selection: TerminalViewModel.TextSelection,
    charWidth: Float,
    charHeight: Float,
) {
    // 规范化选择范围（确保起点在终点之前）
    val (startX, startY, endX, endY) = if (selection.startY < selection.endY || 
        (selection.startY == selection.endY && selection.startX <= selection.endX)) {
        listOf(selection.startX, selection.startY, selection.endX, selection.endY)
    } else {
        listOf(selection.endX, selection.endY, selection.startX, selection.startY)
    }
    
    // 绘制选择区域背景（使用半透明蓝色）
    val selectionColor = Color(0x40, 0x80, 0xFF, 0x60) // 半透明蓝色
    
    if (startY == endY) {
        // 单行选择
        val x1 = startX * charWidth
        val y1 = startY * charHeight
        val width = (endX - startX + 1) * charWidth
        
        drawRect(
            color = selectionColor,
            topLeft = Offset(x1, y1),
            size = Size(width, charHeight)
        )
    } else {
        // 多行选择
        // 第一行：从 startX 到行尾
        val firstLineX = startX * charWidth
        val firstLineY = startY * charHeight
        val firstLineWidth = size.width - firstLineX
        
        drawRect(
            color = selectionColor,
            topLeft = Offset(firstLineX, firstLineY),
            size = Size(firstLineWidth, charHeight)
        )
        
        // 中间行：整行
        for (y in (startY + 1) until endY) {
            val yPos = y * charHeight
            drawRect(
                color = selectionColor,
                topLeft = Offset(0f, yPos),
                size = Size(size.width, charHeight)
            )
        }
        
        // 最后一行：从行首到 endX
        val lastLineY = endY * charHeight
        val lastLineWidth = (endX + 1) * charWidth
        
        drawRect(
            color = selectionColor,
            topLeft = Offset(0f, lastLineY),
            size = Size(lastLineWidth, charHeight)
        )
    }
}

/**
 * 处理键盘事件
 */
@Suppress("DEPRECATION") // ClipboardManager 在新版本中已弃用，但仍正常工作
private fun handleKeyEvent(
    event: KeyEvent,
    viewModel: TerminalViewModel,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
): Boolean {
    // 只处理 KeyDown 事件，忽略 KeyUp 事件，避免重复输入
    if (event.type != KeyEventType.KeyDown) {
        return false
    }
    
    // 调试：打印按键信息（已关闭）
    // println("[KeyEvent] key=${event.key}, ctrl=${event.isCtrlPressed}, shift=${event.isShiftPressed}, alt=${event.isAltPressed}")
    
    val key = event.key
    
    // 1. 优先处理 Ctrl 组合键（用于终端控制）
    if (event.isCtrlPressed) {
        // 特殊处理 Ctrl+V (粘贴)
        if (key == Key.V) {
            coroutineScope.launch {
                val clipboardText = clipboardManager.getText()?.text
                if (!clipboardText.isNullOrEmpty()) {
                    println("  -> Pasting text: ${clipboardText.take(50)}...")
                    viewModel.handleInputBytes(clipboardText.toByteArray())
                }
            }
            return true
        }
        return handleCtrlCombination(event, viewModel)
    }
    
    // 2. 处理特殊功能键
    when (key) {
        Key.Enter -> {
            viewModel.handleInputBytes("\r".toByteArray())
            return true
        }
        Key.Tab -> {
            viewModel.handleInputBytes("\t".toByteArray())
            return true
        }
        Key.Backspace -> {
            viewModel.handleInputBytes("\u007F".toByteArray()) // DEL
            return true
        }
        Key.Delete -> {
            viewModel.handleInputBytes("\u001B[3~".toByteArray())
            return true
        }
        Key.Escape -> {
            viewModel.handleInputBytes("\u001B".toByteArray())
            return true
        }
        Key.Spacebar -> {
            viewModel.handleInputBytes(" ".toByteArray())
            return true
        }
        Key.DirectionUp -> {
            viewModel.handleInputBytes("\u001B[A".toByteArray())
            return true
        }
        Key.DirectionDown -> {
            viewModel.handleInputBytes("\u001B[B".toByteArray())
            return true
        }
        Key.DirectionLeft -> {
            viewModel.handleInputBytes("\u001B[D".toByteArray())
            return true
        }
        Key.DirectionRight -> {
            viewModel.handleInputBytes("\u001B[C".toByteArray())
            return true
        }
        Key.MoveHome -> {
            viewModel.handleInputBytes("\u001B[H".toByteArray())
            return true
        }
        Key.MoveEnd -> {
            viewModel.handleInputBytes("\u001B[F".toByteArray())
            return true
        }
        Key.PageUp -> {
            viewModel.handleInputBytes("\u001B[5~".toByteArray())
            return true
        }
        Key.PageDown -> {
            viewModel.handleInputBytes("\u001B[6~".toByteArray())
            return true
        }
        
        // 小键盘（Numpad）数字键
        Key.NumPad0 -> {
            viewModel.handleInputBytes("0".toByteArray())
            return true
        }
        Key.NumPad1 -> {
            viewModel.handleInputBytes("1".toByteArray())
            return true
        }
        Key.NumPad2 -> {
            viewModel.handleInputBytes("2".toByteArray())
            return true
        }
        Key.NumPad3 -> {
            viewModel.handleInputBytes("3".toByteArray())
            return true
        }
        Key.NumPad4 -> {
            viewModel.handleInputBytes("4".toByteArray())
            return true
        }
        Key.NumPad5 -> {
            viewModel.handleInputBytes("5".toByteArray())
            return true
        }
        Key.NumPad6 -> {
            viewModel.handleInputBytes("6".toByteArray())
            return true
        }
        Key.NumPad7 -> {
            viewModel.handleInputBytes("7".toByteArray())
            return true
        }
        Key.NumPad8 -> {
            viewModel.handleInputBytes("8".toByteArray())
            return true
        }
        Key.NumPad9 -> {
            viewModel.handleInputBytes("9".toByteArray())
            return true
        }
        
        // 小键盘运算符键
        Key.NumPadAdd -> {
            viewModel.handleInputBytes("+".toByteArray())
            return true
        }
        Key.NumPadSubtract -> {
            viewModel.handleInputBytes("-".toByteArray())
            return true
        }
        Key.NumPadMultiply -> {
            viewModel.handleInputBytes("*".toByteArray())
            return true
        }
        Key.NumPadDivide -> {
            viewModel.handleInputBytes("/".toByteArray())
            return true
        }
        Key.NumPadDot -> {
            viewModel.handleInputBytes(".".toByteArray())
            return true
        }
        Key.NumPadEnter -> {
            viewModel.handleInputBytes("\r".toByteArray())
            return true
        }
        Key.NumPadEquals -> {
            viewModel.handleInputBytes("=".toByteArray())
            return true
        }
    }
    
    // 3. 尝试通过反射获取字符（Compose Desktop 的 KeyEvent 可能有私有字段）
    val char: Char? = try {
        val fields = event.javaClass.declaredFields
        var foundChar: Char? = null
        for (field in fields) {
            field.isAccessible = true
            val value = field.get(event)
            if (value is Char) {
                foundChar = value
                break
            } else if (value is Int && value in 0..0xFFFF) {
                val ch = value.toChar()
                if (ch.isDefined()) {
                    foundChar = ch
                    break
                }
            }
        }
        foundChar
    } catch (e: Exception) {
        null
    }
    
    if (char != null) {
        println("  -> Found char: $char (code: ${char.code})")
    }
    
    // 4. 如果获取到了字符且不是控制字符，直接使用（优先级最高）
    if (char != null && !char.isISOControl()) {
        println("  -> Using char directly: '$char' (code: ${char.code})")
        viewModel.handleInputBytes(char.toString().toByteArray())
        return true
    }
    
    // 4.5 如果反射没有获取到字符，尝试通过按键和修饰符推断常见的标点符号
    if (char == null) {
        val punctuation = getPunctuationChar(key, event.isShiftPressed)
        if (punctuation != null) {
            println("  -> Using punctuation mapping: '$punctuation'")
            viewModel.handleInputBytes(punctuation.toString().toByteArray())
            return true
        }
    }
    
    // 5. 如果没有字符，尝试通过按键类型处理（字母、数字）
    when (key) {
        Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J,
        Key.K, Key.L, Key.M, Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T,
        Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z -> {
            // 只在没有获取到字符时才处理
            if (char == null) {
                val charFromKey = when (key) {
                    Key.A -> if (event.isShiftPressed) 'A' else 'a'
                    Key.B -> if (event.isShiftPressed) 'B' else 'b'
                    Key.C -> if (event.isShiftPressed) 'C' else 'c'
                    Key.D -> if (event.isShiftPressed) 'D' else 'd'
                    Key.E -> if (event.isShiftPressed) 'E' else 'e'
                    Key.F -> if (event.isShiftPressed) 'F' else 'f'
                    Key.G -> if (event.isShiftPressed) 'G' else 'g'
                    Key.H -> if (event.isShiftPressed) 'H' else 'h'
                    Key.I -> if (event.isShiftPressed) 'I' else 'i'
                    Key.J -> if (event.isShiftPressed) 'J' else 'j'
                    Key.K -> if (event.isShiftPressed) 'K' else 'k'
                    Key.L -> if (event.isShiftPressed) 'L' else 'l'
                    Key.M -> if (event.isShiftPressed) 'M' else 'm'
                    Key.N -> if (event.isShiftPressed) 'N' else 'n'
                    Key.O -> if (event.isShiftPressed) 'O' else 'o'
                    Key.P -> if (event.isShiftPressed) 'P' else 'p'
                    Key.Q -> if (event.isShiftPressed) 'Q' else 'q'
                    Key.R -> if (event.isShiftPressed) 'R' else 'r'
                    Key.S -> if (event.isShiftPressed) 'S' else 's'
                    Key.T -> if (event.isShiftPressed) 'T' else 't'
                    Key.U -> if (event.isShiftPressed) 'U' else 'u'
                    Key.V -> if (event.isShiftPressed) 'V' else 'v'
                    Key.W -> if (event.isShiftPressed) 'W' else 'w'
                    Key.X -> if (event.isShiftPressed) 'X' else 'x'
                    Key.Y -> if (event.isShiftPressed) 'Y' else 'y'
                    Key.Z -> if (event.isShiftPressed) 'Z' else 'z'
                    else -> return false
                }
                println("  -> Using key mapping: $charFromKey")
                viewModel.handleInputBytes(charFromKey.toString().toByteArray())
                return true
            }
        }
        else -> {
            // 处理数字键和其他字符键
            if (char == null) {
                val keyStr = key.toString()
                // 尝试从 key 名称提取数字
                if (keyStr.startsWith("Number") || keyStr.startsWith("Digit")) {
                    val digitStr = keyStr.substring(6).takeIf { it.isNotEmpty() } 
                        ?: keyStr.substring(5).takeIf { it.isNotEmpty() }
                    val digit = digitStr?.toIntOrNull()
                    if (digit != null && digit in 0..9) {
                        viewModel.handleInputBytes(digit.toString().toByteArray())
                        return true
                    }
                }
            }
        }
    }
    
    // 未处理的键 - 尝试通过字符串匹配方向键（fallback）
    val keyStr = key.toString()
    when {
        keyStr.contains("Up") || keyStr.contains("上") -> {
            viewModel.handleInputBytes("\u001B[A".toByteArray())
            return true
        }
        keyStr.contains("Down") || keyStr.contains("下") -> {
            viewModel.handleInputBytes("\u001B[B".toByteArray())
            return true
        }
        keyStr.contains("Left") || keyStr.contains("左") -> {
            viewModel.handleInputBytes("\u001B[D".toByteArray())
            return true
        }
        keyStr.contains("Right") || keyStr.contains("右") -> {
            viewModel.handleInputBytes("\u001B[C".toByteArray())
            return true
        }
    }
    
    // 未处理的键（调试已关闭）
    // println("[KeyEvent] Unhandled key: $keyStr")
    return false
}

/**
 * 获取标点符号字符
 */
private fun getPunctuationChar(key: Key, shiftPressed: Boolean): Char? {
    return when (key.toString()) {
        // 数字行的标点符号
        "Key: Minus", "Minus" -> if (shiftPressed) '_' else '-'
        "Key: Equals", "Equals" -> if (shiftPressed) '+' else '='
        "Key: LeftBracket", "LeftBracket" -> if (shiftPressed) '{' else '['
        "Key: RightBracket", "RightBracket" -> if (shiftPressed) '}' else ']'
        "Key: Backslash", "Backslash" -> if (shiftPressed) '|' else '\\'
        "Key: Semicolon", "Semicolon" -> if (shiftPressed) ':' else ';'
        "Key: Apostrophe", "Apostrophe", "Quote" -> if (shiftPressed) '"' else '\''
        "Key: Grave", "Grave" -> if (shiftPressed) '~' else '`'
        "Key: Comma", "Comma" -> if (shiftPressed) '<' else ','
        "Key: Period", "Period" -> if (shiftPressed) '>' else '.'
        "Key: Slash", "Slash" -> if (shiftPressed) '?' else '/'
        
        // 数字键（在标准键盘区域）
        "Key: Zero", "Zero", "Key: 0" -> if (shiftPressed) ')' else '0'
        "Key: One", "One", "Key: 1" -> if (shiftPressed) '!' else '1'
        "Key: Two", "Two", "Key: 2" -> if (shiftPressed) '@' else '2'
        "Key: Three", "Three", "Key: 3" -> if (shiftPressed) '#' else '3'
        "Key: Four", "Four", "Key: 4" -> if (shiftPressed) '$' else '4'
        "Key: Five", "Five", "Key: 5" -> if (shiftPressed) '%' else '5'
        "Key: Six", "Six", "Key: 6" -> if (shiftPressed) '^' else '6'
        "Key: Seven", "Seven", "Key: 7" -> if (shiftPressed) '&' else '7'
        "Key: Eight", "Eight", "Key: 8" -> if (shiftPressed) '*' else '8'
        "Key: Nine", "Nine", "Key: 9" -> if (shiftPressed) '(' else '9'
        
        else -> null
    }
}

/**
 * 处理 Ctrl 组合键
 */
private fun handleCtrlCombination(
    event: KeyEvent,
    viewModel: TerminalViewModel,
): Boolean {
    val key = event.key
    
    // Ctrl + 字母键 -> 发送控制字符 (0x01-0x1A)
    when (key) {
        Key.A -> { viewModel.handleInputBytes(byteArrayOf(0x01)); return true } // Ctrl+A
        Key.B -> { viewModel.handleInputBytes(byteArrayOf(0x02)); return true } // Ctrl+B
        Key.C -> { viewModel.handleInputBytes(byteArrayOf(0x03)); return true } // Ctrl+C (中断)
        Key.D -> { viewModel.handleInputBytes(byteArrayOf(0x04)); return true } // Ctrl+D (EOF)
        Key.E -> { viewModel.handleInputBytes(byteArrayOf(0x05)); return true } // Ctrl+E
        Key.F -> { viewModel.handleInputBytes(byteArrayOf(0x06)); return true } // Ctrl+F
        Key.G -> { viewModel.handleInputBytes(byteArrayOf(0x07)); return true } // Ctrl+G (Bell)
        Key.H -> { viewModel.handleInputBytes(byteArrayOf(0x08)); return true } // Ctrl+H (Backspace)
        Key.I -> { viewModel.handleInputBytes(byteArrayOf(0x09)); return true } // Ctrl+I (Tab)
        Key.J -> { viewModel.handleInputBytes(byteArrayOf(0x0A)); return true } // Ctrl+J (LF)
        Key.K -> { viewModel.handleInputBytes(byteArrayOf(0x0B)); return true } // Ctrl+K
        Key.L -> { viewModel.handleInputBytes(byteArrayOf(0x0C)); return true } // Ctrl+L (清屏)
        Key.M -> { viewModel.handleInputBytes(byteArrayOf(0x0D)); return true } // Ctrl+M (CR)
        Key.N -> { viewModel.handleInputBytes(byteArrayOf(0x0E)); return true } // Ctrl+N
        Key.O -> { viewModel.handleInputBytes(byteArrayOf(0x0F)); return true } // Ctrl+O
        Key.P -> { viewModel.handleInputBytes(byteArrayOf(0x10)); return true } // Ctrl+P
        Key.Q -> { viewModel.handleInputBytes(byteArrayOf(0x11)); return true } // Ctrl+Q
        Key.R -> { viewModel.handleInputBytes(byteArrayOf(0x12)); return true } // Ctrl+R
        Key.S -> { viewModel.handleInputBytes(byteArrayOf(0x13)); return true } // Ctrl+S
        Key.T -> { viewModel.handleInputBytes(byteArrayOf(0x14)); return true } // Ctrl+T
        Key.U -> { viewModel.handleInputBytes(byteArrayOf(0x15)); return true } // Ctrl+U
        Key.V -> { viewModel.handleInputBytes(byteArrayOf(0x16)); return true } // Ctrl+V
        Key.W -> { viewModel.handleInputBytes(byteArrayOf(0x17)); return true } // Ctrl+W
        Key.X -> { viewModel.handleInputBytes(byteArrayOf(0x18)); return true } // Ctrl+X
        Key.Y -> { viewModel.handleInputBytes(byteArrayOf(0x19)); return true } // Ctrl+Y
        Key.Z -> { viewModel.handleInputBytes(byteArrayOf(0x1A)); return true } // Ctrl+Z (挂起)
        else -> return false
    }
}
