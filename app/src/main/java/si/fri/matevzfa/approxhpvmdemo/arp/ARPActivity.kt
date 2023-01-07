package si.fri.matevzfa.approxhpvmdemo.arp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.jlibrosa.audio.JLibrosa
import dagger.hilt.android.AndroidEntryPoint
import si.fri.matevzfa.approxhpvmdemo.data.*
import si.fri.matevzfa.approxhpvmdemo.databinding.ActivityArpactivityBinding
import java.io.IOException
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.roundToInt


@AndroidEntryPoint
class ARPActivity : AppCompatActivity() {

    private val RECORDER_SAMPLERATE = 16000
    private val RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO
    private val RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_FLOAT
    private var restart_rec = false
    var BufferElements = 8000
    private var recorder: AudioRecord? = null
    private var mRecorder: MediaRecorder = MediaRecorder()
    private var recordingThread: Thread? = null
    private var peakThread: Thread? = null
    private var isRecording = false
    private var filter = Filter().filter
    private lateinit var binding: ActivityArpactivityBinding
    val MSG_UPDATE_TIME = 1
    val UPDATE_RATE_MS : Long = 1000
    @Inject
    lateinit var traceClassificationDao : ArpTraceClassificationDao

    private var speechCounter = 0
    private var numberOfReadings = 5 // Number of readings before calculating mean
    private var ampSum = 0.0
    private var indexOfCommand = 0

    private var selectedBatteryId = 0
    private var selectedAccuracyId = 0

    private var wordToSay = ""

    fun updateTable(softmax_str: String){
        val tableLayout = binding.tableSoftmax
        val percents = softmax_str.split(",")
        for (row in 0 until 4){
            val tRow = tableLayout.getChildAt(row) as TableRow
            for (column in 1 until 6 step 2){
                val commandView = tRow.getChildAt(column - 1) as TextView
                commandView.setTypeface(null, Typeface.NORMAL)
                var c = column - 1
                if (c > 0) { c-- }
                if (c == 3) { c-- }
                if (row + c * 4 == indexOfCommand){
                    runOnUiThread {
                        binding.WordToSay.text = commandView.text
                    }
                    wordToSay = commandView.text.toString()
                    commandView.setTypeface(null, Typeface.BOLD)
                }
                val percentView = tRow.getChildAt(column) as TextView
                val str = percents[(column / 2) * 4 + row].split(".")[1].substring(0,2) + "%"
                percentView.text = str
            }
        }
    }

    private val updateTimeHandler = object : Handler(Looper.getMainLooper()){
        override fun handleMessage(message: Message) {
            if (MSG_UPDATE_TIME == message.what) {
                //if we have a service, we update on loop
                binding.selected.text = ""
                val inferred = IntArray(labelNames.size)
                var updatedInferredWord = false
                traceClassificationDao.getLastTen()?.forEachIndexed { ix, data ->
                    if (ix == 9) {
                        if (data.usedEngine!!.contains("HARConfidenceAdaptation")) {
                            runOnUiThread {
                                binding.approxLevelConfidence.text =
                                    "Current approximation level from confidence adaptation: ${data.usedConfig!!}"
                            }
                        }
                        updateTable(data.confidenceConcat!!)
                    }
                    if (data.argMax != null) {
                        when(labelNames[data.argMax])  {
                            wordToSay -> {
                                binding.selected.text = labelNames[data.argMax]
                                updatedInferredWord = true
                            }
                            else -> inferred[data.argMax]++
                        }
                    }
                }
                if (!updatedInferredWord) {
                    binding.selected.text = labelNames[
                            inferred.indices.maxByOrNull { inferred[it] }?: 1
                    ]
                }
                sendEmptyMessageDelayed(MSG_UPDATE_TIME, UPDATE_RATE_MS)
            }
        }
    }
    @Inject
    lateinit var signalImageDao: SignalImageDao
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var a = 2

        isRecording = false
        binding = ActivityArpactivityBinding.inflate(layoutInflater)
        val view = binding.root

        binding.stopListening.visibility = View.INVISIBLE
        setContentView(view)
        binding.stopListening.setOnClickListener {
            Log.e("Stop Button", "CLICK")
            kill_recorder()
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            binding.startListening.visibility = View.VISIBLE
            binding.stopListening.visibility = View.INVISIBLE
        }
        binding.startListening.setOnClickListener {
            Log.e("Start Button", "CLICK")
            init_recorder()
            binding.startListening.visibility = View.INVISIBLE
            binding.stopListening.visibility = View.VISIBLE
        }

        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            val permissions = arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            ActivityCompat.requestPermissions(this, permissions, 0)
        }

        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        WorkManager.getInstance(baseContext)
            .getWorkInfosForUniqueWorkLiveData(UNIQUE_WORK_NAME)
            .observe(this) { workInfos ->
                for (info in workInfos) {
                    if (info.state.isFinished) {
                        a = 1
                    } else {
                        a = 0
                    }
                }
            }

    }

    override fun onPause() {
        super.onPause()
        restart_rec = isRecording
        kill_recorder()
    }

    private fun kill_recorder(){
        isRecording = false
        if (null != recorder) {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            recorder?.stop()
            recorder?.release()
            recorder = null
            recordingThread = null
            mRecorder.stop()
            mRecorder.release()
        }
    }

    private fun init_recorder(){
        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORDER_SAMPLERATE, RECORDER_CHANNELS,
            RECORDER_AUDIO_ENCODING, BufferElements
        )
        if (recorder!!.state != AudioRecord.STATE_INITIALIZED) { // check for proper initialization
            Log.e(TAG, "error initializing")
            return
        }
        recorder?.startRecording()
        isRecording = true

        mRecorder = MediaRecorder()
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        mRecorder.setOutputFile("${externalCacheDir?.absolutePath}/temp.3gp")

        try {
            mRecorder.prepare()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        mRecorder.start()

        updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)

        recordingThread = Thread({
            writeAudioDataToFile() }, "AudioRecorder Thread")
        recordingThread!!.start()
    }
    override fun onResume() {
        super.onResume()
        if (restart_rec or isRecording){
            init_recorder()
            restart_rec = false
        }
    }

    // Calculate Decibels and select approximation level if needed
    private fun calculateDb() {
        speechCounter++
        try {
            var currAmp = 20 * log10(mRecorder.maxAmplitude.toDouble())
            if (currAmp == Double.NEGATIVE_INFINITY || currAmp == Double.POSITIVE_INFINITY)
            { currAmp = 0.0 }
            ampSum += currAmp
            Log.d("Decibel level", "$speechCounter $ampSum $currAmp")

            val ampMean = ampSum / speechCounter
            runOnUiThread {
                binding.soundLevel.text = "Current sound level: ${currAmp.roundToInt()}dB"
                binding.avgSoundLevel.text = "Average sound level: ${ampMean.roundToInt()}dB"
            }

            if (speechCounter == numberOfReadings) {
                Log.d("Decibel mean value", "$ampMean")
                speechCounter = 0
                ampSum = 0.0

                indexOfCommand++
                if (indexOfCommand >= 12) { indexOfCommand = 0; binding.stopListening.callOnClick() }
            }
        } catch (e : Exception) {
            Log.e("Decibel calculation thread",
                "Exception thrown: probably media recorder was stopped while this thread" +
                        " was still running")
            e.printStackTrace()
        }

    }

    private fun writeAudioDataToFile() {
        val fDataFirst = FloatArray(BufferElements)
        val fDataSecond = FloatArray(BufferElements)

        val jLibrosa = JLibrosa()
        while (isRecording) {
            System.arraycopy(fDataSecond, 0, fDataFirst, 0, BufferElements)
            recorder!!.read(fDataSecond, 0, BufferElements, AudioRecord.READ_BLOCKING)

            peakThread = Thread({ calculateDb() }, "Decibel calculation Thread")
            peakThread!!.start()

            try {
                signalImageDao.deleteAll()
                val fData = FloatArray(BufferElements * 2)
                System.arraycopy(fDataFirst, 0, fData, 0, BufferElements)
                System.arraycopy(fDataSecond, 0, fData, BufferElements, BufferElements)

                var maxVal : Float = 0f

                fData.forEach { f ->
                    if (abs(f) > maxVal) { maxVal = abs(f) }
                }

                fData.forEachIndexed { i, f ->
                    fData[i] = f / maxVal
                }

                val melSpectrogram: Array<FloatArray> =
                    jLibrosa.generateMelSpectroGram(fData, 16000, 512, 40, 160)

                melSpectrogram.forEachIndexed { i, row ->
                    row.forEachIndexed { j, el ->
                        if (el > 0) { melSpectrogram[i][j] = ln(el) }
                    }
                }

                val multipliedData = Array(101){FloatArray(40)}
                for (i in multipliedData.indices){
                    for (j in filter.indices){
                        for (k in filter.indices){
                            multipliedData[i][j] += filter[j][k] * melSpectrogram[k][i]
                        }
                    }
                }

                val signalImage = FloatArray(101 * 40)
                //val test_data = Filter().test_value
                for (i in multipliedData.indices){//i gre do 100
                    for (j in multipliedData[i].indices){//j gre do 39
                        signalImage[(i) * 40 + (j)] = multipliedData[i][j]
                    }
                }
                val si = SignalImage(
                    uid = 0,
                    img = signalImage.joinToString(","),
                    wordToSay = wordToSay
                )
                signalImageDao.insertAll(si)
                val dataa = Data.Builder()
                    .putString("asd,a", "reeeee,eee")
                    .build()

                val work = OneTimeWorkRequestBuilder<MicrophoneInference>()
                    .setInputData(dataa)
                    .build()

                Log.e(TAG, "Queuening to work manager")

                WorkManager.getInstance(baseContext)
                    .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, work)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    companion object{
        private const val TAG = "ARPActivity"
        private val labelNames =  "silence,unknown,yes,no,up,down,left,right,on,off,stop,go".split(",")
        private const val UNIQUE_WORK_NAME = "trace-microphone"
    }
}