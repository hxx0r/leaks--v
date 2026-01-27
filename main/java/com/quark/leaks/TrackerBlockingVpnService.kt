package com.quark.leaks.vpn

import android.app.PendingIntent
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.quark.leaks.R
import com.quark.leaks.TrackerApp
import com.quark.leaks.ui.MainActivity
import com.quark.leaks.data.model.TrackerEvent
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class TrackerBlockingVpnService : VpnService() {

    companion object {
        private const val TAG = "TrackerVPN"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.quark.leaks.START_VPN"
        const val ACTION_STOP = "com.quark.leaks.STOP_VPN"
        const val VPN_ADDRESS = "10.0.0.2"
        const val VPN_ROUTE = "0.0.0.0"
        const val VPN_DNS = "8.8.8.8"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private var vpnJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VPN Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning.get()) return

        try {
            // Настройка VPN интерфейса
            val builder = Builder()
                .setSession("TrackerBlocker VPN")
                .addAddress(VPN_ADDRESS, 32)
                .addRoute(VPN_ROUTE, 0)
                .addDnsServer(VPN_DNS)
                .setBlocking(true)
                .setMtu(1500)

            // Для Android 5.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.setConfigureIntent(getConfigureIntent())
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            vpnInterface = builder.establish()

            //  уведомление для Foreground Service
            createNotification()

            // запускаем обработку трафика
            vpnJob = CoroutineScope(Dispatchers.IO).launch {
                isRunning.set(true)
                runVpn()
            }

            Log.d(TAG, "VPN включен")

        } catch (e: Exception) {
            Log.e(TAG, "Не удалось включить VPN", e)
            stopSelf()
        }
    }

    private fun getConfigureIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }


    private fun createNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, TrackerApp.CHANNEL_ID)
            .setContentTitle("Защита от трекеров включна")
            .setContentText("ь")
            .setSmallIcon(R.drawable.ic_vpn)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private suspend fun runVpn() {
        val inputStream = FileInputStream(vpnInterface!!.fileDescriptor)
        val outputStream = FileOutputStream(vpnInterface!!.fileDescriptor)

        val buffer = ByteBuffer.allocate(32767)

        while (isRunning.get()) {
            try {
                // Читаем входящие пакеты
                val length = inputStream.read(buffer.array())
                if (length > 0) {
                    buffer.limit(length)

                    // Анализируем пакет
                    val packetInfo = analyzePacket(buffer)

                    if (packetInfo != null) {
                        // Проверяем на трекер
                        if (isTracker(packetInfo.destinationIp, packetInfo.domain)) {
                            // Блокируем трекер
                            logTrackerEvent(packetInfo)
                            continue // Пропускаем пакет
                        }
                    }

                    // Если не трекер, пропускаем пакет дальше
                    buffer.rewind()
                    outputStream.write(buffer.array(), 0, length)
                }

                // Небольшая пауза чтобы не грузить процессор
                delay(10)

            } catch (e: IOException) {
                if (isRunning.get()) {
                    Log.e(TAG, "ошибка чтения", e)
                }
                break
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка", e)
            }
        }
    }

    private data class PacketInfo(
        val destinationIp: String,
        val domain: String?,
        val packetType: String
    )

    private fun analyzePacket(buffer: ByteBuffer): PacketInfo? {
        try {
            // Простой анализ IP заголовка (упрощенно)

            val version = (buffer[0].toInt() and 0xF0) shr 4

            return when (version) {
                4 -> analyzeIPv4Packet(buffer)
                6 -> analyzeIPv6Packet(buffer)
                else -> null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка анализа пакета", e)
            return null
        }
    }

    private fun analyzeIPv4Packet(buffer: ByteBuffer): PacketInfo? {
        try {
            val destIp = ByteArray(4)
            buffer.position(16) // Позиция IP назначения в IPv4 заголовке
            buffer.get(destIp)

            val ipAddress = InetAddress.getByAddress(destIp).hostAddress
            val protocol = buffer[9].toInt() and 0xFF

            return when (protocol) {
                17 -> analyzeUdpPacket(buffer, ipAddress) // UDP
                6 -> analyzeTcpPacket(buffer, ipAddress)  // TCP
                else -> PacketInfo(ipAddress, null, "IP-$protocol")
            }

        } catch (e: Exception) {
            return null
        }
    }



    private fun analyzeUdpPacket(buffer: ByteBuffer, destIp: String): PacketInfo? {
        try {
            val ihl = (buffer[0].toInt() and 0x0F) * 4
            buffer.position(ihl + 2) // Позиция порта назначения в UDP

            val destPort = buffer.short.toInt() and 0xFFFF

            // DNS порт 53
            if (destPort == 53) {
                return analyzeDnsPacket(buffer, destIp, ihl)
            }

            return PacketInfo(destIp, null, "UDP-$destPort")

        } catch (e: Exception) {
            return null
        }
    }

    private fun analyzeDnsPacket(buffer: ByteBuffer, destIp: String, ihl: Int): PacketInfo? {
        try {
            buffer.position(ihl + 8) // Начало DNS данных

            // Пропускаем DNS заголовок (12 байт)
            buffer.position(buffer.position() + 12)

            // Читаем запрос
            val domainBuilder = StringBuilder()
            var labelLength = buffer.get().toInt() and 0xFF

            while (labelLength > 0 && labelLength <= 63) {
                for (i in 0 until labelLength) {
                    domainBuilder.append(buffer.get().toChar())
                }
                labelLength = buffer.get().toInt() and 0xFF
                if (labelLength > 0) domainBuilder.append('.')
            }

            val domain = domainBuilder.toString()
            return PacketInfo(destIp, domain, "DNS")

        } catch (e: Exception) {
            return PacketInfo(destIp, null, "DNS")
        }
    }

    private fun analyzeTcpPacket(buffer: ByteBuffer, destIp: String): PacketInfo? {
        try {
            val ihl = (buffer[0].toInt() and 0x0F) * 4
            buffer.position(ihl) // Начало TCP заголовка

            val destPort = buffer.getShort(2).toInt() and 0xFFFF

            // HTTP(S) порты
            return when (destPort) {
                80, 443, 8080 -> PacketInfo(destIp, null, "HTTP-$destPort")
                else -> PacketInfo(destIp, null, "TCP-$destPort")
            }

        } catch (e: Exception) {
            return null
        }
    }

    private fun analyzeIPv6Packet(buffer: ByteBuffer): PacketInfo? {
        // Упрощенный анализ IPv6
        val destIp = ByteArray(16)
        buffer.position(24) // Позиция IP назначения в IPv6
        buffer.get(destIp)

        val ipAddress = InetAddress.getByAddress(destIp).hostAddress
        return PacketInfo(ipAddress, null, "IPv6")
    }

    private fun isTracker(ip: String, domain: String?): Boolean {
        // Проверяем по базе трекеров
        val trackerRepository = (application as TrackerApp).trackerRepository

        domain?.let {
            // Проверка по домену
            if (trackerRepository.isTrackerDomain(it)) {
                Log.d(TAG, "TДомейн трекера обнаружен: $it")
                return true
            }
        }

        // Проверка по IP
        if (trackerRepository.isTrackerIp(ip)) {
            Log.d(TAG, "Айпи трекер обнаружен: $ip")
            return true
        }

        return false
    }

    private fun logTrackerEvent(packetInfo: PacketInfo) {
        val trackerRepository = (application as TrackerApp).trackerRepository

        CoroutineScope(Dispatchers.IO).launch {
            val event = TrackerEvent(
                timestamp = System.currentTimeMillis(),
                domain = packetInfo.domain,
                ipAddress = packetInfo.destinationIp,
                packetType = packetInfo.packetType,
                blocked = true
            )

            trackerRepository.logTrackerEvent(event)

            // Отправляем событие в Activity через LocalBroadcast
            sendTrackerEvent(event)

            // Отправляем уведомление
            sendTrackerNotification(event)
        }
    }

    private fun sendTrackerEvent(event: TrackerEvent) {
        // Отправляем событие в Activity через LocalBroadcast
        val intent = Intent("TRACKER_DETECTED")
        intent.putExtra("domain", event.domain)
        intent.putExtra("ip", event.ipAddress)
        sendBroadcast(intent)
    }

    private fun sendTrackerNotification(event: TrackerEvent) {
        // Создаем уведомление о заблокированном трекере
        val notification = NotificationCompat.Builder(this, TrackerApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Трекер заблокирован")
            .setContentText("Заблокирован ${event.domain ?: event.ipAddress}")
            .setSmallIcon(R.drawable.ic_danger) // Используем ic_danger вместо ic_block
            .setAutoCancel(true)
            .build()

        // Используем другой ID для уведомлений о трекерах
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun stopVpn() {
        isRunning.set(false)
        vpnJob?.cancel()

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка", e)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Log.d(TAG, "ВПН остановлен")
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}

