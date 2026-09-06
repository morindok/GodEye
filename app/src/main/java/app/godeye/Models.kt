package app.godeye

import java.net.URI
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class ModelProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "مدل من",
    val endpoint: String = "https://api.openai.com/v1/chat/completions",
    val model: String = "",
    val apiKey: String = ""
)
object EndpointPolicy {
    fun validate(value: String): String? = try {
        val uri = URI(value)
        when {
            uri.scheme != "https" -> "نشانی باید با https:// شروع شود."
            uri.host.isNullOrBlank() -> "نشانی میزبان معتبر نیست."
            uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ->
                "نشانی نباید شامل رمز، پارامتر یا fragment باشد."
            uri.path.isNullOrBlank() || uri.path == "/" -> "مسیر کامل API را وارد کن؛ مانند /v1/chat/completions."
            else -> null
        }
    } catch (_: Exception) { "نشانی معتبر نیست." }
}
data class ReportSection(val title: String, val text: String)
data class VisionReport(val summary: String, val sections: List<ReportSection>)
object ReportParser {
    fun parse(raw: String): VisionReport {
        val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            val obj = JSONObject(text)
            val labels = listOf(
                "observations" to "۰۱ / شواهد قابل مشاهده",
                "relationships" to "۰۲ / روابط و الگوها",
                "hypotheses" to "۰۳ / فرضیه‌ها؛ نه واقعیت قطعی",
                "uncertainties" to "۰۴ / نادانسته‌ها و محدودیت‌ها",
                "next_steps" to "۰۵ / قدم بعدی برای بررسی"
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
            if (sections.isEmpty()) VisionReport("پاسخ مدل", listOf(ReportSection("تحلیل", raw)))
            else VisionReport(obj.optString("summary", "تحلیل تصویر"), sections)
        } catch (_: Exception) {
            VisionReport("پاسخ مدل", listOf(ReportSection("تحلیل بدون قالب ساختاریافته", raw)))
        }
    }
}
