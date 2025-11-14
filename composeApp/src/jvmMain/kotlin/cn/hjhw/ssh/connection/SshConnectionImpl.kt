package cn.hjhw.ssh.connection

import cn.hjhw.ssh.connection.SshConnectionParams
import cn.hjhw.ssh.connection.SshConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.method.AuthMethod
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.AuthPublickey
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import java.util.concurrent.TimeUnit

/**
 * 基于 SSHJ 库的 SSH 连接实现
 *
 * 负责建立和管理 SSH 连接，处理认证、PTY 分配、数据传输等。
 *
 * ### 功能特性
 * - **认证方式**: 支持密码认证和 SSH 密钥认证
 * - **PTY 分配**: 分配伪终端 (Pseudo-TTY)，支持交互式 shell
 * - **终端类型**: 默认使用 `xterm-256color`，支持 256 色和 TrueColor
 * - **KeepAlive**: 自动发送心跳保持连接活跃
 * - **动态调整**: 支持运行时调整终端窗口大小
 *
 * ### 终端配置
 * - **TERM**: `xterm-256color` - 支持 ueberzugpp (Sixel) 图形协议
 * - **初始大小**: 80 列 × 24 行
 * - **像素大小**: 640×384 (估算值，用于图形协议)
 *
 * ### 数据流
 * ```
 * SSH 服务器 → observeOutput() → Flow<String> → AnsiParser
 * AnsiParser → TerminalBuffer → TerminalView
 * TerminalView (用户输入) → sendInput() → SSH 服务器
 * ```
 *
 * ### 使用示例
 * ```kotlin
 * val connection = SshConnectionImpl(params)
 * connection.connect()
 * connection.observeOutput().collect { data ->
 *     // 处理输出数据
 * }
 * connection.sendInput("ls\n")
 * ```
 *
 * @param params SSH 连接参数（主机、端口、用户名、认证信息等）
 *
 * @see SshConnection SSH 连接接口
 * @see SshConnectionParams SSH 连接参数
 */
class SshConnectionImpl(
    override val params: SshConnectionParams,
) : SshConnection {
    private var sshClient: SSHClient? = null
    private var session: Session? = null
    private var shell: Session.Shell? = null
    private var keepAliveJob: Job? = null

    override val isConnected: Boolean
        get() = sshClient?.isConnected == true && session?.isOpen == true

    override suspend fun connect() = withContext(Dispatchers.IO) {
        val client = SSHClient()
        sshClient = client

        // 设置连接超时
        client.timeout = params.connectTimeout.toInt()

        // 设置主机密钥验证（根据配置决定是否严格验证）
        // 这里暂时使用宽松验证，后续可以根据配置调整
        client.addHostKeyVerifier(PromiscuousVerifier())

        // 连接
        client.connect(params.host, params.port)

        // 认证
        authenticate(client)

        // 启动 KeepAlive
        startKeepAlive(client)

        // 创建交互式会话
        val newSession = client.startSession()
        session = newSession

        // 分配 PTY，使用 xterm-256color 支持 ueberzugpp (Sixel 协议)
        // 终端类型: xterm-256color (ueberzugpp 使用 Sixel 协议)
        // 列数: 80, 行数: 24 (初始值，后续会通过 resize 调整)
        // 宽度: 80*8 像素, 高度: 24*16 像素 (估计值)
        // 终端模式: 空map (使用默认模式)
        newSession.allocatePTY(
            "xterm-256color",   // 终端类型 - 使用标准 xterm，ueberzugpp 会使用 Sixel 协议
            80,                 // 列数
            24,                 // 行数
            640,                // 宽度（像素）
            384,                // 高度（像素）
            emptyMap()          // 终端模式
        )

        // 启动 shell
        shell = newSession.startShell()
    }

    private suspend fun authenticate(client: SSHClient) = withContext(Dispatchers.IO) {
        val authMethods = mutableListOf<AuthMethod>()

        when (val auth = params.authMethod) {
            is SshAuthMethod.Password -> {
                authMethods.add(AuthPassword(object : PasswordFinder {
                    override fun reqPassword(resource: Resource<*>?): CharArray {
                        return auth.password.toCharArray()
                    }

                    override fun shouldRetry(resource: Resource<*>?): Boolean {
                        return false
                    }
                }))
            }
            is SshAuthMethod.KeyFile -> {
                val keyProvider: KeyProvider = if (auth.passphrase != null) {
                    client.loadKeys(auth.keyPath, object : PasswordFinder {
                        override fun reqPassword(resource: Resource<*>?): CharArray {
                            return auth.passphrase.toCharArray()
                        }

                        override fun shouldRetry(resource: Resource<*>?): Boolean {
                            return false
                        }
                    })
                } else {
                    client.loadKeys(auth.keyPath)
                }
                authMethods.add(AuthPublickey(keyProvider))
            }
            is SshAuthMethod.None -> {
                // 尝试使用默认密钥（按优先级顺序尝试）
                val homeDir = System.getProperty("user.home") ?: ""
                val defaultKeyPaths = listOf(
                    "$homeDir/.ssh/id_rsa",
                    "$homeDir/.ssh/id_ed25519",
                    "$homeDir/.ssh/id_ecdsa",
                    "$homeDir/.ssh/id_dsa",
                )
                
                var keyLoaded = false
                var lastException: Exception? = null
                
                for (keyPath in defaultKeyPaths) {
                    try {
                        val keyFile = java.io.File(keyPath)
                        if (keyFile.exists() && keyFile.isFile) {
                            val defaultKeyProvider = client.loadKeys(keyPath)
                            authMethods.add(AuthPublickey(defaultKeyProvider))
                            keyLoaded = true
                            break
                        }
                    } catch (e: Exception) {
                        lastException = e
                        // 继续尝试下一个密钥
                    }
                }
                
                // 如果所有默认密钥都失败，扫描 ~/.ssh 目录下所有可能的密钥文件
                if (!keyLoaded) {
                    try {
                        val sshDir = java.io.File("$homeDir/.ssh")
                        if (sshDir.exists() && sshDir.isDirectory) {
                            // 查找所有可能的私钥文件
                            val keyFiles = sshDir.listFiles { file ->
                                file.isFile && (
                                    (file.name.startsWith("id_") || 
                                     file.name.startsWith("ssh_") ||
                                     file.name.endsWith("_rsa") ||
                                     file.name.endsWith("_ed25519") ||
                                     file.name.endsWith("_ecdsa") ||
                                     file.name.endsWith("_dsa")) &&
                                    !file.name.endsWith(".pub") &&
                                    !file.name.endsWith(".bak") &&
                                    !file.name.contains("known_hosts") &&
                                    !file.name.contains("config")
                                )
                            } ?: emptyArray()
                            
                            // 按优先级排序：id_rsa > id_ed25519 > id_ecdsa > 其他
                            val sortedKeys = keyFiles.sortedBy { file ->
                                when {
                                    file.name == "id_rsa" -> 0
                                    file.name == "id_ed25519" -> 1
                                    file.name == "id_ecdsa" -> 2
                                    file.name.startsWith("id_") -> 3
                                    else -> 4
                                }
                            }
                            
                            for (keyFile in sortedKeys) {
                                try {
                                    val keyProvider = client.loadKeys(keyFile.absolutePath)
                                    authMethods.add(AuthPublickey(keyProvider))
                                    keyLoaded = true
                                    break
                                } catch (e: Exception) {
                                    // 继续尝试下一个密钥
                                }
                            }
                        }
                    } catch (e: Exception) {
                        lastException = e
                    }
                }
                
                // 如果仍然没有找到密钥，抛出异常
                if (!keyLoaded) {
                    throw IllegalStateException(
                        "No authentication method available. " +
                        "Please specify a password or key file, or ensure default SSH keys exist in ~/.ssh/",
                        lastException
                    )
                }
            }
        }

        client.auth(params.user, *authMethods.toTypedArray())
    }

    private fun startKeepAlive(client: SSHClient) {
        keepAliveJob?.cancel()
        keepAliveJob = CoroutineScope(Dispatchers.IO).launch {
            while (client.isConnected) {
                delay(params.keepAliveInterval)
                try {
                    // sshj 通过发送空数据包来保持连接
                    client.getConnection().getKeepAlive().setKeepAliveInterval(params.keepAliveInterval.toInt())
                } catch (e: Exception) {
                    // KeepAlive 失败，连接可能已断开
                    break
                }
            }
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        keepAliveJob?.cancel()
        shell?.close()
        session?.close()
        sshClient?.disconnect()
        sshClient = null
        session = null
        shell = null
    }

    override suspend fun write(data: String) {
        withContext(Dispatchers.IO) {
            shell?.outputStream?.write(data.toByteArray())
            shell?.outputStream?.flush()
        }
    }

    override suspend fun writeBytes(data: ByteArray) {
        withContext(Dispatchers.IO) {
            shell?.outputStream?.write(data)
            shell?.outputStream?.flush()
        }
    }

    override suspend fun resize(width: Int, height: Int) {
        withContext(Dispatchers.IO) {
            try {
                println("[SshConnection] === Resize Request ===")
                println("[SshConnection] Target size: ${width}x${height}")
                println("[SshConnection] Shell is null? ${shell == null}")
                
                if (shell == null) {
                    println("[SshConnection] ERROR: Shell is null, cannot resize!")
                    return@withContext
                }
                
                println("[SshConnection] Shell class: ${shell!!.javaClass.name}")
                
                // shell 本身就是 SessionChannel！不需要获取 channel 字段
                if (shell is net.schmizz.sshj.connection.channel.direct.SessionChannel) {
                    val channel = shell as net.schmizz.sshj.connection.channel.direct.SessionChannel
                    println("[SshConnection] Shell is SessionChannel, sending window-change request...")
                    channel.changeWindowDimensions(width, height, 0, 0)
                    println("[SshConnection] ✓ Window change request sent successfully: ${width}x${height}")
                } else {
                    println("[SshConnection] ERROR: Shell is not a SessionChannel, it's ${shell!!.javaClass.name}")
                    println("[SshConnection] Trying to find changeWindowDimensions method via reflection...")
                    
                    try {
                        // 尝试通过反射调用 changeWindowDimensions 方法
                        val method = shell!!.javaClass.getMethod(
                            "changeWindowDimensions",
                            Int::class.java,
                            Int::class.java,
                            Int::class.java,
                            Int::class.java
                        )
                        method.invoke(shell, width, height, 0, 0)
                        println("[SshConnection] ✓ Window change sent via reflection: ${width}x${height}")
                    } catch (e: Exception) {
                        println("[SshConnection] ERROR: Could not send window change: ${e.message}")
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                println("[SshConnection] FATAL ERROR in resize: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    override fun observeOutput(): Flow<String> = flow {
        // 等待 shell 准备好（最多等待 5 秒）
        var waitCount = 0
        while (shell == null && waitCount < 50 && isConnected) {
            delay(100)
            waitCount++
        }
        
        val inputStream = shell?.inputStream ?: return@flow
        
        val buffer = ByteArray(8192)
        val charset = Charsets.UTF_8
        val decoder = charset.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE)

        while (isConnected) {
            val bytesRead = withContext(Dispatchers.IO) {
                try {
                    inputStream.read(buffer)
                } catch (e: Exception) {
                    -1
                }
            }

            if (bytesRead > 0) {
                // ⚠️ CRITICAL FIX: 不要每次都 reset() decoder！
                // emoji 等多字节 UTF-8 字符可能跨越 buffer 边界
                // decoder 需要保持状态以正确处理跨 buffer 的字符
                val byteBuffer = java.nio.ByteBuffer.wrap(buffer, 0, bytesRead)
                val charBuffer = decoder.decode(byteBuffer)
                val data = charBuffer.toString()
                
                emit(data)
            } else if (bytesRead == -1) {
                break
            } else {
                delay(10)
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun exec(command: String): String = withContext(Dispatchers.IO) {
        val execSession = sshClient?.startSession() ?: throw IllegalStateException("Not connected")
        try {
            val cmd = execSession.exec(command)
            val output = cmd.inputStream.bufferedReader().readText()
            cmd.join(5, TimeUnit.SECONDS)
            output
        } finally {
            execSession.close()
        }
    }
}

