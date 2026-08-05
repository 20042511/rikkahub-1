package me.rerere.rikkahub.data.files.skills

import org.junit.Assert.assertEquals
import org.junit.Test

class SkillNamesTest {

    @Test
    fun `normalize converts spaces and underscores to hyphens`() {
        assertEquals("vue-components", SkillNames.normalize("Vue Components"))
        assertEquals("api-v2", SkillNames.normalize("API_v2"))
    }

    @Test
    fun `normalize collapses repeated hyphens and trims`() {
        assertEquals("foo-bar", SkillNames.normalize("--foo--bar--"))
        assertEquals("foo-bar", SkillNames.normalize("foo   bar"))
    }

    @Test
    fun `normalize lowercases and strips invalid chars`() {
        assertEquals("foo-bar-123", SkillNames.normalize("Foo@Bar#123"))
        assertEquals("skill", SkillNames.normalize("___"))
    }

    @Test
    fun `normalize replaces path separators with hyphens`() {
        assertEquals("foo-bar", SkillNames.normalize("foo/bar"))
        assertEquals("foo-bar", SkillNames.normalize("foo\\bar"))
    }

    @Test
    fun `normalize blank falls back to skill`() {
        assertEquals("skill", SkillNames.normalize(""))
        assertEquals("skill", SkillNames.normalize("   "))
    }

    @Test
    fun `fromFileName strips extension and dot prefix`() {
        assertEquals("vue-components", SkillNames.fromFileName("vue-components.mdc"))
        assertEquals("cursorrules", SkillNames.fromFileName(".cursorrules"))
        assertEquals("api-v2", SkillNames.fromFileName("API_v2.md"))
    }

    @Test
    fun `fromFileName handles no extension`() {
        assertEquals("foo", SkillNames.fromFileName("foo"))
    }

    @Test
    fun `withSourcePrefix adds prefix for non-anthropic formats`() {
        assertEquals("cursor-vue", SkillNames.withSourcePrefix("vue", SkillSourceFormat.CURSOR_MDC))
        assertEquals("cursor-vue", SkillNames.withSourcePrefix("vue", SkillSourceFormat.CURSOR_RULES_LEGACY))
        assertEquals("claude-md-notes", SkillNames.withSourcePrefix("notes", SkillSourceFormat.FLAT_MARKDOWN))
        assertEquals("windsurf-x", SkillNames.withSourcePrefix("x", SkillSourceFormat.WINDSURF_RULE))
        assertEquals("kiro-x", SkillNames.withSourcePrefix("x", SkillSourceFormat.KIRO_STEERING))
        assertEquals("cline-x", SkillNames.withSourcePrefix("x", SkillSourceFormat.CLINE_RULE))
        assertEquals("copilot-x", SkillNames.withSourcePrefix("x", SkillSourceFormat.COPILOT_INSTRUCTION))
    }

    @Test
    fun `withSourcePrefix returns name unchanged for anthropic and unknown`() {
        assertEquals("vue", SkillNames.withSourcePrefix("vue", SkillSourceFormat.ANTHROPIC_SKILL_MD))
        assertEquals("vue", SkillNames.withSourcePrefix("vue", SkillSourceFormat.UNKNOWN))
    }
}
