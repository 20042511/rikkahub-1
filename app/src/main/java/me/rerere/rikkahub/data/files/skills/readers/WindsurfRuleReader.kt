package me.rerere.rikkahub.data.files.skills.readers

import me.rerere.rikkahub.data.files.skills.ImportedSkill
import me.rerere.rikkahub.data.files.skills.SkillNames
import me.rerere.rikkahub.data.files.skills.SkillSourceFormat

/**
 * Windsurf Rules：`.windsurf/rules/*.md`。
 *
 * frontmatter：
 * - trigger: always_on | glob | model_decision | manual
 * - globs: 触发条件为 glob 时的文件匹配(逗号分隔)
 *
 * 映射：
 * - trigger=always_on → compatibility="always"
 * - trigger=glob + globs → allowedTools
 * - trigger=manual → compatibility="manual-only"
 */
class WindsurfRuleReader : SkillReader {
    override val sourceFormat: SkillSourceFormat = SkillSourceFormat.WINDSURF_RULE

    override fun canHandle(input: RawSkillInput): Boolean {
        if (!input.lowerFileName.endsWith(".md")) return false
        return input.normalizedPath.contains(".windsurf/rules/")
    }

    override fun read(input: RawSkillInput): List<ImportedSkill> {
        val fm = ReaderHelpers.parseFrontmatter(input.content)
        val body = ReaderHelpers.extractBody(input.content)
        val name = SkillNames.fromFileName(input.fileName)
        val description = (fm["description"] as? String)?.trim().orEmpty()
        val trigger = (fm["trigger"] as? String)?.trim().orEmpty()
        val globs = ReaderHelpers.parseGlobsToList(fm["globs"])

        val compatibility = when (trigger.lowercase()) {
            "always_on" -> "always"
            "manual" -> "manual-only"
            "glob", "model_decision", "" -> null
            else -> null
        }
        val allowedTools = if (trigger.lowercase() == "glob") globs else emptyList()

        val finalDescription = if (description.isBlank()) {
            ReaderHelpers.deriveDescription(body, "Windsurf rule $name")
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
