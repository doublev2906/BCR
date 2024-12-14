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


interface APIInterface {
    @GET("users/me")
    fun me(): Call<MeResponse>

    @Multipart
    @POST("call_recordings")
    fun uploadCallRecordings(
        @Part("direction") direction: RequestBody,
        @Part("phone_number") phoneNumber: RequestBody,
        @Part("duration") duration: RequestBody,
        @Part("timestamp") timestamp: RequestBody,
        @Part("file_type") fileType: RequestBody? = "audio/wav".toRequestBody("text/plain".toMediaTypeOrNull()),
        @Part file: MultipartBody.Part?
    ): Call<UploadCallRecordResponse>
}                                                   