import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

detekt {
    config.setFrom(file("detekt.yml"))
    buildUponDefaultConfig = true
}

ktlint {
    android = false
    ignoreFailures = true  // 允许构建在有 ktlint 错误时继续
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
    filter {
        exclude("**/generated/**")
        include("**/kotlin/**")
    }
}

kotlin {
    jvm()

    // 配置所有目标以抑制 expect/actual Beta 警告
    targets.all {
        compilations.all {
            compilerOptions.configure {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sshj)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "cn.hjhw.ssh.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            // 应用包名（用于系统识别）
            packageName = "HJSSH"

            // 应用版本
            packageVersion = "1.0.0"

            // 应用描述
            description = "HJSSH - Modern SSH Terminal Emulator"
            copyright = "© 2024 HJSSH. All rights reserved."
            vendor = "HJSSH Project"

            // 应用图标
            // 注意：这里的路径相对于 src/jvmMain/resources/
            // 如果图标在 commonMain/resources，会自动找到
            // 对于不同平台，可能需要不同尺寸的图标
            macOS {
                // macOS 图标（.icns 格式，或 PNG 会自动转换）
                iconFile.set(project.file("src/commonMain/resources/icon.png"))
                // 应用在 Dock 和菜单栏显示的名称
                bundleID = "cn.hjhw.ssh.HJSSH"
                appCategory = "public.app-category.utilities"
            }

            windows {
                // Windows 图标（.ico 格式，或 PNG 会自动转换）
                iconFile.set(project.file("src/commonMain/resources/icon.png"))
                // 开始菜单中的应用名称
                menuGroup = "HJSSH"
                // 升级 UUID（用于 MSI 安装包）
                upgradeUuid = "8a5e8f0a-1b2c-3d4e-5f6a-7b8c9d0e1f2a"
            }

            linux {
                // Linux 图标（PNG 格式）
                iconFile.set(project.file("src/commonMain/resources/icon.png"))
                // 应用在菜单中的分类
                menuGroup = "Utility;TerminalEmulator"
                appCategory = "Utility"
            }
        }
    }
}
