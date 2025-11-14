package cn.hjhw.ssh.terminal

/**
 * 键盘事件
 */
data class KeyEvent(
    val key: Key,
    val modifiers: Set<KeyModifier> = emptySet(),
    val character: Char? = null,
)

/**
 * 按键类型
 */
enum class Key {
    // 字母和数字
    A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z,
    Digit0, Digit1, Digit2, Digit3, Digit4, Digit5, Digit6, Digit7, Digit8, Digit9,

    // 功能键
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,

    // 方向键
    Up, Down, Left, Right,

    // 控制键
    Enter, Tab, Backspace, Delete, Insert, Home, End, PageUp, PageDown,
    Escape, Space,

    // 其他
    Unknown,
}

/**
 * 按键修饰符
 */
enum class KeyModifier {
    Ctrl,
    Alt,
    Shift,
}

/**
 * 终端模式
 */
enum class TerminalMode {
    NORMAL,      // 正常模式
    APP_KEY,     // 应用键模式（用于功能键）
}

/**
 * 键盘到控制序列的映射器
 */
class KeyMapper(
    private var mode: TerminalMode = TerminalMode.NORMAL,
) {
    /**
     * 将键盘事件转换为字节序列
     */
    fun mapKeyEvent(event: KeyEvent): ByteArray {
        // 处理 Ctrl+字符组合
        if (KeyModifier.Ctrl in event.modifiers && event.character != null) {
            return mapCtrlChar(event.character)
        }

        // 处理特殊键
        return when (event.key) {
            Key.Enter -> "\r".toByteArray()
            Key.Tab -> "\t".toByteArray()
            Key.Backspace -> "\u007F".toByteArray() // DEL
            Key.Escape -> "\u001B".toByteArray()
            Key.Up -> mapArrowKey("A")
            Key.Down -> mapArrowKey("B")
            Key.Right -> mapArrowKey("C")
            Key.Left -> mapArrowKey("D")
            Key.Home -> "\u001B[H".toByteArray()
            Key.End -> "\u001B[F".toByteArray()
            Key.Delete -> "\u001B[3~".toByteArray()
            Key.Insert -> "\u001B[2~".toByteArray()
            Key.PageUp -> "\u001B[5~".toByteArray()
            Key.PageDown -> "\u001B[6~".toByteArray()
            Key.F1 -> mapFunctionKey(1)
            Key.F2 -> mapFunctionKey(2)
            Key.F3 -> mapFunctionKey(3)
            Key.F4 -> mapFunctionKey(4)
            Key.F5 -> mapFunctionKey(5)
            Key.F6 -> mapFunctionKey(6)
            Key.F7 -> mapFunctionKey(7)
            Key.F8 -> mapFunctionKey(8)
            Key.F9 -> mapFunctionKey(9)
            Key.F10 -> mapFunctionKey(10)
            Key.F11 -> mapFunctionKey(11)
            Key.F12 -> mapFunctionKey(12)
            else -> {
                // 普通字符
                if (event.character != null) {
                    event.character.toString().toByteArray()
                } else {
                    byteArrayOf()
                }
            }
        }
    }

    /**
     * 映射 Ctrl+字符组合
     */
    private fun mapCtrlChar(char: Char): ByteArray {
        val code = when (char.uppercaseChar()) {
            '@' -> 0x00
            'A' -> 0x01
            'B' -> 0x02
            'C' -> 0x03 // Ctrl+C (中断)
            'D' -> 0x04 // Ctrl+D (EOF)
            'E' -> 0x05
            'F' -> 0x06
            'G' -> 0x07
            'H' -> 0x08 // Ctrl+H (退格)
            'I' -> 0x09 // Ctrl+I (Tab)
            'J' -> 0x0A // Ctrl+J (换行)
            'K' -> 0x0B
            'L' -> 0x0C // Ctrl+L (清屏)
            'M' -> 0x0D // Ctrl+M (回车)
            'N' -> 0x0E
            'O' -> 0x0F
            'P' -> 0x10
            'Q' -> 0x11 // Ctrl+Q (恢复)
            'R' -> 0x12
            'S' -> 0x13 // Ctrl+S (暂停)
            'T' -> 0x14
            'U' -> 0x15
            'V' -> 0x16
            'W' -> 0x17
            'X' -> 0x18
            'Y' -> 0x19
            'Z' -> 0x1A // Ctrl+Z (挂起)
            '[' -> 0x1B // Ctrl+[ (ESC)
            '\\' -> 0x1C
            ']' -> 0x1D
            '^' -> 0x1E
            '_' -> 0x1F
            else -> char.code and 0x1F
        }
        return byteArrayOf(code.toByte())
    }

    /**
     * 映射方向键
     */
    private fun mapArrowKey(direction: String): ByteArray {
        return when (mode) {
            TerminalMode.NORMAL -> "\u001B[$direction".toByteArray()
            TerminalMode.APP_KEY -> "\u001BO$direction".toByteArray()
        }
    }

    /**
     * 映射功能键
     */
    private fun mapFunctionKey(number: Int): ByteArray {
        return when (mode) {
            TerminalMode.NORMAL -> "\u001B[$number~".toByteArray()
            TerminalMode.APP_KEY -> "\u001B[1;$number~".toByteArray()
        }
    }

    /**
     * 设置终端模式
     */
    fun setMode(newMode: TerminalMode) {
        mode = newMode
    }

    /**
     * 获取当前模式
     */
    fun getMode(): TerminalMode = mode
}



