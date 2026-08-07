package me.rerere.rikkahub.data.files.skills.readers

import me.rerere.rikkahub.data.files.skills.ImportedSkill
import me.rerere.rikkahub.data.files.skills.SkillNames
import me.rerere.rikkahub.data.files.skills.SkillSourceFormat

/**
 * Anthropic Agent Skills `SKILL.md`。
 *
 * frontmatter(标准字段)：
 * - name(必填，kebab-case，应与目录名一致)
 * - description(可单行可 `|-` 多行块标量，是触发器)
 * - allowed-tools(空格分隔字符串或 YAML 数组)
 * - license / disable-model-invocation / user-invocable(忽略或转 compatibility 备注)
 */
class AnthropicSkillReader : SkillReader {
    override val sourceFormat: SkillSourceFormat = SkillSourceFormat.ANTHROPIC_SKILL_MD

    override fun canHandle(input: RawSkillInput): Boolean {
        if (!input.lowerFileName.equals("SKILL.md", ignoreCase = true)) return false
        if (!input.content.startsWith("---")) return false
        val fm = ReaderHelpers.parseFrontmatter(input.content)
        val name = (fm["name"] as? String)?.trim()
        return !name.isNullOrBlank()
    }

    override fun read(input: RawSkillInput): List<ImportedSkill> {
        val fm = ReaderHelpers.parseFrontmatter(input.content)
        val nameRaw = (fm["name"] as? String)?.trim().orEmpty()
        val name = SkillNames.normalize(nameRaw.ifBlank { SkillNames.fromFileName(input.fileName) })

        val description = (fm["description"] as? String)?.trim().orEmpty()
        val body = ReaderHelpers.extractBody(input.content)
        val finalDescription = if (description.isBlank()) {
            ReaderHelpers.deriveDescription(body, "Imported skill $name")
        } else {
            description
        }

        val allowedTools = when (val v = fm["allowed-tools"]) {
            is String -> v.split(' ', '\n').map { it.trim() }.filter { it.isNotBlank() }
            is List<*> -> ReaderHelpers.parseGlobsToList(v)
            else -> emptyList()
        }

        // rikkahub 原生 compatibility 仅识别 always / manual-only / null，
        // 不要用逗号拼接多值，也不要写入原生不认识的 "background-only"。
        val disableModelInvocation = (fm["disable-model-invocation"] as? Boolean) == true
        val userInvocable = fm["user-invocable"]
        val compatibility = if (disableModelInvocation) "manual-only" else null

        val basePath = input.relativePath.substringBeforeLast('/', missingDelimiterValue = "")
        val extraFiles = ReaderHelpers.collectExtraFiles(input.siblingFiles, basePath)

        val warnings = mutableListOf<String>()
        if (nameRaw.isNotBlank() && SkillNames.normalize(nameRaw) != name) {
            warnings += "name '$nameRaw' 规范化为 '$name'"
        }
        if (description.isBlank()) {
            warnings += "description 缺失，已从正文首行派生"
        }
        if (fm["license"] != null) {
            warnings += "license 字段已忽略"
        }
        if (userInvocable == false) {
            warnings += "user-invocable=false 无法映射到 rikkahub compatibility(原生不支持 background-only)，已忽略"
        }

        val skill = ImportedSkill(
            name = name,
            description = finalDescription,
            body = body,
            allowedTools = allowedTools,
            compatibility = compatibility,
            sourceFormat = sourceFormat,
            extraFiles = extraFiles,
            warnings = warnings,
        )
        return listOf(skill)
    }
}
