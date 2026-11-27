package ir.namak.sms.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.namak.sms.core.persian.JalaliCalendar
import ir.namak.sms.core.persian.PersianTextFormatter
import ir.namak.sms.domain.model.SmsMessageUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhaterahBaziScreen(
    message: SmsMessageUi,
    contactName: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val templates = listOf(
        "فیروزه کاشان" to listOf(Color(0xFF0D5C63), Color(0xFF1F8A93)),
        "غروب کویر" to listOf(Color(0xFFB55D35), Color(0xFFD99B26)),
        "شب یلدا" to listOf(Color(0xFF2B193D), Color(0xFF6B2D5C)),
        "کاغذ کاهی" to listOf(Color(0xFFF3E9D2), Color(0xFFE2D1B3))
    )

    var selectedTemplateIdx by remember { mutableIntStateOf(0) }
    var showSender by remember { mutableStateOf(true) }
    var showJalaliDate by remember { mutableStateOf(true) }
    var fontSizeSp by remember { mutableFloatStateOf(18f) }
    var useSerif by remember { mutableStateOf(false) }

    val (templateName, colors) = templates[selectedTemplateIdx]
    val isLightBg = selectedTemplateIdx == 3
    val contentColor = if (isLightBg) Color(0xFF2A2118) else Color.White

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("خاطره بازی (کارت‌پستال پیامکی)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(colors))
                    .border(2.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                    .padding(22.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = PersianTextFormatter.isolateBidiText(message.body),
                        color = contentColor,
                        fontSize = fontSizeSp.sp,
                        fontFamily = if (useSerif) FontFamily.Serif else FontFamily.SansSerif,
                        textAlign = TextAlign.Center,
                        lineHeight = (fontSizeSp * 1.5f).sp
                    )
                    Spacer(Modifier.height(16.dp))
                    if (showSender) {
                        Text(
                            text = "— ${contactName ?: PersianTextFormatter.formatPhoneLtr(message.address)}",
                            color = contentColor.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    if (showJalaliDate) {
                        Text(
                            text = JalaliCalendar.fromTimestamp(message.timestamp).formatFull(),
                            color = contentColor.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Text("انتخاب قالب پس‌زمینه: $templateName", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                templates.forEachIndexed { idx, (_, grad) ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(grad))
                            .border(
                                width = if (selectedTemplateIdx == idx) 3.dp else 1.dp,
                                color = if (selectedTemplateIdx == idx) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable { selectedTemplateIdx = idx }
                    )
                }
            }

            Text("اندازه متن: ${PersianTextFormatter.toPersianDigits(fontSizeSp.toInt().toString())}")
            Slider(value = fontSizeSp, onValueChange = { fontSizeSp = it }, valueRange = 14f..28f)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = useSerif, onCheckedChange = { useSerif = it })
                Spacer(Modifier.width(8.dp))
                Text("فونت ادبی / کلاسیک")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showSender, onCheckedChange = { showSender = it })
                Text("نمایش نام فرستنده / شماره")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showJalaliDate, onCheckedChange = { showJalaliDate = it })
                Text("نمایش تاریخ و ساعت شمسی")
            }

            Button(
                onClick = {
                    savePostcardToGallery(
                        context = context,
                        body = PersianTextFormatter.isolateBidiText(message.body),
                        sender = if (showSender) (contactName ?: message.address) else null,
                        dateText = if (showJalaliDate) JalaliCalendar.fromTimestamp(message.timestamp).formatFull() else null,
                        colorStart = colors[0].toArgb(),
                        colorEnd = colors[1].toArgb(),
                        textColor = contentColor.toArgb(),
                        useSerif = useSerif
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("ذخیره کارت‌پستال در گالری")
            }
        }
    }
}

private fun savePostcardToGallery(
    context: Context,
    body: String,
    sender: String?,
    dateText: String?,
    colorStart: Int,
    colorEnd: Int,
    textColor: Int,
    useSerif: Boolean
) {
    val width = 1080
    val height = 1080
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), colorStart, colorEnd, Shader.TileMode.CLAMP)
    }
    canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 64f, 64f, bgPaint)

    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = 48f
        typeface = if (useSerif) Typeface.SERIF else Typeface.SANS_SERIF
    }

    val fullText = buildString {
        append(body)
        if (!sender.isNullOrBlank()) append("\n\n— $sender")
        if (!dateText.isNullOrBlank()) append("\n$dateText")
    }

    val layout = StaticLayout.Builder.obtain(fullText, 0, fullText.length, textPaint, width - 160)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setLineSpacing(16f, 1.1f)
        .build()

    canvas.save()
    canvas.translate(80f, (height - layout.height) / 2f)
    layout.draw(canvas)
    canvas.restore()

    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Namak_Khaterah_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/NamakSMS")
        }
    }

    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    if (uri != null) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        Toast.makeText(context, "کارت‌پستال در گالری ذخیره شد.", Toast.LENGTH_LONG).show()
    }
}
