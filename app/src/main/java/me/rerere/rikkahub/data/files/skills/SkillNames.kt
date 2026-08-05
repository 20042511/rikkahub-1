package me.rerere.rikkahub.data.files.skills

/**
 * skill 名称规范化工具：kebab-case + 路径安全。
 *
 * 仅对新导入的 skill 应用；已有 skill 的 name 不被重新规范化(避免 enabledSkills 引用失效)。
 */
object SkillNames {
    private val INVALID_CHARS = Regex("[^a-zA-Z0-9-]")

    /**
     * 把任意来源名/标题/文件名规范化为 kebab-case 目录名。
     * 例："Vue Components" -> "vue-components"; "API_v2.md" -> "api-v2"
     */
    fun normalize(raw: String): String {
        val cleaned = raw.trim()
            .replace('_', '-')
            .replace(' ', '-')
            .replace(Regex("[/\\\\]"), "-")
        val kebab = cleaned.lowercase()
            .replace(Regex("-+"), "-")
            .trim('-')
        return INVALID_CHARS.replace(kebab, "").ifBlank { "skill" }
    }

    /**
     * 从文件名派生 skill 名(去掉扩展名)。
     * 例："vue-components.mdc" -> "vue-components"; ".cursorrules" -> "cursorrules"
     */
    fun fromFileName(fileName: String): String {
        val name = fileName.trimStart('.').substringBeforeLast('.', missingDelimiterValue = fileName.trimStart('.'))
        return normalize(name)
    }

    /**
     * name 冲突时加来源前缀。
     */
    fun withSourcePrefix(name: String, format: SkillSourceFormat): String {
        val prefix = when (format) {
            SkillSourceFormat.CURSOR_MDC, SkillSourceFormat.CURSOR_RULES_LEGACY -> "cursor"
            SkillSourceFormat.FLAT_MARKDOWN -> "claude-md"
            SkillSourceFormat.WINDSURF_RULE -> "windsurf"
            SkillSourceFormat.KIRO_STEERING -> "kiro"
            SkillSourceFormat.CLINE_RULE -> "cline"
            SkillSourceFormat.COPILOT_INSTRUCTION -> "copilot"
            SkillSourceFormat.ANTHROPIC_SKILL_MD, SkillSourceFormat.UNKNOWN -> return name
        }
        return normalize("$prefix-$name")
    }
}
