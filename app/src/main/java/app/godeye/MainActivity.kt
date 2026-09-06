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
                Text("ببین. بررسی کن. مطمئن نشو بی‌دلیل.", fontSize = 14.sp, color = Muted)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("چشم", "مدل‌ها", "حریم خصوصی").forEachIndexed { index, title ->
                FilterChip(selected = page == index, onClick = {
                    if (page != index) { live = false; consent = null; vm.stop(); page = index }
                }, label = { Text(title) })
            }
        }
        state.error?.let { message ->
            Surface(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(8.dp), color = Color(0xFF3B2027)) {
                Column(Modifier.padding(12.dp)) {
                    Text(message, color = Color(0xFFFFDAD6))
                    TextButton(onClick = { vm.error(null) }) { Text("بستن", color = Color(0xFFFFDAD6)) }
                }
            }
        }
        when (page) {
            0 -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (live) "● پایش دوره‌ای فعال" else "○ ارسال خودکار خاموش", color = if (live) Blue else Muted, fontSize = 14.sp)
                        Text(state.active?.name ?: "مدل تعریف نشده", color = Muted, fontSize = 14.sp)
                    }
                }
                item {
                    Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(12.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(12.dp))) {
                        if (permission) {
                            CameraFeed(onReady = { capture = it }, onError = { vm.error(it) })
                            Reticle(Modifier.matchParentSize())
                            Text("پیش‌نمایش محلی • فقط هنگام تحلیل ارسال می‌شود",
                                modifier = Modifier.align(Alignment.BottomCenter).background(Ink.copy(alpha = .88f)).fillMaxWidth().padding(12.dp),
                                color = Color.White, fontSize = 14.sp)
                        } else {
                            Column(Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                EyeMark(Modifier.size(48.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("چشم آمادهٔ باز شدن است", fontSize = 20.sp)
                                Text("برای دیدن صحنه، اجازهٔ دوربین لازم است.", color = Muted, modifier = Modifier.padding(vertical = 12.dp))
                                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("اجازهٔ دوربین") }
                                TextButton(onClick = {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
                                }) { Text("تنظیمات مجوز برنامه") }
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(value = question, onValueChange = { if (it.length <= 1000) question = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("دنبال چه چیزی بگردم؟") },
                        placeholder = { Text("مثلاً: چه جزئیاتی از این صحنه جا مانده؟") }, maxLines = 3, enabled = !state.busy && !live)
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { consent = "single" }, enabled = capture != null && state.active != null && !state.busy && !live,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("تحلیل این لحظه") }
                        OutlinedButton(onClick = { if (live || state.busy) { live = false; vm.stop() } else consent = "live" },
                            enabled = state.busy || live || (capture != null && state.active != null), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(if (live || state.busy) "توقف" else "پایش ۱۵ ثانیه‌ای")
                        }
                    }
                    if (state.busy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                        Text("در حال ثبت / ارسال / تحلیل تصویر…", color = Blue, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (state.active == null) TextButton(onClick = { page = 1 }) { Text("اولین مدل بینایی را اضافه کن ←") }
                }
                item {
                    Text("تحلیل احتمالی است؛ نه کشف غیب، هویت یا افکار افراد. هر تصویر ممکن است هزینهٔ API داشته باشد.", color = Muted, fontSize = 14.sp)
                }
                val report = state.report
                if (report == null) {
                    item {
                        Surface(shape = RoundedCornerShape(12.dp), color = Panel) {
                            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                                Text("ورای نگاه اول؛ نه ورای شواهد", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(8.dp))
                                Text("گزارش در پنج لایه: شواهد، روابط، فرضیه‌ها، نادانسته‌ها و راه بررسی.", color = Muted)
                            }
                        }
                    }
                } else {
                    item {
                        Column {
                            Text("آخرین تحلیل ثبت‌شده", color = Blue, fontSize = 14.sp)
                            Text(report.summary, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                            val time = state.completedAt?.let { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it)) }.orEmpty()
                            Text("${state.reportModel} • $time • مربوط به تصویر ثبت‌شده، نه ویدئوی زنده", color = Muted, fontSize = 14.sp)
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
                    item { TextButton(onClick = vm::clearReport) { Text("پاک کردن تحلیل از این نشست") } }
                }
            }
            1 -> ProfileScreen(vm, Modifier.weight(1f))
            else -> PrivacyScreen(Modifier.weight(1f))
        }
    }
    consent?.let { mode ->
        AlertDialog(onDismissRequest = { consent = null }, title = { Text("اجازهٔ ارسال تصویر") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (mode == "live") "هر ۱۵ ثانیه، در صورت تمام‌شدن درخواست قبلی، یک عکس ارسال می‌شود؛ نه ویدئوی پیوسته. تا توقف یا خروج از برنامه ادامه دارد."
                        else "یک عکس از دوربین برای تحلیل ارسال می‌شود.")
                    Text("گیرنده: ${state.active?.name}\n${state.active?.endpoint}", color = Blue)
                    Text("ممکن است هزینه داشته باشد. سیاست نگهداری تصویر با ارائه‌دهنده است. فقط از صحنه‌هایی استفاده کن که اجازهٔ ارسالشان را داری. توقف، دادهٔ قبلاً ارسال‌شده را پس نمی‌گیرد.")
                }
            }, confirmButton = { TextButton(onClick = {
                consent = null
                if (mode == "live") live = true else takeShot()
            }) { Text("موافقم؛ شروع") } }, dismissButton = { TextButton(onClick = { consent = null }) { Text("لغو") } })
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
                } catch (_: Exception) { ready(null); fail("دوربین باز نشد؛ مجوز و در دسترس بودن دوربین را بررسی کن.") }
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
            Text("یک چشم، مدل‌های مختلف", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Text("ارائه‌دهنده باید تصویر و API سازگار با OpenAI Chat Completions را پشتیبانی کند؛ نه هر مدل متنی.", color = Muted, modifier = Modifier.padding(top = 8.dp))
        }
        item { Button(onClick = { editing = ModelProfile() }) { Text("+ تعریف مدل") } }
        items(vm.state.profiles, key = { it.id }) { profile ->
            Surface(shape = RoundedCornerShape(12.dp), color = Panel, border = androidx.compose.foundation.BorderStroke(1.dp, if(vm.state.selectedId == profile.id) Blue else Line)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(profile.name, fontSize = 20.sp)
                    Text(profile.model, color = Blue)
                    Text(Uri.parse(profile.endpoint).host.orEmpty(), color = Muted, fontSize = 14.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { vm.select(profile.id) }) { Text(if(vm.state.selectedId == profile.id) "✓ فعال" else "انتخاب") }
                        TextButton(onClick = { editing = profile }) { Text("ویرایش") }
                        TextButton(onClick = { vm.delete(profile.id) }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        item { Text("کلیدها روی دستگاه با Android Keystore رمزگذاری می‌شوند. کلید فقط به نشانی انتخابی تو فرستاده می‌شود؛ نشانی را با دقت بررسی کن.", color = Muted) }
    }
    editing?.let { profile ->
        key(profile.id) {
            var name by remember { mutableStateOf(profile.name) }
            var endpoint by remember { mutableStateOf(profile.endpoint) }
            var model by remember { mutableStateOf(profile.model) }
            var apiKey by remember { mutableStateOf(profile.apiKey) }
            var localError by remember { mutableStateOf<String?>(null) }
            AlertDialog(onDismissRequest = { editing = null }, title = { Text("اتصال مدل بینایی") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(name, { name = it }, label = { Text("نام پروفایل") }, singleLine = true)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            OutlinedTextField(endpoint, { endpoint = it }, label = { Text("HTTPS endpoint (full path)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), maxLines = 3)
                            OutlinedTextField(model, { model = it }, label = { Text("Vision model ID") }, singleLine = true)
                            OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API key (Bearer)") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
                        }
                        Text("مسیر کامل، معمولاً /v1/chat/completions. شناسهٔ مدل را از ارائه‌دهنده بگیر. کلید برای سرور بدون احراز هویت می‌تواند خالی باشد.", fontSize = 14.sp, color = Muted)
                        localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }, confirmButton = { TextButton(onClick = {
                    val p = profile.copy(name = name.trim(), endpoint = endpoint.trim(), model = model.trim(), apiKey = apiKey.trim())
                    if (vm.save(p)) editing = null else localError = vm.state.error
                }) { Text("ذخیرهٔ امن") } }, dismissButton = { TextButton(onClick = { editing = null }) { Text("لغو") } })
        }
    }
}

@Composable
private fun PrivacyScreen(modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        EyeMark(Modifier.size(64.dp))
        Text("قدرت دیدن، با مرزهای روشن", fontSize = 26.sp)
        Text("God Eye ابزار تحلیل تصویر است. نام و ظاهر آن استعاری است؛ نه چشم برزخی واقعی و نه ابزار کشف حقیقت پنهان.")
        Text("چه چیزی ارسال می‌شود؟", color = Blue, fontSize = 20.sp)
        Text("فقط عکس انتخاب‌شده از دوربین، پرسش تو و دستور تحلیل؛ به نشانی مدلی که خودت تنظیم کرده‌ای. عکس تا ضلع بلند ۱۲۸۰ پیکسل کوچک می‌شود و بدون EXIF/GPS بازنویسی می‌شود. جزئیات ریز ممکن است از دست بروند.")
        Text("چه چیزی ذخیره می‌شود؟", color = Blue, fontSize = 20.sp)
        Text("پروفایل مدل و کلید به‌صورت رمزگذاری‌شده روی دستگاه می‌مانند. عکس موقت در پوشهٔ خصوصی کش ساخته و بعد از آماده‌سازی یا پایان درخواست حذف می‌شود؛ بازمانده‌های احتمالی در شروع بعدی پاک می‌شوند. آخرین تحلیل فقط در حافظهٔ نشست است. حذف کش به معنی پاک‌سازی تضمینی فیزیکی نیست.")
        Text("کنترل دست توست", color = Blue, fontSize = 20.sp)
        Text("هیچ صدا، موقعیت مکانی، ردیاب یا ارسال پس‌زمینه‌ای اضافه نشده است. با رفتن برنامه به پس‌زمینه یا تعویض زبانه، پایش و درخواست فعال متوقف می‌شوند. توقف نمی‌تواند داده‌ای را که به سرور رسیده پس بگیرد. نگهداری داده و هزینه با ارائه‌دهنده است.")
        Text("نتیجه را راستی‌آزمایی کن", color = Blue, fontSize = 20.sp)
        Text("مدل ممکن است اشتباه کند. از تصویر نمی‌توان افکار، نیت، هویت یا ویژگی‌های حساس افراد را اثبات کرد. این برنامه ابزار تشخیص پزشکی، قضاوت حقوقی یا تصمیم ایمنی نیست. متن‌های داخل تصویر نیز ممکن است مدل را گمراه کنند.")
        Text("God Eye • 0.1.0 / نسخهٔ اولیه", color = Muted, fontSize = 14.sp)
    }
}
