package cn.hjhw.ssh.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.hjhw.ssh.connection.SshConnection
import cn.hjhw.ssh.terminal.AnsiParser
import cn.hjhw.ssh.terminal.CursorState
import cn.hjhw.ssh.terminal.TerminalBuffer
import cn.hjhw.ssh.terminal.TerminalCell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * 终端视图模型 - 连接 SSH 连接、终端缓冲区和 UI 的桥梁
 *
 * 作为 MVVM 架构中的 ViewModel，负责管理终端的业务逻辑和状态。
 * 主要职责包括：
 *
 * ### 数据流管理
 * ```
 * SSH 输出 → observeOutput() → AnsiParser → TerminalBuffer → UI 状态更新
 * 用户输入 → handleKeyInput() → KeyMapper → SSH 连接
 * ```
 *
 * ### 核心组件
 * - **TerminalBuffer**: 管理终端内容和历史
 * - **CursorState**: 管理光标位置和样式
 * - **AnsiParser**: 解析 ANSI 转义序列
 * - **KeyMapper**: 将 Compose 键盘事件映射为终端按键序列
 *
 * ### UI 状态管理
 * - **terminalWidth/Height**: 终端尺寸（列数×行数）
 * - **cursorVisible**: 光标可见性（支持闪烁效果）
 * - **refreshTrigger**: UI 刷新触发器（用于强制重绘）
 * - **selectedText**: 选中的文本（复制功能，开发中）
 *
 * ### 生命周期管理
 * 1. **创建**: 初始化缓冲区、光标和解析器
 * 2. **连接**: `startConnection()` - 建立 SSH 连接并开始接收数据
 * 3. **调整大小**: `resize()` - 动态调整终端窗口
 * 4. **清理**: `dispose()` - 关闭连接和协程
 *
 * ### 并发处理
 * - 使用 Kotlin Coroutines 处理异步操作
 * - `SupervisorJob` 确保单个任务失败不影响其他任务
 * - `Dispatchers.Main` 确保 UI 状态更新在主线程
 *
 * @param connection SSH 连接实例
 * @param scope 协程作用域，默认使用 Main 线程
 *
 * @see TerminalBuffer 终端缓冲区
 * @see AnsiParser ANSI 解析器
 * @see CursorState 光标状态
 * @see SshConnection SSH 连接接口
 */
class TerminalViewModel(
    private val connection: SshConnection,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val buffer = TerminalBuffer(80, 24, scrollbackSize = 1000)
    private val cursor = CursorState()
    private val parser = AnsiParser()
    private val keyMapper = cn.hjhw.ssh.terminal.KeyMapper()
    
    private var outputJob: Job? = null
    private var cursorBlinkJob: Job? = null
    
    // 标记第一次 resize 是否完成
    private var initialResizeDone = false
    // 标记是否已连接
    private var isConnected = false
    // 标记是否已启动 output observer
    private var observerStarted = false
    
    // UI 状态
    var terminalWidth by mutableStateOf(80)
        private set
    var terminalHeight by mutableStateOf(24)
        private set
    var cursorVisible by mutableStateOf(true)
        private set
    var selectedText by mutableStateOf<String?>(null)
        private set
    
    // 文本选择状态
    data class TextSelection(
        val startX: Int,
        val startY: Int,
        val endX: Int,
        val endY: Int
    )
    
    var textSelection by mutableStateOf<TextSelection?>(null)
        private set
    
    // UI 刷新触发器 - 用于强制重绘（例如切换备用屏幕后）
    var refreshTrigger by mutableStateOf(0)
        private set
    
    /**
     * 手动触发UI刷新
     */
    fun forceRefresh() {
        refreshTrigger++
    }
    
    init {
        println("[TerminalViewModel] Initialized with size ${terminalWidth}x${terminalHeight}")
        println("[TerminalViewModel] Buffer size: ${buffer.getWidth()}x${buffer.getHeight()}")
        
        // 设置屏幕切换回调 - 强制刷新UI
        buffer.setOnScreenChangedCallback {
            println("      [TerminalViewModel] Screen changed, forcing UI refresh")
            forceRefresh()
        }
        
        startCursorBlink()
    }
    
    /**
     * 连接到 SSH 服务器
     */
    suspend fun connect() {
        // 等待连接完成
        connection.connect()
        isConnected = true
        
        // 等待一小段时间，确保 shell 已经创建
        kotlinx.coroutines.delay(200)
        
        // 通知服务器当前终端尺寸
        connection.resize(terminalWidth, terminalHeight)
        
        // ✅ 修复：无论 initialResizeDone 状态如何，都启动 observer
        // 原因：后台标签页可能不会触发 UI resize 事件
        if (!observerStarted) {
            observerStarted = true
            
            // 如果还没有 resize，使用默认尺寸
            if (!initialResizeDone) {
                initialResizeDone = true  // 标记为已完成，避免后续重复处理
            }
            
            startOutputObserver()
        }
    }
    
    /**
     * 获取缓冲区
     */
    fun getBuffer(): TerminalBuffer = buffer
    
    /**
     * 获取光标状态
     */
    fun getCursor(): CursorState = cursor
    
    /**
     * 获取键盘映射器
     */
    fun getKeyMapper(): cn.hjhw.ssh.terminal.KeyMapper = keyMapper
    
    /**
     * 处理输入
     */
    fun handleInput(text: String) {
        scope.launch {
            connection.write(text)
        }
    }
    
    /**
     * 处理输入字节
     */
    fun handleInputBytes(bytes: ByteArray) {
        // 不进行本地处理，完全依赖服务器的回显和响应
        // 这样可以避免重复输入和删除的问题
        // 服务器会处理输入并返回相应的输出（包括回显和删除序列）
        
        // 调试：打印发送的字节（已关闭）
        // val hexString = bytes.joinToString(" ") { "0x%02X".format(it) }
        // val charString = bytes.map { 
        //     if (it in 0x20..0x7E) it.toInt().toChar().toString() 
        //     else if (it == 0x0D.toByte()) "\\r"
        //     else if (it == 0x0A.toByte()) "\\n"
        //     else if (it == 0x09.toByte()) "\\t"
        //     else if (it == 0x7F.toByte()) "DEL"
        //     else if (it == 0x08.toByte()) "BS"
        //     else if (it == 0x1B.toByte()) "ESC"
        //     else "?"
        // }.joinToString("")
        // println(">>> Sending to server: $hexString ($charString)")
        
        // 发送到服务器
        scope.launch {
            connection.writeBytes(bytes)
        }
    }
    
    /**
     * 调整终端大小
     */
    fun resize(width: Int, height: Int) {
        terminalWidth = width
        terminalHeight = height
        buffer.resize(width, height)
        
        // 标记第一次 resize 已完成（buffer 已经是正确尺寸）
        if (!initialResizeDone) {
            initialResizeDone = true
        }
        
        // 通知 SSH 服务器终端尺寸变化
        scope.launch {
            try {
                connection.resize(width, height)
            } catch (e: Exception) {
                println(">>> [TerminalViewModel] SSH resize failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 向上滚动
     */
    fun scrollUp(lines: Int = 1) {
        buffer.scrollUp(lines)
    }
    
    /**
     * 向下滚动
     */
    fun scrollDown(lines: Int = 1) {
        buffer.scrollDown(lines)
    }
    
    /**
     * 滚动到底部
     */
    fun scrollToBottom() {
        buffer.scrollToBottom()
    }
    
    /**
     * 复制选中的文本
     */
    fun copySelectedText(): String? {
        return selectedText
    }
    
    /**
     * 粘贴文本
     */
    fun pasteText(text: String) {
        handleInput(text)
    }
    
    /**
     * 清屏
     */
    fun clearScreen() {
        buffer.clear()
        cursor.moveTo(0, 0)
    }
    
    /**
     * 从光标到屏幕末尾清除
     */
    private fun eraseFromCursorToEnd() {
        val width = terminalWidth
        val height = terminalHeight
        
        // 清除光标所在行的剩余部分
        val cursorLine = buffer.getVisibleLine(cursor.y)
        if (cursorLine != null) {
            for (x in cursor.x until width) {
                val emptyCell = TerminalCell(
                    char = ' ',
                    foregroundColor = cursor.currentStyle.foregroundColor,
                    backgroundColor = cursor.currentStyle.backgroundColor,
                )
                cursorLine.setCell(x, emptyCell)
            }
        }
        
        // 清除光标下方的所有行
        for (y in (cursor.y + 1) until height) {
            val line = buffer.getVisibleLine(y)
            if (line != null) {
                for (x in 0 until width) {
                    val emptyCell = TerminalCell(
                        char = ' ',
                        foregroundColor = cursor.currentStyle.foregroundColor,
                        backgroundColor = cursor.currentStyle.backgroundColor,
                    )
                    line.setCell(x, emptyCell)
                }
            }
        }
    }
    
    /**
     * 从屏幕开头到光标清除
     */
    private fun eraseFromStartToCursor() {
        val width = terminalWidth
        
        // 清除光标上方的所有行
        for (y in 0 until cursor.y) {
            val line = buffer.getVisibleLine(y)
            if (line != null) {
                for (x in 0 until width) {
                    val emptyCell = TerminalCell(
                        char = ' ',
                        foregroundColor = cursor.currentStyle.foregroundColor,
                        backgroundColor = cursor.currentStyle.backgroundColor,
                    )
                    line.setCell(x, emptyCell)
                }
            }
        }
        
        // 清除光标所在行到光标位置
        val cursorLine = buffer.getVisibleLine(cursor.y)
        if (cursorLine != null) {
            for (x in 0..cursor.x) {
                val emptyCell = TerminalCell(
                    char = ' ',
                    foregroundColor = cursor.currentStyle.foregroundColor,
                    backgroundColor = cursor.currentStyle.backgroundColor,
                )
                cursorLine.setCell(x, emptyCell)
            }
        }
    }
    
    /**
     * 开始观察输出
     */
    private fun startOutputObserver() {
        outputJob?.cancel()
        outputJob = connection.observeOutput()
            .onEach { data ->
                parseAndUpdateBuffer(data)
            }
            .launchIn(scope)
    }
    
    /**
     * 解析并更新缓冲区
     */
    private fun parseAndUpdateBuffer(data: String) {
        // 调试日志已关闭（性能优化）
        // if (buffer.isUsingAlternateScreen() && data.isNotEmpty()) {
        //     println("<<< [AltScreen] Received (${data.length} bytes)")
        //     println("    Cursor before: (${cursor.x}, ${cursor.y})")
        // }
        
        parser.parse(
            input = data,
            cursor = cursor,
            buffer = buffer,
            onResponse = { response ->
                // 发送响应到SSH（例如Kitty Graphics Query响应）
                scope.launch {
                    connection.write(response)
                    println("      [TerminalViewModel] Sent response to SSH: ${response.take(50)}...")
                }
            },
            onText = { char, style ->
                // 使用 buffer 的实际高度，确保与 buffer 同步
                val bufferHeight = buffer.getHeight()
                val bufferWidth = buffer.getWidth()
                
                // 【延迟换行】如果有待定的换行，先执行换行再写入字符
                if (cursor.wrapPending) {
                    // 延迟换行：在写入新字符前执行换行
                    cursor.x = 0
                    cursor.y++
                    cursor.wrapPending = false
                    
                    if (cursor.y >= bufferHeight) {
                        // 在 alternate screen 模式下，不应该滚动
                        if (buffer.isUsingAlternateScreen()) {
                            cursor.y = bufferHeight - 1
                        } else {
                            buffer.newLine()
                            cursor.y = bufferHeight - 1
                        }
                    }
                }
                
                // 确保光标在有效范围内
                if (cursor.y < 0) {
                    cursor.y = 0
                }
                if (cursor.y >= bufferHeight) {
                    if (buffer.isUsingAlternateScreen()) {
                        cursor.y = bufferHeight - 1
                    } else {
                        buffer.newLine()
                        cursor.y = bufferHeight - 1
                    }
                }
                
                // 确保光标 X 在有效范围内
                if (cursor.x >= bufferWidth) {
                    cursor.x = bufferWidth - 1
                }
                
                
                // 调试日志已关闭（性能优化）
                // if (buffer.isUsingAlternateScreen() && char != ' ') {
                //     val charDisplay = if (char == '\n') "\\n" else if (char == '\r') "\\r" else char.toString()
                //     println("      [WriteChar] Writing '$charDisplay' at (${cursor.x}, ${cursor.y})")
                // }
                
                // 检测是否是宽字符（如中文，占2个单元格）
                val isWide = cn.hjhw.ssh.terminal.isWideChar(char)
                
                // 获取当前行
                val line = buffer.getVisibleLine(cursor.y) ?: buffer.getCurrentLine()
                
                // 写入主字符单元格
                val cell = TerminalCell(
                    char = char,
                    foregroundColor = style.foregroundColor,
                    backgroundColor = style.backgroundColor,
                    bold = style.bold,
                    faint = style.faint,
                    italic = style.italic,
                    underline = style.underline,
                    strikethrough = style.strikethrough,
                    blink = style.blink,
                    reverse = style.reverse,
                    invisible = style.invisible,
                    isWideChar = isWide,
                )
                line.setCell(cursor.x, cell)
                
                // 如果是宽字符，需要在下一个单元格写入延续标记
                if (isWide && cursor.x + 1 < bufferWidth) {
                    val continuationCell = TerminalCell(
                        char = ' ',  // 延续单元格不显示字符
                        foregroundColor = style.foregroundColor,
                        backgroundColor = style.backgroundColor,
                        isWideContinuation = true,
                    )
                    line.setCell(cursor.x + 1, continuationCell)
                }
                
                // 移动光标（宽字符移动2格，普通字符移动1格）
                val charWidth = if (isWide) 2 else 1
                cursor.x += charWidth
                
                // 【延迟换行】当光标到达或超出行尾时，设置 wrapPending 标志
                if (cursor.x >= bufferWidth) {
                    // 延迟换行：光标到达行尾时不立即换行
                    if (cursor.x == bufferWidth) {
                        cursor.wrapPending = true
                    } else {
                        // 宽字符导致超出，立即换行
                        cursor.x = 0
                        cursor.y++
                        cursor.wrapPending = false
                        
                        if (cursor.y >= bufferHeight) {
                            if (buffer.isUsingAlternateScreen()) {
                                cursor.y = bufferHeight - 1
                            } else {
                                buffer.newLine()
                                cursor.y = bufferHeight - 1
                            }
                        }
                    }
                }
            },
            onControlSequence = { sequence ->
                when (sequence) {
                    is cn.hjhw.ssh.terminal.ControlSequence.EraseDisplay -> {
                        when (sequence.mode) {
                            0 -> {
                                // 从光标到屏幕末尾
                                eraseFromCursorToEnd()
                            }
                            1 -> {
                                // 从光标到屏幕开头
                                eraseFromStartToCursor()
                            }
                            2, 3 -> {
                                clearScreen()
                            }
                        }
                    }
                    is cn.hjhw.ssh.terminal.ControlSequence.EraseLine -> {
                        // 清行已经在解析器中处理了
                    }
                    is cn.hjhw.ssh.terminal.ControlSequence.ScrollUp -> {
                        buffer.scrollUp(sequence.lines)
                    }
                    is cn.hjhw.ssh.terminal.ControlSequence.ScrollDown -> {
                        buffer.scrollDown(sequence.lines)
                    }
                    else -> {
                        // 其他控制序列
                    }
                }
            },
        )
        
        // 调试日志已关闭（性能优化）
        // if (buffer.isUsingAlternateScreen() && data.isNotEmpty()) {
        //     println("    Cursor after: (${cursor.x}, ${cursor.y})")
        // }
        
        // 触发 UI 刷新（确保 Compose 重绘 Canvas）
        refreshTrigger++
    }
    
    /**
     * 启动光标闪烁动画
     */
    private fun startCursorBlink() {
        cursorBlinkJob?.cancel()
        cursorBlinkJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(500) // 500ms 闪烁间隔
                cursorVisible = !cursorVisible
            }
        }
    }
    
    /**
     * 开始文本选择
     */
    fun startSelection(x: Int, y: Int) {
        textSelection = TextSelection(x, y, x, y)
    }
    
    /**
     * 更新文本选择范围
     */
    fun updateSelection(x: Int, y: Int) {
        textSelection?.let { selection ->
            textSelection = selection.copy(endX = x, endY = y)
        }
    }
    
    /**
     * 结束文本选择并提取选中的文本
     */
    fun endSelection(): String? {
        val selection = textSelection ?: return null
        
        // 确定选择的起点和终点（处理反向选择）
        val normalized = normalizeSelection(selection)
        val (startX, startY) = normalized.first
        val (endX, endY) = normalized.second
        
        // 提取选中的文本
        val selectedText = extractSelectedText(startX, startY, endX, endY)
        
        // 保存选中的文本（用于显示高亮）
        this.selectedText = selectedText
        
        return selectedText
    }
    
    /**
     * 清除文本选择
     */
    fun clearSelection() {
        textSelection = null
        selectedText = null
    }
    
    /**
     * 规范化选择范围（确保起点在终点之前）
     * @return Pair of (startX, startY) to (endX, endY)
     */
    private fun normalizeSelection(selection: TextSelection): Pair<Pair<Int, Int>, Pair<Int, Int>> {
        val (sx, sy, ex, ey) = selection.run {
            listOf(startX, startY, endX, endY)
        }
        
        return if (sy < ey || (sy == ey && sx <= ex)) {
            // 正向选择
            Pair(Pair(sx, sy), Pair(ex, ey))
        } else {
            // 反向选择
            Pair(Pair(ex, ey), Pair(sx, sy))
        }
    }
    
    /**
     * 从终端缓冲区提取选中的文本
     */
    private fun extractSelectedText(startX: Int, startY: Int, endX: Int, endY: Int): String {
        val lines = mutableListOf<String>()
        
        for (y in startY..endY) {
            val line = buffer.getLine(y)
            if (line == null) continue
            
            val cells = line.getCells()
            
            val lineText = if (y == startY && y == endY) {
                // 单行选择
                extractLineText(cells, startX, endX)
            } else if (y == startY) {
                // 第一行
                extractLineText(cells, startX, cells.size - 1)
            } else if (y == endY) {
                // 最后一行
                extractLineText(cells, 0, endX)
            } else {
                // 中间行
                extractLineText(cells, 0, cells.size - 1)
            }
            
            lines.add(lineText)
        }
        
        return lines.joinToString("\n")
    }
    
    /**
     * 从行中提取指定范围的文本
     */
    private fun extractLineText(cells: List<cn.hjhw.ssh.terminal.TerminalCell>, startX: Int, endX: Int): String {
        val sb = StringBuilder()
        for (x in startX..endX.coerceAtMost(cells.size - 1)) {
            val cell = cells.getOrNull(x)
            if (cell != null && cell.char != '\u0000') {
                sb.append(cell.char)
            }
        }
        return sb.toString().trimEnd()
    }
    
    /**
     * 清理资源
     */
    fun dispose() {
        outputJob?.cancel()
        cursorBlinkJob?.cancel()
        scope.cancel()
    }
}

