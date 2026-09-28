package com.mhss.app.data.sync

import com.mhss.app.domain.repository.CloudSyncException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

/**
 * Minimal GitHub REST client used by Penguin Brain cloud sync.
 * The sync file lives in a (private) repository owned by the user, so no extra server is needed
 * and the same file can be read by the Penguin Brain website.
 */
internal class GitHubSyncApi(
    private val token: String,
    private val owner: String,
    private val repo: String,
    private val path: String = SYNC_FILE_PATH
) {

    data class RemoteFile(val content: String, val sha: String)

    class ConflictException : IOException("Remote file changed during sync")

    private val json = Json { ignoreUnknownKeys = true }

    fun getLogin(): String {
        val (code, body) = request("GET", "https://api.github.com/user")
        if (code == 401) throw CloudSyncException("Invalid GitHub token")
        if (code !in 200..299) throw CloudSyncException("GitHub error $code")
        return json.parseToJsonElement(body).jsonObject["login"]?.jsonPrimitive?.content ?: ""
    }

    fun checkRepo() {
        val (code, _) = request("GET", "https://api.github.com/repos/${enc(owner)}/${enc(repo)}")
        when (code) {
            in 200..299 -> Unit
            401 -> throw CloudSyncException("Invalid GitHub token")
            403, 404 -> throw CloudSyncException("Repository $owner/$repo not found or token has no access to it")
            else -> throw CloudSyncException("GitHub error $code")
        }
    }

    fun getFile(): RemoteFile? {
        val (code, body) = request("GET", contentsUrl())
        if (code == 404) return null
        if (code == 401) throw CloudSyncException("Invalid GitHub token")
        if (code !in 200..299) throw CloudSyncException("Could not read sync file (GitHub error $code)")
        val obj = json.parseToJsonElement(body).jsonObject
        val sha = obj.string("sha")
        var base64 = obj.string("content")
        if (base64.isBlank() && (obj["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L) > 0L) {
            // files > 1MB: content is not inlined, use the git blobs api instead
            val (blobCode, blobBody) = request(
                "GET",
                "https://api.github.com/repos/${enc(owner)}/${enc(repo)}/git/blobs/$sha"
            )
            if (blobCode !in 200..299) throw CloudSyncException("Could not read sync file (GitHub error $blobCode)")
            base64 = json.parseToJsonElement(blobBody).jsonObject.string("content")
        }
        val bytes = Base64.getMimeDecoder().decode(base64)
        return RemoteFile(String(bytes, Charsets.UTF_8), sha)
    }

    fun putFile(content: String, sha: String?, message: String) {
        val payload = buildJsonObject {
            put("message", message)
            put("content", Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8)))
            if (sha != null) put("sha", sha)
        }
        val (code, _) = request("PUT", contentsUrl(), payload.toString())
        when (code) {
            in 200..299 -> Unit
            409, 422 -> throw ConflictException()
            401 -> throw CloudSyncException("Invalid GitHub token")
            403, 404 -> throw CloudSyncException("Token has no write access to $owner/$repo (Contents: Read and write)")
            else -> throw CloudSyncException("Could not upload sync file (GitHub error $code)")
        }
    }

    private fun contentsUrl() =
        "https://api.github.com/repos/${enc(owner)}/${enc(repo)}/contents/" +
                path.split('/').joinToString("/") { enc(it) }

    private fun request(method: String, url: String, body: String? = null): Pair<Int, String> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            useCaches = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "PenguinBrain-Android")
            setRequestProperty("Cache-Control", "no-cache")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return code to text
        } catch (e: IOException) {
            throw CloudSyncException("Network error: ${e.message ?: "no connection"}")
        } finally {
            connection.disconnect()
        }
    }

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.content ?: ""

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    companion object {
        const val SYNC_FILE_PATH = "penguinbrain-sync.json"
    }
}
