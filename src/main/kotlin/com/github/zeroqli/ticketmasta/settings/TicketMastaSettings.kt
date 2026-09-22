package com.github.zeroqli.ticketmasta.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "TicketMastaSettings", storages = [Storage("ticketmasta.xml")])
class TicketMastaSettings : PersistentStateComponent<TicketMastaSettings.State> {

    data class State(
        var githubRepo: String = "",
        var aiProvider: String = PROVIDER_OPENROUTER,
        var aiBaseUrl: String = BASE_URL_OPENROUTER,
        var aiModel: String = "openai/gpt-4o-mini",
        var agentCommand: String = DEFAULT_AGENT_COMMAND,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        const val PROVIDER_OPENAI = "openai"
        const val PROVIDER_OPENROUTER = "openrouter"
        const val BASE_URL_OPENAI = "https://api.openai.com/v1"
        const val BASE_URL_OPENROUTER = "https://openrouter.ai/api/v1"
        const val DEFAULT_AGENT_COMMAND = "opencode run --file TODO.md --title \"{title}\" \"{prompt}\""

        fun getInstance(): TicketMastaSettings =
            ApplicationManager.getApplication().getService(TicketMastaSettings::class.java)
    }
}

object AiProviders {

    val all: List<String> = listOf(
        TicketMastaSettings.PROVIDER_OPENAI,
        TicketMastaSettings.PROVIDER_OPENROUTER,
    )

    fun baseUrl(provider: String): String = when (provider) {
        TicketMastaSettings.PROVIDER_OPENAI -> TicketMastaSettings.BASE_URL_OPENAI
        else -> TicketMastaSettings.BASE_URL_OPENROUTER
    }

    fun models(provider: String): List<String> = when (provider) {
        TicketMastaSettings.PROVIDER_OPENAI -> listOf("gpt-4o-mini", "gpt-4o")
        else -> listOf("openai/gpt-4o-mini", "openai/gpt-4o", "anthropic/claude-3.5-sonnet")
    }
}
