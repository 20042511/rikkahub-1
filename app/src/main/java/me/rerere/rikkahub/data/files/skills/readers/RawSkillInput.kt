package me.rerere.rikkahub.data.files.skills.readers

import me.rerere.rikkahub.data.files.skills.ImportedSkill
import me.rerere.rikkahub.data.files.skills.SkillSourceFormat

/**
 * 待解析的原始 skill 文件输入。
 *
 * @param fileName 文件名(如 "vue.mdc" / "SKILL.md" / ".cursorrules")
 * @param relativePath 相对路径(含目录上下文，如 ".cursor/rules/vue.mdc")，用于路径型格式检测
 * @param content 文件文本内容
 * @param siblingFiles 同目录/同 skill 包内的其它文件(relativePath -> bytes)，
 *   用于关联 references/ scripts/ 等附带资源(保留二进制)；单文件导入时为空
 */
data class RawSkillInput(
    val fileName: String,
    val relativePath: String,
    val content: String,
    val siblingFiles: Map<String, ByteArray> = emptyMap(),
) {
    val lowerFileName: String get() = fileName.lowercase()
    val normalizedPath: String get() = relativePath.replace('\\', '/').trimStart('/')
}

/**
 * 格式 Reader：把一种外部格式解析为 [ImportedSkill] 列表。
 *
 * 一个 Reader 可产出多个 skill(如扁平 markdown 按 `## 标题` 拆分)。
 */
interface SkillReader {
    val sourceFormat: SkillSourceFormat
    fun canHandle(input: RawSkillInput): Boolean
    fun read(input: RawSkillInput): List<ImportedSkill>
}
