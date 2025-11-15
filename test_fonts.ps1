# 字体测试脚本
# 用于验证 btop 等工具中的特殊字符显示

Write-Host "🎯 HJSSH 字体测试" -ForegroundColor Green
Write-Host "================================" -ForegroundColor Cyan

# 测试 Nerd Fonts 图标
Write-Host "`n🔤 Nerd Fonts 图标测试:" -ForegroundColor Yellow
Write-Host "                     " -ForegroundColor White
Write-Host "                     " -ForegroundColor White
Write-Host "                     " -ForegroundColor White

# 测试 Emoji
Write-Host "`n😊 Emoji 测试:" -ForegroundColor Yellow
Write-Host "   😀 😃 😄 😁 😆 😅 🤣 😂 🥲 ☺️" -ForegroundColor White
Write-Host "   🚀 🎯 ⭐ 🔥 💡 📱 💻 🖥️ 📡 🔌" -ForegroundColor White

# 测试 btop 常用字符
Write-Host "`n📊 btop 字符测试:" -ForegroundColor Yellow
Write-Host "   CPU: ██████████ 100%" -ForegroundColor White
Write-Host "   RAM: ██████░░░░ 60%" -ForegroundColor White
Write-Host "   NET: ▲ 1.2MB/s ▼ 0.8MB/s" -ForegroundColor White
Write-Host "   DISK: ██████████ 100%" -ForegroundColor White

Write-Host "`n📋 测试说明:" -ForegroundColor Cyan
Write-Host "   - 如果看到方块 □ 或问号 ?，说明字体未正确加载" -ForegroundColor White
Write-Host "   - 如果看到正确的图标和符号，说明字体工作正常" -ForegroundColor White
Write-Host "   - btop 需要 Nerd Fonts 来显示进度条和图标" -ForegroundColor White

Write-Host "`n✅ 测试完成" -ForegroundColor Green
