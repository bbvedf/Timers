// ✅ FULL FILE VERSION
// Path: C:/local/Android/Timers/app/src/main/java/com/pneumasoft/multitimer/MainActivity.kt

package com.pneumasoft.multitimer

// 🔄 CORRECCIÓN: Import exacto de la nueva ubicación
import com.pneumasoft.multitimer.ui.adapter.TimerAdapter
import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pneumasoft.multitimer.databinding.ActivityMainBinding
import com.pneumasoft.multitimer.services.TimerService
import com.pneumasoft.multitimer.viewmodel.TimerViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val isRunningTest: Boolean by lazy {
        try {
            Class.forName("androidx.test.espresso.Espresso")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }
    private lateinit var binding: ActivityMainBinding
    private val viewModel: TimerViewModel by viewModels()

    // ✅ Ahora el compilador reconocerá TimerAdapter y los tipos (id)
    private val adapter = TimerAdapter(
        onStartPauseClick = { id -> handleStartPause(id) },
        onResetClick = { id -> viewModel.resetTimer(id) },
        onEditClick = { id -> showEditTimerDialog(id) },
        onDeleteClick = { id -> viewModel.deleteTimer(id) }
    )

    private var timerService: TimerService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TimerService.LocalBinder
            timerService = binder.getService()
            isServiceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isServiceBound = false
        }
    }

    private fun setupRecyclerView() {
        binding.timerRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            if (itemDecorationCount > 0) removeItemDecorationAt(0)
        }
    }

    private fun setupAddButton() {
        binding.addTimerButton.setOnClickListener { showAddTimerDialog() }
    }

    private fun observeTimers() {
        lifecycleScope.launch {
            viewModel.timers.collect { timers -> adapter.updateTimers(timers) }
        }
    }

    private fun bindTimerService() {
        val serviceIntent = Intent(this, TimerService::class.java)
        if (Build.VERSION_CODES.O <= Build.VERSION.SDK_INT) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun getManufacturerSpecificInstructions(): String? {
        return when (Build.MANUFACTURER.lowercase()) {
            "xiaomi", "redmi", "poco" -> "Go to Settings > Apps > Manage Apps > MultiTimer > Battery > No restrictions"
            "huawei", "honor" -> "Go to Settings > Apps > MultiTimer > Battery > App launch"
            "samsung" -> "Go to Settings > Apps > MultiTimer > Battery > Allow background activity"
            "oppo", "oneplus", "realme" -> "Go to Settings > Battery > Background apps > MultiTimer"
            else -> null
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshTimers()
    }

    private fun requestBatteryOptimizationExemption() {
        if (isRunningTest) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val specificInstructions = getManufacturerSpecificInstructions()
                val message = "For timers to work properly when the screen is off, please disable battery optimization.\n" +
                        (specificInstructions?.let { "\nOn your device: $it" } ?: "")

                AlertDialog.Builder(this)
                    .setTitle("Battery Optimization")
                    .setMessage(message)
                    .setPositiveButton("Settings") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this, "Find MultiTimer in settings and disable optimization", Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton("Later", null)
                    .show()
            }
        }
    }

    private fun checkAndRequestFullScreenIntentPermission() {
        if (isRunningTest) return
        if (Build.VERSION.SDK_INT >= 34) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.canUseFullScreenIntent()) {
                AlertDialog.Builder(this)
                    .setTitle("Full Screen Permission")
                    .setMessage("Allow Full Screen Intent so the alarm can pop up while the screen is locked.")
                    .setPositiveButton("Settings") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Failed to open settings", e)
                        }
                    }
                    .setNegativeButton("Later", null)
                    .show()
            }
        }
    }

    private fun checkAndRequestOverlayPermission() {
        if (isRunningTest) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Display Over Other Apps")
                .setMessage("Required to show the alarm dialog while using other apps.")
                .setPositiveButton("Settings") { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to open overlay settings", e)
                    }
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private fun handleStartPause(id: String) {
        val timer = viewModel.timers.value.find { it.id == id } ?: return
        if (timer.isRunning) viewModel.pauseTimer(id) else viewModel.startTimer(id)
    }

    private fun showAddTimerDialog() {
        setupTimerDialog(null)
    }

    private fun showEditTimerDialog(id: String) {
        val timer = viewModel.timers.value.find { it.id == id } ?: return
        setupTimerDialog(timer)
    }

    private fun setupTimerDialog(existingTimer: com.pneumasoft.multitimer.model.TimerItem?) {
        try {
            val dialogView = layoutInflater.inflate(R.layout.dialog_add_timer, null)
            val nameEdit = dialogView.findViewById<EditText>(R.id.timer_name_edit)
            val displayPreview = dialogView.findViewById<TextView>(R.id.timer_display_text)
            val hEdit = dialogView.findViewById<EditText>(R.id.hours_value)
            val mEdit = dialogView.findViewById<EditText>(R.id.minutes_value)
            val sEdit = dialogView.findViewById<EditText>(R.id.seconds_value)
            val hUp = dialogView.findViewById<ImageButton>(R.id.hours_up_button)
            val hDown = dialogView.findViewById<ImageButton>(R.id.hours_down_button)
            val mUp = dialogView.findViewById<ImageButton>(R.id.minutes_up_button)
            val mDown = dialogView.findViewById<ImageButton>(R.id.minutes_down_button)
            val sUp = dialogView.findViewById<ImageButton>(R.id.seconds_up_button)
            val sDown = dialogView.findViewById<ImageButton>(R.id.seconds_down_button)

            val isEditing = existingTimer != null
            val isRunning = existingTimer?.isRunning ?: false
            val initialSecondsTotal = if (isEditing) {
                if (isRunning) existingTimer!!.remainingSeconds else existingTimer!!.durationSeconds
            } else 0

            var h = initialSecondsTotal / 3600
            var m = (initialSecondsTotal % 3600) / 60
            var s = initialSecondsTotal % 60
            var isUpdating = false

            if (isEditing) nameEdit.setText(existingTimer!!.name)

            fun updateUIFromVars(source: View? = null) {
                if (isUpdating) return
                isUpdating = true
                try {
                    val fmtH = String.format("%02d", h)
                    val fmtM = String.format("%02d", m)
                    val fmtS = String.format("%02d", s)
                    displayPreview.text = "${fmtH}h ${fmtM}m ${fmtS}s"
                    if (source != hEdit) hEdit.setText(fmtH)
                    if (source != mEdit) mEdit.setText(fmtM)
                    if (source != sEdit) sEdit.setText(fmtS)
                } finally {
                    isUpdating = false
                }
            }

            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(editable: Editable?) {
                    if (isUpdating) return
                    val valInt = editable.toString().toIntOrNull() ?: 0
                    when {
                        hEdit.hasFocus() -> h = valInt.coerceIn(0, 99)
                        mEdit.hasFocus() -> m = valInt.coerceIn(0, 59)
                        sEdit.hasFocus() -> s = valInt.coerceIn(0, 59)
                    }
                    updateUIFromVars(if (hEdit.hasFocus()) hEdit else if (mEdit.hasFocus()) mEdit else sEdit)
                }
            }

            hEdit.addTextChangedListener(textWatcher)
            mEdit.addTextChangedListener(textWatcher)
            sEdit.addTextChangedListener(textWatcher)
            hUp.setOnClickListener { h = (h + 1) % 100; updateUIFromVars() }
            hDown.setOnClickListener { h = if (h > 0) h - 1 else 99; updateUIFromVars() }
            mUp.setOnClickListener { m = (m + 1) % 60; updateUIFromVars() }
            mDown.setOnClickListener { m = if (m > 0) m - 1 else 59; updateUIFromVars() }
            sUp.setOnClickListener { s = (s + 1) % 60; updateUIFromVars() }
            sDown.setOnClickListener { s = if (s > 0) s - 1 else 59; updateUIFromVars() }

            updateUIFromVars()

            AlertDialog.Builder(this)
                .setTitle(if (isEditing) "Edit Timer" else "Add New Timer")
                .setView(dialogView)
                .setPositiveButton(if (isEditing) "Update" else "Add") { _, _ ->
                    val name = nameEdit.text.toString()
                    val total = h * 3600 + m * 60 + s
                    if (total > 0) {
                        if (isEditing) {
                            viewModel.updateTimer(existingTimer!!.id, name.ifBlank { existingTimer.name }, total, isRunning)
                        } else {
                            viewModel.addTimer(name.ifBlank { "Timer ${viewModel.timers.value.size + 1}" }, total)
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e("TimerDialog", "Error: ${e.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ Pedir permiso de notificaciones (Limpiado y sin duplicados)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }

        requestBatteryOptimizationExemption()
        checkAndRequestFullScreenIntentPermission()
        checkAndRequestOverlayPermission()
        setupRecyclerView()
        setupAddButton()
        observeTimers()
    }

    override fun onStart() {
        super.onStart()
        bindTimerService()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> { startActivity(Intent(this, AboutActivity::class.java)); true }
            R.id.action_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 100
    }
}