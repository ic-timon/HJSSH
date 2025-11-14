# 真实SSH环境集成测试指南

## 概述

这套集成测试在真实的SSH服务器上执行，验证终端模拟器在实际环境中的表现。

## 测试服务器配置

### SSH连接信息
```
Host: myserver
HostName: example.com
User: testuser
Port: 22
```

**配置文件**: `~/.ssh/config`

## 测试架构

### 测试层次结构
```
RealSshIntegrationTestBase (基类)
├── BasicCommandTest (基础命令)
├── NanoRealTest (Nano编辑器)
├── VimRealTest (Vim编辑器)
├── LessRealTest (Less分页器)
├── HtopRealTest (Htop监控)
├── YaziRealTest (Yazi文件管理器) ⭐ 新增
├── GitRealTest (Git命令)
└── TmuxRealTest (Tmux复用器)
```

### 基类功能 (`RealSshIntegrationTestBase`)

#### 自动化管理
- ✅ 自动建立SSH连接
- ✅ 自动解析ANSI序列
- ✅ 自动清理测试环境
- ✅ 自动断开连接

#### 核心方法
```kotlin
connectToServer()           // 连接到SSH服务器
sendCommandAndWait()        // 发送命令并等待输出
getBufferText()            // 获取终端缓冲区文本
assertOutputContains()      // 断言输出包含特定内容
assertOutputNotContains()   // 断言输出不包含特定内容
```

---

## 测试套件详情

### 1. BasicCommandTest - 基础命令测试

验证基本的shell命令执行。

**测试用例**:
- ✅ `testEcho` - Echo命令
- ✅ `testPwd` - 工作目录
- ✅ `testLs` - 列出文件（彩色输出）
- ✅ `testWhoami` - 当前用户

**验证内容**:
- 命令输出正确性
- ANSI颜色代码
- 基础终端交互

### 2. NanoRealTest - Nano编辑器

在真实服务器上测试nano编辑器。

**测试用例**:
- ✅ `testNanoStartupAndExit` - 启动和退出
- ✅ `testNanoEditing` - 编辑和保存文件

**验证内容**:
- Alternate screen buffer切换
- 文件编辑功能
- 保存和退出流程
- 界面显示正确性

**操作流程**:
```
1. 启动 nano
2. 检查 alternate screen
3. 输入文本
4. Ctrl+X 退出
5. 确认保存
6. 验证文件内容
```

### 3. VimRealTest - Vim编辑器

测试vim编辑器的完整功能。

**测试用例**:
- ✅ `testVimStartupAndExit` - 启动和退出
- ✅ `testVimEditing` - 编辑和保存

**验证内容**:
- Alternate screen buffer
- 插入模式
- 命令模式
- 保存退出 (:wq)

**操作流程**:
```
1. 启动 vim
2. 按 i 进入插入模式
3. 输入文本
4. ESC 返回命令模式
5. :wq 保存退出
6. 验证文件
```

### 4. LessRealTest - Less分页器

测试less分页器的显示和导航。

**测试用例**:
- ✅ `testLessDisplay` - 文件分页显示

**验证内容**:
- Alternate screen buffer
- 内容显示
- 退出功能

### 5. HtopRealTest - Htop系统监控

测试htop系统监控工具。

**测试用例**:
- ✅ `testHtopDisplay` - 系统监控界面

**验证内容**:
- 实时数据显示
- CPU/内存信息
- 进程列表
- 界面刷新

**特性**:
- 自动跳过（如果未安装）
- 彩色显示验证

### 6. YaziRealTest - Yazi文件管理器 ⭐ 新增

测试yazi现代文件管理器。

**测试用例**:
- ✅ `testYaziStartupAndExit` - 启动和退出
- ✅ `testYaziNavigation` - 文件导航

**验证内容**:
- TUI界面显示
- Alternate screen buffer
- 键盘导航（j/k）
- 文件列表显示

**操作流程**:
```
1. 创建测试目录结构
2. 启动 yazi
3. 验证界面显示
4. 测试导航键
   - j: 向下
   - k: 向上
5. q 退出
6. 清理测试文件
```

**关于Yazi**:
- 现代化的终端文件管理器
- 快速且易用
- 支持文件预览
- 使用Rust编写
- GitHub: https://github.com/sxyazi/yazi

### 7. GitRealTest - Git命令

测试git的彩色输出。

**测试用例**:
- ✅ `testGitColoredOutput` - Git diff彩色输出

**验证内容**:
- ANSI颜色代码
- Diff格式化
- 命令执行

### 8. TmuxRealTest - Tmux终端复用器

测试tmux会话管理。

**测试用例**:
- ✅ `testTmuxSession` - 会话创建和分离

**验证内容**:
- 会话创建
- 界面显示
- 分离功能

---

## 运行测试

### 前提条件

1. **SSH访问**
   - 确保可以SSH到服务器: `ssh testuser@example.com`
   - 最好配置SSH密钥认证
   - 确认 `~/.ssh/config` 中的配置正确

2. **服务器环境**
   - Linux系统（已验证）
   - 安装必要工具（部分可选）

### 必需工具
- ✅ nano
- ✅ vim
- ✅ less
- ✅ git

### 可选工具（测试会自动跳过如果未安装）
- ⬜ htop
- ⭐ yazi
- ⬜ tmux

### 运行所有集成测试
```bash
.\gradlew.bat :composeApp:jvmTest --tests "*RealTest"
```

### 运行特定测试套件
```bash
# 基础命令
.\gradlew.bat :composeApp:jvmTest --tests "BasicCommandTest"

# Nano编辑器
.\gradlew.bat :composeApp:jvmTest --tests "NanoRealTest"

# Vim编辑器
.\gradlew.bat :composeApp:jvmTest --tests "VimRealTest"

# Yazi文件管理器
.\gradlew.bat :composeApp:jvmTest --tests "YaziRealTest"

# Htop监控
.\gradlew.bat :composeApp:jvmTest --tests "HtopRealTest"
```

### 运行单个测试
```bash
.\gradlew.bat :composeApp:jvmTest --tests "YaziRealTest.testYaziStartupAndExit"
```

---

## 测试流程

### 自动化流程

1. **连接阶段**
   ```
   [Setup] 初始化终端缓冲区
   [SSH] 连接到 testuser@example.com:22
   [SSH] 等待认证
   [SSH] 等待初始提示符
   [Ready] 测试环境就绪
   ```

2. **执行阶段**
   ```
   [CMD] 发送测试命令
   [Wait] 等待输出
   [Parse] 解析ANSI序列
   [Buffer] 更新终端缓冲区
   [Assert] 验证输出
   ```

3. **清理阶段**
   ```
   [Cleanup] 删除临时文件
   [Exit] 发送exit命令
   [Disconnect] 关闭SSH连接
   [Done] 测试完成
   ```

---

## 测试配置

### 配置文件
**路径**: `composeApp/src/jvmTest/resources/ssh-test-config.properties`

```properties
ssh.host=example.com
ssh.user=testuser
ssh.port=22

test.command.timeout=5000
test.connection.timeout=10000

test.skip.if.not.installed=true
test.cleanup.after.run=true
```

### 自定义配置

如需更改配置，编辑配置文件或在代码中修改：

```kotlin
// 在测试类中覆盖
protected override val sshHost = "your.server.com"
protected override val sshUser = "youruser"
protected override val sshPort = 22
```

---

## 故障排查

### 常见问题

#### 1. 连接失败
```
Error: Failed to connect to SSH server
```

**解决方案**:
- 检查网络连接
- 验证SSH配置
- 确认服务器运行状态
- 检查防火墙设置

#### 2. 工具未安装
```
[SKIP] yazi not installed, skipping test
```

**解决方案**:
- 测试会自动跳过（如果配置了跳过选项）
- 或者在服务器上安装工具:
  ```bash
  # Yazi
  cargo install --locked yazi-fm
  # 或使用包管理器
  
  # Htop
  apt-get install htop  # Debian/Ubuntu
  yum install htop      # CentOS/RHEL
  
  # Tmux
  apt-get install tmux
  ```

#### 3. 超时问题
```
Test timed out after 5000ms
```

**解决方案**:
- 增加超时配置
- 检查网络延迟
- 确认命令正常执行

#### 4. Alternate Screen未切换
```
Assertion failed: Should use alternate screen
```

**解决方案**:
- 确认TERM环境变量正确
- 增加等待时间
- 检查应用是否正常启动

---

## 最佳实践

### 编写新测试

1. **继承基类**
   ```kotlin
   class MyToolRealTest : RealSshIntegrationTestBase() {
       @Test
       fun testMyTool() = runBlocking {
           connectToServer()
           // 测试逻辑
       }
   }
   ```

2. **检查工具是否安装**
   ```kotlin
   val checkOutput = sendCommandAndWait("which mytool", 500)
   if (!checkOutput.contains("/mytool")) {
       println("[SKIP] mytool not installed")
       return@runBlocking
   }
   ```

3. **清理测试环境**
   ```kotlin
   // 测试前
   sendCommandAndWait("rm -rf /tmp/test_dir", 500)
   
   // 测试后
   sendCommandAndWait("rm -rf /tmp/test_dir", 500)
   ```

4. **合理的等待时间**
   ```kotlin
   delay(1500)  // 启动应用
   delay(300)   // 简单操作
   delay(2000)  // 复杂操作
   ```

5. **验证多个方面**
   ```kotlin
   // 检查 alternate screen
   assertTrue(buffer.isUsingAlternateScreen())
   
   // 检查输出内容
   assertOutputContains("expected text")
   
   // 检查缓冲区内容
   val bufferText = getBufferText()
   assertTrue(bufferText.contains("interface"))
   ```

---

## 性能考虑

### 测试执行时间
- 单个测试: 3-10秒
- 完整套件: 1-2分钟
- 取决于网络延迟和服务器响应

### 优化建议
1. 并行运行独立测试（需配置）
2. 重用SSH连接（当前每个测试独立）
3. 缓存工具检测结果
4. 减少不必要的等待时间

---

## 安全考虑

### 测试安全
- ✅ 使用临时目录 (`/tmp`)
- ✅ 自动清理测试文件
- ✅ 不修改系统配置
- ✅ 不需要sudo权限（大部分测试）

### SSH安全
- ✅ 建议使用密钥认证
- ✅ 避免在代码中硬编码密码
- ✅ 使用 ~/.ssh/config 管理连接
- ✅ 限制测试服务器权限

---

## 测试报告

### 生成报告
```bash
.\gradlew.bat :composeApp:jvmTest --tests "*RealTest"
start composeApp\build\reports\tests\jvmTest\index.html
```

### 报告内容
- 测试执行时间
- 通过/失败统计
- 详细错误信息
- SSH连接日志
- 命令输出

---

## CI/CD集成

### GitHub Actions示例
```yaml
name: SSH Integration Tests
on: [push, pull_request]

jobs:
  integration-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Setup SSH
        run: |
          mkdir -p ~/.ssh
          echo "${{ secrets.SSH_PRIVATE_KEY }}" > ~/.ssh/id_rsa
          chmod 600 ~/.ssh/id_rsa
          echo "Host myserver" >> ~/.ssh/config
          echo "  HostName example.com" >> ~/.ssh/config
          echo "  User testuser" >> ~/.ssh/config
          echo "  Port 22" >> ~/.ssh/config
      
      - name: Run Integration Tests
        run: ./gradlew :composeApp:jvmTest --tests "*RealTest"
```

---

## 下一步

### 待添加的测试
- [ ] mc (Midnight Commander)
- [ ] ranger (文件管理器)
- [ ] btop (系统监控)
- [ ] lazygit (Git TUI)
- [ ] ncdu (磁盘使用分析)

### 增强功能
- [ ] 截图对比
- [ ] 性能基准测试
- [ ] 压力测试
- [ ] 多服务器测试
- [ ] Docker容器测试

---

## 参考资料

- [SSH配置文件](~/.ssh/config)
- [测试配置](composeApp/src/jvmTest/resources/ssh-test-config.properties)
- [测试计划](TERMINAL_COMPATIBILITY_TEST_PLAN.md)
- [Yazi文档](https://github.com/sxyazi/yazi)

---

**最后更新**: 2025年11月14日  
**测试服务器**: myserver (example.com)  
**版本**: v1.0

