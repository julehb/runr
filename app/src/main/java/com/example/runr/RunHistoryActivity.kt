package com.example.runr

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class RunHistoryActivity : AppCompatActivity() {
    private lateinit var runHistoryStore: RunHistoryStore
    private lateinit var historyRecyclerView: RecyclerView
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
        historyRecyclerView = findViewById(R.id.historyRecyclerView)
        emptyHistoryText = findViewById(R.id.emptyHistoryText)

        renderHistory(runHistoryStore.getRuns())
    }

    private fun renderHistory(entries: List<RunHistoryEntry>) {
        emptyHistoryText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        historyRecyclerView.adapter = RunHistoryAdapter(entries)
    }
}
