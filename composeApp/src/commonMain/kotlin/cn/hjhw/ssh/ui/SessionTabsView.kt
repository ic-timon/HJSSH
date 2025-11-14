package cn.hjhw.ssh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.hjhw.ssh.session.Session
import cn.hjhw.ssh.session.SessionState

/**
 * 会话标签页视图
 */
@Composable
fun SessionTabsView(
    sessions: List<Session>,
    activeSession: Session?,
    onSessionSelected: (Session) -> Unit,
    onSessionClosed: (Session) -> Unit,
    onCreateNewSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 标签栏（固定高度，带水平滚动）
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)  // 固定高度，确保标签栏可见
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
                // 空状态提示（当没有标签页时显示）
                if (sessions.isEmpty()) {
                    Text(
                        text = "No sessions - Click + to create one or select a host from the sidebar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                
                // 现有会话标签
                sessions.forEach { session ->
                    SessionTab(
                        session = session,
                        isActive = session == activeSession,
                        onClick = { onSessionSelected(session) },
                        onClose = { onSessionClosed(session) },
                    )
                }
                
            // 新建会话按钮（更明显的设计）
            Button(
                onClick = onCreateNewSession,
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = "+ New Session",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * 单个会话标签
 */
@Composable
private fun SessionTab(
    session: Session,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by session.state.collectAsState()
    val backgroundColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    val textColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    
    Row(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 状态指示器
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    when (state) {
                        SessionState.CONNECTED -> Color(0xFF4CAF50)
                        SessionState.CONNECTING -> Color(0xFFFF9800)
                        SessionState.FAILED -> Color(0xFFF44336)
                        else -> Color(0xFF9E9E9E)
                    },
                    shape = androidx.compose.foundation.shape.CircleShape,
                ),
        )
        
        // 会话名称
        Text(
            text = session.config.name,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
        )
        
        // 关闭按钮
        TextButton(
            onClick = onClose,
            modifier = Modifier.size(20.dp),
            contentPadding = PaddingValues(4.dp),
        ) {
            Text(
                text = "×",
                color = textColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

