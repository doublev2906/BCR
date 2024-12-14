package com.pancake.callApp.network

import android.util.Log
import com.chiller3.bcr.RecorderApplication
import com.pancake.callApp.PancakePreferences
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

    val client: APIInterface
        get() {
            val interceptor: HttpLoggingInterceptor = HttpLoggingInterceptor()
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
            val client = OkHttpClient.Builder().addInterceptor(interceptor).addInterceptor(
                Interceptor { chain ->
                    val accessToken = PancakePreferences(RecorderApplication.getAppContext()).accessToken
                        ?: return@Interceptor chain.proceed(chain.request())
                    val original: Request = chain.request()
                    val originalHttpUrl: HttpUrl = original.url

                    val url = originalHttpUrl.newBuilder()
                        .addQueryParameter("access_token", accessToken)
                        .build() 

                    Log.d("APIClient", "get: $url")
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