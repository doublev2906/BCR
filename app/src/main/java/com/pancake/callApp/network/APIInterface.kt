package com.pancake.callApp.network

import com.pancake.callApp.model.MeResponse
import retrofit2.Call
import retrofit2.http.GET



interface APIInterface {
    @GET("users/me")
    fun me(): Call<MeResponse>
}                                                   