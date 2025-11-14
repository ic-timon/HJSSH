package cn.hjhw.ssh.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnsiParserTest {
    @Test
    fun testSgrRedText() {
        val parser = AnsiParser()
        val cursor = CursorState()
        val buffer = TerminalBuffer(80, 24)
        var receivedStyle: CellStyle? = null

        val input = "\u001B[31mred\u001B[0m"
        parser.parse(
            input = input,
            cursor = cursor,
            buffer = buffer,
            onText = { char, style ->
                if (char == 'r') {
                    receivedStyle = style
                }
            },
            onControlSequence = {},
        )

        assertNotNull(receivedStyle)
        assertTrue(receivedStyle!!.foregroundColor is TerminalColor.Standard)
        assertEquals(
            TerminalColor.StandardColor.Red,
            (receivedStyle!!.foregroundColor as TerminalColor.Standard).color,
        )
    }

    @Test
    fun testCursorMovement() {
        val parser = AnsiParser()
        val cursor = CursorState()
        val buffer = TerminalBuffer(80, 24)

        // 先移动到中间位置
        cursor.moveTo(10, 10)

        // 测试光标上移
        parser.parse("\u001B[5A", cursor, buffer, { _, _ -> }, {})
        assertEquals(10, cursor.x)
        assertEquals(5, cursor.y) // 向上移动5行：10-5=5（会被限制为>=0）

        // 重置光标
        cursor.moveTo(10, 10)

        // 测试光标右移
        parser.parse("\u001B[3C", cursor, buffer, { _, _ -> }, {})
        assertEquals(13, cursor.x)
        assertEquals(10, cursor.y)

        // 测试光标定位
        parser.parse("\u001B[5;10H", cursor, buffer, { _, _ -> }, {})
        assertEquals(9, cursor.x) // 列从1开始，所以10-1=9
        assertEquals(4, cursor.y) // 行从1开始，所以5-1=4
    }

    @Test
    fun testClearScreen() {
        val parser = AnsiParser()
        val cursor = CursorState()
        val buffer = TerminalBuffer(80, 24)
        var clearCalled = false

        // 添加一些内容
        buffer.newLine()
        buffer.newLine()

        parser.parse(
            "\u001B[2J",
            cursor,
            buffer,
            { _, _ -> },
            { seq ->
                if (seq is ControlSequence.EraseDisplay && seq.mode == 2) {
                    clearCalled = true
                }
            },
        )

        assertTrue(clearCalled)
        assertEquals(0, cursor.x)
        assertEquals(0, cursor.y)
    }

    @Test
    fun testTrueColor() {
        val parser = AnsiParser()
        val cursor = CursorState()
        val buffer = TerminalBuffer(80, 24)

        // 设置 TrueColor 前景色 (RGB: 255, 128, 64)，然后输出一个字符来验证样式
        parser.parse(
            "\u001B[38;2;255;128;64mX",
            cursor,
            buffer,
            { char, style ->
                if (char == 'X') {
                    assertTrue(style.foregroundColor is TerminalColor.TrueColor)
                    val trueColor = style.foregroundColor as TerminalColor.TrueColor
                    assertEquals(255, trueColor.r)
                    assertEquals(128, trueColor.g)
                    assertEquals(64, trueColor.b)
                }
            },
            {},
        )
    }

    @Test
    fun test256Color() {
        val parser = AnsiParser()
        val cursor = CursorState()
        val buffer = TerminalBuffer(80, 24)

        // 设置 256 色索引，然后输出一个字符来验证样式
        parser.parse(
            "\u001B[38;5;42mX",
            cursor,
            buffer,
            { char, style ->
                if (char == 'X') {
                    assertTrue(style.foregroundColor is TerminalColor.Indexed256)
                    assertEquals(42, (style.foregroundColor as TerminalColor.Indexed256).index)
                }
            },
            {},
        )
    }

    @Test
    fun testTextStyles() {
        val parser = AnsiParser()
        val cursor = CursorState()
        val buffer = TerminalBuffer(80, 24)

        // 测试粗体
        parser.parse("\u001B[1m", cursor, buffer, { _, _ -> }, {})
        assertTrue(cursor.currentStyle.bold)

        // 测试下划线
        parser.parse("\u001B[4m", cursor, buffer, { _, _ -> }, {})
        assertTrue(cursor.currentStyle.underline)

        // 测试重置
        parser.parse("\u001B[0m", cursor, buffer, { _, _ -> }, {})
        assertFalse(cursor.currentStyle.bold)
        assertFalse(cursor.currentStyle.underline)
    }

    @Test
    fun testUtf8Characters() {
        val parser = AnsiParser()
        val cursor = CursorState()
        val buffer = TerminalBuffer(80, 24)
        val receivedChars = mutableListOf<Char>()

        // 测试中文字符
        val input = "你好世界"
        parser.parse(
            input,
            cursor,
            buffer,
            { char, _ -> receivedChars.add(char) },
            {},
        )

        assertEquals(4, receivedChars.size)
        assertEquals('你', receivedChars[0])
        assertEquals('好', receivedChars[1])
        assertEquals('世', receivedChars[2])
        assertEquals('界', receivedChars[3])
    }

    @Test
    fun testFuzzTolerance() {
        val parser = AnsiParser()
        val cursor = CursorState()
        val buffer = TerminalBuffer(80, 24)

        // 测试各种异常输入，不应该崩溃
        val fuzzInputs =
            listOf(
                // 不完整的序列
                "\u001B[",
                // 超大数字
                "\u001B[999999999m",
                // 空参数
                "\u001B[;m",
                // 不完整的 TrueColor
                "\u001B[38;2;",
                // 不完整的 256色
                "\u001B[38;5;",
                // 无效命令
                "\u001B[XYZ",
            )

        for (input in fuzzInputs) {
            try {
                parser.parse(input, cursor, buffer, { _, _ -> }, {})
            } catch (e: Exception) {
                // 不应该抛出异常
                throw AssertionError("Parser should handle fuzz input gracefully: $input", e)
            }
        }
    }
}
