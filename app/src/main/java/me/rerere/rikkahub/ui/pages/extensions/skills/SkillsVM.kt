package me.rerere.rikkahub.ui.pages.extensions.skills

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedHashMap
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.files.FileUtils
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.files.SkillMetadata
import me.rerere.rikkahub.data.files.skills.SkillFormatAdapter
import me.rerere.rikkahub.data.files.skills.readers.RawSkillInput
import org.json.JSONArray

class SkillsVM(
    private val skillManager: SkillManager,
    private val formatAdapter: SkillFormatAdapter,
) : ViewModel() {
    private val _skills = MutableStateFlow<List<SkillMetadata>>(emptyList())
    val skills = _skills.asStateFlow()

    init {
        loadSkills()
    }

    private fun loadSkills() {
        viewModelScope.launch(Dispatchers.IO) {
            _skills.value = skillManager.listSkills()
        }
    }

    fun saveSkill(name: String, content: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = skillManager.saveSkill(name, content)
            _skills.value = skillManager.listSkills()
            withContext(Dispatchers.Main) {
                onResult(result != null)
            }
        }
    }

    fun deleteSkill(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            skillManager.deleteSkill(name)
            _skills.value = skillManager.listSkills()
        }
    }

    fun getSkillsDir() = skillManager.getSkillsDir()

    fun importSkillFromFile(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = FileUtils.getFileNameFromUri(appContext, uri).orEmpty()
                val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: run {
                        withContext(Dispatchers.Main) { onResult(false, "无法读取文件") }
                        return@launch
                    }

                val importedNames = if (isZipFile(fileName, bytes)) {
                    importSkillsFromZip(bytes)
                } else {
                    importSkillMarkdown(bytes, fileName)
                }

                _skills.value = skillManager.listSkills()
                withContext(Dispatchers.Main) {
                    onResult(true, importedNames.joinToString())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "未知错误") }
            }
        }
    }

    fun importSkillFromGitHub(repoUrl: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = parseGitHubUrl(repoUrl) ?: run {
                    withContext(Dispatchers.Main) { onResult(false, "无效的 GitHub 仓库链接") }
                    return@launch
                }

                // Collect all files recursively via GitHub Contents API
                val pathToDownloadUrl = mutableListOf<Pair<String, String>>() // relativePath -> downloadUrl
                val listed = listFilesRecursively(info.owner, info.repo, info.branch, info.path, info.path, pathToDownloadUrl)
                if (!listed) {
                    withContext(Dispatchers.Main) { onResult(false, "读取 GitHub 目录失败") }
                    return@launch
                }

                val candidatePaths = findCandidateSkillFiles(pathToDownloadUrl.map { it.first })
                if (candidatePaths.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "目录中未找到可识别的 skill 文件(SKILL.md/.mdc/.cursorrules/CLAUDE.md 等)")
                    }
                    return@launch
                }

                val files = LinkedHashMap<String, ByteArray>()
                for ((relativePath, downloadUrl) in pathToDownloadUrl) {
                    val content = downloadBytes(downloadUrl) ?: run {
                        withContext(Dispatchers.Main) { onResult(false, "下载文件失败：$relativePath") }
                        return@launch
                    }
                    files[relativePath] = content
                }

                val importedNames = importFromFilesMap(files)

                _skills.value = skillManager.listSkills()
                withContext(Dispatchers.Main) {
                    onResult(true, importedNames.joinToString())
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "未知错误") }
            }
        }
    }

    /**
     * 单文件导入：直接交给格式适配器。无 frontmatter 的纯 markdown 也会作为 UNKNOWN skill 兜底导入。
     */
    private fun importSkillMarkdown(bytes: ByteArray, fileName: String): List<String> {
        val content = bytes.toString(Charsets.UTF_8)
        val input = RawSkillInput(
            fileName = fileName.ifBlank { "imported.md" },
            relativePath = fileName,
            content = content,
            siblingFiles = emptyMap(),
        )
        val imported = formatAdapter.import(input)
        return imported.mapNotNull { skillManager.importSkill(it)?.name }
    }

    private fun importSkillsFromZip(bytes: ByteArray): List<String> {
        val files = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zipInput ->
            while (true) {
                val entry = zipInput.nextEntry ?: break
                try {
                    if (!entry.isDirectory) {
                        val path = normalizeZipEntryPath(entry.name) ?: continue
                        files[path] = zipInput.readBytes()
                    }
                } finally {
                    zipInput.closeEntry()
                }
            }
        }
        return importFromFilesMap(files)
    }

    /**
     * 从 (relativePath -> bytes) 映射批量导入：检测候选 skill 文件，为每个构建 [RawSkillInput]
     * (含同目录附带资源作为 siblingFiles)，交给 [SkillFormatAdapter] 解析后落盘。
     */
    private fun importFromFilesMap(files: Map<String, ByteArray>): List<String> {
        val candidatePaths = findCandidateSkillFiles(files.keys)
        if (candidatePaths.isEmpty()) {
            error("未找到可识别的 skill 文件(SKILL.md/.mdc/.cursorrules/CLAUDE.md 等)")
        }
        val candidatePathSet = candidatePaths.toSet()
        val importedNames = mutableListOf<String>()
        val errors = mutableListOf<String>()

        for (candidatePath in candidatePaths) {
            val basePath = candidatePath.substringBeforeLast('/', missingDelimiterValue = "")
            val content = files[candidatePath]?.toString(Charsets.UTF_8) ?: continue
            val siblings = buildSiblingFiles(candidatePath, basePath, files, candidatePathSet)
            val input = RawSkillInput(
                fileName = candidatePath.substringAfterLast('/'),
                relativePath = candidatePath,
                content = content,
                siblingFiles = siblings,
            )
            runCatching { formatAdapter.import(input) }
                .onSuccess { skills ->
                    for (skill in skills) {
                        val saved = skillManager.importSkill(skill)
                        if (saved != null) {
                            importedNames += saved.name
                        } else {
                            errors += "保存失败：${skill.name}"
                        }
                    }
                }
                .onFailure { e ->
                    errors += "$candidatePath: ${e.message}"
                }
        }
        if (importedNames.isEmpty() && errors.isNotEmpty()) {
            error(errors.joinToString("\n"))
        }
        return importedNames.distinct()
    }

    /**
     * 候选 skill 文件检测：覆盖所有 Reader 可识别的格式。
     * 命名型(.mdc/.cursorrules/SKILL.md/CLAUDE.md/GEMINI.md)优先；
     * 路径型(.clinerules/*.md 等)需要文件在特定目录下。
     */
    private fun findCandidateSkillFiles(paths: Collection<String>): List<String> {
        return paths.asSequence()
            .map { it.replace('\\', '/').trimStart('/') }
            .filter { isCandidateSkillFile(it) }
            .sorted()
            .toList()
    }

    private fun isCandidateSkillFile(normalizedPath: String): Boolean {
        val name = normalizedPath.substringAfterLast('/').lowercase()
        if (name == "skill.md" || name == "claude.md" || name == "gemini.md" ||
            name == ".cursorrules" || name.endsWith(".mdc") ||
            name.endsWith(".instructions.md")
        ) {
            return true
        }
        if (!name.endsWith(".md")) return false
        return normalizedPath.contains(".clinerules/") ||
            normalizedPath.contains(".windsurf/rules/") ||
            normalizedPath.contains(".kiro/steering/") ||
            normalizedPath.contains(".github/instructions/")
    }

    /**
     * 为候选 skill 文件收集同包附带资源(relativePath -> bytes)。
     *
     * - 仅收集位于 [candidateBasePath] 子树内的文件
     * - 排除候选文件自身、其它候选文件(避免 .clinerules/foo.md 被收为 bar.md 的资源)
     * - 排除嵌套 skill 包内的文件(沿用原 SKILL.md 嵌套隔离逻辑)
     */
    private fun buildSiblingFiles(
        candidatePath: String,
        candidateBasePath: String,
        allFiles: Map<String, ByteArray>,
        allCandidatePaths: Set<String>,
    ): Map<String, ByteArray> {
        val nestedBasePaths = allCandidatePaths.asSequence()
            .filter { it != candidatePath }
            .map { it.substringBeforeLast('/', missingDelimiterValue = "") }
            .filter { it != candidateBasePath && isPathInsideBase(it, candidateBasePath) }
            .distinct()
            .toList()

        val siblings = LinkedHashMap<String, ByteArray>()
        for ((path, bytes) in allFiles) {
            if (path == candidatePath) continue
            if (!isPathInsideBase(path, candidateBasePath)) continue
            if (path in allCandidatePaths) continue
            if (nestedBasePaths.any { isPathInsideBase(path, it) }) continue
            siblings[path] = bytes
        }
        return siblings
    }

    private fun isPathInsideBase(path: String, basePath: String): Boolean {
        return basePath.isBlank() || path == basePath || path.startsWith("$basePath/")
    }

    private fun normalizeZipEntryPath(path: String): String? {
        val parts = path.replace('\\', '/')
            .trimStart('/')
            .split('/')
            .filter { it.isNotBlank() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }

    private fun isZipFile(fileName: String, bytes: ByteArray): Boolean {
        return fileName.endsWith(".zip", ignoreCase = true) ||
            bytes.startsWithBytes(0x50, 0x4B, 0x03, 0x04) ||
            bytes.startsWithBytes(0x50, 0x4B, 0x05, 0x06) ||
            bytes.startsWithBytes(0x50, 0x4B, 0x07, 0x08)
    }

    private fun ByteArray.startsWithBytes(vararg values: Int): Boolean {
        if (size < values.size) return false
        return values.indices.all { index -> (this[index].toInt() and 0xFF) == values[index] }
    }

    private fun listFilesRecursively(
        owner: String,
        repo: String,
        branch: String,
        dirPath: String,
        basePath: String,
        result: MutableList<Pair<String, String>>,
    ): Boolean {
        val apiUrl = "https://api.github.com/repos/$owner/$repo/contents/$dirPath?ref=$branch"
        val json = downloadText(apiUrl) ?: return false
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val type = item.getString("type")
            val itemPath = item.getString("path")
            val relativePath = itemPath.removePrefix("$basePath/").removePrefix(basePath)
            when (type) {
                "file" -> {
                    val downloadUrl = item.optString("download_url").takeIf { it.isNotBlank() }
                        ?: return false
                    result.add(relativePath to downloadUrl)
                }

                "dir" -> {
                    val ok = listFilesRecursively(owner, repo, branch, itemPath, basePath, result)
                    if (!ok) return false
                }
            }
        }
        return true
    }

    private data class GitHubRepoInfo(
        val owner: String,
        val repo: String,
        val branch: String,
        val path: String,
    )

    private fun parseGitHubUrl(url: String): GitHubRepoInfo? {
        val trimmed = url.trim().trimEnd('/')
        // https://github.com/owner/repo
        // https://github.com/owner/repo/tree/branch
        // https://github.com/owner/repo/tree/branch/sub/path
        val regex = Regex("""https://github\.com/([^/]+)/([^/]+)(?:/tree/([^/]+)(/.*)?)?""")
        val match = regex.matchEntire(trimmed) ?: return null
        val owner = match.groupValues[1]
        val repo = match.groupValues[2]
        val branch = match.groupValues[3].ifBlank { "HEAD" }
        val subPath = match.groupValues[4].trimStart('/')
        return GitHubRepoInfo(owner, repo, branch, subPath)
    }

    private fun downloadText(url: String): String? = downloadBytes(url)?.toString(Charsets.UTF_8)

    private fun downloadBytes(url: String): ByteArray? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        return try {
            if (connection.responseCode == 200) connection.inputStream.readBytes()
            else null
        } finally {
            connection.disconnect()
        }
    }
}
