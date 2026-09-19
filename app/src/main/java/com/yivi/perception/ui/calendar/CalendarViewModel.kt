package com.yivi.perception.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yivi.perception.data.db.EventEntity
import com.yivi.perception.data.repo.PerceptionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CalendarViewModel(private val repo: PerceptionRepository) : ViewModel() {

    val events: StateFlow<List<EventEntity>> = repo.observeByCategory("行程")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(event: EventEntity) { viewModelScope.launch { repo.add(event) } }

    fun delete(id: Long) { viewModelScope.launch { repo.deleteById(id) } }

    class Factory(private val repo: PerceptionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CalendarViewModel(repo) as T
    }
}
