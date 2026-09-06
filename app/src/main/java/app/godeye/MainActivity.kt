package app.godeye

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF080D16)
private val Panel = Color(0xFF121D2B)
private val Blue = Color(0xFF9CCAFF)
private val Muted = Color(0xFFB3C0D1)
private val Line = Color(0xFF36495F)
private val EyeScheme = darkColorScheme(primary = Blue, onPrimary = Ink, background = Ink,
    surface = Panel, onSurface = Color(0xFFF0F5FC), onSurfaceVariant = Muted,
    outline = Line, secondary = Blue, error = Color(0xFFFFB4AB))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = EyeScheme) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = Ink) { GodEyeApp() }
                }
            }
        }
    }
}

// Camera operations below are guarded by runtime permission and foreground lifecycle checks.
@SuppressLint("MissingPermission")
@Composable
private fun GodEyeApp(vm: EyeViewModel = viewModel()) {
    val state = vm.state
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var page by rememberSaveable { mutableStateOf(0) }
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var live by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf<String?>(null) }
    var question by rememberSaveable { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { live = false; consent = null; vm.stop() }
            if (event == Lifecycle.Event.ON_RESUME) {
                permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); vm.stop() }
    }
    val takeShot: () -> Unit = shot@{
        if (!lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@shot
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permission = false; live = false; vm.stop(); return@shot
        }
        val camera = capture ?: return@shot
        val token = vm.beginCapture() ?: return@shot
        val file = try { File.createTempFile("eye_", ".jpg", context.cacheDir) }
            catch (_: Exception) { vm.captureFailed(token); return@shot }
        try {
            camera.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) { vm.analyze(file, token, question) }
                    override fun onError(exception: ImageCaptureException) { file.delete(); vm.captureFailed(token) }
                })
        } catch (_: Exception) { file.delete(); vm.captureFailed(token) }
    }
    val latestShot by rememberUpdatedState(takeShot)
    LaunchedEffect(live, capture) {
        if (live && capture != null) {
            while (true) {
                if (!vm.state.busy) latestShot()
                delay(15_000)
            }
        }
    }
    LaunchedEffect(state.error) { if (state.error != null) live = false }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            EyeMark(Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("GOD EYE", fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Text("See. Analyze. Never trust blindly.", fontSize = 14.sp, color = Muted)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Eye", "Models", "Privacy").forEachIndexed { index, title ->
                FilterChip(selected = page == index, onClick = {
                    if (page != index) { live = false; consent = null; vm.stop(); page = index }
                }, label = { Text(title) })
            }
        }
        state.error?.let { message ->
            Surface(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(8.dp), color = Color(0xFF3B2027)) {
                Column(Modifier.padding(12.dp)) {
                    Text(message, color = Color(0xFFFFDAD6))
                    TextButton(onClick = { vm.error(null) }) { Text("Close", color = Color(0xFFFFDAD6)) }
                }
            }
        }
        when (page) {
            0 -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (live) "● Periodic monitoring active" else "○ Auto-send off", color = if (live) Blue else Muted, fontSize = 14.sp)
                        Text(state.active?.name ?: "No model configured", color = Muted, fontSize = 14.sp)
                    }
                }
                item {
                    Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(12.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(12.dp))) {
                        if (permission) {
                            CameraFeed(onReady = { capture = it }, onError = { vm.error(it) })
                            Reticle(Modifier.matchParentSize())
                            Text("Local preview • sent only during analysis",
                                modifier = Modifier.align(Alignment.BottomCenter).background(Ink.copy(alpha = .88f)).fillMaxWidth().padding(12.dp),
                                color = Color.White, fontSize = 14.sp)
                        } else {
                            Column(Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                EyeMark(Modifier.size(48.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("The eye is ready to open", fontSize = 20.sp)
                                Text("Camera permission is required to see the scene.", color = Muted, modifier = Modifier.padding(vertical = 12.dp))
                                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Grant camera permission") }
                                TextButton(onClick = {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
                                }) { Text("App permission settings") }
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(value = question, onValueChange = { if (it.length <= 1000) question = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("What should I look for?") },
                        placeholder = { Text("e.g. What details of this scene am I missing?") }, maxLines = 3, enabled = !state.busy && !live)
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { consent = "single" }, enabled = capture != null && state.active != null && !state.busy && !live,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Analyze this moment") }
                        OutlinedButton(onClick = { if (live || state.busy) { live = false; vm.stop() } else consent = "live" },
                            enabled = state.busy || live || (capture != null && state.active != null), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(if (live || state.busy) "Stop" else "15s monitoring")
                        }
                    }
                    if (state.busy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                        Text("Capturing / sending / analyzing…", color = Blue, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (state.active == null) TextButton(onClick = { page = 1 }) { Text("← Add your first vision model") }
                }
                item {
                    Text("Analysis is probabilistic — it does not reveal hidden truths, identities, or thoughts. Each image may incur API cost.", color = Muted, fontSize = 14.sp)
                }
                val report = state.report
                if (report == null) {
                    item {
                        Surface(shape = RoundedCornerShape(12.dp), color = Panel) {
                            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                                Text("Beyond first glance; never beyond evidence", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(8.dp))
                                Text("Reports come in five layers: evidence, relations, hypotheses, unknowns, and follow-up checks.", color = Muted)
                            }
                        }
                    }
                } else {
                    item {
                        Column {
                            Text("Latest analysis", color = Blue, fontSize = 14.sp)
                            Text(report.summary, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                            val time = state.completedAt?.let { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it)) }.orEmpty()
                            Text("${state.reportModel} • $time • refers to the captured photo, not a live feed", color = Muted, fontSize = 14.sp)
                        }
                    }
                    items(report.sections) { section ->
                        Surface(shape = RoundedCornerShape(12.dp), color = Panel) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(section.title, color = Blue, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text(section.text, lineHeight = 28.sp)
                            }
                        }
                    }
                    item { TextButton(onClick = vm::clearReport) { Text("Clear analysis from this session") } }
                }
            }
            1 -> ProfileScreen(vm, Modifier.weight(1f))
            else -> PrivacyScreen(Modifier.weight(1f))
        }
    }
    consent?.let { mode ->
        AlertDialog(onDismissRequest = { consent = null }, title = { Text("Permission to send image") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (mode == "live") "Every 15 seconds — only once the previous request finishes — one photo is sent; this is not continuous video. It continues until you stop it or leave the app."
                        else "One photo from the camera will be sent for analysis.")
                    Text("Recipient: ${state.active?.name}\n${state.active?.endpoint}", color = Blue)
                    Text("This may incur cost. Image retention is governed by the provider. Only capture scenes you are allowed to send. Stop cannot recall data already sent.")
                }
            }, confirmButton = { TextButton(onClick = {
                consent = null
                if (mode == "live") live = true else takeShot()
            }) { Text("I agree — start") } }, dismissButton = { TextButton(onClick = { consent = null }) { Text("Cancel") } })
    }
}

// Composed only while CAMERA permission is granted by the parent screen.
@SuppressLint("MissingPermission")
@Composable
private fun CameraFeed(onReady: (ImageCapture?) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val ready by rememberUpdatedState(onReady)
    val fail by rememberUpdatedState(onError)
    val view = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER } }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
    DisposableEffect(owner, view) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var photo: ImageCapture? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!disposed) {
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    val rotation = view.display?.rotation ?: android.view.Surface.ROTATION_0
                    val p = Preview.Builder().setTargetRotation(rotation).build().also { it.setSurfaceProvider(view.surfaceProvider) }
                    val c = ImageCapture.Builder().setTargetRotation(rotation).setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                    preview = p; photo = c
                    val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                    cameraProvider.bindToLifecycle(owner, selector, p, c)
                    ready(c)
                } catch (_: Exception) { ready(null); fail("Camera failed to open; check permission and camera availability.") }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true; ready(null)
            preview?.let { provider?.unbind(it) }; photo?.let { provider?.unbind(it) }
        }
    }
}

@Composable
private fun EyeMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val path = Path().apply { moveTo(w*.08f, h*.5f); quadraticBezierTo(w*.5f, h*.03f, w*.92f, h*.5f); quadraticBezierTo(w*.5f,h*.97f,w*.08f,h*.5f); close() }
        drawPath(path, Blue, style = Stroke(2.dp.toPx()))
        drawCircle(Blue, w*.17f, Offset(w*.5f,h*.5f), style = Stroke(2.dp.toPx()))
        drawCircle(Blue, w*.045f, Offset(w*.5f,h*.5f))
    }
}
@Composable
private fun Reticle(modifier: Modifier) {
    Canvas(modifier) {
        val x = size.width*.13f; val y = size.height*.14f
        val right = size.width-x; val bottom = size.height*.78f
        val length = 24.dp.toPx(); val stroke = 2.dp.toPx()
        listOf(Triple(x,y,1f), Triple(right,y,-1f), Triple(x,bottom,1f), Triple(right,bottom,-1f)).forEachIndexed { i, (cx,cy,dir) ->
            drawLine(Blue, Offset(cx,cy), Offset(cx+length*dir,cy),stroke)
            drawLine(Blue, Offset(cx,cy), Offset(cx,cy+length*(if(i<2)1f else -1f)),stroke)
        }
        val center = Offset(size.width/2,size.height*.46f)
        drawCircle(Blue.copy(alpha=.6f), 26.dp.toPx(), center, style=Stroke(1.dp.toPx()))
        drawLine(Blue, center-Offset(6.dp.toPx(),0f),center+Offset(6.dp.toPx(),0f),stroke)
        drawLine(Blue, center-Offset(0f,6.dp.toPx()),center+Offset(0f,6.dp.toPx()),stroke)
    }
}

@Composable
private fun ProfileScreen(vm: EyeViewModel, modifier: Modifier) {
    var editing by remember { mutableStateOf<ModelProfile?>(null) }
    LazyColumn(modifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("One eye, many models", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Text("Your provider must support image input and an OpenAI Chat Completions-compatible API — not just text models.", color = Muted, modifier = Modifier.padding(top = 8.dp))
        }
        item { Button(onClick = { editing = ModelProfile() }) { Text("+ Add model") } }
        items(vm.state.profiles, key = { it.id }) { profile ->
            Surface(shape = RoundedCornerShape(12.dp), color = Panel, border = androidx.compose.foundation.BorderStroke(1.dp, if(vm.state.selectedId == profile.id) Blue else Line)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(profile.name, fontSize = 20.sp)
                    Text(profile.model, color = Blue)
                    Text(Uri.parse(profile.endpoint).host.orEmpty(), color = Muted, fontSize = 14.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { vm.select(profile.id) }) { Text(if(vm.state.selectedId == profile.id) "✓ Active" else "Select") }
                        TextButton(onClick = { editing = profile }) { Text("Edit") }
                        TextButton(onClick = { vm.delete(profile.id) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        item { Text("Keys are encrypted on-device with Android Keystore. Your key is sent only to the endpoint you choose — verify the address carefully.", color = Muted) }
    }
    editing?.let { profile ->
        key(profile.id) {
            var name by remember { mutableStateOf(profile.name) }
            var endpoint by remember { mutableStateOf(profile.endpoint) }
            var model by remember { mutableStateOf(profile.model) }
            var apiKey by remember { mutableStateOf(profile.apiKey) }
            var localError by remember { mutableStateOf<String?>(null) }
            AlertDialog(onDismissRequest = { editing = null }, title = { Text("Connect a vision model") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(name, { name = it }, label = { Text("Profile name") }, singleLine = true)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            OutlinedTextField(endpoint, { endpoint = it }, label = { Text("HTTPS endpoint (full path)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), maxLines = 3)
                            OutlinedTextField(model, { model = it }, label = { Text("Vision model ID") }, singleLine = true)
                            OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API key (Bearer)") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
                        }
                        Text("Full path, usually /v1/chat/completions. Get the model ID from your provider. The key may be empty for servers without auth.", fontSize = 14.sp, color = Muted)
                        localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }, confirmButton = { TextButton(onClick = {
                    val p = profile.copy(name = name.trim(), endpoint = endpoint.trim(), model = model.trim(), apiKey = apiKey.trim())
                    if (vm.save(p)) editing = null else localError = vm.state.error
                }) { Text("Save securely") } }, dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } })
        }
    }
}

@Composable
private fun PrivacyScreen(modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        EyeMark(Modifier.size(64.dp))
        Text("The power to see, with clear boundaries", fontSize = 26.sp)
        Text("God Eye is an image-analysis tool. Its name and look are metaphorical — not a real X-ray eye and not a hidden-truth detector.")
        Text("What is sent?", color = Blue, fontSize = 20.sp)
        Text("Only the captured photo, your question, and the analysis prompt — to the model address you configured. The photo is downscaled to a 1280px long edge and rewritten without EXIF/GPS. Fine details may be lost.")
        Text("What is stored?", color = Blue, fontSize = 20.sp)
        Text("The model profile and key stay encrypted on the device. The temporary photo is created in the private cache and deleted after preparation or when the request ends; leftovers are cleaned on next start. The latest analysis lives only in session memory. Cache deletion is not a guaranteed physical wipe.")
        Text("You are in control", color = Blue, fontSize = 20.sp)
        Text("No microphone, location, trackers, or background uploads. Monitoring and active requests stop when the app goes to background or the tab changes. Stop cannot recall data already sent to the server. Retention and cost are governed by the provider.")
        Text("Verify the results", color = Blue, fontSize = 20.sp)
        Text("The model can be wrong. An image cannot prove thoughts, intent, identity, or sensitive attributes. This app is not a medical, legal, or safety decision tool. Text inside images may also mislead the model.")
        Text("God Eye • 0.1.0 / initial release", color = Muted, fontSize = 14.sp)
    }
}
