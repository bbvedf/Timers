// ✅ FULL FILE VERSION
// Path: C:/local/Android/Timers/app/src/main/java/com/pneumasoft/multitimer/ui/adapter/TimerAdapter.kt

package com.pneumasoft.multitimer.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pneumasoft.multitimer.R
import com.pneumasoft.multitimer.databinding.ItemTimerBinding
import com.pneumasoft.multitimer.model.TimerItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TimerAdapter(
    private var timers: List<TimerItem> = emptyList(),
    private val onStartPauseClick: (String) -> Unit,
    private val onResetClick: (String) -> Unit,
    private val onEditClick: (String) -> Unit,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<TimerAdapter.TimerViewHolder>() {
    class TimerViewHolder(val binding: ItemTimerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimerViewHolder {
        val binding = ItemTimerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TimerViewHolder(binding)
    }

    override fun getItemCount() = timers.size

    override fun onBindViewHolder(holder: TimerViewHolder, position: Int) {
        val timer = timers[position]
        holder.binding.apply {
            timerName.text = timer.name
            timerDisplay.text = formatTime(timer.remainingSeconds)

            val secondsValue = timer.remainingSeconds % 60
            secondsProgress.progress = secondsValue

            // 🔄 MODIFICACIÓN: Lógica de botón Dinámico (Play / Pause / STOP)
            val isRunning = timer.isRunning
            val isFinished = timer.remainingSeconds <= 0 && isRunning

            when {
                isFinished -> {
                    // 🚨 ESTADO: SONANDO (Alarma activa)
                    startPauseButton.setImageResource(R.drawable.ic_stop) // Asegúrate de tener ic_stop
                    startPauseButton.setColorFilter(Color.RED) // Ponlo rojo para que destaque
                    startPauseButton.contentDescription = "Stop Alarm"
                    timerDisplay.setTextColor(Color.RED) // Que el 00:00 parpadee o esté en rojo
                }
                isRunning -> {
                    // 🟢 ESTADO: CORRIENDO
                    startPauseButton.setImageResource(R.drawable.ic_pause)
                    startPauseButton.setColorFilter(null)
                    startPauseButton.contentDescription = "Pause"
                    timerDisplay.setTextColor(Color.BLACK) // O el color por defecto de tu tema
                }
                else -> {
                    // ⚪ ESTADO: PARADO / EDITANDO
                    startPauseButton.setImageResource(R.drawable.ic_play)
                    startPauseButton.setColorFilter(null)
                    startPauseButton.contentDescription = "Play"
                    timerDisplay.setTextColor(Color.BLACK)
                }
            }

            // Calculate and display expiration time
            if (isRunning && !isFinished) {
                val expirationTimeText = calculateExpirationTime(timer.remainingSeconds)
                timerExpirationTime.text = expirationTimeText
                timerExpirationTime.visibility = View.VISIBLE
            } else {
                timerExpirationTime.visibility = View.GONE
            }

            // Set click listeners
            startPauseButton.setOnClickListener { onStartPauseClick(timer.id) }
            resetButton.setOnClickListener { onResetClick(timer.id) }
            editButton.setOnClickListener { onEditClick(timer.id) }
            deleteButton.setOnClickListener { onDeleteClick(timer.id) }
        }
    }

    private fun calculateExpirationTime(remainingSeconds: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.SECOND, remainingSeconds)
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(calendar.time)
    }

    private fun formatTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val sec = seconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, sec)
        } else {
            "%d:%02d".format(minutes, sec)
        }
    }

    fun updateTimers(newTimers: List<TimerItem>) {
        val oldTimers = timers
        this.timers = newTimers
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldTimers.size
            override fun getNewListSize() = newTimers.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldTimers[oldItemPosition].id == newTimers[newItemPosition].id
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = oldTimers[oldItemPosition]
                val new = newTimers[newItemPosition]
                return old.name == new.name &&
                        old.remainingSeconds == new.remainingSeconds &&
                        old.isRunning == new.isRunning
            }
        })
        diffResult.dispatchUpdatesTo(this)
    }
}