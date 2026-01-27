package com.quark.leaks.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.net.VpnService
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.quark.leaks.R
import com.quark.leaks.TrackerApp
import com.quark.leaks.databinding.ActivityMainBinding
import com.quark.leaks.vpn.TrackerBlockingVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var trackerApp: TrackerApp

    // разрешение на впн
    private val vpnPermissionRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        } else {
            showSnackbar("Разрешение на VPN отклонено")
            binding.vpnToggleButton.isChecked = false
        }
    }

   private val trackerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "TRACKER_DETECTED") {
                val domain = intent.getStringExtra("domain")
                val ip = intent.getStringExtra("ip")

                lifecycleScope.launch {
                    val blockedCount = trackerApp.trackerRepository.getBlockedCount()
                    updateBlockedStats(blockedCount)

                    domain?.let {
                        showTrackerNotification(it, ip)
                    }
                }
            }
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        trackerApp = application as TrackerApp

        setupUI()
        setupListeners()
        loadStats()
        val filter = IntentFilter().apply {
            addAction("TRACKER_DETECTED")
            addAction("VPN_STATUS_CHANGED")
        }
        registerReceiver(trackerReceiver, filter, RECEIVER_NOT_EXPORTED)

        checkVpnStatus()
    }



    private fun setupUI() {
        window.statusBarColor = getColor(R.color.background)
        window.navigationBarColor = getColor(R.color.background)

        //спрятать пароль
        binding.passwordResultCard.visibility = View.GONE

        // показать трекеры
        updateBlockedStats(0)
    }

    private fun setupListeners() {
        // впн ползунок
        binding.vpnToggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startVpn()
            } else {
                stopVpn()
            }
        }
        

        // проверка слодности пароля
        binding.passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updatePasswordStrength(s.toString())
            }
        })

        // кнопка проверить парол
        binding.checkButton.setOnClickListener {
            val password = binding.passwordEditText.text.toString()
            if (password.isBlank()) {
                showSnackbar("Введите пароль")
                return@setOnClickListener
            }
            checkPassword(password)
        }

        // посмотреть логи
        binding.trackerLogButton.setOnClickListener {
            startActivity(Intent(this, TrackerLogActivity::class.java))
        }

        // кнопка настроек
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // кнопка о
        binding.aboutButton.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun checkVpnStatus() {

        binding.vpnToggleButton.isChecked = false
        binding.vpnStatusText.text = "VPN выключен"
        binding.vpnStatusText.setTextColor(getColor(R.color.text_secondary))
    }

    private fun startVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionRequest.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, TrackerBlockingVpnService::class.java).apply {
            action = TrackerBlockingVpnService.ACTION_START
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        binding.vpnToggleButton.isChecked = true
        binding.vpnStatusText.text = "VPN активен"
        binding.vpnStatusText.setTextColor(getColor(R.color.safe))

        showSnackbar("Блокировщик трекеров запущен")
    }

    private fun stopVpn() {
        val intent = Intent(this, TrackerBlockingVpnService::class.java).apply {
            action = TrackerBlockingVpnService.ACTION_STOP
        }
        startService(intent)

        binding.vpnToggleButton.isChecked = false
        binding.vpnStatusText.text = "VPN выключен"
        binding.vpnStatusText.setTextColor(getColor(R.color.text_secondary))

        showSnackbar("Блокировщик трекеров остановлен")
    }

    private fun updatePasswordStrength(password: String) {
        var score = 0

        if (password.length >= 8) score += 25
        if (password.length >= 12) score += 15
        if (password.matches(Regex(".*[A-Z].*"))) score += 20
        if (password.matches(Regex(".*[a-z].*"))) score += 20
        if (password.matches(Regex(".*[0-9].*"))) score += 20
        if (password.matches(Regex(".*[^A-Za-z0-9].*"))) score += 20

        score = score.coerceAtMost(100)

        binding.strengthProgress.progress = score

        val weakColor = ContextCompat.getColor(this, R.color.weak)
        val mediumColor = ContextCompat.getColor(this, R.color.medium)
        val strongColor = ContextCompat.getColor(this, R.color.strong)

        when {
            score < 40 -> {
                binding.strengthProgress.progressTintList = ColorStateList.valueOf(weakColor)
                binding.strengthLabel.text = "Сложность: Слабый"
                binding.strengthLabel.setTextColor(weakColor)
            }
            score < 70 -> {
                binding.strengthProgress.progressTintList = ColorStateList.valueOf(mediumColor)
                binding.strengthLabel.text = "Сложность: Средний"
                binding.strengthLabel.setTextColor(mediumColor)
            }
            else -> {
                binding.strengthProgress.progressTintList = ColorStateList.valueOf(strongColor)
                binding.strengthLabel.text = "Сложность: Сильный"
                binding.strengthLabel.setTextColor(strongColor)
            }
        }
    }

    private fun checkPassword(password: String) {
        binding.passwordResultCard.visibility = View.VISIBLE
        binding.passwordLoadingIndicator.visibility = View.VISIBLE
        binding.passwordResultIcon.visibility = View.GONE
        binding.passwordResultTitle.text = "Проверка..."
        binding.passwordResultText.text = "Ищем пароль в базах утечек..."

        binding.hashText.text = "SHA-1 хеш: вычисляется..."
        binding.prefixText.text = "Префикс запроса: вычисляется..."

        lifecycleScope.launch {
            try {
                // Cрассчет SHA-1
                val hash = calculateSHA1(password)
                val hashUpper = hash.uppercase(Locale.US)

                withContext(Dispatchers.Main) {
                    binding.hashText.text = "SHA-1 хеш: $hashUpper"
                    binding.prefixText.text = "Префикс запроса: ${hashUpper.substring(0, 5)}"
                }

                // запрос к API
                val result = queryPwnedAPI(hashUpper)

                withContext(Dispatchers.Main) {
                    showPasswordResult(result)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showPasswordError("Ошибка: ${e.message ?: "Неизвестная ошибка"}")
                }
            }
        }
    }

    private suspend fun queryPwnedAPI(fullHash: String): PwnedResult {
        val prefix = fullHash.substring(0, 5)
        val suffix = fullHash.substring(5)

        val url = URL("https://api.pwnedpasswords.com/range/$prefix")
        val connection = url.openConnection() as HttpURLConnection

        return withContext(Dispatchers.IO) {
            try {
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "LeaksShield/1.0")
                connection.setRequestProperty("Accept", "application/vnd.haveibeenpwned.v2+json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("Ошибка API: ${connection.responseCode}")
                }

                val response = connection.inputStream.bufferedReader().readText()
                val lines = response.lines()

                var found = false
                var count = 0

                for (line in lines) {
                    val parts = line.split(":")
                    if (parts.size == 2 && parts[0].trim().equals(suffix, ignoreCase = true)) {
                        found = true
                        count = parts[1].trim().toIntOrNull() ?: 0
                        break
                    }
                }

                PwnedResult(found, count, fullHash, prefix)

            } finally {
                connection.disconnect()
            }
        }
    }

    private fun showPasswordResult(result: PwnedResult) {
        binding.passwordLoadingIndicator.visibility = View.GONE
        binding.passwordResultIcon.visibility = View.VISIBLE

        val safeColor = ContextCompat.getColor(this, R.color.safe)
        val dangerColor = ContextCompat.getColor(this, R.color.danger)

        if (result.found) {
            binding.passwordResultIcon.setImageResource(R.drawable.ic_danger)
            binding.passwordResultIcon.setColorFilter(dangerColor)
            binding.passwordResultTitle.text = "Пароль скомпрометирован"
            binding.passwordResultText.text =
                "Этот пароль обнаружен в ${result.count} утечках данных.\n\n" +
                        "Рекомендуется немедленно заменить его на более надежный."

            binding.passwordResultCard.strokeColor = dangerColor
            binding.passwordResultCard.strokeWidth = 2
        } else {
            binding.passwordResultIcon.setImageResource(R.drawable.ic_safe)
            binding.passwordResultIcon.setColorFilter(safeColor)
            binding.passwordResultTitle.text = "Пароль безопасен"
            binding.passwordResultText.text =
                "Этот пароль не найден в известных утечках.\n\n" +
                        "круто"

            binding.passwordResultCard.strokeColor = safeColor
            binding.passwordResultCard.strokeWidth = 2
        }
    }

    private fun showPasswordError(message: String) {
        binding.passwordLoadingIndicator.visibility = View.GONE
        binding.passwordResultIcon.visibility = View.VISIBLE
        binding.passwordResultIcon.setImageResource(R.drawable.ic_error)
        binding.passwordResultIcon.setColorFilter(ContextCompat.getColor(this, R.color.warning))
        binding.passwordResultTitle.text = "Ошибка"
        binding.passwordResultText.text = message

        binding.passwordResultCard.strokeColor = ContextCompat.getColor(this, R.color.warning)
        binding.passwordResultCard.strokeWidth = 2
    }

    private fun calculateSHA1(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(input.toByteArray())
        return String.format("%040x", BigInteger(1, digest))
    }

    private fun loadStats() {
        lifecycleScope.launch {
            try {
                val blockedCount = trackerApp.trackerRepository.getBlockedCount()
                updateBlockedStats(blockedCount)

                val trackersCount = trackerApp.trackerRepository.getAllTrackers().size
                binding.trackersInDbText.text = "$trackersCount+"

            } catch (e: Exception) {
                updateBlockedStats(0)
                binding.trackersInDbText.text = "10+"
            }
        }
    }

    private fun updateBlockedStats(blockedCount: Int) {
        binding.blockedTrackersCount.text = blockedCount.toString()
        binding.blockedTrackersCard.visibility = View.VISIBLE
    }

    private fun showTrackerNotification(domain: String, ip: String?) {
        val message = if (ip != null) {
            "Заблокирован трекер: $domain ($ip)"
        } else {
            "Заблокирован трекер: $domain"
        }

        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Подробнее") {
                startActivity(Intent(this, TrackerLogActivity::class.java))
            }
            .show()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("leaks")
            .setMessage(
                """
                гитхаб https://github.com/hxx0r
                """.trimIndent()
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        loadStats()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(trackerReceiver)
        } catch (e: Exception) {
        }
    }

    data class PwnedResult(
        val found: Boolean,
        val count: Int,
        val fullHash: String,
        val prefix: String
    )
}