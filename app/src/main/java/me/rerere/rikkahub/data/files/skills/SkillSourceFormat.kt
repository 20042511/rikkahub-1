package me.rerere.rikkahub.data.files.skills

/**
 * 外部 skill / 规则文件的来源格式。
 *
 * 每种格式由对应的 [me.rerere.rikkahub.data.files.skills.readers.SkillReader] 解析，
 * 最终统一转换为 rikkahub 原生 SKILL.md 格式落盘。
 */
enum class SkillSourceFormat(val displayName: String) {
    ANTHROPIC_SKILL_MD("Anthropic SKILL.md"),
    CURSOR_MDC("Cursor .mdc"),
    CURSOR_RULES_LEGACY("Cursor .cursorrules"),
    FLAT_MARKDOWN("Flat Markdown (CLAUDE.md/GEMINI.md)"),
    WINDSURF_RULE("Windsurf Rule"),
    KIRO_STEERING("Kiro Steering"),
    CLINE_RULE("Cline Rule"),
    COPILOT_INSTRUCTION("Copilot Instruction"),
    UNKNOWN("Unknown"),
}
