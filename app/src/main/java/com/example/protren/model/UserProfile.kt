package com.example.protren.model

import com.google.gson.annotations.SerializedName

data class UserProfile(
    @SerializedName("userId") val userId: String? = null,

    val age: Int? = null,
    val height: Int? = null,
    val weight: Int? = null,
    val goal: String? = null,
    val gender: String? = null,

    @SerializedName("activityLevel")
    val activityLevel: String? = null,

    val calories: Int? = null,
    val protein: Int? = null,
    val fat: Int? = null,
    val carbs: Int? = null,

    val trainingPlanId: String? = null,

    @SerializedName("avatar")
    val avatar: String? = null,

    @SerializedName("trainerId")
    val trainerId: String? = null,

    @SerializedName("subscriptionActive")
    val subscriptionActive: Boolean? = null,

    @SerializedName("subscriptionUntil")
    val subscriptionUntil: String? = null,
)
