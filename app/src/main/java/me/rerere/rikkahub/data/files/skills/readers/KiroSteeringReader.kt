package me.rerere.rikkahub.data.files.skills.readers

import me.rerere.rikkahub.data.files.skills.ImportedSkill
import me.rerere.rikkahub.data.files.skills.SkillNames
import me.rerere.rikkahub.data.files.skills.SkillSourceFormat

/**
 * Kiro Steering：`.kiro/steering/*.md`。
 *
 * frontmatter：
 * - inclusion: always | fileMatch | manual
 * - globs: inclusion=fileMatch 时的文件匹配
 *
 * 映射：
 * - inclusion=always → compatibility="always"
 * - inclusion=fileMatch + globs → allowedTools
 * - inclusion=manual → compatibility="manual-only"
 */
class KiroSteeringReader : SkillReader {
    override val sourceFormat: SkillSourceFormat = SkillSourceFormat.KIRO_STEERING

    override fun canHandle(input: RawSkillInput): Boolean {
        if (!input.lowerFileName.endsWith(".md")) return false
        return input.normalizedPath.contains(".kiro/steering/")
    }

    override fun read(input: RawSkillInput): List<ImportedSkill> {
        val fm = ReaderHelpers.parseFrontmatter(input.content)
        val body = ReaderHelpers.extractBody(input.content)
        val name = SkillNames.fromFileName(input.fileName)
        val description = (fm["description"] as? String)?.trim().orEmpty()
        val inclusion = (fm["inclusion"] as? String)?.trim().orEmpty()
        val globs = ReaderHelpers.parseGlobsToList(fm["globs"])

        val compatibility = when (inclusion.lowercase()) {
            "always" -> "always"
            "manual" -> "manual-only"
            "filematch", "" -> null
            else -> null
        }
        val allowedTools = if (inclusion.lowercase() == "filematch") globs else emptyList()

        val finalDescription = if (description.isBlank()) {
            ReaderHelpers.deriveDescription(body, "Kiro steering $name")
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
                allowedTools = allowedTools,
                compatibility = compatibility,
                sourceFormat = sourceFormat,
                extraFiles = extraFiles,
                warnings = if (description.isBlank()) listOf("description 缺失，已从正文首行派生") else emptyList(),
            ),
        )
    }
}
