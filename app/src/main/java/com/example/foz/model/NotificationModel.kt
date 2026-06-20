package com.example.foz.model

import android.app.PendingIntent

data class NotificationActionModel(
    val title: CharSequence?,
    val actionIntent: PendingIntent?
)

data class NotificationModel(
    val key: String,
    val id: Int,
    val packageName: String,
    val title: String?,
    val text: String?,
    val postTime: Long,
    val isClearable: Boolean,
    val actions: List<NotificationActionModel> = emptyList(),
    val largeIcon: android.graphics.Bitmap? = null,
    val contentIntent: PendingIntent? = null
)
