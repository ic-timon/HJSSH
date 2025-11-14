# 快速发布指南 / Quick Release Guide

## 🚀 发布新版本（3 步完成）

### 1️⃣ 更新版本号

编辑 `composeApp/build.gradle.kts`，找到并更新：

```kotlin
nativeDistributions {
    packageName = "HJSSH"
    packageVersion = "1.0.0"  // ← 修改这里
    // ...
}
```

### 2️⃣ 提交并推送

```bash
git add composeApp/build.gradle.kts
git commit -m "Bump version to 1.0.0"
git push origin main
```

### 3️⃣ 创建并推送标签

```bash
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

**🎉 完成！** GitHub Actions 会自动构建并发布。

---

## 📦 发布内容

自动生成的安装包：

- 🐧 **Linux**: `HJSSH-linux-x64.deb`
- 🪟 **Windows**: `HJSSH-windows-x64.msi`
- 🍎 **macOS**: `HJSSH-macos-x64.dmg`

---

## 🔍 查看发布状态

1. **GitHub Actions**: https://github.com/ic-timon/HJSSH/actions
2. **Releases**: https://github.com/ic-timon/HJSSH/releases

---

## 📝 版本号规范

- `v1.0.0` - 首个正式版本
- `v1.1.0` - 新功能
- `v1.1.1` - Bug 修复
- `v2.0.0` - 重大更新

详细指南：[RELEASE_GUIDE.md](RELEASE_GUIDE.md)

