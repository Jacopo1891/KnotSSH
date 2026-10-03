package com.knotssh

import android.app.Application
import com.knotssh.data.diagnostics.DiagnosticsLog
import com.knotssh.data.sync.SyncEngine
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KnotSshApplication : Application() {

    @Inject
    lateinit var diagnostics: DiagnosticsLog

    @Inject
    lateinit var syncEngine: SyncEngine

    override fun onCreate() {
        super.onCreate()
        diagnostics.info("app", "started ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        installCrashRecorder()
        syncEngine.start()
    }

    /**
     * Records the crash and then hands it back to the platform handler, so the app still dies the
     * way Android expects and the stack trace is waiting in the log at the next launch.
     */
    private fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { diagnostics.crash(thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }
}
