package si.fri.matevzfa.approxhpvmdemo.har

import android.app.ActivityManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.HandlerThread
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.AndroidEntryPoint
import si.fri.matevzfa.approxhpvmdemo.R
import si.fri.matevzfa.approxhpvmdemo.activityName
import si.fri.matevzfa.approxhpvmdemo.adaptation.AdaptationEngine
import si.fri.matevzfa.approxhpvmdemo.adaptation.StateAdaptation
import si.fri.matevzfa.approxhpvmdemo.data.Classification
import si.fri.matevzfa.approxhpvmdemo.data.ClassificationDao
import si.fri.matevzfa.approxhpvmdemo.dateTimeFormatter
import java.time.Instant
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class HARService : Service(), TextToSpeech.OnInitListener {

    @Inject
    lateinit var mApproxHVPMWrapper: ApproxHPVMWrapper

    @Inject
    lateinit var classificationDao: ClassificationDao

    private lateinit var mHandlerThreadSensors: HandlerThread
    private lateinit var mSensorSampler: HARSensorSampler

    private lateinit var mHandlerThreadClassify: HandlerThread
    private lateinit var mClassifier: HARClassifierWithBaseline

    private lateinit var mAdaptationEngine: AdaptationEngine

    private var isTtsInit: Boolean = false
    private lateinit var tts: TextToSpeech

    private lateinit var startedAt: Instant

    /* Service */

    override fun onCreate() {
        super.onCreate()

        startedAt = Instant.now()

        // Init text-to-speech
        tts = TextToSpeech(applicationContext, this)

        // Init sampler
        mHandlerThreadSensors = HandlerThread("HARService.mHandlerThreadSensors").apply { start() }
        mSensorSampler = HARSensorSampler(
            this,
            mHandlerThreadSensors,
            HARSignalProcessor(),
            ::sensorSamplerCallback,
        )

        // mAdaptationEngine = StateAdaptation(mApproxHVPMWrapper)
        mAdaptationEngine = StateAdaptation(mApproxHVPMWrapper)

        // Init classification thread
        mHandlerThreadClassify =
            HandlerThread("HARService.mHandlerThreadClassify").apply { start() }
        mClassifier = HARClassifierWithBaseline(
            mApproxHVPMWrapper,
            mHandlerThreadClassify,
            ::classifierCallback,
        )

        startForeground()
        startSensing()
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

        tts.shutdown()
    }

    override fun onInit(status: Int) {
        isTtsInit = true
        tts.language = Locale.ENGLISH
    }

    /* Callbacks */

    /**
     * Called by [mSensorSampler] when enough data has been collected.
     */
    private fun sensorSamplerCallback(signalImage: FloatArray) {
        mAdaptationEngine.useFor {
            mClassifier.classify(signalImage)
        }
    }

    /**
     * Called by [mClassifier] after NN inference has completed.
     */
    private fun classifierCallback(
        softMax: FloatArray,
        softMaxBaseline: FloatArray,
        signalImage: FloatArray
    ) {
        Log.d(TAG, "Received SoftMax ${softMax.joinToString(", ")}")

        val argMax: Int = softMax.indices.maxByOrNull { softMax[it] } ?: -1
        val argMaxBaseline = softMaxBaseline.indices.maxByOrNull { softMaxBaseline[it] } ?: -1
        val usedConf: Int = mAdaptationEngine.configuration()

        Intent().also { intent ->
            intent.action = HARActivity.BROADCAST_SOFTMAX
            intent.putExtra("argMax", argMax)
            intent.putExtra("usedConf", usedConf)

            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }

        // Insert into db
        Classification(
            uid = 0,
            timestamp = dateTimeFormatter.format(Instant.now())!!,
            runStart = dateTimeFormatter.format(startedAt)!!,
            usedConfig = usedConf,
            argMax = argMax,
            argMaxBaseline = argMaxBaseline,
            confidenceConcat = softMax.joinToString(","),
            confidenceBaselineConcat = softMaxBaseline.joinToString(","),
            signalImage = signalImage.joinToString(","),
            usedEngine = mAdaptationEngine.name(),
        ).also {
            classificationDao.insertAll(it)
        }

        runTts(argMax, usedConf)

        mAdaptationEngine.actUpon(softMax, argMax)
    }

    /* Utility */

    /**
     * Runs text-to-speech of human activity and used approximation configuration
     */
    private fun runTts(argMax: Int, usedConf: Int) {
        val text = activityName(argMax)

        if (isTtsInit) {
            if (isTtsInit) {
                tts.speak("$text, $usedConf", TextToSpeech.QUEUE_FLUSH, null, "speechRequest")
            }
        }
    }

    /**
     * Starts the service as a foreground service with a permanent notification.
     */
    private fun startForeground() {
        val pendingIntent: PendingIntent =
            Intent(this, HARActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
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
        private const val TAG = "HARService"

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

        const val ONGOING_NOTIFICATION_ID = 1112123
    }
}
