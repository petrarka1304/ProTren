package com.example.protren.model

import com.google.gson.annotations.SerializedName


data class Trainer(
    @SerializedName("id") val id: String? = null,
    @SerializedName("_id") val mongoId: String? = null,
    @SerializedName("userId") val userId: String? = null,

    @SerializedName("name") val name: String = "",
    @SerializedName("email") val email: String? = null,
    @SerializedName("headline") val headline: String? = null,
    @SerializedName("bio") val bio: String? = null,

    @SerializedName("specialties") val specialties: List<String>? = null,

    @SerializedName("priceMonth") val priceMonth: Double? = null,

    @SerializedName("ratingAvg") val ratingAvg: Double? = null,
    @SerializedName("ratingCount") val ratingCount: Int? = null,

    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("galleryUrls") val galleryUrls: List<String>? = null,
    @SerializedName("maxTrainees") val maxTrainees: Int? = null,

    @SerializedName("traineesCount") val traineesCount: Int? = null,
    @SerializedName("workoutsCount") val workoutsCount: Int? = null
) {
    val stableId: String get() = id ?: mongoId.orEmpty()
}
