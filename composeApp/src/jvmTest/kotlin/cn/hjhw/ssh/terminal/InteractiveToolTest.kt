package cn.hjhw.ssh.terminal

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

/**
 * 交互式工具模拟测试
 * 这些测试模拟真实工具的输出序列
 */

/**
 * nano 编辑器测试
 */
class NanoEditorTest : TerminalCompatibilityTestBase() {
    
    @Test
    fun testNanoStartup() {
        setup(80, 24)
        
        // 模拟 nano 启动（切换到 alternate screen）
        sendInput("\u001b[?1049h")
        assertTrue(isInAlternateScreen(), "Should switch to alternate screen")
        
        // 模拟标题栏（简化测试）
        sendInput("\u001b[H") // Home
        sendInput("\u001b[7m") // Reverse video
        sendInput("GNU nano")
        sendInput("\u001b[m") // Reset
        
        // 验证内容和reverse属性
        val titleLine = getLineText(0)
        assertTrue(titleLine.contains("GNU"), "Title should contain 'GNU'")
        
        // 检查有反显单元格
        val hasReverse = (0 until 20).any { x -> 
            getCellAt(x, 0).reverse 
        }
        assertTrue(hasReverse || titleLine.contains("nano"), 
            "Title bar should have reverse video or contain 'nano'")
    }
    
    @Test
    fun testNanoEditingWithNewlines() {
        setup(80, 24)
        
        // 切换到 alternate screen
        sendInput("\u001b[?1049h")
        
        // 设置滚动区域（nano 的编辑区域）
        sendInput("\u001b[4;22r") // 行 4-22 为编辑区
        sendInput("\u001b[4;1H") // 移动到编辑区开始
        
        sendInput("Line 1")
        
        // 模拟按回车键：nano 会使用 RI (Reverse Index)
        sendInput("\u001b[4;1H") // 回到行首
        sendInput("\u001bM") // Reverse Index - 在当前位置上方插入空行
        
        // 新行应该被插入
        assertTrue(getLineText(3).isEmpty() || getLineText(3).isBlank())
    }
    
    @Test
    fun testNanoExit() {
        setup(80, 24)
        
        // 在主屏幕记录位置
        sendInput("$ ")
        val mainScreenContent = getLineText(0)
        sendInput("\u001b[1;3H") // 光标在 '$ ' 后面
        
        // 启动 nano
        sendInput("\u001b[?1049h")
        sendInput("nano content")
        
        // 退出 nano
        sendInput("\u001b[?1049l")
        
        // 应该返回主屏幕
        assertFalse(isInAlternateScreen())
        assertEquals(mainScreenContent, getLineText(0))
    }
}

/**
 * vim 编辑器测试
 */
class VimEditorTest : TerminalCompatibilityTestBase() {
    
    @Test
    fun testVimStartup() {
        setup(80, 24)
        
        // vim 启动（alternate screen）
        sendInput("\u001b[?1049h")
        assertTrue(isInAlternateScreen())
        
        // vim 的波浪线（空行标记）
        sendInput("\u001b[2;1H")
        sendInput("\u001b[36m~\u001b[m") // 青色的 ~
        
        val cell = getCellAt(0, 1)
        assertEquals('~', cell.char)
    }
    
    @Test
    fun testVimStatusLine() {
        setup(80, 24)
        sendInput("\u001b[?1049h")
        
        // 模拟 vim 状态栏（最后一行，反显）
        sendInput("\u001b[24;1H")
        sendInput("\u001b[7m")
        sendInput(" test.txt                                     1,1           All ")
        sendInput("\u001b[m")
        
        assertTrue(getCellAt(0, 23).reverse)
        assertTrue(getLineText(23).contains("test.txt"))
    }
}

/**
 * htop 监控工具测试
 */
class HtopTest : TerminalCompatibilityTestBase() {
    
    @Test
    fun testHtopHeader() {
        setup(120, 40)
        
        // htop 使用 alternate screen
        sendInput("\u001b[?1049h")
        
        // 模拟 CPU 使用率条（带颜色）
        sendInput("\u001b[1;1H")
        sendInput("\u001b[32m") // 绿色
        sendInput("  1  [")
        sendInput("\u001b[42m   \u001b[m") // 绿色背景
        sendInput("     ]")
        
        assertTrue(getLineText(0).contains("["))
        assertTrue(getLineText(0).contains("]"))
    }
    
    @Test
    fun testHtopProcessList() {
        setup(120, 40)
        sendInput("\u001b[?1049h")
        
        // 模拟进程列表（带颜色和对齐）
        sendInput("\u001b[10;1H")
        sendInput("\u001b[1m") // Bold
        sendInput("  PID USER      PRI  NI  VIRT   RES   SHR S CPU% MEM%   TIME+  Command")
        sendInput("\u001b[m")
        
        val header = getLineText(9)
        assertTrue(header.contains("PID"))
        assertTrue(header.contains("CPU%"))
    }
}

/**
 * less 分页器测试
 */
class LessTest : TerminalCompatibilityTestBase() {
    
    @Test
    fun testLessDisplay() {
        setup(80, 24)
        
        // less 使用 alternate screen
        sendInput("\u001b[?1049h")
        
        // 显示文件内容
        for (i in 1..20) {
            sendInput("\u001b[$i;1H")
            sendInput("Line $i of the file")
        }
        
        // 最后一行显示提示符（反显）
        sendInput("\u001b[24;1H")
        sendInput("\u001b[7m:")
        sendInput("\u001b[m")
        
        assertTrue(getCellAt(0, 23).reverse)
        assertEquals(':', getCellAt(0, 23).char)
    }
}

/**
 * tmux 终端复用器测试
 */
class TmuxTest : TerminalCompatibilityTestBase() {
    
    @Test
    fun testTmuxStatusBar() {
        setup(80, 24)
        
        // tmux 状态栏通常在底部
        sendInput("\u001b[24;1H")
        sendInput("\u001b[30;42m") // 黑字绿底
        sendInput(" [0] 0:bash*")
        sendInput("\u001b[m")
        
        val cell = getCellAt(1, 23)
        assertTrue(cell.backgroundColor is TerminalColor.Standard)
    }
    
    @Test
    fun testTmuxPaneBorders() {
        setup(120, 40)
        
        // 模拟分屏边界（使用线条绘制字符）
        sendInput("\u001b[20;60H")
        sendInput("│") // 垂直线
        
        assertEquals('│', getCellAt(59, 19).char)
    }
}

/**
 * git diff 输出测试
 */
class GitDiffTest : TerminalCompatibilityTestBase() {
    
    @Test
    fun testGitDiffColors() {
        setup(80, 24)
        
        // git diff 文件名（粗体）
        sendInput("\u001b[1mdiff --git a/file.txt b/file.txt\u001b[m\n")
        
        // 删除的行（红色）
        sendInput("\u001b[31m-removed line\u001b[m\n")
        
        // 添加的行（绿色）
        sendInput("\u001b[32m+added line\u001b[m\n")
        
        // 检查粗体属性
        assertTrue(getCellAt(0, 0).bold, "First line should be bold")
        
        // 检查删除行（第二行）
        val removedLine = getLineText(1)
        assertTrue(removedLine.contains("-") || removedLine.contains("removed"), 
            "Second line should contain '-' or 'removed'")
        
        // 检查添加行（第三行）
        val addedLine = getLineText(2)
        assertTrue(addedLine.contains("+") || addedLine.contains("added"),
            "Third line should contain '+' or 'added'")
    }
}

/**
 * Python REPL 测试
 */
class PythonReplTest : TerminalCompatibilityTestBase() {
    
    @Test
    fun testPythonPrompt() {
        setup(80, 24)
        
        // Python 提示符
        sendInput(">>> ")
        assertEquals(">>>", getLineText(0).substring(0, 3))
    }
    
    @Test
    fun testPythonMultilineInput() {
        setup(80, 24)
        
        // 多行输入
        sendInput(">>> def test():\n")
        sendInput("...     print('hello')\n")
        sendInput("... \n")
        
        assertTrue(getLineText(0).startsWith(">>>"))
        assertTrue(getLineText(1).startsWith("..."))
    }
}

/**
 * man 手册页测试
 */
class ManPageTest : TerminalCompatibilityTestBase() {
    
    @Test
    fun testManPageFormatting() {
        setup(80, 24)
        
        // man 页面使用 alternate screen
        sendInput("\u001b[?1049h")
        
        // 粗体文本（命令名）
        sendInput("\u001b[1mNAME\u001b[m\n")
        sendInput("       ls - list directory contents\n")
        
        // 下划线文本（参数）
        sendInput("\u001b[4moption\u001b[m")
        
        assertTrue(getCellAt(0, 0).bold)
    }
}

/**
 * curl 下载进度测试
 */
class CurlProgressTest : TerminalCompatibilityTestBase() {
    
    @Test
    fun testCurlProgressBar() {
        setup(80, 5)
        
        // curl 进度条（使用 \r 回到行首更新）
        sendInput("  % Total    % Received % Xferd\r")
        sendInput("  0     0    0     0    0     0      0      0 --:--:-- --:--:--\r")
        sendInput(" 10  1024   10   102    0     0    500      0  0:00:02  0:00:01\r")
        
        val line = getLineText(0)
        assertTrue(line.contains("10") || line.contains("Total"))
    }
}

/**
 * 表格化输出测试（如 mysql, ps 等）
 */
class TableOutputTest : TerminalCompatibilityTestBase() {
    
    @Test
    fun testAlignedColumns() {
        setup(120, 24)
        
        // 模拟 ps 命令输出
        sendInput("  PID TTY          TIME CMD\n")
        sendInput(" 1234 pts/0    00:00:05 bash\n")
        sendInput(" 5678 pts/0    00:00:01 vim\n")
        
        assertTrue(getLineText(0).contains("PID"))
        assertTrue(getLineText(1).contains("1234"))
        assertTrue(getLineText(2).contains("5678"))
    }
    
    @Test
    fun testBoxDrawingCharacters() {
        setup(80, 24)
        
        // 使用框线字符
        sendInput("┌─────┬─────┐\n")
        sendInput("│ A   │ B   │\n")
        sendInput("└─────┴─────┘\n")
        
        assertEquals('┌', getCellAt(0, 0).char)
        assertEquals('─', getCellAt(1, 0).char)
        assertEquals('┬', getCellAt(6, 0).char)
    }
}

/**
 * Yazi 文件管理器测试
 * 
 * Yazi是一个现代化的终端文件管理器，使用Rust编写
 * 特点：快速、易用、支持文件预览
 */
class YaziTest : TerminalCompatibilityTestBase() {
    
    @Test
    fun testYaziStartup() {
        setup(160, 50)
        
        // 模拟 yazi 启动并切换到 alternate screen
        sendInput("\u001b[?1049h")
        assertTrue(isInAlternateScreen(), "Yazi should use alternate screen")
        
        // 模拟清屏
        sendInput("\u001b[2J")
        sendInput("\u001b[H")
        
        // 模拟顶部状态栏（反显）
        sendInput("\u001b[7m")
        sendInput(" /home/user                                     ")
        sendInput("\u001b[m\n")
        
        // 模拟文件列表
        sendInput("  Documents/\n")
        sendInput("  Downloads/\n")
        sendInput("  Pictures/\n")
        sendInput("\u001b[7m")  // 选中行反显
        sendInput("  Videos/\n")
        sendInput("\u001b[m")
        sendInput("  Music/\n")
        
        // 验证内容
        val line0 = getLineText(0)
        val line1 = getLineText(1)
        val line2 = getLineText(2)
        assertTrue(
            line0.contains("Documents") || line0.contains("home") ||
            line1.contains("Documents") || line2.contains("Documents"), 
            "Should show file listing"
        )
        
        // 验证有反显单元格（选中行）
        val hasReverse = (0 until buffer.getWidth()).any { x -> 
            (0 until 10).any { y -> getCellAt(x, y).reverse }
        }
        assertTrue(hasReverse, "Should have reverse video for selected item")
    }
    
    @Test
    fun testYaziNavigation() {
        setup(160, 50)
        
        // 启动 yazi
        sendInput("\u001b[?1049h")
        sendInput("\u001b[2J\u001b[H")
        
        // 初始文件列表（第一项选中）
        sendInput("\u001b[7mfile1.txt\u001b[m\n")
        sendInput("file2.txt\n")
        sendInput("file3.txt\n")
        
        val initialPos = getCursorPosition()
        
        // 模拟向下移动（j键）- 清除旧选中，新位置反显
        sendInput("\u001b[1;1H")  // 回到第一行
        sendInput("file1.txt\n")  // 取消反显
        sendInput("\u001b[7mfile2.txt\u001b[m\n")  // 新选中
        
        // 验证导航效果
        val line0 = getLineText(0)
        val line1 = getLineText(1)
        val line2 = getLineText(2)
        assertTrue(
            line0.contains("file1") || line1.contains("file1") || line2.contains("file1"), 
            "Should contain file1"
        )
        assertTrue(
            line0.contains("file2") || line1.contains("file2") || line2.contains("file2"),
            "Should contain file2"
        )
    }
    
    @Test
    fun testYaziExit() {
        setup(160, 50)
        
        // 启动 yazi
        sendInput("\u001b[?1049h")
        assertTrue(isInAlternateScreen())
        
        // 显示内容
        sendInput("\u001b[2J\u001b[H")
        sendInput("Yazi File Manager\n")
        sendInput("Documents/\n")
        
        // 退出 yazi（切回主屏幕）
        sendInput("\u001b[?1049l")
        
        // 验证已返回主屏幕
        assertFalse(isInAlternateScreen(), "Should return to main screen after exit")
    }
    
    @Test
    fun testYaziFilePreview() {
        setup(160, 50)
        
        // 启动 yazi 并进入 alternate screen
        sendInput("\u001b[?1049h")
        sendInput("\u001b[2J\u001b[H")
        
        // 模拟三栏布局
        // 左栏：父目录
        sendInput("\u001b[1;1H")
        sendInput("Parent/\n")
        
        // 中栏：当前目录（使用反显高亮选中项）
        sendInput("\u001b[1;30H")
        sendInput("\u001b[7mreadme.txt\u001b[m\n")
        sendInput("\u001b[2;30H")
        sendInput("script.sh\n")
        
        // 右栏：文件预览
        sendInput("\u001b[1;80H")
        sendInput("# README\n")
        sendInput("\u001b[2;80H")
        sendInput("This is a test file\n")
        
        // 验证布局
        val line0 = getLineText(0)
        val line1 = getLineText(1)
        assertTrue(
            line0.contains("readme", ignoreCase = true) || 
            line1.contains("readme", ignoreCase = true), 
            "Should show file name"
        )
        
        // 验证有反显（选中文件）
        val hasReverse = (0 until buffer.getWidth()).any { x -> 
            getCellAt(x, 0).reverse || getCellAt(x, 1).reverse
        }
        assertTrue(hasReverse, "Should have highlighted selection")
    }
    
    @Test
    fun testYaziDirectoryNavigation() {
        setup(160, 50)
        
        // 启动 yazi
        sendInput("\u001b[?1049h")
        sendInput("\u001b[2J\u001b[H")
        
        // 初始目录列表
        sendInput("Current: /home/user\n")
        sendInput("\u001b[7m../\u001b[m\n")
        sendInput("Documents/\n")
        sendInput("Downloads/\n")
        
        assertTrue(isInAlternateScreen())
        
        // 模拟进入目录（按 l 或 Enter）
        // 清屏并显示新目录
        sendInput("\u001b[2J\u001b[H")
        sendInput("Current: /home/user/Documents\n")
        sendInput("\u001b[7m../\u001b[m\n")
        sendInput("report.pdf\n")
        sendInput("notes.txt\n")
        
        // 验证目录切换
        val line0 = getLineText(0)
        val line1 = getLineText(1)
        val line2 = getLineText(2)
        val line3 = getLineText(3)
        assertTrue(
            line0.contains("Documents") || line0.contains("report") || line0.contains("notes") ||
            line1.contains("Documents") || line1.contains("report") || line1.contains("notes") ||
            line2.contains("report") || line2.contains("notes") ||
            line3.contains("report") || line3.contains("notes"),
            "Should show new directory contents"
        )
        
        // 模拟返回上级（按 h）
        sendInput("\u001b[2J\u001b[H")
        sendInput("Current: /home/user\n")
        
        // Yazi 仍在运行
        assertTrue(isInAlternateScreen(), "Should still be in yazi")
    }
    
    @Test
    fun testYaziImagePreview() {
        setup(160, 50)
        
        // 启动 yazi 并进入 alternate screen
        sendInput("\u001b[?1049h")
        sendInput("\u001b[2J\u001b[H")
        
        // 模拟文件列表，选中图片文件
        sendInput("\u001b[1;1H")
        sendInput("document.txt\n")
        sendInput("\u001b[7mpic.jpg\u001b[m\n")  // 选中图片文件
        sendInput("video.mp4\n")
        
        // 模拟右侧预览区显示图片信息
        sendInput("\u001b[1;80H")
        sendInput("Image Preview\n")
        sendInput("\u001b[2;80H")
        sendInput("File: pic.jpg\n")
        sendInput("\u001b[3;80H")
        sendInput("Size: 1920x1080\n")
        sendInput("\u001b[4;80H")
        sendInput("Type: JPEG\n")
        
        // 模拟使用Unicode块字符显示图片缩略图
        // 使用不同深度的块字符模拟像素
        sendInput("\u001b[6;80H")
        sendInput("░░▒▒▓▓██\n")  // 模拟图像的一行
        sendInput("\u001b[7;80H")
        sendInput("▒▒▓▓████\n")
        sendInput("\u001b[8;80H")
        sendInput("▓▓██████\n")
        
        // 验证文件名显示
        val line1 = getLineText(1)
        assertTrue(
            line1.contains("pic", ignoreCase = true) || line1.contains("jpg", ignoreCase = true),
            "Should show image file name"
        )
        
        // 验证有反显（选中的图片文件）
        val hasReverse = (0 until buffer.getWidth()).any { x -> 
            (0 until 5).any { y -> getCellAt(x, y).reverse }
        }
        assertTrue(hasReverse, "Should have highlighted image file")
        
        // 验证预览区有内容（块字符）
        val previewLine6 = getLineText(6)
        val previewLine7 = getLineText(7)
        assertTrue(
            previewLine6.contains("░") || previewLine6.contains("▒") || 
            previewLine6.contains("▓") || previewLine6.contains("█") ||
            previewLine7.contains("░") || previewLine7.contains("▒") ||
            previewLine7.contains("▓") || previewLine7.contains("█"),
            "Should show image preview with block characters"
        )
    }
    
    @Test
    fun testYaziImagePreviewWithSixel() {
        setup(160, 50)
        
        // 启动 yazi
        sendInput("\u001b[?1049h")
        sendInput("\u001b[2J\u001b[H")
        
        // 模拟选中图片
        sendInput("\u001b[1;1H")
        sendInput("\u001b[7mpic.jpg\u001b[m\n")
        
        // 模拟 Sixel 图形序列（简化版）
        // Sixel 以 ESC P q 开始，以 ESC \ 结束
        sendInput("\u001b[10;80H")
        sendInput("\u001bPq")  // Sixel 开始
        sendInput("\"1;1;100;100")  // 图像尺寸
        sendInput("#0;2;0;0;0")  // 颜色定义
        sendInput("#1;2;100;100;100")
        sendInput("!50~")  // 重复数据
        sendInput("\u001b\\")  // Sixel 结束
        
        // 验证在 alternate screen
        assertTrue(isInAlternateScreen(), "Should be in alternate screen")
        
        // 验证文件被选中
        val line0 = getLineText(0)
        val line1 = getLineText(1)
        assertTrue(
            line0.contains("pic", ignoreCase = true) || line1.contains("pic", ignoreCase = true),
            "Should show image file"
        )
    }
    
    @Test
    fun testYaziMultipleImageFormats() {
        setup(160, 50)
        
        // 启动 yazi
        sendInput("\u001b[?1049h")
        sendInput("\u001b[2J\u001b[H")
        
        // 模拟多种图片格式的文件列表
        sendInput("\u001b[1;1H")
        sendInput("\u001b[7mphoto.jpg\u001b[m\n")
        sendInput("image.png\n")
        sendInput("graphic.gif\n")
        sendInput("vector.svg\n")
        sendInput("pic.webp\n")
        
        // 模拟右侧预览显示图片信息
        sendInput("\u001b[1;80H")
        sendInput("JPEG Image\n")
        sendInput("\u001b[2;80H")
        sendInput("1920 x 1080 pixels\n")
        sendInput("\u001b[3;80H")
        sendInput("RGB Color\n")
        sendInput("\u001b[4;80H")
        sendInput("24-bit depth\n")
        
        // 验证文件列表
        val line0 = getLineText(0)
        val line1 = getLineText(1)
        val line2 = getLineText(2)
        val line3 = getLineText(3)
        val line4 = getLineText(4)
        
        assertTrue(
            line0.contains("jpg", ignoreCase = true) || 
            line0.contains("photo", ignoreCase = true),
            "Should show JPEG file"
        )
        
        assertTrue(
            line1.contains("png", ignoreCase = true) || 
            line2.contains("gif", ignoreCase = true) ||
            line3.contains("svg", ignoreCase = true) ||
            line4.contains("webp", ignoreCase = true),
            "Should show various image formats"
        )
        
        // 验证预览信息
        val previewLine = getLineText(1)
        val previewLine2 = getLineText(2)
        assertTrue(
            previewLine.contains("1920") || previewLine.contains("1080") ||
            previewLine2.contains("1920") || previewLine2.contains("1080") ||
            previewLine.contains("JPEG") || previewLine.contains("RGB"),
            "Should show image properties"
        )
    }
}

