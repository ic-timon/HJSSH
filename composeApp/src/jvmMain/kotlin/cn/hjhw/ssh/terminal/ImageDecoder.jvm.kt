package cn.hjhw.ssh.terminal

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage
import java.util.Base64

/**
 * JVM平台的图片解码器
 * 支持Base64、PNG、JPEG等格式
 */
actual object ImageDecoder {
    
    /**
     * 解码base64编码的图片数据
     * @param base64Data Base64编码的图片数据
     * @param format 图片格式（PNG, JPEG等）
     * @return 解码后的ImageBitmap，失败返回null
     */
    actual fun decodeBase64(base64Data: String, format: TransmissionFormat): ImageBitmap? {
        return try {
            // 移除可能的空白字符
            val cleanData = base64Data.replace("\\s".toRegex(), "")
            
            // Base64解码
            val imageBytes = Base64.getDecoder().decode(cleanData)
            
            println("      [ImageDecoder] Decoded ${imageBytes.size} bytes from base64")
            
            // 使用Skia解码图片
            val skiaImage = SkiaImage.makeFromEncoded(imageBytes)
            val imageBitmap = skiaImage.toComposeImageBitmap()
            
            println("      [ImageDecoder] Successfully decoded ${format.name} image: ${imageBitmap.width}x${imageBitmap.height}")
            
            imageBitmap
        } catch (e: Exception) {
            println("      [ImageDecoder] Failed to decode image: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 解码原始图片字节数据
     * @param bytes 图片字节数据
     * @param format 图片格式
     * @return 解码后的ImageBitmap，失败返回null
     */
    actual fun decodeBytes(bytes: ByteArray, format: TransmissionFormat): ImageBitmap? {
        return try {
            println("      [ImageDecoder] Decoding ${bytes.size} bytes as ${format.name}")
            
            // 使用Skia解码图片
            val skiaImage = SkiaImage.makeFromEncoded(bytes)
            val imageBitmap = skiaImage.toComposeImageBitmap()
            
            println("      [ImageDecoder] Successfully decoded image: ${imageBitmap.width}x${imageBitmap.height}")
            
            imageBitmap
        } catch (e: Exception) {
            println("      [ImageDecoder] Failed to decode image: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 解码RGB/RGBA原始像素数据
     * @param data 原始像素数据
     * @param width 图片宽度
     * @param height 图片高度
     * @param format 像素格式（RGB或RGBA）
     * @return 解码后的ImageBitmap，失败返回null
     */
    actual fun decodeRawPixels(
        data: String,
        width: Int,
        height: Int,
        format: TransmissionFormat
    ): ImageBitmap? {
        return try {
            // Base64解码像素数据
            val pixelBytes = Base64.getDecoder().decode(data.replace("\\s".toRegex(), ""))
            
            // 根据格式创建Skia Image
            val bitmap = when (format) {
                TransmissionFormat.RGB -> {
                    // RGB: 每个像素3字节
                    if (pixelBytes.size < width * height * 3) {
                        println("      [ImageDecoder] Insufficient data for RGB: expected ${width * height * 3}, got ${pixelBytes.size}")
                        return null
                    }
                    
                    // 转换RGB到BGRA（Skia的makeS32默认是BGRA格式）
                    val bgraBytes = ByteArray(width * height * 4)
                    for (i in 0 until (width * height)) {
                        bgraBytes[i * 4] = pixelBytes[i * 3 + 2]     // B ← 交换！
                        bgraBytes[i * 4 + 1] = pixelBytes[i * 3 + 1] // G
                        bgraBytes[i * 4 + 2] = pixelBytes[i * 3]     // R ← 交换！
                        bgraBytes[i * 4 + 3] = 0xFF.toByte()          // A (不透明)
                    }
                    
                    // 创建Skia Image（makeS32在大多数平台是BGRA）
                    val imageInfo = org.jetbrains.skia.ImageInfo.makeS32(width, height, org.jetbrains.skia.ColorAlphaType.PREMUL)
                    SkiaImage.makeRaster(imageInfo, bgraBytes, width * 4)
                }
                
                TransmissionFormat.RGBA -> {
                    // RGBA: 每个像素4字节
                    if (pixelBytes.size < width * height * 4) {
                        println("      [ImageDecoder] Insufficient data for RGBA: expected ${width * height * 4}, got ${pixelBytes.size}")
                        return null
                    }
                    
                    // 转换RGBA到BGRA（交换R和B通道）
                    val bgraBytes = ByteArray(width * height * 4)
                    for (i in 0 until (width * height)) {
                        bgraBytes[i * 4] = pixelBytes[i * 4 + 2]     // B ← 交换！
                        bgraBytes[i * 4 + 1] = pixelBytes[i * 4 + 1] // G
                        bgraBytes[i * 4 + 2] = pixelBytes[i * 4]     // R ← 交换！
                        bgraBytes[i * 4 + 3] = pixelBytes[i * 4 + 3] // A
                    }
                    
                    // 创建Skia Image（makeS32在大多数平台是BGRA）
                    val imageInfo = org.jetbrains.skia.ImageInfo.makeS32(width, height, org.jetbrains.skia.ColorAlphaType.PREMUL)
                    SkiaImage.makeRaster(imageInfo, bgraBytes, width * 4)
                }
                
                else -> {
                    println("      [ImageDecoder] Unsupported raw pixel format: $format")
                    return null
                }
            }
            
            bitmap.toComposeImageBitmap()
        } catch (e: Exception) {
            println("      [ImageDecoder] Failed to decode raw pixels: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}

