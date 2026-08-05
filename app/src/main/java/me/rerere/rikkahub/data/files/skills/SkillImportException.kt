package me.rerere.rikkahub.data.files.skills

/**
 * 格式适配器导入失败时抛出。
 *
 * @param warnings 已成功解析但需提示用户的信息(部分失败时已成功的 skill 不回滚)
 */
class SkillImportException(
    message: String,
    val warnings: List<String> = emptyList(),
) : Exception(message)
