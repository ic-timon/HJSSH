package cn.hjhw.ssh.terminal

/**
 * 终端单元格 - 表示屏幕上的一个字符位置
 */
data class TerminalCell(
    val char: Char = ' ',
    val foregroundColor: TerminalColor = TerminalColor.Default,
    val backgroundColor: TerminalColor = TerminalColor.Default,
    val bold: Boolean = false,
    val faint: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val blink: Boolean = false,
    val reverse: Boolean = false,
    val invisible: Boolean = false,
    val isWideChar: Boolean = false, // 是否是宽字符（如中文，占2个单元格）
    val isWideContinuation: Boolean = false, // 是否是宽字符的延续单元格（不渲染）
) {
    /**
     * 创建默认的空单元格
     */
    companion object {
        fun empty(): TerminalCell = TerminalCell()
    }
}

/**
 * 终端颜色
 */
sealed class TerminalColor {
    /**
     * 默认颜色（继承自终端设置）
     */
    object Default : TerminalColor()

    /**
     * 标准 16 色
     */
    enum class StandardColor(val index: Int) {
        Black(0),
        Red(1),
        Green(2),
        Yellow(3),
        Blue(4),
        Magenta(5),
        Cyan(6),
        White(7),
        BrightBlack(8),
        BrightRed(9),
        BrightGreen(10),
        BrightYellow(11),
        BrightBlue(12),
        BrightMagenta(13),
        BrightCyan(14),
        BrightWhite(15),
    }

    /**
     * 标准颜色
     */
    data class Standard(val color: StandardColor) : TerminalColor()

    /**
     * 256 色索引
     */
    data class Indexed256(val index: Int) : TerminalColor()

    /**
     * TrueColor (RGB)
     */
    data class TrueColor(val r: Int, val g: Int, val b: Int) : TerminalColor()
}
