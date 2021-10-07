package si.fri.matevzfa.approxhpvmdemo.trace

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import si.fri.matevzfa.approxhpvmdemo.data.TraceClassificationDao
import si.fri.matevzfa.approxhpvmdemo.dateTimeFormatter
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@AndroidEntryPoint
class TraceAdaptation : AppCompatActivity() {

    @Inject
    lateinit var classificationDao: ClassificationDao

    @Inject
    lateinit var traceClassificationDao: TraceClassificationDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trace_adaptation)

        supportActionBar?.title = "Trace classification"

        val adapter = Adapter(classificationDao, traceClassificationDao)

        val recycler = findViewById<RecyclerView>(R.id.trace_list)
        recycler.adapter = adapter

        markFinished()

        // Listen for work
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(UNIQUE_WORK_NAME)
            .observe(this) { workInfos ->
                for (info in workInfos) {
                    if (info.state.isFinished) {
                        markFinished()
                    } else {
                        markRunning()
                    }
                }
            }

        findViewById<View>(R.id.traceProgressButton).setOnClickListener {
            WorkManager.getInstance(this).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }

    private fun markFinished() {
        findViewById<View>(R.id.traceProgress).visibility = View.INVISIBLE
    }

    private fun markRunning() {
        findViewById<View>(R.id.traceProgress).visibility = View.VISIBLE
    }

    private class Adapter(
        private val classificationDao: ClassificationDao,
        private val traceClassificationDao: TraceClassificationDao
    ) : RecyclerView.Adapter<Adapter.ViewHolder>() {

        lateinit var data: List<ClassificationInfo>

        init {
            loadData()
        }

        fun loadData() {
            data = classificationDao.getRunStartsWithClassifiedFlag()
        }

        class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val cardView: CardView = view.findViewById(R.id.card)
            val textView: TextView = cardView.findViewById(R.id.text)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.text_row_item, parent, false)

            return ViewHolder(view)
        }

        @SuppressLint("SetTextI18n")
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


            holder.cardView.setOnClickListener {
                if (!data[position].wasClassified) {
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
                        .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, work)
                } else {

                    AlertDialog.Builder(holder.view.context)
                        .setTitle("Confirmation")
                        .setMessage("Delete trace with runStart $runStart?")
                        .setPositiveButton("Yes") { dialog, id ->
                            traceClassificationDao.deleteAllByRunStart(runStart)
                            Toast.makeText(
                                holder.view.context,
                                "Deleted all for runStart $runStart",
                                Toast.LENGTH_SHORT
                            ).show()

                            loadData()
                            notifyItemChanged(position)
                        }
                        .setNegativeButton("No", null)
                        .create()
                        .show()
                }
            }
        }

        override fun getItemCount(): Int = data.size
    }

    companion object {
        const val TAG = "TraceAdaptation"

        const val UNIQUE_WORK_NAME = "trace-adaptation"
    }
}
