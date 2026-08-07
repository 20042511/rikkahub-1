package me.rerere.rikkahub.data.files.skills.readers

import me.rerere.rikkahub.data.files.SkillFrontmatterParser
import me.rerere.rikkahub.data.files.skills.ImportedSkill
import me.rerere.rikkahub.data.files.skills.SkillNames
import me.rerere.rikkahub.data.files.skills.SkillSourceFormat

/**
 * Reader 共享工具：派生 description、解析 globs、收集附带资源。
 */
internal object ReaderHelpers {
    private const val DESCRIPTION_FALLBACK_MAX = 200

    /** 从正文首行(非标题、非空、非代码围栏)派生 description。 */
    fun deriveDescription(body: String, fallback: String): String {
        val firstLine = body.lines()
            .map { it.trim() }
            .firstOrNull { line ->
                line.isNotBlank() &&
                    !line.startsWith('#') &&
                    !line.startsWith("```") &&
                    !line.startsWith("---")
            }
        if (firstLine.isNullOrBlank()) return fallback
        val cleaned = firstLine.removePrefix("-").trim()
        return if (cleaned.length > DESCRIPTION_FALLBACK_MAX) {
            cleaned.substring(0, DESCRIPTION_FALLBACK_MAX) + "…"
        } else {
            cleaned
        }
    }

    /**
     * 解析 Cursor/Windsurf/Kiro/Copilot 的 globs/applyTo 字段：
     * - 裸逗号分隔字符串 "src 下任意层级的 .ts, .tsx"
     * - YAML 数组 [a, b] 或 - item
     * 都转成 List<String>。
     */
    fun parseGlobsToList(value: Any?): List<String> {
        return when (value) {
            null -> emptyList()
            is String -> value.split(',', '\n')
                .map { it.trim().trim('"').trim('\'') }
                .filter { it.isNotBlank() }
            is List<*> -> value.mapNotNull {
                when (it) {
                    is String -> it.trim().ifBlank { null }
                    else -> it?.toString()?.trim()?.ifBlank { null }
                }
            }
            else -> emptyList()
        }
    }

    /** 把 YAML frontmatter 解析为结构化 Map(支持块标量/数组)。 */
    fun parseFrontmatter(content: String): Map<String, Any?> =
        SkillFrontmatterParser.parseStructured(content)

    /** 提取 frontmatter 之后的正文；无 frontmatter 则返回原内容。 */
    fun extractBody(content: String): String =
        SkillFrontmatterParser.extractBody(content)

    /**
     * 收集附带资源文件(references/ scripts/ assets/ data/)作为 extraFiles。
     * @param siblingFiles 同 skill 包内其它文件(relativePath -> bytes)
     * @param basePath 当前 skill 的基准目录(空表示根)
     */
    fun collectExtraFiles(
        siblingFiles: Map<String, ByteArray>,
        basePath: String,
    ): Map<String, ByteArray> {
        if (siblingFiles.isEmpty()) return emptyMap()
        val normalizedBase = basePath.replace('\\', '/').trimStart('/').trimEnd('/')
        val result = LinkedHashMap<String, ByteArray>()
        for ((path, bytes) in siblingFiles) {
            val normalized = path.replace('\\', '/').trimStart('/')
            val relative = if (normalizedBase.isBlank()) {
                normalized
            } else {
                normalized.removePrefix("$normalizedBase/").takeIf { it != normalized } ?: continue
            }
            // 跳过其它 SKILL.md(避免嵌套 skill 串味)和隐藏文件
            if (relative.equals("SKILL.md", ignoreCase = true)) continue
            if (relative.startsWith(".") && !relative.startsWith("references/") &&
                !relative.startsWith("scripts/") && !relative.startsWith("assets/") &&
                !relative.startsWith("data/")) {
                // 只跳过明显的临时/隐藏文件，保留 .github/ 等已知资源目录
                continue
            }
            result[relative] = bytes
        }
        return result
    }

    /** 构造带来源前缀的 skill name(避免与已存在 skill 冲突)。 */
    fun ensureUniqueName(
        name: String,
        format: SkillSourceFormat,
        existing: Set<String>,
    ): String {
        var candidate = name
        if (candidate in existing) {
            candidate = SkillNames.withSourcePrefix(candidate, format)
        }
        var n = 2
        while (candidate in existing) {
            candidate = SkillNames.normalize("${name}-$n")
            n++
        }
        return candidate
    }

    /** 给 ImportedSkill 追加 warnings 的便捷构造。 */
    fun withWarnings(skill: ImportedSkill, vararg warnings: String): ImportedSkill =
        skill.copy(warnings = warnings.filter { it.isNotBlank() }.toList())
}
