package com.mhss.app.data.sync

import android.content.Context
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mhss.app.database.MyBrainDatabase
import com.mhss.app.database.entity.toTask
import com.mhss.app.domain.repository.CloudSyncException
import com.mhss.app.domain.repository.CloudSyncRepository
import com.mhss.app.domain.repository.CloudSyncSummary
import com.mhss.app.domain.use_case.DeleteTaskUseCase
import com.mhss.app.domain.use_case.UpsertTaskUseCase
import com.mhss.app.preferences.PrefsConstants
import com.mhss.app.preferences.domain.model.booleanPreferencesKey
import com.mhss.app.preferences.domain.model.stringPreferencesKey
import com.mhss.app.preferences.domain.use_case.GetPreferenceUseCase
import com.mhss.app.preferences.domain.use_case.SavePreferenceUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import java.io.File
import java.util.concurrent.TimeUnit

@Single
class CloudSyncRepositoryImpl(
    private val context: Context,
    private val database: MyBrainDatabase,
    private val upsertTaskUseCase: UpsertTaskUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val getPreference: GetPreferenceUseCase,
    private val savePreference: SavePreferenceUseCase,
    @Named("ioDispatcher") private val ioDispatcher: CoroutineDispatcher
) : CloudSyncRepository {

    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
        coerceInputValues = true
    }
    private val stateFile by lazy { File(context.filesDir, "penguin_cloud_sync_state.json") }
    private val workManager by lazy { WorkManager.getInstance(context) }

    override suspend fun signIn(token: String, owner: String, repo: String): String =
        withContext(ioDispatcher) {
            val cleanToken = token.trim()
            val cleanOwner = owner.trim()
            val cleanRepo = repo.trim()
            if (cleanToken.isBlank() || cleanOwner.isBlank() || cleanRepo.isBlank()) {
                throw CloudSyncException("Token, owner and repository are required")
            }
            val api = GitHubSyncApi(cleanToken, cleanOwner, cleanRepo)
            val login = api.getLogin()
            api.checkRepo()
            savePreference(stringPreferencesKey(PrefsConstants.CLOUD_SYNC_TOKEN), cleanToken)
            savePreference(stringPreferencesKey(PrefsConstants.CLOUD_SYNC_OWNER), cleanOwner)
            savePreference(stringPreferencesKey(PrefsConstants.CLOUD_SYNC_REPO), cleanRepo)
            savePreference(stringPreferencesKey(PrefsConstants.CLOUD_SYNC_LOGIN), login)
            savePreference(stringPreferencesKey(PrefsConstants.CLOUD_SYNC_LAST_ERROR), "")
            stateFile.delete()
            login
        }

    override suspend fun signOut() {
        withContext(ioDispatcher) {
            setAutoSync(false)
            savePreference(booleanPreferencesKey(PrefsConstants.CLOUD_SYNC_AUTO), false)
            savePreference(stringPreferencesKey(PrefsConstants.CLOUD_SYNC_TOKEN), "")
            savePreference(stringPreferencesKey(PrefsConstants.CLOUD_SYNC_LOGIN), "")
            savePreference(stringPreferencesKey(PrefsConstants.CLOUD_SYNC_LAST_TIME), "")
            savePreference(stringPreferencesKey(PrefsConstants.CLOUD_SYNC_LAST_ERROR), "")
            stateFile.delete()
        }
    }

    override suspend fun sync(): CloudSyncSummary = mutex.withLock {
        withContext(ioDispatcher) {
            val token = pref(PrefsConstants.CLOUD_SYNC_TOKEN)
            val owner = pref(PrefsConstants.CLOUD_SYNC_OWNER)
            val repo = pref(PrefsConstants.CLOUD_SYNC_REPO)
            if (token.isBlank() || owner.isBlank() || repo.isBlank()) {
                throw CloudSyncException("Not signed in")
            }
            val api = GitHubSyncApi(token, owner, repo)
            try {
                var attempt = 0
                var summary: CloudSyncSummary? = null
                while (summary == null) {
                    try {
                        summary = syncOnce(api, "$owner/$repo")
                    } catch (_: GitHubSyncApi.ConflictException) {
                        attempt++
                        if (attempt >= 3) throw CloudSyncException("Sync conflict, please try again")
                    }
                }
                savePreference(
                    stringPreferencesKey(PrefsConstants.CLOUD_SYNC_LAST_TIME),
                    summary!!.time.toString()
                )
                savePreference(stringPreferencesKey(PrefsConstants.CLOUD_SYNC_LAST_ERROR), "")
                summary!!
            } catch (e: CloudSyncException) {
                savePreference(stringPreferencesKey(PrefsConstants.CLOUD_SYNC_LAST_ERROR), e.message ?: "")
                throw e
            } catch (e: SerializationException) {
                val error = CloudSyncException("Sync file is not valid: ${e.message}")
                savePreference(stringPreferencesKey(PrefsConstants.CLOUD_SYNC_LAST_ERROR), error.message ?: "")
                throw error
            } catch (e: Exception) {
                val error = CloudSyncException(e.message ?: "Unknown sync error")
                savePreference(stringPreferencesKey(PrefsConstants.CLOUD_SYNC_LAST_ERROR), error.message ?: "")
                throw error
            }
        }
    }

    private suspend fun syncOnce(api: GitHubSyncApi, account: String): CloudSyncSummary {
        val now = System.currentTimeMillis()
        val remoteFile = api.getFile()
        val remote = remoteFile?.let { json.decodeFromString<SyncFile>(it.content) } ?: SyncFile()
        val state = readState().takeIf { it.account == account } ?: SyncState(account = account)
        val base = state.ids.mapValues { it.value.toSet() }

        val externalNotes = getPreference(
            booleanPreferencesKey(PrefsConstants.EXTERNAL_NOTES_ENABLED),
            false
        ).first()

        val noteDao = database.noteDao()
        val taskDao = database.taskDao()
        val diaryDao = database.diaryDao()
        val bookmarkDao = database.bookmarkDao()

        val localNotes = if (externalNotes) null else noteDao.getAllFullNotes()
        val localFolders = if (externalNotes) null else noteDao.getAllNoteFolders().first()
        val localTasksRaw = taskDao.getAllFullTasks()
        val localTasks = localTasksRaw.map { it.copy(alarmId = null) }
        val localDiary = diaryDao.getAllFullEntries()
        val localBookmarks = bookmarkDao.getAllFullBookmarks()

        val tombs = HashMap<String, Long>()
        remote.deleted.forEach { t ->
            val key = key(t.type, t.id)
            tombs[key] = maxOf(tombs[key] ?: 0L, t.at)
        }

        val mergedFolders = if (localFolders == null) remote.noteFolders else merge(
            SyncTypes.NOTE_FOLDERS, localFolders, remote.noteFolders, { it.id }, { null },
            base[SyncTypes.NOTE_FOLDERS].orEmpty(), tombs, now
        )
        val mergedNotes = if (localNotes == null) remote.notes else merge(
            SyncTypes.NOTES, localNotes, remote.notes, { it.id }, { it.updatedDate },
            base[SyncTypes.NOTES].orEmpty(), tombs, now
        )
        val mergedTasks = merge(
            SyncTypes.TASKS, localTasks, remote.tasks.map { it.copy(alarmId = null) },
            { it.id }, { it.updatedDate },
            base[SyncTypes.TASKS].orEmpty(), tombs, now
        )
        val mergedDiary = merge(
            SyncTypes.DIARY, localDiary, remote.diary, { it.id }, { it.updatedDate },
            base[SyncTypes.DIARY].orEmpty(), tombs, now
        )
        val mergedBookmarks = merge(
            SyncTypes.BOOKMARKS, localBookmarks, remote.bookmarks, { it.id }, { it.updatedDate },
            base[SyncTypes.BOOKMARKS].orEmpty(), tombs, now
        )

        // ---- apply merged state locally ----
        var pulled = 0
        val folderDiff = localFolders?.let { diff(it, mergedFolders) { f -> f.id } }
        val notesDiff = localNotes?.let { diff(it, mergedNotes) { n -> n.id } }
        val diaryDiff = diff(localDiary, mergedDiary) { it.id }
        val bookmarksDiff = diff(localBookmarks, mergedBookmarks) { it.id }
        val tasksDiff = diff(localTasks, mergedTasks) { it.id }

        database.withTransaction {
            if (folderDiff != null && folderDiff.first.isNotEmpty()) {
                noteDao.upsertNoteFolders(folderDiff.first)
            }
            if (notesDiff != null) {
                if (notesDiff.first.isNotEmpty()) noteDao.upsertNotes(notesDiff.first)
                notesDiff.second.forEach { noteDao.deleteNote(it) }
            }
            folderDiff?.second?.forEach { noteDao.deleteNoteFolderById(it.id) }
            if (diaryDiff.first.isNotEmpty()) diaryDao.upsertEntries(diaryDiff.first)
            diaryDiff.second.forEach { diaryDao.deleteEntry(it) }
            if (bookmarksDiff.first.isNotEmpty()) bookmarkDao.upsertBookmarks(bookmarksDiff.first)
            bookmarksDiff.second.forEach { bookmarkDao.deleteBookmark(it) }
        }
        pulled += (folderDiff?.let { it.first.size + it.second.size } ?: 0) +
                (notesDiff?.let { it.first.size + it.second.size } ?: 0) +
                diaryDiff.first.size + diaryDiff.second.size +
                bookmarksDiff.first.size + bookmarksDiff.second.size

        val rawById = localTasksRaw.associateBy { it.id }
        val taskChanges = tasksDiff.first.size + tasksDiff.second.size
        var taskIndex = 0
        tasksDiff.first.forEach { entity ->
            taskIndex++
            val previous = rawById[entity.id]?.toTask()
            val task = entity.toTask().copy(alarmId = previous?.alarmId)
            // Never roll a recurring task while syncing, the other device already did that.
            val safePrevious = if (previous != null && task.isCompleted && task.recurring && !previous.isCompleted) {
                previous.copy(isCompleted = true)
            } else previous
            upsertTaskUseCase(
                task = task,
                previousTask = safePrevious,
                updateWidget = taskIndex == taskChanges
            )
        }
        tasksDiff.second.forEach { entity ->
            deleteTaskUseCase((rawById[entity.id] ?: entity).toTask())
        }
        pulled += taskChanges

        // ---- upload ----
        val tombList = tombs
            .filter { now - it.value < TOMBSTONE_TTL_MS }
            .map { (k, at) ->
                val (type, id) = k.split(KEY_SEPARATOR, limit = 2)
                Tombstone(type, id, at)
            }
        val newFile = SyncFile(
            updatedAt = now,
            updatedBy = "android",
            notes = mergedNotes,
            noteFolders = mergedFolders,
            tasks = mergedTasks,
            diary = mergedDiary,
            bookmarks = mergedBookmarks,
            deleted = tombList
        )
        val pushed = countChanges(remote, newFile)
        val needsUpload = remoteFile == null || normalize(newFile) != normalize(remote)
        if (needsUpload) {
            api.putFile(
                json.encodeToString(SyncFile.serializer(), newFile),
                remoteFile?.sha,
                "Penguin Brain sync from Android"
            )
        }

        val newIds = mutableMapOf(
            SyncTypes.TASKS to mergedTasks.map { it.id },
            SyncTypes.DIARY to mergedDiary.map { it.id },
            SyncTypes.BOOKMARKS to mergedBookmarks.map { it.id }
        )
        if (!externalNotes) {
            newIds[SyncTypes.NOTES] = mergedNotes.map { it.id }
            newIds[SyncTypes.NOTE_FOLDERS] = mergedFolders.map { it.id }
        }
        writeState(SyncState(account = account, ids = newIds))

        return CloudSyncSummary(
            pulledChanges = pulled,
            pushedChanges = if (needsUpload) pushed else 0,
            uploaded = needsUpload,
            time = now
        )
    }

    /**
     * Last-writer-wins merge by id using updatedDate, with tombstones for deletions.
     * Items without updatedDate (folders) prefer the local copy.
     */
    private fun <T> merge(
        type: String,
        local: List<T>,
        remote: List<T>,
        id: (T) -> String,
        updated: (T) -> Long?,
        base: Set<String>,
        tombs: MutableMap<String, Long>,
        now: Long
    ): List<T> {
        val localIds = local.map(id).toSet()
        base.forEach { baseId ->
            if (baseId !in localIds) {
                val k = key(type, baseId)
                tombs[k] = maxOf(tombs[k] ?: 0L, now)
            }
        }
        val map = LinkedHashMap<String, T>()
        remote.forEach { map[id(it)] = it }
        local.forEach { item ->
            val existing = map[id(item)]
            val localUpdated = updated(item)
            if (existing == null || localUpdated == null || localUpdated > (updated(existing) ?: 0L)) {
                map[id(item)] = item
            }
        }
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            val (itemId, item) = iterator.next()
            val k = key(type, itemId)
            val deletedAt = tombs[k] ?: continue
            val itemUpdated = updated(item)
            if (itemUpdated != null && itemUpdated > deletedAt) {
                tombs.remove(k) // edited after it was deleted somewhere else: keep it
            } else {
                iterator.remove()
            }
        }
        return map.values.sortedBy(id)
    }

    /** @return items to upsert locally and local items to delete */
    private fun <T> diff(local: List<T>, merged: List<T>, id: (T) -> String): Pair<List<T>, List<T>> {
        val localById = local.associateBy(id)
        val mergedIds = merged.map(id).toSet()
        val upserts = merged.filter { localById[id(it)] != it }
        val deletes = local.filter { id(it) !in mergedIds }
        return upserts to deletes
    }

    private fun normalize(file: SyncFile) = file.copy(
        format = SyncFile.FORMAT,
        version = 1,
        updatedAt = 0L,
        updatedBy = "",
        notes = file.notes.sortedBy { it.id },
        noteFolders = file.noteFolders.sortedBy { it.id },
        tasks = file.tasks.map { it.copy(alarmId = null) }.sortedBy { it.id },
        diary = file.diary.sortedBy { it.id },
        bookmarks = file.bookmarks.sortedBy { it.id },
        deleted = file.deleted.sortedWith(compareBy({ it.type }, { it.id }))
    )

    private fun countChanges(old: SyncFile, new: SyncFile): Int {
        fun <T> count(a: List<T>, b: List<T>, id: (T) -> String): Int {
            val aById = a.associateBy(id)
            val bIds = b.map(id).toSet()
            return b.count { aById[id(it)] != it } + a.count { id(it) !in bIds }
        }
        return count(old.notes, new.notes) { it.id } +
                count(old.noteFolders, new.noteFolders) { it.id } +
                count(old.tasks.map { it.copy(alarmId = null) }, new.tasks) { it.id } +
                count(old.diary, new.diary) { it.id } +
                count(old.bookmarks, new.bookmarks) { it.id }
    }

    override fun setAutoSync(enabled: Boolean) {
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(networkConstraints())
                .build()
            workManager.enqueueUniquePeriodicWork(
                CloudSyncWorker.PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } else {
            workManager.cancelUniqueWork(CloudSyncWorker.PERIODIC_WORK_NAME)
        }
    }

    override fun requestBackgroundSync() {
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(networkConstraints())
            .build()
        workManager.enqueueUniqueWork(
            CloudSyncWorker.ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun networkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private suspend fun pref(name: String): String =
        getPreference(stringPreferencesKey(name), "").first()

    private fun readState(): SyncState = try {
        if (stateFile.exists()) json.decodeFromString(SyncState.serializer(), stateFile.readText())
        else SyncState()
    } catch (_: Exception) {
        SyncState()
    }

    private fun writeState(state: SyncState) {
        stateFile.writeText(json.encodeToString(SyncState.serializer(), state))
    }

    private fun key(type: String, id: String) = "$type$KEY_SEPARATOR$id"

    private companion object {
        const val KEY_SEPARATOR = "::"
        const val TOMBSTONE_TTL_MS = 180L * 24 * 60 * 60 * 1000
    }
}
