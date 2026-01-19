package com.example.protren.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.protren.network.ExerciseDto
import com.example.protren.repository.ExerciseRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ExercisePickerState(
    val loading: Boolean = false,
    val error: String? = null,
    val items: List<ExerciseDto> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val query: String = "",
    val group: String? = null,
    val equipment: String? = null,
    val page: Int = 1,
    val endReached: Boolean = false,
    val groups: List<String> = listOf("Wszystkie")
)

class ExercisePickerViewModel(
    private val repo: ExerciseRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(ExercisePickerState())
    val ui: StateFlow<ExercisePickerState> = _ui

    private var running: Job? = null

    init {
        refresh()
        loadGroups()
    }

    fun refresh() {
        loadPage(page = 1, replace = true)
    }

    fun loadNext() {
        val s = _ui.value
        if (s.loading || s.endReached) return
        loadPage(page = s.page + 1, replace = false)
    }

    fun onQueryChange(q: String) {
        _ui.value = _ui.value.copy(query = q)
        refresh()
    }

    fun onGroupPick(g: String) {
        _ui.value = _ui.value.copy(group = if (g == "Wszystkie") null else g)
        refresh()
    }

    fun toggle(id: String) {
        val s = _ui.value
        val set = s.selectedIds.toMutableSet()
        if (!set.add(id)) set.remove(id)
        _ui.value = s.copy(selectedIds = set)
    }

    private fun loadGroups() = viewModelScope.launch {
        val names = repo.groups().map { it.name }.sorted()
        _ui.value = _ui.value.copy(groups = listOf("Wszystkie") + names)
    }

    private fun loadPage(page: Int, replace: Boolean) {
        running?.cancel()
        running = viewModelScope.launch {
            val s = _ui.value
            _ui.value = s.copy(loading = true, error = null)
            runCatching {
                repo.page(
                    query = s.query.ifBlank { null },
                    group = s.group,
                    equipment = s.equipment,
                    page = page,
                    limit = 50,
                    mine = false
                )
            }.onSuccess { p ->
                val newItems = p?.items.orEmpty()
                val merged = if (replace) newItems else s.items + newItems
                _ui.value = s.copy(
                    loading = false,
                    items = merged,
                    page = page,
                    endReached = newItems.isEmpty()
                )
            }.onFailure { e ->
                _ui.value = s.copy(loading = false, error = e.message ?: "Błąd pobierania ćwiczeń")
            }
        }
    }

    fun pickedNames(): List<String> {
        val s = _ui.value
        val map = s.items.associateBy { it._id }
        return s.selectedIds.mapNotNull { map[it]?.name }
    }
}

class ExercisePickerVmFactory(
    private val repo: ExerciseRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ExercisePickerViewModel(repo) as T
    }
}
