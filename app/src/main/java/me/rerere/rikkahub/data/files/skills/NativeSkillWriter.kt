package me.rerere.rikkahub.data.files.skills

/**
 * 把 [ImportedSkill] 序列化为 rikkahub 原生 skill 文件(`SKILL.md` + 附带资源)。
 *
 * 输出格式与 [me.rerere.rikkahub.data.files.SkillManager.parseSkillFile] 期望严格对齐：
 * - frontmatter 字段：name / description / allowed-tools(空格分隔) / compatibility
 * - description 单行(短且无换行)否则用 `|-` 块标量
 * - 附带资源按原 relativePath 落盘
 */
object NativeSkillWriter {
    private const val DESCRIPTION_INLINE_LIMIT = 200

    /**
     * @return relativePath -> content(bytes)，至少含 "SKILL.md"
     */
    fun toSkillFiles(skill: ImportedSkill): Map<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        files["SKILL.md"] = buildSkillMd(skill).toByteArray(Charsets.UTF_8)
        for ((path, content) in skill.extraFiles) {
            val safe = sanitizeRelativePath(path) ?: continue
            files[safe] = content
        }
        return files
    }

    private fun buildSkillMd(skill: ImportedSkill): String {
        val sb = StringBuilder()
        sb.append("---\n")
        sb.append("name: ").append(escapeScalar(skill.name)).append('\n')
        sb.append("description: ").append(serializeDescription(skill.description)).append('\n')
        if (skill.allowedTools.isNotEmpty()) {
            sb.append("allowed-tools: ")
                .append(skill.allowedTools.joinToString(" ") { escapeScalar(it) })
                .append('\n')
        }
        if (!skill.compatibility.isNullOrBlank()) {
            sb.append("compatibility: ").append(escapeScalar(skill.compatibility)).append('\n')
        }
        sb.append("---\n\n")
        sb.append(skill.body.trimEnd())
        sb.append('\n')
        return sb.toString()
    }

    private fun serializeDescription(description: String): String {
        val trimmed = description.trim()
        if (trimmed.isEmpty()) return "\"\""
        val hasNewline = trimmed.any { it == '\n' || it == '\r' }
        val tooLong = trimmed.length > DESCRIPTION_INLINE_LIMIT
        val needsQuoting = trimmed.startsWith("-") || trimmed.contains(':') ||
            trimmed.startsWith("#") || trimmed.startsWith("!") ||
            trimmed.startsWith("@") || trimmed.startsWith("*") ||
            trimmed.startsWith("[") || trimmed.startsWith("{") ||
            trimmed.startsWith("?") || trimmed.startsWith("|") ||
            trimmed.startsWith(">") || trimmed.startsWith("'") ||
            trimmed.startsWith("\"") || trimmed.startsWith("&")
        return when {
            hasNewline || tooLong -> {
                // |- 块标量：保留换行，去除末尾换行符
                val body = trimmed.replace("\r\n", "\n").replace('\r', '\n')
                "|\n" + body.prependIndent("  ")
            }
            needsQuoting -> "\"" + trimmed.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            else -> trimmed
        }
    }

    /** 简单标量转义：含特殊字符时加双引号。 */
    private fun escapeScalar(value: String): String {
        val s = value.trim()
        val needsQuoting = s.isEmpty() || s.any { c ->
            c == ':' || c == '\n' || c == '\r' || c == '#' || c == '"' ||
                c == '\'' || c == '[' || c == ']' || c == '{' || c == '}' ||
                c == ',' || c == '|' || c == '>' || c == '@' || c == '`' ||
                c == ' ' || c == '\t'
        }
        return if (needsQuoting) {
            "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        } else {
            s
        }
    }

    /** 拒绝越界/绝对路径，规范分隔符。 */
    private fun sanitizeRelativePath(path: String): String? {
        val normalized = path.replace('\\', '/').trimStart('/')
        if (normalized.isBlank()) return null
        val parts = normalized.split('/').filter { it.isNotBlank() && it != "." }
        if (parts.any { it == ".." }) return null
        if (parts.isEmpty()) return null
        return parts.joinToString("/")
    }
}
