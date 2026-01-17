package com.example.protren.repository

import com.example.protren.model.UserProfile
import com.example.protren.network.NutritionResponse
import com.example.protren.network.SaveProfileResponse
import com.example.protren.network.UserProfileApi
import retrofit2.Response

class UserProfileRepository(
    private val api: UserProfileApi
) {
    suspend fun getProfile(): Response<UserProfile> =
        api.getProfile()

    suspend fun saveMyProfile(profile: UserProfile): Response<SaveProfileResponse> =
        api.saveMyProfile(profile)

    suspend fun saveUserProfile(userId: String, profile: UserProfile): Response<SaveProfileResponse> =
        api.saveUserProfile(userId, profile)

    suspend fun calculateNutrition(profile: UserProfile): Response<NutritionResponse> =
        api.calculateNutrition(profile)
}
