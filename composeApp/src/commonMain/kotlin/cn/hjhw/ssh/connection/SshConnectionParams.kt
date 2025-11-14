package cn.hjhw.ssh.connection

/**
 * SSH 认证方法
 */
sealed interface SshAuthMethod {
    /**
     * 密码认证
     */
    data class Password(val password: String) : SshAuthMethod

    /**
     * 密钥文件认证
     */
    data class KeyFile(val keyPath: String, val passphrase: String? = null) : SshAuthMethod

    /**
     * 无密码认证（使用默认密钥）
     */
    object None : SshAuthMethod
}

/**
 * SSH 连接参数
 */
data class SshConnectionParams(
    val host: String,
    val port: Int = 22,
    val user: String,
    val authMethod: SshAuthMethod = SshAuthMethod.None,
    val keepAliveInterval: Long = 30000, // 毫秒
    val connectTimeout: Long = 10000, // 毫秒
)



