package com.github.zeroqli.ticketmasta.ui

import com.github.zeroqli.ticketmasta.MyBundle
import com.github.zeroqli.ticketmasta.model.Ticket
import com.github.zeroqli.ticketmasta.services.AgentLauncher
import com.github.zeroqli.ticketmasta.services.AiService
import com.github.zeroqli.ticketmasta.services.IssueService
import com.github.zeroqli.ticketmasta.settings.TicketMastaSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JSplitPane

private const val TODO_FILE_NAME = "TODO.md"

/** Right-side tool window: browse GitHub issues, then draft and hand off a `TODO.md` for them. */
class TicketMastaPanel(private val project: Project) : Disposable {

    private val issueService = project.service<IssueService>()
    private val editor = VditorEditor()
    private val overview = MarkdownPreview()

    private val issuesCombo = ComboBox<Any>()
    private val refreshButton = JButton(MyBundle["refresh"])
    private val scaffoldButton = JButton(MyBundle["scaffoldAi"])
    private val sendButton = JButton(MyBundle["sendToAi"])

    init {
        issuesCombo.addItem(MyBundle["issues.placeholder"])
        issuesCombo.addActionListener { showSelectedIssue() }
        refreshButton.addActionListener { refreshIssues(showErrors = true) }
        refreshIssues(showErrors = false)
    }

    fun getContent(): JComponent = JBPanel<JBPanel<*>>(BorderLayout(0, 8)).apply {
        border = JBUI.Borders.empty(8)
        add(issuesBar(), BorderLayout.NORTH)
        add(centerSplit(), BorderLayout.CENTER)
        add(actionBar(), BorderLayout.SOUTH)
    }

    private fun issuesBar(): JComponent = JBPanel<JBPanel<*>>(BorderLayout(8, 0)).apply {
        add(JBLabel(MyBundle["issues.label"]), BorderLayout.WEST)
        add(issuesCombo, BorderLayout.CENTER)
        add(refreshButton, BorderLayout.EAST)
    }

    private fun centerSplit(): JComponent = JSplitPane(
        JSplitPane.VERTICAL_SPLIT,
        section(JBScrollPane(overview.component)),
        section(editorHeader(), editor.component),
    ).apply {
        resizeWeight = 0.35
        border = JBUI.Borders.empty()
        isContinuousLayout = true
    }

    private fun section(content: JComponent): JComponent =
        JBPanel<JBPanel<*>>(BorderLayout()).apply { add(content, BorderLayout.CENTER) }

    private fun section(header: JComponent, content: JComponent): JComponent =
        JBPanel<JBPanel<*>>(BorderLayout(0, 4)).apply {
            add(header, BorderLayout.NORTH)
            add(content, BorderLayout.CENTER)
        }

    private fun editorHeader(): JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        add(JBLabel(MyBundle["editor.title"]), BorderLayout.WEST)
        add(scaffoldButton.apply { addActionListener { scaffoldAi() } }, BorderLayout.EAST)
    }

    private fun actionBar(): JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        add(
            JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                add(JButton(MyBundle["save"]).apply { addActionListener { saveTodoFile() } })
                add(sendButton.apply { addActionListener { sendToAi() } })
            },
            BorderLayout.EAST,
        )
    }

    private fun refreshIssues(showErrors: Boolean) {
        refreshButton.isEnabled = false
        issuesCombo.removeAllItems()
        issuesCombo.addItem(MyBundle["issues.placeholder"])
        overview.setMarkdown("*${MyBundle["issues.loading"]}*")

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runCatching { issueService.loadOpenIssues() }
            ApplicationManager.getApplication().invokeLater {
                refreshButton.isEnabled = true
                result.onSuccess { tickets ->
                    issuesCombo.removeAllItems()
                    if (tickets.isEmpty()) {
                        issuesCombo.addItem(MyBundle["issues.none"])
                        overview.setMarkdown("*${MyBundle["issues.none"]}*")
                    } else {
                        tickets.forEach { issuesCombo.addItem(it) }
                        issuesCombo.selectedIndex = 0
                    }
                }.onFailure { error ->
                    issuesCombo.removeAllItems()
                    issuesCombo.addItem(MyBundle["issues.placeholder"])
                    val message = error.message.orEmpty()
                    overview.setMarkdown("*${MyBundle["issues.error.message", message]}*")
                    if (showErrors) {
                        Messages.showErrorDialog(project, message, MyBundle["issues.error.title"])
                    }
                }
            }
        }
    }

    private fun showSelectedIssue() {
        val ticket = issuesCombo.selectedItem as? Ticket
        overview.setMarkdown(
            if (ticket == null) "*${MyBundle["issuesOverview.placeholder"]}*" else formatTicket(ticket),
        )
    }

    private fun formatTicket(ticket: Ticket): String = buildString {
        appendLine("# ${MyBundle["overview.title", ticket.number, ticket.title]}")
        appendLine()
        appendLine("**${MyBundle["overview.state", ticket.state]}**")
        if (ticket.labels.isNotEmpty()) {
            appendLine()
            appendLine(MyBundle["overview.labels", ticket.labels.joinToString(", ")])
        }
        if (ticket.htmlUrl.isNotBlank()) {
            appendLine()
            appendLine("[${MyBundle["overview.url"]}](${ticket.htmlUrl})")
        }
        if (ticket.body.isNotBlank()) {
            appendLine()
            appendLine("---")
            appendLine()
            append(ticket.body)
        }
    }

    private fun scaffoldAi() {
        val ticket = issuesCombo.selectedItem as? Ticket
        if (ticket == null) {
            Messages.showErrorDialog(project, MyBundle["scaffold.error.noIssue"], MyBundle["scaffold.error.title"])
            return
        }
        scaffoldButton.isEnabled = false
        editor.setMarkdown("*${MyBundle["scaffold.progress"]}*")

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runCatching { AiService.scaffold(project, ticket) }
            ApplicationManager.getApplication().invokeLater {
                scaffoldButton.isEnabled = true
                result.onSuccess { markdown ->
                    editor.setMarkdown(markdown)
                }.onFailure { error ->
                    Messages.showErrorDialog(project, error.message.orEmpty(), MyBundle["scaffold.error.title"])
                }
            }
        }
    }

    private fun saveTodoFile() {
        if (writeTodoFile(editor.getMarkdown())) {
            Messages.showInfoMessage(
                project,
                MyBundle["save.success.message", TODO_FILE_NAME],
                MyBundle["save.success.title"],
            )
        }
    }

    private fun writeTodoFile(markdown: String): Boolean {
        val basePath = project.basePath
        if (basePath == null) {
            Messages.showErrorDialog(project, MyBundle["save.error.message"], MyBundle["save.error.title"])
            return false
        }
        val target = Path.of(basePath, TODO_FILE_NAME)
        return try {
            Files.writeString(target, markdown)
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(target)
            true
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                MyBundle["save.error.io", TODO_FILE_NAME, e.message.orEmpty()],
                MyBundle["save.error.title"],
            )
            false
        }
    }

    private fun sendToAi() {
        val ticket = issuesCombo.selectedItem as? Ticket
        if (ticket == null) {
            Messages.showErrorDialog(project, MyBundle["send.error.noIssue"], MyBundle["send.error.title"])
            return
        }
        val markdown = editor.getMarkdown()
        if (markdown.isBlank()) {
            Messages.showErrorDialog(project, MyBundle["send.error.noContent"], MyBundle["send.error.title"])
            return
        }
        if (!writeTodoFile(markdown)) return

        if (!AgentLauncher.isOpencodeAvailable()) {
            Messages.showErrorDialog(project, MyBundle["send.error.agentMissing"], MyBundle["send.error.title"])
            return
        }

        val state = TicketMastaSettings.getInstance().state
        val command = AgentLauncher.buildCommand(state, ticket, TODO_FILE_NAME, state.githubRepo)
        AgentLauncher.logCommand(command)
        try {
            AgentLauncher.launch(project, command, "#${ticket.number} ${ticket.title}")
        } catch (e: Exception) {
            Messages.showErrorDialog(project, MyBundle["send.error.launch", e.message.orEmpty()], MyBundle["send.error.title"])
        }
    }

    override fun dispose() {
        editor.dispose()
    }
}
