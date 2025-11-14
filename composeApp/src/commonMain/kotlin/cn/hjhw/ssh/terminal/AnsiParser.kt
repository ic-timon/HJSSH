package cn.hjhw.ssh.terminal

/**
 * ANSI/VT100 转义序列解析器
 *
 * 负责解析 SSH 服务器发送的 ANSI/VT100 转义序列，并将其转换为终端操作。
 * 支持的主要协议和序列：
 *
 * ### 基础控制序列
 * - **CSI (Control Sequence Introducer)**: `ESC [` - 用于光标移动、文本样式、屏幕操作等
 * - **OSC (Operating System Command)**: `ESC ]` - 用于设置终端标题、颜色等
 * - **SGR (Select Graphic Rendition)**: `ESC[...m` - 文本样式和颜色
 *
 * ### 图形协议
 * - **DCS (Device Control String)**: `ESC P` - 用于 Sixel 图形协议
 * - **APC (Application Program Command)**: `ESC _` - 用于 Kitty Graphics Protocol
 *
 * ### 字符集
 * - SI/SO (Shift In/Out): 字符集切换
 * - G0/G1 字符集选择
 *
 * ### 终端特性
 * - 交替屏幕缓冲区 (SMCUP/RMCUP)
 * - 滚动区域 (DECSTBM)
 * - 光标保存/恢复
 * - 终端查询和响应 (DA, XTVERSION, DECRQM 等)
 *
 * @see TerminalBuffer 终端缓冲区管理
 * @see CursorState 光标状态管理
 */
class AnsiParser {
    private enum class ParseState {
        NORMAL,
        ESCAPE,
        CSI, // Control Sequence Introducer: ESC [
        OSC, // Operating System Command: ESC ]
        PARAMETER,
        CHARSET, // Character Set Selection: ESC ( or ESC )
        DCS, // Device Control String: ESC P (用于Sixel等)
        PM, // Privacy Message: ESC ^
        APC, // Application Program Command: ESC _ (用于Kitty Graphics)
        APC_KITTY, // Kitty Graphics Protocol: ESC _ G ...
    }

    private var state = ParseState.NORMAL
    private var previousState = ParseState.NORMAL  // 保存前一个状态（用于处理ESC \）
    private val parameters = mutableListOf<Int>()
    private var currentParameter = StringBuilder()
    private val oscData = StringBuilder()
    private val apcData = StringBuilder()  // APC数据缓冲区（用于Kitty Graphics）
    private var isPrivateMode = false  // DEC 私有模式标记
    private var intermediateChar: Char? = null  // CSI 中间字符 (如 >, =, $, 空格等)

    /**
     * 解析输入数据并触发相应的终端操作
     *
     * 这是解析器的核心函数，使用状态机逐字符解析输入流。
     * 根据不同的转义序列，调用相应的回调函数来更新终端状态。
     *
     * @param input 从 SSH 服务器接收到的原始输入字符串
     * @param cursor 当前光标状态，用于光标相关的操作
     * @param buffer 终端缓冲区，用于屏幕内容和图像管理
     * @param onText 文本字符回调，当接收到普通文本字符时调用，参数为字符和当前样式
     * @param onControlSequence 控制序列回调，当解析到控制序列时调用
     * @param onResponse 响应回调，用于向 SSH 服务器发送终端响应（如查询应答）
     *
     * ### 解析流程
     * 1. 逐字符读取输入
     * 2. 根据当前状态 (NORMAL, ESCAPE, CSI 等) 进行状态转换
     * 3. 收集参数（数字、中间字符等）
     * 4. 当收到终结字符时，执行相应操作并重置状态
     *
     * ### 状态说明
     * - **NORMAL**: 普通文本输入状态
     * - **ESCAPE**: ESC 后的状态，等待下一个字符确定序列类型
     * - **CSI**: Control Sequence Introducer (ESC [) 状态
     * - **OSC**: Operating System Command (ESC ]) 状态
     * - **DCS**: Device Control String (ESC P) 状态，用于 Sixel
     * - **APC**: Application Program Command (ESC _) 状态，用于 Kitty Graphics
     * - **APC_KITTY**: Kitty Graphics Protocol 特殊处理状态
     *
     * @see handleCsi CSI 序列处理
     * @see handleKittyGraphics Kitty Graphics 协议处理
     */
    fun parse(
        input: String,
        cursor: CursorState,
        buffer: TerminalBuffer,
        onText: (Char, CellStyle) -> Unit,
        onControlSequence: (ControlSequence) -> Unit,
        onResponse: (String) -> Unit = {},  // 新增：用于发送响应到SSH
    ) {
        for (char in input) {
            when (state) {
                ParseState.NORMAL -> {
                    when (char.code) {
                        0x1B -> { // ESC
                            state = ParseState.ESCAPE
                        }
                        0x07 -> { // BEL
                            onControlSequence(ControlSequence.Bell)
                        }
                        0x08 -> { // BS (Backspace)
                            // 标准退格行为：只向左移动光标，不删除字符
                            // 删除是通过 BS + 空格 + BS 序列完成的
                            if (cursor.x > 0) {
                                cursor.moveBy(-1, 0)
                            }
                        }
                        0x7F -> { // DEL (Delete)
                            // DEL 字符通常作为键盘输入发送到服务器，而不是作为输出序列
                            // 如果服务器发送了 DEL，可能是某些特殊情况，暂时忽略
                            // 不做任何处理
                        }
                        0x09 -> { // TAB
                            // 移动到下一个制表位（每8个字符）
                            val nextTab = ((cursor.x / 8) + 1) * 8
                            cursor.moveTo(nextTab, cursor.y)
                        }
                        0x0A -> { // LF (Line Feed)
                            // 检查是否在滚动区域内
                            val scrollBottom = buffer.getScrollRegionBottom()
                            
                            if (scrollBottom != null && cursor.y == scrollBottom) {
                                // 光标在滚动区域底部，需要滚动
                                buffer.newLine()
                                // 光标保持在滚动区域底部
                            } else {
                                // 正常向下移动一行
                                cursor.moveBy(0, 1)
                                if (cursor.y >= buffer.getHeight()) {
                                    // 超出缓冲区，需要滚动
                                    buffer.newLine()
                                    cursor.y = buffer.getHeight() - 1
                                }
                            }
                        }
                        0x0D -> { // CR (Carriage Return)
                            cursor.x = 0
                        }
                        0x0E -> { // SO - Shift Out (启用 G1 字符集，通常是 DEC 特殊图形)
                            // Shift Out: 切换到备用字符集 G1
                            // nano 使用这个来绘制线条字符
                            // 目前我们忽略这个序列，使用 Unicode 字符
                        }
                        0x0F -> { // SI - Shift In (恢复 G0 字符集，通常是 ASCII)
                            // Shift In: 切换回默认字符集 G0
                            // 目前我们忽略这个序列
                        }
                        in 0x20..0x7E -> { // ASCII 可打印字符
                            onText(char, cursor.currentStyle)
                        }
                        else -> {
                            // Unicode 字符（包括中文、日文、韩文等）
                            if (!char.isISOControl()) {
                                onText(char, cursor.currentStyle)
                            }
                        }
                    }
                }
                ParseState.ESCAPE -> {
                    when (char) {
                        '[' -> {
                            state = ParseState.CSI
                            parameters.clear()
                            currentParameter.clear()
                            isPrivateMode = false
                        }
                        ']' -> {
                            state = ParseState.OSC
                            oscData.clear()
                        }
                        '7' -> { // DECSC - Save Cursor
                            cursor.save()
                            state = ParseState.NORMAL
                        }
                        '8' -> { // DECRC - Restore Cursor
                            cursor.restore()
                            state = ParseState.NORMAL
                        }
                        'c' -> { // RIS - Reset to Initial State
                            onControlSequence(ControlSequence.Reset)
                            state = ParseState.NORMAL
                        }
                        'D' -> { // IND - Index (向下滚动一行)
                            // 向下滚动一行
                            onControlSequence(ControlSequence.ScrollDown(1))
                            state = ParseState.NORMAL
                        }
                        'M' -> { // RI - Reverse Index (反向索引)
                            // RI 的行为：
                            // 如果光标在滚动区域顶部，在当前行上方插入空行（向下推内容）
                            // 否则，光标向上移动一行
                            val scrollTop = buffer.getScrollRegionTop() ?: 0
                            
                            if (cursor.y == scrollTop) {
                                // 光标在滚动区域顶部，插入空行（反向滚动）
                                buffer.insertLineAt(cursor.y)
                            } else if (cursor.y > 0) {
                                // 光标不在顶部，向上移动
                                cursor.moveBy(0, -1)
                            }
                            state = ParseState.NORMAL
                        }
                        'E' -> { // NEL - Next Line (回车+换行)
                            cursor.x = 0
                            cursor.moveBy(0, 1)
                            if (cursor.y >= buffer.getHeight()) {
                                buffer.newLine()
                                cursor.y = buffer.getHeight() - 1
                            }
                            state = ParseState.NORMAL
                        }
                        '(', ')' -> { // SCS - Select Character Set (字符集选择)
                            // ESC ( B = 选择 G0 字符集为 ASCII
                            // ESC ) B = 选择 G1 字符集为 ASCII
                            // ESC ( 0 = 选择 G0 字符集为 DEC 特殊图形字符集
                            // 我们使用 UTF-8，所以简单地忽略这些序列
                            state = ParseState.CHARSET
                        }
                        'P' -> { // DCS - Device Control String (Sixel等图形协议)
                            // ESC P 开始，ESC \ 结束
                            // 用于Sixel图形协议
                            println("      [AnsiParser] DCS (Sixel) sequence started, ignoring graphics data")
                            state = ParseState.DCS
                        }
                        '^' -> { // PM - Privacy Message
                            // ESC ^ 开始，ESC \ 结束
                            state = ParseState.PM
                        }
                        '_' -> { // APC - Application Program Command (Kitty图形协议等)
                            // ESC _ 开始，ESC \ 结束
                            // 用于Kitty Graphics Protocol
                            state = ParseState.APC
                            apcData.clear()
                        }
                        '=', '>' -> { // Application/Normal Keypad Mode
                            // ESC = : 设置应用小键盘模式（DECKPAM）
                            // ESC > : 设置正常小键盘模式（DECKPNM）
                            // 我们已经在键盘处理中支持了小键盘，所以可以忽略这些模式切换
                            state = ParseState.NORMAL
                        }
                        '\\' -> { // ST - String Terminator (ESC \)
                            // 用于结束 DCS/PM/APC/OSC 等序列
                            // 检查之前的状态来决定如何处理
                            when (previousState) {
                                ParseState.APC_KITTY -> {
                                    // Kitty Graphics命令结束（简化日志）
                                    // println("      [AnsiParser] Kitty Graphics sequence ended (ESC \\), data length: ${apcData.length}")
                                    handleKittyGraphics(apcData.toString(), cursor, buffer, onResponse)
                                    apcData.clear()
                                }
                                ParseState.DCS, ParseState.PM, ParseState.APC -> {
                                    println("      [AnsiParser] Graphics sequence ended (ESC \\)")
                                }
                                else -> {
                                    println("      [AnsiParser] String Terminator (ESC \\) from state: $previousState")
                                }
                            }
                            state = ParseState.NORMAL
                            previousState = ParseState.NORMAL
                        }
                        else -> {
                            // 未知的转义序列，回到正常状态
                            println("      [AnsiParser] Unknown ESC sequence: ESC $char (0x${char.code.toString(16)})")
                            state = ParseState.NORMAL
                        }
                    }
                }
                ParseState.CSI -> {
                    when {
                        char in '0'..'9' -> {
                            currentParameter.append(char)
                        }
                        char == ';' -> {
                            parameters.add(currentParameter.toString().toIntOrNull() ?: 0)
                            currentParameter.clear()
                        }
                        char == '?' -> {
                            // DEC 私有模式标记
                            isPrivateMode = true
                        }
                        // Intermediate characters: 0x20-0x2F (space to /)
                        // 以及特殊的 > = < 等
                        char in ' '..'/' || char in '>'.. '>' || char == '=' || char == '<' -> {
                            // 存储 intermediate 字符（通常只有一个）
                            intermediateChar = char
                        }
                        else -> {
                            // 完成参数收集
                            if (currentParameter.isNotEmpty()) {
                                parameters.add(currentParameter.toString().toIntOrNull() ?: 0)
                                currentParameter.clear()
                            }
                            
                            // 处理 CSI 命令
                            handleCsiCommand(char, parameters, cursor, buffer, onControlSequence, intermediateChar, onResponse)
                            state = ParseState.NORMAL
                            parameters.clear()
                            isPrivateMode = false  // 重置私有模式标记
                            intermediateChar = null  // 重置 intermediate 字符
                        }
                    }
                }
                ParseState.OSC -> {
                    when (char.code) {
                        0x07, 0x1B -> { // BEL 或 ESC 结束 OSC
                            handleOscCommand(oscData.toString(), onControlSequence)
                            state = ParseState.NORMAL
                            oscData.clear()
                        }
                        else -> {
                            oscData.append(char)
                        }
                    }
                }
                ParseState.CHARSET -> {
                    // 字符集选择序列：ESC ( X 或 ESC ) X
                    // X 可以是：
                    //   B = ASCII
                    //   0 = DEC 特殊图形字符集
                    //   A = 英国字符集
                    //   等等...
                    // 我们使用 UTF-8，所以简单地忽略字符集标识符，直接返回正常状态
                    state = ParseState.NORMAL
                }
                ParseState.DCS, ParseState.PM -> {
                    // DCS/PM 模式：忽略所有内容直到遇到 ESC \ (String Terminator)
                    // 用于过滤Sixel等图形协议数据
                    when (char) {
                        '\u001b' -> {
                            // 遇到ESC，保存当前状态并切换到ESCAPE状态
                            previousState = state
                            state = ParseState.ESCAPE
                        }
                        '\u009c' -> {
                            // ST (String Terminator) 的单字节形式（C1控制码）
                            println("      [AnsiParser] Graphics sequence ended (ST)")
                            state = ParseState.NORMAL
                            previousState = ParseState.NORMAL
                        }
                        // 否则忽略所有字符（图形数据/二进制数据）
                    }
                }
                ParseState.APC -> {
                    // APC模式：检测是否是Kitty Graphics Protocol
                    when (char) {
                        'G' -> {
                            // Kitty Graphics Protocol开始（简化日志）
                            // println("      [AnsiParser] Kitty Graphics Protocol detected")
                            state = ParseState.APC_KITTY
                            apcData.clear()
                        }
                        '\u001b' -> {
                            // 可能是结束序列
                            previousState = state
                            state = ParseState.ESCAPE
                        }
                        '\u009c' -> {
                            // ST - 结束但没有数据
                            state = ParseState.NORMAL
                            previousState = ParseState.NORMAL
                            apcData.clear()
                        }
                        else -> {
                            // 其他APC命令，忽略
                            apcData.append(char)
                        }
                    }
                }
                ParseState.APC_KITTY -> {
                    // Kitty Graphics Protocol：收集数据直到ESC \
                    when (char) {
                        '\u001b' -> {
                            // 可能是结束序列 ESC \，保存当前状态
                            previousState = state
                            state = ParseState.ESCAPE
                        }
                        '\u009c' -> {
                            // ST - 结束Kitty Graphics命令
                            println("      [AnsiParser] Kitty Graphics ended (ST), data length: ${apcData.length}")
                            handleKittyGraphics(apcData.toString(), cursor, buffer, onResponse)
                            state = ParseState.NORMAL
                            previousState = ParseState.NORMAL
                            apcData.clear()
                        }
                        else -> {
                            // 收集数据
                            apcData.append(char)
                        }
                    }
                }
                else -> {
                    state = ParseState.NORMAL
                }
            }
        }
    }

    private fun handleCsiCommand(
        command: Char,
        params: List<Int>,
        cursor: CursorState,
        buffer: TerminalBuffer,
        onControlSequence: (ControlSequence) -> Unit,
        intermediate: Char? = null,  // CSI 中间字符
        onResponse: (String) -> Unit = {},  // 响应回调
    ) {
        // 注意：不同命令的默认值不同！
        // 对于移动命令（A/B/C/D），默认是 1
        // 对于清除命令（J/K），默认是 0
        // 对于光标位置（H/f），默认是 1
        
        when (command) {
            'A' -> { // CUU - Cursor Up
                val count = params.getOrElse(0) { 1 }
                cursor.moveBy(0, -count)
            }
            'B' -> { // CUD - Cursor Down
                val count = params.getOrElse(0) { 1 }
                cursor.moveBy(0, count)
            }
            'C' -> { // CUF - Cursor Forward
                val count = params.getOrElse(0) { 1 }
                cursor.moveBy(count, 0)
            }
            'D' -> { // CUB - Cursor Backward
                val count = params.getOrElse(0) { 1 }
                cursor.moveBy(-count, 0)
            }
            'G' -> { // CHA - Cursor Horizontal Absolute
                val col = params.getOrElse(0) { 1 }
                val newX = (col - 1).coerceAtLeast(0)
                cursor.moveTo(newX, cursor.y)
            }
            'd' -> { // VPA - Vertical Position Absolute
                val row = params.getOrElse(0) { 1 }
                val newY = (row - 1).coerceAtLeast(0)
                cursor.moveTo(cursor.x, newY)
            }
            'H', 'f' -> { // CUP - Cursor Position
                val row = params.getOrElse(0) { 1 }
                val col = params.getOrElse(1) { 1 }
                val newX = (col - 1).coerceAtLeast(0)
                val newY = (row - 1).coerceAtLeast(0)
                cursor.moveTo(newX, newY)
            }
            'J' -> { // Erase in Display
                val mode = params.getOrElse(0) { 0 }  // 默认值是 0
                when (mode) {
                    0 -> { // 从光标到屏幕末尾
                        onControlSequence(ControlSequence.EraseDisplay(0))
                    }
                    1 -> { // 从光标到屏幕开头
                        onControlSequence(ControlSequence.EraseDisplay(1))
                    }
                    2, 3 -> { // 清除整个屏幕
                        onControlSequence(ControlSequence.EraseDisplay(2))
                        buffer.clear()
                        cursor.moveTo(0, 0)
                    }
                }
            }
            'K' -> { // Erase in Line
                val mode = params.getOrElse(0) { 0 }  // 默认值是 0（从光标到行尾）
                handleEraseLine(mode, cursor, buffer)
            }
            'L' -> { // IL - Insert Line
                val count = params.getOrElse(0) { 1 }
                handleInsertLine(count, buffer, cursor)
            }
            'M' -> { // DL - Delete Line
                val count = params.getOrElse(0) { 1 }
                handleDeleteLine(count, buffer, cursor)
            }
            'P' -> { // DCH - Delete Character
                val count = params.getOrElse(0) { 1 }
                handleDeleteCharacter(count, buffer, cursor)
            }
            '@' -> { // ICH - Insert Character
                val count = params.getOrElse(0) { 1 }
                handleInsertCharacter(count, buffer, cursor)
            }
            'X' -> { // ECH - Erase Character
                // ESC[nX - 从光标位置开始擦除 n 个字符（用空格替换）
                val count = params.getOrElse(0) { 1 }
                val line = buffer.getVisibleLine(cursor.y) ?: buffer.getCurrentLine()
                val emptyCell = TerminalCell(
                    char = ' ',
                    foregroundColor = cursor.currentStyle.foregroundColor,
                    backgroundColor = cursor.currentStyle.backgroundColor,
                    reverse = cursor.currentStyle.reverse,
                    bold = cursor.currentStyle.bold,
                    faint = cursor.currentStyle.faint,
                    underline = cursor.currentStyle.underline,
                )
                for (i in 0 until count) {
                    val x = cursor.x + i
                    if (x < buffer.getWidth()) {
                        line.setCell(x, emptyCell)
                    }
                }
            }
            'S' -> { // SU - Scroll Up
                // ESC[nS - 向上滚动 n 行
                val count = params.getOrElse(0) { 1 }
                repeat(count) {
                    buffer.newLine()
                }
            }
            'T' -> { // SD - Scroll Down
                // ESC[nT - 向下滚动 n 行（很少使用）
                val count = params.getOrElse(0) { 1 }
                // 暂时不实现，因为向下滚动在终端中很少使用
            }
            's' -> { // SCP - Save Cursor Position (非 DEC 私有模式)
                cursor.save()
            }
            'u' -> { // RCP - Restore Cursor Position (非 DEC 私有模式)
                cursor.restore()
            }
            'I' -> { // CHT - Cursor Horizontal Forward Tabulation
                // ESC[nI - 向前移动 n 个制表位
                val count = params.getOrElse(0) { 1 }
                val nextTab = ((cursor.x / 8) + count) * 8
                cursor.moveTo(nextTab, cursor.y)
            }
            'Z' -> { // CBT - Cursor Backward Tabulation
                // ESC[nZ - 向后移动 n 个制表位
                val count = params.getOrElse(0) { 1 }
                val prevTab = ((cursor.x / 8) - count) * 8
                cursor.moveTo(prevTab.coerceAtLeast(0), cursor.y)
            }
            'm' -> { // SGR - Select Graphic Rendition
                handleSgr(params, cursor)
            }
            'r' -> { // DECSTBM - Set Top and Bottom Margins (滚动区域)
                val top = if (params.size > 0) params[0] else 1
                val bottom = if (params.size > 1) params[1] else buffer.getHeight()
                buffer.setScrollRegion(top - 1, bottom - 1)
            }
            'h' -> { // SM - Set Mode
                if (isPrivateMode) {
                    handleDecPrivateModeSet(params, buffer, cursor, onControlSequence)
                }
                // 普通模式设置，暂时忽略
            }
            'l' -> { // RM - Reset Mode
                if (isPrivateMode) {
                    handleDecPrivateModeReset(params, buffer, cursor, onControlSequence)
                }
                // 普通模式重置，暂时忽略
            }
            'n' -> { // DSR - Device Status Report / CPR - Cursor Position Report
                handleStatusReport(params, isPrivateMode, cursor, onResponse)
            }
            'c' -> { // DA - Device Attributes
                handleDeviceAttributes(params, isPrivateMode, intermediate, onResponse)
            }
            'x' -> { // DECREQTPARM - Request Terminal Parameters
                handleTerminalParameters(params, isPrivateMode, onResponse)
            }
            'p' -> { // DECRQM - DEC Request Mode
                if (isPrivateMode && intermediate == '$') {
                    handleDecRequestMode(params, onResponse)
                }
                // else: 其他 'p' 命令，暂时忽略
            }
            'q' -> { // XTVERSION / DECSCUSR
                if (intermediate == '>') {
                    // XTVERSION - Terminal name and version
                    val response = "\u001bP>|xterm-256color\u001b\\"
                    onResponse(response)
                    println("      [AnsiParser] Responded to XTVERSION: xterm-256color")
                }
                // else: DECSCUSR - Set Cursor Style, ignore for now
            }
            't' -> { // Window manipulation
                // 窗口操作，暂时忽略
            }
            else -> {
                // 未知命令 - 只记录非常见的命令
                if (command !in listOf('>', '$', ' ')) {
                    println("      [AnsiParser] Unknown CSI command '$command' (0x${command.code.toString(16)}), params=$params, isPrivate=$isPrivateMode, intermediate=$intermediate")
                }
            }
        }
    }

    private fun handleSgr(params: List<Int>, cursor: CursorState) {
        if (params.isEmpty()) {
            // 重置所有样式
            cursor.currentStyle = CellStyle.default()
            return
        }
        
        // 调试：只打印非常见的 SGR 参数
        // if (params.any { it > 49 }) {
        //     println("      [AnsiParser] SGR params: $params")
        // }

        var i = 0
        while (i < params.size) {
            val code = params[i]
            when {
                code == 0 -> {
                    // 重置所有样式
                    cursor.currentStyle = CellStyle.default()
                }
                code == 1 -> cursor.currentStyle = cursor.currentStyle.copy(bold = true)
                code == 2 -> cursor.currentStyle = cursor.currentStyle.copy(faint = true)
                code == 3 -> cursor.currentStyle = cursor.currentStyle.copy(italic = true)
                code == 4 -> cursor.currentStyle = cursor.currentStyle.copy(underline = true)
                code == 5 -> cursor.currentStyle = cursor.currentStyle.copy(blink = true)
                code == 7 -> cursor.currentStyle = cursor.currentStyle.copy(reverse = true)
                code == 8 -> cursor.currentStyle = cursor.currentStyle.copy(invisible = true)
                code == 9 -> cursor.currentStyle = cursor.currentStyle.copy(strikethrough = true)
                code in 21..29 -> {
                    // 关闭样式
                    when (code) {
                        21 -> cursor.currentStyle = cursor.currentStyle.copy(bold = false)
                        22 -> cursor.currentStyle = cursor.currentStyle.copy(bold = false, faint = false)
                        23 -> cursor.currentStyle = cursor.currentStyle.copy(italic = false)
                        24 -> cursor.currentStyle = cursor.currentStyle.copy(underline = false)
                        25 -> cursor.currentStyle = cursor.currentStyle.copy(blink = false)
                        27 -> cursor.currentStyle = cursor.currentStyle.copy(reverse = false)
                        28 -> cursor.currentStyle = cursor.currentStyle.copy(invisible = false)
                        29 -> cursor.currentStyle = cursor.currentStyle.copy(strikethrough = false)
                    }
                }
                code in 30..37 -> {
                    // 标准前景色
                    val colorIndex = code - 30
                    cursor.currentStyle = cursor.currentStyle.copy(
                        foregroundColor = TerminalColor.Standard(
                            TerminalColor.StandardColor.values()[colorIndex]
                        )
                    )
                }
                code == 38 -> {
                    // 设置前景色（256色或TrueColor）
                    i++
                    if (i < params.size) {
                        when (params[i]) {
                            5 -> { // 256色
                                i++
                                if (i < params.size) {
                                    cursor.currentStyle = cursor.currentStyle.copy(
                                        foregroundColor = TerminalColor.Indexed256(params[i])
                                    )
                                }
                            }
                            2 -> { // TrueColor
                                i++
                                val r = if (i < params.size) params[i] else 0
                                i++
                                val g = if (i < params.size) params[i] else 0
                                i++
                                val b = if (i < params.size) params[i] else 0
                                cursor.currentStyle = cursor.currentStyle.copy(
                                    foregroundColor = TerminalColor.TrueColor(r, g, b)
                                )
                            }
                        }
                    }
                }
                code in 40..47 -> {
                    // 标准背景色
                    val colorIndex = code - 40
                    cursor.currentStyle = cursor.currentStyle.copy(
                        backgroundColor = TerminalColor.Standard(
                            TerminalColor.StandardColor.values()[colorIndex]
                        )
                    )
                }
                code == 48 -> {
                    // 设置背景色（256色或TrueColor）
                    i++
                    if (i < params.size) {
                        when (params[i]) {
                            5 -> { // 256色
                                i++
                                if (i < params.size) {
                                    cursor.currentStyle = cursor.currentStyle.copy(
                                        backgroundColor = TerminalColor.Indexed256(params[i])
                                    )
                                }
                            }
                            2 -> { // TrueColor
                                i++
                                val r = if (i < params.size) params[i] else 0
                                i++
                                val g = if (i < params.size) params[i] else 0
                                i++
                                val b = if (i < params.size) params[i] else 0
                                cursor.currentStyle = cursor.currentStyle.copy(
                                    backgroundColor = TerminalColor.TrueColor(r, g, b)
                                )
                            }
                        }
                    }
                }
                code == 39 -> {
                    // 重置前景色
                    cursor.currentStyle = cursor.currentStyle.copy(
                        foregroundColor = TerminalColor.Default
                    )
                }
                code == 49 -> {
                    // 重置背景色
                    cursor.currentStyle = cursor.currentStyle.copy(
                        backgroundColor = TerminalColor.Default
                    )
                }
                code in 90..97 -> {
                    // 亮前景色（Bright foreground colors）
                    val colorIndex = code - 90 + 8  // 映射到 BrightBlack(8) - BrightWhite(15)
                    cursor.currentStyle = cursor.currentStyle.copy(
                        foregroundColor = TerminalColor.Standard(
                            TerminalColor.StandardColor.values()[colorIndex]
                        )
                    )
                }
                code in 100..107 -> {
                    // 亮背景色（Bright background colors）
                    val colorIndex = code - 100 + 8  // 映射到 BrightBlack(8) - BrightWhite(15)
                    cursor.currentStyle = cursor.currentStyle.copy(
                        backgroundColor = TerminalColor.Standard(
                            TerminalColor.StandardColor.values()[colorIndex]
                        )
                    )
                }
            }
            i++
        }
    }

    private fun handleOscCommand(data: String, onControlSequence: (ControlSequence) -> Unit) {
        // 处理 OSC 命令（如设置窗口标题）
        when {
            data.startsWith("0;") || data.startsWith("2;") -> {
                val title = data.substring(2)
                onControlSequence(ControlSequence.SetTitle(title))
            }
            data.startsWith("1337;") -> {
                // iTerm2 图片协议：ESC ] 1337 ; File = ... BEL
                // 目前暂不支持实际图片显示，仅记录
                println("      [AnsiParser] iTerm2 image protocol detected, not yet implemented")
            }
        }
    }
    
    /**
     * 处理Kitty Graphics Protocol
     * 格式: ESC _ G key=value,key=value;base64data ESC \
     */
    private fun handleKittyGraphics(
        data: String,
        cursor: CursorState,
        buffer: TerminalBuffer,
        onResponse: (String) -> Unit
    ) {
        try {
            // 简化日志：只在关键时刻打印
            // println("      [KittyGraphics] Parsing command, data length: ${data.length}")
            
            val command = KittyProtocolParser.parse(data)
            if (command == null) {
                println("      [KittyGraphics] Failed to parse command")
                return
            }
            
            when (command) {
                is KittyGraphicsCommand.Transmit -> handleKittyTransmit(command, cursor, buffer, onResponse)
                is KittyGraphicsCommand.Display -> handleKittyDisplay(command, cursor, buffer)
                is KittyGraphicsCommand.Delete -> handleKittyDelete(command, buffer)
                is KittyGraphicsCommand.Query -> handleKittyQuery(command, onResponse)
            }
        } catch (e: Exception) {
            println("      [KittyGraphics] Error handling command: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun handleKittyTransmit(
        command: KittyGraphicsCommand.Transmit,
        cursor: CursorState,
        buffer: TerminalBuffer,
        onResponse: (String) -> Unit
    ) {
        val actualImageId = resolveActualImageId(command)
        
        if (command.more > 0 && command.unicodePlaceholder) {
            println("      [KittyGraphics] ⏰ FIRST CHUNK: id=$actualImageId, U=1, cursor=(${cursor.x},${cursor.y}), size=${command.width}x${command.height}, more=${command.more}")
        }
        
        buffer.addImage(
            imageId = actualImageId,
            imageNumber = command.imageNumber,
            format = command.format,
            width = command.width,
            height = command.height,
            data = command.data,
            isMore = command.more > 0,
            unicodePlaceholder = command.unicodePlaceholder,
            cursorX = cursor.x,
            cursorY = cursor.y,
            columns = command.columns,
            rows = command.rows
        )
        
        if (command.more == 0) {
            finalizeKittyTransmission(command, actualImageId, cursor, buffer, onResponse)
        }
    }

    private fun handleKittyDisplay(
        command: KittyGraphicsCommand.Display,
        cursor: CursorState,
        buffer: TerminalBuffer
    ) {
        println("      [KittyGraphics] Display command: imageId=${command.imageId}")
        buffer.placeImage(
            imageId = command.imageId,
            placementId = command.placementId.takeIf { it > 0 } ?: generatePlacementId(),
            x = cursor.x,
            y = cursor.y,
            columns = command.columns,
            rows = command.rows,
            zIndex = command.zIndex
        )
    }

    private fun handleKittyDelete(
        command: KittyGraphicsCommand.Delete,
        buffer: TerminalBuffer
    ) {
        println("      [KittyGraphics] Delete command: mode=${command.deleteMode}, imageId=${command.imageId}")
        buffer.deleteImages(
            deleteMode = command.deleteMode,
            imageId = command.imageId,
            placementId = command.placementId
        )
    }

    private fun handleKittyQuery(
        command: KittyGraphicsCommand.Query,
        onResponse: (String) -> Unit
    ) {
        println("      [KittyGraphics] Query command: action=${command.action}, imageId=${command.imageId}")
        val response = "\u001b_Ga=q,s=1,i=${command.imageId};\u001b\\"
        onResponse(response)
    }

    private fun resolveActualImageId(command: KittyGraphicsCommand.Transmit): Int {
        return when {
            command.imageId > 0 -> {
                currentTransmittingImageId = command.imageId
                command.imageId
            }
            command.imageNumber > 0 -> {
                val resolvedId = imageNumberToIdMap.getOrPut(command.imageNumber) { generateImageId() }
                currentTransmittingImageId = resolvedId
                resolvedId
            }
            currentTransmittingImageId != null -> currentTransmittingImageId!!
            else -> {
                val newId = generateImageId()
                currentTransmittingImageId = newId
                newId
            }
        }
    }

    private fun finalizeKittyTransmission(
        command: KittyGraphicsCommand.Transmit,
        actualImageId: Int,
        cursor: CursorState,
        buffer: TerminalBuffer,
        onResponse: (String) -> Unit
    ) {
        if (command.imageNumber > 0) {
            imageNumberToIdMap.remove(command.imageNumber)
        }
        currentTransmittingImageId = null
        
        if (command.action == 't' || command.action == 'T') {
            buffer.placeImage(
                imageId = actualImageId,
                placementId = command.placementId.takeIf { it > 0 } ?: generatePlacementId(),
                x = cursor.x,
                y = cursor.y,
                columns = command.columns,
                rows = command.rows,
                zIndex = command.zIndex
            )
        }
        
        if (command.quiet != 1) {
            val response = "\u001b_Gi=${actualImageId};OK\u001b\\"
            onResponse(response)
            println("      [KittyGraphics] ✅ Sent OK response for image id=$actualImageId (U=${command.unicodePlaceholder})")
        }
    }
    
    private var nextImageId = 1
    private var nextPlacementId = 1
    
    // 用于追踪imageNumber到actualImageId的映射（多块传输）
    private val imageNumberToIdMap = mutableMapOf<Int, Int>()
    
    // 用于追踪当前正在传输的图片ID（无论是显式还是隐式）
    private var currentTransmittingImageId: Int? = null
    
    private fun generateImageId(): Int = nextImageId++
    private fun generatePlacementId(): Int = nextPlacementId++
    
    /**
     * 处理清行命令 (CSI K)
     */
    private fun handleEraseLine(mode: Int, cursor: CursorState, buffer: TerminalBuffer) {
        val line = buffer.getVisibleLine(cursor.y) ?: buffer.getCurrentLine()
        val width = buffer.getWidth()
        
        // 日志已禁用（性能优化）
        
        when (mode) {
            0 -> {
                // 从光标到行末尾
                // println("      [AnsiParser] Erasing from cursor to end of line (${cursor.x} to ${width-1}), current style: reverse=${cursor.currentStyle.reverse}")
                for (x in cursor.x until width) {
                    val emptyCell = TerminalCell(
                        char = ' ',
                        foregroundColor = cursor.currentStyle.foregroundColor,
                        backgroundColor = cursor.currentStyle.backgroundColor,
                        reverse = cursor.currentStyle.reverse,
                        bold = cursor.currentStyle.bold,
                        faint = cursor.currentStyle.faint,
                        underline = cursor.currentStyle.underline,
                    )
                    line.setCell(x, emptyCell)
                }
            }
            1 -> {
                // 从行开头到光标（包括光标位置）
                // println("      [AnsiParser] Erasing from start of line to cursor (0 to ${cursor.x})")
                for (x in 0..cursor.x) {
                    val emptyCell = TerminalCell(
                        char = ' ',
                        foregroundColor = cursor.currentStyle.foregroundColor,
                        backgroundColor = cursor.currentStyle.backgroundColor,
                        reverse = cursor.currentStyle.reverse,
                        bold = cursor.currentStyle.bold,
                        faint = cursor.currentStyle.faint,
                        underline = cursor.currentStyle.underline,
                    )
                    line.setCell(x, emptyCell)
                }
            }
            2 -> {
                // 清除整行
                // println("      [AnsiParser] Erasing entire line (0 to ${width-1})")
                for (x in 0 until width) {
                    val emptyCell = TerminalCell(
                        char = ' ',
                        foregroundColor = cursor.currentStyle.foregroundColor,
                        backgroundColor = cursor.currentStyle.backgroundColor,
                        reverse = cursor.currentStyle.reverse,
                        bold = cursor.currentStyle.bold,
                        faint = cursor.currentStyle.faint,
                        underline = cursor.currentStyle.underline,
                    )
                    line.setCell(x, emptyCell)
                }
            }
        }
    }
    
    /**
     * 处理插入行命令 (CSI L)
     */
    private fun handleInsertLine(count: Int, buffer: TerminalBuffer, cursor: CursorState) {
        val height = buffer.getHeight()
        val width = buffer.getWidth()
        val linesToInsert = count.coerceAtLeast(1)
        
        // 在光标位置插入空行
        for (i in 0 until linesToInsert) {
            if (cursor.y < height) {
                // 将光标行及其下方的行向下移动
                // 移除最底行到 scrollback
                buffer.newLine()
                // 在光标位置插入空行
                val newLine = TerminalLine(width)
                // TODO: 需要在 TerminalBuffer 中实现插入行功能
            }
        }
    }
    
    /**
     * 处理删除行命令 (CSI M)
     */
    private fun handleDeleteLine(count: Int, buffer: TerminalBuffer, cursor: CursorState) {
        val linesToDelete = count.coerceAtLeast(1)
        
        // 删除光标所在行及其下方的行
        // 在底部插入空行
        for (i in 0 until linesToDelete) {
            // TODO: 需要在 TerminalBuffer 中实现删除行功能
        }
    }
    
    /**
     * 处理删除字符命令 (CSI P)
     */
    private fun handleDeleteCharacter(count: Int, buffer: TerminalBuffer, cursor: CursorState) {
        val line = buffer.getVisibleLine(cursor.y) ?: buffer.getCurrentLine()
        val width = buffer.getWidth()
        val charsToDelete = count.coerceAtLeast(1)
        
        // 删除光标位置的字符，后续字符左移
        for (i in 0 until charsToDelete) {
            if (cursor.x < width) {
                line.deleteAt(cursor.x)
            }
        }
    }
    
    /**
     * 处理插入字符命令 (CSI @)
     */
    private fun handleInsertCharacter(count: Int, buffer: TerminalBuffer, cursor: CursorState) {
        val line = buffer.getVisibleLine(cursor.y) ?: buffer.getCurrentLine()
        val width = buffer.getWidth()
        val charsToInsert = count.coerceAtLeast(1)
        
        // 在光标位置插入空字符，后续字符右移
        for (i in 0 until charsToInsert) {
            if (cursor.x < width) {
                val emptyCell = TerminalCell(
                    char = ' ',
                    foregroundColor = cursor.currentStyle.foregroundColor,
                    backgroundColor = cursor.currentStyle.backgroundColor,
                )
                line.insertAt(cursor.x, emptyCell)
            }
        }
    }
    
    /**
     * 处理 DEC 私有模式设置 (CSI ? ... h)
     */
    private fun handleDecPrivateModeSet(
        params: List<Int>,
        buffer: TerminalBuffer,
        cursor: CursorState,
        onControlSequence: (ControlSequence) -> Unit
    ) {
        for (param in params) {
            when (param) {
                1 -> {
                    // DECCKM - Cursor Keys Mode (应用键模式)
                    // 方向键发送不同的序列
                }
                25 -> {
                    // DECTCEM - Text Cursor Enable Mode (显示光标)
                    cursor.visible = true
                }
                47 -> {
                    // DECSTBM - Switch to Alternate Screen Buffer
                    buffer.switchToAlternateScreen()
                    // 切换到 alternate screen 后，重置光标到 (0, 0)
                    cursor.moveTo(0, 0)
                }
                1049 -> {
                    // 1049 - Enable alternate screen buffer and save cursor
                    cursor.saveForAltScreen()  // 使用独立的保存位置
                    buffer.switchToAlternateScreen()
                    // 切换到 alternate screen 后，重置光标到 (0, 0)
                    cursor.moveTo(0, 0)
                }
                else -> {
                    // 其他私有模式，暂时忽略
                }
            }
        }
    }
    
    /**
     * 处理 DEC 私有模式重置 (CSI ? ... l)
     */
    private fun handleDecPrivateModeReset(
        params: List<Int>,
        buffer: TerminalBuffer,
        cursor: CursorState,
        onControlSequence: (ControlSequence) -> Unit
    ) {
        for (param in params) {
            when (param) {
                1 -> {
                    // DECCKM - Cursor Keys Mode (正常键模式)
                }
                25 -> {
                    // DECTCEM - Text Cursor Enable Mode (隐藏光标)
                    cursor.visible = false
                }
                47 -> {
                    // DECSTBM - Switch back to Normal Screen Buffer
                    buffer.switchToMainScreen()
                }
                1049 -> {
                    // 1049 - Restore normal screen buffer and restore cursor
                    buffer.switchToMainScreen()
                    cursor.restoreFromAltScreen()  // 使用独立的恢复位置
                }
                else -> {
                    // 其他私有模式，暂时忽略
                }
            }
        }
    }

    /**
     * 处理状态报告请求 (DSR/CPR)
     */
    private fun handleStatusReport(
        params: List<Int>,
        isPrivateMode: Boolean,
        cursor: CursorState,
        onResponse: (String) -> Unit
    ) {
        val requestType = params.getOrElse(0) { 0 }
        
        when {
            // DSR - Device Status Report: CSI 5 n
            requestType == 5 && !isPrivateMode -> {
                val response = "\u001b[0n"  // Terminal OK
                onResponse(response)
                println("      [AnsiParser] Responded to DSR: Terminal OK")
            }
            // CPR - Cursor Position Report: CSI 6 n
            requestType == 6 && !isPrivateMode -> {
                val response = "\u001b[${cursor.y + 1};${cursor.x + 1}R"
                onResponse(response)
                println("      [AnsiParser] Responded to CPR: row=${cursor.y + 1}, col=${cursor.x + 1}")
            }
            // DECXCPR - Extended CPR: CSI ? 6 n
            requestType == 6 && isPrivateMode -> {
                val response = "\u001b[?${cursor.y + 1};${cursor.x + 1};1R"
                onResponse(response)
                println("      [AnsiParser] Responded to DECXCPR: row=${cursor.y + 1}, col=${cursor.x + 1}")
            }
        }
    }

    /**
     * 处理设备属性查询 (DA)
     */
    private fun handleDeviceAttributes(
        params: List<Int>,
        isPrivateMode: Boolean,
        intermediate: Char?,
        onResponse: (String) -> Unit
    ) {
        when {
            // Primary DA: CSI c or CSI 0 c
            !isPrivateMode && (params.isEmpty() || params[0] == 0) && intermediate == null -> {
                // 响应：VT220 with extensions
                // 62=VT220, 1=132col, 2=printer, 6=selective erase, 9=natl charset, 15=tech charset, 22=color
                val response = "\u001b[?62;1;2;6;9;15;22c"
                onResponse(response)
                println("      [AnsiParser] Responded to DA1 (Primary Device Attributes)")
            }
            
            // Secondary DA: CSI > c or CSI > 0 c
            !isPrivateMode && (params.isEmpty() || params[0] == 0) && intermediate == '>' -> {
                // 响应：VT220, firmware version 10.0
                val response = "\u001b[>1;10;0c"
                onResponse(response)
                println("      [AnsiParser] Responded to DA2 (Secondary Device Attributes)")
            }
            
            // Tertiary DA: CSI = c
            !isPrivateMode && intermediate == '=' -> {
                // 响应：Unit ID (all zeros)
                val response = "\u001bP!|00000000\u001b\\"
                onResponse(response)
                println("      [AnsiParser] Responded to DA3 (Tertiary Device Attributes)")
            }
        }
    }

    /**
     * 处理终端参数请求 (DECREQTPARM)
     */
    private fun handleTerminalParameters(
        params: List<Int>,
        isPrivateMode: Boolean,
        onResponse: (String) -> Unit
    ) {
        if (!isPrivateMode && params.isNotEmpty()) {
            val sol = params[0]
            // 响应格式: CSI <sol> ; <par> ; <nbits> ; <xspeed> ; <rspeed> ; <clkmul> ; <flags> x
            // par=1 (no parity), nbits=1 (8 bits), speeds=120 (9600 baud), clkmul=1, flags=0
            val response = "\u001b[$sol;1;1;120;120;1;0x"
            onResponse(response)
            println("      [AnsiParser] Responded to DECREQTPARM (Terminal Parameters)")
        }
    }

    /**
     * 处理 DEC 模式查询请求 (DECRQM)
     * 格式: CSI ? <mode> $ p
     * 响应: CSI ? <mode> ; <value> $ y
     * value: 0=不识别, 1=设置, 2=未设置, 3=永久设置, 4=永久未设置
     */
    private fun handleDecRequestMode(
        params: List<Int>,
        onResponse: (String) -> Unit
    ) {
        if (params.isEmpty()) return
        
        val mode = params[0]
        // 根据不同的模式返回不同的状态
        // 大多数模式我们返回 0 (不识别) 或 2 (未设置)
        val value = when (mode) {
            // 常见的 DEC 私有模式
            1 -> 2      // DECCKM - Cursor Keys Mode (未设置，使用普通模式)
            3 -> 2      // DECCOLM - 80/132 Column Mode (未设置，使用80列)
            6 -> 2      // DECOM - Origin Mode (未设置，使用绝对定位)
            7 -> 2      // DECAWM - Auto Wrap Mode (未设置，自动换行关闭)
            12 -> 2     // Start Blinking Cursor (未设置)
            25 -> 1     // DECTCEM - Text Cursor Enable Mode (设置，光标可见)
            1000 -> 2   // Send Mouse X & Y on button press (未设置)
            1001 -> 2   // Use Hilite Mouse Tracking (未设置)
            1002 -> 2   // Use Cell Motion Mouse Tracking (未设置)
            1003 -> 2   // Use All Motion Mouse Tracking (未设置)
            1004 -> 2   // Send FocusIn/FocusOut events (未设置)
            1005 -> 2   // Enable UTF-8 Mouse Mode (未设置)
            1006 -> 2   // Enable SGR Mouse Mode (未设置)
            1007 -> 2   // Enable Alternate Scroll Mode (未设置)
            1047 -> 2   // Use Alternate Screen Buffer (取决于当前状态)
            1048 -> 2   // Save cursor (未设置)
            1049 -> 2   // Save cursor and use Alternate Screen Buffer (未设置)
            2004 -> 2   // Bracketed Paste Mode (未设置)
            else -> 0   // 不识别的模式
        }
        
        val response = "\u001b[?$mode;${value}\$y"
        onResponse(response)
        println("      [AnsiParser] Responded to DECRQM mode=$mode, value=$value")
    }
}

/**
 * 控制序列类型
 */
sealed class ControlSequence {
    object Bell : ControlSequence()
    object Reset : ControlSequence()
    data class EraseDisplay(val mode: Int) : ControlSequence()
    data class EraseLine(val mode: Int) : ControlSequence()
    data class SetTitle(val title: String) : ControlSequence()
    data class ScrollUp(val lines: Int) : ControlSequence()
    data class ScrollDown(val lines: Int) : ControlSequence()
}

