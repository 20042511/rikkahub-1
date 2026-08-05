package me.rerere.rikkahub.di

import android.content.Context
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.files.skills.SkillFormatAdapter
import me.rerere.rikkahub.data.files.skills.readers.AnthropicSkillReader
import me.rerere.rikkahub.data.files.skills.readers.ClineRuleReader
import me.rerere.rikkahub.data.files.skills.readers.CopilotInstructionReader
import me.rerere.rikkahub.data.files.skills.readers.CursorRuleReader
import me.rerere.rikkahub.data.files.skills.readers.FlatMarkdownReader
import me.rerere.rikkahub.data.files.skills.readers.KiroSteeringReader
import me.rerere.rikkahub.data.files.skills.readers.WindsurfRuleReader
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FavoriteRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.FilesRepository
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.ProotShellRunner
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.WorkspaceBindMount
import me.rerere.workspace.WorkspaceManager
import org.koin.dsl.module
import java.io.File

val repositoryModule = module {
    single {
        ConversationRepository(get(), get(), get(), get(), get(), get())
    }

    single {
        FolderRepository(get(), get())
    }

    single {
        MemoryRepository(get())
    }

    single {
        GenMediaRepository(get())
    }

    single {
        FilesRepository(get())
    }

    single {
        FavoriteRepository(get())
    }

    single {
        val context: Context = get()
        WorkspaceManager(
            baseDir = File(context.filesDir, "workspaces"),
            shellRunner = ProotShellRunner(
                nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir),
            ),
            // 同一份挂载表既用于 PRoot 的 -b 参数, 也用于文件工具的路径解析, 避免两处漂移
            bindMounts = listOf(
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.SKILLS).apply { mkdirs() },
                    target = "/skills",
                ),
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.TOOL_OUTPUTS).apply { mkdirs() },
                    target = "/tool_outputs",
                ),
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.UPLOAD).apply { mkdirs() },
                    target = "/upload",
                ),
            ),
        )
    }

    single {
        RootfsInstaller(get())
    }

    single {
        WorkspaceRepository(get(), get(), get(), get())
    }

    single {
        FilesManager(get(), get(), get())
    }

    single {
        SkillManager(get(), get())
    }

    // 格式适配器：Reader 按优先级排序，命名型(.mdc/.cursorrules/SKILL.md/CLAUDE.md)在前，
    // 路径型(.clinerules/.windsurf/.kiro/.github)在后；AnthropicSkillReader 兜底处理含
    // frontmatter 但无 Reader 认领的文件
    single {
        SkillFormatAdapter(
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
    }
}
