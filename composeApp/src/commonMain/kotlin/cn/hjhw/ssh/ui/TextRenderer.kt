package cn.hjhw.ssh.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * 文本渲染器接口（跨平台）
 */
expect object TextRenderer {
    fun drawText(
        scope: DrawScope,
        text: String,
        x: Float,
        y: Float,
        color: Color,
        fontSize: Float,
        bold: Boolean = false,
    )
}

