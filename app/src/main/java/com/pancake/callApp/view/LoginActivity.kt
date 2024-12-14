package com.pancake.callApp.view

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chiller3.bcr.Permissions
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.databinding.PancakeLoginActivityBinding
import com.chiller3.bcr.format.Format
import com.google.gson.Gson
import com.pancake.callApp.PancakePreferences
import com.pancake.callApp.network.APIClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.awaitResponse


class LoginActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "LoginActivity"
    }

    private val requestPermissionRequired =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            // Call recording can still be enabled if optional permissions were not granted
            if (granted.all { it.key !in Permissions.REQUIRED || it.value }) {
                setDefaultPreferences()
            } else {
                startActivity(Permissions.getAppInfoIntent(this))
            }
        }

    private val startForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("access_token")?.let {
                PancakePreferences(this).accessToken = it
                fetchUser()
            }
        } else {
            setButtonToDefault()
        }
    }
    
    

    private fun setButtonToDefault() {
        runOnUiThread {
            binding.loginButton.isEnabled = true
            binding.loginButton.text = "Đăng nhập bằng Pancake Account"
        }

    }
    private fun fetchUser() {
        lifecycleScope.launch(Dispatchers.IO) {
            runOnUiThread {
                binding.loginButton.text = "Đang tải thông tin người dùng ..."
            }
            try {
                val response = APIClient.client.me().awaitResponse()
                if (response.isSuccessful) {
                    response.body()?.user?.let { user ->
                        Log.d(TAG, "User: $user")
                        PancakePreferences(this@LoginActivity).user = user
                        startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                        finish()
                    }
                } else {
                    setButtonToDefault()
//                    Toast.makeText(this@LoginActivity, "Đã xảy ra lỗi ${response.code()}", Toast.LENGTH_LONG).show()
                    Log.e( TAG, "Error: ${response.code()}")
                }
            } catch (e: Exception) {
                setButtonToDefault()
//                Toast.makeText(this@LoginActivity, "Đã xảy ra lỗi $e", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Exception: $e")
            }
        }
    }
    
    private lateinit var binding : PancakeLoginActivityBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = PancakeLoginActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.loginButton.setOnClickListener {
            binding.loginButton.isEnabled = false
            binding.loginButton.text = "Đang đăng nhập..."
            loginPancakeAccount()
        }
        
    }

    override fun onStart() {
        super.onStart()
        if (!Permissions.haveRequired(this)) {
            requestPermissionRequired.launch(Permissions.REQUIRED + Permissions.OPTIONAL)
        } else {
            setDefaultPreferences()
        }
    }

    private fun setDefaultPreferences() {
        val bcrPref = Preferences(this)
        if (!bcrPref.isCallRecordingEnabled) {
            bcrPref.isCallRecordingEnabled = true
        }
        if (!bcrPref.writeMetadata) {
            bcrPref.writeMetadata = true
        }
        bcrPref.format = Format.getByName("WAV/PCM")
    }

    private fun loginPancakeAccount() {
//        PancakePreferences(this).accessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3NDE4Mzk1MjIsImlhdCI6MTczNDA2MzUyMiwiaWQiOiIxMmRhNTkzZC0xN2Q0LTRjMzktYTE4NS02ZDY1ZTdlMDllMWYiLCJuYW1lIjoiTkFOQSJ9.qMowstjvnkOZeHgYLs1iVxixgr0iH6dNQpzoBA8qoMc"
//        fetchUser()
//        return;
        val stateMap = mapOf(
            "mobile_login" to true,
            "country" to "VN",
            "application" to "pancake_sim_call",
            "os" to "android"
        )

        val stateByte = Gson().toJson(stateMap).toByteArray()
        val state = Base64.encodeToString(stateByte, Base64.DEFAULT)
        val redirectUrl = "https://hub.internal.pancake.vn/api/users/pancake_id_login_success"
        val params = mapOf(
            "grant_type" to "code",
            "client_id" to "4c44c5400d2c427a9c276908accf8f20",
            "redirect_uri" to redirectUrl,
            "scope" to "avatar,email",
            "locale" to "vi",
            "verification_method" to "email",
            "state" to state,
        )
        val intent = Intent(this, WebViewActivity::class.java)
        intent.putExtra(
            "url",
            "https://account.pancake.vn/oauth2/authorize?${objectToQueryString(params)}"
        )
        startForResult.launch(intent)
    }

    private fun objectToQueryString(params: Map<String, Any?>): String {
        val result = mutableListOf<String>()
        for ((key, value) in params) {
            result.add("${Uri.encode(key, "UTF-8")}=$value")
        }
        return result.joinToString("&")
    }
    

}
