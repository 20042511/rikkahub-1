package me.rerere.rikkahub.data.files.skills.readers

import me.rerere.rikkahub.data.files.skills.ImportedSkill
import me.rerere.rikkahub.data.files.skills.SkillNames
import me.rerere.rikkahub.data.files.skills.SkillSourceFormat

/**
 * Cline Rules：`.clinerules/*.md`。
 *
 * 无标准 frontmatter 约定，整文件作为一个 skill。若文件恰好有 frontmatter，
 * 仍尝试解析 description(最佳努力)；body 用 extractBody。
 */
class ClineRuleReader : SkillReader {
    override val sourceFormat: SkillSourceFormat = SkillSourceFormat.CLINE_RULE

    override fun canHandle(input: RawSkillInput): Boolean {
        if (!input.lowerFileName.endsWith(".md")) return false
        return input.normalizedPath.contains(".clinerules/")
    }

    override fun read(input: RawSkillInput): List<ImportedSkill> {
        val name = SkillNames.fromFileName(input.fileName)
        val hasFrontmatter = input.content.startsWith("---")
        val description: String
        val body: String

        if (hasFrontmatter) {
            val fm = ReaderHelpers.parseFrontmatter(input.content)
            description = (fm["description"] as? String)?.trim().orEmpty()
            body = ReaderHelpers.extractBody(input.content)
        } else {
            description = ""
            body = input.content
        }

        val finalDescription = if (description.isBlank()) {
            ReaderHelpers.deriveDescription(body, "Cline rule $name")
        } else {
            description
        }

        val basePath = input.relativePath.substringBeforeLast('/', missingDelimiterValue = "")
        val extraFiles = ReaderHelpers.collectExtraFiles(input.siblingFiles, basePath)

        return listOf(
            ImportedSkill(
                name = name,
                description = finalDescription,
                body = body,
                sourceFormat = sourceFormat,
                extraFiles = extraFiles,
                warnings = if (description.isBlank()) listOf("description 缺失，已从正文首行派生") else emptyList(),
            ),
        )
    }
}
