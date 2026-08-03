package com.example.runr

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RunHistoryActivity : AppCompatActivity() {
    private lateinit var runHistoryStore: RunHistoryStore
    private lateinit var historyListContainer: LinearLayout
    private lateinit var emptyHistoryText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_run_history)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.runHistoryRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        runHistoryStore = RunHistoryStore(this)
        historyListContainer = findViewById(R.id.historyListContainer)
        emptyHistoryText = findViewById(R.id.emptyHistoryText)

        renderHistory(runHistoryStore.getRuns())
    }

    private fun renderHistory(entries: List<RunHistoryEntry>) {
        historyListContainer.removeAllViews()
        emptyHistoryText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE

        entries.forEach { entry ->
            historyListContainer.addView(createHistoryItem(entry))
        }
    }

    private fun createHistoryItem(entry: RunHistoryEntry): View {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@RunHistoryActivity, R.drawable.run_stat_tile_background)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(12)
            }
        }

        item.addView(
            TextView(this).apply {
                text = formatDate(entry.finishedAtMillis)
                setTextColor(ContextCompat.getColor(this@RunHistoryActivity, R.color.run_screen_secondary_text))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            },
        )

        item.addView(
            TextView(this).apply {
                text = formatDistance(entry.distanceMeters)
                setTextColor(ContextCompat.getColor(this@RunHistoryActivity, R.color.run_screen_primary_text))
                textSize = 28f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(8)
                }
            },
        )

        item.addView(
            TextView(this).apply {
                text = getString(
                    R.string.history_run_details,
                    formatDuration(entry.durationMillis),
                    formatAveragePace(entry.durationMillis, entry.distanceMeters),
                )
                setTextColor(ContextCompat.getColor(this@RunHistoryActivity, R.color.run_screen_primary_text))
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(6)
                }
            },
        )

        return item
    }

    private fun formatDate(timestampMillis: Long): String {
        return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestampMillis))
    }

    private fun formatDistance(distanceMeters: Float): String {
        return if (distanceMeters < METERS_PER_KILOMETER) {
            getString(R.string.distance_meters, distanceMeters.toInt())
        } else {
            getString(R.string.distance_kilometers, distanceMeters / METERS_PER_KILOMETER)
        }
    }

    private fun formatDuration(durationMillis: Long): String {
        val totalSeconds = durationMillis / MILLIS_PER_SECOND
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE

        return if (hours > 0) {
            getString(R.string.history_duration_hours, hours, minutes, seconds)
        } else {
            getString(R.string.history_duration_minutes, minutes, seconds)
        }
    }

    private fun formatAveragePace(durationMillis: Long, distanceMeters: Float): String {
        if (distanceMeters <= 0f) return getString(R.string.current_pace_placeholder)

        val elapsedSeconds = (durationMillis / MILLIS_PER_SECOND).coerceAtLeast(1L)
        val paceSecondsPerKilometer = (
            elapsedSeconds / (distanceMeters / METERS_PER_KILOMETER)
            ).toInt()
        val paceMinutes = paceSecondsPerKilometer / SECONDS_PER_MINUTE
        val paceSeconds = paceSecondsPerKilometer % SECONDS_PER_MINUTE
        return getString(R.string.pace_minutes_per_kilometer, paceMinutes, paceSeconds)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private companion object {
        private const val METERS_PER_KILOMETER = 1_000f
        private const val MILLIS_PER_SECOND = 1_000L
        private const val SECONDS_PER_MINUTE = 60
        private const val SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE
    }
}
