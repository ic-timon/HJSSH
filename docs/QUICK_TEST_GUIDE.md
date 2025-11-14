# 快速测试指南

## 🚀 快速开始

### 运行所有测试
```bash
.\gradlew.bat :composeApp:allTests
```

### 查看测试报告
```bash
# Windows
start composeApp\build\reports\tests\allTests\index.html

# macOS/Linux
open composeApp/build/reports/tests/allTests/index.html
```

---

## 📋 常用测试命令

### 基础功能测试
```bash
# 基础 ANSI 序列
.\gradlew.bat :composeApp:jvmTest --tests "BasicAnsiTest"

# 滚动和换行
.\gradlew.bat :composeApp:jvmTest --tests "ScrollingTest"

# 备用屏幕
.\gradlew.bat :composeApp:jvmTest --tests "AlternateScreenTest"

# 光标保存/恢复
.\gradlew.bat :composeApp:jvmTest --tests "CursorSaveRestoreTest"

# 宽字符
.\gradlew.bat :composeApp:jvmTest --tests "WideCharacterTest"

# 延迟换行
.\gradlew.bat :composeApp:jvmTest --tests "DelayedWrapTest"
```

### 工具兼容性测试
```bash
# Nano 编辑器
.\gradlew.bat :composeApp:jvmTest --tests "NanoEditorTest"

# Vim 编辑器
.\gradlew.bat :composeApp:jvmTest --tests "VimEditorTest"

# Htop 监控
.\gradlew.bat :composeApp:jvmTest --tests "HtopTest"

# Less 分页器
.\gradlew.bat :composeApp:jvmTest --tests "LessTest"

# Tmux 终端复用
.\gradlew.bat :composeApp:jvmTest --tests "TmuxTest"

# Git Diff
.\gradlew.bat :composeApp:jvmTest --tests "GitDiffTest"

# Python REPL
.\gradlew.bat :composeApp:jvmTest --tests "PythonReplTest"

# Man 手册页
.\gradlew.bat :composeApp:jvmTest --tests "ManPageTest"

# Curl 进度条
.\gradlew.bat :composeApp:jvmTest --tests "CurlProgressTest"

# 表格输出
.\gradlew.bat :composeApp:jvmTest --tests "TableOutputTest"
```

### 运行单个测试方法
```bash
.\gradlew.bat :composeApp:jvmTest --tests "BasicAnsiTest.testColoredText"
.\gradlew.bat :composeApp:jvmTest --tests "NanoEditorTest.testNanoStartup"
```

---

## 🔍 测试类型说明

### 基础功能测试
验证终端核心功能的正确性：
- ✅ 文本显示
- ✅ ANSI 颜色
- ✅ SGR 属性
- ✅ 光标控制
- ✅ 屏幕操作

### 兼容性测试
模拟真实工具的输出，验证兼容性：
- ✅ 编辑器（nano, vim）
- ✅ 监控工具（htop）
- ✅ 分页器（less）
- ✅ 终端复用器（tmux）
- ✅ 开发工具（git）
- ✅ 交互式环境（Python REPL）

---

## 📊 测试结果解读

### 成功的测试
```
✅ BasicAnsiTest > testColoredText[jvm] PASSED
```
- 功能正常工作
- 符合预期行为

### 失败的测试
```
❌ ScrollingTest > testNewLine[jvm] FAILED
```
- 需要检查失败原因
- 可能是实现问题或测试问题

### 查看详细错误
1. 打开测试报告 HTML
2. 找到失败的测试
3. 查看错误堆栈和消息
4. 分析根本原因

---

## 🛠️ 调试技巧

### 1. 启用详细日志
在测试类中添加 `dumpScreen()`:

```kotlin
@Test
fun myTest() {
    setup()
    sendInput("Some text")
    dumpScreen() // 打印当前屏幕内容
}
```

### 2. 检查单个单元格
```kotlin
val cell = getCellAt(0, 0)
println("Cell: char='${cell.char}', fg=${cell.foregroundColor}, bold=${cell.bold}")
```

### 3. 逐步验证
```kotlin
@Test
fun stepByStepTest() {
    setup()
    
    sendInput("Step 1")
    assertEquals("Step 1", getLineText(0))
    
    sendInput("\n")
    assertEquals(1, getCursorPosition().second)
    
    sendInput("Step 2")
    assertEquals("Step 2", getLineText(1))
}
```

### 4. 隔离问题
如果测试失败，尝试：
- 简化输入
- 减少步骤
- 单独测试每个功能
- 对比预期和实际输出

---

## 📝 添加新测试

### 1. 创建测试类
```kotlin
class MyNewTest : TerminalCompatibilityTestBase() {
    
    @Test
    fun testMyFeature() {
        setup(80, 24)
        
        // 发送输入
        sendInput("Hello")
        
        // 验证结果
        assertEquals("Hello", getLineText(0))
    }
}
```

### 2. 测试 ANSI 序列
```kotlin
@Test
fun testMyAnsiSequence() {
    setup()
    
    // 发送 ANSI 序列
    sendInput("\u001b[31mRed Text\u001b[0m")
    
    // 检查颜色
    val cell = getCellAt(0, 0)
    assertTrue(cell.foregroundColor is TerminalColor.Standard)
}
```

### 3. 模拟工具输出
```kotlin
@Test
fun testMyTool() {
    setup(80, 24)
    
    // 切换到 alternate screen
    sendInput("\u001b[?1049h")
    assertTrue(isInAlternateScreen())
    
    // 模拟工具输出
    sendInput("\u001b[H") // Home
    sendInput("Tool Output")
    
    // 验证
    assertEquals("Tool Output", getLineText(0))
}
```

---

## 🎯 测试最佳实践

### DO ✅
- 使用描述性的测试名称
- 每个测试只测试一个功能
- 使用 `setup()` 初始化环境
- 添加清晰的注释
- 验证多个方面（位置、内容、属性）

### DON'T ❌
- 不要依赖测试执行顺序
- 不要使用硬编码的魔法数字
- 不要忽略边缘情况
- 不要编写过于复杂的测试
- 不要忘记清理状态

---

## 🔄 持续集成

### 本地运行
```bash
# 运行并生成报告
.\gradlew.bat :composeApp:allTests

# 查看结果
start composeApp\build\reports\tests\allTests\index.html
```

### CI/CD 集成
```yaml
# .github/workflows/test.yml
name: Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-java@v2
        with:
          java-version: '17'
      - name: Run tests
        run: .\gradlew.bat :composeApp:allTests
      - name: Upload results
        uses: actions/upload-artifact@v2
        with:
          name: test-results
          path: composeApp/build/reports/tests/
```

---

## 📈 性能测试

### 基准测试
```kotlin
@Test
fun benchmarkLargeOutput() {
    setup(200, 100)
    
    val startTime = System.currentTimeMillis()
    
    repeat(10000) {
        sendInput("Line $it\n")
    }
    
    val duration = System.currentTimeMillis() - startTime
    println("Processed 10000 lines in ${duration}ms")
    assertTrue(duration < 1000, "Too slow: ${duration}ms")
}
```

---

## 🐛 常见问题

### Q: 测试失败但实际使用正常？
A: 可能是测试预期不准确，检查并调整断言。

### Q: 如何测试实际 SSH 连接？
A: 目前框架模拟终端，真实 SSH 测试需要额外的集成测试。

### Q: 测试运行很慢？
A: 检查是否有死循环或大量数据处理，优化测试逻辑。

### Q: 如何测试鼠标事件？
A: 目前框架主要测试键盘和显示，鼠标事件需要额外实现。

---

## 📚 更多资源

- [测试计划](TERMINAL_COMPATIBILITY_TEST_PLAN.md)
- [测试结果](TEST_RESULTS_SUMMARY.md)
- [自动化文档](TEST_AUTOMATION_README.md)
- [项目计划](plan.md)

---

**快速链接**:
- [测试报告](composeApp/build/reports/tests/allTests/index.html)
- [源代码](composeApp/src/commonTest/kotlin/cn/hjhw/ssh/terminal/)

**获取帮助**: 查看测试失败的详细日志和堆栈跟踪

