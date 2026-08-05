package me.rerere.rikkahub.data.files.skills.readers

import me.rerere.rikkahub.data.files.skills.ImportedSkill
import me.rerere.rikkahub.data.files.skills.SkillNames
import me.rerere.rikkahub.data.files.skills.SkillSourceFormat

/**
 * Cursor Rules：`.mdc`(新，有 frontmatter)与 `.cursorrules`(旧，无 frontmatter 常驻)。
 *
 * .mdc frontmatter：
 * - description(触发器)
 * - globs(裸逗号分隔字符串，**不是** YAML 数组；也兼容数组形式)
 * - alwaysApply(布尔)
 *
 * 映射：
 * - globs → allowedTools(路径提示)
 * - alwaysApply=true → compatibility="always"
 */
class CursorRuleReader : SkillReader {
    override val sourceFormat: SkillSourceFormat = SkillSourceFormat.CURSOR_MDC

    override fun canHandle(input: RawSkillInput): Boolean {
        if (input.lowerFileName.endsWith(".mdc")) return true
        if (input.lowerFileName == ".cursorrules") return true
        return false
    }

    override fun read(input: RawSkillInput): List<ImportedSkill> {
        val isLegacy = input.lowerFileName == ".cursorrules"
        val format = if (isLegacy) SkillSourceFormat.CURSOR_RULES_LEGACY else SkillSourceFormat.CURSOR_MDC

        if (isLegacy) {
            // .cursorrules 无 frontmatter，整文件作 body
            val name = SkillNames.fromFileName(input.fileName)
            val description = ReaderHelpers.deriveDescription(input.content, "Cursor rules ($name)")
            val basePath = input.relativePath.substringBeforeLast('/', missingDelimiterValue = "")
            val extraFiles = ReaderHelpers.collectExtraFiles(input.siblingFiles, basePath)
            return listOf(
                ImportedSkill(
                    name = name,
                    description = description,
                    body = input.content,
                    compatibility = "always",
                    sourceFormat = format,
                    extraFiles = extraFiles,
                    warnings = listOf("从旧版 .cursorrules 导入(常驻规则)"),
                ),
            )
        }

        val fm = ReaderHelpers.parseFrontmatter(input.content)
        val description = (fm["description"] as? String)?.trim().orEmpty()
        val body = ReaderHelpers.extractBody(input.content)
        val name = SkillNames.fromFileName(input.fileName)

        val globs = ReaderHelpers.parseGlobsToList(fm["globs"])
        val alwaysApply = (fm["alwaysApply"] as? Boolean) == true
        val compatibility = if (alwaysApply) "always" else null

        val finalDescription = if (description.isBlank()) {
            ReaderHelpers.deriveDescription(body, "Cursor rule $name")
        } else {
            description
        }

        val basePath = input.relativePath.substringBeforeLast('/', missingDelimiterValue = "")
        val extraFiles = ReaderHelpers.collectExtraFiles(input.siblingFiles, basePath)

        val warnings = mutableListOf<String>()
        if (description.isBlank()) warnings += "description 缺失，已从正文首行派生"
        if (globs.isNotEmpty() && alwaysApply) {
            warnings += "alwaysApply=true 时 globs 被忽略(Cursor 行为)，已按 always 处理"
        }

        return listOf(
            ImportedSkill(
                name = name,
                description = finalDescription,
                body = body,
                allowedTools = globs,
                compatibility = compatibility,
                sourceFormat = format,
                extraFiles = extraFiles,
                warnings = warnings,
            ),
        )
    }
}
