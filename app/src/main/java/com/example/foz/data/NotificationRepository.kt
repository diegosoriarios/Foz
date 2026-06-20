package com.example.foz.data

import com.example.foz.model.NotificationModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotificationRepository {
    private val _notifications = MutableStateFlow<List<NotificationModel>>(emptyList())
    val notifications = _notifications.asStateFlow()

    fun updateNotifications(newList: List<NotificationModel>) {
        _notifications.value = newList
    }

    companion object {
        private var instance: NotificationRepository? = null
        fun getInstance(): NotificationRepository {
            if (instance == null) {
                instance = NotificationRepository()
            }
            return instance!!
        }
    }
}
