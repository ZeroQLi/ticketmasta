package com.github.zeroqli.ticketmasta.services

import com.github.zeroqli.ticketmasta.model.Ticket
import com.github.zeroqli.ticketmasta.settings.TicketMastaSecrets
import com.github.zeroqli.ticketmasta.settings.TicketMastaSettings
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.openapi.project.Project
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

object AiService {

    private val gson = Gson()

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 120_000

    data class Message(val role: String, val content: String)

    fun scaffold(project: Project, ticket: Ticket): String {
        val settings = TicketMastaSettings.getInstance().state
        val apiKey = TicketMastaSecrets.getAiApiKey()
        val digest = CodebaseDigest.digest(project)
        val response = chat(
            baseUrl = settings.aiBaseUrl,
            model = settings.aiModel,
            apiKey = apiKey,
            messages = listOf(
                Message("system", PromptStore.scaffoldPrompt()),
                Message("user", buildString {
                    appendLine(issueHeader(ticket))
                    appendLine("## Codebase digest")
                    append(digest)
                }),
            ),
        )
        if (response.isBlank()) {
            throw IllegalStateException("AI returned an empty response.")
        }
        return stripCodeFence(response.trim())
    }

    private fun stripCodeFence(markdown: String): String {
        val fenced = Regex("^```[\\w-]*\\n([\\s\\S]*?)\\n?```\\s*$").find(markdown)
            ?: return markdown
        return fenced.groupValues[1]
    }

    private fun issueHeader(ticket: Ticket): String = buildString {
        appendLine("## GitHub issue")
        appendLine("Number: ${ticket.number}")
        appendLine("Title: ${ticket.title}")
        appendLine("State: ${ticket.state}")
        if (ticket.labels.isNotEmpty()) {
            appendLine("Labels: ${ticket.labels.joinToString(", ")}")
        }
        appendLine("Body:")
        append(ticket.body)
    }

    fun chat(baseUrl: String, model: String, apiKey: String, messages: List<Message>): String {
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val payload = gson.toJson(
            mapOf(
                "model" to model,
                "messages" to messages,
            ),
        )
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("AI request failed (HTTP $status): ${text.take(300)}")
            }
            return JsonParser.parseString(text).asJsonObject
                .getAsJsonArray("choices")
                ?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")?.asString
                .orEmpty()
        } finally {
            connection.disconnect()
        }
    }
}
