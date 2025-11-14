package cn.hjhw.ssh.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 文件系统接口，用于跨平台文件读取
 */
expect class FileSystem {
    fun readText(path: String): String?

    fun exists(path: String): Boolean
}

/**
 * SSH 配置文件解析器
 * 解析标准的 OpenSSH 配置文件格式
 */
class SshConfigParser(
    private val fileSystem: FileSystem = createFileSystem(),
) {
    /**
     * 解析 SSH 配置文件
     * @param configPath 配置文件路径
     * @return 解析后的 SSH 配置
     */
    suspend fun parse(configPath: String): SshConfig =
        withContext(Dispatchers.IO) {
            if (!fileSystem.exists(configPath)) {
                return@withContext SshConfig(emptyList())
            }

            val content = fileSystem.readText(configPath) ?: ""
            parseContent(content, configPath)
        }

    /**
     * 解析配置内容
     */
    fun parseContent(
        content: String,
        basePath: String = "",
    ): SshConfig {
        val hosts = mutableListOf<SshHostConfig>()
        val lines = content.lines()
        var currentHost: String? = null
        val currentConfig = mutableMapOf<String, String>()

        fun flushCurrentHost() {
            if (currentHost != null) {
                hosts.add(createHostConfig(currentHost!!, currentConfig, basePath))
                currentConfig.clear()
            }
        }

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            // 跳过空行和注释
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue
            }

            val parts = trimmed.split(Regex("\\s+"), limit = 2)
            if (parts.isEmpty()) continue

            val key = parts[0]
            val value = parts.getOrNull(1)?.trim() ?: ""

            // 处理 Host 指令
            if (key.equals("Host", ignoreCase = true)) {
                flushCurrentHost()
                currentHost = value
            } else if (currentHost != null) {
                // 处理其他配置项（大小写不敏感）
                when (key.lowercase()) {
                    "hostname" -> currentConfig["hostName"] = value
                    "user" -> currentConfig["user"] = value
                    "port" -> {
                        val port = value.toIntOrNull()
                        if (port != null) {
                            currentConfig["port"] = port.toString()
                        }
                    }
                    "identityfile" -> {
                        val expanded = expandPath(value, basePath)
                        currentConfig["identityFile"] = expanded
                    }
                    "proxycommand" -> currentConfig["proxyCommand"] = value
                    "forwardagent" -> {
                        val boolValue =
                            when (value.lowercase()) {
                                "yes", "true", "1" -> "true"
                                "no", "false", "0" -> "false"
                                else -> null
                            }
                        if (boolValue != null) {
                            currentConfig["forwardAgent"] = boolValue
                        }
                    }
                    "stricthostkeychecking" -> currentConfig["strictHostKeyChecking"] = value
                    // 忽略未知的配置项
                }
            }
        }

        // 处理最后一个 host
        flushCurrentHost()

        return SshConfig(hosts)
    }

    /**
     * 创建主机配置对象
     */
    private fun createHostConfig(
        host: String,
        config: Map<String, String>,
        basePath: String,
    ): SshHostConfig {
        val identityFile = config["identityFile"]?.let { expandPath(it, basePath) }

        return SshHostConfig(
            host = host,
            hostName = config["hostName"]?.takeIf { it.isNotEmpty() },
            user = config["user"]?.takeIf { it.isNotEmpty() },
            port = config["port"]?.toIntOrNull(),
            identityFile = identityFile,
            proxyCommand = config["proxyCommand"]?.takeIf { it.isNotEmpty() },
            forwardAgent = config["forwardAgent"]?.toBooleanStrictOrNull(),
            strictHostKeyChecking = config["strictHostKeyChecking"]?.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * 展开路径（处理 ~ 和相对路径）
     */
    private fun expandPath(
        path: String,
        basePath: String,
    ): String {
        return expandPathImpl(path, basePath)
    }

    companion object {
        /**
         * 解析默认路径的 SSH 配置文件
         */
        suspend fun parse(): SshConfig =
            withContext(Dispatchers.IO) {
                val parser = SshConfigParser()
                val defaultPath = getDefaultSshConfigPath()
                parser.parse(defaultPath)
            }
    }
}

/**
 * 创建平台特定的文件系统实例
 */
expect fun createFileSystem(): FileSystem

/**
 * 展开路径（处理 ~ 和相对路径）
 * 平台特定实现
 */
expect fun expandPathImpl(
    path: String,
    basePath: String,
): String

/**
 * 获取默认的 SSH 配置文件路径
 */
expect fun getDefaultSshConfigPath(): String
