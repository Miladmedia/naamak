package ir.namak.sms

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ir.namak.sms.core.backup.SmsBackupManager
import ir.namak.sms.domain.model.SmsMessageUi
import ir.namak.sms.ui.components.GlassCard
import ir.namak.sms.ui.screens.*
import ir.namak.sms.ui.theme.AppFontChoice
import ir.namak.sms.ui.theme.AppThemeMode
import ir.namak.sms.ui.theme.NamakTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var backupManager: SmsBackupManager
    private var viewModelRef: HomeViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: HomeViewModel = hiltViewModel()
            viewModelRef = viewModel
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val rules by viewModel.rulesFlow.collectAsStateWithLifecycle()
            val scheduled by viewModel.scheduledFlow.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()

            val defaultSmsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) {
                viewModel.onResumeCheckDefaultSms()
            }

            val permissionsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) {
                viewModel.refreshAll()
            }

            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                if (uri != null) scope.launch { backupManager.exportToUri(uri) }
            }
            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) scope.launch {
                    backupManager.restoreFromUri(uri)
                    viewModel.refreshAll()
                }
            }

            LaunchedEffect(Unit) {
                val perms = mutableListOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_PHONE_STATE
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissionsLauncher.launch(perms.toTypedArray())
            }

            var currentScreen by remember { mutableStateOf("HOME") }
            var activeThreadId by remember { mutableLongStateOf(-1L) }
            var activeAddress by remember { mutableStateOf("") }
            var forwardText by remember { mutableStateOf("") }
            var khaterahMessage by remember { mutableStateOf<SmsMessageUi?>(null) }

            NamakTheme(
                themeMode = state.themeMode,
                seedColor = state.seedColor,
                fontChoice = state.fontChoice,
                textScale = state.textScale
            ) {
                when (currentScreen) {
                    "HOME" -> HomeScreen(
                        state = state,
                        onRequestDefaultSms = {
                            val reqIntent = viewModel.defaultSmsHelper.createRequestDefaultSmsIntent()
                            if (reqIntent != null) {
                                defaultSmsLauncher.launch(reqIntent)
                            } else {
                                viewModel.onResumeCheckDefaultSms()
                            }
                        },
                        onSelectTab = viewModel::selectTab,
                        onSearchChange = viewModel::updateSearch,
                        onOpenThread = { tid, addr ->
                            activeThreadId = tid
                            activeAddress = addr
                            viewModel.markThreadRead(tid)
                            currentScreen = "CHAT"
                        },
                        onNewMessage = {
                            forwardText = ""
                            currentScreen = "NEW"
                        },
                        onOpenRulesAndSchedule = { currentScreen = "RULES" },
                        onOpenSettings = { currentScreen = "SETTINGS" },
                        onBulkDelete = viewModel::bulkDeleteThreads,
                        onBulkArchive = viewModel::bulkArchiveThreads,
                        onToggleArchiveView = viewModel::toggleShowArchived
                    )

                    "CHAT" -> {
                        val threadUi = state.threads.find { it.threadId == activeThreadId }
                        val msgs = state.messagesByThread[activeThreadId].orEmpty()
                        ChatScreen(
                            threadId = activeThreadId,
                            address = activeAddress,
                            threadUi = threadUi,
                            messages = msgs,
                            activeSims = state.activeSims,
                            onBack = { currentScreen = "HOME" },
                            onSend = { addr, body, sub, tid -> viewModel.sendSms(addr, body, sub, tid) },
                            onDeleteMessages = viewModel::deleteMessages,
                            onToggleStar = viewModel::toggleStarMessage,
                            onUpdateThreadMeta = { pin, arch, mute, spam, block, cat ->
                                viewModel.updateThreadMeta(activeThreadId, activeAddress, pin, arch, mute, spam, block, cat)
                            },
                            onOpenKhaterahBazi = { msg ->
                                khaterahMessage = msg
                                currentScreen = "KHATERAH"
                            },
                            onSetReminder = { msg, triggerAt ->
                                viewModel.setReminder(activeThreadId, msg.id, activeAddress, msg.body, triggerAt)
                            },
                            onForwardMessage = { text ->
                                forwardText = text
                                currentScreen = "NEW"
                            }
                        )
                    }

                    "NEW" -> NewMessageScreen(
                        initialBody = forwardText,
                        contacts = state.contacts,
                        activeSims = state.activeSims,
                        onBack = { currentScreen = "HOME" },
                        onSendAndOpen = { addr, body, sub ->
                            viewModel.sendSms(addr, body, sub)
                            currentScreen = "HOME"
                        }
                    )

                    "RULES" -> RulesAndScheduleScreen(
                        rules = rules,
                        scheduledList = scheduled,
                        canScheduleExact = viewModel.canScheduleExactAlarms(),
                        onBack = { currentScreen = "HOME" },
                        onSaveRule = viewModel::saveRule,
                        onDeleteRule = viewModel::deleteRule,
                        onScheduleSms = { addr, body, trigger, conf ->
                            viewModel.scheduleSms(addr, body, null, trigger, conf)
                        },
                        onCancelScheduled = viewModel::cancelScheduledSms
                    )

                    "KHATERAH" -> khaterahMessage?.let { msg ->
                        val cName = state.threads.find { it.threadId == msg.threadId }?.contactName
                        KhaterahBaziScreen(
                            message = msg,
                            contactName = cName,
                            onBack = { currentScreen = "CHAT" }
                        )
                    }

                    "SETTINGS" -> SettingsScreen(
                        state = state,
                        onBack = { currentScreen = "HOME" },
                        onThemeMode = viewModel::setThemeMode,
                        onSeedColor = viewModel::setSeedColor,
                        onFontChoice = viewModel::setFontChoice,
                        onTextScale = viewModel::setTextScale,
                        onMoveTabUp = { idx -> if (idx > 0) viewModel.reorderTab(idx, idx - 1) },
                        onSetDefaultTab = viewModel::setDefaultTab,
                        onExportBackup = { exportLauncher.launch("Namak_Backup_${System.currentTimeMillis()}.json") },
                        onImportBackup = { importLauncher.launch(arrayOf("application/json")) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModelRef?.onResumeCheckDefaultSms()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: HomeUiState,
    onBack: () -> Unit,
    onThemeMode: (AppThemeMode) -> Unit,
    onSeedColor: (Color) -> Unit,
    onFontChoice: (AppFontChoice) -> Unit,
    onTextScale: (Float) -> Unit,
    onMoveTabUp: (Int) -> Unit,
    onSetDefaultTab: (ir.namak.sms.domain.model.SmsTab) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    val accentColors = listOf(
        Color(0xFF0D5C63),
        Color(0xFF1E6091),
        Color(0xFF2D6A4F),
        Color(0xFF8C2F39),
        Color(0xFF6B2D5C)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ظاهر، زبانه‌ها و پشتیبان‌گیری") },
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
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassCard {
                Text("حالت پوسته و رنگ اصلی", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.themeMode == mode,
                            onClick = { onThemeMode(mode) },
                            label = { Text(mode.titleFa) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    accentColors.forEach { color ->
                        Button(
                            onClick = { onSeedColor(color) },
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            modifier = Modifier.size(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {}
                    }
                }
            }

            GlassCard {
                Text("فونت و اندازه متن", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFontChoice.entries.forEach { font ->
                        FilterChip(
                            selected = state.fontChoice == font,
                            onClick = { onFontChoice(font) },
                            label = { Text(font.titleFa) }
                        )
                    }
                }
                Slider(value = state.textScale, onValueChange = onTextScale, valueRange = 0.85f..1.35f)
            }

            GlassCard {
                Text("ترتیب زبانه‌ها و زبانه پیش‌فرض", style = MaterialTheme.typography.titleMedium)
                state.orderedTabs.forEachIndexed { index, tab ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(tab.titleFa)
                        Row {
                            if (index > 0) {
                                TextButton(onClick = { onMoveTabUp(index) }) { Text("بالا") }
                            }
                            TextButton(onClick = { onSetDefaultTab(tab) }) {
                                Text(if (state.defaultTab == tab) "پیش‌فرض فعلی" else "انتخاب به‌عنوان پیش‌فرض")
                            }
                        }
                    }
                }
            }

            GlassCard {
                Text("پشتیبان‌گیری و بازیابی آفلاین (SAF)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onExportBackup, modifier = Modifier.weight(1f)) {
                        Text("خروجی پشتیبان JSON")
                    }
                    OutlinedButton(onClick = onImportBackup, modifier = Modifier.weight(1f)) {
                        Text("بازیابی از فایل")
                    }
                }
            }
        }
    }
}
