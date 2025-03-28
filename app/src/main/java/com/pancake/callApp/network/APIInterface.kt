package com.pancake.callApp.network

import com.pancake.callApp.model.MeResponse
import com.pancake.callApp.model.UploadCallRecordResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap


interface APIInterface {
    @GET("users/me")
    fun me(): Call<MeResponse>

    @Multipart
    @POST("call_recordings")
    fun uploadCallRecordings(
        @PartMap data: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part file: MultipartBody.Part?
    ): Call<UploadCallRecordResponse>

    @FormUrlEncoded
    @POST("call_recordings/heartbeat")
    fun checkHeartbeat(
        @Field("id") id: String,
        @Field("name") name: String
    ): Call<Any>
}                                                   