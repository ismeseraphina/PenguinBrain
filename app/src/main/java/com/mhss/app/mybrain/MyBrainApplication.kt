package com.mhss.app.mybrain

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.mhss.app.alarm.di.AlarmModule
import com.mhss.app.data.bookmarksDataModule
import com.mhss.app.data.calendarDataModule
import com.mhss.app.data.di.aiDataModule
import com.mhss.app.data.di.settingsDataModule
import com.mhss.app.data.diaryDataModule
import com.mhss.app.data.noteDataModule
import com.mhss.app.data.noteMarkdownModule
import com.mhss.app.data.noteRoomModule
import com.mhss.app.data.tasksDataModule
import com.mhss.app.database.di.databaseModule
import com.mhss.app.di.coroutinesModule
import com.mhss.app.domain.repository.CloudSyncRepository
import com.mhss.app.mybrain.di.MainPresentationModule
import com.mhss.app.mybrain.di.platformModule
import com.mhss.app.preferences.PrefsConstants
import com.mhss.app.preferences.di.PreferencesModule
import com.mhss.app.preferences.domain.model.booleanPreferencesKey
import com.mhss.app.preferences.domain.model.stringPreferencesKey
import com.mhss.app.preferences.domain.use_case.GetPreferenceUseCase
import com.mhss.app.presentation.di.AiPresentationModule
import com.mhss.app.presentation.di.BookmarksPresentationModule
import com.mhss.app.presentation.di.CalendarPresentationModule
import com.mhss.app.presentation.di.DiaryPresentationModule
import com.mhss.app.presentation.di.NotePresentationModule
import com.mhss.app.presentation.di.SettingsPresentationModule
import com.mhss.app.presentation.di.TasksPresentationModule
import com.mhss.app.ui.R
import com.mhss.app.util.Constants
import com.mhss.app.widget.di.WidgetModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.ksp.generated.module
import kotlin.system.exitProcess

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = PrefsConstants.SETTINGS_PREFERENCES)

class MyBrainApplication : Application() {

    private val getPreference: GetPreferenceUseCase by inject()
    private val cloudSyncRepository: CloudSyncRepository by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            allowOverride(true)
            androidContext(this@MyBrainApplication)
            androidLogger()
            modules(
                platformModule,
                MainPresentationModule().module,
                AlarmModule().module,
                databaseModule,
                coroutinesModule,
                PreferencesModule().module,
                NotePresentationModule().module,
                noteDataModule,
                DiaryPresentationModule().module,
                diaryDataModule,
                TasksPresentationModule().module,
                tasksDataModule,
                SettingsPresentationModule().module,
                settingsDataModule,
                CalendarPresentationModule().module,
                calendarDataModule,
                BookmarksPresentationModule().module,
                bookmarksDataModule,
                WidgetModule().module,
                aiDataModule,
                AiPresentationModule().module
            )
            workManagerFactory()
        }
        loadNotesModule()
        // Penguin Brain: sync with the website on app start (worker exits if auto sync is off)
        runCatching { cloudSyncRepository.requestBackgroundSync() }

        createRemindersNotificationChannel()
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            e.printStackTrace()
            "```\n${e.stackTraceToString()}\n```".copyToClipboard()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, getString(R.string.exception_stack_trace_copied), Toast.LENGTH_LONG).show()
            }
            exitProcess(1)
        }
    }

    private fun loadNotesModule() = runBlocking {
        val isExternalNotesEnabled = getPreference(
            booleanPreferencesKey(PrefsConstants.EXTERNAL_NOTES_ENABLED),
            false
        ).first()
        val rootUri = getPreference(
            stringPreferencesKey(PrefsConstants.EXTERNAL_NOTES_FOLDER_URI),
            ""
        ).first()

        if (isExternalNotesEnabled && rootUri.isNotBlank()) {
            loadKoinModules(noteMarkdownModule(rootUri))
        } else {
            loadKoinModules(noteRoomModule)
        }
    }

    private fun createRemindersNotificationChannel() {
        val channel = NotificationChannel(
            Constants.REMINDERS_CHANNEL_ID,
            getString(R.string.reminders_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = getString(R.string.reminders_channel_description)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

    }

    private fun String.copyToClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("label", this)
        clipboard.setPrimaryClip(clip)
    }
}
