package com.example.foz.model

import android.graphics.drawable.Icon

data class NotificationModel(
    val id: Int,
    val packageName: String,
    val title: String?,
    val text: String?,
    val postTime: Long,
    val isClearable: Boolean
)
