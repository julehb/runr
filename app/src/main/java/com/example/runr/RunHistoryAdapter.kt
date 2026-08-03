package com.example.runr

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RunHistoryAdapter(
    private val entries: List<RunHistoryEntry>,
) : RecyclerView.Adapter<RunHistoryAdapter.RunHistoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RunHistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_run_history, parent, false)
        return RunHistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: RunHistoryViewHolder, position: Int) {
        holder.bind(entries[position])
    }

    override fun getItemCount(): Int = entries.size

    class RunHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.historyRunDateText)
        private val distanceText: TextView = itemView.findViewById(R.id.historyRunDistanceText)
        private val durationText: TextView = itemView.findViewById(R.id.historyRunDurationText)
        private val averagePaceText: TextView = itemView.findViewById(R.id.historyRunAveragePaceText)

        fun bind(entry: RunHistoryEntry) {
            dateText.text = formatDate(entry.finishedAtMillis)
            distanceText.text = formatDistance(entry.distanceMeters)
            durationText.text = itemView.context.getString(
                R.string.history_run_duration,
                formatDuration(entry.durationMillis),
            )
            averagePaceText.text = itemView.context.getString(
                R.string.history_run_average_pace,
                formatAveragePace(entry.durationMillis, entry.distanceMeters),
            )
        }

        private fun formatDate(timestampMillis: Long): String {
            return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestampMillis))
        }

        private fun formatDistance(distanceMeters: Float): String {
            return if (distanceMeters < METERS_PER_KILOMETER) {
                itemView.context.getString(R.string.distance_meters, distanceMeters.toInt())
            } else {
                itemView.context.getString(R.string.distance_kilometers, distanceMeters / METERS_PER_KILOMETER)
            }
        }

        private fun formatDuration(durationMillis: Long): String {
            val totalSeconds = durationMillis / MILLIS_PER_SECOND
            val hours = totalSeconds / SECONDS_PER_HOUR
            val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
            val seconds = totalSeconds % SECONDS_PER_MINUTE

            return if (hours > 0) {
                itemView.context.getString(R.string.history_duration_hours, hours, minutes, seconds)
            } else {
                itemView.context.getString(R.string.history_duration_minutes, minutes, seconds)
            }
        }

        private fun formatAveragePace(durationMillis: Long, distanceMeters: Float): String {
            if (distanceMeters <= 0f) return itemView.context.getString(R.string.current_pace_placeholder)

            val elapsedSeconds = (durationMillis / MILLIS_PER_SECOND).coerceAtLeast(1L)
            val paceSecondsPerKilometer = (
                elapsedSeconds / (distanceMeters / METERS_PER_KILOMETER)
                ).toInt()
            val paceMinutes = paceSecondsPerKilometer / SECONDS_PER_MINUTE
            val paceSeconds = paceSecondsPerKilometer % SECONDS_PER_MINUTE
            return itemView.context.getString(R.string.pace_minutes_per_kilometer, paceMinutes, paceSeconds)
        }
    }

    private companion object {
        private const val METERS_PER_KILOMETER = 1_000f
        private const val MILLIS_PER_SECOND = 1_000L
        private const val SECONDS_PER_MINUTE = 60
        private const val SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE
    }
}
