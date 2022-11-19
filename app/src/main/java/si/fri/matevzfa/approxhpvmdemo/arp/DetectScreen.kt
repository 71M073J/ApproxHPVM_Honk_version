package si.fri.matevzfa.approxhpvmdemo.arp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.jlibrosa.audio.JLibrosa
import java.io.IOException
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.work.*
import si.fri.matevzfa.approxhpvmdemo.R
import si.fri.matevzfa.approxhpvmdemo.adaptation.NoAdaptation
import si.fri.matevzfa.approxhpvmdemo.har.ApproxHPVMWrapper
import si.fri.matevzfa.approxhpvmdemo.trace.TraceAdaptation
import si.fri.matevzfa.approxhpvmdemo.trace.TraceClassificationWork
import java.util.Arrays
import kotlin.math.ln


/**
 * A simple [Fragment] subclass.
 * Use the [DetectScreen.newInstance] factory method to
 * create an instance of this fragment.
 */

class DetectScreen : Fragment() {
    // TODO Add actual microphone readings
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
        recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(32000)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(2 * 1000)
            .build()
         */

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment

        var a = 3
        WorkManager.getInstance(requireActivity().baseContext)
            .getWorkInfosForUniqueWorkLiveData("")
            .observe(viewLifecycleOwner) { workInfos ->
                for (info in workInfos) {
                    if (info.state.isFinished) {
                        //markFinished()

                        a = 0
                    } else {

                        a = 1
                        //markRunning()
                    }
                }
            }
        val view = inflater.inflate(R.layout.fragment_detect_screen, container, false)
        val list: ListView = view.findViewById(R.id.topRecognizedWords)
        // TODO make it so top results will be displayed here
        list.adapter = ArrayAdapter<String>(
            activity?.applicationContext!!,
            android.R.layout.simple_list_item_1,
            mutableListOf("one", "two", "three", "four")
        )
        return view
    }
    //TODO
    private fun markFinished() {
        //findViewById<View>(R.id.traceProgress).visibility = View.INVISIBLE
    }

    private fun markRunning() {
        //findViewById<View>(R.id.traceProgress).visibility = View.VISIBLE
    }

}
