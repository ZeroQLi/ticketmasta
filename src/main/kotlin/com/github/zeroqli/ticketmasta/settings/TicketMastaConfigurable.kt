package com.github.zeroqli.ticketmasta.settings

import com.github.zeroqli.ticketmasta.MyBundle
import com.github.zeroqli.ticketmasta.services.GitHubConnectionTest
import com.github.zeroqli.ticketmasta.services.PromptStore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent

class TicketMastaConfigurable : Configurable {

    private val settings = TicketMastaSettings.getInstance()

    private val githubRepoField = JBTextField()
    private val githubTokenField = JBPasswordField()
    private val testButton = JButton(MyBundle["settings.testConnection"])
    private val testStatusLabel = JBLabel()
    private val aiProviderCombo = ComboBox(AiProviders.all.toTypedArray())
    private val aiBaseUrlField = JBTextField()
    private val aiApiKeyField = JBPasswordField()
    private val aiModelCombo = ComboBox<String>()
    private val agentCommandField = JBTextField()

    private var githubTokenCache = ""
    private var aiKeyCache = ""

    private val panel: JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent(JBLabel(MyBundle["settings.githubRepo"]), githubRepoField)
        .addLabeledComponent(JBLabel(MyBundle["settings.githubToken"]), githubTokenField)
        .addComponent(
            JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                add(testButton)
                add(testStatusLabel)
            },
        )
        .addSeparator()
        .addLabeledComponent(JBLabel(MyBundle["settings.aiProvider"]), aiProviderCombo)
        .addLabeledComponent(JBLabel(MyBundle["settings.aiBaseUrl"]), aiBaseUrlField)
        .addLabeledComponent(JBLabel(MyBundle["settings.aiApiKey"]), aiApiKeyField)
        .addLabeledComponent(JBLabel(MyBundle["settings.aiModel"]), aiModelCombo)
        .addSeparator()
        .addLabeledComponent(JBLabel(MyBundle["settings.agentCommand"]), agentCommandField)
        .addComponent(JBLabel(MyBundle["settings.agentCommand.hint"]))
        .addSeparator()
        .addComponent(JBLabel(MyBundle["settings.prompts"]))
        .addLabeledComponent(
            JBLabel(MyBundle["settings.prompts.scaffold"]),
            JButton(MyBundle["settings.prompts.open"]).apply {
                addActionListener { PromptStore.openFile(PromptStore.scaffoldFilePath()) }
            },
        )
        .addLabeledComponent(
            JBLabel(MyBundle["settings.prompts.agent"]),
            JButton(MyBundle["settings.prompts.open"]).apply {
                addActionListener { PromptStore.openFile(PromptStore.agentFilePath()) }
            },
        )
        .addComponent(
            JButton(MyBundle["settings.prompts.openFolder"]).apply {
                addActionListener { PromptStore.openFolder() }
            },
        )
        .addComponentFillVertically(JBPanel<JBPanel<*>>(), 0)
        .panel

    init {
        aiProviderCombo.addActionListener {
            val provider = selectedProvider()
            aiBaseUrlField.text = AiProviders.baseUrl(provider)
            refreshModels(provider, null)
        }
        testButton.addActionListener { testConnection() }
        reset()
        loadSecrets()
    }

    override fun getDisplayName(): String = MyBundle["settings.displayName"]

    override fun getPreferredFocusedComponent(): JComponent = githubRepoField

    override fun createComponent(): JComponent = panel

    override fun isModified(): Boolean {
        val state = settings.state
        return githubRepoField.text.trim() != state.githubRepo ||
            selectedProvider() != state.aiProvider ||
            aiBaseUrlField.text.trim() != state.aiBaseUrl ||
            selectedModel() != state.aiModel ||
            String(githubTokenField.password) != githubTokenCache ||
            String(aiApiKeyField.password) != aiKeyCache ||
            agentCommandField.text.trim() != state.agentCommand
    }

    override fun apply() {
        val state = settings.state
        state.githubRepo = githubRepoField.text.trim()
        state.aiProvider = selectedProvider()
        state.aiBaseUrl = aiBaseUrlField.text.trim()
        state.aiModel = selectedModel()
        state.agentCommand = agentCommandField.text.trim()

        val githubToken = String(githubTokenField.password)
        val aiKey = String(aiApiKeyField.password)
        TicketMastaSecrets.setGitHubToken(githubToken.ifBlank { null })
        TicketMastaSecrets.setAiApiKey(aiKey.ifBlank { null })
        githubTokenCache = githubToken
        aiKeyCache = aiKey
    }

    override fun reset() {
        val state = settings.state
        githubRepoField.text = state.githubRepo
        githubTokenField.text = githubTokenCache
        aiProviderCombo.selectedItem = state.aiProvider
        aiBaseUrlField.text = state.aiBaseUrl
        refreshModels(state.aiProvider, state.aiModel)
        aiApiKeyField.text = aiKeyCache
        agentCommandField.text = state.agentCommand
    }

    private fun loadSecrets() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val token = TicketMastaSecrets.getGitHubToken().orEmpty()
            val key = TicketMastaSecrets.getAiApiKey()
            ApplicationManager.getApplication().invokeLater {
                githubTokenCache = token
                aiKeyCache = key
                if (githubTokenField.password.isEmpty()) githubTokenField.text = token
                if (aiApiKeyField.password.isEmpty()) aiApiKeyField.text = key
            }
        }
    }

    private fun testConnection() {
        val repo = githubRepoField.text.trim()
        if (repo.isEmpty()) {
            setTestStatus(MyBundle["settings.testConnection.noRepo"], null)
            return
        }
        val token = String(githubTokenField.password).ifBlank { null }
        testButton.isEnabled = false
        setTestStatus(MyBundle["settings.testConnection.testing"], null)

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = GitHubConnectionTest.test(repo, token)
            ApplicationManager.getApplication().invokeLater {
                testButton.isEnabled = true
                result.onSuccess { info ->
                    val message = if (info.isPrivate) {
                        MyBundle["settings.testConnection.success.private", info.fullName]
                    } else {
                        MyBundle["settings.testConnection.success", info.fullName]
                    }
                    setTestStatus(message, true)
                }.onFailure { error ->
                    setTestStatus(
                        MyBundle["settings.testConnection.failure", error.message.orEmpty()],
                        false,
                    )
                }
            }
        }
    }

    private fun setTestStatus(text: String, success: Boolean?) {
        testStatusLabel.text = text
        testStatusLabel.foreground = when (success) {
            true -> JBColor.GREEN
            false -> JBColor.RED
            null -> JBColor.GRAY
        }
    }

    private fun selectedProvider(): String =
        aiProviderCombo.selectedItem as? String ?: TicketMastaSettings.PROVIDER_OPENROUTER

    private fun selectedModel(): String = (aiModelCombo.selectedItem as? String).orEmpty()

    private fun refreshModels(provider: String, preferred: String?) {
        val models = AiProviders.models(provider)
        aiModelCombo.removeAllItems()
        models.forEach { aiModelCombo.addItem(it) }
        if (preferred != null && models.contains(preferred)) {
            aiModelCombo.selectedItem = preferred
        }
    }
}
