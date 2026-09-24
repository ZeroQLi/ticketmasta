package com.github.zeroqli.ticketmasta.ui

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.UIManager
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.intellij.markdown.html.HtmlGenerator
import java.awt.Desktop
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.net.URI
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

/**
 * Read-only Markdown renderer whose font size scales with the component's width, so the issue
 * text stays readable whether the tool window is narrow or wide.
 */
class MarkdownPreview {

    private val pane = JEditorPane().apply {
        isEditable = false
        contentType = "text/html"
        isOpaque = false
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        selectionColor = UIUtil.getListSelectionBackground(true)
        selectedTextColor = UIUtil.getListSelectionForeground(true)
        addHyperlinkListener(linkListener())
    }

    val component: JComponent = pane

    private var markdown = ""
    private var lastFontSize = -1

    init {
        pane.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) = render()
        })
    }

    fun setMarkdown(markdown: String) {
        this.markdown = markdown
        render(force = true)
    }

    private fun render(force: Boolean = false) {
        val fontSize = fontSizeForWidth(pane.width)
        if (!force && fontSize == lastFontSize) return
        lastFontSize = fontSize

        val css = css(fontSize)
        val html = "<html><head><style>$css</style></head><body>${markdownToHtml(markdown)}</body></html>"
        pane.text = html
        pane.caretPosition = 0
        pane.border = JBUI.Borders.empty()
    }

    /** Scales body text between [MIN_FONT_SIZE] and [MAX_FONT_SIZE] as the preview widens. */
    private fun fontSizeForWidth(width: Int): Int {
        if (width <= 0) return MIN_FONT_SIZE
        val steps = (width - NARROW_WIDTH) / WIDTH_PER_STEP
        return (MIN_FONT_SIZE + steps).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
    }

    private fun css(fontSize: Int): String {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val dark = EditorColorsManager.getInstance().isDarkEditor

        val uiFont = UIManager.getFont("Label.font")
        val uiFontFamily = uiFont.family

        val editorFontFamily = scheme.editorFontName
        val codeSize = (fontSize - 2).coerceAtLeast(9)

        val foreground = if (dark) "#bbbbbb" else "#3c3c3c"
        val secondary = if (dark) "#888888" else "#808080"
        val headingSize = fontSize + 1

        return """
            body { font-family: '$uiFontFamily', sans-serif; font-size: ${fontSize}pt; color: $foreground; line-height: 1.4; }
            h1, h2, h3, h4, h5, h6 { font-size: ${headingSize}pt; font-weight: 600; }
            p { margin: 4px 0; }
            ul, ol { margin: 4px 0 4px 18px; }
            li { margin: 2px 0; }
            blockquote { color: $secondary; border-left: 2px solid $secondary; padding-left: 8px; margin: 4px 0; }
            hr { border: none; border-top: 1px solid $secondary; }
            code, pre { font-family: '$editorFontFamily', monospace; font-size: ${codeSize}pt; }
            table { border-spacing: 0; }
        """.trimIndent()
    }

    private fun markdownToHtml(markdown: String): String = try {
        val flavour = GFMFlavourDescriptor()
        val tree: ASTNode = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
        HtmlGenerator(markdown, tree, flavour, includeSrcPositions = false).generateHtml()
    } catch (_: Exception) {
        val escaped = markdown.replace("&", "&amp;").replace("<", "&lt;")
        "<pre>$escaped</pre>"
    }

    private fun linkListener(): HyperlinkListener = HyperlinkListener { event ->
        if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
            runCatching { Desktop.getDesktop().browse(URI(event.url.toString())) }
        }
    }

    private companion object {
        const val MIN_FONT_SIZE = 12
        const val MAX_FONT_SIZE = 20
        const val NARROW_WIDTH = 320
        const val WIDTH_PER_STEP = 90
    }
}
