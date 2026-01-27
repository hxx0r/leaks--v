package com.quark.leaks.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.quark.leaks.R
import com.quark.leaks.TrackerApp
import com.quark.leaks.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = android.preference.PreferenceManager.getDefaultSharedPreferences(this)

        setupUI()
        setupListeners()
        loadSettings()
    }

    private fun setupUI() {
        // Настройка Toolbar
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.toolbar.title = "Настройки"

        // Устанавливаем цвет статус бара
        window.statusBarColor = ContextCompat.getColor(this, R.color.background)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.background)
    }

    private fun setupListeners() {
        // VPN настройки
        binding.autoStartVpnSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean("auto_start_vpn", isChecked).apply()
        }

        binding.allowBypassSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean("allow_bypass", isChecked).apply()
        }

        // Уведомления
        binding.trackerNotificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean("tracker_notifications", isChecked).apply()
        }

        binding.notificationSoundSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean("notification_sound", isChecked).apply()
        }

        binding.vpnStatusNotificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean("vpn_status_notification", isChecked).apply()
        }

        // Обновления
        binding.autoUpdateTrackersSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean("auto_update_trackers", isChecked).apply()
            if (isChecked) {
                showUpdateFrequencyDialog()
            }
        }

        // Безопасность
        binding.blockAllAdsSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean("block_all_ads", isChecked).apply()
        }

        binding.blockAnalyticsSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean("block_analytics", isChecked).apply()
        }

        binding.blockSocialSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean("block_social", isChecked).apply()
        }

        // Информация и действия
        binding.privacyPolicyButton.setOnClickListener {
            showPrivacyPolicy()
        }

        binding.clearLogsButton.setOnClickListener {
            showClearLogsConfirmation()
        }

        binding.exportLogsButton.setOnClickListener {
            showSnackbar("Экспорт логов (в разработке)")
        }

        binding.aboutAppButton.setOnClickListener {
            showAboutDialog()
        }

        // Кнопка сброса настроек
        binding.resetSettingsButton.setOnClickListener {
            showResetConfirmation()
        }
    }

    private fun loadSettings() {
        // VPN настройки
        binding.autoStartVpnSwitch.isChecked = preferences.getBoolean("auto_start_vpn", false)
        binding.allowBypassSwitch.isChecked = preferences.getBoolean("allow_bypass", false)

        // Уведомления
        binding.trackerNotificationsSwitch.isChecked = preferences.getBoolean("tracker_notifications", true)
        binding.notificationSoundSwitch.isChecked = preferences.getBoolean("notification_sound", false)
        binding.vpnStatusNotificationSwitch.isChecked = preferences.getBoolean("vpn_status_notification", true)

        // Обновления
        binding.autoUpdateTrackersSwitch.isChecked = preferences.getBoolean("auto_update_trackers", true)

        // Безопасность
        binding.blockAllAdsSwitch.isChecked = preferences.getBoolean("block_all_ads", true)
        binding.blockAnalyticsSwitch.isChecked = preferences.getBoolean("block_analytics", true)
        binding.blockSocialSwitch.isChecked = preferences.getBoolean("block_social", true)

        // Информация
        loadAppInfo()
    }

    private fun loadAppInfo() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            binding.appVersionText.text = "Версия ${packageInfo.versionName}"
        } catch (e: Exception) {
            binding.appVersionText.text = "Версия 1.0.0 alpha lelele"
        }

        // Загружаем статистику в корутине
        loadStats()
    }

    private fun loadStats() {
        lifecycleScope.launch {
            try {
                val trackerApp = application as TrackerApp
                val blockedCount = trackerApp.trackerRepository.getBlockedCount()
                val trackersCount = trackerApp.trackerRepository.getAllTrackers().size

                binding.trackersInDbText.text = "$trackersCount трекеров"
                binding.blockedTotalText.text = "$blockedCount заблокировано"
            } catch (e: Exception) {
                binding.trackersInDbText.text = "10+ трекеров"
                binding.blockedTotalText.text = "0 заблокировано"
            }
        }
    }

    private fun showUpdateFrequencyDialog() {
        val frequencies = arrayOf("Каждый час", "Каждые 6 часов", "Ежедневно", "Еженедельно", "Вручную")
        val savedFrequency = preferences.getString("update_frequency", "daily") ?: "daily"

        var selectedIndex = when (savedFrequency) {
            "hourly" -> 0
            "six_hours" -> 1
            "daily" -> 2
            "weekly" -> 3
            "manual" -> 4
            else -> 2
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Частота обновлений")
            .setSingleChoiceItems(frequencies, selectedIndex) { dialog, which ->
                val frequencyValue = when (which) {
                    0 -> "hourly"
                    1 -> "six_hours"
                    2 -> "daily"
                    3 -> "weekly"
                    4 -> "manual"
                    else -> "daily"
                }
                preferences.edit().putString("update_frequency", frequencyValue).apply()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showPrivacyPolicy() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Политика конфиденциальности")
            .setMessage(
                """
                Сбор данных:
                • Приложение НЕ собирает пароли пользователей
                • Приложение НЕ собирает личные данные
                • Все проверки паролей выполняются локально
                все
                github:
                https://github.com/hxx0r
                """
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showClearLogsConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Очистка логов")
            .setMessage("Вы уверены, что хотите очистить все логи трекеров? Это действие нельзя отменить.")
            .setPositiveButton("Очистить") { _, _ ->
                clearLogs()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun clearLogs() {
        lifecycleScope.launch {
            try {
                val trackerApp = application as TrackerApp
                trackerApp.trackerRepository.clearEvents()
                loadStats()
                showSnackbar("Логи очищены")
            } catch (e: Exception) {
                showSnackbar("Ошибка при очистке логов")
            }
        }
    }

    private fun showResetConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Сброс настроек")
            .setMessage("Вы уверены, что хотите сбросить все настройки к значениям по умолчанию?")
            .setPositiveButton("Сбросить") { _, _ ->
                resetSettings()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun resetSettings() {
        preferences.edit().clear().apply()
        loadSettings()
        showSnackbar("Настройки сброшены")
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("О приложении")
            .setMessage(
                """
                ученицы 10б класса
                Заславской Серафимы
                github
                https://github.com/hxx0r
                """
            )
            .setPositiveButton("а ок", null)
            .show()
    }

    private fun showSnackbar(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}