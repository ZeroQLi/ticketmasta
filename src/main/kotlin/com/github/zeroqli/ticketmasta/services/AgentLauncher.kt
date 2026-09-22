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

/**
 * Launches the external agent (opencode) in the built-in Terminal with a prompt expanded from
 * [PromptStore.agentPrompt].
 */
object AgentLauncher {

    fun isOpencodeAvailable(): Boolean {
        val pathVar = System.getenv("PATH") ?: return false
        val extensions = listOf("", ".exe", ".cmd", ".bat")
        return pathVar.split(File.pathSeparatorChar)
            .filter { it.isNotBlank() }
            .any { dir ->
                extensions.any { ext -> Files.isRegularFile(Path.of(dir, "opencode$ext")) }
            }
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
        .replace('"', '\'')
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Creates a new Terminal tab rooted at the project and runs [command]. Must be called on the EDT. */
    fun launch(project: Project, command: String, tabTitle: String) {
        val workingDirectory = project.basePath?.let { normalizeBasePath(it).toString() }
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