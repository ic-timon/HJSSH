# HJSSH 版本配置说明

## 当前版本
**v0.0.1** (测试版本)

## 版本号配置位置

### 1. Gradle 构建配置 ✅
**文件**: `composeApp/build.gradle.kts`
```kotlin
nativeDistributions {
    packageVersion = "0.0.1"  // ← 版本号配置
}
```
**用途**: 
- MSI 安装包版本号
- DEB 包版本号
- DMG 包版本号
- 应用程序"关于"对话框显示的版本号

### 2. Git 标签（发布时使用）
```bash
# 创建版本标签
git tag -a v0.0.1 -m "Release version 0.0.1"

# 推送标签触发自动发布
git push origin v0.0.1
```

## 版本号规范

遵循 [语义化版本控制 2.0.0](https://semver.org/lang/zh-CN/)

**格式**: `主版本号.次版本号.修订号` (MAJOR.MINOR.PATCH)

### 版本号含义

- **v0.0.x** - 初期测试版本
  - v0.0.1 - 首个测试版本
  - v0.0.2 - 修复关键问题
  - v0.0.3 - 继续完善

- **v0.x.0** - 功能开发版本
  - v0.1.0 - 基础功能完成
  - v0.2.0 - 新增图形协议支持
  - v0.3.0 - 新增多标签支持

- **v1.0.0** - 首个正式稳定版本
  - 所有核心功能完成
  - 经过充分测试
  - 适合生产环境使用

- **v1.x.0** - 正式版本功能更新
  - v1.1.0 - 新增功能（向下兼容）
  - v1.2.0 - 更多新功能

- **v1.x.y** - 正式版本 Bug 修复
  - v1.1.1 - 修复问题（向下兼容）
  - v1.1.2 - 修复更多问题

- **v2.0.0** - 重大更新
  - 可能包含不兼容的更改
  - 需要重新测试集成

## 版本更新流程

### 更新版本号
1. 编辑 `composeApp/build.gradle.kts`
2. 修改 `packageVersion = "x.y.z"`
3. 提交更改

### 发布新版本
```bash
# 1. 确保版本号已更新
cat composeApp/build.gradle.kts | grep packageVersion

# 2. 提交版本更新
git add composeApp/build.gradle.kts
git commit -m "Bump version to 0.0.1"
git push origin main

# 3. 创建并推送标签
git tag -a v0.0.1 -m "Release version 0.0.1

测试功能:
- SSH 连接
- 终端显示
- 基本编辑器支持
"
git push origin v0.0.1
```

## 当前项目状态

### 已完成的功能
- ✅ 完整的 ANSI/VT100 支持
- ✅ TrueColor 支持
- ✅ nano/vi 编辑器支持
- ✅ Kitty Graphics Protocol
- ✅ Yazi 文件管理器支持
- ✅ 多标签会话管理
- ✅ 文本选择和复制
- ✅ Emoji 和 Nerd Font 图标

### 版本建议
- **v0.0.1** - 首次测试发布（当前）
- **v0.1.0** - 内测版本（修复初期问题后）
- **v1.0.0** - 正式发布（充分测试后）

## 快速参考

```bash
# 查看当前版本
grep packageVersion composeApp/build.gradle.kts

# 查看所有标签
git tag -l

# 查看最新标签
git describe --tags --abbrev=0

# 查看标签详情
git show v0.0.1
```

---

**最后更新**: 2024-11-14
**当前版本**: v0.0.1
**下一计划版本**: v0.0.2 或 v0.1.0

