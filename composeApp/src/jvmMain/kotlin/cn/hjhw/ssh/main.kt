package cn.hjhw.ssh

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import cn.hjhw.ssh.ui.TextRenderer

fun main() =
    application {
        // 初始化嵌入字体（JetBrains Mono Nerd Font 和 Noto Color Emoji）
        TextRenderer.initialize()

        Window(
            onCloseRequest = ::exitApplication,
            title = "HJSSH - SSH Terminal Emulator",
            icon = painterResource("icon.png"),
        ) {
            App()
        }
    }
