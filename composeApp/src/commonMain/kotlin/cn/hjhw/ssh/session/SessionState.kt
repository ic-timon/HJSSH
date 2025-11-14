package cn.hjhw.ssh.session

/**
 * 会话状态
 */
enum class SessionState {
    /**
     * 未连接
     */
    DISCONNECTED,
    
    /**
     * 正在连接
     */
    CONNECTING,
    
    /**
     * 已连接
     */
    CONNECTED,
    
    /**
     * 连接失败
     */
    FAILED,
    
    /**
     * 正在断开
     */
    DISCONNECTING,
}



