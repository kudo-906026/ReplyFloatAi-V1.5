package com.example

import android.app.Application
import com.example.state.AppStateManager

class ReplyFloatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppStateManager.init(this)
    }
}
