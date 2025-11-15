package cn.hjhw.ssh.session

import cn.hjhw.ssh.connection.SshAuthMethod
import kotlinx.serialization.Serializable

/**
 * 会话配置
 * 用于保存和加载 SSH 会话配置
 */
@Serializable
data class SessionConfig(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val user: String,
    val authMethod: SerializedAuthMethod,
    val keepAliveInterval: Long = 30000,
    val connectTimeout: Long = 10000,
    val createdAt: Long = System.currentTimeMillis(),
    val lastConnectedAt: Long? = null,
) {
    /**
     * 序列化的认证方法
     */
    @Serializable
    sealed class SerializedAuthMethod {
        @Serializable
        data class Password(val password: String) : SerializedAuthMethod()

        @Serializable
        data class KeyFile(val keyPath: String, val passphrase: String? = null) : SerializedAuthMethod()

        @Serializable
        object None : SerializedAuthMethod()
    }

    /**
     * 转换为 SshAuthMethod
     */
    fun toSshAuthMethod(): SshAuthMethod {
        return when (val auth = authMethod) {
            is SerializedAuthMethod.Password -> cn.hjhw.ssh.connection.SshAuthMethod.Password(auth.password)
            is SerializedAuthMethod.KeyFile -> cn.hjhw.ssh.connection.SshAuthMethod.KeyFile(auth.keyPath, auth.passphrase)
            is SerializedAuthMethod.None -> cn.hjhw.ssh.connection.SshAuthMethod.None
        }
    }

    companion object {
        /**
         * 从 SshAuthMethod 创建 SerializedAuthMethod
         */
        fun fromSshAuthMethod(auth: cn.hjhw.ssh.connection.SshAuthMethod): SerializedAuthMethod {
            return when (auth) {
                is cn.hjhw.ssh.connection.SshAuthMethod.Password -> SerializedAuthMethod.Password(auth.password)
                is cn.hjhw.ssh.connection.SshAuthMethod.KeyFile -> SerializedAuthMethod.KeyFile(auth.keyPath, auth.passphrase)
                is cn.hjhw.ssh.connection.SshAuthMethod.None -> SerializedAuthMethod.None
            }
        }
    }
}

