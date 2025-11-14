package cn.hjhw.ssh.terminal

/**
 * 终端缓冲区 - 管理终端的所有行、滚动历史和图像
 *
 * 这是终端的核心数据结构，负责管理所有显示内容和状态。
 * 主要功能包括：
 *
 * ### 屏幕管理
 * - **主屏幕**: 用于常规命令行交互，支持滚动历史
 * - **交替屏幕**: 用于全屏应用（如 vi、nano、htop），不保存历史
 * - **滚动区域**: 支持 DECSTBM，限制滚动范围
 *
 * ### 内容管理
 * - **文本内容**: 存储每行的字符、样式和颜色
 * - **宽字符支持**: 正确处理 CJK 等占用两个单元格的字符
 * - **滚动历史**: 保存超出屏幕的历史内容，默认 1000 行
 *
 * ### 图形支持
 * - **Kitty Graphics Protocol**: 存储和管理图像及其放置位置
 * - **多块传输**: 累积和重组分块传输的图像数据
 * - **Z-index**: 支持图像分层显示
 *
 * ### 数据结构
 * ```
 * scrollbackLines (历史行)
 *     ↓
 * visibleLines (当前屏幕)
 *     ↓
 * mainScreenLines (主屏幕备份，切换时使用)
 * ```
 *
 * @param width 终端宽度（列数）
 * @param height 终端高度（行数）
 * @param scrollbackSize 滚动历史大小（行数），默认 1000
 *
 * @see TerminalLine 终端行数据结构
 * @see TerminalImage 图像数据结构
 * @see AnsiParser ANSI 解析器
 */
class TerminalBuffer(
    private var width: Int,
    private var height: Int,
    private val scrollbackSize: Int = 1000,
) {
    // 屏幕切换回调
    private var onScreenChanged: (() -> Unit)? = null
    
    fun setOnScreenChangedCallback(callback: () -> Unit) {
        onScreenChanged = callback
    }
    // 当前可见区域的行（从 scrollback 的末尾开始）
    private val visibleLines = mutableListOf<TerminalLine>()
    
    // 滚动历史（超出可见区域的历史行）
    private val scrollbackLines = mutableListOf<TerminalLine>()
    
    // 当前滚动位置（0 = 显示最新内容，> 0 = 向上滚动）
    private var scrollOffset: Int = 0
    
    // 滚动区域（top, bottom），null 表示整个屏幕
    private var scrollRegionTop: Int? = null
    private var scrollRegionBottom: Int? = null
    
    // 备用屏幕缓冲（用于全屏应用如 vi/nano）
    private val mainScreenLines = mutableListOf<TerminalLine>()  // 保存主屏幕内容
    private var useAlternateScreen: Boolean = false
    
    // 标记是否已经进行过首次 resize
    private var hasResized = false
    
    // Kitty Graphics Protocol: 图片存储
    private val images = mutableMapOf<Int, TerminalImage>()
    private val imagePlacements = mutableMapOf<Int, ImagePlacement>()
    
    // 用于累积多块传输的图片数据
    private data class ImageAccumulator(
        val data: StringBuilder = StringBuilder(),
        var width: Int = 0,
        var height: Int = 0,
        var format: TransmissionFormat = TransmissionFormat.PNG,
        var imageNumber: Int = 0,
        var unicodePlaceholder: Boolean = false  // 保存 U=1 参数
    )
    private val imageDataBuffer = mutableMapOf<Int, ImageAccumulator>()

    init {
        // 初始化可见行
        for (i in 0 until height) {
            visibleLines.add(TerminalLine(width))
        }
    }

    /**
     * 获取指定行的内容
     * @param lineIndex 行索引（0 = 最顶行，包括滚动历史）
     */
    fun getLine(lineIndex: Int): TerminalLine? {
        val totalLines = scrollbackLines.size + visibleLines.size
        if (lineIndex < 0 || lineIndex >= totalLines) {
            return null
        }

        return if (lineIndex < scrollbackLines.size) {
            scrollbackLines[lineIndex]
        } else {
            visibleLines[lineIndex - scrollbackLines.size]
        }
    }

    /**
     * 获取当前可见区域的行
     * @param visibleLineIndex 可见区域内的行索引（0 = 屏幕顶部）
     */
    fun getVisibleLine(visibleLineIndex: Int): TerminalLine? {
        if (visibleLineIndex < 0 || visibleLineIndex >= height) {
            return null
        }

        // 考虑滚动偏移
        val actualIndex = scrollbackLines.size + visibleLineIndex - scrollOffset
        return getLine(actualIndex)
    }

    /**
     * 获取当前编辑行（屏幕底部的行）
     */
    fun getCurrentLine(): TerminalLine {
        return visibleLines.last()
    }

    /**
     * 在当前位置添加新行（滚动屏幕）
     * 在主屏幕：将顶行移到 scrollback，在底部添加新行
     * 在 alternate screen：直接移除顶行（不使用 scrollback）
     */
    fun newLine() {
        // 如果没有设置滚动区域，滚动整个屏幕
        if (scrollRegionTop == null || scrollRegionBottom == null) {
            if (visibleLines.isNotEmpty()) {
                val topLine = visibleLines.removeAt(0)
                
                // 只在主屏幕模式下才使用 scrollback
                // alternate screen 不应该使用 scrollback（标准终端行为）
                if (!useAlternateScreen) {
                    scrollbackLines.add(topLine)
                    
                    // 限制 scrollback 大小
                    if (scrollbackLines.size > scrollbackSize) {
                        scrollbackLines.removeAt(0)
                    }
                }
                // 在 alternate screen 中，顶行直接丢弃，不保存
            }

            // 在底部添加新行
            visibleLines.add(TerminalLine(width))
            
            // 重置滚动偏移（显示最新内容）
            scrollOffset = 0
        } else {
            // 有滚动区域：只在滚动区域内滚动
            val top = scrollRegionTop!!
            val bottom = scrollRegionBottom!!
            
            println("      [TerminalBuffer] newLine() with scroll region: top=$top, bottom=$bottom, total lines=${visibleLines.size}")
            
            if (top < visibleLines.size && bottom < visibleLines.size && top <= bottom) {
                // 移除滚动区域的顶行
                val topLine = visibleLines.removeAt(top)
                
                // 在滚动区域底部插入新行（在 bottom 位置）
                visibleLines.add(bottom, TerminalLine(width))
                
                println("      [TerminalBuffer] Scrolled within region: removed line at $top, added at $bottom")
                
                // 注意：不使用 scrollback，因为这是滚动区域内的滚动
            }
        }
    }

    /**
     * 向上滚动
     */
    fun scrollUp(lines: Int = 1) {
        val maxScroll = scrollbackLines.size
        scrollOffset = (scrollOffset + lines).coerceAtMost(maxScroll)
    }

    /**
     * 向下滚动
     */
    fun scrollDown(lines: Int = 1) {
        scrollOffset = (scrollOffset - lines).coerceAtLeast(0)
    }

    /**
     * 滚动到顶部
     */
    fun scrollToTop() {
        scrollOffset = scrollbackLines.size
    }

    /**
     * 滚动到底部
     */
    fun scrollToBottom() {
        scrollOffset = 0
    }

    /**
     * 获取总行数（包括滚动历史）
     */
    fun totalLines(): Int = scrollbackLines.size + visibleLines.size

    /**
     * 获取当前滚动位置
     */
    fun getScrollOffset(): Int = scrollOffset

    /**
     * 是否在底部（显示最新内容）
     */
    fun isAtBottom(): Boolean = scrollOffset == 0

    /**
     * 清除所有内容
     */
    fun clear() {
        scrollbackLines.clear()
        visibleLines.clear()
        scrollOffset = 0
        for (i in 0 until height) {
            visibleLines.add(TerminalLine(width))
        }
    }

    /**
     * 获取缓冲区宽度
     */
    fun getWidth(): Int = width

    /**
     * 获取缓冲区高度（可见区域）
     */
    fun getHeight(): Int = height
    
    /**
     * 判断当前是否在 alternate screen 模式
     */
    fun isUsingAlternateScreen(): Boolean = useAlternateScreen
    
    /**
     * 调整缓冲区大小
     */
    fun resize(newWidth: Int, newHeight: Int) {
        println("      [TerminalBuffer] Resizing buffer from ${width}x${height} to ${newWidth}x${newHeight}")
        
        if (newWidth == width && newHeight == height) {
            return // 尺寸没变，不需要调整
        }
        
        // 只在第一次 resize 时清除 scrollback，避免初始化时的错位问题
        // 后续的 resize（用户拖动窗口）不应该清除已有内容
        if (!hasResized) {
            println("      [TerminalBuffer] First resize, clearing scrollback to avoid misalignment")
            scrollbackLines.clear()
            scrollOffset = 0
            hasResized = true
        }
        
        // 调整高度
        if (newHeight != height) {
            if (newHeight > height) {
                // 增加高度 - 在底部添加新行
                val linesToAdd = newHeight - height
                for (i in 0 until linesToAdd) {
                    visibleLines.add(TerminalLine(width))
                }
            } else {
                // 减少高度 - 移除底部的行，但保留内容
                val linesToRemove = height - newHeight
                for (i in 0 until linesToRemove) {
                    if (visibleLines.isNotEmpty()) {
                        val line = visibleLines.removeAt(visibleLines.size - 1)
                        // 如果行有内容，移到 scrollback
                        if (line.getCells().any { it.char != ' ' }) {
                            scrollbackLines.add(line)
                            if (scrollbackLines.size > scrollbackSize) {
                                scrollbackLines.removeAt(0)
                            }
                        }
                    }
                }
            }
            height = newHeight
        }
        
        // 调整宽度
        if (newWidth != width) {
            // 对于宽度改变，我们需要更新所有行的最大宽度
            // 由于 TerminalLine 的 maxWidth 是在构造时设置的，需要重新创建所有行
            
            // 重新创建所有可见行
            val newVisibleLines = mutableListOf<TerminalLine>()
            for (oldLine in visibleLines) {
                val newLine = TerminalLine(newWidth)
                // 复制现有内容
                val cells = oldLine.getCells()
                for (i in cells.indices) {
                    if (i < newWidth) {
                        newLine.setCell(i, cells[i])
                    }
                }
                newVisibleLines.add(newLine)
            }
            visibleLines.clear()
            visibleLines.addAll(newVisibleLines)
            
            // 重新创建主屏幕保存区域的所有行
            val newMainScreenLines = mutableListOf<TerminalLine>()
            for (oldLine in mainScreenLines) {
                val newLine = TerminalLine(newWidth)
                // 复制现有内容
                val cells = oldLine.getCells()
                for (i in cells.indices) {
                    if (i < newWidth) {
                        newLine.setCell(i, cells[i])
                    }
                }
                newMainScreenLines.add(newLine)
            }
            mainScreenLines.clear()
            mainScreenLines.addAll(newMainScreenLines)
            
            // 重新创建 scrollback 中的所有行
            val newScrollbackLines = mutableListOf<TerminalLine>()
            for (oldLine in scrollbackLines) {
                val newLine = TerminalLine(newWidth)
                // 复制现有内容
                val cells = oldLine.getCells()
                for (i in cells.indices) {
                    if (i < newWidth) {
                        newLine.setCell(i, cells[i])
                    }
                }
                newScrollbackLines.add(newLine)
            }
            scrollbackLines.clear()
            scrollbackLines.addAll(newScrollbackLines)
            
            width = newWidth
        }
        
        println("      [TerminalBuffer] Resize complete, new size: ${width}x${height}, visible lines: ${visibleLines.size}")
    }
    
    /**
     * 设置滚动区域
     */
    fun setScrollRegion(top: Int, bottom: Int) {
        scrollRegionTop = top.coerceIn(0, height - 1)
        scrollRegionBottom = bottom.coerceIn(0, height - 1)
        println("      [TerminalBuffer] Set scroll region: top=$scrollRegionTop, bottom=$scrollRegionBottom (height=$height)")
    }
    
    /**
     * 获取滚动区域底部行号
     */
    fun getScrollRegionBottom(): Int? {
        return scrollRegionBottom
    }
    
    /**
     * 获取滚动区域顶部行号
     */
    fun getScrollRegionTop(): Int? {
        return scrollRegionTop
    }
    
    /**
     * 在指定位置插入一个新的空行（用于 Reverse Index）
     * 如果有滚动区域，移除滚动区域底部的行
     * 否则，移除屏幕底部的行
     */
    fun insertLineAt(lineIndex: Int) {
        if (lineIndex < 0 || lineIndex >= visibleLines.size) {
            return
        }
        
        if (scrollRegionTop == null || scrollRegionBottom == null) {
            // 没有滚动区域：在指定位置插入，移除底部行
            visibleLines.add(lineIndex, TerminalLine(width))
            if (visibleLines.size > height) {
                visibleLines.removeAt(visibleLines.size - 1)
            }
        } else {
            // 有滚动区域：在指定位置插入，移除滚动区域底部行
            val top = scrollRegionTop!!
            val bottom = scrollRegionBottom!!
            
            if (lineIndex in top..bottom) {
                // 在滚动区域内插入
                visibleLines.add(lineIndex, TerminalLine(width))
                // 移除滚动区域底部的行（bottom+1位置，因为插入后索引后移）
                if (bottom + 1 < visibleLines.size) {
                    visibleLines.removeAt(bottom + 1)
                }
                println("      [TerminalBuffer] insertLineAt($lineIndex) in scroll region: top=$top, bottom=$bottom")
            }
        }
    }
    
    /**
     * 清除滚动区域（使用整个屏幕）
     */
    fun clearScrollRegion() {
        scrollRegionTop = null
        scrollRegionBottom = null
    }
    
    /**
     * 在滚动区域内向上滚动
     */
    fun scrollUpInRegion(lines: Int = 1) {
        val top = scrollRegionTop ?: 0
        val bottom = scrollRegionBottom ?: (height - 1)
        val actualLines = (scrollbackLines.size + visibleLines.size)
        
        // 在指定区域内滚动
        if (top < bottom) {
            // 将区域内最底行移到 scrollback
            if (bottom < visibleLines.size) {
                val lineToRemove = visibleLines.removeAt(bottom)
                scrollbackLines.add(lineToRemove)
                if (scrollbackLines.size > scrollbackSize) {
                    scrollbackLines.removeAt(0)
                }
                // 在顶部插入空行
                visibleLines.add(top, TerminalLine(width))
            }
        }
    }
    
    /**
     * 在滚动区域内向下滚动
     */
    fun scrollDownInRegion(lines: Int = 1) {
        val top = scrollRegionTop ?: 0
        val bottom = scrollRegionBottom ?: (height - 1)
        
        // TODO: 实现向下滚动
    }
    
    /**
     * 切换到备用屏幕
     */
    fun switchToAlternateScreen() {
        if (!useAlternateScreen) {
            println("      [TerminalBuffer] Switching to alternate screen, saving main screen (${visibleLines.size} lines)")
            
            // 调试：检查保存的内容是否为空
            val nonEmptyLines = visibleLines.count { line -> 
                line.getCells().any { cell -> cell.char != ' ' && cell.char != '\u0000' }
            }
            println("      [TerminalBuffer] Main screen has $nonEmptyLines non-empty lines (out of ${visibleLines.size})")
            
            // 保存当前主屏幕内容
            mainScreenLines.clear()
            mainScreenLines.addAll(visibleLines.map { it.copy() })
            
            // 清空visibleLines，准备给备用屏幕使用
            visibleLines.clear()
            for (i in 0 until height) {
                visibleLines.add(TerminalLine(width))
            }
            
            useAlternateScreen = true
            println("      [TerminalBuffer] Switched to alternate screen, main screen saved")
            
            // 触发UI刷新
            onScreenChanged?.invoke()
        }
    }
    
    /**
     * 切换回主屏幕
     */
    fun switchToMainScreen() {
        if (useAlternateScreen) {
            println("      [TerminalBuffer] Switching to main screen, restoring ${mainScreenLines.size} lines")
            
            // 调试：检查要恢复的内容
            val nonEmptyLines = mainScreenLines.count { line -> 
                line.getCells().any { cell -> cell.char != ' ' && cell.char != '\u0000' }
            }
            println("      [TerminalBuffer] Restoring $nonEmptyLines non-empty lines (out of ${mainScreenLines.size})")
            
            // 恢复主屏幕内容
            visibleLines.clear()
            visibleLines.addAll(mainScreenLines.map { it.copy() })
            mainScreenLines.clear()
            
            useAlternateScreen = false
            println("      [TerminalBuffer] Switched to main screen, restored successfully")
            
            // 触发UI刷新
            onScreenChanged?.invoke()
        }
    }
    
    // ========== Kitty Graphics Protocol支持 ==========
    
    /**
     * 添加图片数据（支持分块传输）
     */
    fun addImage(
        imageId: Int,
        imageNumber: Int,
        format: TransmissionFormat,
        width: Int,
        height: Int,
        data: String,
        isMore: Boolean,
        unicodePlaceholder: Boolean = false,
        cursorX: Int = 0,  // 当前光标位置，用于立即写入placeholder
        cursorY: Int = 0,
        columns: Int = 0,  // 图片显示的列数
        rows: Int = 0      // 图片显示的行数
    ) {
        try {
            if (isMore) {
                // 累积数据块
                val accumulator = imageDataBuffer.getOrPut(imageId) { ImageAccumulator() }
                val isFirstChunk = accumulator.data.isEmpty()
                accumulator.data.append(data)
                
                // 保存第一块的元数据（只在width>0时更新，避免被后续块的0覆盖）
                if (width > 0) accumulator.width = width
                if (height > 0) accumulator.height = height
                if (accumulator.format == TransmissionFormat.PNG) accumulator.format = format  // 只设置一次
                if (accumulator.imageNumber == 0) accumulator.imageNumber = imageNumber
                // 保存 unicodePlaceholder（只设置一次，从第一个chunk）
                if (!accumulator.unicodePlaceholder && unicodePlaceholder) {
                    accumulator.unicodePlaceholder = true
                }
                
                // Yazi 会自己写入 placeholders，终端不主动写入
                
                // 减少日志噪音，不输出每个chunk
            } else {
                // 最后一块或单块传输
                val accumulator = imageDataBuffer.remove(imageId)
                val fullData: String
                val finalWidth: Int
                val finalHeight: Int
                val finalFormat: TransmissionFormat
                val finalImageNumber: Int
                val finalUnicodePlaceholder: Boolean
                
                if (accumulator != null) {
                    // 有累积数据 - 合并最后一块
                    accumulator.data.append(data)
                    fullData = accumulator.data.toString()
                    finalWidth = if (accumulator.width > 0) accumulator.width else width
                    finalHeight = if (accumulator.height > 0) accumulator.height else height
                    finalFormat = accumulator.format
                    finalImageNumber = accumulator.imageNumber
                    finalUnicodePlaceholder = accumulator.unicodePlaceholder  // 使用保存的值
                    println("      [TerminalBuffer] Multi-chunk complete, totalLength=${fullData.length}, size=${finalWidth}x${finalHeight}, U=${finalUnicodePlaceholder}")
                } else {
                    // 单块传输
                    fullData = data
                    finalWidth = width
                    finalHeight = height
                    finalFormat = format
                    finalImageNumber = imageNumber
                    finalUnicodePlaceholder = unicodePlaceholder
                    println("      [TerminalBuffer] Single-chunk transmission, dataLength=${fullData.length}")
                }
                
                // 创建图片对象
                val image = TerminalImage(
                    id = imageId,
                    imageNumber = finalImageNumber,
                    format = finalFormat,
                    width = finalWidth,
                    height = finalHeight,
                    data = fullData.toByteArray(),  // base64字符串转字节数组
                    isBase64 = true,
                    unicodePlaceholder = finalUnicodePlaceholder
                )
                
                images[imageId] = image
                println("      [TerminalBuffer] ✓ Image stored: id=$imageId, size=${finalWidth}x${finalHeight}")
            }
        } catch (e: Exception) {
            println("      [TerminalBuffer] Error adding image: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 放置图片到终端
     */
    fun placeImage(
        imageId: Int,
        placementId: Int,
        x: Int,
        y: Int,
        columns: Int,
        rows: Int,
        zIndex: Int
    ) {
        try {
            val image = images[imageId]
            if (image == null) {
                // 图片还未完全接收，静默跳过（Yazi会在每个chunk都尝试放置）
                return
            }
            
            val placement = ImagePlacement(
                placementId = placementId,
                imageId = imageId,
                x = x,
                y = y,
                columns = columns,
                rows = rows,
                zIndex = zIndex
            )
            
            imagePlacements[placementId] = placement
            
            // 注意：如果图片有unicodePlaceholder=true，占位符已经在第一个chunk时写入了
            // 这里不需要再次写入，因为：
            // 1. 时机太晚（图片传输完成后），Yazi已经清屏并需要重新布局
            // 2. 会覆盖Yazi已经绘制的文本
            // if (image.unicodePlaceholder) {
            //     writePlaceholders(image, x, y, columns, rows)
            // }
            
            // println("      [TerminalBuffer] Image placed: id=$imageId at ($x,$y)")
        } catch (e: Exception) {
            println("      [TerminalBuffer] Error placing image: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 在指定区域写入 Unicode Placeholder 字符 (U+10EEEE)
     * 根据 Kitty Graphics Protocol，当 U=1 时，应该在图片位置写入这些占位符
     */
    private fun writePlaceholders(image: TerminalImage, startX: Int, startY: Int, columns: Int, rows: Int) {
        writePlaceholdersWithDimensions(startX, startY, columns, rows, image.width, image.height)
    }
    
    /**
     * 根据像素尺寸写入 Unicode Placeholder
     * 用于在图片传输的第一个chunk时立即写入占位符（图片对象还不存在）
     */
    private fun writePlaceholdersWithDimensions(startX: Int, startY: Int, columns: Int, rows: Int, pixelWidth: Int, pixelHeight: Int) {
        // 计算实际占用的网格大小
        val actualColumns = if (columns > 0) columns else {
            // 如果没有指定列数，根据图片像素宽度和字符宽度计算
            // 假设 charWidth = 9.0 (从 TerminalView 获取，这里硬编码为常见值)
            val charWidth = 9.0
            kotlin.math.ceil(pixelWidth / charWidth).toInt()
        }
        val actualRows = if (rows > 0) rows else {
            // 如果没有指定行数，根据图片像素高度和字符高度计算
            // 假设 charHeight = 18.0 (从 TerminalView 获取，这里硬编码为常见值)
            val charHeight = 18.0
            kotlin.math.ceil(pixelHeight / charHeight).toInt()
        }
        
        // Unicode Placeholder: U+10EEEE (Private Use Character)
        val placeholder = '\uDBFF' // High surrogate for U+10EEEE
        val placeholderLow = '\uDEEE' // Low surrogate for U+10EEEE
        
        println("      [TerminalBuffer] Writing placeholders at ($startX,$startY), size ${actualColumns}x${actualRows} (pixel: ${pixelWidth}x${pixelHeight})")
        
        // 在指定区域写入 placeholder 字符
        for (row in 0 until actualRows) {
            val lineY = startY + row
            if (lineY < 0 || lineY >= visibleLines.size) continue
            
            val line = visibleLines[lineY]
            for (col in 0 until actualColumns) {
                val cellX = startX + col
                if (cellX < 0 || cellX >= width) continue
                
                // 写入 placeholder（使用 low surrogate，因为 Kitty 使用这个来标记）
                line.setCell(cellX, TerminalCell(
                    char = placeholderLow,
                    foregroundColor = TerminalColor.Default,
                    backgroundColor = TerminalColor.Default
                ))
            }
        }
    }
    
    /**
     * 删除图片
     * @param deleteMode 删除模式 (a=all, i=id, p=placement, 等)
     */
    fun deleteImages(deleteMode: Char, imageId: Int, placementId: Int) {
        try {
            when (deleteMode) {
                'a', 'A' -> {
                    // 删除所有图片
                    images.clear()
                    imagePlacements.clear()
                    imageDataBuffer.clear()
                    println("      [TerminalBuffer] All images deleted")
                }
                'i', 'I' -> {
                    // 删除指定ID的图片
                    images.remove(imageId)
                    imagePlacements.values.removeIf { it.imageId == imageId }
                    println("      [TerminalBuffer] Image deleted: imageId=$imageId")
                }
                'p', 'P' -> {
                    // 删除指定放置
                    imagePlacements.remove(placementId)
                    println("      [TerminalBuffer] Placement deleted: placementId=$placementId")
                }
                'c', 'C' -> {
                    // 删除光标处的图片（TODO: 实现）
                    println("      [TerminalBuffer] Delete at cursor not yet implemented")
                }
                else -> {
                    println("      [TerminalBuffer] Unknown delete mode: $deleteMode")
                }
            }
        } catch (e: Exception) {
            println("      [TerminalBuffer] Error deleting images: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 获取所有图片放置（用于渲染）
     */
    fun getImagePlacements(): List<ImagePlacement> {
        return imagePlacements.values.toList().sortedBy { it.zIndex }
    }
    
    /**
     * 获取图片数据
     */
    fun getImage(imageId: Int): TerminalImage? {
        return images[imageId]
    }
}
