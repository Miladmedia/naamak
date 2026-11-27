package ir.namak.sms.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.namak.sms.core.persian.JalaliCalendar
import ir.namak.sms.core.persian.JalaliDate
import ir.namak.sms.core.persian.PersianTextFormatter
import ir.namak.sms.data.local.ClassificationRuleEntity
import ir.namak.sms.data.local.ScheduledSmsEntity
import ir.namak.sms.domain.model.SmsTab
import ir.namak.sms.ui.components.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesAndScheduleScreen(
    rules: List<ClassificationRuleEntity>,
    scheduledList: List<ScheduledSmsEntity>,
    canScheduleExact: Boolean,
    onBack: () -> Unit,
    onSaveRule: (ClassificationRuleEntity) -> Unit,
    onDeleteRule: (ClassificationRuleEntity) -> Unit,
    onScheduleSms: (address: String, body: String, triggerMillis: Long, confirmed: Boolean) -> Unit,
    onCancelScheduled: (ScheduledSmsEntity) -> Unit
) {
    val context = LocalContext.current
    var selectedSection by remember { mutableIntStateOf(0) }

    var ruleName by remember { mutableStateOf("") }
    var rulePattern by remember { mutableStateOf("") }
    var ruleType by remember { mutableStateOf("KEYWORD") }
    var ruleTarget by remember { mutableStateOf(SmsTab.ADS) }
    var rulePriority by remember { mutableStateOf("10") }

    val todayJalali = remember { JalaliCalendar.fromTimestamp(System.currentTimeMillis()) }
    var schAddress by remember { mutableStateOf("") }
    var schBody by remember { mutableStateOf("") }
    var jYear by remember { mutableIntStateOf(todayJalali.year) }
    var jMonth by remember { mutableIntStateOf(todayJalali.month) }
    var jDay by remember { mutableIntStateOf(todayJalali.day) }
    var jHour by remember { mutableIntStateOf((todayJalali.hour + 1) % 24) }
    var jMinute by remember { mutableIntStateOf(0) }
    var userConfirmedSend by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("قوانین هوشمند و زمان‌بندی شمسی") },
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
                .padding(12.dp)
        ) {
            TabRow(selectedTabIndex = selectedSection) {
                Tab(selected = selectedSection == 0, onClick = { selectedSection = 0 }, text = { Text("زمان‌بندی شمسی") })
                Tab(selected = selectedSection == 1, onClick = { selectedSection = 1 }, text = { Text("قوانین دسته‌بندی") })
            }

            Spacer(Modifier.height(12.dp))

            if (selectedSection == 0) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!canScheduleExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        item {
                            GlassCard(tintColor = MaterialTheme.colorScheme.secondary, alpha = 0.18f) {
                                Text("مجوز آلارم دقیق در اندروید ۱۲+ غیرفعال است؛ ارسال ممکن است با چند دقیقه تأخیر انجام شود.")
                                TextButton(onClick = {
                                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                                }) {
                                    Text("اعطای مجوز زمان‌بندی دقیق")
                                }
                            }
                        }
                    }

                    item {
                        GlassCard {
                            Text("زمان‌بندی پیامک جدید (تقویم جلالی)", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = schAddress,
                                onValueChange = { schAddress = it },
                                label = { Text("شماره گیرنده") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = schBody,
                                onValueChange = { schBody = it },
                                label = { Text("متن پیامک") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = jYear.toString(),
                                    onValueChange = { jYear = it.toIntOrNull() ?: jYear },
                                    label = { Text("سال") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = jMonth.toString(),
                                    onValueChange = { jMonth = (it.toIntOrNull() ?: jMonth).coerceIn(1, 12) },
                                    label = { Text("ماه (۱-۱۲)") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = jDay.toString(),
                                    onValueChange = {
                                        val maxD = JalaliCalendar.getDaysInMonth(jYear, jMonth)
                                        jDay = (it.toIntOrNull() ?: jDay).coerceIn(1, maxD)
                                    },
                                    label = { Text("روز") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = jHour.toString(),
                                    onValueChange = { jHour = (it.toIntOrNull() ?: jHour).coerceIn(0, 23) },
                                    label = { Text("ساعت (۰-۲۳)") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = jMinute.toString(),
                                    onValueChange = { jMinute = (it.toIntOrNull() ?: jMinute).coerceIn(0, 59) },
                                    label = { Text("دقیقه") },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = userConfirmedSend, onCheckedChange = { userConfirmedSend = it })
                                Text("تأیید می‌کنم این پیامک در زمان مقرر ارسال شود.")
                            }

                            Button(
                                enabled = userConfirmedSend && schAddress.isNotBlank() && schBody.isNotBlank(),
                                onClick = {
                                    val trigger = JalaliCalendar.toTimestamp(JalaliDate(jYear, jMonth, jDay, jHour, jMinute))
                                    onScheduleSms(schAddress, schBody, trigger, true)
                                    schBody = ""
                                    userConfirmedSend = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("ثبت در صف ارسال (${JalaliDate(jYear, jMonth, jDay, jHour, jMinute).formatFull()})")
                            }
                        }
                    }

                    items(scheduledList) { item ->
                        GlassCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.address, style = MaterialTheme.typography.titleMedium)
                                    Text(item.body, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        JalaliCalendar.fromTimestamp(item.scheduledTimeMillis).formatFull(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { onCancelScheduled(item) }) {
                                    Icon(Icons.Default.Delete, "لغو")
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        GlassCard {
                            Text("افزودن قانون دسته‌بندی آفلاین", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = ruleName,
                                onValueChange = { ruleName = it },
                                label = { Text("نام قانون (مثلاً: پیامک‌های بانکی)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = rulePattern,
                                onValueChange = { rulePattern = it },
                                label = { Text("الگو / پیش‌شماره / کلیدواژه") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("KEYWORD" to "کلیدواژه", "PREFIX" to "پیش‌شماره", "EXACT_NUMBER" to "شماره دقیق").forEach { (k, label) ->
                                    FilterChip(selected = ruleType == k, onClick = { ruleType = k }, label = { Text(label) })
                                }
                            }
                            OutlinedTextField(
                                value = rulePriority,
                                onValueChange = { rulePriority = it },
                                label = { Text("اولویت اجرا (عدد بزرگ‌تر = اولویت بالاتر)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (ruleName.isNotBlank() && rulePattern.isNotBlank()) {
                                        onSaveRule(
                                            ClassificationRuleEntity(
                                                name = ruleName,
                                                matchType = ruleType,
                                                pattern = rulePattern,
                                                targetCategory = ruleTarget.key,
                                                priority = rulePriority.toIntOrNull() ?: 10
                                            )
                                        )
                                        ruleName = ""
                                        rulePattern = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(6.dp))
                                Text("ذخیره قانون")
                            }
                        }
                    }

                    items(rules) { rule ->
                        GlassCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${rule.name} (اولویت ${PersianTextFormatter.toPersianDigits(rule.priority.toString())})", style = MaterialTheme.typography.titleMedium)
                                    Text("الگو: ${rule.pattern} ← دسته: ${rule.targetCategory}", style = MaterialTheme.typography.bodyMedium)
                                }
                                IconButton(onClick = { onDeleteRule(rule) }) {
                                    Icon(Icons.Default.Delete, "حذف قانون")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
