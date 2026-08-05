package me.rerere.rikkahub.data.files.skills

/**
 * 所有 Reader 产出的统一中间模型，[NativeSkillWriter] 消费它落盘为 rikkahub 原生 skill。
 *
 * @param name 规范化后的 skill 名(kebab-case + 路径安全)，作为目录名
 * @param description 触发描述；多行块标量已合并为单串
 * @param body markdown 指令正文(frontmatter 之后的内容)
 * @param allowedTools 关联工具/路径提示(来自 allowed-tools / globs / applyTo 等)
 * @param compatibility 兼容性标记，如 "always" 表示常驻注入
 * @param extraFiles 附带资源(relativePath -> bytes)，如 references/scripts/assets(保留二进制)
 * @param warnings 转换过程中的提示，会透传给用户
 */
data class ImportedSkill(
    val name: String,
    val description: String,
    val body: String,
    val allowedTools: List<String> = emptyList(),
    val compatibility: String? = null,
    val sourceFormat: SkillSourceFormat,
    val extraFiles: Map<String, ByteArray> = emptyMap(),
    val warnings: List<String> = emptyList(),
)
