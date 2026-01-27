package com.quark.leaks.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.quark.leaks.TrackerApp
import com.quark.leaks.databinding.ActivityTrackerLogBinding
import kotlinx.coroutines.launch

class TrackerLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrackerLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackerLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadTrackerStats()
    }

    private fun setupUI() {
        // Настройка Toolbar
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.toolbar.title = "Логи трекеров"

        // Показываем пустое состояние
        binding.emptyState.visibility = android.view.View.VISIBLE

        // Кнопка очистки логов
        binding.clearLogButton.setOnClickListener {
            showClearConfirmation()
        }
    }

    private fun loadTrackerStats() {
        lifecycleScope.launch {
            val trackerApp = application as TrackerApp
            val blockedCount = trackerApp.trackerRepository.getBlockedCount()

            binding.statsCard.visibility = android.view.View.VISIBLE
            binding.totalTrackersText.text = blockedCount.toString()

            // Простая заглушка для сегодня/неделя
            binding.todayTrackersText.text = "0"
            binding.weekTrackersText.text = blockedCount.toString()
        }
    }

    private fun showClearConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Очистка логов")
            .setMessage("Вы уверены что хотите очистить все логи трекеров?")
            .setPositiveButton("Очистить") { _, _ ->
                clearEvents()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun clearEvents() {
        lifecycleScope.launch {
            val trackerApp = application as TrackerApp
            trackerApp.trackerRepository.clearEvents()
            loadTrackerStats()

            // Показать сообщение об успехе
            android.widget.Toast.makeText(
                this@TrackerLogActivity,
                "Логи очищены",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}