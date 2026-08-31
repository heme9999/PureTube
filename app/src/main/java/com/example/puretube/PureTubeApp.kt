package com.example.puretube

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe

class PureTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(DownloaderImpl())
    }
}
