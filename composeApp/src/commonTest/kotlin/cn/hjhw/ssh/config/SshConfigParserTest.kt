package cn.hjhw.ssh.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SshConfigParserTest {
    private val parser = SshConfigParser()

    @Test
    fun testParseBasicConfig() {
        val content =
            """
            Host test
              HostName 198.51.100.10
              User testuser
              Port 2222
              IdentityFile ~/.ssh/id_rsa
            """.trimIndent()

        val config = parser.parseContent(content, "/tmp/config")

        assertEquals(1, config.hosts.size)
        val host = config.hosts.first()
        assertEquals("test", host.host)
        assertEquals("198.51.100.10", host.hostName)
        assertEquals("root", host.user)
        assertEquals(2222, host.port)
        assertNotNull(host.identityFile)
        // identityFile 应该被展开为绝对路径
        assert(host.identityFile!!.contains(".ssh/id_rsa"))
    }

    @Test
    fun testParseMultipleHostsWithWildcard() {
        val content =
            """
            Host *
              User common
            Host server1
              HostName example.com
              Port 22
            """.trimIndent()

        val config = parser.parseContent(content)

        assertEquals(2, config.hosts.size)

        // 测试精确匹配
        val server1 = config.findHost("server1")
        assertNotNull(server1)
        assertEquals("server1", server1.host)
        assertEquals("example.com", server1.hostName)
        assertEquals(22, server1.port)
        assertEquals("common", server1.user) // 继承自 * 配置

        // 测试通配符匹配
        val unknown = config.findHost("unknown")
        assertNotNull(unknown)
        assertEquals("*", unknown.host)
        assertEquals("common", unknown.user)
    }

    @Test
    fun testParseErrorTolerance() {
        val content =
            """
            Host test
              HostName
              Port abc
              UnknownKey something
            """.trimIndent()

        val config = parser.parseContent(content)

        assertEquals(1, config.hosts.size)
        val host = config.hosts.first()
        assertEquals("test", host.host)
        assertNull(host.hostName) // 空值应该为 null
        assertNull(host.port) // 无效的端口应该为 null
        // UnknownKey 应该被忽略，不会导致异常
    }

    @Test
    fun testCaseInsensitive() {
        val content =
            """
            Host test
              HOSTNAME example.com
              USER admin
              PORT 2222
            """.trimIndent()

        val config = parser.parseContent(content)

        val host = config.hosts.first()
        assertEquals("example.com", host.hostName)
        assertEquals("admin", host.user)
        assertEquals(2222, host.port)
    }

    @Test
    fun testWildcardPatternMatching() {
        val content =
            """
            Host *.example.com
              User admin
            Host server1.example.com
              HostName 198.51.100.1
            """.trimIndent()

        val config = parser.parseContent(content)

        // 测试通配符匹配
        val matched = config.findHost("test.example.com")
        assertNotNull(matched)
        assertEquals("*.example.com", matched.host)
        assertEquals("admin", matched.user)

        // 测试精确匹配优先
        val exact = config.findHost("server1.example.com")
        assertNotNull(exact)
        assertEquals("server1.example.com", exact.host)
        assertEquals("198.51.100.1", exact.hostName)
    }

    @Test
    fun testForwardAgent() {
        val content =
            """
            Host test
              ForwardAgent yes
            Host test2
              ForwardAgent no
            """.trimIndent()

        val config = parser.parseContent(content)

        val host1 = config.findHost("test")
        assertEquals(true, host1?.forwardAgent)

        val host2 = config.findHost("test2")
        assertEquals(false, host2?.forwardAgent)
    }

    @Test
    fun testCommentsAndEmptyLines() {
        val content =
            """
            # This is a comment
            Host test
              HostName example.com
              # Another comment
              
              User admin
            """.trimIndent()

        val config = parser.parseContent(content)

        assertEquals(1, config.hosts.size)
        val host = config.hosts.first()
        assertEquals("example.com", host.hostName)
        assertEquals("admin", host.user)
    }
}
