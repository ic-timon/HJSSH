package cn.hjhw.ssh.config

/**
 * SSH 配置文件解析结果
 */
data class SshConfig(
    val hosts: List<SshHostConfig>,
) {
    /**
     * 根据 host 名称查找配置
     * 支持通配符匹配（*, *.example.com 等）
     * 配置继承：通配符配置会被精确匹配的配置继承
     */
    fun findHost(hostName: String): SshHostConfig? {
        // 查找精确匹配
        val exactMatch = hosts.firstOrNull { it.host == hostName }

        // 查找所有匹配的通配符配置（包括 *）
        val wildcardMatches =
            hosts.filter {
                it.host != hostName && matchesPattern(it.host, hostName)
            }.sortedByDescending { it.host.length } // 更具体的模式优先

        // 如果找到精确匹配，先应用通配符配置，再应用精确匹配配置
        if (exactMatch != null) {
            // 先合并所有通配符配置
            val baseConfig =
                wildcardMatches.reduceOrNull { acc, config ->
                    config.mergeWith(acc)
                }

            // 然后应用精确匹配配置（精确匹配优先）
            return if (baseConfig != null) {
                exactMatch.mergeWith(baseConfig)
            } else {
                exactMatch
            }
        }

        // 如果没有精确匹配，返回通配符匹配结果
        if (wildcardMatches.isEmpty()) {
            return null
        }

        // 合并所有匹配的配置，后面的配置优先
        return wildcardMatches.reduceOrNull { acc, config ->
            config.mergeWith(acc)
        }
    }

    /**
     * 检查 host 名称是否匹配模式
     * 支持 * 通配符（如 *.example.com）
     */
    private fun matchesPattern(
        pattern: String,
        hostName: String,
    ): Boolean {
        if (pattern == "*") return true
        if (pattern == hostName) return true

        // 支持 *.example.com 这样的模式
        if (pattern.startsWith("*.")) {
            val suffix = pattern.substring(1) // 去掉开头的 *
            return hostName.endsWith(suffix)
        }

        // 支持简单的通配符匹配
        val regex =
            pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .toRegex()

        return regex.matches(hostName)
    }
}
