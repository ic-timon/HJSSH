package cn.hjhw.ssh.config

import java.io.File

/**
 * JVM 平台的文件系统实现
 */
actual class FileSystem {
    actual fun readText(path: String): String? {
        return try {
            File(path).readText()
        } catch (e: Exception) {
            null
        }
    }

    actual fun exists(path: String): Boolean {
        return File(path).exists()
    }
}

actual fun createFileSystem(): FileSystem = FileSystem()

actual fun getDefaultSshConfigPath(): String {
    val homeDir = System.getProperty("user.home") ?: ""
    return if (homeDir.isNotEmpty()) {
        "$homeDir/.ssh/config"
    } else {
        ".ssh/config"
    }
}

actual fun expandPathImpl(path: String, basePath: String): String {
    if (path.isEmpty()) return path

    // 处理 ~ 展开
    if (path.startsWith("~/")) {
        val homeDir = System.getProperty("user.home") ?: return path
        return path.replace("~/", "$homeDir/")
    }
    if (path == "~") {
        return System.getProperty("user.home") ?: path
    }

    // 如果是绝对路径，直接返回
    if (path.startsWith("/") || (path.length >= 2 && path[1] == ':')) {
        return path
    }

    // 相对路径：相对于配置文件所在目录
    if (basePath.isNotEmpty()) {
        val baseDir = File(basePath).parent ?: ""
        if (baseDir.isNotEmpty()) {
            return File(baseDir, path).absolutePath
        }
    }

    return path
}

