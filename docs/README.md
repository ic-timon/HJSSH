# HJSSH 项目文档

本目录包含 HJSSH 项目的所有技术文档、测试计划和问题修复记录。

## 📋 文档索引

### 🚀 快速开始
- [RUN_APPLICATION.md](RUN_APPLICATION.md) - 如何编译和运行应用
- [APP_CONFIGURATION.md](APP_CONFIGURATION.md) - **应用图标和名称配置** ⭐
- [MULTI_TAB_SUPPORT.md](MULTI_TAB_SUPPORT.md) - **多标签页支持** ⭐
- [TESTING.md](TESTING.md) - 测试指南

### 🧪 测试文档
- [TERMINAL_COMPATIBILITY_TEST_PLAN.md](TERMINAL_COMPATIBILITY_TEST_PLAN.md) - **终端兼容性测试计划** ⭐
- [TEST_AUTOMATION_README.md](TEST_AUTOMATION_README.md) - 自动化测试实现指南
- [REAL_SSH_INTEGRATION_TEST_GUIDE.md](REAL_SSH_INTEGRATION_TEST_GUIDE.md) - SSH 集成测试指南
- [SSH_INTEGRATION_QUICKSTART.md](SSH_INTEGRATION_QUICKSTART.md) - SSH 测试快速入门
- [MANUAL_SSH_TEST_CHECKLIST.md](MANUAL_SSH_TEST_CHECKLIST.md) - 手动测试清单

### 📊 测试结果
- [TEST_RESULTS_FINAL.md](TEST_RESULTS_FINAL.md) - 最终测试结果
- [TEST_RESULTS_SUMMARY.md](TEST_RESULTS_SUMMARY.md) - 测试结果摘要
- [TESTING_COMPLETE_SUMMARY.md](TESTING_COMPLETE_SUMMARY.md) - 测试完成总结
- [QUICK_TEST_GUIDE.md](QUICK_TEST_GUIDE.md) - 快速测试指南

### 🔧 功能实现与修复记录

#### 终端基础功能
- [ANSI_PARSER_ENHANCEMENTS.md](ANSI_PARSER_ENHANCEMENTS.md) - ANSI 解析器增强
- [ALTERNATE_SCREEN_FIX.md](ALTERNATE_SCREEN_FIX.md) - 交替屏幕缓冲区修复
- [ALTERNATE_SCREEN_BUFFER_FIX.md](ALTERNATE_SCREEN_BUFFER_FIX.md) - 交替屏幕缓冲区详细修复

#### Nano 编辑器支持
- [NANO_WIDTH_FIX.md](NANO_WIDTH_FIX.md) - Nano 宽度显示修复
- [NANO_COMPLETE_FIX.md](NANO_COMPLETE_FIX.md) - Nano 完整修复

#### 图形协议支持
- [KITTY_GRAPHICS_IMPLEMENTATION.md](KITTY_GRAPHICS_IMPLEMENTATION.md) - **Kitty Graphics Protocol 实现** ⭐
- [KITTY_GRAPHICS_COMPLETE.md](KITTY_GRAPHICS_COMPLETE.md) - Kitty Graphics 完整实现
- [KITTY_GRAPHICS_BUGFIX.md](KITTY_GRAPHICS_BUGFIX.md) - Kitty Graphics 问题修复
- [KITTY_GRAPHICS_IMAGE_SIZE_FIX.md](KITTY_GRAPHICS_IMAGE_SIZE_FIX.md) - 图像大小修复
- [KITTY_GRAPHICS_COLOR_FIX.md](KITTY_GRAPHICS_COLOR_FIX.md) - 图像颜色修复
- [KITTY_GRAPHICS_FINAL_FIX.md](KITTY_GRAPHICS_FINAL_FIX.md) - Kitty Graphics 最终修复
- [KITTY_GRAPHICS_FINAL_STATUS.md](KITTY_GRAPHICS_FINAL_STATUS.md) - Kitty Graphics 最终状态
- [KITTY_GRAPHICS_DIAGNOSTIC_GUIDE.md](KITTY_GRAPHICS_DIAGNOSTIC_GUIDE.md) - Kitty Graphics 诊断指南

#### Yazi 文件管理器支持
- [YAZI_IMAGE_PREVIEW_TEST.md](YAZI_IMAGE_PREVIEW_TEST.md) - Yazi 图片预览测试
- [YAZI_GRAPHICS_FIX.md](YAZI_GRAPHICS_FIX.md) - Yazi 图形显示修复
- [YAZI_DISPLAY_MODE_EXPLANATION.md](YAZI_DISPLAY_MODE_EXPLANATION.md) - Yazi 显示模式说明
- [YAZI_IMAGE_TEXT_OVERLAP_DEBUG.md](YAZI_IMAGE_TEXT_OVERLAP_DEBUG.md) - Yazi 图片文字重叠调试
- [YAZI_TERMINAL_RESPONSE_TIMEOUT_FIX.md](YAZI_TERMINAL_RESPONSE_TIMEOUT_FIX.md) - Yazi 终端响应超时修复
- [YAZI_COMPLETE_TERMINAL_SUPPORT.md](YAZI_COMPLETE_TERMINAL_SUPPORT.md) - Yazi 完整终端支持
- [FINAL_YAZI_COMPLETE_FIX.md](FINAL_YAZI_COMPLETE_FIX.md) - **Yazi 最终完整修复** ⭐

#### UI 相关
- [UI_REFRESH_BUG_FIX.md](UI_REFRESH_BUG_FIX.md) - UI 刷新问题修复
- [EMOJI_FONT_INTEGRATION.md](EMOJI_FONT_INTEGRATION.md) - Emoji 字体集成指南
- [EMOJI_FONT_IMPLEMENTATION.md](EMOJI_FONT_IMPLEMENTATION.md) - **Emoji 字体实现完成** ⭐

#### 会话管理
- [SESSION_TAB_CREATE_FIX.md](SESSION_TAB_CREATE_FIX.md) - 会话/标签页创建修复
- [MULTI_TAB_VIEWMODEL_CACHE_FIX.md](MULTI_TAB_VIEWMODEL_CACHE_FIX.md) - **多标签页 ViewModel 缓存修复** ⭐

#### 其他
- [AUTHENTICATION_FIX.md](AUTHENTICATION_FIX.md) - SSH 认证修复

### 📈 开发阶段总结
- [PROJECT_COMPLETE_SUMMARY.md](PROJECT_COMPLETE_SUMMARY.md) - **项目完整总结（中英文双语）** ⭐⭐⭐
- [STAGE1_CHECKLIST.md](STAGE1_CHECKLIST.md) - 阶段 1 检查清单
- [STAGE2_SUMMARY.md](STAGE2_SUMMARY.md) - 阶段 2 总结
- [STAGE3_SUMMARY.md](STAGE3_SUMMARY.md) - 阶段 3 总结
- [STAGE4_SUMMARY.md](STAGE4_SUMMARY.md) - 阶段 4 总结
- [STAGE5_SUMMARY.md](STAGE5_SUMMARY.md) - 阶段 5 总结

### 📝 项目规划
- [plan.md](plan.md) - 项目计划

## 🏆 重要里程碑

### 1. 基础终端功能 ✅
- 实现了完整的 ANSI/VT100 转义序列解析
- 支持 256 色和 TrueColor
- 实现交替屏幕缓冲区
- 支持滚动区域和延迟换行

### 2. 编辑器支持 ✅
- `nano` 完全支持（颜色、宽度、编辑、光标）
- `vi`/`vim` 完全支持
- 各种文本样式和字符集

### 3. 系统监控工具 ✅
- `btop` 完全支持
- `htop` 完全支持
- 动态刷新和实时更新

### 4. Kitty Graphics Protocol ✅
- 实现完整的 Kitty Graphics Protocol
- 支持多种图像格式（PNG、JPEG、RGB/RGBA）
- 支持 base64 编码传输
- 支持图像放置和管理

### 5. Yazi 文件管理器 ✅
- 完整的文件浏览功能
- 图片预览（通过 ueberzugpp/Sixel）
- 键盘导航和搜索
- 文件图标正确显示

### 6. 自动化测试框架 ✅
- 实现了完整的自动化测试套件
- 支持真实 SSH 连接测试
- 覆盖主要兼容性场景

## 🔍 如何查找文档

### 按功能查找
- **SSH 连接**: `AUTHENTICATION_FIX.md`, `SSH_INTEGRATION_*.md`
- **ANSI 解析**: `ANSI_PARSER_ENHANCEMENTS.md`
- **屏幕缓冲**: `ALTERNATE_SCREEN_*.md`
- **图形协议**: `KITTY_GRAPHICS_*.md`, `YAZI_*.md`
- **UI 渲染**: `UI_REFRESH_BUG_FIX.md`

### 按软件查找
- **Nano**: `NANO_*.md`
- **Yazi**: `YAZI_*.md`, `FINAL_YAZI_COMPLETE_FIX.md`
- **Kitty**: `KITTY_GRAPHICS_*.md`

### 按类型查找
- **测试相关**: `TEST_*.md`, `TESTING*.md`, `MANUAL_*.md`
- **修复记录**: `*_FIX.md`
- **实现说明**: `*_IMPLEMENTATION.md`
- **状态报告**: `*_STATUS.md`, `*_SUMMARY.md`

## 💡 开发建议

### 新功能开发
1. 先查看 [TERMINAL_COMPATIBILITY_TEST_PLAN.md](TERMINAL_COMPATIBILITY_TEST_PLAN.md) 了解测试标准
2. 参考相关的实现文档（如 `KITTY_GRAPHICS_IMPLEMENTATION.md`）
3. 编写单元测试和集成测试
4. 更新文档记录

### 问题排查
1. 查看相关的 `*_FIX.md` 文档，了解类似问题的解决方案
2. 查看 `*_DIAGNOSTIC_GUIDE.md` 了解诊断方法
3. 运行相关的自动化测试定位问题

### 测试验证
1. 参考 [TEST_AUTOMATION_README.md](TEST_AUTOMATION_README.md) 编写测试
2. 使用 [QUICK_TEST_GUIDE.md](QUICK_TEST_GUIDE.md) 快速验证
3. 查看 [MANUAL_SSH_TEST_CHECKLIST.md](MANUAL_SSH_TEST_CHECKLIST.md) 进行手动测试

## 📞 联系方式

如有问题或建议，请提交 Issue 或查看项目 Wiki。

---

**最后更新**: 2025-11-14
