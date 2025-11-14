package cn.hjhw.ssh.terminal

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 图片解码器
 * 支持Base64、PNG、JPEG、RGB/RGBA等格式
 */
expect object ImageDecoder {
    /**
     * 解码base64编码的图片数据
     * @param base64Data Base64编码的图片数据
     * @param format 图片格式
     * @return 解码后的ImageBitmap，失败返回null
     */
    fun decodeBase64(
        base64Data: String,
        format: TransmissionFormat,
    ): ImageBitmap?

    /**
     * 解码原始图片字节数据
     * @param bytes 图片字节数据
     * @param format 图片格式
     * @return 解码后的ImageBitmap，失败返回null
     */
    fun decodeBytes(
        bytes: ByteArray,
        format: TransmissionFormat,
    ): ImageBitmap?

    /**
     * 解码RGB/RGBA原始像素数据
     * @param data 原始像素数据（base64编码）
     * @param width 图片宽度
     * @param height 图片高度
     * @param format 像素格式（RGB或RGBA）
     * @return 解码后的ImageBitmap，失败返回null
     */
    fun decodeRawPixels(
        data: String,
        width: Int,
        height: Int,
        format: TransmissionFormat,
    ): ImageBitmap?
}
