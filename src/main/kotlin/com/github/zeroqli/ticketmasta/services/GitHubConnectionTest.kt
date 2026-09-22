package com.github.zeroqli.ticketmasta.services

import com.google.gson.JsonParser

object GitHubConnectionTest {

    data class RepoInfo(val fullName: String, val isPrivate: Boolean)

    fun test(repo: String, token: String?): Result<RepoInfo> = try {
        val json = GitHubApi.requestJson("https://api.github.com/repos/$repo", token)

        val obj = JsonParser.parseString(json).asJsonObject
        Result.success(
            RepoInfo(
                fullName = obj.get("full_name").asString,
                isPrivate = obj.get("private")?.takeUnless { it.isJsonNull }?.asBoolean ?: false,
            ),
        )
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.message ?: e.toString()))
    }
}
