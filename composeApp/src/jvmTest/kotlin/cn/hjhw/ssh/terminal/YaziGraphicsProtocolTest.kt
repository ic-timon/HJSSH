package cn.hjhw.ssh.terminal

import java.io.ByteArrayOutputStream
import kotlin.test.Ignore
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.junit.Test

/**
 * 测试Yazi发送的图形协议数据
 * 目的：诊断SSH传输的图形数据格式
 */
class YaziGraphicsProtocolTest {
    @Test
    @Ignore("Requires real SSH connection to external server")
    fun `test capture yazi graphics protocol data`() =
        runBlocking {
            val client = SSHClient()
            var session: Session? = null

            try {
                println("\n=== Yazi Graphics Protocol Capture Test ===")

                // 连接到SSH服务器
                client.addHostKeyVerifier(PromiscuousVerifier())
                client.connect("example.com", 22)

                // 认证 - 尝试多个密钥路径
                val homeDir = System.getProperty("user.home")
                val possibleKeys =
                    listOf(
                        "$homeDir\\.ssh\\id_rsa",
                        "$homeDir/.ssh/id_rsa",
                        "$homeDir\\.ssh\\id_ed25519",
                        "$homeDir/.ssh/id_ed25519",
                    )

                var authenticated = false
                for (keyPath in possibleKeys) {
                    try {
                        val file = java.io.File(keyPath)
                        if (file.exists()) {
                            val keyProvider = client.loadKeys(keyPath)
                            client.authPublickey("testuser", keyProvider)
                            authenticated = true
                            println("✓ Authenticated with key: $keyPath")
                            break
                        }
                    } catch (e: Exception) {
                        // 尝试下一个
                    }
                }

                if (!authenticated) {
                    println("✗ No SSH keys found, skipping test")
                    return@runBlocking
                }

                println("✓ Connected to SSH server")

                // 创建会话
                session = client.startSession()
                session.allocatePTY("xterm-256color", 80, 24, 640, 384, emptyMap())
                val shell = session.startShell()

                println("✓ Shell started")

                val output = ByteArrayOutputStream()
                val inputStream = shell.inputStream
                val outputStream = shell.outputStream

                // 发送yazi命令
                val command = "yazi /home/testuser\n"
                outputStream.write(command.toByteArray())
                outputStream.flush()

                println("✓ Sent command: yazi /home/testuser")
                println("✓ Capturing output for 3 seconds...")

                // 捕获3秒的输出
                val buffer = ByteArray(8192)
                val startTime = System.currentTimeMillis()
                var totalBytes = 0
                var kittyCommandCount = 0
                var sixelCommandCount = 0
                var oscImageCount = 0

                val capturedData = StringBuilder()

                while (System.currentTimeMillis() - startTime < 3000) {
                    if (inputStream.available() > 0) {
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead > 0) {
                            totalBytes += bytesRead
                            output.write(buffer, 0, bytesRead)

                            // 转换为字符串分析
                            val chunk = String(buffer, 0, bytesRead, Charsets.UTF_8)
                            capturedData.append(chunk)

                            // 检测图形协议
                            if (chunk.contains("\u001b_G")) {
                                kittyCommandCount++
                                println("  → Detected Kitty Graphics command")
                            }
                            if (chunk.contains("\u001bP") && chunk.contains("q")) {
                                sixelCommandCount++
                                println("  → Detected Sixel command")
                            }
                            if (chunk.contains("\u001b]1337;")) {
                                oscImageCount++
                                println("  → Detected iTerm2 inline image")
                            }
                        }
                    }
                    Thread.sleep(50)
                }

                // 发送退出命令
                outputStream.write("q".toByteArray())
                outputStream.flush()
                Thread.sleep(500)

                println("\n=== Capture Summary ===")
                println("Total bytes captured: $totalBytes")
                println("Kitty Graphics commands: $kittyCommandCount")
                println("Sixel commands: $sixelCommandCount")
                println("iTerm2 inline images: $oscImageCount")

                // 分析捕获的数据
                val fullData = capturedData.toString()

                // 查找Kitty Graphics命令
                val kittyPattern = Regex("""\x1b_G([^\x1b]*)\x1b\\""")
                val kittyMatches = kittyPattern.findAll(fullData)

                println("\n=== Kitty Graphics Commands ===")
                var kittyIndex = 0
                for (match in kittyMatches) {
                    kittyIndex++
                    val data = match.groupValues[1]
                    val parts = data.split(';', limit = 2)
                    val params = if (parts.isNotEmpty()) parts[0] else ""
                    val payload = if (parts.size > 1) parts[1] else ""

                    println("\nKitty Command #$kittyIndex:")
                    println("  Parameters: $params")
                    println("  Payload length: ${payload.length}")
                    println("  First 50 chars: ${payload.take(50)}")

                    // 解析参数
                    val paramMap = mutableMapOf<Char, String>()
                    params.split(',').forEach { param ->
                        if (param.contains('=')) {
                            val (key, value) = param.split('=', limit = 2)
                            if (key.isNotEmpty()) {
                                paramMap[key[0]] = value
                            }
                        }
                    }

                    println("  Parsed params: $paramMap")
                }

                // 查找Sixel命令
                val sixelPattern = Regex("""\x1bP([0-9;]*q[^\x1b]*)\x1b\\""")
                val sixelMatches = sixelPattern.findAll(fullData)

                println("\n=== Sixel Commands ===")
                var sixelIndex = 0
                for (match in sixelMatches) {
                    sixelIndex++
                    val data = match.groupValues[1]
                    println("\nSixel Command #$sixelIndex:")
                    println("  Data length: ${data.length}")
                    println("  First 100 chars: ${data.take(100)}")
                }

                // 断言：应该至少检测到一种图形协议
                assertTrue(
                    kittyCommandCount > 0 || sixelCommandCount > 0 || oscImageCount > 0,
                    "Expected to detect at least one graphics protocol command",
                )

                println("\n=== Test Complete ===")
            } finally {
                session?.close()
                client.disconnect()
            }
        }

    @Test
    fun `test parse kitty graphics command`() {
        println("\n=== Test Kitty Graphics Parser ===")

        // 测试传输命令
        val transmitData = "a=t,f=100,s=10,v=10;aVZCT1J3MEtHZ29B"
        val transmitCmd = KittyProtocolParser.parse(transmitData)

        println("Transmit command: $transmitCmd")
        assertTrue(transmitCmd is KittyGraphicsCommand.Transmit)

        // 测试显示命令
        val displayData = "a=p,i=1,p=1,c=5,r=3"
        val displayCmd = KittyProtocolParser.parse(displayData)

        println("Display command: $displayCmd")
        assertTrue(displayCmd is KittyGraphicsCommand.Display)

        // 测试删除命令
        val deleteData = "a=d,d=a"
        val deleteCmd = KittyProtocolParser.parse(deleteData)

        println("Delete command: $deleteCmd")
        assertTrue(deleteCmd is KittyGraphicsCommand.Delete)

        println("✓ All parser tests passed")
    }

    @Test
    fun `test image decoding and storage`() {
        println("\n=== Test Image Decoding ===")

        val buffer = TerminalBuffer(80, 24)
        val cursor = CursorState()

        // 创建一个简单的1x1红色像素PNG (base64编码)
        val redPixelPng = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg=="

        println("Adding image with base64 data...")
        buffer.addImage(
            imageId = 1,
            imageNumber = 1,
            format = TransmissionFormat.PNG,
            width = 1,
            height = 1,
            data = redPixelPng,
            isMore = false,
        )

        val image = buffer.getImage(1)
        assertTrue(image != null, "Image should be stored")
        println("✓ Image stored: $image")

        println("Placing image at (0,0)...")
        buffer.placeImage(
            imageId = 1,
            placementId = 1,
            x = 0,
            y = 0,
            columns = 5,
            rows = 3,
            zIndex = 0,
        )

        val placements = buffer.getImagePlacements()
        assertTrue(placements.isNotEmpty(), "Should have at least one placement")
        println("✓ Image placements: ${placements.size}")

        placements.forEach { placement ->
            println(
                "  Placement: id=${placement.placementId}, imageId=${placement.imageId}, " +
                    "pos=(${placement.x},${placement.y}), size=${placement.columns}x${placement.rows}",
            )
        }

        println("✓ Image decoding test complete")
    }
}
