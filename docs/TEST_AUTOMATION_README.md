# 终端兼容性自动化测试

## 概述

这个测试套件提供了全面的终端兼容性自动化测试，用于验证终端模拟器对各种 ANSI/VT100 序列和交互式工具的支持。

## 测试架构

### 测试层次

```
TerminalCompatibilityTestBase (基类)
├── BasicAnsiTest (基础 ANSI 序列)
├── ScrollingTest (滚动和换行)
├── AlternateScreenTest (备用屏幕)
├── WideCharacterTest (宽字符)
├── CursorSaveRestoreTest (光标保存/恢复)
├── DelayedWrapTest (延迟换行)
└── Interactive Tool Tests (交互式工具)
    ├── NanoEditorTest
    ├── VimEditorTest
    ├── HtopTest
    ├── LessTest
    ├── TmuxTest
    ├── GitDiffTest
    ├── PythonReplTest
    ├── ManPageTest
    ├── CurlProgressTest
    └── TableOutputTest
```

## 测试文件

### 1. `TerminalCompatibilityTest.kt`
**位置**: `composeApp/src/commonTest/kotlin/cn/hjhw/ssh/terminal/`

**内容**:
- 基础测试框架类
- ANSI 序列基本功能测试
- 滚动、光标、颜色等核心功能测试

**测试类**:
- `BasicAnsiTest` - 文本、颜色、光标基础测试
- `ScrollingTest` - 滚动区域和换行测试
- `AlternateScreenTest` - 备用屏幕切换测试
- `WideCharacterTest` - CJK 字符测试
- `CursorSaveRestoreTest` - 光标保存/恢复测试
- `DelayedWrapTest` - 自动换行测试

### 2. `InteractiveToolTest.kt`
**位置**: `composeApp/src/jvmTest/kotlin/cn/hjhw/ssh/terminal/`

**内容**:
- 模拟真实工具输出的测试
- 验证特定工具的显示和交互

**测试类**:
- `NanoEditorTest` - nano 编辑器功能测试
- `VimEditorTest` - vim 编辑器功能测试
- `HtopTest` - htop 监控工具测试
- `LessTest` - less 分页器测试
- `TmuxTest` - tmux 终端复用器测试
- `GitDiffTest` - git diff 彩色输出测试
- `PythonReplTest` - Python REPL 测试
- `ManPageTest` - man 手册页测试
- `CurlProgressTest` - curl 进度条测试
- `TableOutputTest` - 表格化输出测试

## 运行测试

### 运行所有测试
```bash
./gradlew test
```

### 运行特定测试类
```bash
./gradlew test --tests "BasicAnsiTest"
./gradlew test --tests "NanoEditorTest"
```

### 运行特定测试方法
```bash
./gradlew test --tests "BasicAnsiTest.testColoredText"
```

### 查看测试报告
```bash
./gradlew test
# 报告位置: build/reports/tests/test/index.html
```

## 测试覆盖范围

### ✅ 已实现的测试

#### 基础功能 (BasicAnsiTest)
- ✅ 简单文本输出
- ✅ 彩色文本渲染
- ✅ 粗体文本
- ✅ 反显 (Reverse Video)
- ✅ 光标移动
- ✅ 行擦除

#### 滚动功能 (ScrollingTest)
- ✅ 换行处理
- ✅ 滚动区域设置
- ✅ 反向索引 (Reverse Index)

#### 备用屏幕 (AlternateScreenTest)
- ✅ 切换到备用屏幕
- ✅ 从备用屏幕恢复
- ✅ 光标位置保存/恢复

#### 宽字符 (WideCharacterTest)
- ✅ CJK 字符显示
- ✅ 混合宽度字符

#### 光标管理 (CursorSaveRestoreTest)
- ✅ DECSC/DECRC 保存/恢复
- ✅ 备用屏幕独立保存

#### 自动换行 (DelayedWrapTest)
- ✅ 延迟换行机制
- ✅ 光标移动取消换行

#### 交互式工具
- ✅ nano 编辑器
- ✅ vim 编辑器
- ✅ htop 监控
- ✅ less 分页器
- ✅ tmux 终端复用
- ✅ git diff 输出
- ✅ Python REPL
- ✅ man 手册页
- ✅ curl 进度条
- ✅ 表格化输出

## 测试方法说明

### 基类方法

#### `setup(width: Int = 80, height: Int = 24)`
初始化测试环境，创建终端缓冲区、光标和解析器。

#### `sendInput(input: String)`
模拟发送输入到终端，包括文本和 ANSI 序列。

#### `getLineText(lineIndex: Int): String`
获取指定行的文本内容。

#### `getCursorPosition(): Pair<Int, Int>`
获取当前光标位置 (x, y)。

#### `getCellAt(x: Int, y: Int): TerminalCell`
获取指定位置的单元格，用于检查字符属性（颜色、粗体等）。

#### `isInAlternateScreen(): Boolean`
检查是否在备用屏幕模式。

#### `dumpScreen()`
打印当前屏幕内容，用于调试。

## 测试示例

### 示例 1: 测试彩色文本
```kotlin
@Test
fun testColoredText() {
    setup()
    sendInput("\u001b[31mRed Text\u001b[0m")
    
    val cell = getCellAt(0, 0)
    assertTrue(cell.foregroundColor is TerminalColor.Standard)
    assertEquals('R', cell.char)
}
```

### 示例 2: 测试 nano 编辑
```kotlin
@Test
fun testNanoEditing() {
    setup(80, 24)
    
    // 切换到 alternate screen
    sendInput("\u001b[?1049h")
    assertTrue(isInAlternateScreen())
    
    // 输入文本
    sendInput("Hello, nano!")
    assertEquals("Hello, nano!", getLineText(0))
    
    // 退出
    sendInput("\u001b[?1049l")
    assertFalse(isInAlternateScreen())
}
```

### 示例 3: 测试滚动区域
```kotlin
@Test
fun testScrollingRegion() {
    setup(80, 24)
    
    // 设置滚动区域
    sendInput("\u001b[5;20r")
    assertEquals(4, buffer.getScrollRegionTop())
    assertEquals(19, buffer.getScrollRegionBottom())
}
```

## 添加新测试

### 1. 为新工具创建测试类

```kotlin
class MyNewToolTest : TerminalCompatibilityTestBase() {
    
    @Test
    fun testMyToolFeature() {
        setup(80, 24)
        
        // 模拟工具输出
        sendInput("\u001b[?1049h") // 切换到 alternate screen
        sendInput("Tool Output")
        
        // 验证结果
        assertTrue(isInAlternateScreen())
        assertEquals("Tool Output", getLineText(0))
    }
}
```

### 2. 测试特定 ANSI 序列

```kotlin
@Test
fun testNewAnsiSequence() {
    setup()
    
    // 发送 ANSI 序列
    sendInput("\u001b[<params><command>")
    
    // 验证效果
    assertEquals(expectedValue, actualValue)
}
```

## ANSI 序列参考

### 常用序列
- `\u001b[H` - 光标移动到 Home (0, 0)
- `\u001b[<row>;<col>H` - 光标移动到指定位置
- `\u001b[J` - 清除屏幕
- `\u001b[K` - 清除行
- `\u001b[<n>m` - SGR (颜色/样式)
- `\u001b[?1049h` - 切换到备用屏幕
- `\u001b[?1049l` - 恢复主屏幕
- `\u001b7` - 保存光标 (DECSC)
- `\u001b8` - 恢复光标 (DECRC)
- `\u001bM` - 反向索引 (RI)

### SGR 参数
- `0` - 重置所有属性
- `1` - 粗体
- `2` - 暗淡
- `4` - 下划线
- `7` - 反显
- `30-37` - 前景色
- `40-47` - 背景色
- `90-97` - 亮前景色
- `100-107` - 亮背景色

## 调试技巧

### 1. 打印屏幕内容
```kotlin
@Test
fun debugTest() {
    setup()
    sendInput("Some text")
    dumpScreen() // 打印当前屏幕
}
```

### 2. 逐步验证
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

### 3. 检查单元格属性
```kotlin
@Test
fun checkCellProperties() {
    setup()
    sendInput("\u001b[1;31mRed Bold\u001b[0m")
    
    val cell = getCellAt(0, 0)
    assertTrue(cell.bold)
    assertTrue(cell.foregroundColor is TerminalColor.Standard)
}
```

## 持续集成

测试可以集成到 CI/CD 流程中：

```yaml
# .github/workflows/test.yml
name: Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-java@v2
        with:
          java-version: '17'
      - name: Run tests
        run: ./gradlew test
      - name: Upload test results
        uses: actions/upload-artifact@v2
        with:
          name: test-results
          path: build/reports/tests/
```

## 性能测试

对于性能敏感的场景，可以添加基准测试：

```kotlin
@Test
fun benchmarkLargeOutput() {
    setup(200, 100)
    
    val startTime = System.currentTimeMillis()
    
    // 发送大量数据
    repeat(10000) {
        sendInput("Line $it\n")
    }
    
    val duration = System.currentTimeMillis() - startTime
    assertTrue(duration < 1000, "Processing took too long: ${duration}ms")
}
```

## 覆盖率

运行测试覆盖率分析：

```bash
./gradlew test jacocoTestReport
# 报告位置: build/reports/jacoco/test/html/index.html
```

## 未来改进

### 待实现的测试
- [ ] 鼠标事件处理
- [ ] 窗口大小调整
- [ ] 性能压力测试
- [ ] Unicode 边缘情况
- [ ] 嵌套终端会话
- [ ] 异步输出处理
- [ ] 真实 SSH 连接集成测试

### 增强功能
- [ ] 可视化测试结果
- [ ] 自动生成测试报告
- [ ] 对比不同终端模拟器
- [ ] 性能回归检测
- [ ] 模糊测试 (Fuzzing)

## 贡献指南

添加新测试时：
1. 继承 `TerminalCompatibilityTestBase`
2. 使用描述性的测试方法名
3. 添加清晰的注释说明测试目的
4. 验证多种边缘情况
5. 更新本文档

## 许可证

与主项目相同

