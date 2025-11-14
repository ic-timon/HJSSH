package cn.hjhw.ssh.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class TerminalBufferTest {
    @Test
    fun testBufferInitialization() {
        val buffer = TerminalBuffer(80, 24)
        assertEquals(80, buffer.getWidth())
        assertEquals(24, buffer.getHeight())
        assertEquals(0, buffer.getScrollOffset())
        assertTrue(buffer.isAtBottom())
    }

    @Test
    fun testGetVisibleLine() {
        val buffer = TerminalBuffer(80, 24)
        val line = buffer.getVisibleLine(0)
        assertNotNull(line)
        // 新初始化的行宽度为 0，直到有内容
        assertTrue(line.width() >= 0)
    }

    @Test
    fun testNewLine() {
        val buffer = TerminalBuffer(80, 24)
        val initialTotal = buffer.totalLines()
        
        buffer.newLine()
        
        assertEquals(initialTotal + 1, buffer.totalLines())
        assertTrue(buffer.isAtBottom())
    }

    @Test
    fun testScrollUpDown() {
        val buffer = TerminalBuffer(80, 24)
        
        // 添加一些行
        for (i in 0 until 10) {
            buffer.newLine()
        }
        
        val totalBeforeScroll = buffer.totalLines()
        buffer.scrollUp(5)
        
        assertEquals(5, buffer.getScrollOffset())
        assertFalse(buffer.isAtBottom())
        
        buffer.scrollDown(3)
        assertEquals(2, buffer.getScrollOffset())
        
        buffer.scrollToBottom()
        assertTrue(buffer.isAtBottom())
        assertEquals(0, buffer.getScrollOffset())
    }

    @Test
    fun testScrollbackLimit() {
        val buffer = TerminalBuffer(80, 24, scrollbackSize = 100)
        
        // 添加超过 scrollback 大小的行
        for (i in 0 until 150) {
            buffer.newLine()
        }
        
        // 总行数应该被限制
        assertTrue(buffer.totalLines() <= 100 + 24)
    }

    @Test
    fun testClear() {
        val buffer = TerminalBuffer(80, 24)
        
        // 添加一些行
        for (i in 0 until 10) {
            buffer.newLine()
        }
        buffer.scrollUp(5)
        
        buffer.clear()
        
        assertEquals(24, buffer.totalLines())
        assertTrue(buffer.isAtBottom())
        assertEquals(0, buffer.getScrollOffset())
    }
}

