package cn.hjhw.ssh.connection

import kotlinx.coroutines.flow.Flow

/**
 * SSH 连接接口
 */
interface SshConnection {
    /**
     * 连接参数
     */
    val params: SshConnectionParams

    /**
     * 是否已连接
     */
    val isConnected: Boolean

    /**
     * 打开连接
     */
    suspend fun connect()

    /**
     * 关闭连接
     */
    suspend fun close()

    /**
     * 写入数据到 SSH 会话
     */
    suspend fun write(data: String)

    /**
     * 写入字节数据
     */
    suspend fun writeBytes(data: ByteArray)

    /**
     * 调整终端大小
     */
    suspend fun resize(
        width: Int,
        height: Int,
    )

    /**
     * 观察输出流
     * @return Flow<String> 输出数据流
     */
    fun observeOutput(): Flow<String>

    /**
     * 执行命令（非交互式）
     */
    suspend fun exec(command: String): String
}

