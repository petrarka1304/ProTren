@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.protren.ui.trainer

import android.app.Application
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.protren.data.UserPreferences
import com.example.protren.network.TrainerPlanApi
import com.example.protren.ui.exercises.ExercisePickerScreen
import com.example.protren.ui.main.SettingsScreen
import com.example.protren.ui.premium.TrainerPublicProfileScreen
import com.example.protren.viewmodel.TrainerOfferViewModel
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Composable
fun TrainerRoot(navController: NavHostController) {
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val trainerNav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    val offerVm: TrainerOfferViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TrainerOfferViewModel(
                    context.applicationContext as Application
                ) as T
            }
        }
    )

    Scaffold(
        bottomBar = { TrainerBottomBar(trainerNav) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        NavHost(
            navController = trainerNav,
            startDestination = "trainerHome",
            modifier = Modifier.padding(padding)
        ) {

            composable("trainerHome") {
                TrainerHomeScreen(nav = trainerNav, appNav = navController)
            }

            composable("trainerTrainees") {
                TrainerTraineesScreen(trainerNav)
            }

            composable("trainerChats") {
                TrainerChatsScreen(trainerNav)
            }

            composable("chatThread/{chatId}") { backStack ->
                val chatId = backStack.arguments?.getString("chatId") ?: return@composable
                ChatThreadScreen(nav = trainerNav, chatId = chatId)
            }

            composable(
                route = "imageViewer/{startIndex}",
                arguments = listOf(navArgument("startIndex") { type = NavType.IntType })
            ) { backStack ->
                val startIndex = backStack.arguments?.getInt("startIndex") ?: 0
                ImageViewerScreen(nav = trainerNav, startIndex = startIndex)
            }

            composable("trainerOffer") {
                TrainerOfferScreen(nav = trainerNav, vm = offerVm)
            }

            composable("exercisePicker") {
                ExercisePickerScreen(navController = trainerNav)
            }

            composable("trainerPeriodPlan") {
                TrainerPeriodPlanScreen(nav = trainerNav, traineeId = null)
            }

            composable("trainerPeriodPlan/{userId}") { backStack ->
                val userId = backStack.arguments?.getString("userId") ?: return@composable
                TrainerPeriodPlanScreen(nav = trainerNav, traineeId = userId)
            }

            composable("trainerPlans") {
                TrainerPlansScreen(trainerNav)
            }

            composable(
                route = "trainerPlanEditor/{planId}",
                arguments = listOf(navArgument("planId") { type = NavType.StringType })
            ) { backStack ->
                val planId = backStack.arguments?.getString("planId") ?: return@composable
                TrainerPlanEditorScreen(nav = trainerNav, planId = planId)
            }

            composable("trainerProfile") {
                TrainerMyProfileScreen(nav = trainerNav)
            }

            composable("trainerPublicProfile/{trainerId}") { backStack ->
                val trainerId = backStack.arguments?.getString("trainerId") ?: return@composable
                TrainerPublicProfileScreen(trainerId = trainerId)
            }

            composable("trainer/traineeProfile/{userId}") { backStack ->
                val userId = backStack.arguments?.getString("userId") ?: return@composable
                TraineeProfileScreen(nav = trainerNav, userId = userId)
            }

            composable("trainerSupplements/{traineeId}/{traineeName}") { backStack ->
                val traineeId = backStack.arguments?.getString("traineeId") ?: return@composable
                val api = rememberTrainerPlanApi(prefs)
                com.example.protren.ui.trainer.plans.TrainerPlansScreen(
                    traineeId = traineeId,
                    api = api
                )
            }

            composable("settings") {
                SettingsScreen(navController = trainerNav)
            }
        }
    }
}

@Composable
private fun rememberTrainerPlanApi(prefs: UserPreferences): TrainerPlanApi {
    val interceptor = remember(prefs) {
        Interceptor { chain ->
            val token = prefs.getAccessToken()
            val request = chain.request().newBuilder().apply {
                if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token")
            }.build()
            chain.proceed(request)
        }
    }

    val client = remember(interceptor) {
        OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    val retrofit = remember(client) {
        Retrofit.Builder()
            .baseUrl("https://protren-backend.onrender.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
    }

    return remember(retrofit) { retrofit.create(TrainerPlanApi::class.java) }
}
