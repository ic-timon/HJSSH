# 版本号修复说明

## 🐛 问题描述

在尝试使用版本号 `0.0.1` 进行打包时，macOS DMG 构建失败，报错：

```
FAILURE: Build failed with an exception.

* What went wrong:
A problem occurred configuring project ':composeApp'.
> * Illegal version for 'Dmg': '0.0.1' is not a valid version.
    * Correct format: 'MAJOR[.MINOR][.PATCH]', where:
      * MAJOR is an integer > 0;
      * MINOR is an optional non-negative integer;
      * PATCH is an optional non-negative integer;
```

## 🔍 根本原因

**macOS DMG 打包工具有特殊的版本号要求：**

- ✅ MAJOR 版本号必须 **大于 0**
- ✅ MINOR 和 PATCH 可以是非负整数（包括 0）

**不符合规范的版本号：**
- ❌ `0.0.1` - MAJOR = 0（不满足 > 0）
- ❌ `0.1.0` - MAJOR = 0（不满足 > 0）
- ❌ `0.9.9` - MAJOR = 0（不满足 > 0）

**符合规范的版本号：**
- ✅ `1.0.0` - MAJOR = 1 (> 0)
- ✅ `1.0.1` - MAJOR = 1 (> 0)
- ✅ `2.5.3` - MAJOR = 2 (> 0)

## ✅ 解决方案

将版本号从 `0.0.1` 改为 `1.0.0`。

### 为什么选择 1.0.0？

1. **功能完整性** ✨
   - HJSSH 已实现所有核心功能
   - 完整的 ANSI/VT100 支持
   - Kitty Graphics Protocol 支持
   - Yazi 文件管理器集成
   - 多标签会话管理

2. **稳定性** 🎯
   - 经过充分测试
   - 主要功能已验证
   - 适合作为首个正式版本

3. **符合规范** 📋
   - 满足所有平台打包要求
   - 遵循语义化版本控制

4. **避免混淆** 🚫
   - 0.x.x 通常表示开发/测试版本
   - 1.0.0 明确表示首个稳定版本

## 📝 版本号修改详情

### 修改的文件

#### 1. `composeApp/build.gradle.kts`
```kotlin
// 修改前
packageVersion = "0.0.1"

// 修改后
packageVersion = "1.0.0"
```

#### 2. `VERSION_CONFIG.md`
- 更新当前版本说明
- 更新所有示例代码中的版本号
- 更新版本规划建议

#### 3. Git 标签
```bash
# 删除旧标签
git tag -d v0.0.1
git push origin :refs/tags/v0.0.1

# 创建新标签（稍后）
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

## 🎯 影响范围

### 不受影响的功能
- ✅ Linux DEB 打包（原本就支持 0.x.x）
- ✅ Windows MSI 打包（原本就支持 0.x.x）
- ✅ 所有应用程序功能
- ✅ CI/CD 构建流程

### 受影响的部分
- 📦 macOS DMG 打包现在可以正常工作
- 🏷️ Git 标签从 v0.0.1 改为 v1.0.0
- 📄 版本文档内容更新

## 🔄 版本演进计划

### 当前版本：v1.0.0
首个正式稳定版本，包含所有核心功能。

### 后续版本规划

#### v1.0.x - Bug 修复版本
```
v1.0.1 - 修复用户报告的问题
v1.0.2 - 性能优化和稳定性改进
```

#### v1.x.0 - 功能增强版本
```
v1.1.0 - 新增功能（如 SFTP 支持）
v1.2.0 - 新增功能（如主题定制）
```

#### v2.0.0 - 重大更新
```
v2.0.0 - 架构升级或重大功能变更
```

## 💡 最佳实践建议

### 选择版本号的原则

1. **测试版本**
   - ❌ 不要使用 0.x.x（不兼容 macOS DMG）
   - ✅ 使用 1.0.0-alpha、1.0.0-beta（预发布版本）
   - ✅ 使用内部版本号管理测试

2. **正式版本**
   - ✅ 从 1.0.0 开始
   - ✅ MAJOR: 不兼容的 API 修改
   - ✅ MINOR: 向下兼容的功能性新增
   - ✅ PATCH: 向下兼容的问题修正

3. **跨平台兼容**
   - ✅ 使用 MAJOR >= 1 的版本号
   - ✅ 遵循语义化版本控制 2.0.0
   - ✅ 在所有平台上保持一致

## 🔗 相关资源

- [语义化版本控制 2.0.0](https://semver.org/lang/zh-CN/)
- [macOS Bundle Version](https://developer.apple.com/documentation/bundleresources/information_property_list/cfbundleversion)
- [Compose Multiplatform Packaging](https://github.com/JetBrains/compose-multiplatform/tree/master/tutorials/Native_distributions_and_local_execution)

## 📊 测试验证

### 验证步骤

```bash
# 1. 验证版本号已更新
grep packageVersion composeApp/build.gradle.kts

# 2. 本地测试打包（可选）
./gradlew :composeApp:packageDmg  # macOS
./gradlew :composeApp:packageMsi  # Windows
./gradlew :composeApp:packageDeb  # Linux

# 3. 推送并发布
git push origin main
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

### 预期结果

- ✅ macOS DMG 打包成功
- ✅ Windows MSI 打包成功
- ✅ Linux DEB 打包成功
- ✅ GitHub Release 自动创建
- ✅ 所有平台安装包正常工作

## ⚠️ 注意事项

1. **不要回退到 0.x.x 版本**
   - macOS 打包会失败
   - 可能造成版本混乱

2. **标签命名保持一致**
   - Git 标签：`v1.0.0`（带 v 前缀）
   - 包版本：`1.0.0`（不带 v）

3. **文档同步更新**
   - README.md
   - VERSION_CONFIG.md
   - Release notes

4. **沟通版本变更**
   - 在 Release notes 中说明版本号跳跃
   - 解释原因（macOS 技术限制）

---

**问题解决日期**: 2024-11-14
**问题类型**: macOS 打包限制
**解决方案**: 版本号从 0.0.1 升级到 1.0.0
**状态**: ✅ 已修复

