package com.github.zeroqli.ticketmasta.toolWindow

import com.github.zeroqli.ticketmasta.ui.TicketMastaPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class TicketMastaToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = TicketMastaPanel(project)
        val content = ContentFactory.getInstance().createContent(panel.getContent(), null, false)
        Disposer.register(content, panel)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true
}
