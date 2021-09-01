package si.fri.matevzfa.approxhpvmdemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class TraceAdaptation : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trace_adaptation)

        supportActionBar?.title = "Trace classification"

        val db = HARClassificationLogger.db(this)

        val data = db.classificationDao().getRunStarts()

        val adapter = Adapter(data)

        val recycler = findViewById<RecyclerView>(R.id.trace_list)
        recycler.adapter = adapter

        db.close()
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

        view.setOnClickListener { a: View -> }

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val date = HARClassificationLogger.dateTimeFormatter.parse(data[position])!!
        holder.textView.text =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(date)

        holder.cardView.setOnClickListener {
            Log.i(
                "CardView",
                "Clicked on ${data[position]}"
            )
        }

    }

    override fun getItemCount(): Int = data.size
}