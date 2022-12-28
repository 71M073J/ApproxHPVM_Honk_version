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
import si.fri.matevzfa.approxhpvmdemo.R
import si.fri.matevzfa.approxhpvmdemo.data.*
import si.fri.matevzfa.approxhpvmdemo.databinding.ActivityArpactivityBinding
import java.io.IOException
import java.util.*
import javax.inject.Inject
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
    //private lateinit var stopListening :Button
    private var filter = Filter().filter;
    private lateinit var binding: ActivityArpactivityBinding
    val MSG_UPDATE_TIME = 1
    val UPDATE_RATE_MS : Long = 1000
    @Inject
    lateinit var traceClassificationDao : ArpTraceClassificationDao

    private var speechCounter = 0
    private var numberOfReadings = 5 // Number of readings before calculating mean
    private var ampSum = 0.0
    private var approxLevel = 0
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
                val data = traceClassificationDao.getLast()
                if (data != null){
                    updateTable(data.confidenceConcat!!)
                    //binding.softmaxOutput.text = data.confidenceConcat
                    if (data.argMax != null){
                        binding.selected.text = labelNames[data.argMax]
                    }
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
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
            ActivityCompat.requestPermissions(this, permissions, 0)
        }

        val adapterBat =
            ArrayAdapter.createFromResource(this, R.array.battery, android.R.layout.simple_spinner_item)
        adapterBat.setDropDownViewResource(android.R.layout.simple_spinner_item)

        val spinnerBattery = findViewById<Spinner>(R.id.battery_dropdown)
        spinnerBattery.adapter = adapterBat

        spinnerBattery.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parentView: AdapterView<*>?,
                selectedItemView: View?,
                position: Int,
                id: Long
            ) {
                selectedBatteryId = position
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {
                selectedBatteryId = 0
            }
        }

        val adapterAcc =
            ArrayAdapter.createFromResource(this, R.array.accuracy, android.R.layout.simple_spinner_item)
        adapterAcc.setDropDownViewResource(android.R.layout.simple_spinner_item)

        val spinnerAcc = findViewById<Spinner>(R.id.AccuracyDropdown)
        spinnerAcc.adapter = adapterAcc

        spinnerAcc.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parentView: AdapterView<*>?,
                selectedItemView: View?,
                position: Int,
                id: Long
            ) {
                selectedAccuracyId = position
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {
                selectedAccuracyId = 0
            }
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

    fun kill_recorder(){
        isRecording = false;
        if (null != recorder) {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            recorder?.stop();
            recorder?.release();
            recorder = null;
            recordingThread = null;
        }
        //restart_rec = true
    }

    fun init_recorder(){
        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORDER_SAMPLERATE, RECORDER_CHANNELS,
            RECORDER_AUDIO_ENCODING, BufferElements
        )
        if (recorder!!.state != AudioRecord.STATE_INITIALIZED) { // check for proper initialization
            Log.e(TAG, "error initializing");
            return;
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
        mRecorder.start();

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

            var ampMean = ampSum / speechCounter
            runOnUiThread {
                binding.soundLevel.text = "Current sound level: ${currAmp.roundToInt()}dB"
                binding.avgSoundLevel.text = "Average sound level: ${ampMean.roundToInt()}dB"
            }

            if (speechCounter == numberOfReadings) {
                Log.d("Decibel mean value", "$ampMean")
                speechCounter = 0
                ampSum = 0.0

                // TODO add actual lookup table for approximation levels
                //  maybe as companion object or something
                approxLevel = 0 // This means no approximation. 3 means max approximation
                if (ampMean < 40) { approxLevel = 35 }
                else if (ampMean < 50) { approxLevel = 15 }
                else if (ampMean < 60) { approxLevel = 10 }

                runOnUiThread {
                    binding.approxLevelNoise.text =
                        "Current approximation level from noise policy adaptation: $approxLevel"
                }

                indexOfCommand++
                if (indexOfCommand >= 12) { indexOfCommand = 0; }
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
        var fDataSecond = FloatArray(BufferElements)

        var jLibrosa: JLibrosa = JLibrosa();
        while (isRecording) {
            isRecording = false
            System.arraycopy(fDataSecond, 0, fDataFirst, 0, BufferElements)
            recorder!!.read(fDataSecond, 0, BufferElements, AudioRecord.READ_BLOCKING)

            peakThread = Thread({ calculateDb() }, "Decibel calculation Thread")
            peakThread!!.start()

            try {
                signalImageDao.deleteAll()
                var fData = FloatArray(BufferElements * 2)
                System.arraycopy(fDataFirst, 0, fData, 0, BufferElements)
                System.arraycopy(fDataSecond, 0, fData, BufferElements, BufferElements)
                var melSpectrogram: Array<FloatArray> =
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

                var signalImage = FloatArray(101 * 40)
                //val test_data = Filter().test_value
                for (i in multipliedData.indices){//i gre do 100
                    for (j in multipliedData[i].indices){//j gre do 39
                        signalImage[(i) * 40 + (j)] = multipliedData[i][j]
                    }
                }
                val si = SignalImage(
                    uid = 0,
                    img = signalImage.joinToString(","),
                    approxnum = approxLevel,
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
    private fun getString(arr : Array<FloatArray>) : String {
        var str = "["
        arr.forEach { r ->
            str += Arrays.toString(r)
        }
        str += "]"
        return str
    }

    companion object{
        private const val TAG = "ARPActivity"
        private val labelNames =  "silence,unknown,yes,no,up,down,left,right,on,off,stop,go".split(",")
        private const val UNIQUE_WORK_NAME = "trace-microphone"
    }
}