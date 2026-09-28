package com.mhss.app.data.sync

import com.mhss.app.database.entity.BookmarkEntity
import com.mhss.app.database.entity.DiaryEntryEntity
import com.mhss.app.database.entity.NoteEntity
import com.mhss.app.database.entity.NoteFolderEntity
import com.mhss.app.database.entity.TaskEntity
import kotlinx.serialization.Serializable

/**
 * File stored in the user's GitHub repository.
 * It is a superset of the normal JSON backup format, so it can also be imported
 * from Settings > Export/Import.
 */
@Serializable
data class SyncFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val updatedAt: Long = 0L,
    val updatedBy: String = "",
    val notes: List<NoteEntity> = emptyList(),
    val noteFolders: List<NoteFolderEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val diary: List<DiaryEntryEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val deleted: List<Tombstone> = emptyList()
) {
    companion object {
        const val FORMAT = "penguinbrain-sync"
    }
}

@Serializable
data class Tombstone(
    val type: String,
    val id: String,
    val at: Long
)

/** Ids known at the last successful sync, used to detect local deletions. */
@Serializable
data class SyncState(
    val account: String = "",
    val ids: Map<String, List<String>> = emptyMap()
)

internal object SyncTypes {
    const val NOTES = "notes"
    const val NOTE_FOLDERS = "noteFolders"
    const val TASKS = "tasks"
    const val DIARY = "diary"
    const val BOOKMARKS = "bookmarks"
}
