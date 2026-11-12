package com.popquest.bubbleshooter

import android.app.Application
import com.google.android.gms.games.PlayGamesSdk

class PopQuestApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PlayGamesSdk.initialize(this)
        Reminders.ensureChannel(this)
    }
}
