package si.fri.matevzfa.approxhpvmdemo

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.*
import com.fri.matevzfa.approxhpvmdemo.R


class HARService : Service(), LifecycleOwner {

    private lateinit var lifecycleRegistry: LifecycleRegistry

    private lateinit var mApproxHVPMWrapper: ApproxHVPMWrapper
    private lateinit var mSensorSampler: SensorSampler

    private val TAG = "HARService"

    val ONGOING_NOTIFICATION_ID = 1112123

    override fun onCreate() {
        super.onCreate()

        lifecycleRegistry = LifecycleRegistry(this)

        // Init HPVM
        mApproxHVPMWrapper = ApproxHVPMWrapper()
        lifecycle.addObserver(mApproxHVPMWrapper)

        // Start sampling
        mSensorSampler = SensorSampler(this)
        lifecycle.addObserver(mSensorSampler)

        startForeground()

        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand")

        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")

        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startForeground() {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }

        val notification: Notification = Notification.Builder(this, MainActivity.CHANNEL_ID)
            .setContentTitle("HAR Service")
            .setContentText("Activity recognition is running.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setTicker("Ticker text")
            .build()

        startForeground(ONGOING_NOTIFICATION_ID, notification)
    }
}


internal class SensorSampler(context: Context) : SensorEventListener, LifecycleObserver {

    val TAG = "SensorSampler"

    private var mSensorManager: SensorManager

    private var mAccelerometer: Sensor
    private var mGyro: Sensor

    init {
        mSensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    private fun start() {
        fun register(sensor: Sensor) {
            mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        register(mAccelerometer)
        register(mGyro)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    private fun stop() {
        mSensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {

        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                Log.v(
                    TAG,
                    "accel x=${event.values[0]} y=${event.values[1]} z=${event.values[2]}"
                );
            }
            Sensor.TYPE_GYROSCOPE -> {
                Log.v(
                    TAG,
                    "gyro x=${event.values[0]} y=${event.values[1]} z=${event.values[2]}"
                );
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        sensor.also {
            Log.i(TAG, "Accuracy changed for ${sensor?.name}: $accuracy")
        }
    }
}
