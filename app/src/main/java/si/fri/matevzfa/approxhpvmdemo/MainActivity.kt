package si.fri.matevzfa.approxhpvmdemo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var mBroadcastReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //
        // Example of a call to a native method
        findViewById<TextView>(R.id.usedConf).text = ""

        //
        // Create notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val name = "HAR Service notification"
            val descriptionText = "Permanent notification."
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
            mChannel.description = descriptionText
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }

        //
        // Update switch value and set listener
        findViewById<SwitchCompat>(R.id.serviceSwitch).apply {

            // Update value
            isChecked = HARService.isRunning(this@MainActivity)

            // Set listener
            setOnCheckedChangeListener { _, isChecked ->
                Intent(this@MainActivity, HARService::class.java).also { intent ->
                    if (isChecked) {
                        Log.i(TAG, "Starting service")
                        startForegroundService(intent)
                    } else {
                        Log.i(TAG, "Stopping service")
                        stopService(intent)
                    }
                }
            }
        }

        //
        // Trace classification button
        findViewById<Button>(R.id.bTraceClassify).apply {
            setOnClickListener {
                startActivity(Intent(this@MainActivity, TraceAdaptation::class.java))
            }
        }

        //
        // Register BroadcastReceiver

        mBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.i(TAG, "broadcast received")

                intent ?: return

                Log.i(TAG, "broadcast received with intent")


                when (intent.action) {
                    BROADCAST_SOFTMAX -> {
                        val argMax = intent.getIntExtra("argMax", -1)
                        val usedConf = intent.getIntExtra("usedConf", -1)
                        updateClass(argMax)
                        updateConf(usedConf)
                    }
                }
            }
        }

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(mBroadcastReceiver, IntentFilter(BROADCAST_SOFTMAX))
    }

    override fun onDestroy() {
        super.onDestroy()

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver)
    }


    private fun updateClass(argMax: Int) {
        Log.i(TAG, "updateClass $argMax")

        findViewById<TextView>(R.id.argMax).text = activityName(argMax)
    }

    private fun updateConf(usedConf: Int) {
        Log.i(TAG, "updateConf $usedConf")

        findViewById<TextView>(R.id.usedConf).text = usedConf.toString()
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {

        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("native-lib")
        }

        const val TAG = "MainActivity"
        const val CHANNEL_ID = "Channel"

        const val BROADCAST_SOFTMAX = "Broadcast.softMax"
    }
}