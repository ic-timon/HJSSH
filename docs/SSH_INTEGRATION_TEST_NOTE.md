# SSH集成测试实现说明

## 当前状态

已创建真实SSH集成测试框架，但需要适配实际的SSH连接API。

## API适配需求

### 当前API（基于代码分析）

```kotlin
// SSH连接接口
interface SshConnection {
    val params: SshConnectionParams
    val isConnected: Boolean
    
    suspend fun connect()
    suspend fun close()
    suspend fun write(data: String)        // 发送数据
    suspend fun writeBytes(data: ByteArray)
    suspend fun resize(width: Int, height: Int)
    fun observeOutput(): Flow<String>      // 接收数据流
    suspend fun exec(command: String): String
}

// 连接参数
data class SshConnectionParams(
    val host: String,
    val port: Int,
    val user: String,
    val authMethod: SshAuthMethod,
    val connectTimeout: Long = 10000,
    val keepAliveInterval: Long = 30000
)
```

### 需要的修改

1. **创建连接参数**
   ```kotlin
   val params = SshConnectionParams(
       host = "example.com",
       port = 22,
       user = "testuser",
       authMethod = SshAuthMethod.KeyFile(
           keyPath = "${System.getProperty("user.home")}/.ssh/id_rsa",
           passphrase = null
       )
   )
   ```

2. **使用Flow接收输出**
   ```kotlin
   connection.observeOutput().collect { data ->
       // 处理接收的数据
       receivedOutput.append(data)
       parser.parse(data, cursor, buffer, ...)
   }
   ```

3. **使用write()发送命令**
   ```kotlin
   connection.write("nano test.txt\n")
   ```

## 实现建议

### 方案1: 集成到现有UI测试中

利用现有的 `TerminalViewModel` 来进行集成测试：

```kotlin
@Test
fun testRealNano() = runBlocking {
    val viewModel = TerminalViewModel()
    viewModel.connect(
        host = "example.com",
        port = 22,
        username = "root"
    )
    
    delay(2000) // 等待连接
    
    // 发送nano命令
    viewModel.sendInput("nano test.txt\n")
    delay(1500)
    
    // 验证
    assertTrue(viewModel.buffer.isUsingAlternateScreen())
    
    // 退出
    viewModel.sendInput("\u0018") // Ctrl+X  
    viewModel.sendInput("n") // 不保存
}
```

### 方案2: 手动集成测试脚本

创建一个手动测试脚本，通过UI来验证：

**测试脚本**: `MANUAL_INTEGRATION_TEST.md`

```markdown
# 手动集成测试清单

## 前置条件
1. 启动应用: `.\gradlew.bat :composeApp:run`
2. 连接到服务器: testuser@example.com:22

## 测试1: Nano编辑器
1. 输入命令: `nano /tmp/test.txt`
2. ✅ 检查: 进入alternate screen
3. ✅ 检查: 显示nano标题栏
4. 输入一些文本
5. ✅ 检查: 文本正常显示
6. 按 Enter 插入新行
7. ✅ 检查: 新行正常显示
8. 按 Ctrl+X 退出
9. 输入 'y' 保存
10. 按 Enter 确认文件名
11. ✅ 检查: 返回主屏幕
12. 输入 `cat /tmp/test.txt`
13. ✅ 检查: 文件内容正确

## 测试2: Yazi文件管理器
1. 输入命令: `yazi`
2. ✅ 检查: 进入yazi界面
3. ✅ 检查: 显示文件列表
4. 按 'j' 向下
5. ✅ 检查: 光标移动
6. 按 'k' 向上  
7. ✅ 检查: 光标移动
8. 按 'q' 退出
9. ✅ 检查: 返回主屏幕

## 测试3: Vim编辑器
1. 输入命令: `vim /tmp/test.txt`
2. ✅ 检查: 进入alternate screen
3. 按 'i' 进入插入模式
4. 输入文本
5. ✅ 检查: 文本显示
6. 按 ESC 返回命令模式
7. 输入 ':wq' 保存退出
8. ✅ 检查: 返回主屏幕

...（其他测试）
```

### 方案3: 简化的自动化测试

使用`exec()`方法进行非交互式测试：

```kotlin
@Test  
fun testBasicCommands() = runBlocking {
    val params = SshConnectionParams(
        host = "example.com",
        port = 22,
        user = "testuser",
        authMethod = SshAuthMethod.KeyFile()
    )
    
    val connection = SshConnectionImpl(params)
    connection.connect()
    
    // 测试echo命令
    val echoResult = connection.exec("echo 'Hello Test'")
    assertTrue(echoResult.contains("Hello Test"))
    
    // 测试pwd命令
    val pwdResult = connection.exec("pwd")
    assertTrue(pwdResult.contains("/"))
    
    // 测试文件操作
    connection.exec("echo 'test content' > /tmp/test.txt")
    val catResult = connection.exec("cat /tmp/test.txt")
    assertTrue(catResult.contains("test content"))
    
    connection.close()
}
```

## 当前可用的测试

### ✅ 模拟测试（已完成）
- 位置: `composeApp/src/commonTest/kotlin/cn/hjhw/ssh/terminal/TerminalCompatibilityTest.kt`
- 位置: `composeApp/src/jvmTest/kotlin/cn/hjhw/ssh/terminal/InteractiveToolTest.kt`
- 数量: 71个测试
- 状态: 100%通过
- 说明: 模拟ANSI序列输出，验证终端解析和渲染

### ⏳ 集成测试（待适配）
- 位置: `composeApp/src/jvmTest/kotlin/cn/hjhw/ssh/terminal/RealSshIntegrationTest.kt`
- 状态: 需要API适配
- 说明: 真实SSH环境测试

### ✅ 手动测试（推荐使用）
- 文档: 可按照上述手动测试清单执行
- 优点: 直观、可靠
- 缺点: 需要人工执行

## 下一步行动

### 选项A: 快速验证（推荐）
1. 启动应用
2. 连接到 example.com
3. 按照手动测试清单验证
4. 记录测试结果

### 选项B: 完成集成测试适配
1. 修改 `RealSshIntegrationTest.kt`
2. 使用正确的API
3. 处理Flow数据流
4. 运行自动化测试

### 选项C: 简化自动化测试
1. 只使用 `exec()` 进行非交互式测试
2. 验证命令输出
3. 不测试交互式应用（如nano, vim）

## 测试优先级

### P0 - 核心功能（手动测试即可）
- [x] 基础命令（echo, ls, pwd）
- [x] Nano编辑器
- [x] Vim编辑器
- [x] Less分页器

### P1 - 重要工具（建议手动测试）
- [ ] Yazi文件管理器
- [ ] Git命令
- [ ] Htop监控（如果已安装）

### P2 - 可选工具
- [ ] Tmux
- [ ] 其他TUI应用

## 结论

考虑到：
1. 模拟测试已经100%通过，验证了终端解析逻辑
2. 真实集成测试需要复杂的异步流处理
3. 手动测试可以快速验证实际使用

**建议**:
- **短期**: 使用手动测试验证功能
- **中期**: 实现简化的`exec()`自动化测试
- **长期**: 完整实现交互式集成测试（如需要）

## 文档链接

- [测试计划](TERMINAL_COMPATIBILITY_TEST_PLAN.md)
- [模拟测试结果](TEST_RESULTS_FINAL.md)
- [测试指南](TEST_AUTOMATION_README.md)
- [快速指南](QUICK_TEST_GUIDE.md)

