package cn.hjhw.ssh.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

class KeyMapperTest {
    @Test
    fun testArrowKeys() {
        val mapper = KeyMapper()

        val up = mapper.mapKeyEvent(KeyEvent(Key.Up))
        assertEquals("\u001B[A", String(up))

        val down = mapper.mapKeyEvent(KeyEvent(Key.Down))
        assertEquals("\u001B[B", String(down))

        val left = mapper.mapKeyEvent(KeyEvent(Key.Left))
        assertEquals("\u001B[D", String(left))

        val right = mapper.mapKeyEvent(KeyEvent(Key.Right))
        assertEquals("\u001B[C", String(right))
    }

    @Test
    fun testControlKeys() {
        val mapper = KeyMapper()

        val enter = mapper.mapKeyEvent(KeyEvent(Key.Enter))
        assertEquals("\r", String(enter))

        val tab = mapper.mapKeyEvent(KeyEvent(Key.Tab))
        assertEquals("\t", String(tab))

        val escape = mapper.mapKeyEvent(KeyEvent(Key.Escape))
        assertEquals("\u001B", String(escape))
    }

    @Test
    fun testCtrlChar() {
        val mapper = KeyMapper()

        // Ctrl+C
        val ctrlC =
            mapper.mapKeyEvent(
                KeyEvent(Key.C, setOf(KeyModifier.Ctrl), 'c'),
            )
        assertEquals(1, ctrlC.size)
        assertEquals(0x03.toByte(), ctrlC[0])

        // Ctrl+D (EOF)
        val ctrlD =
            mapper.mapKeyEvent(
                KeyEvent(Key.D, setOf(KeyModifier.Ctrl), 'd'),
            )
        assertEquals(0x04.toByte(), ctrlD[0])

        // Ctrl+L (清屏)
        val ctrlL =
            mapper.mapKeyEvent(
                KeyEvent(Key.L, setOf(KeyModifier.Ctrl), 'l'),
            )
        assertEquals(0x0C.toByte(), ctrlL[0])
    }

    @Test
    fun testFunctionKeys() {
        val mapper = KeyMapper()

        val f1 = mapper.mapKeyEvent(KeyEvent(Key.F1))
        assertEquals("\u001B[1~", String(f1))

        val f5 = mapper.mapKeyEvent(KeyEvent(Key.F5))
        assertEquals("\u001B[5~", String(f5))
    }

    @Test
    fun testAppKeyMode() {
        val mapper = KeyMapper(TerminalMode.APP_KEY)

        val up = mapper.mapKeyEvent(KeyEvent(Key.Up))
        assertEquals("\u001BOA", String(up))

        val f1 = mapper.mapKeyEvent(KeyEvent(Key.F1))
        assertEquals("\u001B[1;1~", String(f1))
    }

    @Test
    fun testNormalCharacters() {
        val mapper = KeyMapper()

        val charA = mapper.mapKeyEvent(KeyEvent(Key.A, character = 'a'))
        assertEquals("a", String(charA))

        val charSpace = mapper.mapKeyEvent(KeyEvent(Key.Space, character = ' '))
        assertEquals(" ", String(charSpace))
    }

    @Test
    fun testCommandSequence() {
        val mapper = KeyMapper()

        // 模拟输入 "ls\r"
        val ls = mapper.mapKeyEvent(KeyEvent(Key.L, character = 'l'))
        val s = mapper.mapKeyEvent(KeyEvent(Key.S, character = 's'))
        val enter = mapper.mapKeyEvent(KeyEvent(Key.Enter))

        val command = String(ls) + String(s) + String(enter)
        assertEquals("ls\r", command)
    }
}
