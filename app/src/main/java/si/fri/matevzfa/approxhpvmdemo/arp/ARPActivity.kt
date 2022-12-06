package si.fri.matevzfa.approxhpvmdemo.arp

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import com.github.psambit9791.jdsp.filter.Butterworth
import com.jlibrosa.audio.JLibrosa
import dagger.hilt.android.AndroidEntryPoint
import si.fri.matevzfa.approxhpvmdemo.data.SignalImage
import si.fri.matevzfa.approxhpvmdemo.data.SignalImageDao
import si.fri.matevzfa.approxhpvmdemo.data.TraceClassificationDao
import si.fri.matevzfa.approxhpvmdemo.databinding.ActivityArpactivityBinding
import java.io.IOException
import java.util.*
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt


@AndroidEntryPoint
class ARPActivity : AppCompatActivity() {

    private val RECORDER_SAMPLERATE = 16000
    private val RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO
    private val RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_FLOAT

    var BufferElements = 16000
    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var peakThread: Thread? = null
    private var isRecording = false
    //private lateinit var stopListening :Button
    private var filter = Filter().filter;
    private lateinit var binding: ActivityArpactivityBinding
    val MSG_UPDATE_TIME = 1
    val UPDATE_RATE_MS = 1000L
    @Inject
    lateinit var traceClassificationDao :TraceClassificationDao

    private var speechCounter = 0
    private var rmseSum : Double = 0.0
    private var numberOfReadings = 10 // Number of readings before calculating mean

    private val updateTimeHandler = object : Handler(Looper.getMainLooper()){
        override fun handleMessage(message: Message) {
            if (MSG_UPDATE_TIME == message.what) {
                //if we have a service, we update on loop
                val data = traceClassificationDao.getLast()
                if (data != null){
                    binding.softmaxOutput.text = data.confidenceConcat
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
        Log.e(TAG,"wtf")
        binding.stopListening.setOnClickListener {
            Log.e("WTF", "CLICK")
            kill_recorder()
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            binding.startListening.visibility = View.VISIBLE
            binding.stopListening.visibility = View.INVISIBLE
        }
        binding.startListening.setOnClickListener {
            init_recorder()
            binding.startListening.visibility = View.INVISIBLE
            binding.stopListening.visibility = View.VISIBLE
        }


        Log.e(TAG,"wtf")
        //setContentView(R.layout.activity_arpactivity)

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


            //stopListening.setOnClickListener {
            //    isRecording = false
            //    recorder?.stop()
            //    stopListening.visibility = View.INVISIBLE
            //}

        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        Log.e(TAG,"wtf")
        //setContentView(R.layout.activity_arpactivity)

        Log.e(TAG,"wtf2")
        /*WorkManager.getInstance(baseContext)
            .getWorkInfosForUniqueWorkLiveData(UNIQUE_WORK_NAME)
            .observe(this) { workInfos ->
                for (info in workInfos) {
                    if (info.state.isFinished) {
                        a = 1
                    } else {
                        a = 0
                    }
                }
            }*/
    }

    override fun onPause() {
        super.onPause()
        kill_recorder()
    }
    fun kill_recorder(){
        isRecording = false;
        if (null != recorder) {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            //if (alreadyStarted){
            recorder?.stop();
            recorder?.release();
            //}
            recorder = null;
            recordingThread = null;
        }
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

        updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
        //
        recordingThread = Thread({
            //stopListening.visibility = View.VISIBLE
            writeAudioDataToFile() }, "AudioRecorder Thread")
        recordingThread!!.start()
    }
    override fun onResume() {
        super.onResume()
        if (isRecording){
            init_recorder()
        }
    }

    // Calculate rmse and select approximation level if needed
    private fun calculateRmse(fData: FloatArray) {
        val doubleArray = DoubleArray(fData.count())
        for (i in 0 until fData.count()) {
            doubleArray[i] = fData[i].toDouble()
        }

        val order = 4
        // Apparently this is the cutoff frequency to use for speech recognition
        val lowCutOffFreq = 300.0

        val butterworthFilter = Butterworth(RECORDER_SAMPLERATE.toDouble())
        val result: DoubleArray = butterworthFilter.lowPassFilter(doubleArray, order, lowCutOffFreq)
        var rmse = 0.0
        
        doubleArray.forEachIndexed{ i, value ->
            rmse += (value - result[i]).pow(2)
        }
        
        rmseSum += sqrt(rmse)
        speechCounter++
        Log.d("RMSE", "$rmse , $rmseSum, $speechCounter")

        if (speechCounter == numberOfReadings) {
            var rmseMean = rmseSum / speechCounter
            // TODO Select mean value for approximation levels
            //  test which means work for which approximation level
        }
    }

    private fun writeAudioDataToFile() {
        val fData = FloatArray(BufferElements)
        var jLibrosa: JLibrosa = JLibrosa();
        while (isRecording) {
            Thread.sleep(1_500)
            recorder!!.read(fData, 0, BufferElements, AudioRecord.READ_NON_BLOCKING)
            peakThread = Thread({ calculateRmse(fData) }, "Peak finder Thread")
            peakThread!!.start()
            try {
                signalImageDao.deleteAll()
                var melSpectrogram: Array<FloatArray> =
                    jLibrosa.generateMelSpectroGram(fData, 16000, 512, 40, 160)

                melSpectrogram.forEachIndexed { i, row ->
                    row.forEachIndexed { j, el ->
                        if (el > 0) { melSpectrogram[i][j] = ln(el) }
                    }
                }

                //Log.e(TAG, melSpectrogram.size.toString() + " - " + melSpectrogram[0].size.toString())
                //data = [np.matmul(self.dct_filters, np.atleast_2d(x).T) for x in a.T]
                //why is this even necessary???
                /*var data : Array<FloatArray> = emptyArray()
                for (i in melSpectrogram[0].indices) {
                    var l : FloatArray = floatArrayOf()
                    //var j = 0
                    for (j in melSpectrogram.indices) {
                        var m = melSpectrogram[j][i]
                        l = l.plus(melSpectrogram[j][i])
                    }
                    data = data.plus(l)
                }*/
                //101-krat 40 vektor, ta 40 vektor je 40 množenj
                //for (i in melSpectrogram){
                //    Log.e(TAG, i.joinToString(","))
                //}
                val multipliedData = Array(101){FloatArray(40)}
                for (i in multipliedData.indices){
                    for (j in filter.indices){
                        for (k in filter.indices){
                            multipliedData[i][j] += filter[j][k] * melSpectrogram[k][i]
                        }
                    }
                }


                /*var multipliedData = Array(filter.count()) { FloatArray(data[0].count()) }
                // Matrix multiplication with filter
                for (i in filter.indices) {
                    for (j in data[0].indices) {
                        for (k in filter[0].indices) {
                            multipliedData[i][j] += filter[i][k] * data[k][j]
                        }
                    }
                }*/


                var signalImage = FloatArray(101 * 40)
                var cnt = 0
                val test_data = Filter().test_value
                for (i in multipliedData.indices){//i gre do 100
                    for (j in multipliedData[i].indices){//j gre do 39
                        //Log.d("TAGAAAAAAAAAAAAAA", "$i-----------$j")
                        //signalImage[(39 - i) * 40 + (100 - j)] = multipliedData[i][j]
                        //signalImage[(39 - i) * 40 + (j)] = multipliedData[i][j]
                        //signalImage[(i) * 40 + (100 - j)] = multipliedData[i][j]
                        //signalImage[((i) * 40 + (j))] = multipliedData[i][j]
                        //signalImage[(39 - j) * 40 + (100 - i)] = multipliedData[i][j]
                        //signalImage[(39 - j) * 40 + (i)] = multipliedData[i][j]
                        //signalImage[(j) * 40 + (100 - i)] = multipliedData[i][j]
                        signalImage[(i) * 40 + (j)] = multipliedData[i][j]
                        //signalImage[(100 - i) * 40 + (39 - j)] = multipliedData[i][j]
                    }
                }
                //sleep(2000L)

                //for (i in test_data.indices){//i gre do 0 do 100
                //    for (j in test_data[i].indices){//j gre 0 do 39
                //        signalImage[(i) * 40 + (j)] = test_data[i][j]
                //    }
                //}

                //for (i in multipliedData){
                //    Log.e(TAG, i.joinToString(","))
                //}
                val si = SignalImage(
                    uid = 0,
                    img = signalImage.joinToString(",")
                )
                signalImageDao.insertAll(si)
                val dataa = Data.Builder()
                    .putString("asd,a", "reeeee,eee")
                    .build()

                val work = OneTimeWorkRequestBuilder<MicrophoneInference>()
                    .setInputData(dataa)
                    .build()

                //WorkManager.getInstance(baseContext)
                //    .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, work)

                //break

                //isRecording = false
                //val (softmax, argmax) = classify(signalImage)
                //out_view.text = labelNames[argmax]
                // data = [np.matmul(self.dct_filters, x) for x in np.split(data, data.shape[1], axis=1)]
                //var mulString = getString(multipliedData)
                //Log.d("TEST 1234214", Arrays.toString(fData))
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