package si.fri.matevzfa.approxhpvmdemo

import android.app.ActivityManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.localbroadcastmanager.content.LocalBroadcastManager


class HARService : Service(), LifecycleOwner {


    private lateinit var lifecycleRegistry: LifecycleRegistry

    private lateinit var mApproxHVPMWrapper: ApproxHPVMWrapper

    private lateinit var mHandlerThreadSensors: HandlerThread
    private lateinit var mSensorSampler: HARSensorSampler

    private lateinit var mHandlerThreadClassify: HandlerThread
    private lateinit var mClassifier: HARClassifier

    private val TAG = "HARService"

    val ONGOING_NOTIFICATION_ID = 1112123

    /* Service */

    override fun onCreate() {
        super.onCreate()

        lifecycleRegistry = LifecycleRegistry(this)

        // Init HPVM
        mApproxHVPMWrapper = ApproxHPVMWrapper(this)
        lifecycle.addObserver(mApproxHVPMWrapper)

        // Init sampler
        mHandlerThreadSensors = HandlerThread("HARService.mHandlerThreadSensors").apply {
            start()
        }
        mSensorSampler = HARSensorSampler(this, mHandlerThreadSensors) { signalImage ->
            mClassifier.classify(signalImage)
        }

        // Init classification thread
        mHandlerThreadClassify = HandlerThread("HARService.mHandlerThreadClassify").apply {
            start()
        }
        mClassifier = HARClassifier(mApproxHVPMWrapper, mHandlerThreadClassify) { softMax ->
            Log.d(TAG, "Received SoftMax ${softMax.joinToString(", ")}")

            val argMax: Int = softMax.indices.maxByOrNull { softMax[it] } ?: -1
            Intent().also { intent ->
                intent.setAction(MainActivity.BROADCAST_SOFTMAX);
                intent.putExtra("argMax", argMax)
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }

            // TODO:
            //  - use this data to guide approximations
        }


        startForeground()
        startSensing()

        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand")

        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")

        stopSensing()

        // Gracefully stop the HandlerThread after all enqueued work is completed
        mHandlerThreadSensors.quitSafely()
        mHandlerThreadClassify.quitSafely()

        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    /* LifecycleOwner */

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }

    /* Helpers */

    /**
     * Starts the service as a foreground service with a permanent notification.
     */
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

    /**
     * Starts the sampling service.
     */
    private fun startSensing() {
        mSensorSampler.startAll()

        Log.d(TAG, "Started sensing")
    }

    /**
     * Stops sensor data acquisition. Task already queued in [mHandlerThreadSensors] might continue
     * executing until they are completed.
     */
    private fun stopSensing() {
        // Notify mSensorSampler to stop re-registering sensors
        mSensorSampler.shouldStop = true

        Log.d(TAG, "Stopped sensing")
    }

    companion object {

        fun isRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                Log.i(
                    "HarService",
                    "${HARService::class.java.name} == ${service.service.className}"
                )
                if (HARService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    }
}
