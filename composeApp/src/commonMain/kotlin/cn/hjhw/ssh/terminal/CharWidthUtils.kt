package cn.hjhw.ssh.terminal

/**
 * 字符宽度工具函数
 * 用于判断字符在终端中占用的宽度（1或2个单元格）
 */

/**
 * 判断字符是否是宽字符（占2个单元格）
 * 主要包括：CJK字符（中日韩）、全角符号等
 */
fun isWideChar(char: Char): Boolean {
    val code = char.code
    return when {
        // CJK Unified Ideographs（中日韩统一表意文字）
        code in 0x4E00..0x9FFF -> true
        
        // CJK Extension A
        code in 0x3400..0x4DBF -> true
        
        // CJK Extension B, C, D, E, F
        code in 0x20000..0x2CEAF -> true
        
        // CJK Symbols and Punctuation（中日韩符号和标点）
        code in 0x3000..0x303F -> true
        
        // Halfwidth and Fullwidth Forms（半角和全角字符）
        code in 0xFF00..0xFFEF -> true
        
        // Hiragana（平假名）
        code in 0x3040..0x309F -> true
        
        // Katakana（片假名）
        code in 0x30A0..0x30FF -> true
        
        // Hangul Syllables（韩文音节）
        code in 0xAC00..0xD7AF -> true
        
        // Emoji 和其他宽字符
        code in 0x1F300..0x1F9FF -> true  // Emoji
        code in 0x2600..0x26FF -> true    // Miscellaneous Symbols
        code in 0x2700..0x27BF -> true    // Dingbats
        
        else -> false
    }
}

/**
 * 获取字符在终端中占用的宽度（单元格数）
 */
fun getCharWidth(char: Char): Int {
    return if (isWideChar(char)) 2 else 1
}


