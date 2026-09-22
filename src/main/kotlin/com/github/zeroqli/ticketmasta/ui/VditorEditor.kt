package com.github.zeroqli.ticketmasta.ui

import com.github.zeroqli.ticketmasta.MyBundle
import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import javax.swing.JComponent

private const val VDITOR_CDN = "https://unpkg.com/vditor@3"

class VditorEditor : Disposable {

    private val gson = Gson()
    private val fallback = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
    }

    private var browser: JBCefBrowser? = null
    private var query: JBCefJSQuery? = null

    @Volatile
    private var current = ""

    private var ready = false
    private var pending: String? = null

    val component: JComponent = if (JBCefApp.isSupported()) createBrowser() else JBScrollPane(fallback)

    private fun createBrowser(): JComponent {
        val jcefBrowser = JBCefBrowser()
        val browserBase: JBCefBrowserBase = jcefBrowser
        val jsQuery = JBCefJSQuery.create(browserBase)
        jsQuery.addHandler { message ->
            current = message ?: ""
            if (!ready) {
                ready = true
                pending?.let { applyValue(it) }
                pending = null
            }
            JBCefJSQuery.Response(null)
        }
        browser = jcefBrowser
        query = jsQuery
        jcefBrowser.loadHTML(buildHtml(jsQuery))
        return jcefBrowser.component
    }

    private fun buildHtml(jsQuery: JBCefJSQuery): String {
        val callJava = jsQuery.inject("value")
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8"/>
            <link rel="stylesheet" href="$VDITOR_CDN/dist/index.css"/>
            <style>
              html, body { margin: 0; padding: 0; height: 100%; background: transparent; }
              #editor { height: 100%; }
            </style>
            </head>
            <body>
            <div id="editor"></div>
            <script src="$VDITOR_CDN/dist/index.min.js"></script>
            <script>
              var vditor = null;
              function callJava(value) { $callJava }
              function pushValue() {
                try { callJava(vditor ? vditor.getValue() : ''); } catch (e) {}
              }
              function setValue(value) {
                if (vditor) { vditor.setValue(value); pushValue(); }
              }
              function initVditor() {
                vditor = new Vditor('editor', {
                  mode: 'ir',
                  lang: 'en_US',
                  height: '100%',
                  cdn: '$VDITOR_CDN',
                  placeholder: '${jsEscape(MyBundle["editor.placeholder"])}',
                  cache: { enable: false },
                  toolbar: ['bold', 'italic', 'strike', '|', 'list', 'ordered-list', 'check', '|', 'code', 'link', '|', 'undo', 'redo'],
                  toolbarConfig: { pin: true },
                  input: function() { pushValue(); },
                  after: function() { pushValue(); }
                });
              }
              initVditor();
            </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun jsEscape(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'")

    fun getMarkdown(): String = if (browser != null) current else fallback.text

    fun setMarkdown(markdown: String) {
        fallback.text = markdown
        if (browser == null) return
        if (ready) applyValue(markdown) else pending = markdown
    }

    private fun applyValue(markdown: String) {
        val jcefBrowser = browser ?: return
        current = markdown
        try {
            jcefBrowser.cefBrowser.executeJavaScript("setValue(${gson.toJson(markdown)})", "about:blank", 0)
        } catch (_: Exception) {
        }
    }

    override fun dispose() {
        query?.dispose()
        browser?.dispose()
        browser = null
        query = null
    }
}
