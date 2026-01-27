package com.quark.leaks

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.quark.leaks.data.repository.TrackerRepository
import com.quark.leaks.data.room.AppDatabase

class TrackerApp : Application() {

    companion object {
        const val CHANNEL_ID = "vpn_service_channel"
        const val NOTIFICATION_CHANNEL_ID = "tracker_notifications"

        lateinit var instance: TrackerApp
            private set
    }

    lateinit var trackerRepository: TrackerRepository
    private lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Инициализация БД
        database = AppDatabase.getDatabase(this)

        // Инициализация репозитория
        trackerRepository = TrackerRepository(database)

        // Создание каналов уведомлений
        createNotificationChannels()

        // Загрузка списка трекеров при старте
        trackerRepository.loadTrackers()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vpnChannel = NotificationChannel(
                CHANNEL_ID,
                "ВПН сервис",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Впн запущен в фоновом режиме"
                setShowBadge(false)
            }

            val trackerChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Предупреждение о трекерах",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомления о трекерах"
                setShowBadge(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(vpnChannel)
            notificationManager.createNotificationChannel(trackerChannel)
        }
    }
}