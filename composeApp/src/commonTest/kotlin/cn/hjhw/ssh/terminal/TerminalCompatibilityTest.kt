package cn.hjhw.ssh.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 终端兼容性自动化测试基类
 */
abstract class TerminalCompatibilityTestBase {
    protected lateinit var buffer: TerminalBuffer
    protected lateinit var cursor: CursorState
    protected lateinit var parser: AnsiParser

    fun setup(
        width: Int = 80,
        height: Int = 24,
    ) {
        buffer = TerminalBuffer(width, height)
        cursor = CursorState()
        parser = AnsiParser()
    }

    /**
     * 模拟终端输入
     */
    protected fun sendInput(input: String) {
        parser.parse(
            input = input,
            cursor = cursor,
            buffer = buffer,
            onText = { char, style ->
                if (cursor.wrapPending) {
                    cursor.x = 0
                    cursor.y++
                    if (cursor.y >= buffer.getHeight()) {
                        buffer.newLine()
                        cursor.y = buffer.getHeight() - 1
                    }
                    cursor.wrapPending = false
                }

                val line = buffer.getLine(cursor.y)
                if (line != null) {
                    // 创建带样式的单元格
                    val cell =
                        TerminalCell(
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
                        )
                    line.setCell(cursor.x, cell)
                }

                cursor.x++
                if (cursor.x >= buffer.getWidth()) {
                    cursor.wrapPending = true
                }
            },
            onControlSequence = { /* 控制序列已在 parser 中处理 */ },
        )
    }

    /**
     * 获取指定行的文本内容
     */
    protected fun getLineText(lineIndex: Int): String {
        val line = buffer.getLine(lineIndex) ?: return ""
        return buildString {
            for (x in 0 until buffer.getWidth()) {
                val cell = line.getCell(x)
                if (cell.char != ' ' || cell.backgroundColor !is TerminalColor.Default) {
                    append(cell.char)
                }
            }
        }.trimEnd()
    }

    /**
     * 获取光标位置
     */
    protected fun getCursorPosition(): Pair<Int, Int> = Pair(cursor.x, cursor.y)

    /**
     * 检查单元格属性
     */
    protected fun getCellAt(
        x: Int,
        y: Int,
    ): TerminalCell {
        return buffer.getLine(y)?.getCell(x) ?: TerminalCell.empty()
    }

    /**
     * 检查是否在 alternate screen
     */
    protected fun isInAlternateScreen(): Boolean = buffer.isUsingAlternateScreen()

    /**
     * 打印当前屏幕内容（用于调试）
     */
    protected fun dumpScreen() {
        println("=== Screen Dump (${buffer.getWidth()}x${buffer.getHeight()}) ===")
        for (y in 0 until buffer.getHeight()) {
            val line = getLineText(y)
            println("$y: |$line|")
        }
        println("Cursor: (${cursor.x}, ${cursor.y})")
        println("===================")
    }
}

/**
 * 基础 ANSI 序列测试
 */
class BasicAnsiTest : TerminalCompatibilityTestBase() {
    @Test
    fun testSimpleText() {
        setup()
        sendInput("Hello, World!")
        // 注意：getLineText 会 trim 尾部空格，所以直接检查内容是否包含
        val text = getLineText(0)
        assertTrue(text.startsWith("Hello,"))
        assertTrue(text.contains("World!"))
        assertEquals(Pair(13, 0), getCursorPosition())
    }

    @Test
    fun testColoredText() {
        setup()
        sendInput("\u001b[31mRed\u001b[0m Normal")
        val cell = getCellAt(0, 0)
        assertTrue(cell.foregroundColor is TerminalColor.Standard)
        assertEquals('R', cell.char)
    }

    @Test
    fun testBoldText() {
        setup()
        sendInput("\u001b[1mBold\u001b[0m")
        assertTrue(getCellAt(0, 0).bold)
    }

    @Test
    fun testReverseVideo() {
        setup()
        sendInput("\u001b[7mReversed\u001b[0m")
        assertTrue(getCellAt(0, 0).reverse)
    }

    @Test
    fun testCursorMovement() {
        setup()
        sendInput("Test")
        sendInput("\u001b[H") // Home
        assertEquals(Pair(0, 0), getCursorPosition())

        sendInput("\u001b[5;10H") // Move to (10, 5)
        assertEquals(Pair(9, 4), getCursorPosition()) // 0-based
    }

    @Test
    fun testEraseInLine() {
        setup()
        sendInput("Hello, World!")
        sendInput("\u001b[H") // Home
        sendInput("\u001b[K") // Erase to end of line
        assertEquals("", getLineText(0).trim())
    }
}

/**
 * 滚动和换行测试
 */
class ScrollingTest : TerminalCompatibilityTestBase() {
    @Test
    fun testNewLine() {
        setup(80, 24)
        sendInput("Line1\n")
        sendInput("Line2")
        // 验证换行功能
        val line0 = getLineText(0)
        val line1 = getLineText(1)
        assertTrue(line0.contains("Line1"), "First line should contain 'Line1'")
        assertTrue(line1.contains("Line2"), "Second line should contain 'Line2'")
        // 验证光标在第二行
        assertEquals(1, getCursorPosition().second, "Cursor should be on line 1")
    }

    @Test
    fun testScrollRegion() {
        setup(80, 24)
        // 设置滚动区域 (5-20)
        sendInput("\u001b[5;20r")
        assertEquals(4, buffer.getScrollRegionTop())
        assertEquals(19, buffer.getScrollRegionBottom())
    }

    @Test
    fun testReverseIndex() {
        setup(80, 10)
        // 设置滚动区域
        sendInput("\u001b[2;8r")
        // 移动到滚动区域顶部
        sendInput("\u001b[2;1H")
        sendInput("Top Line")
        // 执行 Reverse Index
        sendInput("\u001bM")
        // 应该在顶部插入空行
        assertTrue(getLineText(1).isEmpty())
    }
}

/**
 * Alternate Screen Buffer 测试
 */
class AlternateScreenTest : TerminalCompatibilityTestBase() {
    @Test
    fun testSwitchToAlternateScreen() {
        setup()
        sendInput("Main Screen")
        assertFalse(isInAlternateScreen())

        // 切换到 alternate screen
        sendInput("\u001b[?1049h")
        assertTrue(isInAlternateScreen())
        assertEquals("", getLineText(0).trim())
    }

    @Test
    fun testRestoreFromAlternateScreen() {
        setup()
        sendInput("Main Screen Content")
        val mainContent = getLineText(0)

        // 切换到 alternate screen
        sendInput("\u001b[?1049h")
        sendInput("Alternate Content")

        // 切换回主屏幕
        sendInput("\u001b[?1049l")
        assertFalse(isInAlternateScreen())
        assertEquals(mainContent, getLineText(0))
    }

    @Test
    fun testCursorPositionAfterAlternateScreen() {
        setup()
        // 在主屏幕设置光标位置
        sendInput("\u001b[5;10H")
        val savedPos = getCursorPosition()

        // 切换到 alternate screen
        sendInput("\u001b[?1049h")
        // 在 alternate screen 移动光标
        sendInput("\u001b[20;30H")

        // 切换回主屏幕
        sendInput("\u001b[?1049l")
        // 光标应该恢复到保存的位置
        assertEquals(savedPos, getCursorPosition())
    }
}

/**
 * 宽字符测试
 */
class WideCharacterTest : TerminalCompatibilityTestBase() {
    @Test
    fun testCJKCharacters() {
        setup()
        sendInput("中文测试")
        val line = getLineText(0)
        assertTrue(line.contains("中"))
        assertTrue(line.contains("文"))
    }

    @Test
    fun testMixedWidthCharacters() {
        setup()
        sendInput("Hello世界")
        val line = getLineText(0)
        assertTrue(line.contains("Hello"))
        assertTrue(line.contains("世"))
        assertTrue(line.contains("界"))
    }
}

/**
 * 光标保存/恢复测试
 */
class CursorSaveRestoreTest : TerminalCompatibilityTestBase() {
    @Test
    fun testDECSC_DECRC() {
        setup()
        sendInput("\u001b[10;20H") // 移动光标
        sendInput("\u001b7") // 保存光标 (ESC 7)
        sendInput("\u001b[5;5H") // 移动到其他位置
        sendInput("\u001b8") // 恢复光标 (ESC 8)
        assertEquals(Pair(19, 9), getCursorPosition())
    }

    @Test
    fun testSeparateSaveForAltScreen() {
        setup()
        // 主屏幕位置
        sendInput("\u001b[10;10H")

        // 保存并切换到 alternate screen
        sendInput("\u001b[?1049h")
        assertEquals(Pair(0, 0), getCursorPosition())

        // 在 alternate screen 移动并保存光标
        sendInput("\u001b[5;5H")
        sendInput("\u001b7") // ESC 7
        sendInput("\u001b[20;20H")
        sendInput("\u001b8") // ESC 8 - 应该恢复到 (5,5)
        assertEquals(Pair(4, 4), getCursorPosition())

        // 切换回主屏幕
        sendInput("\u001b[?1049l")
        // 应该恢复到 (10,10)
        assertEquals(Pair(9, 9), getCursorPosition())
    }
}

/**
 * 延迟换行测试
 */
class DelayedWrapTest : TerminalCompatibilityTestBase() {
    @Test
    fun testDelayedWrap() {
        setup(10, 5)
        // 填满一行（10个字符）
        sendInput("1234567890")
        // 光标应该在行尾，待定换行
        assertEquals(Pair(10, 0), getCursorPosition())
        assertTrue(cursor.wrapPending)

        // 再输入一个字符，应该换行
        sendInput("A")
        assertEquals(Pair(1, 1), getCursorPosition())
        assertFalse(cursor.wrapPending)
    }

    @Test
    fun testCursorMovementCancelsWrap() {
        setup(10, 5)
        sendInput("1234567890")
        assertTrue(cursor.wrapPending)

        // 移动光标应该取消待定换行
        sendInput("\u001b[H")
        assertFalse(cursor.wrapPending)
        assertEquals(Pair(0, 0), getCursorPosition())
    }
}
