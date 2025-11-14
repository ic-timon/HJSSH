package cn.hjhw.ssh

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import cn.hjhw.ssh.connection.MockSshConnection
import cn.hjhw.ssh.connection.SshAuthMethod
import cn.hjhw.ssh.connection.SshConnectionParams
import cn.hjhw.ssh.connection.SshConnectionImpl
import cn.hjhw.ssh.session.*
import cn.hjhw.ssh.ui.SessionTabsView
import cn.hjhw.ssh.ui.SshHostSidebar
import cn.hjhw.ssh.ui.TerminalView
import cn.hjhw.ssh.ui.TerminalViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.util.UUID

@Composable
@Preview
fun App() {
    MaterialTheme {
        val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
        
        // 创建会话管理器
        val sessionManager = remember {
            SessionManager(
                scope = scope,
                connectionFactory = { params ->
                    // 在实际应用中，这里应该使用真实的 SSH 连接
                    // 为了演示，我们使用 Mock 连接
                    if (params.host == "mock-host") {
                        MockSshConnection(params)
                    } else {
                        SshConnectionImpl(params)
                    }
                },
                storage = FileSessionStorage(),
            )
        }
        
        // 加载已保存的会话（不自动创建默认会话）
        LaunchedEffect(Unit) {
            // 如果有已保存的会话，加载第一个作为活动会话
            val firstSession = sessionManager.sessionsList.value.firstOrNull()
            if (firstSession != null) {
                sessionManager.setActiveSession(firstSession.config.id)
            }
            // 如果没有会话，用户可以通过侧边栏或 "+" 按钮创建新会话
        }
        
        // 清理资源
        DisposableEffect(Unit) {
            onDispose {
                sessionManager.dispose()
            }
        }
        
        // 主界面
        MainView(
            sessionManager = sessionManager,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun MainView(
    sessionManager: SessionManager,
    modifier: Modifier = Modifier,
) {
    val sessions by sessionManager.sessionsList.collectAsState()
    val activeSession by sessionManager.activeSession.collectAsState()
    
    // ✅ 修复：为所有 sessions 创建并缓存 ViewModel，避免切换时 dispose
    val viewModels = remember { mutableMapOf<String, TerminalViewModel>() }
    
    // 为所有 sessions 创建 ViewModel（如果还没有）
    LaunchedEffect(sessions) {
        sessions.forEach { session ->
            val connection = session.getConnection()
            if (connection != null && !viewModels.containsKey(session.config.id)) {
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
                val viewModel = TerminalViewModel(connection, scope)
                viewModels[session.config.id] = viewModel
                
                // 立即连接
                scope.launch {
                    viewModel.connect()
                }
            }
        }
        
        // 清理已删除的 sessions
        val sessionIds = sessions.map { it.config.id }.toSet()
        val toRemove = viewModels.keys.filter { it !in sessionIds }
        toRemove.forEach { id ->
            viewModels[id]?.dispose()
            viewModels.remove(id)
        }
    }
    
    Row(
        modifier = modifier.fillMaxSize(),
    ) {
        // SSH Host 侧边栏
        SshHostSidebar(
            onHostSelected = { hostConfig ->
                // 从 SSH 配置创建会话配置
                // 确定认证方法：如果有 identityFile 使用密钥文件，否则使用默认密钥（None）
                val authMethod = if (hostConfig.identityFile != null && hostConfig.identityFile.isNotEmpty()) {
                    SessionConfig.SerializedAuthMethod.KeyFile(hostConfig.identityFile, null)
                } else {
                    // 没有指定密钥文件，使用默认密钥认证
                    SessionConfig.SerializedAuthMethod.None
                }
                
                val sessionConfig = SessionConfig(
                    id = UUID.randomUUID().toString(),
                    name = hostConfig.host,
                    host = hostConfig.hostName ?: hostConfig.host,
                    port = hostConfig.port ?: 22,
                    user = hostConfig.user ?: (System.getProperty("user.name") ?: "user"),
                    authMethod = authMethod,
                )
                
                // 创建新会话
                val newSession = sessionManager.createSession(sessionConfig)
                sessionManager.setActiveSession(newSession.config.id)
                newSession.connect()
            },
            modifier = Modifier.fillMaxHeight(),
        )
        
        // 主内容区域
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),  // 确保占据剩余空间
        ) {
            // 标签页栏
            SessionTabsView(
                sessions = sessions,
                activeSession = activeSession,
                onSessionSelected = { session ->
                    sessionManager.setActiveSession(session.config.id)
                },
                onSessionClosed = { session ->
                    sessionManager.deleteSession(session.config.id)
                },
                onCreateNewSession = {
                    // TODO: 打开新建会话对话框
                    val newConfig = SessionConfig(
                        id = UUID.randomUUID().toString(),
                        name = "New Session ${sessions.size + 1}",
                        host = "mock-host",
                        port = 22,
                        user = "user",
                        authMethod = SessionConfig.SerializedAuthMethod.None,
                    )
                    
                    val newSession = sessionManager.createSession(newConfig)
                    sessionManager.setActiveSession(newSession.config.id)
                    newSession.connect()
                },
            )
            
            // 终端视图
            activeSession?.let { session ->
                // 从缓存中获取 ViewModel
                val viewModel = viewModels[session.config.id]
                
                if (viewModel != null) {
                    TerminalView(
                        viewModel = viewModel,
                        modifier = Modifier
                            .weight(1f)  // 占据剩余空间（标签栏下方）
                            .fillMaxWidth(),  // 确保宽度填满
                    )
                } else {
                    // ViewModel 还未创建（可能正在连接）
                    val state by session.state.collectAsState()
                    val error by session.error.collectAsState()
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = when (state) {
                                    SessionState.CONNECTING -> "Connecting..."
                                    SessionState.FAILED -> "Connection Failed"
                                    SessionState.DISCONNECTING -> "Disconnecting..."
                                    else -> "Initializing..."
                                },
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            
                            error?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            
                            if (state == SessionState.FAILED) {
                                Button(
                                    onClick = { session.connect() },
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
            } ?: run {
                // 没有活动会话
                Box(
                    modifier = Modifier
                        .weight(1f)  // 占据剩余空间
                        .fillMaxWidth(),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Text(
                        text = "No active session",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}