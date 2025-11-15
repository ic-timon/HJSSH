# HJSSH v1.0.0 重新发布脚本
# 修复权限问题后的完整发布流程

Write-Host ""
Write-Host "🚀 HJSSH v1.0.0 重新发布流程" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# 步骤 1: 删除旧标签
Write-Host "📝 步骤 1/4: 删除失败的 v1.0.0 标签..." -ForegroundColor Yellow
git tag -d v1.0.0
if ($LASTEXITCODE -eq 0) {
    Write-Host "  ✅ 本地标签已删除" -ForegroundColor Green
} else {
    Write-Host "  ⚠️ 本地标签不存在或已删除" -ForegroundColor Gray
}

Write-Host ""
Write-Host "  正在删除远程标签..." -ForegroundColor Gray
git push origin :refs/tags/v1.0.0
if ($LASTEXITCODE -eq 0) {
    Write-Host "  ✅ 远程标签已删除" -ForegroundColor Green
} else {
    Write-Host "  ⚠️ 远程标签不存在或已删除" -ForegroundColor Gray
}

Write-Host ""
Start-Sleep -Seconds 1

# 步骤 2: 推送权限修复
Write-Host "📝 步骤 2/4: 推送权限修复到 main 分支..." -ForegroundColor Yellow
git push origin main
if ($LASTEXITCODE -eq 0) {
    Write-Host "  ✅ 权限修复已推送" -ForegroundColor Green
} else {
    Write-Host "  ❌ 推送失败" -ForegroundColor Red
    exit 1
}

Write-Host ""
Start-Sleep -Seconds 2

# 步骤 3: 重新创建标签
Write-Host "📝 步骤 3/4: 重新创建 v1.0.0 标签..." -ForegroundColor Yellow
$tagMessage = @"
Release version 1.0.0 - First Stable Release

🎉 HJSSH v1.0.0 - 首个正式稳定版本

✨ 核心功能 / Core Features

终端模拟 / Terminal Emulation:
- 完整的 ANSI/VT100 转义序列支持
- TrueColor 支持 (24-bit)
- 交替屏幕缓冲区、滚动区域、延迟换行
- 宽字符支持 (CJK)

编辑器和工具支持:
- nano/vi/vim 完整支持
- btop/htop 系统监控
- 所有常见命令行工具

图形协议 / Graphics:
- Kitty Graphics Protocol
- Sixel Protocol (via ueberzugpp)
- Yazi 文件管理器集成

UI 功能:
- 多标签会话管理
- 文本选择和复制
- Emoji 和 Nerd Font 图标

🚀 性能优化:
- CI/CD 构建优化 (50-70% 提速)
- 多级缓存策略

📦 安装包:
- Linux: .deb
- Windows: .msi
- macOS: .dmg

📝 技术说明:
- 版本从 0.0.1 升级到 1.0.0 (满足 macOS 要求)
- 修复了 Release workflow 权限问题
- MIT License

For documentation: https://github.com/ic-timon/HJSSH/tree/main/docs
"@

git tag -a v1.0.0 -m $tagMessage
if ($LASTEXITCODE -eq 0) {
    Write-Host "  ✅ 标签已创建" -ForegroundColor Green
} else {
    Write-Host "  ❌ 标签创建失败" -ForegroundColor Red
    exit 1
}

Write-Host ""
Start-Sleep -Seconds 1

# 步骤 4: 推送标签，触发发布
Write-Host "📝 步骤 4/4: 推送标签，触发自动发布..." -ForegroundColor Yellow
git push origin v1.0.0
if ($LASTEXITCODE -eq 0) {
    Write-Host "  ✅ 标签已推送" -ForegroundColor Green
} else {
    Write-Host "  ❌ 推送失败" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "🎉 重新发布流程完成！" -ForegroundColor Green
Write-Host ""
Write-Host "📊 GitHub Actions 正在构建安装包..." -ForegroundColor Cyan
Write-Host "   预计时间: 4-6 分钟" -ForegroundColor Gray
Write-Host ""
Write-Host "🔗 查看进度:" -ForegroundColor Cyan
Write-Host "   Actions:  https://github.com/ic-timon/HJSSH/actions" -ForegroundColor Gray
Write-Host "   Releases: https://github.com/ic-timon/HJSSH/releases" -ForegroundColor Gray
Write-Host ""

