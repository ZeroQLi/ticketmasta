package com.github.zeroqli.ticketmasta.services

import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path

/**
 * Produces a bounded text digest of the repository: file tree plus contents of
 * the most relevant text files, so it can be embedded in an AI prompt.
 */
internal object CodebaseDigest {

    private val SKIP_DIRS = setOf(
        ".git", ".gradle", ".idea", ".github", "node_modules", "build", "dist",
        "out", "target", "vendor", "venv", "__pycache__", ".next", ".nuxt",
    )
    private val SKIP_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "gif", "ico", "svg", "webp", "bmp",
        "woff", "woff2", "ttf", "eot", "otf",
        "pdf", "zip", "gz", "tar", "rar", "7z",
        "jar", "class", "exe", "dll", "so", "dylib", "lib", "obj",
        "mp3", "mp4", "wav", "avi", "mov", "webm",
        "db", "sqlite", "bin", "dat", "lock", "log",
    )

    private const val MAX_DEPTH = 6
    private const val MAX_FILES_IN_TREE = 250
    private const val MAX_FILE_CHARS = 8_000
    private const val MAX_TOTAL_CHARS = 60_000
    private const val MAX_FILE_BYTES = 400_000

    fun digest(project: Project): String {
        val base = project.basePath ?: return "Repository contents are unavailable."
        val root = Path.of(base)
        if (!Files.isDirectory(root)) return "Repository contents are unavailable."

        val files = collectFiles(root)
        if (files.isEmpty()) return "The repository has no readable files."

        val tree = files.take(MAX_FILES_IN_TREE).joinToString("\n") { "- ${relativize(root, it)}" }
        val builder = StringBuilder()
        builder.append("### File tree\n").append(tree).append("\n\n")
        builder.append("### Selected file contents\n")

        var totalChars = 0
        for (file in files) {
            if (totalChars >= MAX_TOTAL_CHARS) break
            if (builder.countChars() > MAX_TOTAL_CHARS) break
            val rel = relativize(root, file)
            val content = readFileText(file) ?: continue
            val excerpt = content.take(MAX_FILE_CHARS)
            val truncated = if (content.length > excerpt.length) " (truncated)" else ""
            val chunk = "#### $rel$truncated\n```${
                extensionOf(file)
            }\n$excerpt\n```\n\n"
            builder.append(chunk)
            totalChars += chunk.length
        }
        return builder.toString()
    }

    private fun StringBuilder.countChars(): Int = this.length

    private fun collectFiles(root: Path): List<Path> = try {
        Files.walk(root, MAX_DEPTH)
            .filter { Files.isRegularFile(it) }
            .filter { path -> isInsideSkippedDir(root, path).not() }
            .filter { path -> extensionOf(path) !in SKIP_EXTENSIONS }
            .filter { path -> Files.size(path) <= MAX_FILE_BYTES }
            .sorted()
            .toList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun isInsideSkippedDir(root: Path, path: Path): Boolean =
        root.relativize(path).any { it.toString() in SKIP_DIRS }

    private fun readFileText(file: Path): String? = try {
        Files.readString(file)
    } catch (_: Exception) {
        null
    }

    private fun relativize(root: Path, path: Path): String = try {
        root.relativize(path).toString().replace('\\', '/')
    } catch (_: Exception) {
        path.toString()
    }

    private fun extensionOf(path: Path): String =
        path.fileName?.toString()?.substringAfterLast('.', "")?.lowercase().orEmpty()
}
