package com.quark.leaks.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.quark.leaks.TrackerApp
import com.quark.leaks.data.model.TrackerEvent
import kotlinx.coroutines.launch
import java.util.Calendar

data class TrackerStats(
    val total: Int,
    val today: Int,
    val thisWeek: Int
)

class TrackerLogViewModel : ViewModel() {

    private val trackerRepository = TrackerApp.instance.trackerRepository

    private val _events = MutableLiveData<List<TrackerEvent>>()
    val events: LiveData<List<TrackerEvent>> = _events

    private val _stats = MutableLiveData<TrackerStats>()
    val stats: LiveData<TrackerStats> = _stats

    fun loadAllEvents() {
        viewModelScope.launch {
            trackerRepository.getAllEvents().collect { eventsList ->
                _events.postValue(eventsList)
            }
        }
    }

    fun loadEventsForToday() {
        viewModelScope.launch {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis

            trackerRepository.getAllEvents().collect { eventsList ->
                val todayEvents = eventsList.filter { it.timestamp >= startOfDay }
                _events.postValue(todayEvents)
            }
        }
    }

    fun loadEventsForWeek() {
        viewModelScope.launch {
            val calendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -7)
            }
            val weekAgo = calendar.timeInMillis

            trackerRepository.getAllEvents().collect { eventsList ->
                val weekEvents = eventsList.filter { it.timestamp >= weekAgo }
                _events.postValue(weekEvents)
            }
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            val allEvents = trackerRepository.getAllEvents()
                .asLiveData()
                .value ?: emptyList()

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis

            calendar.add(Calendar.DAY_OF_YEAR, -7)
            val weekAgo = calendar.timeInMillis

            val todayCount = allEvents.count { it.timestamp >= startOfDay }
            val weekCount = allEvents.count { it.timestamp >= weekAgo }

            _stats.value = TrackerStats(
                total = allEvents.size,
                today = todayCount,
                thisWeek = weekCount
            )
        }
    }

    fun clearEvents() {
        viewModelScope.launch {
            trackerRepository.clearEvents()
            loadAllEvents()
            loadStats()
        }
    }
}