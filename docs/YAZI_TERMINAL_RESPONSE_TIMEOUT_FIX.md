# Yazi Terminal Response Timeout 修复

## 🐛 问题描述

Yazi 报错：
```
Terminal response timeout: the request sent by Yazi didn't receive a correct response.
Please check your terminal environment as per:
https://yazi-rs.github.io/docs/faq#trt
```

## 🔍 根本原因

Yazi 在启动时会发送多个**终端能力查询命令（Device Attributes Query）**，等待终端响应以确定支持的功能。如果在超时时间内没有收到正确响应，就会报这个错误。

## 📊 日志中发现的未响应命令

从日志看到以下未处理的命令：

```
Line 84: [AnsiParser] Unknown CSI command '>' (0x3e), params=[], isPrivate=false
Line 85: [AnsiParser] Unknown CSI command 'c' (0x63), params=[0], isPrivate=false
Line 94: [AnsiParser] Unknown CSI command '$' (0x24), params=[12], isPrivate=true
Line 95: [AnsiParser] Unknown CSI command 'c' (0x63), params=[0], isPrivate=false
```

## 📝 需要实现的终端查询响应

### 1. Primary Device Attributes (DA1)
**查询**：`CSI c` 或 `CSI 0 c`  
**响应**：`CSI ? 1 ; 2 c` (VT100 with Advanced Video Option)  
或更完整的：`CSI ? 62 ; 1 ; 2 ; 6 ; 9 ; 15 ; 22 c`

**含义**：
- 62 = VT220
- 1 = 132 columns
- 2 = Printer port
- 6 = Selective erase
- 9 = National replacement character sets
- 15 = Technical character set
- 22 = Color text

### 2. Secondary Device Attributes (DA2)
**查询**：`CSI > c` 或 `CSI > 0 c`  
**响应**：`CSI > 1 ; 10 ; 0 c`

**含义**：
- 1 = Terminal type (VT220)
- 10 = Firmware version
- 0 = ROM cartridge registration number

### 3. Tertiary Device Attributes (DA3)
**查询**：`CSI = c` 或 `CSI = 0 c`  
**响应**：`DCS ! | 00000000 ST` (Unit ID)

### 4. Cursor Position Report (CPR)
**查询**：`CSI 6 n`  
**响应**：`CSI <row> ; <col> R`

### 5. Extended Cursor Position Report (DECXCPR)
**查询**：`CSI ? 6 n`  
**响应**：`CSI ? <row> ; <col> ; 1 R`

### 6. Terminal Parameters (DECREQTPARM)
**查询**：`CSI <sol> x`  
**响应**：`CSI <sol> ; 1 ; 1 ; 120 ; 120 ; 1 ; 0 x`

### 7. Status Report
**查询**：`CSI 5 n`  
**响应**：`CSI 0 n` (Terminal OK)

### 8. Terminal Name and Version (XTVERSION)
**查询**：`CSI > q`  
**响应**：`DCS > | <name> <version> ST`  
例如：`DCS > | xterm-kitty 0.26.2 ST`

## 🔧 实现方案

### 步骤1：在 AnsiParser.kt 中添加处理函数

```kotlin
private fun handleDeviceAttributes(params: List<Int>, isPrivate: Boolean, intermediate: Char?, onResponse: (String) -> Unit) {
    when {
        // Primary DA: CSI c or CSI 0 c
        !isPrivate && (params.isEmpty() || params[0] == 0) && intermediate == null -> {
            // 响应：VT220 with extensions
            val response = "\u001b[?62;1;2;6;9;15;22c"
            onResponse(response)
            println("      [AnsiParser] Responded to DA1: $response")
        }
        
        // Secondary DA: CSI > c or CSI > 0 c
        !isPrivate && (params.isEmpty() || params[0] == 0) && intermediate == '>' -> {
            // 响应：VT220, firmware version 10.0
            val response = "\u001b[>1;10;0c"
            onResponse(response)
            println("      [AnsiParser] Responded to DA2: $response")
        }
        
        // Tertiary DA: CSI = c
        !isPrivate && intermediate == '=' -> {
            // 响应：Unit ID
            val response = "\u001bP!|00000000\u001b\\"
            onResponse(response)
            println("      [AnsiParser] Responded to DA3: $response")
        }
    }
}

private fun handleStatusReport(params: List<Int>, cursor: CursorState, onResponse: (String) -> Unit) {
    when (params.getOrElse(0) { 0 }) {
        5 -> {
            // DSR - Device Status Report
            val response = "\u001b[0n"  // Terminal OK
            onResponse(response)
            println("      [AnsiParser] Responded to DSR: Terminal OK")
        }
        6 -> {
            // CPR - Cursor Position Report
            val response = "\u001b[${cursor.y + 1};${cursor.x + 1}R"
            onResponse(response)
            println("      [AnsiParser] Responded to CPR: row=${cursor.y + 1}, col=${cursor.x + 1}")
        }
    }
}
```

### 步骤2：修改 handleCsiSequence

在 `handleCsiSequence` 中添加对这些命令的调用：

```kotlin
'c' -> {
    // Device Attributes
    handleDeviceAttributes(params, isPrivate, intermediate, onResponse)
}

'n' -> {
    // Status Report / Cursor Position Report
    handleStatusReport(params, cursor, onResponse)
}

'x' -> {
    // Terminal Parameters (DECREQTPARM)
    if (!isPrivate && params.isNotEmpty()) {
        val sol = params[0]
        val response = "\u001b[$sol;1;1;120;120;1;0x"
        onResponse(response)
        println("      [AnsiParser] Responded to DECREQTPARM")
    }
}

'q' -> {
    // Terminal name/version (XTVERSION)
    if (intermediate == '>') {
        val response = "\u001bP>|xterm-kitty 0.26.2\u001b\\"
        onResponse(response)
        println("      [AnsiParser] Responded to XTVERSION")
    }
}
```

### 步骤3：移除对未知命令的日志

将 "Unknown CSI command" 日志改为只针对未实现的重要命令，避免日志噪音。

## ✅ 预期效果

修复后，Yazi 应该：
1. 正常启动，无 timeout 错误
2. 正确检测终端能力
3. 使用 Kitty Graphics Protocol 显示图片

## 📚 参考资料

- [XTerm Control Sequences](https://invisible-island.net/xterm/ctlseqs/ctlseqs.html)
- [VT100 User Guide](https://vt100.net/docs/vt100-ug/)
- [Yazi FAQ - Terminal Response Timeout](https://yazi-rs.github.io/docs/faq#trt)

---

**下一步**：在 `AnsiParser.kt` 中实现这些响应函数。

