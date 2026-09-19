package com.yivi.perception.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yivi.perception.data.db.EventEntity
import com.yivi.perception.data.repo.PerceptionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(private val repo: PerceptionRepository) : ViewModel() {

    val alarms: StateFlow<List<EventEntity>> = repo.observeByCategory("闹钟")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(alarm: EventEntity) { viewModelScope.launch { repo.add(alarm) } }

    fun delete(id: Long) { viewModelScope.launch { repo.deleteById(id) } }

    /** 闹钟的开关借 remind 这个字段存（闹钟用不到"提醒"） */
    fun toggle(alarm: EventEntity) { viewModelScope.launch { repo.update(alarm.copy(remind = !alarm.remind)) } }

    class Factory(private val repo: PerceptionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AlarmViewModel(repo) as T
    }
}
