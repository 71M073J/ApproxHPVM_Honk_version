package si.fri.matevzfa.approxhpvmdemo.har

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

class HARSensorSampler(
    context: Context,
    handlerThread: HandlerThread,
    private val signalProcessor: HARSignalProcessor,
    private val callback: (FloatArray) -> Unit = {}
) : SensorEventListener {

    private val mHandler: Handler = Handler(handlerThread.looper)

    private val mSensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /* Sensors */
    private var mAccelerometer: Sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var mGyro: Sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    @Volatile
    var shouldStop = false

    /**
     *  Registers [sensor] to [mSensorManager].
     */
    private fun register(sensor: Sensor) {
        mSensorManager.registerListener(this, sensor, signalProcessor.rate50Hz, mHandler)
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
            signalProcessor.reset()

            register(mAccelerometer)
            register(mGyro)
        }
    }

    /**
     * This method is called each time a new sample is acquired from a registered sensor.
     *
     * When enough samples are acquired, [callback] is called with the signal image
     */
    override fun onSensorChanged(event: SensorEvent?) {

        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                if (signalProcessor.needsAccData()) {
                    signalProcessor.addAccData(
                        HARSignalProcessor.SensorData(
                            event.values[0],
                            event.values[1],
                            event.values[2]
                        )
                    )
                } else {
                    unregister(mAccelerometer)
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (signalProcessor.needsGyrData()) {
                    signalProcessor.addGyrData(
                        HARSignalProcessor.SensorData(
                            event.values[0],
                            event.values[1],
                            event.values[2]
                        )
                    )
                } else {
                    unregister(mGyro)
                }
            }
        }

        if (signalProcessor.isFull()) {
            callback(signalProcessor.signalImage())
            startAll()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        sensor.also {
            Log.d(TAG, "Accuracy changed for ${sensor?.name}: $accuracy")
        }
    }

    companion object {
        private const val TAG = "SensorSampler"
    }
}
