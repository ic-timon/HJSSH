# 应用配置指南

## 📋 概述

本文档说明如何配置 HJSSH 应用的图标、名称和打包信息。

---

## 🎨 应用图标配置

### 图标文件位置

图标文件应放置在：
```
composeApp/src/commonMain/resources/icon.png
```

### 图标要求

| 平台 | 推荐格式 | 推荐尺寸 | 说明 |
|------|---------|---------|------|
| **Windows** | `.ico` 或 `.png` | 256x256 px | 支持多尺寸 ICO，PNG 会自动转换 |
| **macOS** | `.icns` 或 `.png` | 512x512 px | PNG 会自动转换为 ICNS |
| **Linux** | `.png` | 512x512 px | 标准 PNG 格式 |

### 多平台图标（可选）

如果需要为不同平台使用不同的图标，可以创建：
```
composeApp/src/commonMain/resources/
├── icon.png           # 通用图标
├── icon-windows.ico   # Windows 专用
├── icon-macos.icns    # macOS 专用
└── icon-linux.png     # Linux 专用
```

然后在 `build.gradle.kts` 中分别指定：
```kotlin
windows {
    iconFile.set(project.file("src/commonMain/resources/icon-windows.ico"))
}
macOS {
    iconFile.set(project.file("src/commonMain/resources/icon-macos.icns"))
}
linux {
    iconFile.set(project.file("src/commonMain/resources/icon-linux.png"))
}
```

---

## 🏷️ 应用名称配置

### 1. 窗口标题

**位置**: `composeApp/src/jvmMain/kotlin/cn/hjhw/ssh/main.kt`

```kotlin
Window(
    onCloseRequest = ::exitApplication,
    title = "HJSSH - SSH Terminal Emulator",  // ← 窗口标题
    icon = painterResource("icon.png"),
) {
    App()
}
```

**用途**: 
- 窗口顶部标题栏
- 任务栏显示的名称

### 2. 应用包名

**位置**: `composeApp/build.gradle.kts`

```kotlin
nativeDistributions {
    packageName = "HJSSH"  // ← 应用包名
    ...
}
```

**用途**:
- 系统中显示的应用名称
- 安装程序中的默认名称
- 文件名前缀

### 3. 平台特定配置

#### Windows
```kotlin
windows {
    menuGroup = "HJSSH"  // 开始菜单分组
}
```

#### macOS
```kotlin
macOS {
    bundleID = "cn.hjhw.ssh.HJSSH"  // Bundle Identifier
    appCategory = "public.app-category.utilities"  // 应用分类
}
```

#### Linux
```kotlin
linux {
    menuGroup = "Utility;TerminalEmulator"  // 应用菜单分类
    appCategory = "Utility"
}
```

---

## 📦 完整配置示例

### main.kt
```kotlin
package cn.hjhw.ssh

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.res.painterResource
import cn.hjhw.ssh.ui.TextRenderer

fun main() = application {
    TextRenderer.initialize()
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "HJSSH - SSH Terminal Emulator",
        icon = painterResource("icon.png"),
    ) {
        App()
    }
}
```

### build.gradle.kts
```kotlin
compose.desktop {
    application {
        mainClass = "cn.hjhw.ssh.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            
            packageName = "HJSSH"
            packageVersion = "1.0.0"
            description = "HJSSH - Modern SSH Terminal Emulator"
            copyright = "© 2024 HJSSH. All rights reserved."
            vendor = "HJSSH Project"
            
            macOS {
                iconFile.set(project.file("src/commonMain/resources/icon.png"))
                bundleID = "cn.hjhw.ssh.HJSSH"
                appCategory = "public.app-category.utilities"
            }
            
            windows {
                iconFile.set(project.file("src/commonMain/resources/icon.png"))
                menuGroup = "HJSSH"
                upgradeUuid = "8a5e8f0a-1b2c-3d4e-5f6a-7b8c9d0e1f2a"
            }
            
            linux {
                iconFile.set(project.file("src/commonMain/resources/icon.png"))
                menuGroup = "Utility;TerminalEmulator"
                appCategory = "Utility"
            }
        }
    }
}
```

---

## 🔨 生成图标

### 从 PNG 生成多格式图标

#### Windows ICO
```bash
# 使用 ImageMagick
convert icon.png -define icon:auto-resize=256,128,96,64,48,32,16 icon.ico

# 或使用在线工具
# https://icoconvert.com/
```

#### macOS ICNS
```bash
# 创建 iconset 目录
mkdir icon.iconset

# 生成不同尺寸
sips -z 16 16     icon.png --out icon.iconset/icon_16x16.png
sips -z 32 32     icon.png --out icon.iconset/icon_16x16@2x.png
sips -z 32 32     icon.png --out icon.iconset/icon_32x32.png
sips -z 64 64     icon.png --out icon.iconset/icon_32x32@2x.png
sips -z 128 128   icon.png --out icon.iconset/icon_128x128.png
sips -z 256 256   icon.png --out icon.iconset/icon_128x128@2x.png
sips -z 256 256   icon.png --out icon.iconset/icon_256x256.png
sips -z 512 512   icon.png --out icon.iconset/icon_256x256@2x.png
sips -z 512 512   icon.png --out icon.iconset/icon_512x512.png
sips -z 1024 1024 icon.png --out icon.iconset/icon_512x512@2x.png

# 转换为 ICNS
iconutil -c icns icon.iconset
```

---

## 🚀 打包应用

### 打包命令

```bash
# 打包当前平台
./gradlew packageDistributionForCurrentOS

# 打包所有平台
./gradlew package

# 仅打包特定格式
./gradlew packageMsi    # Windows
./gradlew packageDmg    # macOS
./gradlew packageDeb    # Linux (Debian/Ubuntu)
```

### 输出位置

打包后的文件会生成在：
```
composeApp/build/compose/binaries/main/
├── dmg/           # macOS .dmg
├── msi/           # Windows .msi
└── deb/           # Linux .deb
```

---

## 🎯 显示效果

### Windows
- **任务栏**: 显示应用图标和标题
- **开始菜单**: "HJSSH" 分组
- **安装程序**: 显示图标和应用信息
- **桌面快捷方式**: 使用配置的图标

### macOS
- **Dock**: 显示应用图标
- **菜单栏**: 显示应用名称
- **启动台**: 显示图标和名称
- **应用程序文件夹**: HJSSH.app

### Linux
- **应用菜单**: 在 "实用工具" 分类下
- **任务栏**: 显示图标和窗口标题
- **启动器**: 显示配置的图标

---

## 🐛 常见问题

### Q1: 图标没有显示？

**解决方案**:
1. 确认图标文件存在：`composeApp/src/commonMain/resources/icon.png`
2. 检查文件名大小写（区分大小写）
3. 重新构建项目：`./gradlew clean build`
4. 检查图标格式（PNG 应该是 RGB 或 RGBA，不是索引色）

### Q2: 打包后的应用图标不正确？

**解决方案**:
1. 确认 `build.gradle.kts` 中的 `iconFile` 路径正确
2. 图标尺寸至少为 256x256 像素
3. 对于 Windows，可能需要手动创建 `.ico` 文件
4. 重新打包：`./gradlew clean packageDistributionForCurrentOS`

### Q3: 窗口标题乱码？

**解决方案**:
1. 确保源文件编码为 UTF-8
2. 在 `build.gradle.kts` 中添加：
```kotlin
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
```

### Q4: macOS 上图标模糊？

**解决方案**:
1. 使用高分辨率图标（至少 512x512，推荐 1024x1024）
2. 使用 `.icns` 格式，包含多个尺寸
3. 确保图标是 PNG 32-bit RGBA 格式

---

## 📝 版本号管理

### 修改版本号

在 `build.gradle.kts` 中：
```kotlin
packageVersion = "1.0.0"  // 主版本.次版本.修订版本
```

### 版本号规范

- **主版本** (1.x.x): 重大更新，可能包含不兼容的改动
- **次版本** (x.1.x): 新功能添加，向后兼容
- **修订版本** (x.x.1): Bug 修复和小改进

---

## 🔐 升级 UUID（Windows）

Windows MSI 安装包使用 UUID 来识别应用，确保升级时正确覆盖旧版本。

**当前 UUID**:
```kotlin
upgradeUuid = "8a5e8f0a-1b2c-3d4e-5f6a-7b8c9d0e1f2a"
```

**注意**: 
- 同一个应用的所有版本应使用相同的 UUID
- 如果是新的应用（非升级），可以生成新的 UUID

**生成新 UUID**:
```bash
# Linux/macOS
uuidgen

# Windows PowerShell
[guid]::NewGuid()

# 在线生成
# https://www.uuidgenerator.net/
```

---

## 📚 相关文档

- [Compose for Desktop 官方文档](https://github.com/JetBrains/compose-multiplatform/tree/master/tutorials/Native_distributions_and_local_execution)
- [应用打包最佳实践](https://github.com/JetBrains/compose-multiplatform/blob/master/tutorials/Native_distributions_and_local_execution/README.md)

---

**配置完成日期**: 2025-11-14  
**当前版本**: 1.0.0  
**应用名称**: HJSSH - SSH Terminal Emulator

