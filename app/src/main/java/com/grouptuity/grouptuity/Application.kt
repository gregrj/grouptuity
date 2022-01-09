package com.grouptuity.grouptuity

import android.app.Application
import com.grouptuity.grouptuity.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class GrouptuityApplication: Application() {
    val scope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Repository.getInstance(this)
    }
}