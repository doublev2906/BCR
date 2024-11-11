package com.pancake.callApp.view

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chiller3.bcr.R
import com.chiller3.bcr.databinding.CallItemLayoutBinding
import com.chiller3.bcr.databinding.PancakeHomeActivityBinding
import com.chiller3.bcr.settings.SettingsActivity
import com.pancake.callApp.PancakePreferences
import com.pancake.callApp.database.CallLog
import com.pancake.callApp.database.CallLogWithCalls
import com.pancake.callApp.database.PancakeDatabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {
    
    private lateinit var binding: PancakeHomeActivityBinding
    private lateinit var listCallAdapter: ListCallAdapter
    
    private var page : Int = 0
    
    private lateinit var preferences: PancakePreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = PancakeHomeActivityBinding.inflate(layoutInflater)
        preferences = PancakePreferences(this)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setInitUi()
    }
    
    private fun setInitUi() {
        val user = preferences.user
        binding.settings.setOnClickListener { 
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.userName.text = user?.name
        Glide.with(this)
            .load(user?.avatar)
            .placeholder(R.drawable.user_circle_fill)
            .error(R.drawable.user_circle_fill)
            .into(binding.userAvatar)

        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(this)
        binding.listCall.layoutManager = layoutManager
        listCallAdapter = ListCallAdapter(emptyList())
        binding.listCall.adapter = listCallAdapter
        binding.swipeRefreshLayout.setOnRefreshListener { 
            binding.swipeRefreshLayout.isRefreshing = true
            loadCallLogs(isFirstLoading = true)
        }
        binding.btnLogout.setOnClickListener { 
            logOut()
        }
        binding.listCall.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val lastVisibleItemPosition = (recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
                if (lastVisibleItemPosition == listCallAdapter.itemCount - 1) {
                    page++
                    loadCallLogs()
                }
            }
        })
        loadCallLogs()
    }

    private fun logOut() {
        preferences.accessToken = null
        preferences.user = null
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadCallLogs(isFirstLoading: Boolean = false) {
        if (isFirstLoading) {
            binding.loadingCall.visibility = View.VISIBLE
            page = 0
        }
        
        lifecycleScope.launch { 
            try {
                val callLogs : List<CallLogWithCalls> = PancakeDatabase.getAllCallLogs(page = page)
                runOnUiThread {
                    listCallAdapter.callList = callLogs
                    listCallAdapter.notifyDataSetChanged()
                    if (isFirstLoading) {
                        binding.loadingCall.visibility = View.GONE
                        binding.swipeRefreshLayout.isRefreshing = false
                    }
                }
            } catch (e: Exception) {
                Log.d("HomeActivity", "Error loading call logs", e)
                runOnUiThread {
                    binding.loadingCall.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }
}

class ListCallAdapter(
    var callList: List<CallLogWithCalls>,
) : RecyclerView.Adapter<ListCallAdapter.ViewHolder>() {

    // create an inner class with name ViewHolder
    // It takes a view argument, in which pass the generated class of single_item.xml
    // ie SingleItemBinding and in the RecyclerView.ViewHolder(binding.root) pass it like this
    inner class ViewHolder(val binding: CallItemLayoutBinding) : RecyclerView.ViewHolder(binding.root)

    // inside the onCreateViewHolder inflate the view of SingleItemBinding
    // and return new ViewHolder object containing this layout
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = CallItemLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        return ViewHolder(binding)
    }

    // bind the items with each item 
    // of the list languageList 
    // which than will be
    // shown in recycler view
    // to keep it simple we are
    // not setting any image data to view
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with(holder){
            with(callList[position]){
                val currentCall = callList[position]
                val call = currentCall.calls[0]
                binding.phoneNumber.text = call.phoneNumberFormatted
                binding.callDescription.text = generateCallDescription(currentCall.callLog)
                when (callLog.direction) {
                    "in" -> 
                        binding.iconCallDes.setImageResource(R.drawable.phone_callback_24px)
                    "out" ->
                        binding.iconCallDes.setImageResource(R.drawable.phone_forwarded_24px)
                    "missed_in" -> 
                        binding.iconCallDes.setImageResource(R.drawable.phone_missed_24px)
                    "missed_out" ->
                        binding.iconCallDes.setImageResource(R.drawable.e911_avatar_24px)
                    else -> 
                        binding.iconCallDes.setImageResource(R.drawable.baseline_call_24)
                }
            }
        }
    }

    // return the size of languageList
    override fun getItemCount(): Int {
        return callList.size
    }
    
    private fun generateCallDescription( callLog: CallLog) : String {
        val direction = when (callLog.direction) {
            "in" -> "Cuộc gọi đến"
            "missed_in" -> "Cuộc gọi nhỡ"
            "missed_out" -> "Cuộc gọi đi nhỡ"
            "out" -> "Cuộc gọi đi"
            else -> "Cuộc gọi"
        }
        val callTime = formatCallTime(callLog.timestampUnixMs)
        val recordTime = formatTimeCalL(callLog.output?.recording?.durationSecsEncoded)
        return "$direction vào lúc $callTime  $recordTime"
        
    }
    
    private fun formatCallTime(timestamp: Long) : String {
        val callTime = Date(timestamp)
        val now = Date()
        val locale = Locale("vi", "VN")
        val differentTime = now.time - callTime.time
        return if (differentTime< 24 * 60 * 60 * 1000) {
            SimpleDateFormat("HH:mm", locale).format(callTime)
        } else {
            SimpleDateFormat("dd/MM", locale).format(callTime)
        }
    }
    
    @SuppressLint("DefaultLocale")
    private fun formatTimeCalL(time: Double?) : String {
        if (time == null) return ""
        if (time < 60) return "${time.toInt()} giây"
        if (time < 3600) {
            val minutes = time / 60
            val seconds = time % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
        val hours = time / 3600
        val minutes = (time % 3600) / 60
        val seconds = time % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}