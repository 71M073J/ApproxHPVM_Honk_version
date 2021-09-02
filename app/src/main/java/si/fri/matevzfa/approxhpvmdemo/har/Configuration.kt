package si.fri.matevzfa.approxhpvmdemo.har

import android.content.Context
import android.util.Log

data class Configuration(
    val id: Int,
    val qosLoss: Float,
    val overallConfidence: ConfidenceInfo,
    val perClassConfidence: List<ConfidenceInfo>,
) {
    data class ConfidenceInfo(
        val avg: Float,
        val correct: Float,
        val wrong: Float,
    )

    companion object {
        private const val TAG = "Configuration"

        fun loadConfigurations(ctx: Context): List<Configuration> =
            ctx.assets.open("models/mobilenet_uci-har/confs.txt").reader()
                .readLines()
                .filter { it.startsWith("conf") }
                .map { it.split(" ") }
                .map { s ->
                    Log.i(TAG, "$s")
                    var idx = 0
                    val id = s[idx++].replace("conf", "").toInt()

                    // speedup, energy, accuracy
                    idx += 3

                    Configuration(
                        id = id,
                        qosLoss = s[idx++].toFloat(),
                        overallConfidence = ConfidenceInfo(
                            s[idx++].toFloat(),
                            s[idx++].toFloat(),
                            s[idx++].toFloat()
                        ),
                        perClassConfidence = (idx..(s.size - 3) step 3).map { i ->
                            ConfidenceInfo(s[i].toFloat(), s[i + 1].toFloat(), s[i + 2].toFloat())
                        },
                    )
                }
    }
}
