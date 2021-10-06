package si.fri.matevzfa.approxhpvmdemo.trace

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import si.fri.matevzfa.approxhpvmdemo.R
import si.fri.matevzfa.approxhpvmdemo.data.ClassificationDao
import si.fri.matevzfa.approxhpvmdemo.data.ClassificationInfo
import si.fri.matevzfa.approxhpvmdemo.dateTimeFormatter
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@AndroidEntryPoint
class TraceAdaptation : AppCompatActivity() {

    @Inject
    lateinit var classificationDao: ClassificationDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trace_adaptation)

        supportActionBar?.title = "Trace classification"

        val data = classificationDao.getRunStartsWithClassifiedFlag()

        for (d in data) {
            Log.i(TAG, "$d")
        }

        val adapter = Adapter(data)

        val recycler = findViewById<RecyclerView>(R.id.trace_list)
        recycler.adapter = adapter
    }

    private class Adapter(private val data: List<ClassificationInfo>) :
        RecyclerView.Adapter<Adapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cardView: CardView = view.findViewById(R.id.card)
            val textView: TextView = cardView.findViewById(R.id.text)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.text_row_item, parent, false)

            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val ctx = holder.cardView.context

            val runStart = data[position].runStart
            val wasClassified = data[position].wasClassified

            val date = dateTimeFormatter.parse(runStart)!!
            holder.textView.text =
                """${if (wasClassified) "(Done) " else ""}${
                    DateTimeFormatter.ofLocalizedDateTime(
                        FormatStyle.MEDIUM
                    ).format(date)
                }"""

            if (!data[position].wasClassified) {
                holder.cardView.setOnClickListener {
                    Log.d(
                        "CardView",
                        "Clicked on $runStart"
                    )

                    val data = Data.Builder()
                        .putString(TraceClassificationWork.RUN_START, runStart)
                        .build()

                    val work = OneTimeWorkRequestBuilder<TraceClassificationWork>()
                        .setInputData(data)
                        .build()

                    WorkManager.getInstance(ctx)
                        .enqueueUniqueWork("trace-$runStart", ExistingWorkPolicy.REPLACE, work)
                }
            }

            holder.cardView.isClickable = !data[position].wasClassified
        }

        override fun getItemCount(): Int = data.size
    }

    companion object {
        const val TAG = "TraceAdaptation"
    }
}
