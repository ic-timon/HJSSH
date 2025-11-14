package cn.hjhw.ssh.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 会话管理器
 */
class SessionManager(
    private val scope: CoroutineScope,
    private val connectionFactory: (cn.hjhw.ssh.connection.SshConnectionParams) -> cn.hjhw.ssh.connection.SshConnection,
    private val storage: SessionStorage,
) {
    private val sessions = mutableMapOf<String, Session>()
    private val _sessionsList = MutableStateFlow<List<Session>>(emptyList())
    val sessionsList: StateFlow<List<Session>> = _sessionsList.asStateFlow()

    private var activeSessionId: String? = null
    private val _activeSession = MutableStateFlow<Session?>(null)
    val activeSession: StateFlow<Session?> = _activeSession.asStateFlow()

    init {
        loadSessions()
    }

    /**
     * 创建新会话
     */
    fun createSession(config: SessionConfig): Session {
        val session = Session(config, connectionFactory, scope)
        sessions[config.id] = session
        updateSessionsList()
        saveSessions()
        return session
    }

    /**
     * 获取会话
     */
    fun getSession(id: String): Session? {
        return sessions[id]
    }

    /**
     * 删除会话
     */
    fun deleteSession(id: String) {
        val session = sessions.remove(id)
        session?.dispose()
        updateSessionsList()
        if (activeSessionId == id) {
            activeSessionId = null
            _activeSession.value = null
        }
        saveSessions()
    }

    /**
     * 设置活动会话
     */
    fun setActiveSession(id: String?) {
        activeSessionId = id
        _activeSession.value = id?.let { sessions[it] }
    }

    /**
     * 更新会话配置
     */
    fun updateSession(
        id: String,
        config: SessionConfig,
    ) {
        val existingSession = sessions[id]
        if (existingSession != null) {
            // 如果配置改变，需要重新创建会话
            if (existingSession.config != config) {
                val wasConnected = existingSession.isConnected()
                existingSession.dispose()

                val newSession = Session(config, connectionFactory, scope)
                sessions[id] = newSession

                if (wasConnected) {
                    newSession.connect()
                }

                updateSessionsList()
                saveSessions()
            }
        }
    }

    /**
     * 保存会话列表
     */
    private fun saveSessions() {
        val configs = sessions.values.map { it.config }
        storage.saveSessions(configs)
    }

    /**
     * 加载会话列表
     */
    private fun loadSessions() {
        val configs = storage.loadSessions()
        configs.forEach { config ->
            val session = Session(config, connectionFactory, scope)
            sessions[config.id] = session
        }
        updateSessionsList()
    }

    /**
     * 更新会话列表
     */
    private fun updateSessionsList() {
        _sessionsList.value = sessions.values.toList()
    }

    /**
     * 清理所有资源
     */
    fun dispose() {
        sessions.values.forEach { it.dispose() }
        sessions.clear()
        updateSessionsList()
    }
}

/**
 * 会话存储接口
 */
interface SessionStorage {
    fun saveSessions(configs: List<SessionConfig>)

    fun loadSessions(): List<SessionConfig>
}
