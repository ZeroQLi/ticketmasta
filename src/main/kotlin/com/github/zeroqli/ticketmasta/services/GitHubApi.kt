package com.github.zeroqli.ticketmasta.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.HttpRequests
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

/**
 * Minimal GitHub REST transport on plain JDK networking.
 *
 * IntelliJ's [HttpRequests] routes through the IDE's HTTP/proxy configuration and
 * can block on connect in sandboxed environments (the IDE's own Marketplace
 * requests hang the same way), so GitHub calls deliberately bypass it.
 */
internal object GitHubApi {

    private val LOG = Logger.getInstance(GitHubApi::class.java)

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000

    fun requestJson(url: String, token: String?): String {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "ticketmasta")
            token?.takeIf { it.isNotBlank() }?.let {
                connection.setRequestProperty("Authorization", "Bearer $it")
            }

            val startedAt = System.currentTimeMillis()
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            LOG.info("GitHubApi GET $url -> $status (auth=${!token.isNullOrBlank()}) in ${System.currentTimeMillis() - startedAt}ms")
            if (status !in 200..299) {
                throw HttpRequests.HttpStatusException(describe(status), status, url)
            }
            return body
        } finally {
            connection.disconnect()
        }
    }

    fun describe(status: Int): String = when (status) {
        401 -> "Bad credentials (401). Check the token."
        403 -> "Forbidden (403). Token lacks access or rate limit hit."
        404 -> "Repository not found (404) or token has no access."
        else -> "GitHub returned HTTP $status."
    }
}
