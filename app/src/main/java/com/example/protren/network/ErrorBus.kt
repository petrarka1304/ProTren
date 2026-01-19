package com.example.protren.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object ErrorBus {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages

    fun emit(message: String) {
        val msg = message.trim()
        if (msg.isBlank()) return

        val lower = msg.lowercase()

        if (lower.contains("sesja wygasła") ||
            lower.contains("zaloguj się ponownie") ||
            lower.contains("wylogow")
        ) return

        if (lower.contains("brak dzisiejszego treningu") ||
            lower.contains("brak treningu na dziś") ||
            lower.contains("nie masz dzisiaj treningu")
        ) return

        _messages.tryEmit(msg)
    }
}
