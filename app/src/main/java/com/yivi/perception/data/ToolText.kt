package com.yivi.perception.data

/**
 * 把工具的返回值拍成纯文字。
 * 起因：JSON 塞给模型又长又占位置，还一堆括号引号。这里只保留"键：值"的骨架，
 * 表的每一项一行，id 这类关键字段照样在里面，模型一样能用。
 */
object ToolText {

    fun plain(value: Any?): String = when (value) {
        null -> "空"
        is Map<*, *> -> value.entries.joinToString("\n") { (k, v) -> "$k：${render(v, 1)}" }
        is List<*> -> value.mapIndexed { i, v -> "${i + 1}. ${render(v, 1)}" }.joinToString("\n")
        else -> render(value, 1)
    }

    private fun render(value: Any?, depth: Int): String = when (value) {
        null -> ""
        is Map<*, *> -> if (depth >= 3) {
            value.entries.joinToString("；") { "${it.key}=${render(it.value, depth + 1)}" }
        } else {
            value.entries.joinToString("\n") { "${it.key}：${render(it.value, depth + 1)}" }
        }

        is List<*> -> if (depth >= 3) {
            value.joinToString("；") { render(it, depth + 1) }
        } else {
            value.mapIndexed { i, v -> "${i + 1}. ${render(v, depth + 1)}" }.joinToString("\n")
        }

        is Double -> if (!value.isFinite()) {
            value.toString()
        } else {
            // 1.5 就写 1.5，1.234 写 1.23，整数走上面的整型分支
            val s = String.format(java.util.Locale.US, "%.2f", value)
            s.trimEnd('0').trimEnd('.')
        }

        is Float -> render(value.toDouble(), depth)
        else -> value.toString()
    }
}
