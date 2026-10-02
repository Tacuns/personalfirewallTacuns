package com.sentinel.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sentinel.core.logs.ChangeHistory
import com.sentinel.core.logs.ChangeHistoryEntity
import com.sentinel.core.logs.LogDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Reads the recorded changes for the Change history screen. Read-only — it never edits rules. */
class ChangeHistoryViewModel(app: Application) : AndroidViewModel(app) {

    val changes: StateFlow<List<ChangeHistoryEntity>> =
        LogDatabase.getInstance(app).changeHistoryDao()
            .recentFlow(ChangeHistory.MAX_ROWS)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clear() {
        viewModelScope.launch { ChangeHistory.clear(getApplication()) }
    }
}
