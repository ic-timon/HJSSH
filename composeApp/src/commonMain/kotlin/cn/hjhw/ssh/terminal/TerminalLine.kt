package cn.hjhw.ssh.terminal

/**
 * 终端行 - 表示终端的一行内容
 */
class TerminalLine(
    private val maxWidth: Int,
) {
    private val cells = mutableListOf<TerminalCell>()

    /**
     * 获取指定位置的单元格
     */
    fun getCell(index: Int): TerminalCell {
        return if (index < cells.size) {
            cells[index]
        } else {
            TerminalCell.empty()
        }
    }

    /**
     * 设置指定位置的单元格
     */
    fun setCell(
        index: Int,
        cell: TerminalCell,
    ) {
        // 确保列表足够大
        while (cells.size <= index && cells.size < maxWidth) {
            cells.add(TerminalCell.empty())
        }
        if (index < maxWidth) {
            if (index < cells.size) {
                cells[index] = cell
            } else {
                cells.add(cell)
            }
        }
    }

    /**
     * 获取行的宽度（实际使用的列数）
     */
    fun width(): Int = cells.size.coerceAtMost(maxWidth)

    /**
     * 获取所有单元格
     */
    fun getCells(): List<TerminalCell> = cells.toList()

    /**
     * 清除行内容
     */
    fun clear() {
        cells.clear()
    }

    /**
     * 从指定位置开始插入字符
     */
    fun insertAt(
        index: Int,
        cell: TerminalCell,
    ) {
        if (index >= maxWidth) return
        cells.add(index, cell)
        // 如果超出最大宽度，移除末尾
        if (cells.size > maxWidth) {
            cells.removeAt(cells.size - 1)
        }
    }

    /**
     * 删除指定位置的字符
     */
    fun deleteAt(index: Int) {
        if (index < cells.size) {
            cells.removeAt(index)
            // 在末尾添加空单元格以保持宽度
            cells.add(TerminalCell.empty())
        }
    }

    /**
     * 截断到指定宽度
     */
    fun truncate(width: Int) {
        if (cells.size > width) {
            cells.subList(width, cells.size).clear()
        }
    }

    /**
     * 复制行内容
     */
    fun copy(): TerminalLine {
        val newLine = TerminalLine(maxWidth)
        newLine.cells.addAll(cells)
        return newLine
    }
}
