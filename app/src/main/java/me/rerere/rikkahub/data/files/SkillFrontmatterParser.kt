package me.rerere.rikkahub.data.files

import android.util.Log
import org.yaml.snakeyaml.Yaml

/**
 * Skill frontmatter 解析器。
 *
 * 用 snakeyaml 解析 YAML frontmatter(支持块标量 `|-` `>` `|`、YAML 数组 `[a,b]` / `- item`、
 * 嵌套对象、引号字符串、注释)，失败时降级为逐行 `key: value` 解析(老行为)，保证不崩溃。
 *
 * - [parse] 返回扁平 `Map<String,String>`(向后兼容现有调用方；List 用空格连接)
 * - [parseStructured] 返回结构化 `Map<String,Any?>`(供 Reader 读取 List/Map 等原始类型)
 * - [extractBody] 返回 frontmatter 之后的正文
 */
object SkillFrontmatterParser {
    private const val TAG = "SkillFrontmatterParser"
    private val frontmatterEndRegex = Regex("""\r?\n---(?:\r?\n|$)"")

    /** 扁平化解析：所有值转为 String；List 用空格连接(兼容老的 allowed-tools 空格分隔)。 */
    fun parse(content: String): Map<String, String> {
        val structured = parseStructured(content)
        return buildMap {
            for ((key, value) in structured) {
                val s = flattenToString(value) ?: continue
                if (s.isBlank()) continue
                put(key, s)
            }
        }
    }

    /**
     * 结构化解析：保留 YAML 原始类型(String / List / Map / Number / Boolean)。
     * Reader 用这个拿 allowed-tools 数组、嵌套字段等。
     */
    fun parseStructured(content: String): Map<String, Any?> {
        val yaml = extractFrontmatterYaml(content) ?: return emptyMap()
        if (yaml.isBlank()) return emptyMap()
        return runCatching {
            val loaded = Yaml().load<Any?>(yaml)
            @Suppress("UNCHECKED_CAST")
            (loaded as? Map<String, Any?>)?.mapKeys { it.key.trim() } ?: emptyMap()
        }.getOrElse { e ->
            Log.w(TAG, "snakeyaml parse failed, falling back to legacy parser", e)
            parseLegacy(yaml)
        }
    }

    fun extractBody(content: String): String {
        if (!content.startsWith("---")) return content
        val endRange = findFrontmatterEndRange(content) ?: return content
        return content.substring(endRange.last + 1).trimStart('\r', '\n')
    }

    private fun extractFrontmatterYaml(content: String): String? {
        if (!content.startsWith("---")) return null
        val endRange = findFrontmatterEndRange(content) ?: return null
        return content.substring(3, endRange.first).trim()
    }

    private fun findFrontmatterEndRange(content: String): IntRange? {
        if (!content.startsWith("---")) return null
        return frontmatterEndRegex.find(content, startIndex = 3)?.range
    }

    /** 把任意 YAML 值扁平化为 String；List 用空格连接。null/空白返回 null。 */
    private fun flattenToString(value: Any?): String? {
        return when (value) {
            null -> null
            is String -> value.trim().ifBlank { null }
            is Number, is Boolean -> value.toString()
            is List<*> -> value
                .mapNotNull { flattenToString(it) }
                .joinToString(" ")
                .ifBlank { null }
            is Map<*, *> -> null // 嵌套对象不参与扁平化
            else -> value.toString().trim().ifBlank { null }
        }
    }

    /** 降级解析器：snakeyaml 失败时复用老的逐行 key:value 逻辑(单行、去引号)。 */
    private fun parseLegacy(yaml: String): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        yaml.lines().forEach { line ->
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val key = line.substring(0, colonIdx).trim()
                val value = line.substring(colonIdx + 1).trim().removeSurrounding("\"")
                if (key.isNotBlank() && value.isNotBlank()) {
                    result[key] = value
                }
            }
        }
        return result
    }
}
