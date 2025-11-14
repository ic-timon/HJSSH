package cn.hjhw.ssh.config

/**
 * SSH 主机配置数据类
 */
data class SshHostConfig(
    val host: String,
    val hostName: String? = null,
    val user: String? = null,
    val port: Int? = null,
    val identityFile: String? = null,
    val proxyCommand: String? = null,
    val forwardAgent: Boolean? = null,
    val strictHostKeyChecking: String? = null,
) {
    /**
     * 合并两个配置，当前配置优先
     */
    fun mergeWith(other: SshHostConfig): SshHostConfig {
        return SshHostConfig(
            host = host,
            hostName = hostName ?: other.hostName,
            user = user ?: other.user,
            port = port ?: other.port,
            identityFile = identityFile ?: other.identityFile,
            proxyCommand = proxyCommand ?: other.proxyCommand,
            forwardAgent = forwardAgent ?: other.forwardAgent,
            strictHostKeyChecking = strictHostKeyChecking ?: other.strictHostKeyChecking,
        )
    }
}
