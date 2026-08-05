package me.rerere.rikkahub.data.files.skills

import me.rerere.rikkahub.data.files.skills.readers.AnthropicSkillReader
import me.rerere.rikkahub.data.files.skills.readers.RawSkillInput
import me.rerere.rikkahub.data.files.skills.readers.SkillReader

/**
 * 格式适配器入口：检测外部格式 → 分发到对应 [SkillReader] → 产出 [ImportedSkill] 列表。
 *
 * Reader 检测优先级由构造时传入顺序决定；无法识别但含 frontmatter 的文件
 * 按 Anthropic SKILL.md best-effort 处理；否则整文件作 body 兜底。
 *
 * @param readers 按优先级排序的 Reader 列表
 */
class SkillFormatAdapter(
    private val readers: List<SkillReader>,
) {
    /**
     * 检测格式。按 readers 顺序找第一个 canHandle；都不命中则按是否有 frontmatter 兜底。
     */
    fun detectFormat(input: RawSkillInput): SkillSourceFormat {
        return readers.firstOrNull { it.canHandle(input) }?.sourceFormat
            ?: if (input.content.startsWith("---")) {
                SkillSourceFormat.ANTHROPIC_SKILL_MD
            } else {
                SkillSourceFormat.UNKNOWN
            }
    }

    /**
     * 解析单个输入为 [ImportedSkill] 列表(一个 Reader 可产出多个，如扁平 markdown 按 ## 拆分)。
     *
     * @throws SkillImportException 解析失败(必填字段派生不出)
     */
    fun import(input: RawSkillInput): List<ImportedSkill> {
        val reader = readers.firstOrNull { it.canHandle(input) }
        val skills = if (reader != null) {
            reader.read(input)
        } else {
            // 兜底：含 frontmatter 按 Anthropic best-effort；否则整文件作 body
            fallbackRead(input)
        }
        if (skills.isEmpty()) {
            throw SkillImportException("无法从 ${input.fileName} 解析出 skill")
        }
        return skills
    }

    /**
     * 批量导入(用于 zip / GitHub 多文件)。逐个解析，失败的文件收集为 warning 不中断。
     *
     * @return 所有成功解析的 ImportedSkill(扁平)；每个 skill 带 sourceFormat 标记
     */
    fun importMany(inputs: List<RawSkillInput>): BatchImportResult {
        val all = mutableListOf<ImportedSkill>()
        val errors = mutableListOf<String>()
        for (input in inputs) {
            val result = runCatching { import(input) }
            result.onSuccess { all += it }
                .onFailure { errors += "${input.relativePath}: ${it.message}" }
        }
        if (all.isEmpty() && errors.isNotEmpty()) {
            throw SkillImportException("全部文件解析失败", errors)
        }
        return BatchImportResult(skills = all, errors = errors)
    }

    private fun fallbackRead(input: RawSkillInput): List<ImportedSkill> {
        if (input.content.startsWith("---")) {
            // 含 frontmatter 但无 Reader 认领：用 AnthropicReader best-effort
            val anthropic = readers.filterIsInstance<AnthropicSkillReader>().firstOrNull()
                ?: AnthropicSkillReader()
            return runCatching { anthropic.read(input) }.getOrElse {
                listOf(readUnknown(input))
            }
        }
        return listOf(readUnknown(input))
    }

    private fun readUnknown(input: RawSkillInput): ImportedSkill {
        if (input.content.isBlank()) {
            throw SkillImportException("无法从 ${input.fileName} 解析出 skill(内容为空)")
        }
        val name = SkillNames.fromFileName(input.fileName)
        val description = me.rerere.rikkahub.data.files.skills.readers.ReaderHelpers
            .deriveDescription(input.content, "Imported from ${input.fileName}")
        return ImportedSkill(
            name = name,
            description = description,
            body = input.content,
            sourceFormat = SkillSourceFormat.UNKNOWN,
            warnings = listOf("未识别的格式，整文件作为 skill 导入"),
        )
    }

    data class BatchImportResult(
        val skills: List<ImportedSkill>,
        val errors: List<String>,
    )
}
