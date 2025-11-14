package cn.hjhw.ssh.terminal

/**
 * 光标状态
 */
data class CursorState(
    var x: Int = 0,
    var y: Int = 0,
    var visible: Boolean = true,
    var blink: Boolean = true,
    var currentStyle: CellStyle = CellStyle.default(),
    // 保存的光标位置（用于 ESC 7/8）
    var savedX: Int = 0,
    var savedY: Int = 0,
    var savedStyle: CellStyle = CellStyle.default(),
    // 备用屏幕切换时保存的光标位置（用于 ESC[?1049h/l，独立于 ESC 7/8）
    var altScreenSavedX: Int = 0,
    var altScreenSavedY: Int = 0,
    var altScreenSavedStyle: CellStyle = CellStyle.default(),
    // 延迟换行标志（当光标在行尾且有待定换行时为 true）
    var wrapPending: Boolean = false,
) {
    /**
     * 移动到指定位置
     */
    fun moveTo(
        x: Int,
        y: Int,
    ) {
        this.x = x.coerceAtLeast(0)
        this.y = y.coerceAtLeast(0)
        // 任何显式的光标移动都会取消待定的换行
        wrapPending = false
    }

    /**
     * 移动相对位置
     */
    fun moveBy(
        dx: Int,
        dy: Int,
    ) {
        this.x = (this.x + dx).coerceAtLeast(0)
        this.y = (this.y + dy).coerceAtLeast(0)
        // 任何显式的光标移动都会取消待定的换行
        wrapPending = false
    }

    /**
     * 保存当前光标位置和样式（用于 ESC 7）
     */
    fun save() {
        savedX = x
        savedY = y
        savedStyle = currentStyle
    }

    /**
     * 恢复保存的光标位置和样式（用于 ESC 8）
     */
    fun restore() {
        x = savedX
        y = savedY
        currentStyle = savedStyle
    }

    /**
     * 保存当前光标位置和样式（用于 alternate screen 切换）
     */
    fun saveForAltScreen() {
        altScreenSavedX = x
        altScreenSavedY = y
        altScreenSavedStyle = currentStyle
        println("      [Cursor] Saved for alt screen: ($altScreenSavedX, $altScreenSavedY)")
    }

    /**
     * 恢复 alternate screen 切换时保存的光标位置和样式
     */
    fun restoreFromAltScreen() {
        x = altScreenSavedX
        y = altScreenSavedY
        currentStyle = altScreenSavedStyle
        println("      [Cursor] Restored from alt screen: ($x, $y)")
    }

    /**
     * 重置到初始位置
     */
    fun reset() {
        x = 0
        y = 0
        visible = true
        blink = true
        currentStyle = CellStyle.default()
        savedX = 0
        savedY = 0
        savedStyle = CellStyle.default()
    }
}

/**
 * 单元格样式（用于应用新字符时的样式）
 */
data class CellStyle(
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
) {
    /**
     * 应用样式到单元格
     */
    fun applyTo(cell: TerminalCell): TerminalCell {
        return cell.copy(
            foregroundColor = if (foregroundColor !is TerminalColor.Default) foregroundColor else cell.foregroundColor,
            backgroundColor = if (backgroundColor !is TerminalColor.Default) backgroundColor else cell.backgroundColor,
            bold = bold || cell.bold,
            faint = faint || cell.faint,
            italic = italic || cell.italic,
            underline = underline || cell.underline,
            strikethrough = strikethrough || cell.strikethrough,
            blink = blink || cell.blink,
            reverse = reverse || cell.reverse,
            invisible = invisible || cell.invisible,
        )
    }

    /**
     * 创建默认样式
     */
    companion object {
        fun default(): CellStyle = CellStyle()
    }
}
