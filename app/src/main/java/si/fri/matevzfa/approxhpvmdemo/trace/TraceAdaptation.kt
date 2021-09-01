package si.fri.matevzfa.approxhpvmdemo

import android.content.Context
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
import dagger.hilt.android.qualifiers.ApplicationContext
import si.fri.matevzfa.approxhpvmdemo.data.ClassificationDao
import si.fri.matevzfa.approxhpvmdemo.har.HARClassificationLogger
import si.fri.matevzfa.approxhpvmdemo.trace.TraceClassificationWork
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

        val data = classificationDao.getRunStarts()

        val adapter = Adapter(data)

        val recycler = findViewById<RecyclerView>(R.id.trace_list)
        recycler.adapter = adapter
    }
}

class Adapter(private val data: List<String?>) :
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

        val runStart = data[position]

        val date = HARClassificationLogger.dateTimeFormatter.parse(runStart)!!
        holder.textView.text =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(date)

        holder.cardView.setOnClickListener {
            Log.i(
                "CardView",
                "Clicked on ${runStart}"
            )

            val work = OneTimeWorkRequestBuilder<TraceClassificationWork>().setInputData(
                Data.Builder().apply {
                    putString(TraceClassificationWork.RUN_START, runStart)
                }.build()
            ).build()

            WorkManager.getInstance(ctx)
                .enqueueUniqueWork("trace-$runStart", ExistingWorkPolicy.REPLACE, work)
        }

    }

    override fun getItemCount(): Int = data.size
}
