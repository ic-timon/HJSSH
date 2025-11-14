package cn.hjhw.ssh.session

import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JVM 平台的会话存储实现
 */
actual class FileSessionStorage actual constructor() : SessionStorage {
    private val configFile =
        File(
            System.getProperty("user.home"),
            ".hjssh/sessions.json",
        )

    init {
        // 确保目录存在
        configFile.parentFile?.mkdirs()
    }

    override fun saveSessions(configs: List<SessionConfig>) {
        try {
            val json =
                Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                }
            val jsonString = json.encodeToString(configs)
            configFile.writeText(jsonString)
        } catch (e: Exception) {
            // 保存失败，记录错误但不抛出异常
            e.printStackTrace()
        }
    }

    override fun loadSessions(): List<SessionConfig> {
        return try {
            if (!configFile.exists()) {
                return emptyList()
            }

            val json =
                Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                }
            val jsonString = configFile.readText()
            json.decodeFromString<List<SessionConfig>>(jsonString)
        } catch (e: Exception) {
            // 加载失败，返回空列表
            e.printStackTrace()
            emptyList()
        }
    }
}
