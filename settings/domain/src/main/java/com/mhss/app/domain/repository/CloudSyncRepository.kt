package com.mhss.app.domain.repository

/**
 * Penguin Brain cloud sync.
 * Syncs the local database with a JSON file stored in a private GitHub repository,
 * so the Android app and the Penguin Brain website can share the same data.
 */
interface CloudSyncRepository {
    /** Verifies the token + repository and returns the GitHub login of the signed in account. */
    suspend fun signIn(token: String, owner: String, repo: String): String

    suspend fun signOut()

    /** Runs a full two-way sync. Throws [CloudSyncException] on failure. */
    suspend fun sync(): CloudSyncSummary

    fun setAutoSync(enabled: Boolean)

    /** Enqueues a one-time background sync (only runs if the user enabled auto sync). */
    fun requestBackgroundSync()
}

data class CloudSyncSummary(
    val pulledChanges: Int,
    val pushedChanges: Int,
    val uploaded: Boolean,
    val time: Long
)

class CloudSyncException(message: String) : Exception(message)
