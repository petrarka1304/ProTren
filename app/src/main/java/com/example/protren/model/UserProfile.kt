package com.example.protren.model

import com.google.gson.annotations.SerializedName

data class UserProfile(
    @SerializedName("userId") val userId: String? = null,

    val age: Int? = null,
    val height: Int? = null,
    val weight: Int? = null,
    val goal: String? = null,
    val gender: String? = null,

    // w Mongo / JSON pole nazywa się "activityLevel"
    @SerializedName("activityLevel")
    val activityLevel: String? = null,

    val calories: Int? = null,
    val protein: Int? = null,
    val fat: Int? = null,
    val carbs: Int? = null,

    val trainingPlanId: String? = null,

    @SerializedName("avatar")
    val avatar: String? = null,

    // 🔑 z Mongo: ObjectId trenera jako string
    @SerializedName("trainerId")
    val trainerId: String? = null,

    // 🔑 status współpracy
    @SerializedName("subscriptionActive")
    val subscriptionActive: Boolean? = null,

    @SerializedName("subscriptionUntil")
    val subscriptionUntil: String? = null,
)
