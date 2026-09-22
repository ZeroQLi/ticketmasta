package com.github.zeroqli.ticketmasta.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe

object TicketMastaSecrets {

    private const val SERVICE_NAME = "ticketmasta"
    private const val GITHUB_TOKEN_KEY = "github.token"
    private const val AI_API_KEY = "ai.apiKey"

    private const val DEFAULT_AI_API_KEY =
        "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"

    fun getGitHubToken(): String? = read(GITHUB_TOKEN_KEY)

    fun setGitHubToken(token: String?) = write(GITHUB_TOKEN_KEY, token)

    fun getAiApiKey(): String = read(AI_API_KEY) ?: DEFAULT_AI_API_KEY

    fun setAiApiKey(key: String?) = write(AI_API_KEY, key)

    private fun read(key: String): String? = try {
        PasswordSafe.instance.getPassword(attributes(key))
    } catch (_: Exception) {
        null
    }

    private fun write(key: String, value: String?) {
        try {
            PasswordSafe.instance.setPassword(attributes(key), value)
        } catch (_: Exception) {
        }
    }

    private fun attributes(key: String) = CredentialAttributes(SERVICE_NAME, key)
}
