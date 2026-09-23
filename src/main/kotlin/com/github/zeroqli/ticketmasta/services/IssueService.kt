package com.github.zeroqli.ticketmasta.services

import com.github.zeroqli.ticketmasta.model.Ticket
import com.github.zeroqli.ticketmasta.settings.TicketMastaSecrets
import com.github.zeroqli.ticketmasta.settings.TicketMastaSettings
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class IssueService(private val project: Project) {

    fun resolveRepo(): String? {
        val configured = TicketMastaSettings.getInstance().state.githubRepo.trim()
        if (configured.isNotEmpty()) return configured
        return detectRepo()
    }

    fun loadOpenIssues(): List<Ticket> {
        val repo = resolveRepo() ?: throw IllegalStateException(
            "No GitHub repository configured. Set one in Settings > Tools > TicketMasta.",
        )
        val url = "https://api.github.com/repos/$repo/issues?state=open&per_page=50"
        val token = TicketMastaSecrets.getGitHubToken()
        val json = GitHubApi.requestJson(url, token)

        return JsonParser.parseString(json).asJsonArray.mapNotNull { element ->
            val obj = element.asJsonObject
            if (obj.has("pull_request")) return@mapNotNull null
            Ticket(
                number = obj.get("number").asInt,
                title = obj.get("title").asString,
                body = obj.stringOrNull("body").orEmpty(),
                state = obj.stringOrNull("state").orEmpty(),
                labels = obj.get("labels")
                    ?.takeUnless { it.isJsonNull }
                    ?.asJsonArray
                    ?.map { it.asJsonObject.get("name").asString }
                    ?: emptyList(),
                htmlUrl = obj.stringOrNull("html_url").orEmpty(),
            )
        }
    }

    private fun detectRepo(): String? {
        val base = project.basePath ?: return null
        val gitDir = resolveGitDir(Path.of(base)) ?: return null
        val config = gitDir.resolve("config")
        if (!Files.isRegularFile(config)) return null
        val url = ORIGIN_URL.findAll(Files.readString(config)).firstOrNull()?.groupValues?.get(1)
            ?: return null
        return toOwnerRepo(url)
    }

    private fun resolveGitDir(base: Path): Path? {
        val dotGit = base.resolve(".git")
        return when {
            Files.isDirectory(dotGit) -> dotGit
            Files.isRegularFile(dotGit) -> {
                val pointer = Files.readString(dotGit).trim().removePrefix("gitdir:").trim()
                if (pointer.isEmpty()) {
                    null
                } else {
                    val path = Path.of(pointer)
                    if (path.isAbsolute) path else base.resolve(path).normalize()
                }
            }
            else -> null
        }
    }

    private fun toOwnerRepo(url: String): String? {
        val cleaned = url.trim().removeSuffix(".git")
        HTTPS_URL.find(cleaned)?.let { return it.groupValues[1] }
        SSH_URL.find(cleaned)?.let { return it.groupValues[1] }
        return null
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val element = get(key) ?: return null
        return if (element.isJsonNull) null else element.asString
    }

    companion object {
        private val ORIGIN_URL =
            Regex("""\[remote "origin"\][^\[]*?url\s*=\s*(\S+)""", RegexOption.DOT_MATCHES_ALL)
        private val HTTPS_URL = Regex("""https?://[^/]+/(.+)""")
        private val SSH_URL = Regex("""[^@\s]+@[^:/\s]+:(.+)""")
    }
}
