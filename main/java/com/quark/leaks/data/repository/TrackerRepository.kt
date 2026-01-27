package com.quark.leaks.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.quark.leaks.R
import com.quark.leaks.data.model.Tracker
import com.quark.leaks.data.model.TrackerEvent
import com.quark.leaks.data.room.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

class TrackerRepository(
    private val database: AppDatabase
) {

    companion object {
        private const val TAG = "TrackerRepository"
        private const val DEFAULT_TRACKER_LIST_URL = "https://raw.githubusercontent.com/duckduckgo/tracker-blocklists/main/app/android-tds.json"
    }

    private val trackers = ConcurrentHashMap<String, Tracker>()
    private val domainToTracker = ConcurrentHashMap<String, Tracker>()
    private val ipToTracker = ConcurrentHashMap<String, Tracker>()

    private val client = OkHttpClient()
    private val gson = Gson()

    fun loadTrackers() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Загружаем встроенный список трекеров
                loadBuiltinTrackers()

                // Загружаем обновленный список из интернета
                loadRemoteTrackers()

                Log.d(TAG, "Загружено ${trackers.size} трекеров")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки трекеров", e)
            }
        }
    }

    private suspend fun loadBuiltinTrackers() = withContext(Dispatchers.IO) {
        try {
            val json = """
            [
                {
                    "id": "google_analytics",
                    "name": "Google Analytics",
                    "category": "Analytics",
                    "domains": ["google-analytics.com", "googletagmanager.com", "analytics.google.com"],
                    "description": "Google's web analytics service",
                    "severity": 2
                },
                {
                    "id": "facebook",
                    "name": "Facebook Pixel",
                    "category": "Social",
                    "domains": ["facebook.com", "fbcdn.net", "facebook.net"],
                    "description": "Facebook tracking pixel",
                    "severity": 3
                },
                {
                    "id": "doubleclick",
                    "name": "Google DoubleClick",
                    "category": "Advertising",
                    "domains": ["doubleclick.net", "googleads.g.doubleclick.net"],
                    "description": "Google's advertising platform",
                    "severity": 3
                },
                {
                    "id": "amazon_ads",
                    "name": "Amazon Ads",
                    "category": "Advertising",
                    "domains": ["amazon-adsystem.com", "amazon.com"],
                    "description": "Amazon advertising services",
                    "severity": 2
                },
                {
                    "id": "microsoft",
                    "name": "Microsoft Clarity",
                    "category": "Analytics",
                    "domains": ["clarity.ms", "microsoft.com"],
                    "description": "Microsoft analytics service",
                    "severity": 2
                }
            ]
            """.trimIndent()

            val type = object : TypeToken<List<Tracker>>() {}.type
            val loadedTrackers: List<Tracker> = gson.fromJson(json, type)

            loadedTrackers.forEach { tracker ->
                trackers[tracker.id] = tracker
                tracker.domains.forEach { domain ->
                    domainToTracker[domain] = tracker
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка", e)
        }
    }

    private suspend fun loadRemoteTrackers() = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(DEFAULT_TRACKER_LIST_URL)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string()
                json?.let { parseTrackerJson(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка", e)
        }
    }

    private fun parseTrackerJson(json: String) {
        try {
            // Парсинг JSON из DuckDuckGo формата
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(json, type)

            val trackersObj = data["trackers"] as? Map<String, Map<String, Any>>
            trackersObj?.forEach { (id, trackerData) ->
                val domains = (trackerData["domains"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val tracker = Tracker(
                    id = id,
                    name = (trackerData["name"] as? String) ?: id,
                    category = (trackerData["category"] as? String) ?: "Unknown",
                    domains = domains,
                    description = trackerData["description"] as? String,
                    severity = (trackerData["severity"] as? Int) ?: 1
                )

                trackers[id] = tracker
                domains.forEach { domain ->
                    domainToTracker[domain] = tracker
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при парсинге трекеров JSON", e)
        }
    }

    fun isTrackerDomain(domain: String): Boolean {
        // Проверяем точное соответствие
        if (domainToTracker.containsKey(domain)) return true

        // Проверяем частичное соответствие
        return domainToTracker.keys.any { trackerDomain ->
            domain.contains(trackerDomain) || trackerDomain.contains(domain)
        }
    }

    fun isTrackerIp(ip: String): Boolean {
        return ipToTracker.containsKey(ip)
    }

    fun getTrackerForDomain(domain: String): Tracker? {
        return domainToTracker[domain]
    }

    fun getAllTrackers(): List<Tracker> {
        return trackers.values.toList()
    }

    fun getTrackersByCategory(category: String): List<Tracker> {
        return trackers.values.filter { it.category.equals(category, ignoreCase = true) }
    }

    suspend fun logTrackerEvent(event: TrackerEvent) {
        database.trackerEventDao().insert(event)
    }

    fun getAllEvents(): Flow<List<TrackerEvent>> {
        return database.trackerEventDao().getAllEvents()
    }

    suspend fun getBlockedCount(): Int {
        return database.trackerEventDao().getBlockedCount()
    }

    suspend fun clearEvents() {
        database.trackerEventDao().deleteAll()
    }

    // Метод для получения всех событий как список
    suspend fun getAllEventsList(): List<TrackerEvent> {
        return database.trackerEventDao().getAllEventsList()
    }
}