package cn.hjhw.ssh.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalLineTest {
    @Test
    fun testLineInitialization() {
        val line = TerminalLine(80)
        assertEquals(0, line.width())
    }

    @Test
    fun testSetAndGetCell() {
        val line = TerminalLine(80)
        val cell = TerminalCell(char = 'A', bold = true)
        
        line.setCell(10, cell)
        
        val retrieved = line.getCell(10)
        assertEquals('A', retrieved.char)
        assertTrue(retrieved.bold)
    }

    @Test
    fun testInsertAndDelete() {
        val line = TerminalLine(80)
        
        line.setCell(0, TerminalCell(char = 'A'))
        line.setCell(1, TerminalCell(char = 'B'))
        line.setCell(2, TerminalCell(char = 'C'))
        
        assertEquals(3, line.width())
        
        line.insertAt(1, TerminalCell(char = 'X'))
        assertEquals('X', line.getCell(1).char)
        assertEquals('B', line.getCell(2).char)
        
        line.deleteAt(1)
        assertEquals('B', line.getCell(1).char)
    }

    @Test
    fun testTruncate() {
        val line = TerminalLine(80)
        
        for (i in 0 until 100) {
            line.setCell(i, TerminalCell(char = 'X'))
        }
        
        line.truncate(50)
        assertEquals(50, line.width())
    }

    @Test
    fun testMaxWidthLimit() {
        val line = TerminalLine(80)
        
        // 尝试设置超出最大宽度的单元格
        line.setCell(100, TerminalCell(char = 'X'))
        
        // 宽度应该被限制在最大宽度
        assertTrue(line.width() <= 80)
    }
}

