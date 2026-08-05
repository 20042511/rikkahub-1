package me.rerere.rikkahub.data.files.skills.readers

import me.rerere.rikkahub.data.files.skills.ImportedSkill
import me.rerere.rikkahub.data.files.skills.SkillNames
import me.rerere.rikkahub.data.files.skills.SkillSourceFormat

/**
 * 扁平 Markdown：`CLAUDE.md` / `GEMINI.md` / `.claude/CLAUDE.md`。
 *
 * 无 frontmatter，用 `## 标题` 把文件拆成多个独立 skill；每个 section 的首段作 description。
 * 若文件无任何 `## ` 标题，整文件作为一个 skill(name 从文件名派生)。
 */
class FlatMarkdownReader : SkillReader {
    override val sourceFormat: SkillSourceFormat = SkillSourceFormat.FLAT_MARKDOWN

    override fun canHandle(input: RawSkillInput): Boolean {
        val name = input.lowerFileName
        return name == "claude.md" || name == "gemini.md"
    }

    override fun read(input: RawSkillInput): List<ImportedSkill> {
        val content = input.content
        val sections = splitByHeadings(content)
        val basePath = input.relativePath.substringBeforeLast('/', missingDelimiterValue = "")
        val extraFiles = ReaderHelpers.collectExtraFiles(input.siblingFiles, basePath)

        if (sections.isEmpty()) {
            val name = SkillNames.fromFileName(input.fileName)
            return listOf(
                ImportedSkill(
                    name = name,
                    description = ReaderHelpers.deriveDescription(content, "${input.fileName} 导入"),
                    body = content,
                    sourceFormat = sourceFormat,
                    extraFiles = extraFiles,
                    warnings = listOf("无 ## 标题，整文件作为单个 skill"),
                ),
            )
        }

        return sections.mapIndexed { index, section ->
            val name = SkillNames.normalize(section.heading).ifBlank { "section-${index + 1}" }
            val description = ReaderHelpers.deriveDescription(section.body, section.heading)
            ImportedSkill(
                name = name,
                description = description,
                body = section.body,
                sourceFormat = sourceFormat,
                extraFiles = extraFiles,
                warnings = if (section.heading.isBlank()) listOf("section $index 无标题，已用序号") else emptyList(),
            )
        }
    }

    private data class Section(val heading: String, val body: String)

    /**
     * 按 `## ` 标题拆分(不拆 `### ` 及更深层级；它们归入当前 section 的 body)。
     * 第一个 `## ` 之前的内容(preamble)跳过。
     */
    private fun splitByHeadings(content: String): List<Section> {
        val lines = content.lines()
        val result = mutableListOf<Section>()
        var heading: String? = null
        val buffer = StringBuilder()

        for (line in lines) {
            val trimmed = line.trimStart()
            // 仅匹配二级标题(### 及以上不算)
            if (trimmed.startsWith("## ") && !trimmed.startsWith("### ")) {
                // flush 之前的 section
                heading?.let { h ->
                    result.add(Section(h, buffer.toString().trim()))
                }
                heading = trimmed.removePrefix("## ").trim()
                buffer.setLength(0)
            } else if (heading != null) {
                buffer.append(line).append('\n')
            }
            // heading == null 时是 preamble，跳过
        }
        heading?.let { h -> result.add(Section(h, buffer.toString().trim())) }
        return result
    }
}
