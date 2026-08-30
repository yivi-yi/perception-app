package com.yivi.perception.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yivi.perception.data.db.EventEntity
import com.yivi.perception.data.repo.PerceptionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val repo: PerceptionRepository) : ViewModel() {

    val category = MutableStateFlow("行程")

    val events: StateFlow<List<EventEntity>> =
        category.flatMapLatest { repo.observeByCategory(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun switchCategory(c: String) { category.value = c }

    fun add(event: EventEntity) { viewModelScope.launch { repo.add(event) } }

    fun delete(id: Long) { viewModelScope.launch { repo.deleteById(id) } }

    class Factory(private val repo: PerceptionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repo) as T
    }
}
