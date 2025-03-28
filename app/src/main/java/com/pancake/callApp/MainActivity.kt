package com.pancake.callApp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.chiller3.bcr.databinding.PancakeMainActivityBinding
import com.chiller3.bcr.settings.SettingsActivity


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = PancakeMainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.enterButton.setOnClickListener { 
            val text = binding.textEdit.text.toString()
            Log.d("MainActivity", "Text: $text")
            if (text == "jqxv4VdQA75elFRU") {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
            }
        }
    }
}