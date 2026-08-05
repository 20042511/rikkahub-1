package me.rerere.rikkahub.data.files.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSkillWriterTest {

    @Test
    fun `toSkillFiles always emits SKILL.md entry`() {
        val skill = ImportedSkill(
            name = "demo-skill",
            description = "demo",
            body = "Hello world",
            sourceFormat = SkillSourceFormat.ANTHROPIC_SKILL_MD,
        )

        val files = NativeSkillWriter.toSkillFiles(skill)

        assertTrue("SKILL.md must be present", files.containsKey("SKILL.md"))
    }

    @Test
    fun `toSkillFiles writes extraFiles under sanitized relative paths`() {
        val skill = ImportedSkill(
            name = "demo",
            description = "demo",
            body = "body",
            sourceFormat = SkillSourceFormat.ANTHROPIC_SKILL_MD,
            extraFiles = mapOf(
                "references/api.md" to "ref".toByteArray(),
                "scripts/run.sh" to "echo hi".toByteArray(),
            ),
        )

        val files = NativeSkillWriter.toSkillFiles(skill)

        assertEquals("ref", files["references/api.md"]?.toString(Charsets.UTF_8))
        assertEquals("echo hi", files["scripts/run.sh"]?.toString(Charsets.UTF_8))
    }

    @Test
    fun `toSkillFiles rejects traversal in extraFiles paths`() {
        val skill = ImportedSkill(
            name = "demo",
            description = "demo",
            body = "body",
            sourceFormat = SkillSourceFormat.ANTHROPIC_SKILL_MD,
            extraFiles = mapOf(
                "../escape.md" to "evil".toByteArray(),
                "../../etc/passwd" to "evil".toByteArray(),
            ),
        )

        val files = NativeSkillWriter.toSkillFiles(skill)

        assertTrue("SKILL.md present", files.containsKey("SKILL.md"))
        assertEquals("only SKILL.md survives", 1, files.size)
    }

    @Test
    fun `SKILL.md contains name description and body in order`() {
        val skill = ImportedSkill(
            name = "vue-components",
            description = "Vue component scaffolding",
            body = "Instructions here",
            sourceFormat = SkillSourceFormat.ANTHROPIC_SKILL_MD,
        )

        val md = NativeSkillWriter.toSkillFiles(skill)["SKILL.md"]!!

        assertTrue("starts with frontmatter", md.startsWith("---\n"))
        assertTrue("has name", md.contains("name: vue-components"))
        assertTrue("has description", md.contains("description: Vue component scaffolding"))
        assertTrue("has body", md.contains("Instructions here"))
        // name before description
        assertTrue("name before description", md.indexOf("name:") < md.indexOf("description:"))
    }

    @Test
    fun `allowed-tools serialized as space separated list`() {
        val skill = ImportedSkill(
            name = "demo",
            description = "demo",
            body = "body",
            allowedTools = listOf("src/**/*.ts", "src/**/*.tsx"),
            sourceFormat = SkillSourceFormat.CURSOR_MDC,
        )

        val md = NativeSkillWriter.toSkillFiles(skill)["SKILL.md"]!!

        assertTrue("allowed-tools present", md.contains("allowed-tools:"))
        assertTrue("first glob", md.contains("src/**/*.ts"))
        assertTrue("second glob", md.contains("src/**/*.tsx"))
    }

    @Test
    fun `compatibility serialized when set`() {
        val skill = ImportedSkill(
            name = "demo",
            description = "demo",
            body = "body",
            compatibility = "always",
            sourceFormat = SkillSourceFormat.CURSOR_RULES_LEGACY,
        )

        val md = NativeSkillWriter.toSkillFiles(skill)["SKILL.md"]!!

        assertTrue("compatibility present", md.contains("compatibility: always"))
    }

    @Test
    fun `long description uses block scalar to preserve newlines`() {
        val longDesc = "Line one\nLine two\n" + "x".repeat(250)

        val skill = ImportedSkill(
            name = "demo",
            description = longDesc,
            body = "body",
            sourceFormat = SkillSourceFormat.ANTHROPIC_SKILL_MD,
        )

        val md = NativeSkillWriter.toSkillFiles(skill)["SKILL.md"]!!

        // |- block scalar marker
        assertTrue("block scalar used", md.contains("description: |"))
        assertTrue("first line preserved", md.contains("Line one"))
        assertTrue("second line preserved", md.contains("Line two"))
    }

    @Test
    fun `description with colon gets quoted`() {
        val skill = ImportedSkill(
            name = "demo",
            description = "Note: this is important",
            body = "body",
            sourceFormat = SkillSourceFormat.ANTHROPIC_SKILL_MD,
        )

        val md = NativeSkillWriter.toSkillFiles(skill)["SKILL.md"]!!

        assertTrue("description quoted", md.contains("description: \"Note: this is important\""))
    }
}
