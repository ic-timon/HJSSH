package cn.hjhw.ssh.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Data

/**
 * JVM 平台的文本渲染器实现
 * 使用 Skia 直接绘制文本，支持嵌入字体和 Emoji
 */
actual object TextRenderer {
    // 嵌入的字体
    private var nerdFontRegular: Typeface? = null
    private var nerdFontBold: Typeface? = null
    private var nerdFontItalic: Typeface? = null
    private var emojiFont: Typeface? = null
    
    // 后备字体（如果嵌入字体加载失败）
    private var cachedTypeface: Typeface? = null
    private var cachedBoldTypeface: Typeface? = null
    private var cachedCjkTypeface: Typeface? = null
    private val fontMgr = FontMgr.default
    
    // 是否已初始化
    private var initialized = false
    
    /**
     * 初始化字体 - 从资源加载嵌入的字体
     */
    fun initialize() {
        if (initialized) return
        
        try {
            println("[TextRenderer] 🔤 Initializing fonts...")
            
            // 加载 JetBrains Mono Nerd Font
            loadFont("fonts/JetBrainsMonoNerdFont-Regular.ttf")?.let {
                nerdFontRegular = it
                println("[TextRenderer] ✅ Loaded: JetBrains Mono Nerd Font Regular")
            }
            
            loadFont("fonts/JetBrainsMonoNerdFont-Bold.ttf")?.let {
                nerdFontBold = it
                println("[TextRenderer] ✅ Loaded: JetBrains Mono Nerd Font Bold")
            }
            
            loadFont("fonts/JetBrainsMonoNerdFont-Italic.ttf")?.let {
                nerdFontItalic = it
                println("[TextRenderer] ✅ Loaded: JetBrains Mono Nerd Font Italic")
            }
            
            // 加载 Noto Color Emoji
            loadFont("fonts/NotoColorEmoji.ttf")?.let {
                emojiFont = it
                println("[TextRenderer] ✅ Loaded: Noto Color Emoji")
            }
            
            initialized = true
            println("[TextRenderer] 🎉 Font initialization complete!")
        } catch (e: Exception) {
            println("[TextRenderer] ⚠️ Failed to initialize fonts: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 从资源加载字体文件
     */
    private fun loadFont(resourcePath: String): Typeface? {
        return try {
            val inputStream = this::class.java.classLoader.getResourceAsStream(resourcePath)
                ?: throw IllegalArgumentException("Font resource not found: $resourcePath")
            
            val fontData = inputStream.readBytes()
            inputStream.close()
            
            // 使用 FontMgr 从字节数组创建字体
            fontMgr.makeFromData(Data.makeFromBytes(fontData))
        } catch (e: Exception) {
            println("[TextRenderer] ⚠️ Failed to load font: $resourcePath - ${e.message}")
            null
        }
    }
    
    /**
     * 判断字符是否为 Emoji
     */
    private fun isEmoji(char: Char): Boolean {
        val code = char.code
        return when {
            // Emoji 主要区域
            code in 0x1F300..0x1F9FF -> true
            code in 0x1FA00..0x1FAFF -> true
            // 杂项符号和象形文字
            code in 0x2600..0x26FF -> true
            code in 0x2700..0x27BF -> true
            // Dingbats
            code in 0x2700..0x27BF -> true
            // 变体选择器
            code in 0xFE00..0xFE0F -> true
            // 麻将牌、扑克牌等
            code in 0x1F000..0x1F0FF -> true
            // 封闭字符补充
            code in 0x1F100..0x1F64F -> true
            // 交通和地图符号
            code in 0x1F680..0x1F6FF -> true
            // 补充符号和象形文字
            code in 0x1F700..0x1F77F -> true
            // 表情符号扩展
            code in 0x1F900..0x1FAFF -> true
            // 私有使用区（Nerd Fonts 图标）
            code in 0xE000..0xF8FF -> true
            code in 0xF0000..0xFFFFD -> true
            code in 0x100000..0x10FFFD -> true
            else -> false
        }
    }
    
    /**
     * 判断是否是 CJK 字符
     */
    private fun isCjkChar(char: Char): Boolean {
        val code = char.code
        return when {
            code in 0x4E00..0x9FFF -> true  // CJK Unified Ideographs
            code in 0x3400..0x4DBF -> true  // CJK Extension A
            code in 0x20000..0x2A6DF -> true  // CJK Extension B
            code in 0x2A700..0x2B73F -> true  // CJK Extension C
            code in 0x2B740..0x2B81F -> true  // CJK Extension D
            code in 0x2B820..0x2CEAF -> true  // CJK Extension E
            code in 0x3000..0x303F -> true  // CJK Symbols and Punctuation
            code in 0xFF00..0xFFEF -> true  // Halfwidth and Fullwidth Forms
            else -> false
        }
    }
    
    /**
     * 获取适合字符的字体
     */
    private fun getTypefaceForChar(char: Char, bold: Boolean): Typeface {
        // 懒初始化
        if (!initialized) {
            initialize()
        }
        
        // 优先级：Emoji > Nerd Font (嵌入) > CJK > 系统字体
        return when {
            // Emoji 字符 - 使用 Noto Color Emoji
            isEmoji(char) && emojiFont != null -> {
                emojiFont!!
            }
            
            // 普通字符 - 优先使用 Nerd Font
            !isCjkChar(char) -> {
                when {
                    bold && nerdFontBold != null -> nerdFontBold!!
                    nerdFontRegular != null -> nerdFontRegular!!
                    else -> getFallbackTypeface(bold, isCjk = false)
                }
            }
            
            // CJK 字符 - 使用系统 CJK 字体
            else -> {
                getFallbackTypeface(bold, isCjk = true)
            }
        }
    }
    
    /**
     * 获取后备字体（如果嵌入字体加载失败）
     */
    private fun getFallbackTypeface(bold: Boolean, isCjk: Boolean): Typeface {
        return when {
            isCjk -> {
                if (cachedCjkTypeface == null) {
                    cachedCjkTypeface = fontMgr.matchFamilyStyle("Microsoft YaHei", FontStyle.NORMAL)
                        ?: fontMgr.matchFamilyStyle("SimHei", FontStyle.NORMAL)
                        ?: fontMgr.matchFamilyStyle("Noto Sans CJK SC", FontStyle.NORMAL)
                        ?: fontMgr.matchFamilyStyle("Source Han Sans", FontStyle.NORMAL)
                        ?: fontMgr.matchFamilyStyle("monospace", FontStyle.NORMAL)
                        ?: throw IllegalStateException("No font available")
                }
                cachedCjkTypeface!!
            }
            bold -> {
                if (cachedBoldTypeface == null) {
                    cachedBoldTypeface = fontMgr.matchFamilyStyle("DejaVu Sans Mono", FontStyle.BOLD)
                        ?: fontMgr.matchFamilyStyle("JetBrains Mono", FontStyle.BOLD)
                        ?: fontMgr.matchFamilyStyle("Courier New", FontStyle.BOLD)
                        ?: fontMgr.matchFamilyStyle("monospace", FontStyle.BOLD)
                        ?: throw IllegalStateException("No bold font available")
                }
                cachedBoldTypeface!!
            }
            else -> {
                if (cachedTypeface == null) {
                    cachedTypeface = fontMgr.matchFamilyStyle("DejaVu Sans Mono", FontStyle.NORMAL)
                        ?: fontMgr.matchFamilyStyle("JetBrains Mono", FontStyle.NORMAL)
                        ?: fontMgr.matchFamilyStyle("Courier New", FontStyle.NORMAL)
                        ?: fontMgr.matchFamilyStyle("monospace", FontStyle.NORMAL)
                        ?: throw IllegalStateException("No monospace font available")
                }
                cachedTypeface!!
            }
        }
    }
    
    actual fun drawText(
        scope: DrawScope,
        text: String,
        x: Float,
        y: Float,
        color: Color,
        fontSize: Float,
        bold: Boolean,
    ) {
        scope.drawIntoCanvas { canvas ->
            try {
                val nativeCanvas = canvas.nativeCanvas
                
                // 创建画笔
                val paint = Paint().apply {
                    val r = (color.red * 255).toInt().coerceIn(0, 255)
                    val g = (color.green * 255).toInt().coerceIn(0, 255)
                    val b = (color.blue * 255).toInt().coerceIn(0, 255)
                    val a = (color.alpha * 255).toInt().coerceIn(0, 255)
                    this.color = org.jetbrains.skia.Color.makeRGB(r, g, b)
                    alpha = a
                    isAntiAlias = true
                }
                
                // 由于使用了混合字体，我们不能逐字符渲染
                // 而是应该一次性渲染整个字符串，使用主字体
                val firstChar = text.firstOrNull()
                val typeface = if (firstChar != null) {
                    getTypefaceForChar(firstChar, bold)
                } else {
                    nerdFontRegular ?: getFallbackTypeface(bold, isCjk = false)
                }
                
                val font = Font(typeface, fontSize)
                nativeCanvas.drawString(text, x, y, font, paint)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

