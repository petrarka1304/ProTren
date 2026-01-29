package com.example.protren.ui.trainer.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.protren.model.Supplement
import com.example.protren.network.TrainerPlanApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TrainerSuppUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val traineeId: String? = null,
    val items: List<Supplement> = emptyList(),
    val editorVisible: Boolean = false,
    val editorInitial: Supplement? = null,
)

class TrainerPlansViewModel(
    private val api: TrainerPlanApi
) : ViewModel() {

    private val _ui = MutableStateFlow(TrainerSuppUiState())
    val ui: StateFlow<TrainerSuppUiState> = _ui

    fun init(traineeId: String) {
        if (_ui.value.traineeId == traineeId && _ui.value.items.isNotEmpty()) return
        _ui.value = _ui.value.copy(traineeId = traineeId)
        load()
    }

    fun load() {
        val traineeId = _ui.value.traineeId ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isLoading = true, error = null)
            try {
                val res = api.listSupplements(traineeId)
                if (res.isSuccessful) {
                    _ui.value = _ui.value.copy(isLoading = false, items = res.body().orEmpty())
                } else {
                    _ui.value = _ui.value.copy(isLoading = false, error = "Błąd ${res.code()}")
                }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun openCreate() {
        _ui.value = _ui.value.copy(editorVisible = true, editorInitial = null)
    }

    fun openEdit(item: Supplement) {
        _ui.value = _ui.value.copy(editorVisible = true, editorInitial = item)
    }

    fun closeEditor() {
        _ui.value = _ui.value.copy(editorVisible = false, editorInitial = null)
    }

    fun saveSupplement(
        name: String,
        dosage: String?,
        notes: String?,
        times: List<String>,
        daysOfWeek: List<Int>
    ) {
        val traineeId = _ui.value.traineeId ?: return
        val editing = _ui.value.editorInitial
        android.util.Log.d("TRAINER_DEBUG", "Próba zapisu. Editing ID: ${editing?._id}")
        val payload: Map<String, Any?> = mapOf(
            "name" to name.trim(),
            "dosage" to (dosage?.trim().orEmpty()),
            "notes" to (notes?.trim().orEmpty()),
            "times" to times,
            "daysOfWeek" to daysOfWeek
        )

        viewModelScope.launch {
            _ui.value = _ui.value.copy(isLoading = true, error = null)
            try {
                val res = if (editing == null) {
                    api.createSupplement(traineeId, payload)
                } else {
                    val sid = editing._id ?: return@launch run {
                        _ui.value = _ui.value.copy(isLoading = false, error = "Brak ID suplementu")
                    }
                    api.updateSupplement(traineeId, sid, payload)
                }

                if (res.isSuccessful) {
                    closeEditor()
                    load()
                } else {
                    _ui.value = _ui.value.copy(isLoading = false, error = "Błąd zapisu ${res.code()}")
                }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun deleteSupplement(item: Supplement) {
        val traineeId = _ui.value.traineeId ?: return
        val sid = item._id ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isLoading = true, error = null)
            try {
                val res = api.deleteSupplement(traineeId, sid)
                if (res.isSuccessful) {
                    load()
                } else {
                    _ui.value = _ui.value.copy(isLoading = false, error = "Błąd usuwania ${res.code()}")
                }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}

