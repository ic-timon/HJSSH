# 默认密钥认证修复

## 问题描述

当使用默认密钥，没有写 authentication 方法的时候，提示 "No authentication method available"。

## 解决方案

改进了 `SshConnectionImpl` 中的默认密钥认证逻辑，现在会：

### 1. 按优先级尝试常见默认密钥

按以下顺序尝试加载默认密钥：
1. `~/.ssh/id_rsa` (最常用)
2. `~/.ssh/id_ed25519` (推荐，更安全)
3. `~/.ssh/id_ecdsa`
4. `~/.ssh/id_dsa` (已弃用，但兼容)

### 2. 扫描 ~/.ssh 目录

如果上述默认密钥都不存在，会扫描 `~/.ssh` 目录下所有可能的私钥文件：
- 以 `id_` 开头的文件
- 以 `ssh_` 开头的文件
- 以 `_rsa`, `_ed25519`, `_ecdsa`, `_dsa` 结尾的文件
- 排除 `.pub` (公钥)、`.bak` (备份)、`known_hosts`、`config` 等文件

### 3. 按优先级排序

扫描到的密钥文件会按优先级排序：
- `id_rsa` > `id_ed25519` > `id_ecdsa` > 其他 `id_*` > 其他文件

### 4. 错误信息改进

如果所有密钥都找不到，会显示更详细的错误信息：
```
No authentication method available. 
Please specify a password or key file, or ensure default SSH keys exist in ~/.ssh/
```

## 代码位置

- `composeApp/src/jvmMain/kotlin/cn/hjhw/ssh/connection/SshConnectionImpl.kt`
  - `authenticate()` 方法中的 `SshAuthMethod.None` 分支

## 使用场景

1. **SSH 配置中没有指定 IdentityFile**
   - 从 `~/.ssh/config` 读取的 host 配置
   - 没有 `IdentityFile` 字段
   - 会自动尝试使用默认密钥

2. **手动创建会话时选择默认密钥**
   - 创建新会话时选择 "使用默认密钥"
   - 会自动查找并使用可用的默认密钥

## 测试建议

1. **有默认密钥的情况**
   - 确保 `~/.ssh/id_rsa` 或 `~/.ssh/id_ed25519` 存在
   - 从侧边栏点击 host 连接
   - 应该能成功连接

2. **没有默认密钥的情况**
   - 临时重命名 `~/.ssh/id_rsa`（如果存在）
   - 尝试连接
   - 应该显示详细的错误信息，而不是简单的 "No authentication method available"

3. **多个密钥的情况**
   - 确保有多个密钥文件（如 `id_rsa`, `id_ed25519`）
   - 应该优先使用 `id_rsa`

## 后续改进

可以考虑：
1. 支持 ssh-agent（如果可用）
2. 支持交互式密码输入（当没有密钥时）
3. 支持密钥密码短语的交互式输入



