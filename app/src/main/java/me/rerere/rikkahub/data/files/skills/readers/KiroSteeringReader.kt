package me.rerere.rikkahub.data.files.skills.readers

import me.rerere.rikkahub.data.files.skills.ImportedSkill
import me.rerere.rikkahub.data.files.skills.SkillNames
import me.rerere.rikkahub.data.files.skills.SkillSourceFormat

/**
 * Kiro Steering：`.kiro/steering` 目录下的 Markdown。
 *
 * frontmatter(官方字段名，见 https://kiro.dev/docs/steering/）：
 * - inclusion: always | fileMatch | manual | auto
 * - fileMatchPattern: inclusion=fileMatch 时的 glob(单字符串或字符串数组)
 * - domain: 可选，组织分类用(不参与触发)
 *
 * 映射：
 * - inclusion=always → compatibility="always"
 * - inclusion=fileMatch + fileMatchPattern → allowedTools
 * - inclusion=manual → compatibility="manual-only"
 * - inclusion=auto → compatibility=null(由 AI 决定，无固定标记)
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
        // 官方字段是 fileMatchPattern(单数)；兼容回落到 globs 以防第三方误用
        val patterns = ReaderHelpers.parseGlobsToList(fm["fileMatchPattern"])
            .ifEmpty { ReaderHelpers.parseGlobsToList(fm["globs"]) }

        val compatibility = when (inclusion.lowercase()) {
            "always" -> "always"
            "manual" -> "manual-only"
            "filematch", "auto", "" -> null
            else -> null
        }
        val allowedTools = if (inclusion.lowercase() == "filematch") patterns else emptyList()

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
