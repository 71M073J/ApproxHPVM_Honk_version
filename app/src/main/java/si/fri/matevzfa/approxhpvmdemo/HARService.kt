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
import java.util.*
import kotlin.concurrent.fixedRateTimer


class HARService : Service(), LifecycleOwner {

    private lateinit var lifecycleRegistry: LifecycleRegistry

    private lateinit var mApproxHVPMWrapper: ApproxHVPMWrapper
    private lateinit var mSensorSampler: SensorSampler
    private lateinit var mTimer: Timer

    private val TAG = "HARService"

    val ONGOING_NOTIFICATION_ID = 1112123

    override fun onCreate() {
        super.onCreate()

        lifecycleRegistry = LifecycleRegistry(this)

        // Init HPVM
        mApproxHVPMWrapper = ApproxHVPMWrapper()
        lifecycle.addObserver(mApproxHVPMWrapper)

        // Init sampler
        mSensorSampler = SensorSampler(this)

        startForeground()
        startLoop()

        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand")

        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")

        mTimer.cancel()

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

    private fun startLoop() {
        mTimer = fixedRateTimer("sampling", true, period = 5000) {
            Log.i(TAG, "Would start sampling at this point")
            mSensorSampler.start()
        }
    }
}


internal class SensorSampler(context: Context) : SensorEventListener {

    val TAG = "SensorSampler"

    private var mSensorManager: SensorManager

    private var mAccelerometer: Sensor
    private var mGyro: Sensor

    private val NUM_READS = 32

    private var accelerometerIdx = 0
    private var accelerometerData = FloatArray(NUM_READS * 3)
    private var gyroIdx = 0
    private var gyroData = FloatArray(NUM_READS * 3)

    init {
        mSensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    fun start() {
        fun register(sensor: Sensor) {
            mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        }

        register(mAccelerometer)
        register(mGyro)
    }

    override fun onSensorChanged(event: SensorEvent?) {

        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                if (accelerometerIdx < accelerometerData.size) {
                    accelerometerData[accelerometerIdx++] = event.values[0]
                    accelerometerData[accelerometerIdx++] = event.values[1]
                    accelerometerData[accelerometerIdx++] = event.values[2]
                } else {
                    mSensorManager.unregisterListener(this, mAccelerometer)
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (gyroIdx < gyroData.size) {
                    gyroData[gyroIdx++] = event.values[0]
                    gyroData[gyroIdx++] = event.values[1]
                    gyroData[gyroIdx++] = event.values[2]
                } else {
                    mSensorManager.unregisterListener(this, mGyro)
                }
            }
        }

        if (accelerometerIdx >= accelerometerData.size && gyroIdx >= gyroData.size) {

            accelerometerIdx = 0;
            gyroIdx = 0;

            Log.d(TAG, "accelerometer ${dataToString(accelerometerData)}, ...")
            Log.d(TAG, "gyro ${dataToString(gyroData)}, ...")

            Log.i(TAG, "TODO: start a worker that will send data to HPVM")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        sensor.also {
            Log.d(TAG, "Accuracy changed for ${sensor?.name}: $accuracy")
        }
    }
}

private fun dataToString(values: FloatArray): String =
    values.take(9).map { "%.2f".format(it) }.joinToString(", ")
