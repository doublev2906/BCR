package com.pancake.callApp.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


internal object APIClient {
//    private const val BASE_URL = "http://192.168.1.29:4005/api/"
    private const val BASE_URL = "https://hub.internal.pancake.vn/api/"
    private const val ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3NDQ4OTM1ODQsImlhdCI6MTczNzExNzU4NCwiaWQiOiIzOWIwZWQ3MC1mZThhLTQzZTUtOTkyNy1hZDQ1MmMzZTE0NDQiLCJuYW1lIjoibmd1eWVuYmFkdXkxMjMifQ.ey7SP55Cb9pTEK7UiK0GWtAAzzATEA2kYC-BWl3iiHI"

    val client: APIInterface
        get() {
            val interceptor: HttpLoggingInterceptor = HttpLoggingInterceptor()
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
            val client = OkHttpClient.Builder().addInterceptor(interceptor).addInterceptor(
                Interceptor { chain ->
                    val original: Request = chain.request()
                    val originalHttpUrl: HttpUrl = original.url

                    val url = originalHttpUrl.newBuilder()
                        .addQueryParameter("access_token", ACCESS_TOKEN)
                        .build() 
                    
                    // Request customization: add request headers
                    val requestBuilder: Request.Builder = original.newBuilder()
                        .url(url)

                    val request: Request = requestBuilder.build()
                    chain.proceed(request)
                }).build()


            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
                .create(APIInterface::class.java)
        }
}