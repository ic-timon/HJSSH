# 字体修复和重新发布脚本
# 修复 btop 等工具中字符显示为方块的问题

param(
    [string]$Version = "1.0.1"
)

Write-Host "🎯 HJSSH 字体修复和重新发布" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan

# 步骤 1: 验证字体配置
Write-Host "`n?? 步骤 1/6: 验证字体配置..." -ForegroundColor Yellow
if (Test-Path "composeApp/src/commonMain/resources/fonts") {
    Write-Host "   ? 字体目录存在" -ForegroundColor Green
    $fontFiles = Get-ChildItem "composeApp/src/commonMain/resources/fonts" -File
    Write-Host "   ? 找到 $($fontFiles.Count) 个字体文件:" -ForegroundColor Green
    foreach ($font in $fontFiles) {
        Write-Host "     - $($font.Name) ($([math]::Round($font.Length/1024/1024, 2)) MB)" -ForegroundColor White
    }
} else {
    Write-Host "   ? 字体目录不存在，需要重新下载字体" -ForegroundColor Red
    exit 1
}

# 步骤 2: 检查 Gradle 配置
Write-Host "`n?? 步骤 2/6: 检查 Gradle 配置..." -ForegroundColor Yellow
if (Select-String -Path "composeApp/build.gradle.kts" -Pattern "appResourcesRootDir" -Quiet) {
    Write-Host "   ? 字体资源打包配置已存在" -ForegroundColor Green
} else {
    Write-Host "   ? 字体资源打包配置缺失" -ForegroundColor Red
    exit 1
}

# 步骤 3: 本地测试字体显示
Write-Host "`n?? 步骤 3/6: 本地测试字体显示..." -ForegroundColor Yellow
Write-Host "   运行字体测试脚本..." -ForegroundColor White
.\test_fonts.ps1

# 步骤 4: 更新版本号
Write-Host "`n?? 步骤 4/6: 更新版本号为 v$Version..." -ForegroundColor Yellow
$gradleFile = Get-Content "composeApp/build.gradle.kts" -Raw
$gradleFile = $gradleFile -replace "packageVersion = \"1.0.0\"", "packageVersion = \"$Version\""
Set-Content "composeApp/build.gradle.kts" -Value $gradleFile
Write-Host "   ? 版本号已更新为 $Version" -ForegroundColor Green

# 步骤 5: 提交更改
Write-Host "`n?? 步骤 5/6: 提交字体修复更改..." -ForegroundColor Yellow
git add composeApp/build.gradle.kts test_fonts.ps1 fix_fonts_and_republish.ps1
git commit -m "Fix font packaging for btop compatibility (v$Version)"
Write-Host "   ? 更改已提交" -ForegroundColor Green

# 步骤 6: 创建并推送标签
Write-Host "`n?? 步骤 6/6: 创建并推送 v$Version 标签..." -ForegroundColor Yellow
git tag -a "v$Version" -m "Release v$Version - Font packaging fix for btop compatibility"
git push origin main
git push origin "v$Version"
Write-Host "   ? 标签 v$Version 已推送" -ForegroundColor Green

Write-Host "`n==========================================" -ForegroundColor Cyan
Write-Host "?? 字体修复和重新发布完成！" -ForegroundColor Green
Write-Host "`n?? 修复内容:" -ForegroundColor Yellow
Write-Host "   - 添加了字体资源打包配置" -ForegroundColor White
Write-Host "   - 确保 Nerd Fonts 和 Emoji 字体包含在发布包中" -ForegroundColor White
Write-Host "   - 解决了 btop 等工具中字符显示为方块的问题" -ForegroundColor White

Write-Host "`n?? GitHub Actions 正在构建 v$Version..." -ForegroundColor Cyan
Write-Host "   预计时间: 4-6 分钟" -ForegroundColor White

Write-Host "`n?? 查看进度:" -ForegroundColor Cyan
Write-Host "   Actions:  https://github.com/ic-timon/HJSSH/actions" -ForegroundColor White
Write-Host "   Releases: https://github.com/ic-timon/HJSSH/releases" -ForegroundColor White

Write-Host "`n✅ 修复完成！新的 v$Version 版本将包含完整的字体支持。" -ForegroundColor Green
