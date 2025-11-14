# HJSSH 项目结构与代码规范

本文档描述 HJSSH 项目的整体架构、代码组织和编码规范。

## 📂 项目结构

### 整体架构
```
HJSSH/
├── composeApp/              # 主应用模块 (Kotlin Multiplatform)
│   ├── build.gradle.kts     # 构建配置
│   ├── detekt.yml          # 代码检查配置
│   └── src/
│       ├── commonMain/      # 跨平台通用代码
│       ├── jvmMain/         # JVM 平台特定实现
│       ├── commonTest/      # 跨平台单元测试
│       └── jvmTest/         # JVM 集成测试
│
├── docs/                    # 项目文档
├── gradle/                  # Gradle 配置
├── build.gradle.kts         # 根项目构建配置
├── settings.gradle.kts      # 项目设置
└── README.md                # 项目说明
```

### CommonMain 模块结构

#### 📦 connection 包 - SSH 连接接口
```
cn.hjhw.ssh.connection/
├── SshConnection.kt         # SSH 连接接口 (expect)
├── SshConnectionParams.kt   # 连接参数数据类
└── SshAuthParams.kt         # 认证参数密封类
```

**职责**:
- 定义 SSH 连接的跨平台接口
- 管理连接参数和认证信息

#### 📦 terminal 包 - 终端核心逻辑
```
cn.hjhw.ssh.terminal/
├── AnsiParser.kt            # ANSI/VT100 转义序列解析器 ⭐
├── TerminalBuffer.kt        # 终端缓冲区管理 ⭐
├── CursorState.kt           # 光标状态管理
├── TerminalCell.kt          # 终端单元格数据结构
├── TerminalLine.kt          # 终端行数据结构
├── TerminalColor.kt         # 颜色系统
├── CellStyle.kt             # 单元格样式
├── ControlSequence.kt       # 控制序列定义
├── KeyMapper.kt             # 键盘映射
├── TerminalImage.kt         # 图像数据结构
├── KittyGraphics.kt         # Kitty Graphics Protocol
└── ImageDecoder.kt          # 图像解码 (expect)
```

**职责**:
- 解析 ANSI 转义序列
- 管理终端内容和状态
- 处理键盘输入映射
- 支持图形协议

**核心类**:
- **AnsiParser**: 状态机解析器，处理所有 ANSI 序列
- **TerminalBuffer**: 双缓冲区设计，支持主屏幕和交替屏幕
- **CursorState**: 管理光标位置、样式和状态栈

#### 📦 ui 包 - UI 层
```
cn.hjhw.ssh.ui/
├── TerminalView.kt          # 终端渲染视图 (Compose)
├── TerminalViewModel.kt     # 终端视图模型 ⭐
└── TextRenderer.kt          # 文本渲染器 (expect)
```

**职责**:
- Compose UI 组件
- MVVM 架构中的 ViewModel
- 跨平台文本渲染抽象

### JvmMain 模块结构

#### JVM 平台实现
```
cn.hjhw.ssh.connection/
└── SshConnectionImpl.kt     # SSH 连接实现 (SSHJ) ⭐

cn.hjhw.ssh.terminal/
└── ImageDecoder.jvm.kt      # JVM 图像解码实现

cn.hjhw.ssh.ui/
└── TextRenderer.jvm.kt      # JVM 文本渲染实现 (Skia)
```

**职责**:
- 使用 SSHJ 库实现 SSH 连接
- 使用 Java ImageIO 解码图像
- 使用 Skia 渲染文本

## 🏗️ 架构设计

### MVVM 架构

```
┌─────────────────────────────────────────────────────────┐
│                         UI Layer                         │
│                                                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │           TerminalView (Compose)                 │  │
│  │  - 渲染终端内容                                   │  │
│  │  - 处理用户输入                                   │  │
│  │  - 绘制光标和图像                                 │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                          ↕ (State & Events)
┌─────────────────────────────────────────────────────────┐
│                     ViewModel Layer                      │
│                                                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │         TerminalViewModel                        │  │
│  │  - 管理终端状态                                   │  │
│  │  - 协调数据流                                     │  │
│  │  - 处理业务逻辑                                   │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                          ↕ (Data Flow)
┌─────────────────────────────────────────────────────────┐
│                      Domain Layer                        │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ AnsiParser   │  │TerminalBuffer│  │  CursorState │  │
│  │ - 解析序列   │  │ - 管理内容   │  │  - 管理光标  │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
                          ↕ (SSH Protocol)
┌─────────────────────────────────────────────────────────┐
│                      Data Layer                          │
│                                                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │         SshConnectionImpl (SSHJ)                 │  │
│  │  - SSH 连接                                       │  │
│  │  - PTY 管理                                       │  │
│  │  - 数据传输                                       │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 数据流

#### 输出流 (SSH → UI)
```
SSH 服务器
    ↓ (字节流)
SshConnectionImpl.observeOutput()
    ↓ (Flow<String>, UTF-8 解码)
TerminalViewModel.observeOutput()
    ↓ (逐字符处理)
AnsiParser.parse()
    ↓ (解析 ANSI 序列)
    ├─→ onText() → TerminalBuffer.writeChar()
    ├─→ onControlSequence() → 各种控制操作
    └─→ onResponse() → SSH 服务器响应
    ↓ (更新状态)
TerminalBuffer (缓冲区更新)
    ↓ (触发 UI 刷新)
TerminalView.drawTerminalContent()
    ↓ (Compose 渲染)
屏幕显示
```

#### 输入流 (UI → SSH)
```
用户键盘输入
    ↓ (KeyEvent)
TerminalView.onKeyEvent()
    ↓ (传递给 ViewModel)
TerminalViewModel.handleKeyInput()
    ↓ (键盘映射)
KeyMapper.mapKey()
    ↓ (转换为 ANSI 序列)
SshConnectionImpl.sendInput()
    ↓ (字节流)
SSH 服务器
```

## 📝 代码规范

### 命名约定

#### 类名
- **PascalCase**: `AnsiParser`, `TerminalBuffer`
- **接口**: 使用名词，如 `SshConnection`
- **实现类**: 添加 `Impl` 后缀，如 `SshConnectionImpl`

#### 函数名
- **camelCase**: `parseInput`, `handleKeyEvent`
- **布尔函数**: 使用 `is`/`has` 前缀，如 `isConnected`, `hasResized`
- **回调函数**: 使用 `on` 前缀，如 `onText`, `onControlSequence`

#### 变量名
- **camelCase**: `cursorX`, `terminalWidth`
- **常量**: 使用 `const val`，全大写，如 `MAX_SCROLLBACK_SIZE`
- **私有成员**: 建议使用描述性名称，避免 `_` 前缀

#### 包名
- 全小写: `cn.hjhw.ssh.terminal`
- 使用点分隔: `cn.hjhw.ssh.ui`

### 文档注释

#### 类文档
```kotlin
/**
 * 类的简短描述
 *
 * 详细说明类的职责和用途。
 *
 * ### 主要功能
 * - 功能 1
 * - 功能 2
 *
 * ### 使用示例
 * ```kotlin
 * val example = Example()
 * example.doSomething()
 * ```
 *
 * @param param1 参数说明
 * @see RelatedClass 相关类
 */
class Example(param1: String) {
    // ...
}
```

#### 函数文档
```kotlin
/**
 * 函数简短描述
 *
 * 详细说明函数的行为和副作用。
 *
 * @param input 输入参数说明
 * @return 返回值说明
 * @throws Exception 异常说明
 */
fun doSomething(input: String): Result {
    // ...
}
```

#### 行内注释
```kotlin
// 简短注释：解释为什么这样做，而不是怎么做
val result = complexCalculation()  // 需要缓存结果以提高性能
```

### 代码组织

#### 类成员顺序
1. 伴生对象 (companion object)
2. 属性 (properties)
   - 公共属性
   - 私有属性
3. 初始化块 (init)
4. 构造函数 (secondary constructors)
5. 公共函数
6. 私有函数
7. 嵌套类/对象

#### 导入顺序
1. 标准库导入
2. 第三方库导入
3. 项目内部导入
4. 按字母顺序排列

### Kotlin 最佳实践

#### 使用数据类
```kotlin
// ✅ 好的实践
data class TerminalCell(
    val char: Char = ' ',
    val foreground: TerminalColor = TerminalColor.Default,
    val background: TerminalColor = TerminalColor.Default,
    val bold: Boolean = false,
    // ...
)
```

#### 使用密封类
```kotlin
// ✅ 好的实践
sealed class SshAuthParams {
    data class Password(val password: String) : SshAuthParams()
    data class PrivateKey(val keyPath: String, val passphrase: String?) : SshAuthParams()
}
```

#### 使用 when 表达式
```kotlin
// ✅ 好的实践：穷尽所有情况
when (state) {
    ParseState.NORMAL -> handleNormal()
    ParseState.ESCAPE -> handleEscape()
    ParseState.CSI -> handleCsi()
    // ...
}
```

#### 空安全
```kotlin
// ✅ 好的实践：使用安全调用和 Elvis 操作符
val width = image?.width ?: 0
val session = session ?: return
```

#### 协程
```kotlin
// ✅ 好的实践：使用 withContext 切换调度器
suspend fun connect() = withContext(Dispatchers.IO) {
    // 网络操作
}
```

### Compose 最佳实践

#### 状态管理
```kotlin
// ✅ 好的实践：使用 mutableStateOf
var cursorVisible by mutableStateOf(true)
    private set  // 只读外部访问
```

#### 组合函数
```kotlin
// ✅ 好的实践：使用 @Composable 注解
@Composable
fun TerminalView(
    viewModel: TerminalViewModel,
    modifier: Modifier = Modifier
) {
    // UI 代码
}
```

## 🧪 测试规范

### 测试结构
```
src/
├── commonTest/          # 单元测试
│   └── kotlin/cn/hjhw/ssh/terminal/
│       ├── AnsiParserTest.kt
│       ├── TerminalBufferTest.kt
│       └── TerminalCompatibilityTest.kt
│
└── jvmTest/            # 集成测试
    └── kotlin/cn/hjhw/ssh/terminal/
        └── YaziGraphicsProtocolTest.kt
```

### 测试命名
```kotlin
class AnsiParserTest {
    @Test
    fun `test CSI cursor movement sequences`() {
        // ...
    }
    
    @Test
    fun `test SGR color attributes`() {
        // ...
    }
}
```

### 测试覆盖
- **单元测试**: 核心逻辑类（AnsiParser, TerminalBuffer）
- **集成测试**: SSH 连接和真实软件兼容性
- **手动测试**: UI 交互和复杂场景

## 🔧 工具配置

### Detekt (代码检查)
配置文件: `composeApp/detekt.yml`
- 检查代码风格
- 发现潜在问题
- 强制最佳实践

### Gradle
- 使用 Kotlin DSL (`build.gradle.kts`)
- 版本管理: `gradle/libs.versions.toml`
- 模块化配置

## 📚 参考资料

### 终端协议
- [ANSI/VT100 Terminal Control Codes](https://vt100.net/)
- [Xterm Control Sequences](https://invisible-island.net/xterm/ctlseqs/ctlseqs.html)
- [Kitty Graphics Protocol](https://sw.kovidgoyal.net/kitty/graphics-protocol/)

### 技术文档
- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [SSHJ Library](https://github.com/hierynomus/sshj)

---

**最后更新**: 2025-11-14

