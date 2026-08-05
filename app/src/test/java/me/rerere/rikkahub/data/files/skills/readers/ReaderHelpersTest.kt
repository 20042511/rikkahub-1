package me.rerere.rikkahub.data.files.skills.readers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderHelpersTest {

    @Test
    fun `collectExtraFiles strips basePath prefix from siblings`() {
        val siblings = mapOf(
            "my-skill/references/api.md" to "ref".toByteArray(),
            "my-skill/scripts/run.sh" to "echo hi".toByteArray(),
            "my-skill/SKILL.md" to "skip".toByteArray(),
        )

        val extra = ReaderHelpers.collectExtraFiles(siblings, basePath = "my-skill")

        assertEquals("ref", extra["references/api.md"]?.toString(Charsets.UTF_8))
        assertEquals("echo hi", extra["scripts/run.sh"]?.toString(Charsets.UTF_8))
        assertFalse("SKILL.md not collected", extra.containsKey("SKILL.md"))
    }

    @Test
    fun `collectExtraFiles preserves binary resources`() {
        val binary = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) // PNG header
        val siblings = mapOf(
            "my-skill/assets/logo.png" to binary,
        )

        val extra = ReaderHelpers.collectExtraFiles(siblings, basePath = "my-skill")

        val out = extra["assets/logo.png"]
        assertTrue("binary preserved", out != null)
        assertEquals(binary.toList(), out!!.toList())
    }

    @Test
    fun `collectExtraFiles handles empty basePath as root`() {
        val siblings = mapOf(
            "references/x.md" to "x".toByteArray(),
            "scripts/y.sh" to "y".toByteArray(),
        )

        val extra = ReaderHelpers.collectExtraFiles(siblings, basePath = "")

        assertEquals(2, extra.size)
        assertEquals("x", extra["references/x.md"]?.toString(Charsets.UTF_8))
    }

    @Test
    fun `collectExtraFiles skips files outside basePath`() {
        val siblings = mapOf(
            "skill-a/references/a.md" to "a".toByteArray(),
            "skill-b/references/b.md" to "b".toByteArray(),
        )

        val extra = ReaderHelpers.collectExtraFiles(siblings, basePath = "skill-a")

        assertEquals(1, extra.size)
        assertEquals("a", extra["references/a.md"]?.toString(Charsets.UTF_8))
    }

    @Test
    fun `collectExtraFiles keeps references under dotfile-style dirs`() {
        val siblings = mapOf(
            "my-skill/.github/workflows/ci.yml" to "ci".toByteArray(),
        )

        val extra = ReaderHelpers.collectExtraFiles(siblings, basePath = "my-skill")

        // .github/ should be preserved (not skipped as a hidden file)
        assertTrue(".github/ preserved", extra.containsKey(".github/workflows/ci.yml"))
    }

    @Test
    fun `parseGlobsToList handles bare comma string`() {
        val result = ReaderHelpers.parseGlobsToList("*.ts, *.tsx, src/**/*.ts")

        assertEquals(3, result.size)
        assertTrue(result.contains("*.ts"))
        assertTrue(result.contains("*.tsx"))
        assertTrue(result.contains("src/**/*.ts"))
    }

    @Test
    fun `parseGlobsToList handles YAML list`() {
        val result = ReaderHelpers.parseGlobsToList(listOf("**/*.go", "**/*.ts"))

        assertEquals(2, result.size)
        assertTrue(result.contains("**/*.go"))
        assertTrue(result.contains("**/*.ts"))
    }

    @Test
    fun `parseGlobsToList null returns empty`() {
        assertEquals(emptyList<String>(), ReaderHelpers.parseGlobsToList(null))
    }

    @Test
    fun `deriveDescription uses first non-heading non-empty line`() {
        val body = """
            # Heading

            This becomes the description

            More body
        """.trimIndent()

        assertEquals("This becomes the description", ReaderHelpers.deriveDescription(body, "fallback"))
    }

    @Test
    fun `deriveDescription falls back when body is empty or only headings`() {
        assertEquals("fallback", ReaderHelpers.deriveDescription("", "fallback"))
        assertEquals("fallback", ReaderHelpers.deriveDescription("# only heading", "fallback"))
    }

    @Test
    fun `deriveDescription truncates very long first line`() {
        val long = "x".repeat(300)
        val result = ReaderHelpers.deriveDescription(long, "fallback")

        assertTrue("truncated", result.length <= 201) // 200 + ellipsis
        assertTrue(result.endsWith("…"))
    }
}
