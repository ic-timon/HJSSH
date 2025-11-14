# Emoji 字体集成指南

## 🎯 问题描述

终端中的 emoji 图标（如 Yazi 文件管理器中的文件/文件夹图标）显示为方框或问号，需要集成 emoji 字体支持。

## 📦 推荐字体

### 1. Noto Color Emoji (推荐用于 Emoji)

**优势**:
- ✅ Google 官方维护，更新及时
- ✅ 完整的 Unicode Emoji 覆盖
- ✅ 彩色 emoji 支持
- ✅ 开源免费 (SIL OFL)
- ✅ 跨平台兼容性好

**下载地址**:
- GitHub Release: https://github.com/googlefonts/noto-emoji/releases
- 直接下载: https://github.com/googlefonts/noto-emoji/raw/main/fonts/NotoColorEmoji.ttf

**文件大小**: ~10.5 MB

### 2. Nerd Fonts (推荐用于终端图标)

**优势**:
- ✅ 专为终端设计
- ✅ 包含 Devicons, Font Awesome, Powerline 等图标
- ✅ Yazi 文件管理器首选
- ✅ 支持文件类型图标

**推荐字体**: JetBrains Mono Nerd Font

**下载地址**:
- 官网: https://www.nerdfonts.com/font-downloads
- GitHub: https://github.com/ryanoasis/nerd-fonts/releases
- 直接下载 JetBrains Mono: https://github.com/ryanoasis/nerd-fonts/releases/download/v3.1.1/JetBrainsMono.zip

**文件大小**: ~100 MB (完整包，包含多种变体)

### 3. Twemoji (可选)

**优势**:
- ✅ Twitter 风格的 emoji
- ✅ 视觉风格统一
- ✅ 开源 (CC-BY 4.0)

**下载地址**:
- GitHub: https://github.com/twitter/twemoji
- 字体版本: https://github.com/13rac1/twemoji-color-font

## 🔧 集成步骤

### 步骤 1: 下载字体文件

```bash
# 创建字体资源目录
mkdir -p composeApp/src/commonMain/resources/fonts

# 下载 Noto Color Emoji
curl -L -o composeApp/src/commonMain/resources/fonts/NotoColorEmoji.ttf \
  https://github.com/googlefonts/noto-emoji/raw/main/fonts/NotoColorEmoji.ttf

# 下载 JetBrains Mono Nerd Font（需要解压）
# 访问 https://www.nerdfonts.com/font-downloads 手动下载
```

**推荐文件结构**:
```
composeApp/src/commonMain/resources/fonts/
├── NotoColorEmoji.ttf              # Emoji 字体
├── JetBrainsMonoNerdFont-Regular.ttf   # 主字体
├── JetBrainsMonoNerdFont-Bold.ttf      # 粗体
└── JetBrainsMonoNerdFont-Italic.ttf    # 斜体
```

### 步骤 2: 在 JVM 端加载字体

创建字体管理类:

```kotlin
// composeApp/src/jvmMain/kotlin/cn/hjhw/ssh/ui/FontManager.kt
package cn.hjhw.ssh.ui

import java.awt.Font
import java.awt.FontFormatException
import java.io.IOException

object FontManager {
    private val fonts = mutableMapOf<String, Font>()
    
    /**
     * 加载嵌入的字体
     */
    fun loadFonts() {
        try {
            // 加载主字体
            loadFont("JetBrainsMonoNerdFont-Regular.ttf", "JetBrainsMono-Regular")
            loadFont("JetBrainsMonoNerdFont-Bold.ttf", "JetBrainsMono-Bold")
            loadFont("JetBrainsMonoNerdFont-Italic.ttf", "JetBrainsMono-Italic")
            
            // 加载 Emoji 字体
            loadFont("NotoColorEmoji.ttf", "NotoColorEmoji")
            
            println("[FontManager] ✅ Fonts loaded successfully")
        } catch (e: Exception) {
            println("[FontManager] ⚠️ Failed to load fonts: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun loadFont(fileName: String, fontName: String) {
        try {
            val inputStream = this::class.java.classLoader
                .getResourceAsStream("fonts/$fileName")
                ?: throw IOException("Font file not found: $fileName")
            
            val font = Font.createFont(Font.TRUETYPE_FONT, inputStream)
            fonts[fontName] = font
            
            println("[FontManager] Loaded font: $fontName")
        } catch (e: FontFormatException) {
            println("[FontManager] ⚠️ Invalid font format: $fileName")
        } catch (e: IOException) {
            println("[FontManager] ⚠️ Failed to load font: $fileName")
        }
    }
    
    /**
     * 获取指定字体
     */
    fun getFont(name: String, size: Float): Font? {
        return fonts[name]?.deriveFont(size)
    }
    
    /**
     * 获取主字体（带 fallback 到 Emoji）
     */
    fun getMainFont(size: Float): Font {
        return getFont("JetBrainsMono-Regular", size) 
            ?: Font("Monospaced", Font.PLAIN, size.toInt())
    }
    
    /**
     * 获取 Emoji 字体
     */
    fun getEmojiFont(size: Float): Font? {
        return getFont("NotoColorEmoji", size)
    }
    
    /**
     * 创建带 Emoji fallback 的字体
     */
    fun createFontWithEmojiFallback(size: Float): java.awt.font.TextAttribute {
        // TODO: 实现字体 fallback 链
        return java.awt.font.TextAttribute.FAMILY to "JetBrains Mono"
    }
}
```

### 步骤 3: 在 TextRenderer 中使用字体

修改 `TextRenderer.jvm.kt`:

```kotlin
// composeApp/src/jvmMain/kotlin/cn/hjhw/ssh/ui/TextRenderer.jvm.kt
package cn.hjhw.ssh.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Typeface
import java.io.File

actual object TextRenderer {
    private var mainTypeface: Typeface? = null
    private var emojiTypeface: Typeface? = null
    private var font: Font? = null
    
    /**
     * 初始化字体
     */
    fun initialize() {
        try {
            // 从资源加载主字体
            val mainFontStream = this::class.java.classLoader
                .getResourceAsStream("fonts/JetBrainsMonoNerdFont-Regular.ttf")
            if (mainFontStream != null) {
                val mainFontData = mainFontStream.readBytes()
                mainTypeface = Typeface.makeFromData(org.jetbrains.skia.Data.makeFromBytes(mainFontData))
                println("[TextRenderer] ✅ Loaded main font: JetBrains Mono Nerd Font")
            }
            
            // 从资源加载 Emoji 字体
            val emojiFontStream = this::class.java.classLoader
                .getResourceAsStream("fonts/NotoColorEmoji.ttf")
            if (emojiFontStream != null) {
                val emojiFontData = emojiFontStream.readBytes()
                emojiTypeface = Typeface.makeFromData(org.jetbrains.skia.Data.makeFromBytes(emojiFontData))
                println("[TextRenderer] ✅ Loaded emoji font: Noto Color Emoji")
            }
            
            // 创建默认字体（如果主字体加载失败）
            if (mainTypeface == null) {
                mainTypeface = Typeface.makeDefault()
                println("[TextRenderer] ⚠️ Using default system font")
            }
            
            font = Font(mainTypeface, 14f)
        } catch (e: Exception) {
            println("[TextRenderer] ⚠️ Failed to initialize fonts: ${e.message}")
            mainTypeface = Typeface.makeDefault()
            font = Font(mainTypeface, 14f)
        }
    }
    
    /**
     * 判断字符是否为 Emoji
     */
    private fun isEmoji(char: Char): Boolean {
        val codePoint = char.code
        return when {
            codePoint in 0x1F300..0x1F9FF -> true  // Emoji 主要区域
            codePoint in 0x2600..0x26FF -> true    // 杂项符号
            codePoint in 0x2700..0x27BF -> true    // Dingbats
            codePoint in 0xFE00..0xFE0F -> true    // 变体选择器
            codePoint in 0x1F000..0x1F02F -> true  // 麻将牌
            codePoint in 0x1F0A0..0x1F0FF -> true  // 扑克牌
            codePoint in 0x1F100..0x1F64F -> true  // 封闭字符
            codePoint in 0x1F680..0x1F6FF -> true  // 交通和地图符号
            codePoint in 0x1F900..0x1F9FF -> true  // 补充符号和象形文字
            codePoint in 0xE000..0xF8FF -> true    // 私有使用区（Nerd Fonts）
            else -> false
        }
    }
    
    actual fun drawText(
        scope: DrawScope,
        text: String,
        x: Float,
        y: Float,
        color: Color,
        fontSize: Float
    ) {
        scope.drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            
            val paint = Paint().apply {
                this.color = org.jetbrains.skia.Color.makeRGB(
                    (color.red * 255).toInt(),
                    (color.green * 255).toInt(),
                    (color.blue * 255).toInt()
                )
                isAntiAlias = true
            }
            
            // 逐字符渲染，为 emoji 使用专门的字体
            var currentX = x
            for (char in text) {
                val typeface = if (isEmoji(char) && emojiTypeface != null) {
                    emojiTypeface
                } else {
                    mainTypeface
                }
                
                val charFont = Font(typeface, fontSize)
                val charWidth = charFont.measureText(char.toString())
                
                nativeCanvas.drawString(
                    char.toString(),
                    currentX,
                    y,
                    charFont,
                    paint
                )
                
                currentX += charWidth
            }
        }
    }
}
```

### 步骤 4: 在应用启动时初始化字体

修改主应用入口:

```kotlin
// composeApp/src/jvmMain/kotlin/main.kt
import cn.hjhw.ssh.ui.TextRenderer

fun main() = application {
    // 初始化字体
    TextRenderer.initialize()
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "HJSSH",
    ) {
        App()
    }
}
```

## 📝 验证字体是否加载成功

添加字体测试工具:

```kotlin
// composeApp/src/jvmTest/kotlin/cn/hjhw/ssh/ui/FontTest.kt
package cn.hjhw.ssh.ui

import org.junit.Test
import kotlin.test.assertNotNull

class FontTest {
    @Test
    fun `test font loading`() {
        TextRenderer.initialize()
        
        // 验证主字体
        assertNotNull(TextRenderer.mainTypeface, "Main font should be loaded")
        
        // 验证 Emoji 字体
        assertNotNull(TextRenderer.emojiTypeface, "Emoji font should be loaded")
        
        println("✅ All fonts loaded successfully")
    }
    
    @Test
    fun `test emoji detection`() {
        val emojiChars = listOf('😀', '📁', '📄', '🎨', '🚀')
        val normalChars = listOf('A', 'a', '中', '1')
        
        // TODO: 添加 isEmoji 测试
    }
}
```

## 🎨 字体配置建议

### 推荐组合 1: 最佳效果
```
主字体: JetBrains Mono Nerd Font (文件图标)
Emoji: Noto Color Emoji (彩色 emoji)
```

### 推荐组合 2: 简化版
```
主字体: 系统 Monospace
Emoji: Noto Color Emoji
```

## 🔍 常见问题

### Q1: 字体文件太大怎么办？

**方案 1**: 使用字体子集化工具
```bash
# 使用 pyftsubset 创建字体子集
pip install fonttools
pyftsubset NotoColorEmoji.ttf \
  --output-file=NotoColorEmoji-subset.ttf \
  --unicodes=U+1F300-1F9FF,U+2600-26FF
```

**方案 2**: 仅使用必要的字体变体
- 只包含 Regular 和 Bold
- 不包含 Italic（终端很少用）

### Q2: Emoji 显示为黑白怎么办？

Noto Color Emoji 是彩色字体（COLR/CPAL 格式），Skia 应该能正确渲染。如果显示为黑白：
1. 检查 Skia 版本是否支持彩色字体
2. 尝试使用 SVG 格式的 emoji 字体
3. 考虑使用 Twemoji 的 SVG 版本

### Q3: 字体未找到资源？

确保 `build.gradle.kts` 包含资源配置:

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            resources.srcDirs("src/commonMain/resources")
        }
    }
}
```

## 📊 字体文件大小对比

| 字体 | 文件大小 | 覆盖范围 | 推荐用途 |
|------|---------|---------|---------|
| Noto Color Emoji | ~10 MB | 完整 Unicode Emoji | Emoji 显示 |
| JetBrains Mono Nerd Font (Regular) | ~3 MB | 编程图标 + ASCII | 主字体 |
| Twemoji Color Font | ~8 MB | 完整 Emoji | 备选 Emoji |
| Symbols Nerd Font | ~2 MB | 仅图标 | 最小化方案 |

## 🚀 性能优化建议

1. **懒加载**: 仅在需要时加载 Emoji 字体
2. **缓存**: 缓存已渲染的字形
3. **子集化**: 仅包含常用的 emoji 和图标
4. **异步加载**: 在后台线程加载字体

## 📚 参考资料

- [Noto Emoji GitHub](https://github.com/googlefonts/noto-emoji)
- [Nerd Fonts 官网](https://www.nerdfonts.com/)
- [Skia Typography](https://skia.org/docs/user/modules/skparagraph/)
- [Unicode Emoji 标准](https://unicode.org/emoji/charts/full-emoji-list.html)

---

**最后更新**: 2025-11-14
**状态**: 待实现
**优先级**: 中等

