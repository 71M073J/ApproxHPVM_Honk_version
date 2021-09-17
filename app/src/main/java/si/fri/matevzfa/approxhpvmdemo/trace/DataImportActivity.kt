package si.fri.matevzfa.approxhpvmdemo.trace

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import si.fri.matevzfa.approxhpvmdemo.R

class DataImportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_import)

        supportActionBar?.title = "Data Import"
    }
}
