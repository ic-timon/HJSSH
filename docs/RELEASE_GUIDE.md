# 发布指南 / Release Guide

## 自动发布流程 / Automated Release Process

本项目配置了 GitHub Actions 自动构建和发布流程。

### 📦 发布步骤 / Release Steps

#### 1. 更新版本号

在 `composeApp/build.gradle.kts` 中更新版本号：

```kotlin
nativeDistributions {
    // ...
    packageVersion = "1.0.0"  // 更新此处
    // ...
}
```

#### 2. 创建并推送 Git 标签

```bash
# 创建标签（版本号格式: v主版本.次版本.修订版本）
git tag -a v1.0.0 -m "Release version 1.0.0"

# 推送标签到 GitHub
git push origin v1.0.0
```

#### 3. 自动构建和发布

推送标签后，GitHub Actions 会自动：
1. ✅ 在 Linux、Windows、macOS 上构建应用程序包
2. ✅ 创建 GitHub Release
3. ✅ 上传所有平台的安装包到 Release

### 🔧 手动触发发布

如果需要手动触发发布（不创建标签）：

1. 访问 GitHub Actions 页面
2. 选择 "Release" workflow
3. 点击 "Run workflow" 按钮
4. 选择分支并运行

### 📋 发布产物 / Release Artifacts

每次发布会生成以下安装包：

| 平台 | 文件类型 | 说明 |
|------|---------|------|
| **Linux** | `.deb` | 适用于 Ubuntu/Debian 系统 |
| **Windows** | `.msi` | Windows 安装程序 |
| **macOS** | `.dmg` | macOS 磁盘镜像 |

### 🎯 版本命名规范 / Version Naming Convention

遵循 [语义化版本控制 2.0.0](https://semver.org/lang/zh-CN/)：

- **主版本号**（Major）：不兼容的 API 修改
- **次版本号**（Minor）：向下兼容的功能性新增
- **修订号**（Patch）：向下兼容的问题修正

示例：
- `v1.0.0` - 首个正式版本
- `v1.1.0` - 新增功能
- `v1.1.1` - 修复 bug
- `v2.0.0` - 重大更新，可能不兼容旧版本

### 📝 发布说明模板 / Release Notes Template

创建标签时，建议使用详细的发布说明：

```bash
git tag -a v1.0.0 -m "Release version 1.0.0

新功能 / New Features:
- 支持多标签会话管理
- 集成 Yazi 文件管理器图像预览
- 完整的 Kitty Graphics Protocol 支持

改进 / Improvements:
- 优化图像渲染性能
- 改进终端缓冲区管理
- 增强键盘映射支持

修复 / Bug Fixes:
- 修复文本选择复制问题
- 修复宽字符显示问题
- 修复 nano 编辑器显示问题
"
```

### 🔍 验证发布 / Verify Release

发布完成后，检查以下内容：

1. **GitHub Release 页面**
   - 访问 `https://github.com/ic-timon/HJSSH/releases`
   - 确认新版本已创建
   - 检查所有安装包是否已上传

2. **下载并测试**
   - 下载对应平台的安装包
   - 安装并运行应用程序
   - 验证核心功能正常工作

3. **更新文档**
   - 更新 README.md 中的版本信息
   - 更新 CHANGELOG.md（如果有）

### 🚨 回滚操作 / Rollback

如果发布的版本有严重问题：

1. **删除 Release**
   ```bash
   # 在 GitHub Release 页面手动删除
   # 或使用 GitHub CLI
   gh release delete v1.0.0
   ```

2. **删除标签**
   ```bash
   # 删除本地标签
   git tag -d v1.0.0
   
   # 删除远程标签
   git push origin :refs/tags/v1.0.0
   ```

3. **发布修复版本**
   - 修复问题
   - 增加修订号（如 v1.0.1）
   - 重新发布

### 📊 发布检查清单 / Release Checklist

发布前确认：

- [ ] 所有测试通过（`./gradlew :composeApp:allTests`）
- [ ] 代码已合并到 main 分支
- [ ] 版本号已更新（`build.gradle.kts`）
- [ ] 文档已更新（如有必要）
- [ ] CHANGELOG 已更新（如有）
- [ ] 本地测试应用程序正常运行
- [ ] 创建 Git 标签并推送

发布后确认：

- [ ] GitHub Actions workflow 成功完成
- [ ] Release 页面显示正确
- [ ] 所有平台的安装包已上传
- [ ] 下载安装包并测试安装
- [ ] 应用程序运行正常
- [ ] 更新项目状态（如 Twitter、博客等）

---

## CI/CD Pipeline Details

### Workflow Files

- `.github/workflows/ci.yml` - 持续集成（每次推送）
- `.github/workflows/release.yml` - 发布流程（标签触发）

### Environment Requirements

- **Java**: JDK 17 (Temurin)
- **Gradle**: 使用项目自带的 Gradle Wrapper
- **OS**: Ubuntu, Windows, macOS（多平台构建）

### Build Commands

```bash
# Linux
./gradlew :composeApp:packageDeb

# Windows
.\gradlew.bat :composeApp:packageMsi

# macOS
./gradlew :composeApp:packageDmg
```

---

## Troubleshooting

### 构建失败 / Build Fails

1. 检查 GitHub Actions 日志
2. 本地运行相同的构建命令
3. 确认所有依赖已正确配置
4. 检查 JDK 版本是否正确

### 安装包缺失 / Missing Artifacts

1. 确认 workflow 完全成功
2. 检查 artifact 上传步骤
3. 查看构建输出目录结构

### 标签推送失败 / Tag Push Fails

```bash
# 检查标签是否已存在
git tag -l

# 强制更新标签（谨慎使用）
git tag -f v1.0.0
git push origin v1.0.0 --force
```

---

## 相关链接 / Related Links

- [GitHub Releases 文档](https://docs.github.com/en/repositories/releasing-projects-on-github)
- [语义化版本控制](https://semver.org/lang/zh-CN/)
- [Compose Multiplatform 打包文档](https://github.com/JetBrains/compose-multiplatform/tree/master/tutorials/Native_distributions_and_local_execution)

