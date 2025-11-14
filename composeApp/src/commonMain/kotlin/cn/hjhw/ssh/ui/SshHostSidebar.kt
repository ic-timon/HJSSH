package cn.hjhw.ssh.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.hjhw.ssh.config.SshConfigParser
import cn.hjhw.ssh.config.SshHostConfig

/**
 * SSH Host 侧边栏
 */
@Composable
fun SshHostSidebar(
    onHostSelected: (SshHostConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(true) }
    var hosts by remember { mutableStateOf<List<SshHostConfig>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // 加载 SSH 配置
    LaunchedEffect(Unit) {
        isLoading = true
        error = null
        try {
            val config = SshConfigParser.parse()
            hosts = config.hosts.filter { 
                // 过滤掉通配符 host（如 *），只显示具体的 host
                it.host != "*" && !it.host.startsWith("*.")
            }
        } catch (e: Exception) {
            error = e.message ?: "Failed to load SSH config"
        } finally {
            isLoading = false
        }
    }
    
    // 侧边栏宽度：展开时 250.dp，收起时只显示按钮（48.dp）
    val sidebarWidth by animateDpAsState(
        targetValue = if (isExpanded) 250.dp else 48.dp,
        animationSpec = tween(300),
        label = "sidebarWidth"
    )
    
    Column(
        modifier = modifier
            .width(sidebarWidth)  // 固定宽度，根据展开状态变化
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // 顶部展开/收起按钮（始终显示）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.size(32.dp),
            ) {
                Text(
                    text = if (isExpanded) "◀" else "▶",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            
            // 标题（仅在展开时显示）
            AnimatedVisibility(
                visible = isExpanded,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(
                    text = "SSH Hosts",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        
        // 侧边栏内容（可展开/收起）
        AnimatedVisibility(
            visible = isExpanded,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
            ) {
                HorizontalDivider()
                
                // Host 列表
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (error != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = error ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else if (hosts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No hosts found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(hosts) { host ->
                            HostItem(
                                host = host,
                                onClick = { onHostSelected(host) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Host 列表项
 */
@Composable
private fun HostItem(
    host: SshHostConfig,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = host.host,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            
            host.hostName?.let { hostName ->
                Text(
                    text = hostName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                host.user?.let { user ->
                    Text(
                        text = "@$user",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                
                host.port?.let { port ->
                    Text(
                        text = ":$port",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

