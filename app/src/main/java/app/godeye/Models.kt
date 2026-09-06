package app.godeye

import java.net.URI
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class ModelProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "My model",
    val endpoint: String = "https://api.openai.com/v1/chat/completions",
    val model: String = "",
    val apiKey: String = ""
)
/** Verified image-analysis model presets. apiKey stays empty — the user supplies their own key. */
val VisionPresets: List<ModelProfile> = listOf(
    ModelProfile(name = "GPT-6 Astra Free (LLMTR)", endpoint = "https://llmtr.com/v1/chat/completions", model = "openai/gpt-6-astra-free"),
    ModelProfile(name = "StepFun 3.7 Flash (Bynara)", endpoint = "https://router.bynara.id/v1/chat/completions", model = "stepfun-3.7-flash"),
    ModelProfile(name = "Agnes 2.5 Flash (Bynara)", endpoint = "https://router.bynara.id/v1/chat/completions", model = "agnes-2.5-flash"),
    ModelProfile(name = "MiniMax M3 Free (Bynara)", endpoint = "https://router.bynara.id/v1/chat/completions", model = "minimax-m3-free"),
    ModelProfile(name = "Muse Spark 1.2 (Bynara)", endpoint = "https://router.bynara.id/v1/chat/completions", model = "muse-spark-1.2-contributor-free"),
    ModelProfile(name = "Ashna X1 (AshnaAI)", endpoint = "https://api.ashna.ai/v1/api/chat/completions", model = "ashna-x1")
)

object EndpointPolicy {
    private val loopbackHosts = setOf("localhost", "127.0.0.1", "[::1]", "0.0.0.0")
    fun validate(value: String): String? = try {
        val uri = URI(value)
        val isLoopback = uri.host != null && uri.host.lowercase() in loopbackHosts
        when {
            uri.scheme != "https" && !(isLoopback && uri.scheme == "http") ->
                "The endpoint must start with https:// (http:// is allowed for localhost only)."
            uri.host.isNullOrBlank() -> "The host address is invalid."
            uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ->
                "The URL must not contain credentials, query parameters, or fragments."
            uri.path.isNullOrBlank() || uri.path == "/" -> "Enter the full API path, e.g. /v1/chat/completions."
            else -> null
        }
    } catch (_: Exception) { "Invalid URL." }
}
data class ReportSection(val title: String, val text: String)
data class VisionReport(val summary: String, val sections: List<ReportSection>)
object ReportParser {
    fun parse(raw: String): VisionReport {
        val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            val obj = JSONObject(text)
            val labels = listOf(
                "observations" to "01 / Observable evidence",
                "relationships" to "02 / Relations and patterns",
                "hypotheses" to "03 / Hypotheses; not established facts",
                "uncertainties" to "04 / Unknowns and limitations",
                "next_steps" to "05 / Next check to run"
            )
            val sections = labels.mapNotNull { (key, label) ->
                val value = obj.opt(key)
                val body = when (value) {
                    null, JSONObject.NULL -> ""
                    is JSONArray -> (0 until value.length()).joinToString("\n") { "• " + value.optString(it) }
                    else -> value.toString()
                }
                if (body.isBlank()) null else ReportSection(label, body)
            }
            if (sections.isEmpty()) VisionReport("Model response", listOf(ReportSection("Analysis", raw)))
            else VisionReport(obj.optString("summary", "Image analysis"), sections)
        } catch (_: Exception) {
            VisionReport("Model response", listOf(ReportSection("Unstructured analysis", raw)))
        }
    }
}
