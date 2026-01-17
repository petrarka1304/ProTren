package com.example.protren.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Prosty bus zdarzeń autoryzacji.
 * Wysyłamy sygnał wylogowania z powodem (np. "Sesja wygasła...").
 */
object AuthBus {
    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AuthEvent> = _events

    fun emit(event: AuthEvent) {
        _events.tryEmit(event)
    }

    /**
     * Najczęstszy przypadek: wymuszone wylogowanie (np. wygasła sesja).
     */
    fun emitLoggedOut(message: String) {
        _events.tryEmit(AuthEvent.LoggedOut(message))
    }
}

sealed class AuthEvent {
    data class LoggedOut(val reason: String) : AuthEvent()
}
