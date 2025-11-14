# HJSSH - Modern SSH Terminal | 现代化 SSH 终端



[English](#english) | [中文](#中文)

[![GitHub Stars](https://img.shields.io/github/stars/ic-timon/HJSSH?style=flat-square)](https://github.com/ic-timon/HJSSH/stargazers)
[![GitHub Watchers](https://img.shields.io/github/watchers/ic-timon/HJSSH?style=flat-square)](https://github.com/ic-timon/HJSSH/watchers)
[![GitHub Forks](https://img.shields.io/github/forks/ic-timon/HJSSH?style=flat-square)](https://github.com/ic-timon/HJSSH/network)
[![GitHub Issues](https://img.shields.io/github/issues/ic-timon/HJSSH?style=flat-square)](https://github.com/ic-timon/HJSSH/issues)
[![GitHub Pull Requests](https://img.shields.io/github/issues-pr/ic-timon/HJSSH?style=flat-square)](https://github.com/ic-timon/HJSSH/pulls)

[![GitHub License](https://img.shields.io/github/license/ic-timon/HJSSH?style=flat-square)](https://github.com/ic-timon/HJSSH/blob/main/LICENSE)
[![Version](https://img.shields.io/badge/version-v1.2.1-green.svg?style=flat-square)](https://github.com/ic-timon/HJSSH)

---

<a name="english"></a>
## English

A modern SSH terminal emulator built with Kotlin Multiplatform and Compose Multiplatform, featuring advanced terminal capabilities and graphics protocol support.

### ✨ Features

#### Core Capabilities
- 🔐 **SSH Connection**: Password and key-based authentication
- 🖥️ **Full ANSI/VT100 Support**: Complete terminal control sequence implementation
- 🎨 **TrueColor Support**: 24-bit true color rendering
- 📝 **Full-Featured Text Editors**: Perfect compatibility with `nano`, `vi`/`vim`
- 📊 **System Monitoring Tools**: Proper display of `btop`, `htop`, and more
- 🎯 **Advanced Terminal Features**:
  - Alternate Screen Buffer
  - Scroll Regions
  - Delayed Wrap
  - Wide Character (CJK) Support
  - Character Set Switching (SI/SO)

#### Graphics Protocol Support
- 🖼️ **Kitty Graphics Protocol**: High-performance image transfer protocol
- 🎭 **Sixel Protocol**: Classic graphics protocol support
- 📁 **File Manager Support**: Perfect compatibility with `yazi` file manager, including image previews

#### User Experience
- ⌨️ **Full Keyboard Support**: Numpad, function keys, arrow keys, and more
- 🎨 **Rich Text Styles**: Bold, italic, underline, strikethrough, reverse video, etc.
- 🔄 **Dynamic Window Resizing**: Support for window size changes
- 📋 **Text Selection and Copy**: Mouse-based text selection with right-click copy
- 🎭 **Emoji and Icon Support**: Nerd Fonts and color emoji rendering
- 🗂️ **Multi-Tab Sessions**: Multiple SSH sessions with tab management

### 🚀 Quick Start

#### Prerequisites
- JDK 17 or higher
- Gradle 8.x
- SSH server access

#### Running the Application
```bash
# Windows
.\gradlew.bat :composeApp:run

# Linux/macOS
./gradlew :composeApp:run
```

#### Running Tests
```bash
# Run all tests
.\gradlew.bat :composeApp:allTests

# Run JVM tests only
.\gradlew.bat :composeApp:jvmTest
```

### 📚 Architecture

#### Project Structure
```
HJSSH/
├── composeApp/                 # Main application module
│   └── src/
│       ├── commonMain/        # Cross-platform common code
│       │   └── kotlin/cn/hjhw/ssh/
│       │       ├── connection/    # SSH connection interface
│       │       ├── terminal/      # Terminal emulation core
│       │       ├── session/       # Session management
│       │       └── ui/            # UI components
│       │
│       ├── jvmMain/           # JVM platform implementation
│       │   └── kotlin/cn/hjhw/ssh/
│       │       ├── connection/ # SSH implementation (SSHJ)
│       │       └── ui/         # Platform-specific rendering
│       │
│       └── commonTest/        # Tests
│
├── docs/                      # Documentation
└── README.md                  # This file
```

#### Core Modules

**AnsiParser** - ANSI escape sequence parser supporting:
- CSI (Control Sequence Introducer) sequences
- OSC (Operating System Command) sequences
- DCS (Device Control String) - Sixel protocol
- APC (Application Program Command) - Kitty Graphics protocol
- Character set switching (G0/G1)
- Terminal queries and responses

**TerminalBuffer** - Terminal content manager handling:
- Main and alternate screen buffers
- Scroll regions and history
- Image storage and placement
- Wide character processing

**TerminalView** - Compose Multiplatform rendering:
- High-performance text rendering
- Image overlay rendering
- Cursor drawing
- Scrolling and selection

**SshConnectionImpl** - SSH connection using SSHJ library:
- Password/key authentication
- PTY allocation and configuration
- Terminal environment variable setup
- Dynamic window resizing

### 🎯 Tested Compatibility

✅ **Text Editors**
- `nano` - Full support
- `vi`/`vim` - Full support

✅ **System Monitoring**
- `btop` - Full support
- `htop` - Full support
- `top` - Full support

✅ **File Managers**
- `yazi` - Full support (image preview, navigation, search)
- `ranger` - Supported
- `mc` (Midnight Commander) - Supported

✅ **Other Tools**
- `less`/`more` - Supported
- `tmux`/`screen` - Basic support
- Standard command-line utilities

### 🔧 Configuration

#### SSH Connection
Configure SSH connections in `~/.ssh/config`:
```
Host myserver
    HostName example.com
    User testuser
    Port 22
    IdentityFile ~/.ssh/id_rsa
```

#### Terminal Type
The application automatically sets `TERM=xterm-256color`, supporting:
- 256 color palette
- TrueColor (24-bit)
- Sixel graphics protocol
- ueberzugpp image preview

### 📖 Documentation

Detailed documentation available in [docs/README.md](docs/README.md):
- Development guide
- Test plans
- Compatibility testing
- Bug fix records

### 🛠️ Tech Stack

- **Kotlin Multiplatform**: Cross-platform core logic
- **Compose Multiplatform**: Declarative UI framework
- **SSHJ**: SSH protocol implementation
- **Skia**: High-performance graphics rendering
- **Kotlin Coroutines**: Asynchronous programming

### 🤝 Contributing

Issues and Pull Requests are welcome!

### 📄 License

[To be added]

### 🙏 Acknowledgments

Thanks to all open-source contributors, especially:
- SSHJ
- Compose Multiplatform
- Kitty Terminal
- Yazi File Manager

---

<a name="中文"></a>
## 中文

基于 Kotlin Multiplatform 和 Compose Multiplatform 构建的现代化 SSH 终端模拟器，支持先进的终端功能和图形协议。

### ✨ 特性

#### 核心功能
- 🔐 **SSH 连接**：支持密码和密钥认证
- 🖥️ **完整的 ANSI/VT100 支持**：实现标准终端控制序列
- 🎨 **TrueColor 支持**：24-bit 真彩色渲染
- 📝 **全功能文本编辑器支持**：`nano`、`vi`/`vim` 完美兼容
- 📊 **系统监控工具支持**：`btop`、`htop` 等工具正常显示
- 🎯 **高级终端功能**：
  - 交替屏幕缓冲区 (Alternate Screen Buffer)
  - 滚动区域 (Scroll Regions)
  - 延迟换行 (Delayed Wrap)
  - 宽字符 (CJK) 支持
  - 字符集切换 (SI/SO)

#### 图形协议支持
- 🖼️ **Kitty Graphics Protocol**：高性能图像传输协议
- 🎭 **Sixel Protocol**：传统图形协议支持
- 📁 **文件管理器支持**：`yazi` 文件管理器完美兼容，支持图片预览

#### 用户体验
- ⌨️ **完整键盘支持**：包括小键盘、功能键、方向键等
- 🎨 **丰富的文本样式**：粗体、斜体、下划线、删除线、反显等
- 🔄 **动态窗口调整**：支持窗口大小变化
- 📋 **文本选择和复制**：鼠标选择文本，右键复制
- 🎭 **Emoji 和图标支持**：Nerd Fonts 和彩色 emoji 渲染
- 🗂️ **多标签会话**：多个 SSH 会话的标签管理

### 🚀 快速开始

#### 前置要求
- JDK 17 或更高版本
- Gradle 8.x
- SSH 服务器访问权限

#### 运行应用
```bash
# Windows
.\gradlew.bat :composeApp:run

# Linux/macOS
./gradlew :composeApp:run
```

#### 运行测试
```bash
# 运行所有测试
.\gradlew.bat :composeApp:allTests

# 仅运行 JVM 测试
.\gradlew.bat :composeApp:jvmTest
```

### 📚 架构

#### 项目结构
```
HJSSH/
├── composeApp/                 # 主应用模块
│   └── src/
│       ├── commonMain/        # 跨平台通用代码
│       │   └── kotlin/cn/hjhw/ssh/
│       │       ├── connection/    # SSH 连接接口
│       │       ├── terminal/      # 终端模拟核心
│       │       ├── session/       # 会话管理
│       │       └── ui/            # UI 组件
│       │
│       ├── jvmMain/           # JVM 平台实现
│       │   └── kotlin/cn/hjhw/ssh/
│       │       ├── connection/ # SSH 实现 (SSHJ)
│       │       └── ui/         # 平台特定渲染
│       │
│       └── commonTest/        # 测试
│
├── docs/                      # 文档
└── README.md                  # 本文件
```

#### 核心模块

**AnsiParser** - ANSI 转义序列解析器，支持：
- CSI (Control Sequence Introducer) 序列
- OSC (Operating System Command) 序列
- DCS (Device Control String) - Sixel 协议
- APC (Application Program Command) - Kitty Graphics 协议
- 字符集切换 (G0/G1)
- 各种终端查询和响应

**TerminalBuffer** - 终端内容管理器，处理：
- 主屏幕和交替屏幕缓冲区
- 滚动区域和历史记录
- 图像存储和放置
- 宽字符处理

**TerminalView** - Compose Multiplatform 渲染：
- 高性能文本渲染
- 图像叠加渲染
- 光标绘制
- 滚动和选择

**SshConnectionImpl** - 使用 SSHJ 库的 SSH 连接：
- 密码/密钥认证
- PTY 分配和配置
- 终端环境变量设置
- 动态窗口大小调整

### 🎯 已测试兼容软件

✅ **文本编辑器**
- `nano` - 完全支持
- `vi`/`vim` - 完全支持

✅ **系统监控**
- `btop` - 完全支持
- `htop` - 完全支持
- `top` - 完全支持

✅ **文件管理器**
- `yazi` - 完全支持（图片预览、导航、搜索）
- `ranger` - 支持
- `mc` (Midnight Commander) - 支持

✅ **其他工具**
- `less`/`more` - 支持
- `tmux`/`screen` - 基本支持
- 各种标准命令行工具

### 🔧 配置

#### SSH 连接配置
在 `~/.ssh/config` 中配置 SSH 连接：
```
Host myserver
    HostName example.com
    User testuser
    Port 22
    IdentityFile ~/.ssh/id_rsa
```

#### 终端类型
应用自动设置 `TERM=xterm-256color`，支持：
- 256 色调色板
- TrueColor (24-bit)
- Sixel 图形协议
- ueberzugpp 图像预览

### 📖 文档

详细文档请查看 [docs/README.md](docs/README.md)：
- 开发指南
- 测试计划
- 兼容性测试
- 问题修复记录

### 🛠️ 技术栈

- **Kotlin Multiplatform**：跨平台核心逻辑
- **Compose Multiplatform**：声明式 UI 框架
- **SSHJ**：SSH 协议实现
- **Skia**：高性能图形渲染
- **Kotlin Coroutines**：异步编程

### 🤝 贡献

欢迎提交 Issue 和 Pull Request！

### 📄 许可证

[待添加]

### 🙏 致谢

感谢所有开源项目的贡献者，特别是：
- SSHJ
- Compose Multiplatform
- Kitty Terminal
- Yazi File Manager
