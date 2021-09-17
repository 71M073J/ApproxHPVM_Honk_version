package si.fri.matevzfa.approxhpvmdemo.trace

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import si.fri.matevzfa.approxhpvmdemo.R

@AndroidEntryPoint
class DataImportActivity : AppCompatActivity() {

    lateinit var mProgressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_import)

        supportActionBar?.title = "Data Import"

        mProgressBar = findViewById(R.id.dataImportProgress)

        findViewById<Button>(R.id.bSelectFile).apply {
            setOnClickListener {

                val work = OneTimeWorkRequestBuilder<DataImportWork>().addTag(TAG).build()

                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        "data-import",
                        ExistingWorkPolicy.KEEP,
                        work
                    )

                WorkManager.getInstance(context)
                    .getWorkInfosByTagLiveData(TAG)
                    .observe(this@DataImportActivity) {
                        for (info in it) {
                            when (info.state) {
                                WorkInfo.State.ENQUEUED -> {
                                    startSpinning()
                                }
                                WorkInfo.State.FAILED -> {
                                    stopSpinning()
                                    toast("Data import failed.")
                                }
                                WorkInfo.State.SUCCEEDED -> {
                                    stopSpinning()
                                    toast("Data import successful.")
                                }
                                else -> {
                                }
                            }
                        }
                    }
            }
        }
    }

    private fun startSpinning() {
        mProgressBar.visibility = View.VISIBLE
    }

    private fun stopSpinning() {
        mProgressBar.visibility = View.GONE
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG: String = "DataImportActivity"
    }
}
