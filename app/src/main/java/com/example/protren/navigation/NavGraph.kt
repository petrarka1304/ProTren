package com.example.protren.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.protren.data.UserPreferences
import com.example.protren.network.ApiClient
import com.example.protren.network.ChatApi
import com.example.protren.network.ReviewApi
import com.example.protren.network.StartChatRequest
import com.example.protren.repository.ReviewRepository
import com.example.protren.ui.analytics.AnalyticsScreen
import com.example.protren.ui.analytics.TrackedExercisesProgressScreen
import com.example.protren.ui.analytics.TrackedExercisesScreen
import com.example.protren.ui.auth.RegisterScreen
import com.example.protren.ui.login.LoginScreen
import com.example.protren.ui.main.HomeScreen
import com.example.protren.ui.main.ProfileScreen
import com.example.protren.ui.main.SettingsScreen
import com.example.protren.ui.plans.PlanDetailsScreen
import com.example.protren.ui.plans.PlanEditorScreen
import com.example.protren.ui.plans.PlansScreen
import com.example.protren.ui.pr.ExerciseHistoryScreen
import com.example.protren.ui.pr.PersonalRecordsScreen
import com.example.protren.ui.premium.PurchaseTrainerScreen
import com.example.protren.ui.premium.TrainerListScreen
import com.example.protren.ui.premium.TrainerPublicProfileScreen
import com.example.protren.ui.reviews.AddReviewScreen
import com.example.protren.ui.splash.SplashScreen
import com.example.protren.ui.supplements.SupplementEditorScreen
import com.example.protren.ui.supplements.SupplementsManageScreen
import com.example.protren.ui.supplements.SupplementsScreen
import com.example.protren.ui.trainer.TrainerPeriodPlanScreen
import com.example.protren.ui.trainer.TrainerRoot
import com.example.protren.ui.user.UserChatsScreen
import com.example.protren.ui.viewer.ImageViewerScreen
import com.example.protren.ui.workouts.AddWorkoutScreen
import com.example.protren.ui.workouts.AutoPlanScreen
import com.example.protren.ui.workouts.EditWorkoutScreen
import com.example.protren.ui.workouts.WorkoutsListScreen
import com.example.protren.ui.exercises.ExercisePickerScreen
import com.example.protren.ui.trainer.ChatThreadScreen
import com.example.protren.viewmodel.HomeViewModel
import com.example.protren.viewmodel.ReviewViewModel
import kotlinx.coroutines.launch

@Composable
fun AppNavGraph(
    navController: NavHostController,
    isTrainer: Boolean
) {
    NavHost(
        navController = navController,
        startDestination = "splash",
        route = "root_graph"
    ) {
        // ─── Splash / Auth ───
        composable("splash") { SplashScreen(navController) }
        composable(NavItem.Login) { LoginScreen(navController) }
        composable(NavItem.Register) { RegisterScreen(navController) }

        // ─── HOME ───
        composable(NavItem.Home) {
            val ctx = LocalContext.current
            val prefs = remember { UserPreferences(ctx) }
            val vm = remember { HomeViewModel(prefs) }

            HomeScreen(
                navController = navController,
                viewModel = vm
            )
        }

        // ─── Analityka ───
        composable(NavItem.Analytics) { AnalyticsScreen(navController) }

        // ✅ NOWE: Śledzone ćwiczenia (wybór + podgląd)
        composable("trackedExercises") { TrackedExercisesScreen(navController) }
        composable("trackedExercisesProgress") { TrackedExercisesProgressScreen(navController) }

        // ─── Suplementy ───
        composable(NavItem.SupplementsToday) {
            SupplementsScreen(navController = navController)
        }
        composable(NavItem.SupplementEditor) {
            SupplementEditorScreen(navController = navController, supplementId = null)
        }
        composable(
            route = "${NavItem.SupplementEditor}?id={id}",
            arguments = listOf(navArgument("id") { nullable = true; defaultValue = null })
        ) { backStack ->
            val id = backStack.arguments?.getString("id")
            SupplementEditorScreen(navController = navController, supplementId = id)
        }
        composable("supplements/manage") {
            SupplementsManageScreen(navController = navController)
        }

        // ─── Workouts ───
        composable(NavItem.Workouts) { WorkoutsListScreen(navController) }
        composable(NavItem.AddWorkout) { AddWorkoutScreen(navController) }
        composable("${NavItem.EditWorkout}/{id}") { backStack ->
            val id = backStack.arguments?.getString("id") ?: ""
            EditWorkoutScreen(navController, workoutId = id)
        }
        composable("autoPlan") { AutoPlanScreen(navController) }

        // ─── Plany ───
        composable("plans") { PlansScreen(navController) }
        composable("planDetails/{id}") { backStack ->
            val id = backStack.arguments?.getString("id") ?: ""
            PlanDetailsScreen(navController, planId = id)
        }
        composable("planEditor/{id}") { backStack ->
            val id = backStack.arguments?.getString("id") ?: ""
            PlanEditorScreen(navController = navController, planId = id)
        }

        // ─── PR ───
        composable("pr") { PersonalRecordsScreen(navController) }
        composable("pr/{encodedName}") { backStack ->
            val name = backStack.arguments?.getString("encodedName") ?: ""
            ExerciseHistoryScreen(navController, encodedName = name)
        }

        // ─── Profil ───
        composable(NavItem.Profile) { ProfileScreen(navController) }
        composable("exercisePicker") { ExercisePickerScreen(navController) }

        composable("settings") { SettingsScreen(navController) }

        // ─── Trenerzy / premium ───
        composable("trainerList") { TrainerListScreen(navController) }

        // publiczny profil trenera (widok użytkownika)
        composable("trainerProfile/{id}") { backStack ->
            val id = backStack.arguments?.getString("id") ?: ""

            val ctx = LocalContext.current
            val prefs = remember { UserPreferences(ctx) }

            val api = remember {
                ApiClient.createWithAuth(
                    tokenProvider = { prefs.getAccessToken() },
                    refreshTokenProvider = { prefs.getRefreshToken() },
                    onTokensUpdated = { a, r -> prefs.setTokens(a, r) },
                    onUnauthorized = { prefs.clearAll() }
                ).create(ReviewApi::class.java)
            }

            val repo = remember { ReviewRepository(api) }
            val reviewVm = remember { ReviewViewModel(repo) }

            TrainerPublicProfileScreen(
                trainerId = id,
                reviewViewModel = reviewVm,
                onAddReviewClick = { trainerId ->
                    navController.navigate("trainerReview/$trainerId")
                }
            )
        }

        composable(
            route = "purchaseTrainer/{trainerId}",
            arguments = listOf(navArgument("trainerId") { type = NavType.StringType })
        ) { backStack ->
            val trainerId = backStack.arguments?.getString("trainerId") ?: ""
            PurchaseTrainerScreen(navController = navController, trainerId = trainerId)
        }

        // ─── Dodawanie opinii o trenerze ───
        composable(
            route = "trainerReview/{trainerId}",
            arguments = listOf(navArgument("trainerId") { type = NavType.StringType })
        ) { backStack ->
            val trainerId = backStack.arguments?.getString("trainerId") ?: ""
            val ctx = LocalContext.current
            val prefs = remember { UserPreferences(ctx) }

            val api = remember {
                ApiClient.createWithAuth(
                    tokenProvider = { prefs.getAccessToken() },
                    refreshTokenProvider = { prefs.getRefreshToken() },
                    onTokensUpdated = { a, r -> prefs.setTokens(a, r) },
                    onUnauthorized = { prefs.clearAll() }
                ).create(ReviewApi::class.java)
            }

            val repo = remember { ReviewRepository(api) }
            val reviewVm = remember { ReviewViewModel(repo) }

            AddReviewScreen(
                trainerId = trainerId,
                viewModel = reviewVm,
                onFinished = {
                    navController.popBackStack()
                }
            )
        }

        // ─── Czaty ───
        composable(
            route = "chatThread/{chatId}?otherUserId={otherUserId}&otherName={otherName}",
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("otherUserId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("otherName") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStack ->
            val id = backStack.arguments?.getString("chatId") ?: return@composable
            val otherUserId = backStack.arguments?.getString("otherUserId")
            val otherName = backStack.arguments?.getString("otherName")
            ChatThreadScreen(
                nav = navController,
                chatId = id,
                otherUserId = otherUserId,
                otherNameInitial = otherName
            )
        }
        composable("chats") { UserChatsScreen(navController) }

        // 🔹 NOWY EKRAN: start czatu z wybranym trenerem
        composable(
            route = "chatStart/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStack ->
            val userId = backStack.arguments?.getString("userId") ?: return@composable
            ChatStartScreen(navController = navController, otherUserId = userId)
        }

        // ─── Panel trenera ───
        composable("trainerRoot") { TrainerRoot(navController = navController) }
        composable("trainerProfile") {
            val ctx = LocalContext.current
            val prefs = remember { UserPreferences(ctx) }

            val api = remember {
                ApiClient.createWithAuth(
                    tokenProvider = { prefs.getAccessToken() },
                    refreshTokenProvider = { prefs.getRefreshToken() },
                    onTokensUpdated = { a, r -> prefs.setTokens(a, r) },
                    onUnauthorized = { prefs.clearAll() }
                ).create(ReviewApi::class.java)
            }

            val repo = remember { ReviewRepository(api) }
            val reviewVm = remember { ReviewViewModel(repo) }

            // profil "me" – trener widzi swoje opinie
            TrainerPublicProfileScreen(
                trainerId = "me",
                reviewViewModel = reviewVm
            )
        }
        composable(
            route = "trainerPeriodPlan/{traineeId}",
            arguments = listOf(navArgument("traineeId") { nullable = true })
        ) { backStack ->
            val traineeId = backStack.arguments?.getString("traineeId")
            TrainerPeriodPlanScreen(nav = navController, traineeId = traineeId)
        }
        composable(
            route = "imageViewer/{startIndex}",
            arguments = listOf(navArgument("startIndex") { type = NavType.IntType })
        ) { backStackEntry ->
            val startIndex = backStackEntry.arguments?.getInt("startIndex") ?: 0
            ImageViewerScreen(
                nav = navController,
                startIndex = startIndex
            )
        }
    }
}

/**
 * Ekran pomocniczy: po wybraniu trenera wywołuje /chats/start,
 * a potem przekierowuje do ChatThreadScreen.
 */
@Composable
private fun ChatStartScreen(
    navController: NavHostController,
    otherUserId: String
) {
    val ctx = LocalContext.current
    val prefs = remember { UserPreferences(ctx) }
    val scope = rememberCoroutineScope()

    val api = remember {
        ApiClient.createWithAuth(
            tokenProvider = { prefs.getAccessToken() },
            refreshTokenProvider = { prefs.getRefreshToken() },
            onTokensUpdated = { a, r -> prefs.setTokens(a, r) },
            onUnauthorized = { prefs.clearAll() }
        ).create(ChatApi::class.java)
    }

    LaunchedEffect(otherUserId) {
        scope.launch {
            try {
                val resp = api.startOrGet(StartChatRequest(userId = otherUserId))
                if (resp.isSuccessful) {
                    val chat = resp.body()
                    if (chat != null) {
                        val otherId = chat.otherUserId ?: otherUserId
                        val encodedName = chat.otherName?.let { Uri.encode(it) } ?: ""
                        navController.navigate(
                            "chatThread/${chat.id}?otherUserId=$otherId&otherName=$encodedName"
                        ) {
                            popUpTo("trainerList") { inclusive = false }
                        }
                    } else {
                        navController.popBackStack()
                    }
                } else {
                    navController.popBackStack()
                }
            } catch (_: Exception) {
                navController.popBackStack()
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
