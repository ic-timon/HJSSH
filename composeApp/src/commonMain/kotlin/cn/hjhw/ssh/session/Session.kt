package cn.hjhw.ssh.session

import cn.hjhw.ssh.connection.SshConnection
import cn.hjhw.ssh.connection.SshConnectionParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SSH 会话
 */
class Session(
    val config: SessionConfig,
    private val connectionFactory: (SshConnectionParams) -> SshConnection,
    private val scope: CoroutineScope,
) {
    private var connection: SshConnection? = null
    private var connectJob: Job? = null
    
    private val _state = MutableStateFlow<SessionState>(SessionState.DISCONNECTED)
    val state: StateFlow<SessionState> = _state.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    /**
     * 连接
     */
    fun connect() {
        if (_state.value == SessionState.CONNECTED || _state.value == SessionState.CONNECTING) {
            return
        }
        
        connectJob?.cancel()
        
        try {
            // 立即创建连接对象（在协程外），确保 getConnection() 能立即返回
            val params = SshConnectionParams(
                host = config.host,
                port = config.port,
                user = config.user,
                authMethod = config.toSshAuthMethod(),
                keepAliveInterval = config.keepAliveInterval,
                connectTimeout = config.connectTimeout,
            )
            
            val conn = connectionFactory(params)
            connection = conn
            
            // 在协程中执行实际的连接操作
            connectJob = scope.launch {
                try {
                    _state.value = SessionState.CONNECTING
                    _error.value = null
                    
                    conn.connect()
                    
                    _state.value = SessionState.CONNECTED
                } catch (e: Exception) {
                    _state.value = SessionState.FAILED
                    _error.value = e.message ?: "Connection failed"
                    connection = null
                }
            }
        } catch (e: Exception) {
            _state.value = SessionState.FAILED
            _error.value = e.message ?: "Failed to create connection"
            connection = null
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        if (_state.value == SessionState.DISCONNECTED || _state.value == SessionState.DISCONNECTING) {
            return
        }
        
        connectJob?.cancel()
        scope.launch {
            try {
                _state.value = SessionState.DISCONNECTING
                connection?.close()
                connection = null
                _state.value = SessionState.DISCONNECTED
            } catch (e: Exception) {
                _error.value = e.message ?: "Disconnect failed"
                _state.value = SessionState.DISCONNECTED
            }
        }
    }
    
    /**
     * 重连
     */
    fun reconnect() {
        disconnect()
        // 等待断开完成后再连接
        scope.launch {
            kotlinx.coroutines.delay(500)
            connect()
        }
    }
    
    /**
     * 获取连接（无论连接状态如何）
     * 
     * 即使在 CONNECTING 状态，也返回 connection 对象，以便 UI 能立即显示终端。
     * 如果连接失败或未开始连接，则返回 null。
     */
    fun getConnection(): SshConnection? {
        return connection
    }
    
    /**
     * 是否已连接
     */
    fun isConnected(): Boolean {
        return _state.value == SessionState.CONNECTED && connection?.isConnected == true
    }
    
    /**
     * 清理资源
     */
    fun dispose() {
        connectJob?.cancel()
        disconnect()
    }
}



