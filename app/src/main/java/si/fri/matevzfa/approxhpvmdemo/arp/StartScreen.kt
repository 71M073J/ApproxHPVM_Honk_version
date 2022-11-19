package si.fri.matevzfa.approxhpvmdemo.arp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import si.fri.matevzfa.approxhpvmdemo.R


/**
 * A simple [Fragment] subclass.
 * Use the [StartScreen.newInstance] factory method to
 * create an instance of this fragment.
 */
class StartScreen : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_start_screen, container, false)
    }


}