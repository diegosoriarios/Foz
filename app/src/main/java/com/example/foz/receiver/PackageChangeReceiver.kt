package com.example.foz.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PackageChangeReceiver(
    private val onPackageChanged: () -> Unit
) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_PACKAGE_ADDED || action == Intent.ACTION_PACKAGE_REMOVED) {
            onPackageChanged()
        }
    }
}
