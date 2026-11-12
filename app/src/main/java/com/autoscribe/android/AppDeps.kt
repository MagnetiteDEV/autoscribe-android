package com.autoscribe.android

import android.content.Context
import com.autoscribe.android.data.ResultsDatabase
import com.autoscribe.android.data.SettingsStore
import com.autoscribe.android.engine.PingRunner
import com.autoscribe.android.engine.WireGuardController

/** Single shared instances, initialised once from the application context. */
object AppDeps {
    private lateinit var appContext: Context

    fun init(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
        }
    }

    val context: Context get() = appContext

    val db: ResultsDatabase by lazy { ResultsDatabase(appContext) }
    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val wireGuard: WireGuardController by lazy { WireGuardController(appContext) }
    val pinger: PingRunner by lazy { PingRunner() }
}
