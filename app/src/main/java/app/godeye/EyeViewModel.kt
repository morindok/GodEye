package app.godeye

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UiState(
    val profiles: List<ModelProfile> = emptyList(), val selectedId: String? = null,
    val busy: Boolean = false, val report: VisionReport? = null,
    val error: String? = null, val completedAt: Long? = null,
    val reportModel: String = ""
) { val active: ModelProfile? get() = profiles.firstOrNull { it.id == selectedId } }
class EyeViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ProfileStore(application)
    private val api = VisionClient()
    var state by mutableStateOf(UiState())
        private set
    private var request: Job? = null
    private var generation = 0
    init {
        application.cacheDir.listFiles()?.filter { it.name.startsWith("eye_") }?.forEach { it.delete() }
        try {
            val profiles = store.load()
            state = state.copy(profiles = profiles, selectedId = profiles.firstOrNull()?.id)
        } catch (_: Exception) { state = state.copy(error = "پروفایل‌های ذخیره‌شده باز نشدند؛ دوباره تنظیم کن.") }
    }
    fun save(profile: ModelProfile): Boolean {
        EndpointPolicy.validate(profile.endpoint)?.let { error(it); return false }
        if (profile.model.isBlank() || profile.name.isBlank()) { error("نام پروفایل و شناسهٔ مدل لازم است."); return false }
        if (profile.apiKey.any { it.code !in 32..126 }) { error("کلید API باید تک‌خطی و بدون نویسهٔ نامعتبر باشد."); return false }
        val list = state.profiles.filterNot { it.id == profile.id } + profile
        return try {
            store.save(list); state = state.copy(profiles = list, selectedId = profile.id, error = null); true
        } catch (_: Exception) { error("ذخیرهٔ امن کلید انجام نشد؛ هیچ کلیدی به‌صورت ساده ذخیره نشد."); false }
    }
    fun delete(id: String) {
        val list = state.profiles.filterNot { it.id == id }
        try {
            store.save(list)
            state = state.copy(profiles = list, selectedId = if (state.selectedId == id) list.firstOrNull()?.id else state.selectedId, error = null)
        } catch (_: Exception) { error("حذف پروفایل انجام نشد.") }
    }
    fun select(id: String) { state = state.copy(selectedId = id, error = null) }
    fun error(message: String?) { state = state.copy(error = message) }
    fun beginCapture(): Int? {
        if (state.busy) return null
        if (state.active == null) { error("ابتدا یک مدل بینایی تعریف کن."); return null }
        generation += 1
        state = state.copy(busy = true, error = null)
        return generation
    }
    fun captureFailed(token: Int) {
        if (token == generation) state = state.copy(busy = false, error = "ثبت تصویر انجام نشد؛ دوربین را بررسی کن.")
    }
    fun analyze(file: File, token: Int, question: String) {
        val profile = state.active
        if (token != generation || !state.busy || profile == null) { file.delete(); return }
        request = viewModelScope.launch {
            try {
                val image = withContext(Dispatchers.IO) { ImageTools.jpeg(file) }
                file.delete()
                val result = api.analyze(profile, image, question)
                if (token == generation) state = state.copy(report = result, completedAt = System.currentTimeMillis(), reportModel = profile.name)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (token == generation) error(e.message ?: "تحلیل انجام نشد.")
            } finally {
                file.delete()
                if (token == generation) state = state.copy(busy = false)
            }
        }
    }
    fun stop() {
        generation += 1
        request?.cancel(); request = null
        state = state.copy(busy = false)
    }
    fun clearReport() { state = state.copy(report = null, completedAt = null, reportModel = "") }
}
