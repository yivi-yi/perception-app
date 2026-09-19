package com.yivi.perception.ui.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yivi.perception.alarm.AlarmScheduler
import com.yivi.perception.data.db.EventEntity
import com.yivi.perception.data.repo.PerceptionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CalendarViewModel(
    private val repo: PerceptionRepository,
    private val context: Context
) : ViewModel() {

    val events: StateFlow<List<EventEntity>> = repo.observeByCategory("行程")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 开了提醒的日程，写完立刻交给系统排班 */
    fun add(event: EventEntity) {
        viewModelScope.launch {
            val id = repo.add(event)
            if (event.remind) AlarmScheduler.schedule(context, event.copy(id = id))
        }
    }

    fun delete(event: EventEntity) {
        viewModelScope.launch {
            AlarmScheduler.cancel(context, event)
            repo.deleteById(event.id)
        }
    }

    class Factory(
        private val repo: PerceptionRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CalendarViewModel(repo, context) as T
    }
}
