package cn.hjhw.ssh.ui

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * 会话事件
 */
@Serializable
sealed class SessionEvent {
    /**
     * 输出事件
     */
    @Serializable
    data class Output(
        val timestamp: Long,
        val data: String,
    ) : SessionEvent()

    /**
     * 输入事件
     */
    @Serializable
    data class Input(
        val timestamp: Long,
        val data: String,
    ) : SessionEvent()
}

/**
 * 会话录制器
 */
class SessionRecorder {
    private val events = mutableListOf<SessionEvent>()
    private var isRecording = false
    private val startTime = System.currentTimeMillis()

    /**
     * 开始录制
     */
    fun start() {
        isRecording = true
        events.clear()
    }

    /**
     * 停止录制
     */
    fun stop() {
        isRecording = false
    }

    /**
     * 记录输出事件
     */
    fun recordOutput(data: String) {
        if (isRecording) {
            events.add(
                SessionEvent.Output(
                    timestamp = System.currentTimeMillis() - startTime,
                    data = data,
                )
            )
        }
    }

    /**
     * 记录输入事件
     */
    fun recordInput(data: String) {
        if (isRecording) {
            events.add(
                SessionEvent.Input(
                    timestamp = System.currentTimeMillis() - startTime,
                    data = data,
                )
            )
        }
    }

    /**
     * 获取所有事件
     */
    fun getEvents(): List<SessionEvent> = events.toList()

    /**
     * 序列化为 JSON
     */
    fun toJson(): String {
        val json = Json {
            serializersModule = SerializersModule {
                polymorphic(SessionEvent::class) {
                    subclass(SessionEvent.Output::class)
                    subclass(SessionEvent.Input::class)
                }
            }
        }
        return json.encodeToString(ListSerializer(SessionEvent.serializer()), events)
    }

    /**
     * 从 JSON 反序列化
     */
    fun fromJson(jsonString: String) {
        val json = Json {
            serializersModule = SerializersModule {
                polymorphic(SessionEvent::class) {
                    subclass(SessionEvent.Output::class)
                    subclass(SessionEvent.Input::class)
                }
            }
        }
        events.clear()
        events.addAll(json.decodeFromString(ListSerializer(SessionEvent.serializer()), jsonString))
    }

    /**
     * 清除所有事件
     */
    fun clear() {
        events.clear()
    }

    /**
     * 获取事件数量
     */
    fun eventCount(): Int = events.size
}

/**
 * 会话回放器
 */
class SessionReplayer(
    private val events: List<SessionEvent>,
) {
    private var currentIndex = 0
    private var startTime: Long = 0
    private var isPlaying = false

    /**
     * 开始回放
     */
    suspend fun start(onOutput: (String) -> Unit, onInput: (String) -> Unit) {
        isPlaying = true
        startTime = System.currentTimeMillis()
        currentIndex = 0

        // 按时间顺序回放事件
        if (events.isNotEmpty()) {
            val firstEvent = events[0]
            val baseTime = when (firstEvent) {
                is SessionEvent.Output -> firstEvent.timestamp
                is SessionEvent.Input -> firstEvent.timestamp
            }

            for (event in events) {
                val eventTime = when (event) {
                    is SessionEvent.Output -> event.timestamp
                    is SessionEvent.Input -> event.timestamp
                }
                val delay = eventTime - baseTime
                if (delay > 0) {
                    kotlinx.coroutines.delay(delay)
                }

                when (event) {
                    is SessionEvent.Output -> onOutput(event.data)
                    is SessionEvent.Input -> onInput(event.data)
                }
                currentIndex++
            }
        }
    }

    /**
     * 停止回放
     */
    fun stop() {
        isPlaying = false
    }

    /**
     * 是否正在回放
     */
    fun isPlaying(): Boolean = isPlaying

    /**
     * 获取进度（0.0 - 1.0）
     */
    fun getProgress(): Float {
        return if (events.isEmpty()) {
            0f
        } else {
            (currentIndex.toFloat() / events.size).coerceIn(0f, 1f)
        }
    }
}

