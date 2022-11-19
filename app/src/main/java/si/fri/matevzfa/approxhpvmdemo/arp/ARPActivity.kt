package si.fri.matevzfa.approxhpvmdemo.arp

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.jlibrosa.audio.JLibrosa
import dagger.hilt.android.AndroidEntryPoint
import si.fri.matevzfa.approxhpvmdemo.R
import si.fri.matevzfa.approxhpvmdemo.adaptation.NoAdaptation
import si.fri.matevzfa.approxhpvmdemo.data.ClassificationDao
import si.fri.matevzfa.approxhpvmdemo.data.SignalImage
import si.fri.matevzfa.approxhpvmdemo.data.SignalImageDao
import si.fri.matevzfa.approxhpvmdemo.data.TraceClassification
import si.fri.matevzfa.approxhpvmdemo.har.ApproxHPVMWrapper
import java.io.IOException
import java.util.*
import javax.inject.Inject
import kotlin.math.ln

@AndroidEntryPoint
class ARPActivity : AppCompatActivity() {

    private val RECORDER_SAMPLERATE = 16000
    private val RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO
    private val RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_FLOAT

    var BufferElements = 16000
    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false
    //private lateinit var stopListening :Button
    private var filter = Filter().filter;

    @Inject
    lateinit var signalImageDao: SignalImageDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var a = 2

        Log.e(TAG,"wtf")



        Log.e(TAG,"wtf")
        setContentView(R.layout.activity_arpactivity)
        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORDER_SAMPLERATE, RECORDER_CHANNELS,
            RECORDER_AUDIO_ENCODING, BufferElements
        )
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            val permissions = arrayOf(
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
            ActivityCompat.requestPermissions(this, permissions, 0)
        } else {
            recorder?.startRecording()
            isRecording = true
            //
            recordingThread = Thread({
                //stopListening.visibility = View.VISIBLE
                writeAudioDataToFile() }, "AudioRecorder Thread")
            recordingThread!!.start()
            //stopListening.setOnClickListener {
            //    isRecording = false
            //    recorder?.stop()
            //    stopListening.visibility = View.INVISIBLE
            //}
        }
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        Log.e(TAG,"wtf")
        setContentView(R.layout.activity_arpactivity)

        Log.e(TAG,"wtf2")
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
        setContentView(R.layout.activity_arpactivity)


    }

    override fun onPause() {
        super.onPause()
        if (null != recorder) {
            isRecording = false;
            recorder?.stop();
            recorder?.release();
            recorder = null;
            recordingThread = null;
        }
    }

    private fun writeAudioDataToFile() {
        val fData = FloatArray(BufferElements)
        var jLibrosa: JLibrosa = JLibrosa();
        while (isRecording) {
            recorder!!.read(fData, 0, BufferElements, AudioRecord.READ_BLOCKING)
            //System.out.println("Short writing to file $fData")
            try {
                signalImageDao.deleteAll()
                var melSpectrogram: Array<FloatArray> =
                    jLibrosa.generateMelSpectroGram(fData, 16000, 512, 40, 160)

                //var melString = getString(melSpectrogram)

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
                for (i in multipliedData.indices){
                    for (j in multipliedData[i].indices){
                        //signalImage[(39 - j) * 40 + (100 - i)] = multipliedData[i][j]
                        //signalImage[(39 - j) * 40 + (i)] = multipliedData[i][j]
                        //signalImage[(j) * 40 + (100 - i)] = multipliedData[i][j]
                        signalImage[(j) * 40 + (i)] = multipliedData[i][j]
                        //signalImage[(39 - i) * 40 + (100 - j)] = multipliedData[i][j]
                        //signalImage[(39 - i) * 40 + (j)] = multipliedData[i][j]
                        //signalImage[(i) * 40 + (100 - j)] = multipliedData[i][j]
                        //signalImage[(i) * 40 + (j)] = multipliedData[i][j]
                        cnt += 12
                    }
                }
                for (i in multipliedData){
                    Log.e(TAG, i.joinToString(","))
                }
                val si = SignalImage(
                    uid = 0,
                    img = signalImage.joinToString(",")
                )

                signalImageDao.insertAll(si)
                val dataa = Data.Builder()
                    .putString("asda", "reeeeeeee")
                    .build()

                val work = OneTimeWorkRequestBuilder<MicrophoneInference>()
                    .setInputData(dataa)
                    .build()

                WorkManager.getInstance(baseContext)
                    .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, work)

                break
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