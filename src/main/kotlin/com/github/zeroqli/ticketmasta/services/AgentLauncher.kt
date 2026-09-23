package com.github.zeroqli.ticketmasta.services

import com.github.zeroqli.ticketmasta.model.Ticket
import com.github.zeroqli.ticketmasta.settings.TicketMastaSettings
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Launches the external agent (opencode) in the built-in Terminal with a prompt expanded from
 * [PromptStore.agentPrompt].
 *
 * The IDE process PATH may predate an opencode install (and npm shims live in `%APPDATA%\npm`),
 * so availability also falls back to known install locations and `where.exe`.
 */
object AgentLauncher {

    private val EXTENSIONS = listOf(".cmd", ".exe", ".bat", ".ps1", "")

    /**
     * Resolves whether opencode is available: PATH first, then known install locations,
     * then `where.exe`.
     */
    fun isOpencodeAvailable(): Boolean =
        pathCandidate() != null || knownLocationCandidate() != null || whereCandidate() != null

    private fun pathCandidate(): String? =
        System.getenv("PATH")
            ?.split(File.pathSeparatorChar)
            ?.asSequence()
            ?.map { it.trim().trim('"') }
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { dir -> EXTENSIONS.firstNotNullOfOrNull { ext -> opencodeFile(dir, ext) } }
            ?.firstOrNull()
            ?.toString()

    private fun opencodeFile(dir: String, ext: String): Path? =
        Path.of(dir, "opencode$ext").takeIf { Files.isRegularFile(it) }

    private fun knownLocationCandidate(): String? = knownLocations()
        .firstOrNull { Files.isRegularFile(it) }
        ?.toString()

    private fun knownLocations(): List<Path> {
        val candidates = mutableListOf<Path>()
        System.getenv("APPDATA")?.let { appData ->
            EXTENSIONS.filter { it.isNotEmpty() }.forEach { ext -> candidates.add(Path.of(appData, "npm", "opencode$ext")) }
        }
        val home = System.getProperty("user.home")
        EXTENSIONS.forEach { ext -> candidates.add(Path.of(home, ".opencode", "bin", "opencode$ext")) }
        System.getenv("LOCALAPPDATA")?.let { localAppData ->
            candidates.add(Path.of(localAppData, "Programs", "@opencodedesktop", "resources", "opencode-cli.exe"))
        }
        return candidates
    }

    private fun whereCandidate(): String? = try {
        val process = ProcessBuilder("cmd.exe", "/c", "where", "opencode").start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
            output.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    fun buildCommand(state: TicketMastaSettings.State, ticket: Ticket, fileName: String, repo: String): String {
        val template = state.agentCommand.ifBlank { TicketMastaSettings.DEFAULT_AGENT_COMMAND }
        val prompt = expandAgentPrompt(PromptStore.agentPrompt(), ticket, fileName, repo)
        return template
            .replace("{prompt}", sanitize(prompt))
            .replace("{file}", fileName)
            .replace("{number}", ticket.number.toString())
            .replace("{title}", sanitize(ticket.title))
            .replace("{repo}", repo)
    }

    private fun expandAgentPrompt(template: String, ticket: Ticket, fileName: String, repo: String): String =
        template
            .replace("{file}", fileName)
            .replace("{number}", ticket.number.toString())
            .replace("{title}", ticket.title)
            .replace("{repo}", repo)

    private fun sanitize(value: String): String = value
        .replace(Regex("[\"']"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Creates a new Terminal tab rooted at the project and runs [command]. Must be called on the EDT. */
    fun launch(project: Project, command: String, tabTitle: String) {
        val workingDirectory = project.basePath
        val manager = TerminalToolWindowManager.getInstance(project)
        manager.toolWindow?.activate(null, true, true)
        val terminalWidget = manager.createShellWidget(workingDirectory, tabTitle, true, false)
        val widget: ShellTerminalWidget = ShellTerminalWidget.asShellJediTermWidget(terminalWidget)
            ?: error("The terminal did not start as a shell widget.")
        widget.executeCommand(command)
    }

    fun logCommand(command: String) {
        thisLogger().info("TicketMasta: launching agent command: $command")
    }
}