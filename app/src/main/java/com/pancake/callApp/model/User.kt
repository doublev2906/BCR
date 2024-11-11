package com.pancake.callApp.model

import com.google.gson.annotations.SerializedName


data class User(
    @SerializedName("avatar") var avatar: String? = null,
    @SerializedName("countries") var countries: ArrayList<String> = arrayListOf(),
    @SerializedName("email") var email: String? = null,
    @SerializedName("group") var group: String? = null,
    @SerializedName("id") var id: String? = null,
    @SerializedName("inserted_at") var insertedAt: String? = null,
    @SerializedName("locale") var locale: String? = null,
    @SerializedName("name") var name: String? = null,
    @SerializedName("phone_number") var phoneNumber: String? = null,
    @SerializedName("projects") var projects: ArrayList<String> = arrayListOf(),
    @SerializedName("updated_at") var updatedAt: String? = null
)

data class MeResponse(
    @SerializedName("status") var status: String? = null,
    @SerializedName("user") var user: User? = User()
)
