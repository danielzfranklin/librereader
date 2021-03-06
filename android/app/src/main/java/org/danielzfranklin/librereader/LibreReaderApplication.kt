package org.danielzfranklin.librereader

import android.app.Application
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import timber.log.Timber

@Suppress("unused") // used in AndroidManifest.xml
class LibreReaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(LogDebugTree())

        if (BuildConfig.DEBUG) {
            val policy = VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
            StrictMode.setVmPolicy(policy)
        }
    }

    class LogDebugTree : Timber.DebugTree() {
        override fun createStackElementTag(element: StackTraceElement): String {
            return "LRTAG(${element.fileName}:${element.lineNumber})#${element.methodName}"
        }
    }
}