package cn.hjhw.ssh.connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Mock SSH 连接 - 用于演示和测试
 */
class MockSshConnection(
    override val params: SshConnectionParams,
) : SshConnection {
    override val isConnected: Boolean = true

    override suspend fun connect() {
        // 模拟连接延迟
        delay(500)
    }

    override suspend fun close() {
        // 模拟关闭
    }

    override suspend fun write(data: String) {
        // 模拟回显输入（在实际实现中，输入会被发送到服务器）
        // 这里只是占位，实际输出会通过 observeOutput 流返回
    }

    override suspend fun writeBytes(data: ByteArray) {
        write(String(data))
    }

    override suspend fun resize(
        width: Int,
        height: Int,
    ) {
        // 模拟调整大小
    }

    override fun observeOutput(): Flow<String> =
        flow {
            // 发送一些示例输出
            delay(500)
            emit("Welcome to Mock SSH Terminal!\r\n")
            delay(300)
            emit("This is a demonstration terminal.\r\n")
            delay(300)
            emit("You can see terminal output here.\r\n")
            delay(300)
            emit("\r\n")
            emit("\u001B[32muser@host\u001B[0m:\u001B[34m~\u001B[0m$ ")

            // 模拟一些命令输出
            delay(2000)
            emit("ls\r\n")
            delay(500)
            emit("file1.txt  file2.txt  directory/\r\n")
            emit("\u001B[32muser@host\u001B[0m:\u001B[34m~\u001B[0m$ ")

            delay(2000)
            emit("echo Hello World\r\n")
            delay(500)
            emit("Hello World\r\n")
            emit("\u001B[32muser@host\u001B[0m:\u001B[34m~\u001B[0m$ ")

            // 持续显示提示符
            while (true) {
                delay(5000)
                emit("\r\n\u001B[33m[Terminal is ready for input]\u001B[0m\r\n")
                emit("\u001B[32muser@host\u001B[0m:\u001B[34m~\u001B[0m$ ")
            }
        }.flowOn(Dispatchers.Default)

    override suspend fun exec(command: String): String {
        return when (command.trim()) {
            "echo OK" -> "OK\n"
            "ls" -> "file1.txt\nfile2.txt\n"
            "pwd" -> "/home/user\n"
            else -> "Command: $command\n"
        }
    }
}
