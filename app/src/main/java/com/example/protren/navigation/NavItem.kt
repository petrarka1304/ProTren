package com.example.protren.navigation

/**
 * Centralne definicje tras. Używaj tych stałych w całej aplikacji.
 * Jeśli masz inne nazwy/ścieżki – podmień tutaj, a reszta się zsynchronizuje.
 */
object NavItem {
    const val Splash = "splash"
    const val Login = "login"
    const val Register = "register"

    const val Home = "home"
    const val Profile = "profile"
    const val Analytics = "analytics"
    const val Workouts = "workouts"
    const val AddWorkout = "addWorkout"
    const val EditWorkout = "editWorkout" // jeśli używasz parametru, dodaj w NavGraph

    // Suplementy
    const val SupplementsToday = "supplements/today"
    const val SupplementsManage = "supplements/manage"
    // Edytor suplementu – wersja z opcjonalnym query param ?id={...}
    const val SupplementEditor = "supplements/editor"
}
