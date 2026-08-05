package me.rerere.rikkahub.data.files.skills

import me.rerere.rikkahub.data.files.skills.readers.AnthropicSkillReader
import me.rerere.rikkahub.data.files.skills.readers.ClineRuleReader
import me.rerere.rikkahub.data.files.skills.readers.CopilotInstructionReader
import me.rerere.rikkahub.data.files.skills.readers.CursorRuleReader
import me.rerere.rikkahub.data.files.skills.readers.FlatMarkdownReader
import me.rerere.rikkahub.data.files.skills.readers.KiroSteeringReader
import me.rerere.rikkahub.data.files.skills.readers.RawSkillInput
import me.rerere.rikkahub.data.files.skills.readers.WindsurfRuleReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillFormatAdapterTest {

    private val adapter = SkillFormatAdapter(
        readers = listOf(
            AnthropicSkillReader(),
            CursorRuleReader(),
            FlatMarkdownReader(),
            ClineRuleReader(),
            WindsurfRuleReader(),
            KiroSteeringReader(),
            CopilotInstructionReader(),
        ),
    )

    @Test
    fun `detectFormat recognizes Anthropic SKILL.md`() {
        val input = RawSkillInput(
            fileName = "SKILL.md",
            relativePath = "my-skill/SKILL.md",
            content = "---\nname: my-skill\ndescription: test\n---\nbody",
        )

        assertEquals(SkillSourceFormat.ANTHROPIC_SKILL_MD, adapter.detectFormat(input))
    }

    @Test
    fun `detectFormat recognizes Cursor mdc`() {
        val input = RawSkillInput(
            fileName = "vue.mdc",
            relativePath = ".cursor/rules/vue.mdc",
            content = "---\ndescription: vue\nglobs: *.vue\n---\nbody",
        )

        assertEquals(SkillSourceFormat.CURSOR_MDC, adapter.detectFormat(input))
    }

    @Test
    fun `detectFormat recognizes legacy cursorrules`() {
        val input = RawSkillInput(
            fileName = ".cursorrules",
            relativePath = ".cursorrules",
            content = "always apply this rule",
        )

        assertEquals(SkillSourceFormat.CURSOR_MDC, adapter.detectFormat(input))
    }

    @Test
    fun `detectFormat recognizes flat markdown CLAUDE.md`() {
        val input = RawSkillInput(
            fileName = "CLAUDE.md",
            relativePath = "CLAUDE.md",
            content = "Some preamble\n## Section A\nbody a\n## Section B\nbody b",
        )

        assertEquals(SkillSourceFormat.FLAT_MARKDOWN, adapter.detectFormat(input))
    }

    @Test
    fun `detectFormat recognizes Cline rule`() {
        val input = RawSkillInput(
            fileName = "memory.md",
            relativePath = ".clinerules/memory.md",
            content = "remember stuff",
        )

        assertEquals(SkillSourceFormat.CLINE_RULE, adapter.detectFormat(input))
    }

    @Test
    fun `detectFormat recognizes Windsurf rule`() {
        val input = RawSkillInput(
            fileName = "vue.md",
            relativePath = ".windsurf/rules/vue.md",
            content = "---\ntrigger: always_on\n---\nbody",
        )

        assertEquals(SkillSourceFormat.WINDSURF_RULE, adapter.detectFormat(input))
    }

    @Test
    fun `detectFormat recognizes Kiro steering`() {
        val input = RawSkillInput(
            fileName = "spec.md",
            relativePath = ".kiro/steering/spec.md",
            content = "---\ninclusion: always\n---\nbody",
        )

        assertEquals(SkillSourceFormat.KIRO_STEERING, adapter.detectFormat(input))
    }

    @Test
    fun `detectFormat recognizes Copilot instruction`() {
        val input = RawSkillInput(
            fileName = "typescript.instructions.md",
            relativePath = ".github/instructions/typescript.instructions.md",
            content = "---\napplyTo: '**/*.ts'\n---\nbody",
        )

        assertEquals(SkillSourceFormat.COPILOT_INSTRUCTION, adapter.detectFormat(input))
    }

    @Test
    fun `detectFormat returns UNKNOWN for plain markdown without frontmatter`() {
        val input = RawSkillInput(
            fileName = "notes.md",
            relativePath = "notes.md",
            content = "Just some notes",
        )

        assertEquals(SkillSourceFormat.UNKNOWN, adapter.detectFormat(input))
    }

    @Test
    fun `detectFormat returns ANTHROPIC for file with frontmatter but no reader claims`() {
        val input = RawSkillInput(
            fileName = "random.md",
            relativePath = "random.md",
            content = "---\nfoo: bar\n---\nbody",
        )

        // no Reader claims "random.md"; fallback rule treats `---` as Anthropic best-effort
        assertEquals(SkillSourceFormat.ANTHROPIC_SKILL_MD, adapter.detectFormat(input))
    }

    @Test
    fun `import Anthropic SKILL.md preserves name description and body`() {
        val input = RawSkillInput(
            fileName = "SKILL.md",
            relativePath = "my-skill/SKILL.md",
            content = "---\nname: my-skill\ndescription: does the thing\n---\n\n# Instructions\nDo X then Y",
        )

        val skills = adapter.import(input)

        assertEquals(1, skills.size)
        val skill = skills.first()
        assertEquals("my-skill", skill.name)
        assertEquals("does the thing", skill.description)
        assertTrue("body preserved", skill.body.contains("Do X then Y"))
        assertEquals(SkillSourceFormat.ANTHROPIC_SKILL_MD, skill.sourceFormat)
    }

    @Test
    fun `import Anthropic derives description from body when missing`() {
        val input = RawSkillInput(
            fileName = "SKILL.md",
            relativePath = "my-skill/SKILL.md",
            content = "---\nname: my-skill\n---\n\nFirst line becomes description\nMore body",
        )

        val skills = adapter.import(input)

        assertEquals(1, skills.size)
        assertEquals("First line becomes description", skills.first().description)
    }

    @Test
    fun `import Anthropic YAML array allowed-tools parsed as list`() {
        val input = RawSkillInput(
            fileName = "SKILL.md",
            relativePath = "my-skill/SKILL.md",
            content = """
                ---
                name: my-skill
                description: test
                allowed-tools:
                  - Bash
                  - Read
                ---
                body
            """.trimIndent(),
        )

        val skills = adapter.import(input)

        assertEquals(listOf("Bash", "Read"), skills.first().allowedTools)
    }

    @Test
    fun `import Cursor mdc maps globs to allowedTools and alwaysApply to compatibility`() {
        val input = RawSkillInput(
            fileName = "vue.mdc",
            relativePath = ".cursor/rules/vue.mdc",
            content = """
                ---
                description: Vue rules
                globs: src/**/*.vue
                alwaysApply: true
                ---
                body
            """.trimIndent(),
        )

        val skills = adapter.import(input)

        assertEquals(1, skills.size)
        val skill = skills.first()
        assertEquals("vue", skill.name)
        assertEquals(listOf("src/**/*.vue"), skill.allowedTools)
        assertEquals("always", skill.compatibility)
    }

    @Test
    fun `import Cursor mdc handles comma-separated globs`() {
        val input = RawSkillInput(
            fileName = "ts.mdc",
            relativePath = ".cursor/rules/ts.mdc",
            content = """
                ---
                description: ts rules
                globs: "*.ts, *.tsx, src/**/*.ts"
                ---
                body
            """.trimIndent(),
        )

        val skills = adapter.import(input)

        assertEquals(3, skills.first().allowedTools.size)
        assertTrue(skills.first().allowedTools.contains("*.ts"))
        assertTrue(skills.first().allowedTools.contains("*.tsx"))
        assertTrue(skills.first().allowedTools.contains("src/**/*.ts"))
    }

    @Test
    fun `import legacy cursorrules treats as always compatibility`() {
        val input = RawSkillInput(
            fileName = ".cursorrules",
            relativePath = ".cursorrules",
            content = "Always write tests\nFor every function",
        )

        val skills = adapter.import(input)

        assertEquals(1, skills.size)
        val skill = skills.first()
        assertEquals("cursorrules", skill.name)
        assertEquals("always", skill.compatibility)
        assertEquals(SkillSourceFormat.CURSOR_RULES_LEGACY, skill.sourceFormat)
        assertTrue("body is whole file", skill.body.contains("Always write tests"))
    }

    @Test
    fun `import FlatMarkdown CLAUDE.md splits by level-2 headings`() {
        val input = RawSkillInput(
            fileName = "CLAUDE.md",
            relativePath = "CLAUDE.md",
            content = """
                Preamble that should be skipped

                ## Vue Components
                Vue body content
                ### Sub heading stays in body
                more vue

                ## API Design
                API body content
            """.trimIndent(),
        )

        val skills = adapter.import(input)

        assertEquals(2, skills.size)
        val names = skills.map { it.name }
        assertTrue("vue-components section", names.contains("vue-components"))
        assertTrue("api-design section", names.contains("api-design"))
        // Sub heading (###) should remain in the vue section body, not split into its own skill
        val vueSkill = skills.first { it.name == "vue-components" }
        assertTrue("sub heading preserved in body", vueSkill.body.contains("Sub heading stays in body"))
    }

    @Test
    fun `import FlatMarkdown without headings produces single skill with name from filename`() {
        val input = RawSkillInput(
            fileName = "CLAUDE.md",
            relativePath = "CLAUDE.md",
            content = "Just a flat doc\nwith no headings",
        )

        val skills = adapter.import(input)

        assertEquals(1, skills.size)
        assertEquals("claude", skills.first().name)
    }

    @Test
    fun `import Cline rule treats whole file as body`() {
        val input = RawSkillInput(
            fileName = "memory.md",
            relativePath = ".clinerules/memory.md",
            content = "Remember the user prefers concise answers",
        )

        val skills = adapter.import(input)

        assertEquals(1, skills.size)
        val skill = skills.first()
        assertEquals("memory", skill.name)
        assertTrue(skill.body.contains("Remember the user prefers"))
        assertEquals(SkillSourceFormat.CLINE_RULE, skill.sourceFormat)
    }

    @Test
    fun `import Windsurf maps trigger always_on to always compatibility`() {
        val input = RawSkillInput(
            fileName = "global.md",
            relativePath = ".windsurf/rules/global.md",
            content = """
                ---
                description: global rule
                trigger: always_on
                ---
                body
            """.trimIndent(),
        )

        val skills = adapter.import(input)

        val skill = skills.first()
        assertEquals("always", skill.compatibility)
        // always_on should NOT surface globs as allowedTools
        assertTrue(skill.allowedTools.isEmpty())
    }

    @Test
    fun `import Windsurf maps trigger glob to allowedTools`() {
        val input = RawSkillInput(
            fileName = "ts.md",
            relativePath = ".windsurf/rules/ts.md",
            content = """
                ---
                description: ts rule
                trigger: glob
                globs: "*.ts, *.tsx"
                ---
                body
            """.trimIndent(),
        )

        val skills = adapter.import(input)

        val skill = skills.first()
        assertEquals(listOf("*.ts", "*.tsx"), skill.allowedTools)
        assertEquals(null, skill.compatibility)
    }

    @Test
    fun `import Kiro maps inclusion always to compatibility`() {
        val input = RawSkillInput(
            fileName = "product.md",
            relativePath = ".kiro/steering/product.md",
            content = """
                ---
                description: product context
                inclusion: always
                ---
                body
            """.trimIndent(),
        )

        val skills = adapter.import(input)

        assertEquals("always", skills.first().compatibility)
    }

    @Test
    fun `import Kiro maps inclusion fileMatch to allowedTools`() {
        val input = RawSkillInput(
            fileName = "api.md",
            relativePath = ".kiro/steering/api.md",
            content = """
                ---
                description: api rules
                inclusion: fileMatch
                globs:
                  - "**/api/**/*.go"
                ---
                body
            """.trimIndent(),
        )

        val skills = adapter.import(input)

        val skill = skills.first()
        assertEquals(listOf("**/api/**/*.go"), skill.allowedTools)
        assertEquals(null, skill.compatibility)
    }

    @Test
    fun `import Copilot maps applyTo to allowedTools`() {
        val input = RawSkillInput(
            fileName = "ts.instructions.md",
            relativePath = ".github/instructions/ts.instructions.md",
            content = """
                ---
                applyTo: "**/*.ts"
                description: ts instructions
                ---
                body
            """.trimIndent(),
        )

        val skills = adapter.import(input)

        val skill = skills.first()
        assertEquals(listOf("**/*.ts"), skill.allowedTools)
        // name strips -instructions suffix
        assertEquals("ts", skill.name)
    }

    @Test
    fun `import collects extraFiles from siblings for Anthropic`() {
        val siblings = mapOf(
            "my-skill/references/api.md" to "ref content".toByteArray(),
            "my-skill/scripts/run.sh" to "echo hi".toByteArray(),
            "my-skill/SKILL.md" to "should-be-skipped".toByteArray(),
        )
        val input = RawSkillInput(
            fileName = "SKILL.md",
            relativePath = "my-skill/SKILL.md",
            content = "---\nname: my-skill\ndescription: test\n---\nbody",
            siblingFiles = siblings,
        )

        val skills = adapter.import(input)

        val extra = skills.first().extraFiles
        assertEquals("ref content", extra["references/api.md"]?.toString(Charsets.UTF_8))
        assertEquals("echo hi", extra["scripts/run.sh"]?.toString(Charsets.UTF_8))
        // SKILL.md itself must not be collected as extra
        assertTrue("SKILL.md not in extra", !extra.containsKey("SKILL.md"))
    }

    @Test(expected = SkillImportException::class)
    fun `import throws when no skill derivable`() {
        val input = RawSkillInput(
            fileName = "empty.md",
            relativePath = "empty.md",
            content = "",
        )
        adapter.import(input)
    }

    @Test
    fun `importMany collects successes and errors without aborting batch`() {
        val goodInput = RawSkillInput(
            fileName = "SKILL.md",
            relativePath = "good/SKILL.md",
            content = "---\nname: good\ndescription: ok\n---\nbody",
        )
        val anotherGood = RawSkillInput(
            fileName = "vue.mdc",
            relativePath = ".cursor/rules/vue.mdc",
            content = "---\ndescription: vue\n---\nbody",
        )

        val result = adapter.importMany(listOf(goodInput, anotherGood))

        assertEquals(2, result.skills.size)
        assertTrue("no errors for valid inputs", result.errors.isEmpty())
    }

    @Test
    fun `importMany records errors but keeps successful imports`() {
        val goodInput = RawSkillInput(
            fileName = "SKILL.md",
            relativePath = "good/SKILL.md",
            content = "---\nname: good\ndescription: ok\n---\nbody",
        )
        // empty content can't derive a skill
        val badInput = RawSkillInput(
            fileName = "empty.md",
            relativePath = "empty.md",
            content = "",
        )

        val result = adapter.importMany(listOf(goodInput, badInput))

        assertEquals(1, result.skills.size)
        assertEquals("good", result.skills.first().name)
        assertTrue("error recorded", result.errors.isNotEmpty())
    }
}
