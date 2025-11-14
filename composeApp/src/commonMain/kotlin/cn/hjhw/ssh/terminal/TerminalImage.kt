package cn.hjhw.ssh.terminal

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Kitty Graphics Protocol - 图片数据结构
 *
 * 规范: https://sw.kovidgoyal.net/kitty/graphics-protocol/
 */

/**
 * 图片传输格式
 */
enum class TransmissionFormat {
    PNG, // f=100 - PNG数据
    JPEG, // 未在协议中明确，但通常支持
    RGB, // f=24 - RGB数据
    RGBA, // f=32 - RGBA数据
}

/**
 * 传输介质
 */
enum class TransmissionMedium {
    DIRECT, // t=d - 直接传输（默认）
    FILE, // t=f - 文件路径
    TEMP_FILE, // t=t - 临时文件
    SHARED_MEM, // t=s - 共享内存
}

/**
 * 压缩类型
 */
enum class CompressionType {
    NONE, // o=0 - 无压缩
    ZLIB, // o=z - zlib压缩
}

/**
 * 终端图片 - 存储在终端中的图片数据
 */
data class TerminalImage(
    val id: Int, // 图片ID（i键）
    val imageNumber: Int = 0, // 图片编号（I键），用于引用
    val format: TransmissionFormat, // 图片格式
    val width: Int, // 图片宽度（像素）
    val height: Int, // 图片高度（像素）
    val data: ByteArray, // 图片数据（可能是base64编码或原始数据）
    val isBase64: Boolean = true, // 数据是否是base64编码
    val unicodePlaceholder: Boolean = false, // 是否请求了 Unicode Placeholder (U=1)
    var bitmap: ImageBitmap? = null, // 解码后的位图（延迟加载）
) {
    /**
     * 解码并获取位图
     * 如果还未解码，则立即解码
     */
    fun decodeBitmap(): ImageBitmap? {
        if (bitmap == null && data.isNotEmpty()) {
            val dataString =
                if (isBase64) {
                    String(data, Charsets.UTF_8)
                } else {
                    // 原始数据需要转换为base64
                    java.util.Base64.getEncoder().encodeToString(data)
                }

            bitmap =
                when (format) {
                    TransmissionFormat.RGB, TransmissionFormat.RGBA -> {
                        ImageDecoder.decodeRawPixels(dataString, width, height, format)
                    }
                    TransmissionFormat.PNG, TransmissionFormat.JPEG -> {
                        if (isBase64) {
                            ImageDecoder.decodeBase64(dataString, format)
                        } else {
                            ImageDecoder.decodeBytes(data, format)
                        }
                    }
                }
        }
        return bitmap
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TerminalImage) return false

        if (id != other.id) return false
        if (imageNumber != other.imageNumber) return false
        if (format != other.format) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (!data.contentEquals(other.data)) return false
        if (isBase64 != other.isBase64) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + imageNumber
        result = 31 * result + format.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + data.contentHashCode()
        result = 31 * result + isBase64.hashCode()
        return result
    }
}

/**
 * 图片放置 - 在终端中显示图片的位置和尺寸
 */
data class ImagePlacement(
    val placementId: Int, // 放置ID（p键）
    val imageId: Int, // 关联的图片ID（i键）
    val x: Int, // 左上角X坐标（单元格）
    val y: Int, // 左上角Y坐标（单元格）
    val columns: Int = 0, // 显示列数（c键），0表示自动
    val rows: Int = 0, // 显示行数（r键），0表示自动
    val srcX: Int = 0, // 源图片X偏移（像素）
    val srcY: Int = 0, // 源图片Y偏移（像素）
    val srcWidth: Int = 0, // 源图片宽度（像素），0表示全部
    val srcHeight: Int = 0, // 源图片高度（像素），0表示全部
    val zIndex: Int = 0, // Z轴顺序（z键）
    val cursorMovement: Int = 0, // 光标移动策略（C键）
)

/**
 * Kitty协议命令
 */
sealed class KittyGraphicsCommand {
    /**
     * 传输图片数据
     */
    data class Transmit(
        val action: Char = 't', // a=t - 传输并显示
        val quiet: Int = 0, // q键 - 抑制响应
        val format: TransmissionFormat, // f键 - 格式
        val medium: TransmissionMedium = TransmissionMedium.DIRECT, // t键
        val compression: CompressionType = CompressionType.NONE, // o键
        val width: Int, // s键 - 图片宽度
        val height: Int, // v键 - 图片高度
        val imageId: Int = 0, // i键 - 图片ID
        val imageNumber: Int = 0, // I键 - 图片编号
        val placementId: Int = 0, // p键 - 放置ID
        val columns: Int = 0, // c键 - 显示列数
        val rows: Int = 0, // r键 - 显示行数
        val x: Int = 0, // x键 - X偏移
        val y: Int = 0, // y键 - Y偏移
        val zIndex: Int = 0, // z键 - Z轴
        val unicodePlaceholder: Boolean = false, // U键 - 是否使用Unicode占位符
        val data: String, // payload - base64数据
        val more: Int = 0, // m键 - 是否有更多数据块
    ) : KittyGraphicsCommand()

    /**
     * 显示图片
     */
    data class Display(
        val action: Char = 'p', // a=p - 仅放置（不传输）
        val imageId: Int = 0, // i键
        val imageNumber: Int = 0, // I键
        val placementId: Int = 0, // p键
        val columns: Int = 0, // c键
        val rows: Int = 0, // r键
        val x: Int = 0, // X键 - 源X偏移
        val y: Int = 0, // Y键 - 源Y偏移
        val srcWidth: Int = 0, // w键 - 源宽度
        val srcHeight: Int = 0, // h键 - 源高度
        val zIndex: Int = 0, // z键
        val unicodePlaceholder: Boolean = false, // U键 - 是否使用Unicode占位符
    ) : KittyGraphicsCommand()

    /**
     * 删除图片
     */
    data class Delete(
        val action: Char = 'd', // a=d
        val deleteMode: Char = 'a', // d键 - 删除模式
        val imageId: Int = 0, // i键
        val imageNumber: Int = 0, // I键
        val placementId: Int = 0, // p键
    ) : KittyGraphicsCommand()

    /**
     * 查询
     */
    data class Query(
        val action: Char = 'q', // a=q
        val imageId: Int = 0, // i键
    ) : KittyGraphicsCommand()
}

/**
 * Kitty协议解析器
 */
object KittyProtocolParser {
    /**
     * 解析Kitty协议命令
     * 格式: key1=value1,key2=value2,...;base64data
     */
    fun parse(data: String): KittyGraphicsCommand? {
        try {
            // 分割控制数据和payload
            val parts = data.split(';', limit = 2)
            val controlData = parts.getOrNull(0) ?: return null
            val payload = parts.getOrNull(1) ?: ""

            // 解析控制数据
            val params = mutableMapOf<String, String>()
            controlData.split(',').forEach { pair ->
                val kv = pair.split('=', limit = 2)
                if (kv.size == 2) {
                    params[kv[0].trim()] = kv[1].trim()
                }
            }

            // 获取action
            val action = params["a"]?.firstOrNull() ?: 't'

            return when (action) {
                't', 'T' -> parseTransmit(params, payload)
                'p', 'P' -> parseDisplay(params)
                'd', 'D' -> parseDelete(params)
                'q', 'Q' -> parseQuery(params)
                else -> null
            }
        } catch (e: Exception) {
            println("      [KittyProtocol] Parse error: ${e.message}")
            return null
        }
    }

    private fun parseTransmit(
        params: Map<String, String>,
        payload: String,
    ): KittyGraphicsCommand.Transmit {
        val format =
            when (params["f"]?.toIntOrNull()) {
                100 -> TransmissionFormat.PNG
                24 -> TransmissionFormat.RGB
                32 -> TransmissionFormat.RGBA
                else -> TransmissionFormat.PNG
            }

        val medium =
            when (params["t"]) {
                "f" -> TransmissionMedium.FILE
                "t" -> TransmissionMedium.TEMP_FILE
                "s" -> TransmissionMedium.SHARED_MEM
                else -> TransmissionMedium.DIRECT
            }

        val compression =
            when (params["o"]) {
                "z" -> CompressionType.ZLIB
                else -> CompressionType.NONE
            }

        // Parse U parameter for Unicode placeholders
        val unicodePlaceholder = (params["U"]?.toIntOrNull() ?: 0) == 1
        if (unicodePlaceholder) {
            println("      [KittyProtocol] Unicode placeholder requested (U=1)")
        }

        return KittyGraphicsCommand.Transmit(
            action = params["a"]?.firstOrNull() ?: 't',
            quiet = params["q"]?.toIntOrNull() ?: 0,
            format = format,
            medium = medium,
            compression = compression,
            width = params["s"]?.toIntOrNull() ?: 0,
            height = params["v"]?.toIntOrNull() ?: 0,
            imageId = params["i"]?.toIntOrNull() ?: 0,
            imageNumber = params["I"]?.toIntOrNull() ?: 0,
            placementId = params["p"]?.toIntOrNull() ?: 0,
            columns = params["c"]?.toIntOrNull() ?: 0,
            rows = params["r"]?.toIntOrNull() ?: 0,
            x = params["x"]?.toIntOrNull() ?: 0,
            y = params["y"]?.toIntOrNull() ?: 0,
            zIndex = params["z"]?.toIntOrNull() ?: 0,
            unicodePlaceholder = unicodePlaceholder,
            data = payload,
            more = params["m"]?.toIntOrNull() ?: 0,
        )
    }

    private fun parseDisplay(params: Map<String, String>): KittyGraphicsCommand.Display {
        return KittyGraphicsCommand.Display(
            action = params["a"]?.firstOrNull() ?: 'p',
            imageId = params["i"]?.toIntOrNull() ?: 0,
            imageNumber = params["I"]?.toIntOrNull() ?: 0,
            placementId = params["p"]?.toIntOrNull() ?: 0,
            columns = params["c"]?.toIntOrNull() ?: 0,
            rows = params["r"]?.toIntOrNull() ?: 0,
            x = params["X"]?.toIntOrNull() ?: 0,
            y = params["Y"]?.toIntOrNull() ?: 0,
            srcWidth = params["w"]?.toIntOrNull() ?: 0,
            srcHeight = params["h"]?.toIntOrNull() ?: 0,
            zIndex = params["z"]?.toIntOrNull() ?: 0,
            unicodePlaceholder = (params["U"]?.toIntOrNull() ?: 0) == 1,
        )
    }

    private fun parseDelete(params: Map<String, String>): KittyGraphicsCommand.Delete {
        return KittyGraphicsCommand.Delete(
            action = params["a"]?.firstOrNull() ?: 'd',
            deleteMode = params["d"]?.firstOrNull() ?: 'a',
            imageId = params["i"]?.toIntOrNull() ?: 0,
            imageNumber = params["I"]?.toIntOrNull() ?: 0,
            placementId = params["p"]?.toIntOrNull() ?: 0,
        )
    }

    private fun parseQuery(params: Map<String, String>): KittyGraphicsCommand.Query {
        return KittyGraphicsCommand.Query(
            action = params["a"]?.firstOrNull() ?: 'q',
            imageId = params["i"]?.toIntOrNull() ?: 0,
        )
    }
}
