package si.fri.matevzfa.approxhpvmdemo

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.fri.matevzfa.approxhpvmdemo.R


class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //
        // Example of a call to a native method
        findViewById<TextView>(R.id.sample_text).text = stringFromJNI()

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
            isChecked = isMyServiceRunning(HARService::class.java)

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
    }


    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}