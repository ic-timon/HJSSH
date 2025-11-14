# 测试运行指南

## 运行所有测试

在 Kotlin Multiplatform 项目中，使用以下命令运行所有测试：

```bash
# Windows
.\gradlew.bat :composeApp:allTests

# macOS/Linux
./gradlew :composeApp:allTests
```

## 运行特定平台的测试

```bash
# 运行 JVM 测试
.\gradlew.bat :composeApp:jvmTest
```

## 运行特定测试类

```bash
# 运行 SSH 配置解析器测试
.\gradlew.bat :composeApp:jvmTest --tests "*SshConfigParserTest*"

# 运行桌面应用测试
.\gradlew.bat :composeApp:jvmTest --tests "*ComposeAppDesktopTest*"
```

## 查看测试报告

测试报告生成在以下位置：
- JVM 测试报告: `composeApp/build/reports/tests/jvmTest/index.html`
- 所有测试报告: `composeApp/build/reports/tests/allTests/index.html`

## 当前测试覆盖

### ✅ SSH 配置解析器测试 (`SshConfigParserTest`)
- 基本配置解析
- 多条配置与通配符匹配
- 容错处理
- 大小写不敏感
- 通配符模式匹配
- ForwardAgent 配置
- 注释和空行处理

### ✅ 桌面应用测试 (`ComposeAppDesktopTest`)
- 基本示例测试
- App 组件实例化测试
- 主函数存在性测试

## 测试状态

✅ **所有测试通过** - 10 个测试全部成功



