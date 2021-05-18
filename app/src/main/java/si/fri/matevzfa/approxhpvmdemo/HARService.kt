package si.fri.matevzfa.approxhpvmdemo

import android.app.ActivityManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.fri.matevzfa.approxhpvmdemo.BuildConfig
import com.fri.matevzfa.approxhpvmdemo.R
import uk.me.berndporr.iirj.Butterworth
import uk.me.berndporr.iirj.Cascade
import java.lang.Integer.max
import java.lang.Integer.min


class HARService : Service(), LifecycleOwner {

    private lateinit var lifecycleRegistry: LifecycleRegistry

    private lateinit var mApproxHVPMWrapper: ApproxHVPMWrapper

    private lateinit var mHandlerThreadSensors: HandlerThread
    private lateinit var mSensorSampler: SensorSampler

    private lateinit var mHandlerThreadClassify: HandlerThread
    private lateinit var mClassifier: HARClassifier

    private val TAG = "HARService"

    val ONGOING_NOTIFICATION_ID = 1112123

    /* Service */

    override fun onCreate() {
        super.onCreate()

        lifecycleRegistry = LifecycleRegistry(this)

        // Init HPVM
        mApproxHVPMWrapper = ApproxHVPMWrapper()
        lifecycle.addObserver(mApproxHVPMWrapper)

        // Init sampler
        mHandlerThreadSensors = HandlerThread("HARService.mHandlerThreadSensors").apply {
            start()
        }
        mSensorSampler = SensorSampler(this, mHandlerThreadSensors) { signalImage ->
            mClassifier.classify(signalImage)
        }

        // Init classification thread
        mHandlerThreadClassify = HandlerThread("HARService.mHandlerThreadClassify").apply {
            start()
        }
        mClassifier = HARClassifier(this, mHandlerThreadClassify) { softMax ->
            Log.d(TAG, "Received SoftMax ${softMax.joinToString(", ")}")
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
                if (HARService::javaClass.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    }
}

internal class HARClassifier(
    val context: Context, handlerThread: HandlerThread,
    private val callback: (FloatArray) -> Unit = {}
) {
    val handler = Handler(handlerThread.looper)

    val numClasses = 6

    /**
     * Classify signal image given by [signalImage] and return SoftMax
     */
    fun classify(signalImage: FloatArray) {
        handler.post {
            // callback(hpvmClassify(signalImage))
            callback(FloatArray(numClasses))
        }
    }

    @Suppress("KotlinJniMissingFunction")
    private external fun hpvmClassify(values: FloatArray): FloatArray
}

/**
 * [DataContainer] serves for intermediate storage for sensor data.
 * It is used to read [numReads]  sensor readings with [numAxes] channels (e.g. x, y, z values of
 * gyroscope data).
 */
internal class DataContainer(val numReads: Int, val numAxes: Int) {

    private var idx: Int = 0
    private var data = FloatArray(numReads * numAxes)

    /**
     * Add [numAxes] values to the container
     *
     * @throws IndexOutOfBoundsException if [v] does not have at least [numAxes] elements, or if
     * more than [numReads] values are added
     */
    fun push(v: FloatArray, filter: Cascade? = null) {
        for (i in 0 until numAxes) {
            data[idx++] = filter?.filter(v[i].toDouble())?.toFloat() ?: v[i]
        }
    }

    fun isFull(): Boolean = idx >= data.size

    fun reset() {
        idx = 0
    }

    /**
     * Accessor method for [data] field of this data structure. Data is **NOT** copied.
     */
    fun dataRef(): FloatArray = data
}


internal class SensorSampler(
    context: Context,
    handlerThread: HandlerThread,
    private val callback: (FloatArray) -> Unit = {}
) :
    SensorEventListener {

    private val TAG = "SensorSampler"

    private var mSensorManager: SensorManager

    private val mHandler: Handler

    /* Sensors */
    private var mAccelerometer: Sensor
    private var mGyro: Sensor

    /* Sampling parameters */
    private val numReads = 128
    private val rate50Hz = 20_000

    /* Intermediate sensor data containers */
    private val accelContainer = DataContainer(numReads, 3)
    private val gyroContainer = DataContainer(numReads, 3)

    /* Butterworth filters for filtering sensor signals */
    private val filterAccel20HzX = lowPass20Hz(rate50Hz)
    private val filterAccel20HzY = lowPass20Hz(rate50Hz)
    private val filterAccel20HzZ = lowPass20Hz(rate50Hz)

    private val filterGyro20HzX = lowPass20Hz(rate50Hz)
    private val filterGyro20HzY = lowPass20Hz(rate50Hz)
    private val filterGyro20HzZ = lowPass20Hz(rate50Hz)

    private val filterAccel03HzX = lowPass03Hz(rate50Hz)
    private val filterAccel03HzY = lowPass03Hz(rate50Hz)
    private val filterAccel03HzZ = lowPass03Hz(rate50Hz)

    @Volatile
    var shouldStop = false

    init {
        mSensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        mHandler = Handler(handlerThread.looper)

        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    /**
     *  Registers [sensor] to [mSensorManager].
     */
    private fun register(sensor: Sensor) {
        mSensorManager.registerListener(this, sensor, rate50Hz, mHandler)
    }

    /**
     * Unregisters [sensor] from [mSensorManager]
     */
    private fun unregister(sensor: Sensor) {
        mSensorManager.unregisterListener(this, sensor)
    }

    /**
     * Registers all sensors to [mSensorManager]
     */
    fun startAll() {
        if (!shouldStop) {
            accelContainer.reset()
            gyroContainer.reset()

            register(mAccelerometer)
            register(mGyro)
        }
    }

    /**
     * This method is called each time a new sample is acquired from a registered sensor.
     *
     * When enough samples are acquired, [finalize] is called, after which data acquisition resumes.
     */
    override fun onSensorChanged(event: SensorEvent?) {

        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                if (!accelContainer.isFull()) {
                    accelContainer.push(event.values)
                } else {
                    unregister(mAccelerometer)
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (!gyroContainer.isFull()) {
                    gyroContainer.push(event.values)
                } else {
                    unregister(mGyro)
                }
            }
        }

        if (accelContainer.isFull() && gyroContainer.isFull()) {
            finalize()
            startAll()
        }
    }

    /**
     * Applies required pre-processing of sensor data and starts a worker for HAR classification.
     * Resets DataContainers after copying the data
     *
     * TODO: Actually start classifcation
     */
    private fun finalize() {
        if (BuildConfig.DEBUG && !accelContainer.isFull()) {
            error("Assertion failed")
        }
        if (BuildConfig.DEBUG && !gyroContainer.isFull()) {
            error("Assertion failed")
        }

        val totalAccelData = copyAndMedianFilter(accelContainer.dataRef(), 2)
        val gyroData = copyAndMedianFilter(gyroContainer.dataRef(), 2)

        // Data has been copied, reset the containers
        accelContainer.reset()
        gyroContainer.reset()

        // Convert to 'g's, because android uses m/s²
        for (i in totalAccelData.indices) {
            totalAccelData[i] /= 9.807f
        }

        // Prepare total_acc
        filterWith(totalAccelData, 0, filterAccel20HzX)
        filterWith(totalAccelData, 1, filterAccel20HzY)
        filterWith(totalAccelData, 2, filterAccel20HzZ)

        // Prepare body_acc
        val bodyAccelData = totalAccelData.copyOf()
        filterWith(bodyAccelData, 0, filterAccel03HzX)
        filterWith(bodyAccelData, 1, filterAccel03HzY)
        filterWith(bodyAccelData, 2, filterAccel03HzZ)
        for (i in 0 until bodyAccelData.size) {
            bodyAccelData[i] = totalAccelData[i] - bodyAccelData[i]
        }

        // Prepare gyro_acc
        filterWith(gyroData, 0, filterGyro20HzX)
        filterWith(gyroData, 1, filterGyro20HzY)
        filterWith(gyroData, 2, filterGyro20HzZ)

        Log.d(TAG, "signal image info - ")
        Log.d(TAG, "signal image info - ${printInfo("bodyAccelData", bodyAccelData)}")
        Log.d(TAG, "signal image info - ${printInfo("gyroData", gyroData)}")
        Log.d(TAG, "signal image info - ${printInfo("totalAccelData", totalAccelData)}")
        Log.d(TAG, "signal image info - ")

        // Prepare signal image
        val signalImage = FloatArray(3 * 32 * 32)
        fillSignalImageChannel(signalImage, 0, bodyAccelData)
        fillSignalImageChannel(signalImage, 1, gyroData)
        fillSignalImageChannel(signalImage, 2, totalAccelData)

        callback(signalImage)
    }

    private fun filterWith(data: FloatArray, axis: Int, filter: Cascade) {
        for (i in axis until data.size step 3) {
            data[i] = filter.filter(data[i].toDouble()).toFloat()
        }
    }

    /**
     * Fills [channel] of [out] with data given by [channelData].
     *
     * The data in each channel is represented as follows:
     *
     * ```
     * [
     *   8 rows for d in [x, y, z, y, x, z, x, y]:
     *      channel.d[0..32]
     *   8 rows for d in [x, y, z, y, x, z, x, y]:
     *      channel.d[32..64]
     *   8 rows for d in [x, y, z, y, x, z, x, y]:
     *      channel.d[64..96]
     *   8 rows for d in [x, y, z, y, x, z, x, y]:
     *      channel.d[96..128]
     * ]
     * ```
     */
    private fun fillSignalImageChannel(
        out: FloatArray,
        channel: Int,
        channelData: FloatArray
    ) {
        fun FloatArray.getAxis(i: Int, axis: Int) = this[3 * i + axis]

        val chanOffset = channel * 32 * 32

        // .................. x, y, z, y, x, z, x, y
        val outAxes = arrayOf(0, 1, 2, 1, 0, 2, 0, 1)

        for (row in 0 until 32) {
            for (col in 0 until 32) {
                val fold = row / 8
                val ax = outAxes[row % 8]
                out[chanOffset + row * 32 + col] = channelData.getAxis(fold * 32 + col, ax)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        sensor.also {
            Log.d(TAG, "Accuracy changed for ${sensor?.name}: $accuracy")
        }
    }
}

private fun printInfo(name: String, data: FloatArray): String {
    val min = data.minOrNull()
    val max = data.maxOrNull()
    val avg = data.sum() / data.size
    return "$name: dmin=$min dmax=$max davg=$avg"
}

private fun dataToString(values: FloatArray): String =
    values.take(9).map { "%.2f".format(it) }.joinToString(", ")

/**
 * Applies median filtering to [values], with specified |winSize]. [winSize] is interpreted as the
 * offset in each direction. Actual window size is computed by `2*winSize+1`.
 * For Out-of-bounds values, the nearest valid value is used.
 *
 * The [values] array is an array composed of values of all three axes:
 * `[x, y, z, x, y, z, ... x, y, z]`. The implementation filters each axis separately
 */
private fun copyAndMedianFilter(values: FloatArray, winSize: Int): FloatArray {
    val window = FloatArray(2 * winSize + 1)
    val out = FloatArray(values.size)

    for (axis in 0..2) {
        val axisMinIdx = axis
        val axisMaxIdx = values.size - 3 + axis
        for (i in axis until values.size step 3) {
            for (ir in 0 until (2 * winSize + 1)) {
                val idx = i - (winSize + ir) * 3
                val clippedIdx = min(axisMaxIdx, max(axisMinIdx, idx))
                window[ir] = values[clippedIdx]
            }
            window.sort()
            out[i] = window[winSize]
        }
    }

    return out
}

/**
 * Returns a low-pass Butterworth filter of 3rd order with cut-off frequency of 20Hz.
 * Sampling frequency [sampRateUs] must be given in microseconds.
 */
private fun lowPass20Hz(sampRateUs: Int) = Butterworth().apply {
    lowPass(3, 1_000_000.0 / sampRateUs, 20.0)
}

/**
 * Returns a low-pass Butterworth filter of 3rd order with cut-off frequency of 0.3Hz.
 * Sampling frequency [sampRateUs] must be given in microseconds.
 */
private fun lowPass03Hz(sampRateUs: Int) = Butterworth().apply {
    lowPass(3, 1_000_000.0 / sampRateUs, 0.3)
}
