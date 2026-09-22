package com.github.zeroqli.ticketmasta.ui

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.util.ui.JBUI
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.intellij.markdown.html.HtmlGenerator
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import java.awt.Desktop
import java.net.URI

class MarkdownPreview {

    private val pane = JEditorPane().apply {
        isEditable = false
        contentType = "text/html"
        isOpaque = false
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        addHyperlinkListener(linkListener())
    }

    val component: JComponent = pane

    fun setMarkdown(markdown: String) {
        val dark = EditorColorsManager.getInstance().isDarkEditor
        val foreground = if (dark) "#cccccc" else "#383a42"
        val heading = if (dark) "#e6e6e6" else "#111111"
        val secondary = if (dark) "#9a9a9a" else "#6a737d"
        val link = if (dark) "#6cb2ff" else "#2f6fd0"
        val accent = if (dark) "#b76ba3" else "#a626a4"

        val css = """
            body { font-family: sans-serif; font-size: 12pt; color: $foreground; }
            h1, h2, h3, h4, h5, h6 { font-size: 13pt; color: $heading; }
            p { margin: 2px; }
            ul, ol { margin: 2px 16px; }
            li { margin: 0px; }
            a { color: $link; }
            blockquote { color: $secondary; border-left: 3px solid $secondary; padding-left: 6px; }
            hr { color: $secondary; }
            code, pre { font-family: monospace; font-size: 11pt; color: $accent; }
            table { border-spacing: 0; }
        """.trimIndent()

        pane.text = """
            <html>
            <head><style>$css</style></head>
            <body>${markdownToHtml(markdown)}</body>
            </html>
        """.trimIndent()
        pane.caretPosition = 0
        pane.border = JBUI.Borders.empty()
    }

    private fun markdownToHtml(markdown: String): String = try {
        val flavour = GFMFlavourDescriptor()
        val tree: ASTNode = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
        HtmlGenerator(markdown, tree, flavour, includeSrcPositions = false).generateHtml()
    } catch (_: Exception) {
        val escaped = markdown.replace("&", "&" + "amp;").replace("<", "&" + "lt;")
        "<pre>$escaped</pre>"
    }

    private fun linkListener(): HyperlinkListener = HyperlinkListener { event ->
        if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
            runCatching { Desktop.getDesktop().browse(URI(event.url.toString())) }
        }
    }
}
