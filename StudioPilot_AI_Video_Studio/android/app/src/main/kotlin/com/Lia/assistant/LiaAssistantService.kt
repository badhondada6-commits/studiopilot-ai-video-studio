package com.Lia.assistant

import android.app.Service
import android.content.Intent
import android.os.IBinder

class LiaAssistantService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
