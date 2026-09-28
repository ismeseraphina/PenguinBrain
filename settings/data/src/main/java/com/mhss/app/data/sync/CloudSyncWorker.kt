package com.mhss.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mhss.app.domain.repository.CloudSyncException
import com.mhss.app.domain.repository.CloudSyncRepository
import com.mhss.app.preferences.PrefsConstants
import com.mhss.app.preferences.domain.model.booleanPreferencesKey
import com.mhss.app.preferences.domain.model.stringPreferencesKey
import com.mhss.app.preferences.domain.use_case.GetPreferenceUseCase
import kotlinx.coroutines.flow.first
import org.koin.android.annotation.KoinWorker

@KoinWorker
class CloudSyncWorker(
    private val cloudSyncRepository: CloudSyncRepository,
    private val getPreference: GetPreferenceUseCase,
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val enabled = getPreference(booleanPreferencesKey(PrefsConstants.CLOUD_SYNC_AUTO), false).first()
        val token = getPreference(stringPreferencesKey(PrefsConstants.CLOUD_SYNC_TOKEN), "").first()
        if (!enabled || token.isBlank()) return Result.success()
        return try {
            cloudSyncRepository.sync()
            Result.success()
        } catch (e: CloudSyncException) {
            if (e.message?.startsWith("Network error") == true && runAttemptCount < 3) Result.retry()
            else Result.failure()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "penguin_cloud_sync_periodic"
        const val ONE_TIME_WORK_NAME = "penguin_cloud_sync_once"
    }
}
