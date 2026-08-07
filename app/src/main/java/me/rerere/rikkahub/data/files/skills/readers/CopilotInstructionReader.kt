package me.rerere.rikkahub.data.files.skills.readers

import me.rerere.rikkahub.data.files.skills.ImportedSkill
import me.rerere.rikkahub.data.files.skills.SkillNames
import me.rerere.rikkahub.data.files.skills.SkillSourceFormat

/**
 * GitHub Copilot Instructions：`.github/instructions` 下的 `.instructions.md`
 * (或任何 `.instructions.md`)。
 *
 * frontmatter：
 * - applyTo: glob 字符串(如 `**` 下任意层级的 `.ts`)
 * - description / title(可选)
 *
 * 映射：applyTo → allowedTools
 */
class CopilotInstructionReader : SkillReader {
    override val sourceFormat: SkillSourceFormat = SkillSourceFormat.COPILOT_INSTRUCTION

    override fun canHandle(input: RawSkillInput): Boolean {
        if (input.lowerFileName.endsWith(".instructions.md")) return true
        return input.normalizedPath.contains(".github/instructions/") && input.lowerFileName.endsWith(".md")
    }

    override fun read(input: RawSkillInput): List<ImportedSkill> {
        val fm = ReaderHelpers.parseFrontmatter(input.content)
        val body = ReaderHelpers.extractBody(input.content)
        val name = SkillNames.fromFileName(input.fileName).removeSuffix("-instructions")
        val description = (fm["description"] as? String)?.trim().orEmpty()
        val applyTo = ReaderHelpers.parseGlobsToList(fm["applyTo"])

        val finalDescription = if (description.isBlank()) {
            ReaderHelpers.deriveDescription(body, "Copilot instruction $name")
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
                allowedTools = applyTo,
                sourceFormat = sourceFormat,
                extraFiles = extraFiles,
                warnings = if (description.isBlank()) listOf("description 缺失，已从正文首行派生") else emptyList(),
            ),
        )
    }
}
