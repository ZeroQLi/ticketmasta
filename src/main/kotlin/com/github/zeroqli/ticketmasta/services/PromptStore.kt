package com.github.zeroqli.ticketmasta.services

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path

/**
 * Editable prompt files, stored globally in the IDE config directory so they apply to every project.
 *
 * Files live under `<IDE config>/ticketmasta/prompts/` and are created with default content the
 * first time they are accessed:
 *  - `scaffold.md` — system prompt used by Scaffold AI.
 *  - `agent.md`    — prompt handed to the external agent (opencode) by Send to AI.
 *
 * `agent.md` supports the placeholders `{file}`, `{number}`, `{title}` and `{repo}`.
 */
object PromptStore {

    const val SCAFFOLD_FILE = "scaffold.md"
    const val AGENT_FILE = "agent.md"

    private const val DEFAULT_SCAFFOLD = """You are a senior software engineer working inside an IDE plugin. You are given a GitHub
issue and a digest of the repository (file tree plus excerpts of key files). Produce a
Markdown document that:
1. Gives a short diagnosis of the issue: what it is about, the likely root cause, and the
   parts of the codebase involved (reference concrete files from the digest when possible).
2. Ends with an ordered, actionable implementation plan written as GitHub task-list items
   (lines starting with "- [ ]"), each describing one concrete change.
Output ONLY the Markdown document. Do not wrap it in a code fence. Do not add commentary."""

    private const val DEFAULT_AGENT = """Follow the instructions in {file} to resolve GitHub issue #{number}: {title}.

Work through the checklist in the file, make the necessary code changes, and keep going until the task is complete."""

    fun promptsDir(): Path = Path.of(PathManager.getConfigPath(), "ticketmasta", "prompts")

    fun scaffoldFilePath(): Path = ensureFile(SCAFFOLD_FILE, DEFAULT_SCAFFOLD)

    fun agentFilePath(): Path = ensureFile(AGENT_FILE, DEFAULT_AGENT)

    fun scaffoldPrompt(): String = read(scaffoldFilePath(), DEFAULT_SCAFFOLD)

    fun agentPrompt(): String = read(agentFilePath(), DEFAULT_AGENT)

    private fun ensureFile(name: String, content: String): Path {
        val path = promptsDir().resolve(name)
        if (!Files.exists(path)) {
            runCatching {
                Files.createDirectories(path.parent)
                Files.writeString(path, content)
            }
        }
        return path
    }

    private fun read(path: Path, fallback: String): String =
        runCatching { Files.readString(path).takeIf { it.isNotBlank() } }.getOrNull() ?: fallback

    /** Opens the file in the IDE editor when a project is open, otherwise with the OS default app. */
    fun openFile(path: Path): Boolean {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
        val project = firstOpenProject()
        if (virtualFile != null && project != null) {
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
            return true
        }
        return osOpen(path)
    }

    /** Opens the prompts directory in the OS file manager (or the IDE if it resolves to a directory). */
    fun openFolder(): Boolean {
        val dir = promptsDir()
        runCatching { Files.createDirectories(dir) }
        return osOpen(dir)
    }

    private fun firstOpenProject(): Project? =
        ProjectManager.getInstance().openProjects.firstOrNull { !it.isDefault }

    private fun osOpen(path: Path): Boolean = runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(path.toFile())
            true
        } else {
            false
        }
    }.getOrDefault(false)
}