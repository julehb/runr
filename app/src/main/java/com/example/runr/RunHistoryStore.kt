package com.example.runr

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RunHistoryEntry(
    val id: Long,
    val finishedAtMillis: Long,
    val durationMillis: Long,
    val distanceMeters: Float,
)

class RunHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun addRun(durationMillis: Long, distanceMeters: Float): RunHistoryEntry {
        val entry = RunHistoryEntry(
            id = System.currentTimeMillis(),
            finishedAtMillis = System.currentTimeMillis(),
            durationMillis = durationMillis,
            distanceMeters = distanceMeters,
        )
        val entries = listOf(entry) + getRuns()
        preferences.edit()
            .putString(KEY_RUNS, entries.toJsonArray().toString())
            .apply()
        return entry
    }

    fun getRuns(): List<RunHistoryEntry> {
        val rawRuns = preferences.getString(KEY_RUNS, null) ?: return emptyList()
        val runs = JSONArray(rawRuns)
        return (0 until runs.length()).mapNotNull { index ->
            runs.optJSONObject(index)?.toRunHistoryEntry()
        }
    }

    private fun List<RunHistoryEntry>.toJsonArray(): JSONArray {
        val runs = JSONArray()
        forEach { entry ->
            runs.put(
                JSONObject()
                    .put(FIELD_ID, entry.id)
                    .put(FIELD_FINISHED_AT_MILLIS, entry.finishedAtMillis)
                    .put(FIELD_DURATION_MILLIS, entry.durationMillis)
                    .put(FIELD_DISTANCE_METERS, entry.distanceMeters.toDouble()),
            )
        }
        return runs
    }

    private fun JSONObject.toRunHistoryEntry(): RunHistoryEntry? {
        if (!has(FIELD_ID) || !has(FIELD_FINISHED_AT_MILLIS) || !has(FIELD_DURATION_MILLIS)) {
            return null
        }

        return RunHistoryEntry(
            id = optLong(FIELD_ID),
            finishedAtMillis = optLong(FIELD_FINISHED_AT_MILLIS),
            durationMillis = optLong(FIELD_DURATION_MILLIS),
            distanceMeters = optDouble(FIELD_DISTANCE_METERS, 0.0).toFloat(),
        )
    }

    private companion object {
        private const val PREFERENCES_NAME = "run_history"
        private const val KEY_RUNS = "runs"
        private const val FIELD_ID = "id"
        private const val FIELD_FINISHED_AT_MILLIS = "finishedAtMillis"
        private const val FIELD_DURATION_MILLIS = "durationMillis"
        private const val FIELD_DISTANCE_METERS = "distanceMeters"
    }
}
