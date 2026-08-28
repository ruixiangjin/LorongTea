package com.lorongtea.mobile

import android.app.Application
import com.lorongtea.mobile.data.Backend

class LorongTeaApplication : Application() {
    val backend by lazy { Backend(BuildConfig.API_URL) }
}
