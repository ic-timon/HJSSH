# SSH集成测试 - 快速开始

## 🚀 快速开始（5分钟）

### 1. 确认SSH连接
```bash
# 测试SSH连接
ssh testuser@example.com

# 如果可以连接，按 Ctrl+D 退出
```

### 2. 运行基础测试
```bash
# 运行基础命令测试（最快）
.\gradlew.bat :composeApp:jvmTest --tests "BasicCommandTest"
```

### 3. 运行Yazi测试
```bash
# 测试新增的yazi文件管理器
.\gradlew.bat :composeApp:jvmTest --tests "YaziRealTest"
```

### 4. 运行所有集成测试
```bash
# 运行所有真实SSH测试
.\gradlew.bat :composeApp:jvmTest --tests "*RealTest"
```

---

## 📋 测试清单

### ✅ 必需工具测试（服务器已安装）
```bash
# Nano编辑器
.\gradlew.bat :composeApp:jvmTest --tests "NanoRealTest"

# Vim编辑器  
.\gradlew.bat :composeApp:jvmTest --tests "VimRealTest"

# Less分页器
.\gradlew.bat :composeApp:jvmTest --tests "LessRealTest"

# Git命令
.\gradlew.bat :composeApp:jvmTest --tests "GitRealTest"

# Yazi文件管理器 ⭐
.\gradlew.bat :composeApp:jvmTest --tests "YaziRealTest"
```

### ⬜ 可选工具测试（自动跳过如果未安装）
```bash
# Htop监控
.\gradlew.bat :composeApp:jvmTest --tests "HtopRealTest"

# Tmux复用器
.\gradlew.bat :composeApp:jvmTest --tests "TmuxRealTest"
```

---

## 🎯 测试套件说明

### BasicCommandTest (4个测试)
最基础的测试，验证SSH连接和命令执行。

**测试内容**:
- Echo命令
- Pwd命令
- Ls彩色输出
- Whoami命令

**预期时间**: ~15秒

### NanoRealTest (2个测试)
测试nano编辑器的完整功能。

**测试内容**:
- 启动和退出
- 编辑和保存文件

**预期时间**: ~20秒

### VimRealTest (2个测试)
测试vim编辑器的功能。

**测试内容**:
- 启动和退出
- 编辑和保存文件

**预期时间**: ~20秒

### YaziRealTest ⭐ (2个测试)
测试yazi文件管理器（新增）。

**测试内容**:
- 启动和退出
- 文件导航（j/k键）

**预期时间**: ~25秒

**关于Yazi**:
- 现代化的终端文件管理器
- 使用Rust编写，速度快
- 支持文件预览和搜索
- 类似ranger但更现代化

### LessRealTest (1个测试)
测试less分页器。

**测试内容**:
- 文件分页显示

**预期时间**: ~10秒

### GitRealTest (1个测试)
测试git命令的彩色输出。

**测试内容**:
- Git diff彩色输出
- ANSI颜色代码

**预期时间**: ~15秒

### HtopRealTest (1个测试)
测试htop系统监控（可选）。

**测试内容**:
- 系统监控界面
- 实时数据显示

**预期时间**: ~10秒（如果已安装）

### TmuxRealTest (1个测试)
测试tmux终端复用器（可选）。

**测试内容**:
- 会话创建和分离

**预期时间**: ~15秒（如果已安装）

---

## 📊 预期结果

### 全部测试通过
```
BUILD SUCCESSFUL in 2m 30s

Tests: 14 passed
```

### 部分工具未安装
```
BUILD SUCCESSFUL in 1m 45s

Tests: 12 passed, 2 skipped

[SKIP] htop not installed
[SKIP] tmux not installed
```

---

## 🐛 故障排查

### 问题1: 连接失败
```
Error: Failed to connect to SSH server
```

**解决方案**:
```bash
# 1. 测试SSH连接
ssh testuser@example.com

# 2. 检查网络
ping example.com

# 3. 检查SSH配置
type $env:USERPROFILE\.ssh\config
```

### 问题2: 认证失败
```
Error: Authentication failed
```

**解决方案**:
```bash
# 方法1: 使用密钥认证（推荐）
ssh-copy-id testuser@example.com

# 方法2: 检查密码
ssh testuser@example.com
```

### 问题3: 测试超时
```
Test timed out
```

**解决方案**:
- 检查网络延迟: `ping example.com`
- 增加超时设置（在代码中）
- 确认服务器负载正常

### 问题4: Yazi未安装
```
[SKIP] yazi not installed, skipping test
```

**解决方案**:
```bash
# 在服务器上安装yazi
ssh testuser@example.com

# 使用cargo安装
cargo install --locked yazi-fm

# 或从发布页下载
# https://github.com/sxyazi/yazi/releases
```

---

## 📝 测试日志

### 查看详细日志
测试运行时会输出详细日志：

```
=== [SSH Test] Connecting to testuser@example.com:22 ===
[SSH] Connected successfully
[CMD] Executing: echo 'Hello from SSH test'
[OUT] Received 24 bytes
[SSH] Disconnected
=== [SSH Test] Disconnected ===
```

### 查看测试报告
```bash
# 生成HTML报告
.\gradlew.bat :composeApp:jvmTest --tests "*RealTest"

# 打开报告
start composeApp\build\reports\tests\jvmTest\index.html
```

---

## 🔧 高级用法

### 只运行特定测试方法
```bash
# 只测试yazi启动
.\gradlew.bat :composeApp:jvmTest --tests "YaziRealTest.testYaziStartupAndExit"

# 只测试yazi导航
.\gradlew.bat :composeApp:jvmTest --tests "YaziRealTest.testYaziNavigation"
```

### 运行并显示详细输出
```bash
.\gradlew.bat :composeApp:jvmTest --tests "*RealTest" --info
```

### 运行单个测试类
```bash
# 只运行基础命令测试
.\gradlew.bat :composeApp:jvmTest --tests "BasicCommandTest.*"
```

---

## 📈 性能基准

### 预期执行时间

| 测试套件 | 测试数 | 预期时间 |
|---------|--------|----------|
| BasicCommandTest | 4 | ~15秒 |
| NanoRealTest | 2 | ~20秒 |
| VimRealTest | 2 | ~20秒 |
| YaziRealTest | 2 | ~25秒 |
| LessRealTest | 1 | ~10秒 |
| GitRealTest | 1 | ~15秒 |
| HtopRealTest | 1 | ~10秒 |
| TmuxRealTest | 1 | ~15秒 |
| **总计** | **14** | **~2分钟** |

*注意：实际时间取决于网络延迟和服务器响应速度*

---

## 🎓 下一步

### 验证完成后
1. ✅ 查看测试报告
2. ✅ 确认所有测试通过
3. ✅ 查看[详细指南](REAL_SSH_INTEGRATION_TEST_GUIDE.md)

### 添加更多测试
1. 查看[测试计划](TERMINAL_COMPATIBILITY_TEST_PLAN.md)
2. 选择要测试的工具
3. 参考现有测试编写新测试

### 集成到CI/CD
1. 配置SSH密钥
2. 设置GitHub Actions
3. 自动运行集成测试

---

## 📞 获取帮助

- 详细文档: [REAL_SSH_INTEGRATION_TEST_GUIDE.md](REAL_SSH_INTEGRATION_TEST_GUIDE.md)
- 测试计划: [TERMINAL_COMPATIBILITY_TEST_PLAN.md](TERMINAL_COMPATIBILITY_TEST_PLAN.md)
- 模拟测试: [TEST_RESULTS_FINAL.md](TEST_RESULTS_FINAL.md)

---

**快速链接**:
- [测试配置](composeApp/src/jvmTest/resources/ssh-test-config.properties)
- [测试代码](composeApp/src/jvmTest/kotlin/cn/hjhw/ssh/terminal/RealSshIntegrationTest.kt)
- [SSH配置](~/.ssh/config)

**准备好了吗？运行你的第一个集成测试！** 🚀

```bash
.\gradlew.bat :composeApp:jvmTest --tests "BasicCommandTest"
```

