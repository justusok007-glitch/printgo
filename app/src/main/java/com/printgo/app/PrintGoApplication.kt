package com.printgo.app

import android.app.Application
import com.printgo.app.data.print.PrintJobManager
import com.printgo.app.data.print.PrintNotifications

class PrintGoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PrintNotifications.createChannels(this)
        PrintJobManager.init(this)
    }
}
