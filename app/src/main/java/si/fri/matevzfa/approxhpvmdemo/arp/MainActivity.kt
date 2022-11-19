package si.fri.matevzfa.approxhpvmdemo.arp

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import si.fri.matevzfa.approxhpvmdemo.R
import si.fri.matevzfa.approxhpvmdemo.har.HARActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_main)


        findViewById<Button>(R.id.button_mobiprox).apply{
            // Set listener
            setOnClickListener {
                Intent(this@MainActivity, HARActivity::class.java).also { intent ->
                    Log.i(TAG, "Starting activity Mobiprox")
                    startActivity(intent)
                }
            }
        }
        findViewById<Button>(R.id.button_arp).apply{
            // Set listener
            setOnClickListener {
                Intent(
                    this@MainActivity,
                    ARPActivity::class.java
                ).also { intent ->
                    Log.i(HARActivity.TAG, "Starting activity ARP")
                    startActivity(intent)
                }
            }
        }
    }
    companion object {

        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("native-lib")
        }

        const val TAG = "NewMainActivity"
    }
}