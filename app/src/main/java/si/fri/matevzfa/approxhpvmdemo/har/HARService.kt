package si.fri.matevzfa.approxhpvmdemo.har

import android.app.ActivityManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.HandlerThread
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import si.fri.matevzfa.approxhpvmdemo.R
import si.fri.matevzfa.approxhpvmdemo.activityName
import si.fri.matevzfa.approxhpvmdemo.adaptation.AdaptationEngine
import si.fri.matevzfa.approxhpvmdemo.adaptation.KalmanAdaptation
import java.time.Instant
import java.util.*


class HARService : Service(), LifecycleOwner, TextToSpeech.OnInitListener {


    private lateinit var lifecycleRegistry: LifecycleRegistry

    private lateinit var mApproxHVPMWrapper: ApproxHPVMWrapper

    private lateinit var mHandlerThreadSensors: HandlerThread
    private lateinit var mSensorSampler: HARSensorSampler

    private lateinit var mHandlerThreadClassify: HandlerThread
    private lateinit var mClassifier: HARClassifierWithBaseline

    private lateinit var mAdaptationEngine: AdaptationEngine

    private lateinit var mHandlerThreadLogging: HandlerThread
    private lateinit var mHARClassificationLogger: HARClassificationLogger

    private var isTtsInit: Boolean = false
    private lateinit var tts: TextToSpeech

    private val TAG = "HARService"

    val ONGOING_NOTIFICATION_ID = 1112123

    /* Service */

    override fun onCreate() {
        super.onCreate()

        lifecycleRegistry = LifecycleRegistry(this)

        // Init text-to-speech
        tts = TextToSpeech(applicationContext, this)

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

        // mAdaptationEngine = StateAdaptation(mApproxHVPMWrapper)
        mAdaptationEngine = KalmanAdaptation(mApproxHVPMWrapper)

        // Init classification thread
        mHandlerThreadClassify = HandlerThread("HARService.mHandlerThreadClassify").apply {
            start()
        }
        mClassifier = HARClassifierWithBaseline(
            mApproxHVPMWrapper,
            mHandlerThreadClassify
        ) { softMax, softMaxBaseline, signalImage ->
            Log.d(TAG, "Received SoftMax ${softMax.joinToString(", ")}")

            val argMax: Int = softMax.indices.maxByOrNull { softMax[it] } ?: -1
            val argMaxBaseline = softMaxBaseline.indices.maxByOrNull { softMaxBaseline[it] } ?: -1
            val usedConf: Int = mAdaptationEngine.configuration();

            Intent().also { intent ->
                intent.action = HARActivity.BROADCAST_SOFTMAX;
                intent.putExtra("argMax", argMax)
                intent.putExtra("usedConf", usedConf);

                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }

            Intent().also { intent ->
                intent.action = HARClassificationLogger.ACTION
                intent.putExtra(HARClassificationLogger.USED_CONF, usedConf)
                intent.putExtra(HARClassificationLogger.ARGMAX, argMax)
                intent.putExtra(HARClassificationLogger.ARGMAX_BASELINE, argMaxBaseline)
                intent.putExtra(HARClassificationLogger.CONFIDENCE, softMax)
                intent.putExtra(HARClassificationLogger.CONFIDENCE_BASELINE, softMaxBaseline)
                intent.putExtra(HARClassificationLogger.SIGNAL_IMAGE, signalImage)
                intent.putExtra(HARClassificationLogger.USED_ENGINE, mAdaptationEngine.name())

                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }

            runTts(argMax, usedConf)

            mAdaptationEngine.actUpon(softMax, argMax)
        }

        // Init classification logger
        mHARClassificationLogger = HARClassificationLogger(Instant.now())
        LocalBroadcastManager.getInstance(this).registerReceiver(
            mHARClassificationLogger,
            IntentFilter(HARClassificationLogger.ACTION)
        )

        startForeground()
        startSensing()

        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    private fun runTts(argMax: Int, usedConf: Int) {
        val text = activityName(argMax)

        if (isTtsInit) {
            if (isTtsInit) {
                tts.speak("$text, $usedConf", TextToSpeech.QUEUE_FLUSH, null, "speechRequest")
            }
        }
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

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mHARClassificationLogger)

        stopSensing()

        // Gracefully stop the HandlerThread after all enqueued work is completed
        mHandlerThreadSensors.quitSafely()
        mHandlerThreadClassify.quitSafely()

        tts.shutdown()

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
            Intent(this, HARActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }

        val notification: Notification = Notification.Builder(this, HARActivity.CHANNEL_ID)
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

    override fun onInit(status: Int) {
        isTtsInit = true
        tts.language = Locale.ENGLISH
    }
}
