package com.github.e0ipso.tokkl

import android.app.Application
import com.github.e0ipso.tokkl.api.ConfiguredServer
import com.github.e0ipso.tokkl.session.ServerConfigStore

class TokklApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Every process, whatever starts it, sends requests only to the stored server.
        ConfiguredServer.readStoredFrom(ServerConfigStore(this)::load)
    }
}
