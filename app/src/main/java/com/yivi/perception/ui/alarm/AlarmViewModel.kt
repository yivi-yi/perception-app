package com.yivi.perception.ui.alarm

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

class AlarmViewModel(
    private val repo: PerceptionRepository,
    private val context: Context
) : ViewModel() {

    val alarms: StateFlow<List<EventEntity>> = repo.observeByCategory("闹钟")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 新增：写库之后立刻交给系统排班 */
    fun add(alarm: EventEntity) {
        viewModelScope.launch {
            val id = repo.add(alarm)
            AlarmScheduler.schedule(context, alarm.copy(id = id))
        }
    }

    fun delete(alarm: EventEntity) {
        viewModelScope.launch {
            AlarmScheduler.cancel(context, alarm)
            repo.deleteById(alarm.id)
        }
    }

    /** 开关：借 remind 字段存"这条闹钟开着没" */
    fun toggle(alarm: EventEntity) {
        viewModelScope.launch {
            val next = alarm.copy(remind = !alarm.remind)
            repo.update(next)
            if (next.remind) AlarmScheduler.schedule(context, next) else AlarmScheduler.cancel(context, next)
        }
    }

    class Factory(
        private val repo: PerceptionRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AlarmViewModel(repo, context) as T
    }
}
