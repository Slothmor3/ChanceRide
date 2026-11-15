package com.example.chanceride

import android.app.Application
import com.example.chanceride.data.SessionStore

/** Application-scoped repository; instrumentation can substitute isolated local storage. */
class RideApplication : Application() {
    lateinit var sessions: SessionStore
    override fun onCreate() {
        super.onCreate()
        sessions = SessionStore(this)
    }
}
